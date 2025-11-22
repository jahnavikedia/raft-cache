package com.distributed.cache.eviction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LRU (Least Recently Used) eviction policy.
 *
 * Evicts the keys that were accessed least recently.
 */
public class LRUEvictionPolicy implements EvictionPolicy {
    private static final Logger logger = LoggerFactory.getLogger(LRUEvictionPolicy.class);

    private final Map<String, Long> accessTimes;  // key -> last access timestamp

    public LRUEvictionPolicy() {
        this.accessTimes = new ConcurrentHashMap<>();
    }

    @Override
    public List<String> selectKeysToEvict(Map<String, String> currentData, int count) {
        if (currentData.isEmpty() || count <= 0) {
            return Collections.emptyList();
        }

        // Sort keys by access time (oldest first)
        List<Map.Entry<String, Long>> entries = new ArrayList<>();
        for (String key : currentData.keySet()) {
            long accessTime = accessTimes.getOrDefault(key, 0L);
            entries.add(new AbstractMap.SimpleEntry<>(key, accessTime));
        }

        entries.sort(Comparator.comparingLong(Map.Entry::getValue));

        // Select the oldest 'count' keys
        List<String> keysToEvict = new ArrayList<>();
        for (int i = 0; i < Math.min(count, entries.size()); i++) {
            keysToEvict.add(entries.get(i).getKey());
        }

        logger.info("LRU selected {} keys for eviction", keysToEvict.size());
        return keysToEvict;
    }

    @Override
    public void recordAccess(String key) {
        accessTimes.put(key, System.currentTimeMillis());
    }

    @Override
    public String getPolicyName() {
        return "LRU";
    }

    /**
     * Remove tracking for an evicted key
     */
    public void removeKey(String key) {
        accessTimes.remove(key);
    }
}
