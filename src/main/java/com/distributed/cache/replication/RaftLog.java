package com.distributed.cache.replication;

import com.distributed.cache.raft.LogEntry;
import com.distributed.cache.storage.LogPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * RaftLog manages the replicated log for a Raft node.
 *
 * The log is stored both in-memory (for fast access) and on disk (for durability).
 * All operations are thread-safe using read-write locks.
 *
 * Log indices are 1-based (index 0 represents "before the first entry").
 */
public class RaftLog {
    private static final Logger logger = LoggerFactory.getLogger(RaftLog.class);

    private final List<LogEntry> entries;
    private final LogPersistence persistence;
    private final ReadWriteLock lock;

    private volatile long commitIndex;
    private volatile long lastApplied;

    /**
     * Create a new RaftLog instance
     *
     * @param nodeId The ID of this node (for persistence)
     */
    public RaftLog(String nodeId) {
        this.entries = new ArrayList<>();
        this.persistence = new LogPersistence(nodeId);
        this.lock = new ReentrantReadWriteLock();
        this.commitIndex = 0;
        this.lastApplied = 0;

        // Load existing log from disk
        List<LogEntry> loadedEntries = persistence.loadLog();
        entries.addAll(loadedEntries);

        logger.info("RaftLog initialized for node {}: {} entries loaded from disk", nodeId, entries.size());
    }

    /**
     * Append a new entry to the log
     *
     * @param entry The entry to append
     */
    public void append(LogEntry entry) {
        lock.writeLock().lock();
        try {
            entries.add(entry);
            persistence.appendEntry(entry);
            logger.debug("Appended entry: {}", entry);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get a log entry at a specific index
     *
     * @param index The index (1-based)
     * @return The log entry, or null if index is out of bounds
     */
    public LogEntry getEntry(long index) {
        lock.readLock().lock();
        try {
            if (index < 1 || index > entries.size()) {
                return null;
            }
            // Convert 1-based index to 0-based array index
            return entries.get((int) (index - 1));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get all entries starting from a specific index
     *
     * @param index The starting index (1-based, inclusive)
     * @return List of entries from index onwards
     */
    public List<LogEntry> getEntriesSince(long index) {
        lock.readLock().lock();
        try {
            if (index < 1 || index > entries.size() + 1) {
                return new ArrayList<>();
            }
            // Convert 1-based index to 0-based array index
            int startIdx = (int) (index - 1);
            return new ArrayList<>(entries.subList(startIdx, entries.size()));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Delete all entries from a specific index onwards
     * Used when resolving log conflicts
     *
     * @param index The index to delete from (1-based, inclusive)
     */
    public void deleteEntriesFrom(long index) {
        lock.writeLock().lock();
        try {
            if (index < 1 || index > entries.size()) {
                return;
            }

            // Remove entries from in-memory list
            int startIdx = (int) (index - 1);
            int removed = entries.size() - startIdx;
            entries.subList(startIdx, entries.size()).clear();

            // Truncate persistent log
            persistence.truncate(index);

            logger.info("Deleted {} entries from index {}", removed, index);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get the index of the last entry in the log
     *
     * @return The last index, or 0 if log is empty
     */
    public long getLastIndex() {
        lock.readLock().lock();
        try {
            return entries.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get the term of the last entry in the log
     *
     * @return The last term, or 0 if log is empty
     */
    public int getLastTerm() {
        lock.readLock().lock();
        try {
            if (entries.isEmpty()) {
                return 0;
            }
            return entries.get(entries.size() - 1).getTerm();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if this log is at least as up-to-date as the given log indices
     * Used during leader election to determine if a candidate's log is acceptable
     *
     * @param lastLogIndex The candidate's last log index
     * @param lastLogTerm The candidate's last log term
     * @return true if this log is at least as up-to-date
     */
    public boolean isUpToDate(long lastLogIndex, int lastLogTerm) {
        lock.readLock().lock();
        try {
            int ourLastTerm = getLastTerm();
            long ourLastIndex = getLastIndex();

            // Higher term wins
            if (lastLogTerm != ourLastTerm) {
                return lastLogTerm >= ourLastTerm;
            }

            // Same term: longer log wins
            return lastLogIndex >= ourLastIndex;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get the current commit index
     */
    public long getCommitIndex() {
        return commitIndex;
    }

    /**
     * Set the commit index
     * The commit index should only increase
     */
    public void setCommitIndex(long commitIndex) {
        if (commitIndex > this.commitIndex) {
            long oldCommitIndex = this.commitIndex;
            this.commitIndex = commitIndex;
            logger.debug("Updated commitIndex: {} -> {}", oldCommitIndex, commitIndex);
        }
    }

    /**
     * Get the last applied index
     */
    public long getLastApplied() {
        return lastApplied;
    }

    /**
     * Set the last applied index
     * The last applied index should only increase
     */
    public void setLastApplied(long lastApplied) {
        if (lastApplied > this.lastApplied) {
            long oldLastApplied = this.lastApplied;
            this.lastApplied = lastApplied;
            logger.debug("Updated lastApplied: {} -> {}", oldLastApplied, lastApplied);
        }
    }

    /**
     * Get the total number of entries in the log.
     * Used by snapshot manager to determine when to create snapshots.
     *
     * @return The number of log entries
     */
    public long getLogSize() {
        lock.readLock().lock();
        try {
            return entries.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Delete all log entries up to and including the given index.
     * Used after creating a snapshot to compact the log.
     *
     * @param upToIndex The index up to which entries should be deleted (inclusive)
     * @return The number of entries deleted
     */
    public int deleteEntriesUpTo(long upToIndex) {
        lock.writeLock().lock();
        try {
            int deletedCount = 0;
            // Remove entries from the beginning up to upToIndex
            // Since entries are 1-indexed, we need to remove entries[0] through entries[upToIndex-1]
            while (!entries.isEmpty() && entries.get(0).getIndex() <= upToIndex) {
                entries.remove(0);
                deletedCount++;
            }
            logger.info("Deleted {} log entries up to index {}", deletedCount, upToIndex);
            return deletedCount;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get the total number of entries in the log
     */
    public int size() {
        lock.readLock().lock();
        try {
            return entries.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get a copy of all entries (for debugging/testing)
     */
    public List<LogEntry> getAllEntries() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(entries);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Close the log and release resources
     */
    public void close() {
        persistence.close();
    }
}
