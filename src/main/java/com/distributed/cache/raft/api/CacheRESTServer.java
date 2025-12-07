package com.distributed.cache.raft.api;

import com.distributed.cache.raft.RaftNode;
import com.distributed.cache.raft.RaftState;
import com.distributed.cache.store.KeyValueStore;
import com.distributed.cache.store.KeyValueStore.NotLeaderException;
import com.distributed.cache.store.ReadConsistency;
import com.google.gson.Gson;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * CacheRESTServer exposes an HTTP API for client interaction with the
 * distributed Raft-based key-value store.
 *
 * Routes:
 * POST /cache/{key}
 * GET /cache/{key}
 * DELETE /cache/{key}
 * GET /status
 * GET /health
 * GET /metrics (placeholder)
 */
public class CacheRESTServer {

    private static final Logger logger = LoggerFactory.getLogger(CacheRESTServer.class);
    private static final int REQUEST_TIMEOUT_MS = 5000;

    private final RaftNode raftNode;
    private final KeyValueStore kvStore;
    private final int httpPort;
    private final LeaderProxy leaderProxy;
    private final Gson gson = new Gson();
    private Javalin app;

    public CacheRESTServer(RaftNode raftNode, int httpPort, Map<String, String> nodeHttpAddresses) {
        this.raftNode = raftNode;
        this.kvStore = raftNode.getKvStore();
        this.httpPort = httpPort;
        this.leaderProxy = new LeaderProxy(nodeHttpAddresses);
    }

    /** Start HTTP server and register routes. */
    public void start() {
        app = Javalin.create(conf -> {
            conf.http.defaultContentType = "application/json";
            conf.plugins.enableCors(cors -> cors.add(it -> it.anyHost()));
        });

        // POST /cache/{key} → create/update key
        app.post("/cache/{key}", ctx -> {
            String key = ctx.pathParam("key");
            ClientRequest req = gson.fromJson(ctx.body(), ClientRequest.class);

            if (raftNode.getState() != RaftState.LEADER) {
                // Try forwarding to known leader
                if (leaderProxy.isLeaderKnown()) {
                    logger.info("Forwarding PUT to leader {}", leaderProxy.getLeaderHttpAddress());
                    ClientResponse forwarded = leaderProxy.forwardPutToLeader(
                            key, req.getValue(), req.getClientId(), req.getSequenceNumber());
                    ctx.status(HttpStatus.OK).json(forwarded);
                } else {
                    ctx.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .json(new ClientResponse(false, null, "Leader election in progress", null));
                }
                return;
            }

            try {
                CompletableFuture<String> f = kvStore.put(
                        key, req.getValue(), req.getClientId(), req.getSequenceNumber());
                String value = f.get(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                ctx.json(new ClientResponse(true, value, null, raftNode.getNodeId()));
            } catch (TimeoutException e) {
                ctx.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .json(new ClientResponse(false, null, "Commit timeout", raftNode.getNodeId()));
            } catch (NotLeaderException e) {
                ctx.status(HttpStatus.TEMPORARY_REDIRECT)
                        .json(new ClientResponse(false, null, e.getMessage(), raftNode.getVotedFor()));
            } catch (Exception e) {
                logger.error("PUT failed", e);
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .json(new ClientResponse(false, null, e.getMessage(), raftNode.getNodeId()));
            }
        });

        // GET /cache/all - Get all cache contents
        // IMPORTANT: This must come BEFORE /cache/{key} to avoid path matching issues
        app.get("/cache/all", ctx -> {
            try {
                ctx.json(Map.of(
                        "nodeId", raftNode.getNodeId(),
                        "role", raftNode.getState().name(),
                        "data", kvStore.getAllData()));
            } catch (Exception e) {
                logger.error("Failed to get all cache data", e);
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .json(Map.of("error", e.getMessage()));
            }
        });

        // GET /cache/access-stats - Get access statistics for ML-based eviction
        // IMPORTANT: This must come BEFORE /cache/{key} to avoid path matching issues
        app.get("/cache/access-stats", ctx -> {
            try {
                ctx.json(Map.of(
                        "nodeId", raftNode.getNodeId(),
                        "trackedKeys", kvStore.getAccessTracker().getTrackedKeyCount(),
                        "stats", kvStore.getAccessTracker().getAllStatsAsMaps()));
            } catch (Exception e) {
                logger.error("Failed to get access stats", e);
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .json(Map.of("error", e.getMessage()));
            }
        });

        // GET /cache/{key}?consistency=[strong|lease|eventual] - Read with configurable
        // consistency
        app.get("/cache/{key}", ctx -> {
            String key = ctx.pathParam("key");
            String consistencyParam = ctx.queryParam("consistency");

            // Parse consistency level (default to STRONG for backwards compatibility)
            ReadConsistency consistency = ReadConsistency.STRONG;
            if (consistencyParam != null) {
                try {
                    consistency = ReadConsistency.valueOf(consistencyParam.toUpperCase());
                } catch (IllegalArgumentException e) {
                    ctx.status(HttpStatus.BAD_REQUEST)
                            .json(new ClientResponse(false, null,
                                    "Invalid consistency level. Use: strong, lease, or eventual",
                                    raftNode.getNodeId()));
                    return;
                }
            }

            try {
                long startTime = System.nanoTime();

                // Use the new multi-level get method
                CompletableFuture<String> future = kvStore.get(key, consistency);
                String value = future.get(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

                long latencyMs = (System.nanoTime() - startTime) / 1_000_000;

                ctx.header("X-Raft-Role", raftNode.getState().name());
                ctx.header("X-Consistency-Level", consistency.name());
                ctx.header("X-Read-Latency-Ms", String.valueOf(latencyMs));
                if (raftNode.hasValidReadLease()) {
                    ctx.header("X-Lease-Remaining-Ms", String.valueOf(raftNode.getLeaseRemainingMs()));
                }

                if (value == null) {
                    ctx.status(HttpStatus.NOT_FOUND)
                            .json(new ClientResponse(false, null, "Key not found", raftNode.getNodeId()));
                } else {
                    ctx.json(new ClientResponse(true, value, null, raftNode.getNodeId()));
                }
            } catch (TimeoutException e) {
                ctx.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .json(new ClientResponse(false, null, "Read timeout", raftNode.getNodeId()));
            } catch (com.distributed.cache.raft.NotLeaderException e) {
                ctx.status(HttpStatus.TEMPORARY_REDIRECT)
                        .json(new ClientResponse(false, null, e.getMessage(), e.getCurrentLeaderId()));
            } catch (Exception e) {
                logger.error("GET failed", e);
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .json(new ClientResponse(false, null, e.getMessage(), raftNode.getNodeId()));
            }
        });

        // DELETE /cache/{key}
        app.delete("/cache/{key}", ctx -> {
            String key = ctx.pathParam("key");
            ClientRequest req = gson.fromJson(ctx.body(), ClientRequest.class);

            if (raftNode.getState() != RaftState.LEADER) {
                if (leaderProxy.isLeaderKnown()) {
                    logger.info("Forwarding DELETE to leader {}", leaderProxy.getLeaderHttpAddress());
                    ClientResponse forwarded = leaderProxy.forwardDeleteToLeader(
                            key, req.getClientId(), req.getSequenceNumber());
                    ctx.status(HttpStatus.OK).json(forwarded);
                } else {
                    ctx.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .json(new ClientResponse(false, null, "Leader election in progress", null));
                }
                return;
            }

            try {
                CompletableFuture<Boolean> f = kvStore.delete(key, req.getClientId(), req.getSequenceNumber());
                Boolean success = f.get(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                ctx.json(new ClientResponse(success, null, null, raftNode.getNodeId()));
            } catch (TimeoutException e) {
                ctx.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .json(new ClientResponse(false, null, "Commit timeout", raftNode.getNodeId()));
            } catch (Exception e) {
                logger.error("DELETE failed", e);
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .json(new ClientResponse(false, null, e.getMessage(), raftNode.getNodeId()));
            }
        });

        // GET /status
        app.get("/status", ctx -> {
            String leaderId = raftNode.getVotedFor();
            ctx.json(Map.of(
                    "nodeId", raftNode.getNodeId(),
                    "role", raftNode.getState().name(),
                    "currentTerm", raftNode.getCurrentTerm(),
                    "commitIndex", raftNode.getCommitIndex(),
                    "leaderId", leaderId != null ? leaderId : "",
                    "logSize", raftNode.getRaftLog().getLastIndex()));
        });

        // GET /health
        app.get("/health", ctx -> {
            boolean healthy = (raftNode.getState() == RaftState.LEADER)
                    || (System.currentTimeMillis() - raftNode.getLastHeartbeatReceived() < 2000);
            if (healthy)
                ctx.status(HttpStatus.OK).result("OK");
            else
                ctx.status(HttpStatus.SERVICE_UNAVAILABLE).result("Unhealthy");
        });

        // GET /stats - Cache statistics and eviction info
        app.get("/stats", ctx -> {
            Map<String, Object> stats = kvStore.getStats();
            stats.put("nodeId", raftNode.getNodeId());
            stats.put("role", raftNode.getState().name());
            stats.put("currentTerm", raftNode.getCurrentTerm());
            ctx.json(stats);
        });

        // GET /raft/log - Get all log entries
        app.get("/raft/log", ctx -> {
            try {
                ctx.json(raftNode.getRaftLog().getAllEntries());
            } catch (Exception e) {
                logger.error("Failed to get raft log", e);
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .json(Map.of("error", e.getMessage()));
            }
        });

        // GET /raft/replication-state - Get log replication state (leader only)
        app.get("/raft/replication-state", ctx -> {
            try {
                boolean isLeader = raftNode.getState() == RaftState.LEADER;
                var replicator = raftNode.getLeaderReplicator();

                if (isLeader && replicator != null) {
                    ctx.json(Map.of(
                            "isLeader", true,
                            "nodeId", raftNode.getNodeId(),
                            "currentTerm", raftNode.getCurrentTerm(),
                            "lastLogIndex", raftNode.getRaftLog().getLastIndex(),
                            "commitIndex", raftNode.getCommitIndex(),
                            "nextIndex", replicator.getAllNextIndex(),
                            "matchIndex", replicator.getAllMatchIndex(),
                            "followers", replicator.getPeerIds()));
                } else {
                    // Not a leader - return basic info
                    ctx.json(Map.of(
                            "isLeader", false,
                            "nodeId", raftNode.getNodeId(),
                            "role", raftNode.getState().name(),
                            "currentTerm", raftNode.getCurrentTerm(),
                            "lastLogIndex", raftNode.getRaftLog().getLastIndex(),
                            "commitIndex", raftNode.getCommitIndex()));
                }
            } catch (Exception e) {
                logger.error("Failed to get replication state", e);
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .json(Map.of("error", e.getMessage()));
            }
        });

        // POST /admin/shutdown - Gracefully shutdown this node (for demo purposes)
        app.post("/admin/shutdown", ctx -> {
            logger.warn("Received shutdown request - node will terminate in 500ms");
            ctx.json(Map.of("status", "shutting_down", "nodeId", raftNode.getNodeId()));

            // Shutdown in a separate thread to allow response to be sent
            new Thread(() -> {
                try {
                    Thread.sleep(500);
                    logger.info("Executing shutdown...");
                    raftNode.shutdown();
                    System.exit(0);
                } catch (Exception e) {
                    logger.error("Error during shutdown", e);
                    System.exit(1);
                }
            }).start();
        });

        app.start(httpPort);
        logger.info("CacheRESTServer started on port {}", httpPort);
    }

    public void stop() {
        if (app != null) {
            app.stop();
            logger.info("CacheRESTServer stopped");
        }
    }
}
