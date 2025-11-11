package com.distributed.cache.raft;

import com.distributed.cache.network.NetworkBase;
import com.distributed.cache.network.MessageSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * Represents a single node in the Raft cluster.
 *
 * This class implements the core Raft consensus algorithm including:
 * - Heartbeat mechanism (Person B's responsibility)
 * - Leader election (Person A's responsibility)
 * - State management (FOLLOWER, CANDIDATE, LEADER)
 *
 * Thread Safety:
 * - Uses synchronized methods for state changes
 * - Timers run on separate threads
 * - NetworkBase handles thread-safe message sending
 */
public class RaftNode {
    private static final Logger logger = LoggerFactory.getLogger(RaftNode.class);

    // ========== Timing Constants (from Raft paper) ==========

    /**
     * Heartbeat interval: Leaders send heartbeats every 50ms
     * Why 50ms? Must be much less than election timeout to prevent false failures
     */
    private static final long HEARTBEAT_INTERVAL_MS = 50;

    /**
     * Election timeout range: 150-300ms (randomized)
     * Why randomized? Prevents split votes - first node to timeout usually wins
     * Why 150-300ms? Long enough to receive multiple heartbeats, short enough to detect failures quickly
     */
    private static final long ELECTION_TIMEOUT_MIN_MS = 150;
    private static final long ELECTION_TIMEOUT_MAX_MS = 300;

    // ========== Node Identity ==========

    private final String nodeId;
    private final int port;

    // ========== Raft Persistent State (should be saved to disk in production) ==========

    /**
     * Current term number (monotonically increasing)
     * Used to detect stale leaders and ensure safety
     */
    private volatile long currentTerm = 0;

    /**
     * CandidateId that received vote in current term (or null if none)
     * Ensures each node votes at most once per term
     */
    private volatile String votedFor = null;

    // ========== Raft Volatile State (all servers) ==========

    /**
     * Current state: FOLLOWER, CANDIDATE, or LEADER
     */
    private volatile RaftState state;

    /**
     * Index of highest log entry known to be committed
     * (For Week 2, for now just track it)
     */
    private volatile long commitIndex = 0;

    /**
     * Index of highest log entry applied to state machine
     * (For Week 2, for now just track it)
     */
    private volatile long lastApplied = 0;

    // ========== Leader State (reinitialized after election) ==========

    /**
     * For each server, index of next log entry to send to that server
     * (For Week 2, for now not used)
     */
    private Map<String, Long> nextIndex = new ConcurrentHashMap<>();

    /**
     * For each server, index of highest log entry known to be replicated on server
     * (For Week 2, for now not used)
     */
    private Map<String, Long> matchIndex = new ConcurrentHashMap<>();

    // ========== Network Layer ==========

    private NetworkBase networkBase;

    // ========== Cluster Configuration ==========

    /**
     * All peer nodes in the cluster (nodeId -> "host:port")
     * Example: {"node2" -> "localhost:8002", "node3" -> "localhost:8003"}
     */
    private final Map<String, String> peers = new ConcurrentHashMap<>();

    // ========== Timers ==========

    /**
     * Executor for heartbeat timer (leaders only)
     * Sends heartbeats every HEARTBEAT_INTERVAL_MS
     */
    private ScheduledExecutorService heartbeatScheduler;

    /**
     * Future for the currently scheduled heartbeat task
     * Allows us to cancel when stepping down from leader
     */
    private ScheduledFuture<?> heartbeatTask;

    /**
     * Executor for election timer (followers/candidates)
     * Triggers election if no heartbeat received
     */
    private ScheduledExecutorService electionScheduler;

    /**
     * Future for the currently scheduled election timeout
     * Allows us to cancel/reschedule when receiving heartbeats
     */
    private ScheduledFuture<?> electionTask;

    /**
     * Random number generator for election timeout
     * Each node gets different timeout to prevent split votes
     */
    private final Random random = new Random();

    // ========== Statistics (useful for debugging) ==========

    private volatile long lastHeartbeatReceived = 0;
    private volatile long heartbeatsSent = 0;
    private volatile long heartbeatsReceived = 0;

    // ========== Constructor ==========

    public RaftNode(String nodeId, int port) {
        this.nodeId = nodeId;
        this.port = port;
        this.state = RaftState.FOLLOWER;
        logger.info("RaftNode initialized: id={}, port={}, initial_term={}, initial_state={}",
                    nodeId, port, currentTerm, state);
    }
    
    // ========== Lifecycle Methods ==========

    /**
     * Configure cluster peers before starting
     * @param peerMap Map of peerId -> "host:port"
     */
    public void configurePeers(Map<String, String> peerMap) {
        peers.putAll(peerMap);
        logger.info("Configured {} peers: {}", peerMap.size(), peerMap.keySet());
    }

    /**
     * Add a single peer to the cluster
     */
    public void addPeer(String peerId, String host, int port) {
        String address = host + ":" + port;
        peers.put(peerId, address);
        logger.info("Added peer: {} at {}", peerId, address);
    }

    /**
     * Start the Raft node
     * This initializes the network layer and starts the election timer
     */
    public void start() throws InterruptedException {
        logger.info("Starting Raft node: {}", nodeId);

        // Initialize network layer
        networkBase = new NetworkBase(nodeId, port);
        networkBase.startServer();

        // Register message handlers for heartbeat messages (Person B's responsibility)
        networkBase.registerMessageHandler(Message.MessageType.APPEND_ENTRIES, this::handleAppendEntries);
        networkBase.registerMessageHandler(Message.MessageType.APPEND_ENTRIES_RESPONSE, this::handleAppendEntriesResponse);

        // Note: Person A will add vote handlers here:
        // networkBase.registerMessageHandler(Message.MessageType.REQUEST_VOTE, this::handleRequestVote);
        // networkBase.registerMessageHandler(Message.MessageType.REQUEST_VOTE_RESPONSE, this::handleRequestVoteResponse);

        // Connect to all peers
        for (Map.Entry<String, String> peer : peers.entrySet()) {
            String peerId = peer.getKey();
            String[] parts = peer.getValue().split(":");
            String host = parts[0];
            int peerPort = Integer.parseInt(parts[1]);
            networkBase.connectToPeer(peerId, host, peerPort);
        }

        // Wait a moment for connections to establish
        // Connections happen asynchronously, so we need to give them time
        if (!peers.isEmpty()) {
            Thread.sleep(200);
        }

        // Initialize timer schedulers
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HeartbeatTimer-" + nodeId);
            t.setDaemon(true);
            return t;
        });

        electionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ElectionTimer-" + nodeId);
            t.setDaemon(true);
            return t;
        });

        // Start as follower with election timer
        resetElectionTimer();

        logger.info("Node {} started successfully. State={}, Term={}", nodeId, state, currentTerm);
    }

    /**
     * Shutdown the Raft node gracefully
     */
    public void shutdown() {
        logger.info("Shutting down Raft node: {}", nodeId);

        // Stop timers
        stopHeartbeatTimer();
        stopElectionTimer();

        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdown();
        }
        if (electionScheduler != null) {
            electionScheduler.shutdown();
        }

        // Close network connections
        if (networkBase != null) {
            networkBase.shutdown();
        }

        logger.info("Node {} shutdown complete", nodeId);
    }

    // ========== Heartbeat Timer (Person B's Core Responsibility) ==========

    /**
     * Start sending periodic heartbeats (leaders only)
     * Called when a node becomes leader
     *
     * How it works:
     * - Schedules a task to run every HEARTBEAT_INTERVAL_MS (50ms)
     * - Task calls sendHeartbeats() which broadcasts to all followers
     * - Runs until stopped (when stepping down from leader)
     */
    private void startHeartbeatTimer() {
        // Stop any existing heartbeat timer
        stopHeartbeatTimer();

        logger.info("Starting heartbeat timer for leader {}", nodeId);

        // Schedule heartbeat task to run every 50ms
        heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(
                this::sendHeartbeats,           // Task to run
                0,                               // Initial delay (send immediately)
                HEARTBEAT_INTERVAL_MS,           // Period (50ms)
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * Stop sending heartbeats
     * Called when a leader steps down
     */
    private void stopHeartbeatTimer() {
        if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
            heartbeatTask.cancel(false);
            logger.debug("Stopped heartbeat timer for {}", nodeId);
        }
    }

    /**
     * Send heartbeats to all followers
     * This is the actual heartbeat implementation!
     *
     * Called every 50ms by the heartbeat timer when this node is leader
     *
     * Heartbeat = AppendEntries RPC with no log entries
     */
    private void sendHeartbeats() {
        // Safety check: only leaders send heartbeats
        if (state != RaftState.LEADER) {
            logger.warn("Non-leader {} tried to send heartbeats. State={}", nodeId, state);
            return;
        }

        // Create heartbeat message (AppendEntries with no entries)
        Message heartbeat = MessageSerializer.createHeartbeat(nodeId, currentTerm, commitIndex);

        // Broadcast to all peers
        networkBase.broadcastMessage(heartbeat);

        heartbeatsSent++;
        logger.debug("Leader {} sent heartbeat #{} to {} peers (term={})",
                nodeId, heartbeatsSent, peers.size(), currentTerm);
    }

    // ========== Election Timer (Person B's Core Responsibility) ==========

    /**
     * Reset the election timer
     * Called when:
     * - Node starts as follower
     * - Follower receives valid heartbeat from leader
     * - Candidate starts election
     *
     * This is CRITICAL for heartbeat mechanism!
     * Receiving a heartbeat → reset this timer → prevents election
     */
    private synchronized void resetElectionTimer() {
        // Cancel existing timer
        stopElectionTimer();

        // Calculate random timeout (150-300ms)
        long timeout = ELECTION_TIMEOUT_MIN_MS +
                random.nextInt((int) (ELECTION_TIMEOUT_MAX_MS - ELECTION_TIMEOUT_MIN_MS));

        logger.trace("Resetting election timer for {} with timeout {}ms", nodeId, timeout);

        // Schedule election timeout
        electionTask = electionScheduler.schedule(
                this::onElectionTimeout,
                timeout,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * Stop the election timer
     * Called when becoming leader (leaders don't need election timer)
     */
    private void stopElectionTimer() {
        if (electionTask != null && !electionTask.isCancelled()) {
            electionTask.cancel(false);
            logger.trace("Stopped election timer for {}", nodeId);
        }
    }

    /**
     * Called when election timer expires (no heartbeat received in time)
     * This triggers a new election
     *
     * For Week 1: We'll just log that we detected failure
     * Person A will implement the actual election logic here
     */
    private void onElectionTimeout() {
        logger.warn("Election timeout fired for {} (no heartbeat received). State={}, Term={}",
                nodeId, state, currentTerm);

        // Calculate how long since last heartbeat
        long timeSinceLastHeartbeat = System.currentTimeMillis() - lastHeartbeatReceived;
        logger.warn("Time since last heartbeat: {}ms", timeSinceLastHeartbeat);

        // TODO: Person A will add election logic here
        // For now, just reset timer (prevents log spam)
        if (state == RaftState.FOLLOWER) {
            logger.info("Follower {} detected leader failure, would start election here", nodeId);
            // Person A will implement: startElection();
            resetElectionTimer(); // For now, just reset
        }
    }

    // ========== Message Handlers (Person B's Core Responsibility) ==========

    /**
     * Handle incoming AppendEntries RPC (heartbeat or log replication)
     *
     * This is called when we receive a heartbeat from the leader
     *
     * Raft Rules (from paper):
     * 1. Reply false if term < currentTerm
     * 2. Reply false if log doesn't contain an entry at prevLogIndex whose term matches prevLogTerm
     * 3. If an existing entry conflicts with a new one (same index but different terms),
     *    delete the existing entry and all that follow it
     * 4. Append any new entries not already in the log
     * 5. If leaderCommit > commitIndex, set commitIndex = min(leaderCommit, index of last new entry)
     *
     * For Week 1 (just heartbeat): We only implement rules 1 and 5
     */
    private synchronized void handleAppendEntries(Message message) {
        logger.debug("Node {} received AppendEntries from {} (term={}, our_term={})",
                nodeId, message.getLeaderId(), message.getTerm(), currentTerm);

        heartbeatsReceived++;
        lastHeartbeatReceived = System.currentTimeMillis();

        boolean success = true;
        String responseReason = "accepted";

        // Rule 1: Reply false if term < currentTerm
        if (message.getTerm() < currentTerm) {
            success = false;
            responseReason = "stale term";
            logger.debug("Rejecting AppendEntries from {} - stale term ({} < {})",
                    message.getLeaderId(), message.getTerm(), currentTerm);
        } else {
            // Valid heartbeat from current or newer term

            // If term is higher, update our term and step down
            if (message.getTerm() > currentTerm) {
                logger.info("Node {} discovered higher term {} (was {}), stepping down",
                        nodeId, message.getTerm(), currentTerm);
                currentTerm = message.getTerm();
                votedFor = null; // New term, clear vote
                becomeFollower();
            }

            // We have a valid leader, reset election timer
            // This is THE KEY to heartbeat mechanism!
            resetElectionTimer();

            // If we were a candidate, step down (we have a leader now)
            if (state == RaftState.CANDIDATE) {
                logger.info("Candidate {} stepping down - received heartbeat from leader {}",
                        nodeId, message.getLeaderId());
                becomeFollower();
            }

            // Update commit index if leader's is higher
            if (message.getLeaderCommit() > commitIndex) {
                long oldCommitIndex = commitIndex;
                // For now, just update to leader's commit index
                // In Week 2, we'll check our log length
                commitIndex = message.getLeaderCommit();
                logger.debug("Updated commitIndex from {} to {}", oldCommitIndex, commitIndex);
            }
        }

        // Send response
        Message response = MessageSerializer.createHeartbeatResponse(nodeId, currentTerm, success);
        networkBase.sendMessage(message.getSenderId(), response);

        logger.trace("Node {} responded to heartbeat from {} with success={} ({})",
                nodeId, message.getLeaderId(), success, responseReason);
    }

    /**
     * Handle AppendEntries response (for leaders)
     * Called when a follower responds to our heartbeat
     *
     * For Week 1: Just track that followers are alive
     * For Week 2: We'll use this for log replication
     */
    private synchronized void handleAppendEntriesResponse(Message message) {
        // Only leaders care about responses
        if (state != RaftState.LEADER) {
            return;
        }

        logger.trace("Leader {} received heartbeat response from {} (success={}, term={})",
                nodeId, message.getSenderId(), message.isSuccess(), message.getTerm());

        // If response has higher term, step down
        if (message.getTerm() > currentTerm) {
            logger.warn("Leader {} discovered higher term {} from {}, stepping down",
                    nodeId, message.getTerm(), message.getSenderId());
            currentTerm = message.getTerm();
            votedFor = null;
            becomeFollower();
            return;
        }

        // For Week 1: Just log that follower is responsive
        if (message.isSuccess()) {
            logger.trace("Follower {} acknowledged heartbeat", message.getSenderId());
        } else {
            logger.debug("Follower {} rejected heartbeat (term={})",
                    message.getSenderId(), message.getTerm());
        }

        // Week 2 will add: Update matchIndex, nextIndex for log replication
    }

    // ========== State Transition Methods ==========

    /**
     * Transition to FOLLOWER state
     * Called when:
     * - Node starts
     * - Receives heartbeat/vote request with higher term
     * - Loses election
     */
    private synchronized void becomeFollower() {
        if (state == RaftState.FOLLOWER) {
            return; // Already a follower
        }

        logger.info("Node {} transitioning to FOLLOWER (term={})", nodeId, currentTerm);

        RaftState oldState = state;
        state = RaftState.FOLLOWER;

        // If we were leader, stop sending heartbeats
        if (oldState == RaftState.LEADER) {
            stopHeartbeatTimer();
        }

        // Start/reset election timer
        resetElectionTimer();
    }

    /**
     * Transition to LEADER state
     * Called when winning an election (Person A will implement this)
     *
     * For testing, we'll provide a method to manually become leader
     */
    public synchronized void becomeLeader() {
        if (state == RaftState.LEADER) {
            return; // Already leader
        }

        logger.info("Node {} transitioning to LEADER (term={})", nodeId, currentTerm);

        state = RaftState.LEADER;

        // Leaders don't need election timer
        stopElectionTimer();

        // Initialize leader state (for Week 2)
        for (String peerId : peers.keySet()) {
            nextIndex.put(peerId, 0L);
            matchIndex.put(peerId, 0L);
        }

        // Start sending heartbeats immediately
        startHeartbeatTimer();

        logger.info("Node {} is now LEADER - sending heartbeats every {}ms",
                nodeId, HEARTBEAT_INTERVAL_MS);
    }

    /**
     * Transition to CANDIDATE state (Person A will implement this)
     * Included for completeness
     */
    private synchronized void becomeCandidate() {
        logger.info("Node {} transitioning to CANDIDATE (term={})", nodeId, currentTerm);
        state = RaftState.CANDIDATE;
        // Person A will add: Increment term, vote for self, send RequestVote RPCs
    }

    // ========== Getters ==========

    public String getNodeId() {
        return nodeId;
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
        return commitIndex;
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

    /**
     * Get number of connected peers
     */
    public int getConnectedPeerCount() {
        return networkBase != null ? networkBase.getActivePeerCount() : 0;
    }

    // ========== For Testing ==========

    /**
     * Manually set term (for testing only)
     */
    public synchronized void setCurrentTerm(long term) {
        this.currentTerm = term;
    }
}
