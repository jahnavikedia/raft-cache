package com.distributed.cache.eviction;

import com.distributed.cache.store.AccessStats;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Client for communicating with the ML prediction service.
 */
public class MLClient {
    private static final Logger logger = LoggerFactory.getLogger(MLClient.class);
    private static final String DEFAULT_ML_SERVICE_URL = "http://localhost:5001";
    private static final int TIMEOUT_SECONDS = 5;

    private final String mlServiceUrl;
    private final HttpClient httpClient;
    private final Gson gson;

    public MLClient() {
        this(DEFAULT_ML_SERVICE_URL);
    }

    public MLClient(String mlServiceUrl) {
        this.mlServiceUrl = mlServiceUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
        this.gson = new Gson();
    }

    /**
     * Get ML predictions for which keys will be accessed again.
     *
     * @param accessStatsMap Map of key -> AccessStats
     * @return List of ML predictions
     */
    public List<MLPrediction> getPredictions(Map<String, AccessStats> accessStatsMap) {
        List<MLPrediction> predictions = new ArrayList<>();

        try {
            // Build request payload
            JsonArray keysArray = new JsonArray();
            for (Map.Entry<String, AccessStats> entry : accessStatsMap.entrySet()) {
                JsonObject keyData = new JsonObject();
                keyData.addProperty("key", entry.getKey());

                AccessStats stats = entry.getValue();
                List<Long> timestamps = stats.getAccessTimestamps();

                keyData.addProperty("access_count", stats.getTotalAccessCount());
                keyData.addProperty("last_access_ms", stats.getLastAccessTime());
                keyData.addProperty("access_count_hour", stats.getAccessCountHour());
                keyData.addProperty("access_count_day", stats.getAccessCountDay());

                // Calculate first access time and average interval
                long firstAccessMs = timestamps.isEmpty() ? 0 : timestamps.get(0);
                double avgIntervalMs = 0.0;
                if (timestamps.size() > 1) {
                    long totalInterval = timestamps.get(timestamps.size() - 1) - timestamps.get(0);
                    avgIntervalMs = (double) totalInterval / (timestamps.size() - 1);
                }

                keyData.addProperty("first_access_ms", firstAccessMs);
                keyData.addProperty("avg_interval_ms", avgIntervalMs);

                keysArray.add(keyData);
            }

            JsonObject requestBody = new JsonObject();
            requestBody.add("keys", keysArray);

            String jsonPayload = gson.toJson(requestBody);

            // Send HTTP request to ML service
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mlServiceUrl + "/predict"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .build();

            logger.debug("Sending ML prediction request for {} keys", accessStatsMap.size());

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Parse response
                JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);
                JsonArray predictionsArray = responseJson.getAsJsonArray("predictions");

                for (int i = 0; i < predictionsArray.size(); i++) {
                    JsonObject predObj = predictionsArray.get(i).getAsJsonObject();
                    String key = predObj.get("key").getAsString();
                    double probability = predObj.get("probability").getAsDouble();
                    boolean willBeAccessed = predObj.get("willBeAccessed").getAsBoolean();

                    predictions.add(new MLPrediction(key, probability, willBeAccessed));
                }

                logger.info("Received {} ML predictions from service", predictions.size());
            } else {
                logger.error("ML service returned status {}: {}", response.statusCode(), response.body());
            }

        } catch (Exception e) {
            logger.error("Failed to get ML predictions", e);
        }

        return predictions;
    }

    /**
     * Check if the ML service is available.
     *
     * @return true if service is reachable, false otherwise
     */
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mlServiceUrl + "/health"))
                    .GET()
                    .timeout(Duration.ofSeconds(2))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            logger.debug("ML service not available: {}", e.getMessage());
            return false;
        }
    }
}
