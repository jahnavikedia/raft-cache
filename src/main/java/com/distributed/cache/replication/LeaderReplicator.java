package com.distributed.cache.replication;

import com.distributed.cache.network.NetworkBase;
import com.distributed.cache.raft.LogEntry;
import com.distributed.cache.raft.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * LeaderReplicator manages log replication from the leader to followers.
 *
 * For each follower, it tracks:
 * - nextIndex: the next log entry to send to that follower
 * - matchIndex: the highest log entry known to be replicated on that follower
 *
 * The replicator continuously sends AppendEntries RPCs to all followers.
 */
public class LeaderReplicator {
    private static final Logger logger = LoggerFactory.getLogger(LeaderReplicator.class);

    private static final long REPLICATION_INTERVAL_MS = 50;

    private final String nodeId;
    private final RaftLog raftLog;
    private final NetworkBase networkBase;
    private final int currentTerm;
    private final Map<String, String> peers;

    private final Map<String, Long> nextIndex;
    private final Map<String, Long> matchIndex;

    private ScheduledExecutorService replicationExecutor;
    private ScheduledFuture<?> replicationTask;

    /**
     * Create a new LeaderReplicator
     *
     * @param nodeId The leader's node ID
     * @param currentTerm The current term
     * @param raftLog The Raft log
     * @param networkBase The network manager
     * @param peers Map of peer IDs to addresses
     */
    public LeaderReplicator(String nodeId, int currentTerm, RaftLog raftLog,
                           NetworkBase networkBase, Map<String, String> peers) {
        this.nodeId = nodeId;
        this.currentTerm = currentTerm;
        this.raftLog = raftLog;
        this.networkBase = networkBase;
        this.peers = peers;

        this.nextIndex = new ConcurrentHashMap<>();
        this.matchIndex = new ConcurrentHashMap<>();

        // Initialize nextIndex and matchIndex for all followers
        long lastLogIndex = raftLog.getLastIndex();
        for (String peerId : peers.keySet()) {
            nextIndex.put(peerId, lastLogIndex + 1);
            matchIndex.put(peerId, 0L);
        }

        logger.info("LeaderReplicator initialized for term {} with {} followers", currentTerm, peers.size());
    }

    /**
     * Start replicating to all followers
     */
    public void startReplication() {
        if (replicationExecutor == null || replicationExecutor.isShutdown()) {
            replicationExecutor = Executors.newSingleThreadScheduledExecutor(
                    r -> new Thread(r, "ReplicationTimer-" + nodeId));
        }

        logger.info("Starting log replication to {} followers", peers.size());
        replicationTask = replicationExecutor.scheduleAtFixedRate(
                this::replicateToAllFollowers,
                0,
                REPLICATION_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * Stop replication (called when stepping down from leader)
     */
    public void stopReplication() {
        if (replicationTask != null && !replicationTask.isCancelled()) {
            replicationTask.cancel(false);
            logger.info("Stopped replication task");
        }

        if (replicationExecutor != null && !replicationExecutor.isShutdown()) {
            replicationExecutor.shutdown();
            logger.info("Shut down replication executor");
        }
    }

    /**
     * Replicate to all followers
     */
    private void replicateToAllFollowers() {
        for (String followerId : peers.keySet()) {
            replicateToFollower(followerId);
        }
        updateCommitIndex();
    }

    /**
     * Replicate to a specific follower
     */
    private void replicateToFollower(String followerId) {
        try {
            long followerNextIndex = nextIndex.getOrDefault(followerId, 1L);
            long prevLogIndex = followerNextIndex - 1;
            int prevLogTerm = 0;

            if (prevLogIndex > 0) {
                LogEntry prevEntry = raftLog.getEntry(prevLogIndex);
                if (prevEntry != null) {
                    prevLogTerm = prevEntry.getTerm();
                }
            }

            // Get entries to send
            List<LogEntry> entries = raftLog.getEntriesSince(followerNextIndex);

            // Create and send AppendEntries message with log entries
            Message message = new Message(Message.MessageType.APPEND_ENTRIES, currentTerm, nodeId);
            message.setLeaderId(nodeId);
            message.setPrevLogIndex(prevLogIndex);
            message.setPrevLogTerm(prevLogTerm);
            message.setLeaderCommit(raftLog.getCommitIndex());
            message.setEntries(entries); // Now we can set the entries!

            networkBase.sendMessage(followerId, message);

            if (entries.isEmpty()) {
                logger.trace("Sent heartbeat to follower {}", followerId);
            } else {
                logger.debug("Sent {} entries to follower {} (nextIndex={})",
                        entries.size(), followerId, followerNextIndex);
            }

        } catch (Exception e) {
            logger.error("Failed to replicate to follower {}", followerId, e);
        }
    }

    /**
     * Handle AppendEntries response from a follower
     */
    public synchronized void handleAppendEntriesResponse(AppendEntriesResponse response) {
        String followerId = response.getFollowerId();

        // Ignore responses from old terms
        if (response.getTerm() > currentTerm) {
            logger.warn("Received AppendEntries response with higher term {} (ours: {})",
                    response.getTerm(), currentTerm);
            // Leader should step down (handled by RaftNode)
            return;
        }

        if (response.isSuccess()) {
            // Update nextIndex and matchIndex
            long newMatchIndex = response.getMatchIndex();
            matchIndex.put(followerId, newMatchIndex);
            nextIndex.put(followerId, newMatchIndex + 1);

            logger.debug("Follower {} matched up to index {}", followerId, newMatchIndex);

            // Try to advance commit index
            updateCommitIndex();
        } else {
            // Log inconsistency - decrement nextIndex and retry
            long currentNextIndex = nextIndex.getOrDefault(followerId, 1L);
            if (currentNextIndex > 1) {
                nextIndex.put(followerId, currentNextIndex - 1);
                logger.debug("Follower {} rejected AppendEntries, decrementing nextIndex to {}",
                        followerId, currentNextIndex - 1);

                // Immediately retry with the decremented index
                replicateToFollower(followerId);
            }
        }
    }

    /**
     * Update commit index based on majority replication
     *
     * If there exists an N such that N > commitIndex, a majority of
     * matchIndex[i] >= N, and log[N].term == currentTerm, then set commitIndex = N
     */
    private synchronized void updateCommitIndex() {
        long currentCommitIndex = raftLog.getCommitIndex();
        long lastLogIndex = raftLog.getLastIndex();

        // Try each index from lastLogIndex down to currentCommitIndex + 1
        for (long n = lastLogIndex; n > currentCommitIndex; n--) {
            LogEntry entry = raftLog.getEntry(n);
            if (entry == null || entry.getTerm() != currentTerm) {
                continue; // Only commit entries from current term
            }

            // Count how many nodes have replicated this entry
            int replicatedCount = 1; // Leader always has it
            for (Long match : matchIndex.values()) {
                if (match >= n) {
                    replicatedCount++;
                }
            }

            // Check if majority has replicated
            int totalNodes = peers.size() + 1; // followers + leader
            int majority = (totalNodes / 2) + 1;

            if (replicatedCount >= majority) {
                raftLog.setCommitIndex(n);
                logger.info("Advanced commitIndex to {} (replicated on {}/{} nodes)",
                        n, replicatedCount, totalNodes);
                return;
            }
        }
    }

    /**
     * Get the next index for a follower (for testing/debugging)
     */
    public long getNextIndex(String followerId) {
        return nextIndex.getOrDefault(followerId, 0L);
    }

    /**
     * Get the match index for a follower (for testing/debugging)
     */
    public long getMatchIndex(String followerId) {
        return matchIndex.getOrDefault(followerId, 0L);
    }
}
