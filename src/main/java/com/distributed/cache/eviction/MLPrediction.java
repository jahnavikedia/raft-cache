package com.distributed.cache.eviction;

/**
 * Represents an ML prediction for whether a key will be accessed again.
 */
public class MLPrediction {
    private final String key;
    private final double probability;  // Probability that key will be accessed (0.0 to 1.0)
    private final boolean willBeAccessed;

    public MLPrediction(String key, double probability, boolean willBeAccessed) {
        this.key = key;
        this.probability = probability;
        this.willBeAccessed = willBeAccessed;
    }

    public String getKey() {
        return key;
    }

    public double getProbability() {
        return probability;
    }

    public boolean willBeAccessed() {
        return willBeAccessed;
    }

    @Override
    public String toString() {
        return String.format("MLPrediction{key='%s', probability=%.2f, willBeAccessed=%s}",
                key, probability, willBeAccessed);
    }
}
