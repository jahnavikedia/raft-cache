package com.distributed.cache.store;

import com.distributed.cache.raft.LogEntry;
import com.distributed.cache.raft.LogEntryType;
import com.distributed.cache.replication.LeaderReplicator;
import com.distributed.cache.replication.RaftLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Key-value store state machine that integrates with Raft consensus.
 *
 * Writes (PUT/DELETE) go through Raft consensus and are applied after being committed.
 * Reads (GET) are served locally from the in-memory map.
 */
public class KeyValueStore {
    private static final Logger logger = LoggerFactory.getLogger(KeyValueStore.class);

    private final ConcurrentHashMap<String, String> data;
    private final Map<String, Long> lastAppliedSequence;
    private final RaftLog raftLog;
    private LeaderReplicator replicator;
    private final String nodeId;
    private int currentTerm;

    public KeyValueStore(String nodeId, RaftLog raftLog) {
        this.nodeId = nodeId;
        this.data = new ConcurrentHashMap<>();
        this.lastAppliedSequence = new ConcurrentHashMap<>();
        this.raftLog = raftLog;
        this.replicator = null;
        this.currentTerm = 0;

        logger.info("KeyValueStore initialized for node {}", nodeId);
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
     * Get a value from the store.
     * Reads are served locally without going through Raft.
     *
     * @param key The key
     * @return The value, or null if not found
     */
    public String get(String key) {
        String value = data.get(key);
        logger.debug("GET request: key='{}', value='{}'", key, value);
        return value;
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
     * Exception thrown when a write operation is attempted on a non-leader node
     */
    public static class NotLeaderException extends RuntimeException {
        public NotLeaderException(String message) {
            super(message);
        }
    }
}
