package com.distributed.cache.eviction;

import com.distributed.cache.store.AccessStats;
import com.distributed.cache.store.AccessTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ML-based eviction policy that uses machine learning predictions
 * to decide which keys are least likely to be accessed again.
 *
 * Falls back to LRU if ML service is unavailable.
 */
public class MLEvictionPolicy implements EvictionPolicy {
    private static final Logger logger = LoggerFactory.getLogger(MLEvictionPolicy.class);

    private final MLClient mlClient;
    private final AccessTracker accessTracker;
    private final LRUEvictionPolicy lruFallback;

    public MLEvictionPolicy(AccessTracker accessTracker) {
        this(accessTracker, new MLClient());
    }

    public MLEvictionPolicy(AccessTracker accessTracker, MLClient mlClient) {
        this.accessTracker = accessTracker;
        this.mlClient = mlClient;
        this.lruFallback = new LRUEvictionPolicy();
    }

    @Override
    public List<String> selectKeysToEvict(Map<String, String> currentData, int count) {
        if (currentData.isEmpty() || count <= 0) {
            return Collections.emptyList();
        }

        // Check if ML service is available
        if (!mlClient.isAvailable()) {
            logger.warn("ML service unavailable, falling back to LRU eviction");
            return lruFallback.selectKeysToEvict(currentData, count);
        }

        try {
            // Get access statistics for all keys
            Map<String, AccessStats> accessStatsMap = new HashMap<>();
            for (String key : currentData.keySet()) {
                AccessStats stats = accessTracker.getStats(key);
                if (stats != null) {
                    accessStatsMap.put(key, stats);
                } else {
                    // Create default stats for keys without history
                    accessStatsMap.put(key, new AccessStats(key));
                }
            }

            // Get ML predictions
            List<MLPrediction> predictions = mlClient.getPredictions(accessStatsMap);

            if (predictions.isEmpty()) {
                logger.warn("No ML predictions received, falling back to LRU");
                return lruFallback.selectKeysToEvict(currentData, count);
            }

            // Sort predictions by probability (lowest first = least likely to be accessed)
            predictions.sort(Comparator.comparingDouble(MLPrediction::getProbability));

            // Select keys with lowest probability of being accessed
            List<String> keysToEvict = predictions.stream()
                    .limit(count)
                    .map(MLPrediction::getKey)
                    .collect(Collectors.toList());

            logger.info("ML-based eviction selected {} keys (avg probability: {:.2f})",
                    keysToEvict.size(),
                    predictions.stream().limit(count).mapToDouble(MLPrediction::getProbability).average().orElse(0.0));

            return keysToEvict;

        } catch (Exception e) {
            logger.error("ML eviction failed, falling back to LRU", e);
            return lruFallback.selectKeysToEvict(currentData, count);
        }
    }

    @Override
    public void recordAccess(String key) {
        // Access tracking is handled by AccessTracker, but we also update LRU fallback
        lruFallback.recordAccess(key);
    }

    @Override
    public String getPolicyName() {
        return mlClient.isAvailable() ? "ML-BASED" : "ML-BASED (LRU-FALLBACK)";
    }

    /**
     * Check if ML service is available
     */
    public boolean isMLAvailable() {
        return mlClient.isAvailable();
    }
}
