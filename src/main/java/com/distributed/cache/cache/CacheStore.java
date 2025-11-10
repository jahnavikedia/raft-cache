package com.distributed.cache.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Thread-safe in-memory cache store
 */
public class CacheStore {
    private static final Logger logger = LoggerFactory.getLogger(CacheStore.class);
    
    private final Map<String, CacheEntry> store;
    private final long maxSize;
    
    public CacheStore(long maxSize) {
        this.store = new ConcurrentHashMap<>();
        this.maxSize = maxSize;
        logger.info("CacheStore initialized with max size: {}", maxSize);
    }
    
    /**
     * Put a key-value pair in the cache
     */
    public void put(String key, String value) {
        CacheEntry entry = new CacheEntry(key, value, System.currentTimeMillis());
        store.put(key, entry);
        logger.debug("PUT: key={}, value={}", key, value);
        
        // TODO: Check if eviction is needed
        if (store.size() > maxSize) {
            evict();
        }
    }
    
    /**
     * Get a value from the cache
     */
    public String get(String key) {
        CacheEntry entry = store.get(key);
        if (entry != null) {
            entry.updateAccessTime();
            logger.debug("GET: key={}, found={}", key, true);
            return entry.getValue();
        }
        logger.debug("GET: key={}, found={}", key, false);
        return null;
    }
    
    /**
     * Delete a key from the cache
     */
    public void delete(String key) {
        store.remove(key);
        logger.debug("DELETE: key={}", key);
    }
    
    /**
     * Evict entries when cache is full
     * TODO: Integrate with ML service for intelligent eviction
     */
    private void evict() {
        // Simple LRU eviction for now
        String oldestKey = null;
        long oldestTime = Long.MAX_VALUE;
        
        for (Map.Entry<String, CacheEntry> entry : store.entrySet()) {
            if (entry.getValue().getLastAccessTime() < oldestTime) {
                oldestTime = entry.getValue().getLastAccessTime();
                oldestKey = entry.getKey();
            }
        }
        
        if (oldestKey != null) {
            store.remove(oldestKey);
            logger.info("Evicted key: {}", oldestKey);
        }
    }
    
    public int size() {
        return store.size();
    }
    
    public void clear() {
        store.clear();
        logger.info("Cache cleared");
    }
}
