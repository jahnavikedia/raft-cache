package com.distributed.cache.raft.client;

import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class ClientConfig {

    private List<Map<String, String>> nodes;

    public List<Map<String, String>> getNodes() {
        return nodes;
    }

    @SuppressWarnings("unchecked")
    public static ClientConfig load(String resourceName) {
        Yaml yaml = new Yaml();
        try (InputStream in = ClientConfig.class
                .getClassLoader().getResourceAsStream(resourceName)) {
            Map<String, Object> data = yaml.load(in);
            ClientConfig cfg = new ClientConfig();
            cfg.nodes = (List<Map<String, String>>) data.get("nodes");
            return cfg;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load " + resourceName, e);
        }
    }
}
