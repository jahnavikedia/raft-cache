package com.distributed.cache.raft;

import com.distributed.cache.network.NetworkBase;
import com.distributed.cache.network.MessageSerializer;
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

    private volatile long lastHeartbeatReceived = 0;
    private volatile long heartbeatsSent = 0;
    private volatile long heartbeatsReceived = 0;
    private volatile long electionsStarted = 0;

    public RaftNode(String nodeId, int port) {
        this.nodeId = nodeId;
        this.port = port;
        this.state = RaftState.FOLLOWER;
        this.lastHeartbeatReceived = System.currentTimeMillis();
        logger.info("RaftNode initialized: id={}, port={}, initial_term={}, initial_state={}",
                nodeId, port, currentTerm, state);
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

        // 7. Start as follower with a normal randomized election timeout
        resetElectionTimer(); // FIX: was resetElectionTimer(ELECTION_TIMEOUT_MAX_MS * 3L);
        logger.info("Node {} started successfully. State={}, Term={}", nodeId, state, currentTerm);
    }

    public void shutdown() {
        logger.info("Shutting down Raft node: {}", nodeId);

        stopHeartbeatTimer();
        stopElectionTimer();

        if (heartbeatScheduler != null)
            heartbeatScheduler.shutdown();
        if (electionScheduler != null)
            electionScheduler.shutdown();

        if (networkBase != null)
            networkBase.shutdown();
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
        electionsStarted++;
        becomeCandidate();
        votedFor = nodeId;
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

        logger.debug("Node {} received RequestVote from {} for term {} (current term: {})", nodeId, candidateId,
                requestTerm, currentTerm);

        Message response = new Message(Message.MessageType.REQUEST_VOTE_RESPONSE, currentTerm, nodeId);
        response.setSuccess(false);

        if (requestTerm > currentTerm) {
            logger.info("Node {} updating term from {} to {} (RequestVote from {})", nodeId, currentTerm, requestTerm,
                    candidateId);
            currentTerm = requestTerm;
            becomeFollower();
            votedFor = null;
            response.setTerm(requestTerm);
        }

        if (requestTerm == currentTerm) {
            if (votedFor == null || votedFor.equals(candidateId)) {
                response.setSuccess(true);
                votedFor = candidateId;
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
        String fromNodeId = response.getSenderId();
        long responseTerm = response.getTerm();
        boolean voteGranted = response.isSuccess();

        logger.debug("Node {} received vote response from {}: term={}, granted={}", nodeId, fromNodeId, responseTerm,
                voteGranted);

        if (state != RaftState.CANDIDATE)
            return;

        if (responseTerm > currentTerm) {
            logger.info("Node {} stepping down: higher term {} from {}", nodeId, responseTerm, fromNodeId);
            currentTerm = responseTerm;
            becomeFollower();
            votedFor = null;
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
        logger.debug("Node {} received AppendEntries from {} (term={}, our_term={})",
                nodeId, message.getLeaderId(), message.getTerm(), currentTerm);

        heartbeatsReceived++;
        lastHeartbeatReceived = System.currentTimeMillis();

        boolean success = true;
        String responseReason = "accepted";

        if (message.getTerm() < currentTerm) {
            success = false;
            responseReason = "stale term";
            logger.debug("Rejecting AppendEntries from {} - stale term ({} < {})", message.getLeaderId(),
                    message.getTerm(), currentTerm);
        } else {
            if (message.getTerm() > currentTerm) {
                logger.info("Node {} discovered higher term {} (was {}), stepping down", nodeId, message.getTerm(),
                        currentTerm);
                currentTerm = message.getTerm();
                votedFor = null;
                becomeFollower();
            }
            resetElectionTimer();
            if (state == RaftState.CANDIDATE) {
                logger.info("Candidate {} stepping down - received heartbeat from leader {}", nodeId,
                        message.getLeaderId());
                becomeFollower();
            }

            if (message.getLeaderCommit() > commitIndex) {
                long oldCommitIndex = commitIndex;
                commitIndex = message.getLeaderCommit();
                logger.debug("Updated commitIndex from {} to {}", oldCommitIndex, commitIndex);
            }
        }

        Message response = MessageSerializer.createHeartbeatResponse(nodeId, currentTerm, success);
        networkBase.sendMessage(message.getSenderId(), response);

        logger.trace("Node {} responded to heartbeat from {} with success={} ({})", nodeId, message.getLeaderId(),
                success, responseReason);
    }

    private synchronized void handleAppendEntriesResponse(Message message) {
        if (state != RaftState.LEADER)
            return;

        logger.trace("Leader {} received heartbeat response from {} (success={}, term={})", nodeId,
                message.getSenderId(), message.isSuccess(), message.getTerm());

        if (message.getTerm() > currentTerm) {
            logger.warn("Leader {} discovered higher term {} from {}, stepping down", nodeId, message.getTerm(),
                    message.getSenderId());
            currentTerm = message.getTerm();
            votedFor = null;
            becomeFollower();
            return;
        }

        if (message.isSuccess()) {
            logger.trace("Follower {} acknowledged heartbeat", message.getSenderId());
        } else {
            logger.debug("Follower {} rejected heartbeat (term={})", message.getSenderId(), message.getTerm());
        }
    }

    private synchronized void becomeFollower() {
        if (state == RaftState.FOLLOWER)
            return;
        logger.info("Node {} transitioning to FOLLOWER (term={})", nodeId, currentTerm);
        RaftState oldState = state;
        state = RaftState.FOLLOWER;
        if (oldState == RaftState.LEADER)
            stopHeartbeatTimer();
        resetElectionTimer();
    }

    public synchronized void becomeLeader() {
        if (state == RaftState.LEADER)
            return;
        logger.info("Node {} transitioning to LEADER (term={})", nodeId, currentTerm);
        state = RaftState.LEADER;
        stopElectionTimer();
        for (String peerId : peers.keySet()) {
            nextIndex.put(peerId, 0L);
            matchIndex.put(peerId, 0L);
        }
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

    // Getters for tests and instrumentation
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
}
