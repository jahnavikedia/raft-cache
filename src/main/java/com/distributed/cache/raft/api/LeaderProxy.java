package com.distributed.cache.raft.api;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * LeaderProxy forwards client requests (PUT, DELETE) to the current leader
 * node.
 * Followers use this helper when they receive client writes but are not the
 * leader.
 */
public class LeaderProxy {

    private static final Logger logger = LoggerFactory.getLogger(LeaderProxy.class);
    private static final int REQUEST_TIMEOUT_MS = 5000;

    private final HttpClient httpClient;
    private final Map<String, String> nodeHttpAddresses; // nodeId → http://host:port
    private final Gson gson = new Gson();

    private volatile String currentLeaderId;

    public LeaderProxy(Map<String, String> nodeHttpAddresses) {
        this.nodeHttpAddresses = nodeHttpAddresses;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(1000))
                .build();
    }

    /** Update leader information (called by RaftNode when leadership changes). */
    public void updateLeader(String leaderId) {
        this.currentLeaderId = leaderId;
        logger.info("LeaderProxy updated: new leader = {}", leaderId);
    }

    public boolean isLeaderKnown() {
        return currentLeaderId != null && nodeHttpAddresses.containsKey(currentLeaderId);
    }

    public String getLeaderHttpAddress() {
        return isLeaderKnown() ? nodeHttpAddresses.get(currentLeaderId) : null;
    }

    // -------------------------------------------------------------------------
    // PUT forwarding
    // -------------------------------------------------------------------------

    public ClientResponse forwardPutToLeader(String key, String value, String clientId, long seqNum) {
        if (!isLeaderKnown()) {
            return new ClientResponse(false, null, "Leader unknown", null);
        }
        String leaderUrl = getLeaderHttpAddress() + "/cache/" + key;

        try {
            ClientRequest req = new ClientRequest();
            var bodyJson = gson.toJson(Map.of(
                    "value", value,
                    "clientId", clientId,
                    "sequenceNumber", seqNum));

            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(leaderUrl))
                    .timeout(Duration.ofMillis(REQUEST_TIMEOUT_MS))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<String> resp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 307) {
                logger.warn("Redirected: leader may have changed");
                return new ClientResponse(false, null, "Redirect", currentLeaderId);
            }

            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return gson.fromJson(resp.body(), ClientResponse.class);
            } else {
                return new ClientResponse(false, null,
                        "Leader returned HTTP " + resp.statusCode(), currentLeaderId);
            }

        } catch (Exception e) {
            logger.error("Failed forwarding PUT to leader {}", currentLeaderId, e);
            return new ClientResponse(false, null, e.getMessage(), currentLeaderId);
        }
    }

    // -------------------------------------------------------------------------
    // DELETE forwarding
    // -------------------------------------------------------------------------

    public ClientResponse forwardDeleteToLeader(String key, String clientId, long seqNum) {
        if (!isLeaderKnown()) {
            return new ClientResponse(false, null, "Leader unknown", null);
        }
        String leaderUrl = getLeaderHttpAddress() + "/cache/" + key;

        try {
            var bodyJson = gson.toJson(Map.of(
                    "clientId", clientId,
                    "sequenceNumber", seqNum));

            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(leaderUrl))
                    .timeout(Duration.ofMillis(REQUEST_TIMEOUT_MS))
                    .header("Content-Type", "application/json")
                    .method("DELETE", HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<String> resp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 307) {
                logger.warn("Redirected: leader may have changed");
                return new ClientResponse(false, null, "Redirect", currentLeaderId);
            }

            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return gson.fromJson(resp.body(), ClientResponse.class);
            } else {
                return new ClientResponse(false, null,
                        "Leader returned HTTP " + resp.statusCode(), currentLeaderId);
            }

        } catch (Exception e) {
            logger.error("Failed forwarding DELETE to leader {}", currentLeaderId, e);
            return new ClientResponse(false, null, e.getMessage(), currentLeaderId);
        }
    }
}
