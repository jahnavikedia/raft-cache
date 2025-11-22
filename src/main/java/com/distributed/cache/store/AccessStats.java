package com.distributed.cache.store;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AccessStats tracks access patterns for a single cache key.
 *
 * Thread-safe implementation using concurrent collections and atomic variables.
 * Tracks:
 * - Last 100 access timestamps
 * - Access count in last hour
 * - Access count in last day
 * - Last access timestamp
 */
public class AccessStats {
    private static final int MAX_TIMESTAMPS = 100;

    private final String key;
    private final CopyOnWriteArrayList<Long> accessTimestamps;
    private final AtomicInteger accessCountHour;
    private final AtomicInteger accessCountDay;
    private final AtomicLong lastAccessTime;

    public AccessStats(String key) {
        this.key = key;
        this.accessTimestamps = new CopyOnWriteArrayList<>();
        this.accessCountHour = new AtomicInteger(0);
        this.accessCountDay = new AtomicInteger(0);
        this.lastAccessTime = new AtomicLong(0);
    }

    /**
     * Record a new access at the given timestamp.
     *
     * @param timestamp The timestamp in milliseconds since epoch
     */
    public void recordAccess(long timestamp) {
        // Update last access time
        lastAccessTime.set(timestamp);

        // Add timestamp to list, maintaining max size
        accessTimestamps.add(timestamp);
        if (accessTimestamps.size() > MAX_TIMESTAMPS) {
            accessTimestamps.remove(0);
        }

        // Increment counters (decay will handle cleanup)
        accessCountHour.incrementAndGet();
        accessCountDay.incrementAndGet();
    }

    /**
     * Decay counters by removing accesses older than the given thresholds.
     * Should be called periodically (e.g., every 5 minutes).
     *
     * @param currentTime Current timestamp in milliseconds
     * @param hourThreshold Timestamp threshold for hour counter (currentTime - 1 hour)
     * @param dayThreshold Timestamp threshold for day counter (currentTime - 1 day)
     */
    public void decay(long currentTime, long hourThreshold, long dayThreshold) {
        int hourCount = 0;
        int dayCount = 0;

        // Count accesses within thresholds
        for (Long timestamp : accessTimestamps) {
            if (timestamp >= hourThreshold) {
                hourCount++;
            }
            if (timestamp >= dayThreshold) {
                dayCount++;
            }
        }

        // Update counters
        accessCountHour.set(hourCount);
        accessCountDay.set(dayCount);
    }

    /**
     * Get the key this stats object tracks.
     */
    public String getKey() {
        return key;
    }

    /**
     * Get a copy of the access timestamps list.
     */
    public List<Long> getAccessTimestamps() {
        return new ArrayList<>(accessTimestamps);
    }

    /**
     * Get the access count in the last hour.
     */
    public int getAccessCountHour() {
        return accessCountHour.get();
    }

    /**
     * Get the access count in the last day.
     */
    public int getAccessCountDay() {
        return accessCountDay.get();
    }

    /**
     * Get the last access timestamp.
     */
    public long getLastAccessTime() {
        return lastAccessTime.get();
    }

    /**
     * Get the total number of recorded access timestamps.
     */
    public int getTotalAccessCount() {
        return accessTimestamps.size();
    }

    /**
     * Convert to a JSON-friendly map for API responses.
     */
    public java.util.Map<String, Object> toMap() {
        return java.util.Map.of(
            "key", key,
            "lastAccessTime", lastAccessTime.get(),
            "accessCountHour", accessCountHour.get(),
            "accessCountDay", accessCountDay.get(),
            "totalAccessCount", accessTimestamps.size(),
            "recentTimestamps", getAccessTimestamps()
        );
    }
}
