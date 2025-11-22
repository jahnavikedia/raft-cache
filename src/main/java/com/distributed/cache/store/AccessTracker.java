package com.distributed.cache.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * AccessTracker tracks access patterns for all keys in the cache.
 *
 * Features:
 * - Thread-safe using ConcurrentHashMap
 * - Automatic decay of counters every 5 minutes
 * - Provides access statistics for ML-based eviction decisions
 */
public class AccessTracker {
    private static final Logger logger = LoggerFactory.getLogger(AccessTracker.class);

    private static final long DECAY_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes
    private static final long ONE_HOUR_MS = 60 * 60 * 1000;
    private static final long ONE_DAY_MS = 24 * 60 * 60 * 1000;

    private final ConcurrentHashMap<String, AccessStats> accessStatsMap;
    private final ScheduledExecutorService decayScheduler;
    private volatile boolean running;

    public AccessTracker() {
        this.accessStatsMap = new ConcurrentHashMap<>();
        this.decayScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AccessTracker-Decay");
            t.setDaemon(true);
            return t;
        });
        this.running = false;
    }

    /**
     * Start the decay scheduler.
     * Should be called once during initialization.
     */
    public void start() {
        if (running) {
            logger.warn("AccessTracker already running");
            return;
        }

        running = true;
        decayScheduler.scheduleAtFixedRate(
            this::performDecay,
            DECAY_INTERVAL_MS,
            DECAY_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        logger.info("AccessTracker started with decay interval of {} ms", DECAY_INTERVAL_MS);
    }

    /**
     * Stop the decay scheduler.
     * Should be called during shutdown.
     */
    public void stop() {
        if (!running) {
            return;
        }

        running = false;
        decayScheduler.shutdown();
        try {
            if (!decayScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                decayScheduler.shutdownNow();
            }
            logger.info("AccessTracker stopped");
        } catch (InterruptedException e) {
            decayScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Record an access to a key.
     *
     * @param key The key that was accessed
     */
    public void recordAccess(String key) {
        long timestamp = System.currentTimeMillis();
        AccessStats stats = accessStatsMap.computeIfAbsent(key, AccessStats::new);
        stats.recordAccess(timestamp);

        logger.debug("Recorded access for key: {}, timestamp: {}", key, timestamp);
    }

    /**
     * Get access statistics for a specific key.
     *
     * @param key The key to get stats for
     * @return AccessStats object, or null if key has no tracked accesses
     */
    public AccessStats getStats(String key) {
        return accessStatsMap.get(key);
    }

    /**
     * Get access statistics for all tracked keys.
     *
     * @return Map of key -> AccessStats
     */
    public Map<String, AccessStats> getAllStats() {
        return new ConcurrentHashMap<>(accessStatsMap);
    }

    /**
     * Get a list of all stats as maps (for JSON serialization).
     *
     * @return List of stats maps
     */
    public List<Map<String, Object>> getAllStatsAsMaps() {
        return accessStatsMap.values().stream()
            .map(AccessStats::toMap)
            .collect(Collectors.toList());
    }

    /**
     * Get access history for ML predictions.
     * Returns a map of key -> list of timestamps.
     *
     * @return Map of key -> access timestamps
     */
    public Map<String, List<Long>> getAccessHistory() {
        return accessStatsMap.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().getAccessTimestamps()
            ));
    }

    /**
     * Remove tracking for a key (e.g., when evicted from cache).
     *
     * @param key The key to stop tracking
     */
    public void removeKey(String key) {
        accessStatsMap.remove(key);
        logger.debug("Removed tracking for key: {}", key);
    }

    /**
     * Perform decay on all tracked keys.
     * Called periodically by the scheduler.
     */
    private void performDecay() {
        try {
            long currentTime = System.currentTimeMillis();
            long hourThreshold = currentTime - ONE_HOUR_MS;
            long dayThreshold = currentTime - ONE_DAY_MS;

            int decayedCount = 0;
            for (AccessStats stats : accessStatsMap.values()) {
                stats.decay(currentTime, hourThreshold, dayThreshold);
                decayedCount++;
            }

            logger.debug("Performed decay on {} keys", decayedCount);
        } catch (Exception e) {
            logger.error("Error during decay operation", e);
        }
    }

    /**
     * Get the number of tracked keys.
     */
    public int getTrackedKeyCount() {
        return accessStatsMap.size();
    }

    /**
     * Clear all tracking data (for testing).
     */
    public void clear() {
        accessStatsMap.clear();
        logger.info("Cleared all access tracking data");
    }
}
