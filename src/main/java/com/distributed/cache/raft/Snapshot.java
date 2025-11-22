package com.distributed.cache.raft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Snapshot represents a point-in-time snapshot of the key-value store state.
 * Snapshots are used for log compaction and fast recovery.
 */
public class Snapshot implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(Snapshot.class);

    private final long lastIncludedIndex;
    private final int lastIncludedTerm;
    private final Map<String, String> data;
    private final Map<String, Long> lastAppliedSequence;
    private final long timestamp;

    public Snapshot(long lastIncludedIndex, int lastIncludedTerm,
                   Map<String, String> data, Map<String, Long> lastAppliedSequence) {
        this.lastIncludedIndex = lastIncludedIndex;
        this.lastIncludedTerm = lastIncludedTerm;
        this.data = data;
        this.lastAppliedSequence = lastAppliedSequence;
        this.timestamp = System.currentTimeMillis();
    }

    public long getLastIncludedIndex() {
        return lastIncludedIndex;
    }

    public int getLastIncludedTerm() {
        return lastIncludedTerm;
    }

    public Map<String, String> getData() {
        return data;
    }

    public Map<String, Long> getLastAppliedSequence() {
        return lastAppliedSequence;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Save this snapshot to disk.
     *
     * @param nodeId The node ID (used for directory name)
     * @return true if saved successfully, false otherwise
     */
    public boolean saveToDisk(String nodeId) {
        try {
            Path snapshotsDir = Paths.get("data", nodeId, "snapshots");
            Files.createDirectories(snapshotsDir);

            String filename = String.format("snapshot-%d-%d.dat", lastIncludedIndex, timestamp);
            Path snapshotPath = snapshotsDir.resolve(filename);

            try (FileOutputStream fos = new FileOutputStream(snapshotPath.toFile());
                 ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                oos.writeObject(this);
            }

            logger.info("Snapshot saved: {} (lastIndex={}, term={}, dataSize={})",
                       filename, lastIncludedIndex, lastIncludedTerm, data.size());
            return true;

        } catch (IOException e) {
            logger.error("Failed to save snapshot", e);
            return false;
        }
    }

    /**
     * Load the latest snapshot from disk for the given node.
     *
     * @param nodeId The node ID (used for directory name)
     * @return The latest snapshot, or null if no snapshot exists
     */
    public static Snapshot loadFromDisk(String nodeId) {
        try {
            Path snapshotsDir = Paths.get("data", nodeId, "snapshots");
            if (!Files.exists(snapshotsDir)) {
                logger.info("No snapshots directory found for node {}", nodeId);
                return null;
            }

            // Find the latest snapshot file
            File[] snapshotFiles = snapshotsDir.toFile().listFiles((dir, name) ->
                name.startsWith("snapshot-") && name.endsWith(".dat"));

            if (snapshotFiles == null || snapshotFiles.length == 0) {
                logger.info("No snapshot files found for node {}", nodeId);
                return null;
            }

            // Find the snapshot with the highest lastIncludedIndex
            File latestSnapshotFile = null;
            long maxIndex = -1;

            for (File file : snapshotFiles) {
                try {
                    String[] parts = file.getName().replace("snapshot-", "")
                                                  .replace(".dat", "")
                                                  .split("-");
                    long index = Long.parseLong(parts[0]);
                    if (index > maxIndex) {
                        maxIndex = index;
                        latestSnapshotFile = file;
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Skipping invalid snapshot file: {}", file.getName());
                }
            }

            if (latestSnapshotFile == null) {
                logger.info("No valid snapshot files found for node {}", nodeId);
                return null;
            }

            // Load the snapshot
            try (FileInputStream fis = new FileInputStream(latestSnapshotFile);
                 ObjectInputStream ois = new ObjectInputStream(fis)) {
                Snapshot snapshot = (Snapshot) ois.readObject();
                logger.info("Snapshot loaded: {} (lastIndex={}, term={}, dataSize={})",
                           latestSnapshotFile.getName(),
                           snapshot.lastIncludedIndex,
                           snapshot.lastIncludedTerm,
                           snapshot.data.size());
                return snapshot;
            }

        } catch (IOException | ClassNotFoundException e) {
            logger.error("Failed to load snapshot", e);
            return null;
        }
    }

    @Override
    public String toString() {
        return String.format("Snapshot{lastIndex=%d, term=%d, dataSize=%d, timestamp=%d}",
                           lastIncludedIndex, lastIncludedTerm, data.size(), timestamp);
    }
}
