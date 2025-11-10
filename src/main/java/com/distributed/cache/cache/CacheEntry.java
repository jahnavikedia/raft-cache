package com.distributed.cache.cache;

/**
 * Represents a single cache entry with metadata
 */
public class CacheEntry {
    private final String key;
    private final String value;
    private final long creationTime;
    private long lastAccessTime;
    private int accessCount;
    
    public CacheEntry(String key, String value, long creationTime) {
        this.key = key;
        this.value = value;
        this.creationTime = creationTime;
        this.lastAccessTime = creationTime;
        this.accessCount = 0;
    }
    
    public void updateAccessTime() {
        this.lastAccessTime = System.currentTimeMillis();
        this.accessCount++;
    }
    
    public String getKey() {
        return key;
    }
    
    public String getValue() {
        return value;
    }
    
    public long getCreationTime() {
        return creationTime;
    }
    
    public long getLastAccessTime() {
        return lastAccessTime;
    }
    
    public int getAccessCount() {
        return accessCount;
    }
    
    /**
     * Get the age of this entry in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - creationTime;
    }
    
    /**
     * Get the time since last access in milliseconds
     */
    public long getTimeSinceLastAccess() {
        return System.currentTimeMillis() - lastAccessTime;
    }
}
