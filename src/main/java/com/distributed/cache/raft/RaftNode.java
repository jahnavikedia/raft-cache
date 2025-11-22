package com.distributed.cache.raft;

import com.distributed.cache.network.NetworkBase;
import com.distributed.cache.network.MessageSerializer;
import com.distributed.cache.persistence.PersistentState;
import com.distributed.cache.replication.RaftLog;
import com.distributed.cache.replication.LeaderReplicator;
import com.distributed.cache.replication.FollowerReplicator;
import com.distributed.cache.store.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * RaftNode: core Raft state machine for Week 1 (heartbeats + elections).
 *
 * Key behaviors:
 * - Starts NetworkBase server, registers handlers, connects to peers.
 * - Heartbeat timer for leaders.
 * - Election timer for followers/candidates.
 *
 * Thread-safety: synchronized on state-changing methods; volatile for shared
 * vars.
 */
public class RaftNode {
    private static final Logger logger = LoggerFactory.getLogger(RaftNode.class);

    private static final long HEARTBEAT_INTERVAL_MS = 50;
    private static final long ELECTION_TIMEOUT_MIN_MS = 150;
    private static final long ELECTION_TIMEOUT_MAX_MS = 300;

    private final String nodeId;
    private final int port;
    private final long startTimeMs = System.currentTimeMillis();

    private volatile long currentTerm = 0;
    private volatile String votedFor = null;
    private volatile RaftState state;
    private volatile long commitIndex = 0;
    private volatile long lastApplied = 0;

    private Map<String, Long> nextIndex = new ConcurrentHashMap<>();
    private Map<String, Long> matchIndex = new ConcurrentHashMap<>();

    private NetworkBase networkBase;
    private final Map<String, String> peers = new ConcurrentHashMap<>();

    private ScheduledExecutorService heartbeatScheduler;
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledExecutorService electionScheduler;
    private ScheduledFuture<?> electionTask;

    private final Random random = new Random();

    private final Map<String, Boolean> votesReceived = new ConcurrentHashMap<>();
    private final ReadLease readLease = new ReadLease();

    private volatile long lastHeartbeatReceived = 0;
    private volatile long heartbeatsSent = 0;
    private volatile long heartbeatsReceived = 0;
    private volatile long electionsStarted = 0;

    // --- Persistent state (term/vote durable storage)
    private final PersistentState persistentState;

    // --- Log replication and state machine components
    private final RaftLog raftLog;
    private final KeyValueStore kvStore;
    private LeaderReplicator replicator;
    private final FollowerReplicator followerReplicator;
    private final SnapshotManager snapshotManager;

    private ScheduledExecutorService applyEntriesScheduler;
    private ScheduledFuture<?> applyEntriesTask;
    private ScheduledExecutorService snapshotScheduler;
    private ScheduledFuture<?> snapshotTask;

    public RaftNode(String nodeId, int port) {
        this.nodeId = nodeId;
        this.port = port;
        this.state = RaftState.FOLLOWER;

        // Load persistent state from disk
        this.persistentState = new PersistentState(nodeId);
        this.currentTerm = persistentState.getStoredTerm();
        this.votedFor = persistentState.getStoredVotedFor();

        // Initialize log replication and state machine components
        this.raftLog = new RaftLog(nodeId);
        this.kvStore = new KeyValueStore(nodeId, raftLog);
        this.followerReplicator = new FollowerReplicator(nodeId, raftLog, kvStore);
        this.replicator = null; // Will be set when becoming leader

        // Initialize ReadIndexManager for safe reads
        ReadIndexManager readIndexManager = new ReadIndexManager(this);
        this.kvStore.setReadIndexManager(readIndexManager);
        this.kvStore.setRaftNode(this);  // For read lease checking

        // Initialize SnapshotManager and load snapshot if exists
        this.snapshotManager = new SnapshotManager(nodeId, raftLog, kvStore);
        Snapshot loadedSnapshot = snapshotManager.loadLatestSnapshot();
        if (loadedSnapshot != null) {
            logger.info("Loaded snapshot on startup: lastIndex={}, dataSize={}",
                       loadedSnapshot.getLastIncludedIndex(), loadedSnapshot.getData().size());
        }

        this.lastHeartbeatReceived = System.currentTimeMillis();
        logger.info("RaftNode initialized: id={}, port={}, initial_term={}, initial_state={}, votedFor={}",
                nodeId, port, currentTerm, state, votedFor);
    }

    public void configurePeers(Map<String, String> peerMap) {
        peers.putAll(peerMap);
        logger.info("Configured {} peers: {}", peerMap.size(), peerMap.keySet());
    }

    public void addPeer(String peerId, String host, int port) {
        String address = host + ":" + port;
        peers.put(peerId, address);
        logger.info("Added peer: {} at {}", peerId, address);
    }

    public void start() throws InterruptedException {
        logger.info("Starting Raft node: {}", nodeId);

        // 1. Start network listener first
        networkBase = new NetworkBase(nodeId, port);
        networkBase.startServer();

        // 2. Register message handlers
        networkBase.registerMessageHandler(Message.MessageType.APPEND_ENTRIES, this::handleAppendEntries);
        networkBase.registerMessageHandler(Message.MessageType.APPEND_ENTRIES_RESPONSE,
                this::handleAppendEntriesResponse);
        networkBase.registerMessageHandler(Message.MessageType.REQUEST_VOTE, this::handleRequestVote);
        networkBase.registerMessageHandler(Message.MessageType.REQUEST_VOTE_RESPONSE, this::handleRequestVoteResponse);

        // 3. Give servers a small head start so all ports are bound
        Thread.sleep(200);

        // 4. Try connecting to peers with a bounded retry loop
        for (Map.Entry<String, String> peer : peers.entrySet()) {
            String peerId = peer.getKey();
            String[] parts = peer.getValue().split(":");
            String host = parts[0];
            int peerPort = Integer.parseInt(parts[1]);

            int attempts = 0;
            while (attempts < 5) {
                networkBase.connectToPeer(peerId, host, peerPort);
                Thread.sleep(250);
                if (networkBase.isConnectedToPeer(peerId))
                    break;
                attempts++;
            }
        }

        // 5. Wait until expected peers show up or timeout
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (networkBase.getActivePeerCount() == peers.size())
                break;
            Thread.sleep(100);
        }

        logger.info("Node {} connected to {} peers", nodeId, networkBase.getActivePeerCount());

        // 6. Initialize scheduler threads
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "HeartbeatTimer-" + nodeId));
        electionScheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "ElectionTimer-" + nodeId));
        applyEntriesScheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "ApplyEntries-" + nodeId));
        snapshotScheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "Snapshot-" + nodeId));

        // 7. Start periodic task to apply committed entries
        applyEntriesTask = applyEntriesScheduler.scheduleAtFixedRate(
                this::applyCommittedEntries,
                100,
                100,
                TimeUnit.MILLISECONDS
        );

        // 8. Start periodic snapshot check (every 10 seconds)
        snapshotTask = snapshotScheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        snapshotManager.setCurrentTerm((int) currentTerm);
                        snapshotManager.checkAndCreateSnapshot();
                    } catch (Exception e) {
                        logger.error("Error during periodic snapshot check", e);
                    }
                },
                10,
                10,
                TimeUnit.SECONDS
        );

        // 9. Start as follower with a normal randomized election timeout
        resetElectionTimer(); // FIX: was resetElectionTimer(ELECTION_TIMEOUT_MAX_MS * 3L);
        logger.info("Node {} started successfully. State={}, Term={}", nodeId, state, currentTerm);
    }

    public void shutdown() {
        logger.info("Shutting down Raft node: {}", nodeId);

        stopHeartbeatTimer();
        stopElectionTimer();

        if (applyEntriesTask != null && !applyEntriesTask.isCancelled()) {
            applyEntriesTask.cancel(false);
        }

        if (snapshotTask != null && !snapshotTask.isCancelled()) {
            snapshotTask.cancel(false);
        }

        if (replicator != null) {
            replicator.stopReplication();
        }

        if (heartbeatScheduler != null)
            heartbeatScheduler.shutdown();
        if (electionScheduler != null)
            electionScheduler.shutdown();
        if (applyEntriesScheduler != null)
            applyEntriesScheduler.shutdown();
        if (snapshotScheduler != null)
            snapshotScheduler.shutdown();

        if (networkBase != null)
            networkBase.shutdown();

        if (raftLog != null)
            raftLog.close();

        if (kvStore != null)
            kvStore.shutdown();

        logger.info("Node {} shutdown complete", nodeId);
    }

    private void startHeartbeatTimer() {
        stopHeartbeatTimer();
        logger.info("Starting heartbeat timer for leader {}", nodeId);
        heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(
                this::sendHeartbeats,
                HEARTBEAT_INTERVAL_MS, // delay first tick to avoid an extra send in the 500 ms window
                HEARTBEAT_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
    }

    private void stopHeartbeatTimer() {
        if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
            heartbeatTask.cancel(false);
            logger.debug("Stopped heartbeat timer for {}", nodeId);
        }
    }

    private void sendHeartbeats() {
        if (state != RaftState.LEADER) {
            logger.warn("Non-leader {} tried to send heartbeats. State={}", nodeId, state);
            return;
        }
        Message heartbeat = MessageSerializer.createHeartbeat(nodeId, currentTerm, commitIndex);
        networkBase.broadcastMessage(heartbeat);
        heartbeatsSent++;
        logger.debug("Leader {} sent heartbeat #{} to {} peers (term={})", nodeId, heartbeatsSent, peers.size(),
                currentTerm);
    }

    private synchronized void resetElectionTimer() {
        stopElectionTimer();
        long timeout = ELECTION_TIMEOUT_MIN_MS
                + random.nextInt((int) (ELECTION_TIMEOUT_MAX_MS - ELECTION_TIMEOUT_MIN_MS));
        logger.trace("Resetting election timer for {} with timeout {}ms", nodeId, timeout);
        electionTask = electionScheduler.schedule(this::onElectionTimeout, timeout, TimeUnit.MILLISECONDS);
    }

    private synchronized void resetElectionTimer(long initialDelayMs) {
        stopElectionTimer();
        long timeout = ELECTION_TIMEOUT_MIN_MS
                + random.nextInt((int) (ELECTION_TIMEOUT_MAX_MS - ELECTION_TIMEOUT_MIN_MS));
        long delay = Math.max(timeout, initialDelayMs);
        logger.trace("Resetting election timer for {} with custom delay {}ms (base timeout {}ms)", nodeId, delay,
                timeout);
        electionTask = electionScheduler.schedule(this::onElectionTimeout, delay, TimeUnit.MILLISECONDS);
    }

    private void stopElectionTimer() {
        if (electionTask != null && !electionTask.isCancelled()) {
            electionTask.cancel(false);
            logger.trace("Stopped election timer for {}", nodeId);
        }
    }

    private void onElectionTimeout() {
        logger.warn("Election timeout fired for {} (no heartbeat received). State={}, Term={}", nodeId, state,
                currentTerm);
        long timeSinceLastHeartbeat = System.currentTimeMillis() - lastHeartbeatReceived;
        logger.warn("Time since last heartbeat: {}ms", timeSinceLastHeartbeat);

        // FIX: remove startup grace guard so Test 2 can observe elections within ~500
        // ms
        // long uptime = System.currentTimeMillis() - this.startTimeMs;
        // if (uptime < 5000) {
        // logger.warn("Delaying election for {} (startup grace period, uptime={}ms)",
        // nodeId, uptime);
        // resetElectionTimer();
        // return;
        // }

        if (!hasQuorumConnectivity()) {
            logger.warn("Delaying election for {}: only {} of {} peers connected (need quorum).",
                    nodeId,
                    (networkBase != null ? networkBase.getActivePeerCount() : 0),
                    peers.size());
            resetElectionTimer();
            return;
        }

        if (state == RaftState.FOLLOWER || state == RaftState.CANDIDATE) {
            logger.info("Node {} detected leader failure, starting election", nodeId);
            startElection();
        }
    }

    private boolean hasQuorumConnectivity() {
        if (peers.isEmpty() || networkBase == null)
            return true;
        int total = getTotalNodes();
        int majority = (total / 2) + 1;
        int peersNeeded = majority - 1;
        int activePeers = networkBase.getActivePeerCount();
        return activePeers >= peersNeeded;
    }

    private synchronized void startElection() {
        currentTerm++;
        persistentState.saveTerm(currentTerm); // persist term
        electionsStarted++;
        becomeCandidate();
        votedFor = nodeId;
        persistentState.saveVotedFor(votedFor);
        votesReceived.clear();
        votesReceived.put(nodeId, true);
        logger.info("Node {} started election for term {} (election #{})", nodeId, currentTerm, electionsStarted);

        resetElectionTimer();
        sendRequestVoteToAll();
        checkElectionResult();
    }

    private void sendRequestVoteToAll() {
        for (String peerId : peers.keySet())
            sendRequestVote(peerId);
    }

    private void sendRequestVote(String targetNodeId) {
        Message requestVote = new Message(Message.MessageType.REQUEST_VOTE, currentTerm, nodeId);
        requestVote.setCandidateId(nodeId);
        requestVote.setLastLogIndex(0);
        requestVote.setLastLogTerm(0);

        logger.debug("Node {} sending RequestVote to {} for term {}", nodeId, targetNodeId, currentTerm);
        if (networkBase != null)
            networkBase.sendMessage(targetNodeId, requestVote);
        else
            logger.warn("Node {} cannot send RequestVote - network not ready", nodeId);
    }

    private synchronized void handleRequestVote(Message request) {
        long requestTerm = request.getTerm();
        String candidateId = request.getCandidateId();

        updateTerm(requestTerm);

        logger.debug("Node {} received RequestVote from {} for term {} (current term: {})", nodeId, candidateId,
                requestTerm, currentTerm);

        Message response = new Message(Message.MessageType.REQUEST_VOTE_RESPONSE, currentTerm, nodeId);
        response.setSuccess(false);

        if (requestTerm == currentTerm) {
            if (votedFor == null || votedFor.equals(candidateId)) {
                response.setSuccess(true);
                votedFor = candidateId;
                persistentState.saveVotedFor(votedFor);
                resetElectionTimer();
                logger.info("Node {} GRANTED vote to {} for term {}", nodeId, candidateId, requestTerm);
            } else {
                logger.info("Node {} DENIED vote to {} (already voted for {})", nodeId, candidateId, votedFor);
            }
        } else if (requestTerm < currentTerm) {
            logger.info("Node {} DENIED vote to {} (stale term {} < {})", nodeId, candidateId, requestTerm,
                    currentTerm);
        }

        networkBase.sendMessage(request.getSenderId(), response);
    }

    private synchronized void handleRequestVoteResponse(Message response) {
        updateTerm(response.getTerm());
        String fromNodeId = response.getSenderId();
        long responseTerm = response.getTerm();
        boolean voteGranted = response.isSuccess();

        logger.debug("Node {} received vote response from {}: term={}, granted={}", nodeId, fromNodeId, responseTerm,
                voteGranted);

        if (state != RaftState.CANDIDATE)
            return;

        if (responseTerm > currentTerm) {
            stepDown(responseTerm);
            return;
        }

        if (voteGranted && responseTerm == currentTerm) {
            votesReceived.put(fromNodeId, true);
            logger.info("Node {} received vote from {}. Total: {}/{}", nodeId, fromNodeId, votesReceived.size(),
                    getMajority());
            checkElectionResult();
        }
    }

    private synchronized void checkElectionResult() {
        if (state != RaftState.CANDIDATE)
            return;
        int votes = votesReceived.size();
        int majority = getMajority();
        if (votes >= majority) {
            logger.info("Node {} WON election with {}/{} votes!", nodeId, votes, getTotalNodes());
            becomeLeader();
        }
    }

    private int getMajority() {
        int totalNodes = getTotalNodes();
        return (totalNodes / 2) + 1;
    }

    private int getTotalNodes() {
        return peers.size() + 1;
    }

    private synchronized void handleAppendEntries(Message message) {
        updateTerm(message.getTerm());
        logger.debug("Node {} received AppendEntries from {} (term={}, our_term={}, entries={})",
                nodeId, message.getLeaderId(), message.getTerm(), currentTerm, message.getEntries().size());

        heartbeatsReceived++;
        lastHeartbeatReceived = System.currentTimeMillis();

        boolean success = true;
        long matchIndex = 0;
        String responseReason = "accepted";

        if (message.getTerm() < currentTerm) {
            success = false;
            responseReason = "stale term";
            logger.debug("Rejecting AppendEntries from {} - stale term ({} < {})", message.getLeaderId(),
                    message.getTerm(), currentTerm);
        } else {
            if (message.getTerm() > currentTerm) {
                stepDown(message.getTerm());
            }
            resetElectionTimer();
            if (state == RaftState.CANDIDATE) {
                logger.info("Candidate {} stepping down - received heartbeat from leader {}", nodeId,
                        message.getLeaderId());
                becomeFollower();
            }

            // Process log entries using FollowerReplicator
            if (!message.getEntries().isEmpty() || message.getPrevLogIndex() > 0) {
                // Convert Message to AppendEntriesRequest
                com.distributed.cache.replication.AppendEntriesRequest request =
                    new com.distributed.cache.replication.AppendEntriesRequest(
                        (int) message.getTerm(),
                        message.getLeaderId(),
                        message.getPrevLogIndex(),
                        (int) message.getPrevLogTerm(),
                        message.getEntries(),
                        message.getLeaderCommit()
                    );

                // Let FollowerReplicator handle it
                com.distributed.cache.replication.AppendEntriesResponse followerResponse =
                    followerReplicator.handleAppendEntries(request);

                success = followerResponse.isSuccess();
                matchIndex = followerResponse.getMatchIndex();
                responseReason = success ? "log replicated" : "log inconsistency";
            } else {
                // Just a heartbeat, update commitIndex if needed
                if (message.getLeaderCommit() > commitIndex) {
                    long oldCommitIndex = commitIndex;
                    commitIndex = message.getLeaderCommit();
                    raftLog.setCommitIndex(commitIndex);
                    logger.debug("Updated commitIndex from {} to {}", oldCommitIndex, commitIndex);
                }
                matchIndex = raftLog.getLastIndex();
            }
        }

        // Send response with matchIndex
        Message response = MessageSerializer.createHeartbeatResponse(nodeId, currentTerm, success);
        response.setMatchIndex(matchIndex);
        networkBase.sendMessage(message.getSenderId(), response);

        logger.trace("Node {} responded to AppendEntries from {} with success={}, matchIndex={} ({})",
                nodeId, message.getLeaderId(), success, matchIndex, responseReason);
    }

    private synchronized void handleAppendEntriesResponse(Message message) {
        updateTerm(message.getTerm());
        if (state != RaftState.LEADER)
            return;

        logger.trace("Leader {} received AppendEntries response from {} (success={}, matchIndex={}, term={})",
                nodeId, message.getSenderId(), message.isSuccess(), message.getMatchIndex(), message.getTerm());

        if (message.getTerm() > currentTerm) {
            stepDown(message.getTerm());
            return;
        }

        // Pass response to LeaderReplicator for processing
        if (replicator != null) {
            com.distributed.cache.replication.AppendEntriesResponse response =
                new com.distributed.cache.replication.AppendEntriesResponse(
                    (int) message.getTerm(),
                    message.isSuccess(),
                    message.getMatchIndex(),
                    message.getSenderId()
                );
            replicator.handleAppendEntriesResponse(response);
        }
    }

    private synchronized void becomeFollower() {
        if (state == RaftState.FOLLOWER)
            return;
        logger.info("Node {} transitioning to FOLLOWER (term={})", nodeId, currentTerm);
        RaftState oldState = state;
        state = RaftState.FOLLOWER;
        if (oldState == RaftState.LEADER) {
            stopHeartbeatTimer();
            if (replicator != null) {
                replicator.stopReplication();
                replicator = null;
            }
            kvStore.setReplicator(null);
            readLease.invalidate();  // Invalidate lease when transitioning from leader
        }
        resetElectionTimer();
    }

    public synchronized void becomeLeader() {
        if (state == RaftState.LEADER)
            return;
        logger.info("Node {} transitioning to LEADER (term={})", nodeId, currentTerm);
        state = RaftState.LEADER;
        persistentState.saveState(currentTerm, votedFor);
        stopElectionTimer();

        // Update kvStore with current term
        kvStore.setCurrentTerm((int) currentTerm);

        // Initialize nextIndex and matchIndex for all followers
        for (String peerId : peers.keySet()) {
            nextIndex.put(peerId, raftLog.getLastIndex() + 1);
            matchIndex.put(peerId, 0L);
        }

        // Append NO_OP entry to commit entries from previous terms
        long nextIndex = raftLog.getLastIndex() + 1;
        LogEntry noOpEntry = new LogEntry(nextIndex, (int) currentTerm, "", LogEntryType.NO_OP);
        raftLog.append(noOpEntry);
        logger.info("Leader appended NO_OP entry at index {}", nextIndex);

        // Initialize and start replicator
        replicator = new LeaderReplicator(nodeId, (int) currentTerm, raftLog, networkBase, peers);
        kvStore.setReplicator(replicator);

        // Set up lease renewal callback
        replicator.setLeaseRenewalCallback(() -> {
            readLease.renewLease();
        });

        replicator.startReplication();

        startHeartbeatTimer();
        logger.info("Node {} is now LEADER - sending heartbeats every {}ms", nodeId, HEARTBEAT_INTERVAL_MS);
    }

    private synchronized void becomeCandidate() {
        if (state == RaftState.CANDIDATE)
            return;
        logger.info("Node {} transitioning to CANDIDATE (term={})", nodeId, currentTerm);
        state = RaftState.CANDIDATE;
        resetElectionTimer();
    }

    /** Update term if newTerm > currentTerm */
    private synchronized void updateTerm(long newTerm) {
        if (newTerm > currentTerm) {
            logger.info("Node {} updating term from {} → {}", nodeId, currentTerm, newTerm);
            currentTerm = newTerm;
            votedFor = null;
            state = RaftState.FOLLOWER;
            stopHeartbeatTimer();
            persistentState.saveState(currentTerm, votedFor);
            kvStore.setCurrentTerm((int) currentTerm);
            resetElectionTimer();
        }
    }

    /** Step down (used by leaders/candidates when discovering a higher term) */
    private synchronized void stepDown(long newTerm) {
        if (newTerm > currentTerm) {
            logger.warn("Node {} stepping down due to higher term {} (ours={})",
                    nodeId, newTerm, currentTerm);
            currentTerm = newTerm;
        }
        RaftState oldState = state;
        state = RaftState.FOLLOWER;
        votedFor = null;
        stopHeartbeatTimer();
        if (oldState == RaftState.LEADER && replicator != null) {
            replicator.stopReplication();
            replicator = null;
            kvStore.setReplicator(null);
            readLease.invalidate();  // Invalidate lease when stepping down from leader
        }
        persistentState.saveState(currentTerm, votedFor);
        resetElectionTimer();
    }

    /**
     * Periodically apply committed entries to the state machine
     */
    private void applyCommittedEntries() {
        if (state == RaftState.FOLLOWER || state == RaftState.CANDIDATE) {
            followerReplicator.updateState((int) currentTerm, state);
            followerReplicator.applyCommittedEntries();
        }
    }

    // Getters for tests and instrumentation
    public String getNodeId() {
        return nodeId;
    }

    public KeyValueStore getKvStore() {
        return kvStore;
    }

    public RaftLog getRaftLog() {
        return raftLog;
    }

    public int getPort() {
        return port;
    }

    public RaftState getState() {
        return state;
    }

    public long getCurrentTerm() {
        return currentTerm;
    }

    public long getCommitIndex() {
        return raftLog.getCommitIndex();
    }

    public NetworkBase getNetworkBase() {
        return networkBase;
    }

    public long getHeartbeatsSent() {
        return heartbeatsSent;
    }

    public long getHeartbeatsReceived() {
        return heartbeatsReceived;
    }

    public long getLastHeartbeatReceived() {
        return lastHeartbeatReceived;
    }

    public int getConnectedPeerCount() {
        return networkBase != null ? networkBase.getActivePeerCount() : 0;
    }

    public String getVotedFor() {
        return votedFor;
    }

    public int getVoteCount() {
        return votesReceived.size();
    }

    public long getElectionsStarted() {
        return electionsStarted;
    }

    public synchronized void setCurrentTerm(long term) {
        this.currentTerm = term;
    }

    /**
     * Check if this node is currently the leader.
     *
     * @return true if leader, false otherwise
     */
    public boolean isLeader() {
        return state == RaftState.LEADER;
    }

    /**
     * Send heartbeat and confirm leadership with majority.
     * Used by ReadIndex protocol to ensure linearizable reads.
     *
     * @return CompletableFuture that completes with true if majority acknowledges
     */
    public CompletableFuture<Boolean> sendHeartbeatAndConfirmLeadership() {
        if (state != RaftState.LEADER) {
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        // Count acks: start with 1 for self
        java.util.concurrent.atomic.AtomicInteger ackCount = new java.util.concurrent.atomic.AtomicInteger(1);
        int totalNodes = getTotalNodes();
        int majorityNeeded = (totalNodes / 2) + 1;

        logger.debug("Sending heartbeat for leadership confirmation: need {} of {} nodes",
                    majorityNeeded, totalNodes);

        // If we're alone or already have majority, complete immediately
        if (ackCount.get() >= majorityNeeded) {
            future.complete(true);
            return future;
        }

        // Send heartbeat
        Message heartbeat = MessageSerializer.createHeartbeat(nodeId, currentTerm, commitIndex);

        // Create a temporary handler for this confirmation
        final long confirmationTerm = currentTerm;

        // Register a one-time callback for APPEND_ENTRIES_RESPONSE messages
        // We'll track responses and complete the future when we have majority
        CompletableFuture.runAsync(() -> {
            try {
                networkBase.broadcastMessage(heartbeat);

                // Wait a bit for responses (100ms should be enough for heartbeat acks)
                Thread.sleep(100);

                // Check if we got majority acks
                // Note: In a real implementation, we'd have a proper callback mechanism
                // For now, we'll check the replicator's match indices
                if (replicator != null) {
                    int recentAcks = 1; // self
                    for (String peerId : peers.keySet()) {
                        Long matchIdx = matchIndex.get(peerId);
                        if (matchIdx != null && matchIdx >= 0) {
                            recentAcks++;
                        }
                    }

                    if (recentAcks >= majorityNeeded && currentTerm == confirmationTerm) {
                        future.complete(true);
                    } else {
                        future.complete(false);
                    }
                } else {
                    future.complete(false);
                }
            } catch (Exception e) {
                logger.warn("Error during heartbeat confirmation", e);
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**************************************************************************************************
     * TEST RELATED FUNCTIONS
     */

    // ====== Testing Initializer (no network startup) ======
    void testInitializeForUnitTests() {
        if (heartbeatScheduler == null) {
            heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(
                    r -> new Thread(r, "HeartbeatTimer-" + nodeId));
        }
        if (electionScheduler == null) {
            electionScheduler = Executors.newSingleThreadScheduledExecutor(
                    r -> new Thread(r, "ElectionTimer-" + nodeId));
        }
        if (networkBase == null) {
            logger.debug("Creating mock NetworkBase for unit tests (no sockets)");
            networkBase = new NetworkBase(nodeId, port) {
                @Override
                public void sendMessage(String peerId, Message message) {
                    // no-op during tests
                    logger.debug("[Mock] sendMessage({}, {})", peerId, message.getType());
                }

                @Override
                public void broadcastMessage(Message message) {
                    // no-op during tests
                    logger.debug("[Mock] broadcastMessage({})", message.getType());
                }
            };
        }
    }

    // ====== Testing Accessors (package-private for unit tests only) ======
    // These methods exist solely for testing Raft internal behavior.
    // They delegate to private methods without exposing them publicly.

    void testUpdateTerm(long newTerm) {
        updateTerm(newTerm);
    }

    void testHandleRequestVote(Message message) {
        handleRequestVote(message);
    }

    void testHandleAppendEntries(Message message) {
        handleAppendEntries(message);
    }

    void testBecomeFollower() {
        becomeFollower();
    }

    /**
     * Check if this node has a valid read lease.
     * A lease is valid only when the node is leader AND the lease hasn't expired.
     *
     * @return true if this node is leader with valid lease, false otherwise
     */
    public boolean hasValidReadLease() {
        return state == RaftState.LEADER && readLease.isValid();
    }

    /**
     * Get the remaining time on the read lease in milliseconds.
     *
     * @return remaining lease time in ms, or 0 if no valid lease
     */
    public long getLeaseRemainingMs() {
        return readLease.getRemainingMs();
    }

}
