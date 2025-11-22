package com.distributed.cache.raft.api;

import com.distributed.cache.raft.RaftNode;
import com.distributed.cache.raft.RaftState;
import com.distributed.cache.store.KeyValueStore;
import com.distributed.cache.store.KeyValueStore.NotLeaderException;
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
        app = Javalin.create(conf -> conf.http.defaultContentType = "application/json");

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

        // GET /cache/access-stats - Get access statistics for ML-based eviction
        // IMPORTANT: This must come BEFORE /cache/{key} to avoid path matching issues
        app.get("/cache/access-stats", ctx -> {
            try {
                ctx.json(Map.of(
                    "nodeId", raftNode.getNodeId(),
                    "trackedKeys", kvStore.getAccessTracker().getTrackedKeyCount(),
                    "stats", kvStore.getAccessTracker().getAllStatsAsMaps()
                ));
            } catch (Exception e) {
                logger.error("Failed to get access stats", e);
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .json(Map.of("error", e.getMessage()));
            }
        });

        // GET /cache/{key}
        app.get("/cache/{key}", ctx -> {
            String key = ctx.pathParam("key");
            String value = kvStore.get(key);
            ctx.header("X-Raft-Role", raftNode.getState().name());
            if (value == null) {
                ctx.status(HttpStatus.NOT_FOUND)
                        .json(new ClientResponse(false, null, "Key not found", raftNode.getNodeId()));
            } else {
                ctx.json(new ClientResponse(true, value, null, raftNode.getNodeId()));
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
            ctx.json(Map.of(
                    "nodeId", raftNode.getNodeId(),
                    "role", raftNode.getState().name(),
                    "currentTerm", raftNode.getCurrentTerm(),
                    "commitIndex", raftNode.getCommitIndex(),
                    "leaderId", raftNode.getVotedFor(),
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

        // Placeholder metrics endpoint
        app.get("/metrics", ctx -> ctx.result("Metrics coming soon"));

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
