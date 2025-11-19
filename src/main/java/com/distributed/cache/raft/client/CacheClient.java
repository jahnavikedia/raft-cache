package com.distributed.cache.raft.client;

import com.distributed.cache.raft.api.ClientResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CacheClient: command-line client to interact with the distributed Raft-based
 * cache.
 *
 * Commands:
 * put <key> <value>
 * get <key>
 * delete <key>
 * status
 * connect <nodeIndex>
 */
public class CacheClient {

    private static final Logger logger = LoggerFactory.getLogger(CacheClient.class);
    private static final int TIMEOUT_MS = 5000;

    private final List<String> clusterHttpAddresses;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    private final String clientId;
    private long sequenceNumber = 0;
    private int currentNodeIndex = 0;

    public CacheClient(List<String> clusterHttpAddresses) {
        this.clusterHttpAddresses = clusterHttpAddresses;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(1000))
                .build();
        this.clientId = UUID.randomUUID().toString();
    }

    private long nextSeq() {
        return ++sequenceNumber;
    }

    private String currentNodeAddress() {
        return clusterHttpAddresses.get(currentNodeIndex);
    }

    // -------------------------------------------------------------------------
    // PUT
    // -------------------------------------------------------------------------
    public void put(String key, String value) throws Exception {
        long start = System.currentTimeMillis();

        JsonObject req = new JsonObject();
        req.addProperty("clientId", clientId);
        req.addProperty("sequenceNumber", nextSeq());
        req.addProperty("key", key);
        req.addProperty("value", value);
        String body = gson.toJson(req);

        String url = currentNodeAddress() + "/cache/" + key;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(TIMEOUT_MS))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long latency = System.currentTimeMillis() - start;

        if (response.statusCode() == 307) {
            System.out.println("Redirected to leader...");
            String location = response.headers().firstValue("Location").orElse("unknown");
            System.out.println("→ " + location);
            return;
        }

        ClientResponse resp = gson.fromJson(response.body(), ClientResponse.class);
        if (resp.isSuccess()) {
            System.out.printf("✅ PUT success: %s=%s (%.2f ms)%n", key, value, (double) latency);
        } else {
            System.out.printf("❌ PUT failed: %s (%s)%n", key, resp.getErrorMessage());
        }
    }

    // -------------------------------------------------------------------------
    // GET
    // -------------------------------------------------------------------------
    public void get(String key) throws Exception {
        long start = System.currentTimeMillis();
        String url = currentNodeAddress() + "/cache/" + key;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(TIMEOUT_MS))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long latency = System.currentTimeMillis() - start;

        if (response.statusCode() == 404) {
            System.out.printf("🔍 Key '%s' not found (%.2f ms)%n", key, (double) latency);
            return;
        }

        ClientResponse resp = gson.fromJson(response.body(), ClientResponse.class);
        System.out.printf("📦 GET %s = %s (%.2f ms)%n", key, resp.getValue(), (double) latency);
    }

    // -------------------------------------------------------------------------
    // DELETE
    // -------------------------------------------------------------------------
    public void delete(String key) throws Exception {
        long start = System.currentTimeMillis();

        JsonObject req = new JsonObject();
        req.addProperty("clientId", clientId);
        req.addProperty("sequenceNumber", nextSeq());
        req.addProperty("key", key);

        String body = gson.toJson(req);

        String url = currentNodeAddress() + "/cache/" + key;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(TIMEOUT_MS))
                .header("Content-Type", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long latency = System.currentTimeMillis() - start;

        ClientResponse resp = gson.fromJson(response.body(), ClientResponse.class);
        if (resp.isSuccess()) {
            System.out.printf("🗑️ DELETE success: %s (%.2f ms)%n", key, (double) latency);
        } else {
            System.out.printf("❌ DELETE failed: %s (%s)%n", key, resp.getErrorMessage());
        }
    }

    // -------------------------------------------------------------------------
    // STATUS
    // -------------------------------------------------------------------------
    public void status() throws Exception {
        System.out.println("Cluster Status:");
        System.out.println("Node ID | Role | Term | CommitIndex | LogSize");
        System.out.println("---------------------------------------------");
        for (String addr : clusterHttpAddresses) {
            String url = addr + "/status";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(TIMEOUT_MS))
                    .GET()
                    .build();
            try {
                HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                Map<?, ?> json = gson.fromJson(resp.body(), Map.class);
                System.out.printf("%-8s | %-8s | %-4s | %-12s | %-6s%n",
                        json.get("nodeId"), json.get("role"),
                        json.get("currentTerm"), json.get("commitIndex"), json.get("logSize"));
            } catch (Exception e) {
                System.out.printf("%-8s | %-8s | %-4s | %-12s | %-6s%n",
                        addr, "DOWN", "-", "-", "-");
            }
        }
    }

    // -------------------------------------------------------------------------
    // CONNECT
    // -------------------------------------------------------------------------
    public void connectToNode(int index) {
        if (index < 0 || index >= clusterHttpAddresses.size()) {
            System.out.println("Invalid node index");
            return;
        }
        currentNodeIndex = index;
        System.out.println("Switched to node: " + currentNodeAddress());
    }

    // -------------------------------------------------------------------------
    // MAIN
    // -------------------------------------------------------------------------
    public static void main(String[] args) throws Exception {
        ClientConfig config = ClientConfig.load("client-config.yaml");
        List<String> cluster = config.getNodes().stream()
                .map(n -> n.get("httpAddress"))
                .toList();

        CacheClient client = new CacheClient(cluster);

        if (args.length < 1) {
            System.out.println("Usage: put|get|delete|status|connect");
            return;
        }

        switch (args[0].toLowerCase()) {
            case "put" -> {
                if (args.length < 3) {
                    System.out.println("Usage: put <key> <value>");
                    return;
                }
                client.put(args[1], args[2]);
            }
            case "get" -> {
                if (args.length < 2) {
                    System.out.println("Usage: get <key>");
                    return;
                }
                client.get(args[1]);
            }
            case "delete" -> {
                if (args.length < 2) {
                    System.out.println("Usage: delete <key>");
                    return;
                }
                client.delete(args[1]);
            }
            case "status" -> client.status();
            case "connect" -> {
                if (args.length < 2) {
                    System.out.println("Usage: connect <nodeIndex>");
                    return;
                }
                client.connectToNode(Integer.parseInt(args[1]));
            }
            default -> System.out.println("Unknown command");
        }
    }
}
