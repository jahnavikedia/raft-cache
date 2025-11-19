package com.distributed.cache.raft.config;

import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Map;

public class NodeConfiguration {

    private String nodeId;
    private int raftPort;
    private int httpPort;
    private String dataDir;
    private Map<String, String> peerMap;
    private Map<String, String> httpPeerMap;

    // Getters
    public String getNodeId() {
        return nodeId;
    }

    public int getRaftPort() {
        return raftPort;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public String getDataDir() {
        return dataDir;
    }

    public Map<String, String> getPeerMap() {
        return peerMap;
    }

    public Map<String, String> getHttpPeerMap() {
        return httpPeerMap;
    }

    @SuppressWarnings("unchecked")
    public static NodeConfiguration load(String path) {
        try (InputStream input = new FileInputStream(Paths.get(path).toFile())) {
            Yaml yaml = new Yaml();
            Map<String, Object> obj = yaml.load(input);

            Map<String, Object> node = (Map<String, Object>) obj.get("node");
            Map<String, Object> cluster = (Map<String, Object>) obj.get("cluster");

            NodeConfiguration conf = new NodeConfiguration();
            conf.nodeId = (String) node.get("id");
            conf.raftPort = (Integer) node.get("raftPort");
            conf.httpPort = (Integer) node.get("httpPort");
            conf.dataDir = (String) node.get("dataDir");

            // Build peer maps
            Map<String, String> peers = new java.util.LinkedHashMap<>();
            Map<String, String> httpPeers = new java.util.LinkedHashMap<>();
            var nodes = (Iterable<Map<String, Object>>) cluster.get("nodes");
            for (Map<String, Object> n : nodes) {
                peers.put((String) n.get("id"), (String) n.get("raftAddress"));
                httpPeers.put((String) n.get("id"), (String) n.get("httpAddress"));
            }
            conf.peerMap = peers;
            conf.httpPeerMap = httpPeers;

            return conf;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load --config", e);
        }
    }
}
