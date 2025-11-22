package com.distributed.cache.store;

import com.distributed.cache.eviction.EvictionPolicy;
import com.distributed.cache.eviction.MLEvictionPolicy;
import com.distributed.cache.raft.LogEntry;
import com.distributed.cache.raft.LogEntryType;
import com.distributed.cache.raft.ReadIndexManager;
import com.distributed.cache.raft.RaftNode;
import com.distributed.cache.replication.LeaderReplicator;
import com.distributed.cache.replication.RaftLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Key-value store state machine that integrates with Raft consensus.
 *
 * Writes (PUT/DELETE) go through Raft consensus and are applied after being committed.
 * Reads (GET) are served locally from the in-memory map.
 */
public class KeyValueStore {
    private static final Logger logger = LoggerFactory.getLogger(KeyValueStore.class);
    private static final int MAX_CACHE_SIZE = 1000;  // Maximum cache entries before eviction

    private final ConcurrentHashMap<String, String> data;
    private final Map<String, Long> lastAppliedSequence;
    private final RaftLog raftLog;
    private LeaderReplicator replicator;
    private final String nodeId;
    private int currentTerm;
    private final AccessTracker accessTracker;
    private ReadIndexManager readIndexManager;
    private RaftNode raftNode;  // For checking read lease
    private final ScheduledExecutorService scheduler;
    private EvictionPolicy evictionPolicy;  // Cache eviction strategy
    private long evictionCount = 0;  // Track total evictions

    public KeyValueStore(String nodeId, RaftLog raftLog) {
        this.nodeId = nodeId;
        this.data = new ConcurrentHashMap<>();
        this.lastAppliedSequence = new ConcurrentHashMap<>();
        this.raftLog = raftLog;
        this.replicator = null;
        this.currentTerm = 0;
        this.accessTracker = new AccessTracker();
        this.accessTracker.start();
        this.scheduler = Executors.newScheduledThreadPool(1);
        // readIndexManager will be set after RaftNode is fully initialized

        // Initialize ML-based eviction policy
        this.evictionPolicy = new MLEvictionPolicy(accessTracker);

        logger.info("KeyValueStore initialized for node {} with ML-based eviction (max size: {})",
                nodeId, MAX_CACHE_SIZE);
    }

    /**
     * Update the current term (called by RaftNode when term changes)
     */
    public void setCurrentTerm(int currentTerm) {
        this.currentTerm = currentTerm;
    }

    /**
     * Set the leader replicator (called when node becomes leader)
     */
    public void setReplicator(LeaderReplicator replicator) {
        this.replicator = replicator;
    }

    /**
     * Set the ReadIndexManager (called by RaftNode after initialization)
     */
    public void setReadIndexManager(ReadIndexManager readIndexManager) {
        this.readIndexManager = readIndexManager;
    }

    /**
     * Set the RaftNode (called by RaftNode after initialization)
     */
    public void setRaftNode(RaftNode raftNode) {
        this.raftNode = raftNode;
    }

    /**
     * Put a key-value pair into the store.
     * This operation goes through Raft consensus.
     *
     * @param key The key
     * @param value The value
     * @param clientId The client ID (for deduplication)
     * @param sequenceNumber The sequence number (for ordering)
     * @return CompletableFuture that completes with the value when committed
     * @throws NotLeaderException if this node is not the leader
     */
    public CompletableFuture<String> put(String key, String value, String clientId, long sequenceNumber) {
        if (replicator == null) {
            throw new NotLeaderException("This node is not the leader");
        }

        logger.debug("PUT request: key='{}', value='{}', clientId='{}', seq={}", key, value, clientId, sequenceNumber);

        // Create command
        KeyValueCommand command = KeyValueCommand.put(key, value, clientId, sequenceNumber);

        // Create log entry
        long nextIndex = raftLog.getLastIndex() + 1;
        LogEntry entry = new LogEntry(nextIndex, this.currentTerm, command.toJson(), LogEntryType.COMMAND);

        // Append to log
        raftLog.append(entry);

        // Wait for commit in a background thread
        CompletableFuture<String> future = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            try {
                // Poll until entry is committed
                long deadline = System.currentTimeMillis() + 5000; // 5 second timeout
                while (System.currentTimeMillis() < deadline) {
                    if (raftLog.getCommitIndex() >= nextIndex) {
                        // Entry is committed, apply it
                        applyCommand(entry);
                        future.complete(value);
                        return;
                    }
                    Thread.sleep(10);
                }
                future.completeExceptionally(new RuntimeException("Timeout waiting for commit"));
            } catch (InterruptedException e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * Get a value from the store (unsafe - may return stale data).
     * Reads are served locally without going through Raft.
     *
     * @deprecated Use getSafe() for linearizable reads
     * @param key The key
     * @return The value, or null if not found
     */
    @Deprecated
    public String get(String key) {
        String value = data.get(key);

        // Record access for ML-based eviction
        if (value != null) {
            accessTracker.recordAccess(key);
        }

        logger.debug("GET request (unsafe): key='{}', value='{}'", key, value);
        return value;
    }

    /**
     * Get a value from the store with linearizable read guarantee.
     * Uses ReadIndex protocol to ensure the read sees all committed writes.
     *
     * @param key The key
     * @return CompletableFuture that completes with the value, or null if not found
     * @throws com.distributed.cache.raft.NotLeaderException if this node is not the leader
     */
    public CompletableFuture<String> getSafe(String key) {
        if (readIndexManager == null) {
            // Fallback to unsafe read if ReadIndexManager not initialized
            logger.warn("ReadIndexManager not initialized, using unsafe read");
            return CompletableFuture.completedFuture(get(key));
        }

        // Step 1: Confirm leadership and get readIndex
        return readIndexManager.confirmReadIndex()
            .thenCompose(readIndex -> {
                // Step 2: Wait for state machine to apply up to readIndex
                return waitForApplied(readIndex);
            })
            .thenApply(readIndex -> {
                // Step 3: Read from local state
                String value = data.get(key);

                // Record access for ML-based eviction
                if (value != null) {
                    accessTracker.recordAccess(key);
                }

                logger.debug("GET request (safe): key='{}', value='{}', readIndex={}", key, value, readIndex);
                return value;
            });
    }

    /**
     * Get a value from the store with configurable consistency level.
     *
     * @param key The key
     * @param consistency The consistency level (STRONG, LEASE, or EVENTUAL)
     * @return CompletableFuture that completes with the value, or null if not found
     */
    public CompletableFuture<String> get(String key, ReadConsistency consistency) {
        switch (consistency) {
            case STRONG:
                // Use ReadIndex protocol - confirms leadership via heartbeat
                return getSafe(key);

            case LEASE:
                // Use lease if valid, otherwise fall back to STRONG
                if (raftNode != null && raftNode.hasValidReadLease()) {
                    // Fast path: lease is valid, read locally
                    String value = data.get(key);
                    if (value != null) {
                        accessTracker.recordAccess(key);
                    }
                    logger.debug("GET request (lease): key='{}', value='{}', leaseRemaining={}ms",
                               key, value, raftNode.getLeaseRemainingMs());
                    return CompletableFuture.completedFuture(value);
                } else {
                    // Lease not valid, fall back to STRONG
                    logger.debug("GET request (lease -> strong): lease not valid, using ReadIndex");
                    return getSafe(key);
                }

            case EVENTUAL:
                // Fastest: read locally without any checks (may be stale)
                String value = data.get(key);
                if (value != null) {
                    accessTracker.recordAccess(key);
                }
                logger.debug("GET request (eventual): key='{}', value='{}'", key, value);
                return CompletableFuture.completedFuture(value);

            default:
                // Should never happen, but default to STRONG for safety
                logger.warn("Unknown consistency level {}, defaulting to STRONG", consistency);
                return getSafe(key);
        }
    }

    /**
     * Wait for the state machine to apply entries up to the given index.
     *
     * @param targetIndex The index to wait for
     * @return CompletableFuture that completes when the index is applied
     */
    private CompletableFuture<Long> waitForApplied(long targetIndex) {
        CompletableFuture<Long> future = new CompletableFuture<>();

        // Schedule periodic checks
        scheduler.scheduleAtFixedRate(() -> {
            try {
                long lastApplied = raftLog.getLastApplied();
                if (lastApplied >= targetIndex) {
                    future.complete(targetIndex);
                }
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }, 0, 10, TimeUnit.MILLISECONDS);

        // Set timeout
        scheduler.schedule(() -> {
            if (!future.isDone()) {
                future.completeExceptionally(
                    new RuntimeException("Timeout waiting for index " + targetIndex + " to be applied"));
            }
        }, 1000, TimeUnit.MILLISECONDS);

        return future;
    }

    /**
     * Delete a key from the store.
     * This operation goes through Raft consensus.
     *
     * @param key The key
     * @param clientId The client ID (for deduplication)
     * @param sequenceNumber The sequence number (for ordering)
     * @return CompletableFuture that completes with true when committed
     * @throws NotLeaderException if this node is not the leader
     */
    public CompletableFuture<Boolean> delete(String key, String clientId, long sequenceNumber) {
        if (replicator == null) {
            throw new NotLeaderException("This node is not the leader");
        }

        logger.debug("DELETE request: key='{}', clientId='{}', seq={}", key, clientId, sequenceNumber);

        // Create command
        KeyValueCommand command = KeyValueCommand.delete(key, clientId, sequenceNumber);

        // Create log entry
        long nextIndex = raftLog.getLastIndex() + 1;
        LogEntry entry = new LogEntry(nextIndex, this.currentTerm, command.toJson(), LogEntryType.COMMAND);

        // Append to log
        raftLog.append(entry);

        // Wait for commit in a background thread
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            try {
                // Poll until entry is committed
                long deadline = System.currentTimeMillis() + 5000; // 5 second timeout
                while (System.currentTimeMillis() < deadline) {
                    if (raftLog.getCommitIndex() >= nextIndex) {
                        // Entry is committed, apply it
                        applyCommand(entry);
                        future.complete(true);
                        return;
                    }
                    Thread.sleep(10);
                }
                future.completeExceptionally(new RuntimeException("Timeout waiting for commit"));
            } catch (InterruptedException e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * Apply a committed log entry to the state machine.
     * This is called by the FollowerReplicator when entries are committed.
     *
     * @param entry The log entry to apply
     */
    public void applyCommand(LogEntry entry) {
        if (entry.getType() == LogEntryType.NO_OP) {
            logger.debug("Applying NO_OP entry at index {}", entry.getIndex());
            raftLog.setLastApplied(entry.getIndex());
            return;
        }

        if (entry.getType() != LogEntryType.COMMAND) {
            logger.warn("Unknown entry type: {}", entry.getType());
            return;
        }

        try {
            KeyValueCommand command = KeyValueCommand.fromJson(entry.getCommand());

            // Check for duplicate (already applied)
            Long lastSeq = lastAppliedSequence.get(command.getClientId());
            if (lastSeq != null && lastSeq >= command.getSequenceNumber()) {
                logger.debug("Skipping duplicate command: clientId='{}', seq={} (already applied seq={})",
                        command.getClientId(), command.getSequenceNumber(), lastSeq);
                return;
            }

            // Apply command based on type
            switch (command.getType()) {
                case PUT:
                    // Check if eviction is needed before adding new key
                    if (!data.containsKey(command.getKey())) {
                        checkAndEvict();
                    }
                    data.put(command.getKey(), command.getValue());
                    logger.info("Applied PUT: key='{}', value='{}'", command.getKey(), command.getValue());
                    break;

                case DELETE:
                    data.remove(command.getKey());
                    logger.info("Applied DELETE: key='{}'", command.getKey());
                    break;

                case GET:
                    // GET doesn't modify state, just log it
                    logger.debug("Applied GET: key='{}'", command.getKey());
                    break;

                default:
                    logger.warn("Unknown command type: {}", command.getType());
            }

            // Update last applied sequence for this client
            lastAppliedSequence.put(command.getClientId(), command.getSequenceNumber());

            // Update last applied index
            raftLog.setLastApplied(entry.getIndex());

        } catch (Exception e) {
            logger.error("Failed to apply command from entry: {}", entry, e);
        }
    }

    /**
     * Get a snapshot of the entire store (for debugging/testing)
     */
    public Map<String, String> getSnapshot() {
        return new HashMap<>(data);
    }

    /**
     * Get the number of key-value pairs in the store
     */
    public int size() {
        return data.size();
    }

    /**
     * Get the access tracker for statistics and ML predictions.
     */
    public AccessTracker getAccessTracker() {
        return accessTracker;
    }

    /**
     * Get all data in the store (for snapshot creation).
     *
     * @return A copy of all key-value pairs
     */
    public Map<String, String> getAllData() {
        return new HashMap<>(data);
    }

    /**
     * Get the last applied sequence numbers for all clients (for snapshot creation).
     *
     * @return A copy of the last applied sequence map
     */
    public Map<String, Long> getLastAppliedSequence() {
        return new HashMap<>(lastAppliedSequence);
    }

    /**
     * Restore state from a snapshot.
     *
     * @param snapshotData The key-value data from the snapshot
     * @param snapshotSequence The last applied sequence numbers from the snapshot
     */
    public void restoreFromSnapshot(Map<String, String> snapshotData, Map<String, Long> snapshotSequence) {
        data.clear();
        data.putAll(snapshotData);
        lastAppliedSequence.clear();
        lastAppliedSequence.putAll(snapshotSequence);
        logger.info("Restored from snapshot: {} keys, {} client sequences",
                   snapshotData.size(), snapshotSequence.size());
    }

    /**
     * Check if cache size exceeds maximum and evict keys if necessary.
     */
    private void checkAndEvict() {
        if (data.size() >= MAX_CACHE_SIZE) {
            // Evict 10% of cache to make room
            int numToEvict = (int) (MAX_CACHE_SIZE * 0.1);

            logger.info("Cache full ({}/{}), evicting {} keys using {} policy",
                    data.size(), MAX_CACHE_SIZE, numToEvict, evictionPolicy.getPolicyName());

            List<String> keysToEvict = evictionPolicy.selectKeysToEvict(new HashMap<>(data), numToEvict);

            for (String key : keysToEvict) {
                data.remove(key);
                evictionCount++;
            }

            logger.info("Evicted {} keys, cache size now: {}", keysToEvict.size(), data.size());
        }
    }

    /**
     * Get cache statistics including eviction info.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cacheSize", data.size());
        stats.put("maxCacheSize", MAX_CACHE_SIZE);
        stats.put("evictionCount", evictionCount);
        stats.put("evictionPolicy", evictionPolicy.getPolicyName());

        // Add ML availability if using ML policy
        if (evictionPolicy instanceof MLEvictionPolicy) {
            MLEvictionPolicy mlPolicy = (MLEvictionPolicy) evictionPolicy;
            stats.put("mlServiceAvailable", mlPolicy.isMLAvailable());
        }

        return stats;
    }

    /**
     * Shutdown the key-value store and clean up resources.
     */
    public void shutdown() {
        accessTracker.stop();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("KeyValueStore shutdown for node {}", nodeId);
    }

    /**
     * Exception thrown when a write operation is attempted on a non-leader node
     */
    public static class NotLeaderException extends RuntimeException {
        public NotLeaderException(String message) {
            super(message);
        }
    }
}
