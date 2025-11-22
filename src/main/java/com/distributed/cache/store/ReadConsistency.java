package com.distributed.cache.store;

/**
 * Read consistency levels.
 */
public enum ReadConsistency {
    /**
     * Strong consistency - confirms leadership via heartbeat.
     * Slowest but guarantees linearizability.
     */
    STRONG,

    /**
     * Lease-based consistency - uses leader lease when valid.
     * Fast and safe, but falls back to STRONG if lease expired.
     */
    LEASE,

    /**
     * Eventual consistency - reads locally without any checks.
     * Fastest but may return stale data on partitioned leaders.
     */
    EVENTUAL
}
