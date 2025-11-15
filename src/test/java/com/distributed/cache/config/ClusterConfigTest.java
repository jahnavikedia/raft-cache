package com.distributed.cache.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for ClusterConfig.
 *
 * Tests cover:
 * - Loading valid configuration from file and classpath
 * - Parsing all configuration fields correctly
 * - Validation of timing parameters
 * - Validation of cluster topology
 * - Error handling for invalid configurations
 */
class ClusterConfigTest {

    @Test
    void testLoadFromClasspath() throws IOException {
        // Load the test configuration from classpath
        ClusterConfig config = ClusterConfig.loadFromClasspath("test-cluster-config.yaml");

        assertNotNull(config);
        assertEquals(150, config.getElectionTimeoutMin());
        assertEquals(300, config.getElectionTimeoutMax());
        assertEquals(50, config.getHeartbeatInterval());
        assertEquals(3, config.getClusterSize());
        assertEquals(2, config.getMajoritySize());
    }

    @Test
    void testLoadFromFile(@TempDir Path tempDir) throws IOException {
        // Create a temporary config file
        Path configPath = tempDir.resolve("cluster-config.yaml");
        String yamlContent = """
                cluster:
                  election_timeout_min: 200
                  election_timeout_max: 400
                  heartbeat_interval: 60
                  nodes:
                    - id: "node1"
                      host: "192.168.1.10"
                      port: 8001
                    - id: "node2"
                      host: "192.168.1.11"
                      port: 8001
                    - id: "node3"
                      host: "192.168.1.12"
                      port: 8001
                """;
        Files.writeString(configPath, yamlContent);

        // Load configuration
        ClusterConfig config = ClusterConfig.load(configPath.toString());

        assertNotNull(config);
        assertEquals(200, config.getElectionTimeoutMin());
        assertEquals(400, config.getElectionTimeoutMax());
        assertEquals(60, config.getHeartbeatInterval());
        assertEquals(3, config.getClusterSize());
    }

    @Test
    void testGetAllNodes() throws IOException {
        ClusterConfig config = ClusterConfig.loadFromClasspath("test-cluster-config.yaml");

        List<NodeInfo> nodes = config.getAllNodes();
        assertEquals(3, nodes.size());

        // Verify node IDs
        assertTrue(nodes.stream().anyMatch(n -> n.getId().equals("test-node1")));
        assertTrue(nodes.stream().anyMatch(n -> n.getId().equals("test-node2")));
        assertTrue(nodes.stream().anyMatch(n -> n.getId().equals("test-node3")));
    }

    @Test
    void testGetNodeById() throws IOException {
        ClusterConfig config = ClusterConfig.loadFromClasspath("test-cluster-config.yaml");

        NodeInfo node1 = config.getNodeById("test-node1");
        assertNotNull(node1);
        assertEquals("test-node1", node1.getId());
        assertEquals("localhost", node1.getHost());
        assertEquals(9001, node1.getPort());

        NodeInfo node2 = config.getNodeById("test-node2");
        assertNotNull(node2);
        assertEquals(9002, node2.getPort());

        // Non-existent node
        NodeInfo nonExistent = config.getNodeById("non-existent");
        assertNull(nonExistent);
    }

    @Test
    void testGetOtherNodes() throws IOException {
        ClusterConfig config = ClusterConfig.loadFromClasspath("test-cluster-config.yaml");

        List<NodeInfo> others = config.getOtherNodes("test-node1");
        assertEquals(2, others.size());
        assertTrue(others.stream().noneMatch(n -> n.getId().equals("test-node1")));
        assertTrue(others.stream().anyMatch(n -> n.getId().equals("test-node2")));
        assertTrue(others.stream().anyMatch(n -> n.getId().equals("test-node3")));
    }

    @Test
    void testGetMajoritySize() throws IOException {
        ClusterConfig config = ClusterConfig.loadFromClasspath("test-cluster-config.yaml");

        // 3 nodes → majority = 2
        assertEquals(2, config.getMajoritySize());
    }

    @Test
    void testMajoritySizeFor5Nodes(@TempDir Path tempDir) throws IOException {
        Path configPath = tempDir.resolve("5-node-config.yaml");
        String yamlContent = """
                cluster:
                  election_timeout_min: 150
                  election_timeout_max: 300
                  heartbeat_interval: 50
                  nodes:
                    - id: "node1"
                      host: "localhost"
                      port: 8001
                    - id: "node2"
                      host: "localhost"
                      port: 8002
                    - id: "node3"
                      host: "localhost"
                      port: 8003
                    - id: "node4"
                      host: "localhost"
                      port: 8004
                    - id: "node5"
                      host: "localhost"
                      port: 8005
                """;
        Files.writeString(configPath, yamlContent);

        ClusterConfig config = ClusterConfig.load(configPath.toString());

        // 5 nodes → majority = 3
        assertEquals(3, config.getMajoritySize());
    }

    @Test
    void testFileNotFound() {
        assertThrows(IOException.class, () -> {
            ClusterConfig.load("/non/existent/path/config.yaml");
        });
    }

    @Test
    void testClasspathResourceNotFound() {
        assertThrows(IOException.class, () -> {
            ClusterConfig.loadFromClasspath("non-existent-config.yaml");
        });
    }

    @Test
    void testInvalidYamlSyntax(@TempDir Path tempDir) throws IOException {
        Path configPath = tempDir.resolve("invalid-syntax.yaml");
        String invalidYaml = """
                cluster:
                  election_timeout_min: 150
                  nodes:
                    - id: "node1
                      invalid syntax here
                """;
        Files.writeString(configPath, invalidYaml);

        assertThrows(Exception.class, () -> {
            ClusterConfig.load(configPath.toString());
        });
    }

    @Test
    void testMissingClusterRoot(@TempDir Path tempDir) throws IOException {
        Path configPath = tempDir.resolve("no-cluster-root.yaml");
        String yamlContent = """
                election_timeout_min: 150
                election_timeout_max: 300
                heartbeat_interval: 50
                nodes:
                  - id: "node1"
                    host: "localhost"
                    port: 8001
                """;
        Files.writeString(configPath, yamlContent);

        assertThrows(IllegalArgumentException.class, () -> {
            ClusterConfig.load(configPath.toString());
        });
    }

    @Test
    void testDuplicateNodeIds(@TempDir Path tempDir) throws IOException {
        // When building the nodeIndex map, Collectors.toMap() throws an exception
        // on duplicate keys. This catches duplicate node IDs.

        Path configPath = tempDir.resolve("duplicate-ids.yaml");
        String yamlContent = """
                cluster:
                  election_timeout_min: 150
                  election_timeout_max: 300
                  heartbeat_interval: 50
                  nodes:
                    - id: "node1"
                      host: "localhost"
                      port: 8001
                    - id: "node1"
                      host: "localhost"
                      port: 8002
                    - id: "node2"
                      host: "localhost"
                      port: 8003
                """;
        Files.writeString(configPath, yamlContent);

        // Should throw IllegalArgumentException (wrapped from IllegalStateException)
        // because Collectors.toMap() detects duplicate key "node1"
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ClusterConfig.load(configPath.toString());
        });

        assertTrue(exception.getMessage().contains("Duplicate key"));
    }

    @Test
    void testDuplicateAddresses(@TempDir Path tempDir) throws IOException {
        Path configPath = tempDir.resolve("duplicate-addresses.yaml");
        String yamlContent = """
                cluster:
                  election_timeout_min: 150
                  election_timeout_max: 300
                  heartbeat_interval: 50
                  nodes:
                    - id: "node1"
                      host: "localhost"
                      port: 8001
                    - id: "node2"
                      host: "localhost"
                      port: 8001
                    - id: "node3"
                      host: "localhost"
                      port: 8003
                """;
        Files.writeString(configPath, yamlContent);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ClusterConfig.load(configPath.toString());
        });

        assertTrue(exception.getMessage().contains("Duplicate address"));
    }

    @Test
    void testInvalidElectionTimeoutRange(@TempDir Path tempDir) throws IOException {
        Path configPath = tempDir.resolve("invalid-timeout-range.yaml");
        String yamlContent = """
                cluster:
                  election_timeout_min: 300
                  election_timeout_max: 150
                  heartbeat_interval: 50
                  nodes:
                    - id: "node1"
                      host: "localhost"
                      port: 8001
                    - id: "node2"
                      host: "localhost"
                      port: 8002
                    - id: "node3"
                      host: "localhost"
                      port: 8003
                """;
        Files.writeString(configPath, yamlContent);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ClusterConfig.load(configPath.toString());
        });

        assertTrue(exception.getMessage().contains("must be greater than min"));
    }

    @Test
    void testNegativeHeartbeatInterval(@TempDir Path tempDir) throws IOException {
        Path configPath = tempDir.resolve("negative-heartbeat.yaml");
        String yamlContent = """
                cluster:
                  election_timeout_min: 150
                  election_timeout_max: 300
                  heartbeat_interval: -50
                  nodes:
                    - id: "node1"
                      host: "localhost"
                      port: 8001
                    - id: "node2"
                      host: "localhost"
                      port: 8002
                    - id: "node3"
                      host: "localhost"
                      port: 8003
                """;
        Files.writeString(configPath, yamlContent);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ClusterConfig.load(configPath.toString());
        });

        assertTrue(exception.getMessage().contains("Heartbeat interval must be positive"));
    }

    @Test
    void testHeartbeatTooCloseToDElectionTimeout(@TempDir Path tempDir) throws IOException {
        Path configPath = tempDir.resolve("heartbeat-too-close.yaml");
        String yamlContent = """
                cluster:
                  election_timeout_min: 150
                  election_timeout_max: 300
                  heartbeat_interval: 100
                  nodes:
                    - id: "node1"
                      host: "localhost"
                      port: 8001
                    - id: "node2"
                      host: "localhost"
                      port: 8002
                    - id: "node3"
                      host: "localhost"
                      port: 8003
                """;
        Files.writeString(configPath, yamlContent);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ClusterConfig.load(configPath.toString());
        });

        assertTrue(exception.getMessage().contains("must be much smaller than election timeout"));
    }

    @Test
    void testEmptyNodeList(@TempDir Path tempDir) throws IOException {
        Path configPath = tempDir.resolve("empty-nodes.yaml");
        String yamlContent = """
                cluster:
                  election_timeout_min: 150
                  election_timeout_max: 300
                  heartbeat_interval: 50
                  nodes: []
                """;
        Files.writeString(configPath, yamlContent);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ClusterConfig.load(configPath.toString());
        });

        assertTrue(exception.getMessage().contains("at least one node"));
    }

    @Test
    void testToString() throws IOException {
        ClusterConfig config = ClusterConfig.loadFromClasspath("test-cluster-config.yaml");

        String str = config.toString();
        assertTrue(str.contains("electionTimeoutMin=150"));
        assertTrue(str.contains("electionTimeoutMax=300"));
        assertTrue(str.contains("heartbeatInterval=50"));
        assertTrue(str.contains("nodes=3"));
        assertTrue(str.contains("majority=2"));
    }

    @Test
    void testSingleNodeCluster(@TempDir Path tempDir) throws IOException {
        // Single node is allowed (for testing) but should log a warning
        Path configPath = tempDir.resolve("single-node.yaml");
        String yamlContent = """
                cluster:
                  election_timeout_min: 150
                  election_timeout_max: 300
                  heartbeat_interval: 50
                  nodes:
                    - id: "node1"
                      host: "localhost"
                      port: 8001
                """;
        Files.writeString(configPath, yamlContent);

        ClusterConfig config = ClusterConfig.load(configPath.toString());

        assertEquals(1, config.getClusterSize());
        assertEquals(1, config.getMajoritySize());
    }

    @Test
    void testNodeInfoValidation(@TempDir Path tempDir) throws IOException {
        // Test that NodeInfo validation is triggered
        Path configPath = tempDir.resolve("invalid-port.yaml");
        String yamlContent = """
                cluster:
                  election_timeout_min: 150
                  election_timeout_max: 300
                  heartbeat_interval: 50
                  nodes:
                    - id: "node1"
                      host: "localhost"
                      port: 70000
                    - id: "node2"
                      host: "localhost"
                      port: 8002
                    - id: "node3"
                      host: "localhost"
                      port: 8003
                """;
        Files.writeString(configPath, yamlContent);

        assertThrows(IllegalArgumentException.class, () -> {
            ClusterConfig.load(configPath.toString());
        });
    }
}