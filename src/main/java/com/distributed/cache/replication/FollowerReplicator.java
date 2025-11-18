package com.distributed.cache.replication;

import com.distributed.cache.raft.LogEntry;
import com.distributed.cache.raft.RaftState;
import com.distributed.cache.store.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FollowerReplicator handles log replication on follower nodes.
 *
 * It processes AppendEntries requests from the leader and applies
 * committed entries to the state machine.
 */
public class FollowerReplicator {
    private static final Logger logger = LoggerFactory.getLogger(FollowerReplicator.class);

    private final String nodeId;
    private final RaftLog raftLog;
    private final KeyValueStore kvStore;
    private int currentTerm;
    private RaftState currentState;

    /**
     * Create a new FollowerReplicator
     *
     * @param nodeId The node ID
     * @param raftLog The Raft log
     * @param kvStore The key-value store (state machine)
     */
    public FollowerReplicator(String nodeId, RaftLog raftLog, KeyValueStore kvStore) {
        this.nodeId = nodeId;
        this.raftLog = raftLog;
        this.kvStore = kvStore;

        logger.info("FollowerReplicator initialized for node {}", nodeId);
    }

    /**
     * Update the current term and state (called by RaftNode)
     */
    public void updateState(int currentTerm, RaftState currentState) {
        this.currentTerm = currentTerm;
        this.currentState = currentState;
    }

    /**
     * Handle an AppendEntries request from the leader
     *
     * @param request The AppendEntries request
     * @return The AppendEntries response
     */
    public AppendEntriesResponse handleAppendEntries(AppendEntriesRequest request) {
        logger.debug("Handling AppendEntries: {}", request);

        // 1. Reply false if request.term < currentTerm
        if (request.getTerm() < currentTerm) {
            logger.debug("Rejecting AppendEntries: stale term {} < {}", request.getTerm(), currentTerm);
            return new AppendEntriesResponse(currentTerm, false, 0, nodeId);
        }

        // 2. Check if log contains entry at prevLogIndex with term = prevLogTerm
        long prevLogIndex = request.getPrevLogIndex();
        int prevLogTerm = request.getPrevLogTerm();

        if (prevLogIndex > 0) {
            LogEntry prevEntry = raftLog.getEntry(prevLogIndex);

            // If log doesn't contain entry at prevLogIndex, reply false
            if (prevEntry == null) {
                logger.debug("Rejecting AppendEntries: missing entry at prevLogIndex {}", prevLogIndex);
                return new AppendEntriesResponse(currentTerm, false, 0, nodeId);
            }

            // If entry exists but has different term, reply false
            if (prevEntry.getTerm() != prevLogTerm) {
                logger.debug("Rejecting AppendEntries: term mismatch at prevLogIndex {} (expected {}, got {})",
                        prevLogIndex, prevLogTerm, prevEntry.getTerm());

                // 3. Delete conflicting entry and all that follow
                raftLog.deleteEntriesFrom(prevLogIndex);
                return new AppendEntriesResponse(currentTerm, false, 0, nodeId);
            }
        }

        // 4. Append any new entries not already in the log
        int entriesAppended = 0;
        for (LogEntry newEntry : request.getEntries()) {
            long entryIndex = newEntry.getIndex();
            LogEntry existingEntry = raftLog.getEntry(entryIndex);

            if (existingEntry != null) {
                // If existing entry conflicts with new one (different terms), delete it and all following
                if (existingEntry.getTerm() != newEntry.getTerm()) {
                    logger.info("Conflict at index {}: deleting entries from here", entryIndex);
                    raftLog.deleteEntriesFrom(entryIndex);
                    raftLog.append(newEntry);
                    entriesAppended++;
                }
                // If terms match, entry is already in log (skip)
            } else {
                // Entry is new, append it
                raftLog.append(newEntry);
                entriesAppended++;
            }
        }

        if (entriesAppended > 0) {
            logger.info("Appended {} new entries to log", entriesAppended);
        }

        // 5. If leaderCommit > commitIndex, set commitIndex = min(leaderCommit, index of last new entry)
        long leaderCommit = request.getLeaderCommit();
        long currentCommitIndex = raftLog.getCommitIndex();

        if (leaderCommit > currentCommitIndex) {
            long lastNewEntryIndex = raftLog.getLastIndex();
            long newCommitIndex = Math.min(leaderCommit, lastNewEntryIndex);
            raftLog.setCommitIndex(newCommitIndex);

            logger.debug("Updated commitIndex from {} to {} (leaderCommit={})",
                    currentCommitIndex, newCommitIndex, leaderCommit);
        }

        // 6. Return success with the highest matching index
        long matchIndex = raftLog.getLastIndex();
        return new AppendEntriesResponse(currentTerm, true, matchIndex, nodeId);
    }

    /**
     * Apply committed entries to the state machine.
     * This should be called periodically by the RaftNode.
     */
    public void applyCommittedEntries() {
        long lastApplied = raftLog.getLastApplied();
        long commitIndex = raftLog.getCommitIndex();

        if (commitIndex <= lastApplied) {
            // Nothing to apply
            return;
        }

        logger.debug("Applying entries from {} to {}", lastApplied + 1, commitIndex);

        // Apply each entry from lastApplied+1 to commitIndex
        for (long i = lastApplied + 1; i <= commitIndex; i++) {
            LogEntry entry = raftLog.getEntry(i);
            if (entry == null) {
                logger.error("Missing log entry at index {} (lastApplied={}, commitIndex={})",
                        i, lastApplied, commitIndex);
                break;
            }

            try {
                kvStore.applyCommand(entry);
                logger.debug("Applied entry {} to state machine", i);
            } catch (Exception e) {
                logger.error("Failed to apply entry {} to state machine", i, e);
            }
        }
    }
}
