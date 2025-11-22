package com.distributed.cache.eviction;

import java.util.List;
import java.util.Map;

/**
 * EvictionPolicy defines the interface for cache eviction strategies.
 *
 * Implementations can use different algorithms (LRU, ML-based, etc.)
 * to decide which keys to evict when the cache reaches its capacity.
 */
public interface EvictionPolicy {

    /**
     * Select keys to evict from the cache.
     *
     * @param currentData The current cache data (key -> value mapping)
     * @param count The number of keys to evict
     * @return List of keys to evict
     */
    List<String> selectKeysToEvict(Map<String, String> currentData, int count);

    /**
     * Notify the policy that a key was accessed.
     * Used for tracking access patterns for eviction decisions.
     *
     * @param key The key that was accessed
     */
    void recordAccess(String key);

    /**
     * Get the name of this eviction policy.
     *
     * @return The policy name (e.g., "LRU", "ML-BASED")
     */
    String getPolicyName();
}
