package com.distributed.cache.raft;

import com.distributed.cache.replication.RaftLog;
import com.distributed.cache.store.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * SnapshotManager handles the creation and restoration of snapshots.
 * Snapshots are created when the log size exceeds SNAPSHOT_THRESHOLD.
 */
public class SnapshotManager {
    private static final Logger logger = LoggerFactory.getLogger(SnapshotManager.class);
    private static final long SNAPSHOT_THRESHOLD = 1000;

    private final String nodeId;
    private final RaftLog raftLog;
    private final KeyValueStore kvStore;
    private int currentTerm;

    public SnapshotManager(String nodeId, RaftLog raftLog, KeyValueStore kvStore) {
        this.nodeId = nodeId;
        this.raftLog = raftLog;
        this.kvStore = kvStore;
        this.currentTerm = 0;
    }

    /**
     * Update the current term (called by RaftNode when term changes)
     */
    public void setCurrentTerm(int currentTerm) {
        this.currentTerm = currentTerm;
    }

    /**
     * Check if a snapshot should be created and create it if necessary.
     * Should be called periodically or after significant log growth.
     *
     * @return true if a snapshot was created, false otherwise
     */
    public boolean checkAndCreateSnapshot() {
        long logSize = raftLog.getLogSize();

        if (logSize >= SNAPSHOT_THRESHOLD) {
            logger.info("Log size {} exceeds threshold {}, creating snapshot",
                       logSize, SNAPSHOT_THRESHOLD);
            return createSnapshot();
        }

        return false;
    }

    /**
     * Create a snapshot of the current state.
     *
     * @return true if snapshot was created successfully, false otherwise
     */
    public boolean createSnapshot() {
        try {
            long lastApplied = raftLog.getLastApplied();
            if (lastApplied <= 0) {
                logger.debug("No entries applied yet, skipping snapshot");
                return false;
            }

            // Get the term of the last applied entry
            LogEntry lastEntry = raftLog.getEntry(lastApplied);
            int lastTerm = (lastEntry != null) ? lastEntry.getTerm() : currentTerm;

            // Get the current state from KeyValueStore
            Map<String, String> data = kvStore.getAllData();
            Map<String, Long> lastAppliedSequence = kvStore.getLastAppliedSequence();

            // Create snapshot
            Snapshot snapshot = new Snapshot(lastApplied, lastTerm, data, lastAppliedSequence);

            // Save to disk
            if (!snapshot.saveToDisk(nodeId)) {
                logger.error("Failed to save snapshot to disk");
                return false;
            }

            // Compact the log - delete entries up to lastApplied
            int deletedCount = raftLog.deleteEntriesUpTo(lastApplied);
            logger.info("Snapshot created: lastIndex={}, term={}, dataSize={}, deletedEntries={}",
                       lastApplied, lastTerm, data.size(), deletedCount);

            return true;

        } catch (Exception e) {
            logger.error("Failed to create snapshot", e);
            return false;
        }
    }

    /**
     * Load the latest snapshot from disk and restore the state.
     *
     * @return The loaded snapshot, or null if no snapshot exists
     */
    public Snapshot loadLatestSnapshot() {
        Snapshot snapshot = Snapshot.loadFromDisk(nodeId);

        if (snapshot == null) {
            logger.info("No snapshot found for node {}", nodeId);
            return null;
        }

        try {
            // Restore the state to KeyValueStore
            kvStore.restoreFromSnapshot(snapshot.getData(), snapshot.getLastAppliedSequence());

            // Update the log's lastApplied index
            raftLog.setLastApplied(snapshot.getLastIncludedIndex());

            logger.info("Snapshot restored: lastIndex={}, term={}, dataSize={}",
                       snapshot.getLastIncludedIndex(),
                       snapshot.getLastIncludedTerm(),
                       snapshot.getData().size());

            return snapshot;

        } catch (Exception e) {
            logger.error("Failed to restore from snapshot", e);
            return null;
        }
    }

    /**
     * Get the current snapshot threshold.
     */
    public static long getSnapshotThreshold() {
        return SNAPSHOT_THRESHOLD;
    }
}
