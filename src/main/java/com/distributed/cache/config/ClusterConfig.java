package com.distributed.cache.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ClusterConfig loads and validates Raft cluster configuration from YAML.
 *
 * This class is responsible for:
 * - Loading cluster configuration from YAML file (from classpath or file system)
 * - Validating configuration (duplicate IDs, port ranges, minimum nodes, etc.)
 * - Providing convenient access to node information and timing parameters
 *
 * Configuration format:
 * <pre>
 * cluster:
 *   election_timeout_min: 150
 *   election_timeout_max: 300
 *   heartbeat_interval: 50
 *   nodes:
 *     - id: "node1"
 *       host: "localhost"
 *       port: 8001
 *     - id: "node2"
 *       host: "localhost"
 *       port: 8002
 * </pre>
 *
 * Design decisions:
 * - Immutable after loading (all fields final)
 * - Fail-fast validation on load (throws exception for invalid config)
 * - Jackson YAML for parsing (industry standard, battle-tested)
 * - Supports both classpath and file system loading
 */
public class ClusterConfig {
    private static final Logger logger = LoggerFactory.getLogger(ClusterConfig.class);

    // Raft timing parameters (in milliseconds)
    private final int electionTimeoutMin;
    private final int electionTimeoutMax;
    private final int heartbeatInterval;

    // Cluster topology
    private final List<NodeInfo> nodes;

    // Index for fast lookup by node ID
    private final Map<String, NodeInfo> nodeIndex;

    /**
     * Constructor for Jackson deserialization.
     *
     * @param electionTimeoutMin Minimum election timeout in ms (e.g., 150)
     * @param electionTimeoutMax Maximum election timeout in ms (e.g., 300)
     * @param heartbeatInterval  Heartbeat interval in ms (e.g., 50)
     * @param nodes              List of nodes in the cluster
     */
    @JsonCreator
    public ClusterConfig(
            @JsonProperty("election_timeout_min") int electionTimeoutMin,
            @JsonProperty("election_timeout_max") int electionTimeoutMax,
            @JsonProperty("heartbeat_interval") int heartbeatInterval,
            @JsonProperty("nodes") List<NodeInfo> nodes) {

        this.electionTimeoutMin = electionTimeoutMin;
        this.electionTimeoutMax = electionTimeoutMax;
        this.heartbeatInterval = heartbeatInterval;
        this.nodes = nodes != null ? List.copyOf(nodes) : List.of();

        // Build index for O(1) lookup by node ID
        this.nodeIndex = this.nodes.stream()
                .collect(Collectors.toMap(NodeInfo::getId, node -> node));

        // Validate configuration
        validate();

        logger.info("Loaded cluster configuration: {} nodes, election timeout {}-{}ms, heartbeat {}ms",
                nodes.size(), electionTimeoutMin, electionTimeoutMax, heartbeatInterval);
    }

    /**
     * Load cluster configuration from a YAML file on the file system.
     *
     * @param configPath Path to YAML configuration file
     * @return Loaded and validated ClusterConfig
     * @throws IOException              if file cannot be read
     * @throws IllegalArgumentException if configuration is invalid
     */
    public static ClusterConfig load(String configPath) throws IOException {
        logger.info("Loading cluster configuration from file: {}", configPath);

        File configFile = new File(configPath);
        if (!configFile.exists()) {
            throw new IOException("Configuration file not found: " + configPath);
        }

        if (!configFile.canRead()) {
            throw new IOException("Cannot read configuration file: " + configPath);
        }

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        // Parse the wrapper structure
        Map<String, Object> wrapper = mapper.readValue(configFile, Map.class);
        if (!wrapper.containsKey("cluster")) {
            throw new IllegalArgumentException("Configuration must contain 'cluster' root element");
        }

        // Extract cluster section and parse as ClusterConfig
        Map<String, Object> clusterData = (Map<String, Object>) wrapper.get("cluster");
        ClusterConfig config = mapper.convertValue(clusterData, ClusterConfig.class);

        logger.info("Successfully loaded configuration from {}", configPath);
        return config;
    }

    /**
     * Load cluster configuration from classpath resources.
     *
     * This is useful for packaged JARs where config is bundled as a resource.
     *
     * @param resourcePath Path to resource (e.g., "cluster-config.yaml")
     * @return Loaded and validated ClusterConfig
     * @throws IOException              if resource cannot be read
     * @throws IllegalArgumentException if configuration is invalid
     */
    public static ClusterConfig loadFromClasspath(String resourcePath) throws IOException {
        logger.info("Loading cluster configuration from classpath: {}", resourcePath);

        InputStream inputStream = ClusterConfig.class.getClassLoader().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new IOException("Configuration resource not found in classpath: " + resourcePath);
        }

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        // Parse the wrapper structure
        Map<String, Object> wrapper = mapper.readValue(inputStream, Map.class);
        if (!wrapper.containsKey("cluster")) {
            throw new IllegalArgumentException("Configuration must contain 'cluster' root element");
        }

        // Extract cluster section and parse as ClusterConfig
        Map<String, Object> clusterData = (Map<String, Object>) wrapper.get("cluster");
        ClusterConfig config = mapper.convertValue(clusterData, ClusterConfig.class);

        logger.info("Successfully loaded configuration from classpath resource");
        return config;
    }

    /**
     * Validate the loaded configuration.
     *
     * Checks:
     * - Timing parameters are positive and sensible
     * - Election timeout range is valid
     * - At least 3 nodes in cluster (minimum for Raft quorum)
     * - No duplicate node IDs
     * - No duplicate ports on same host
     *
     * @throws IllegalArgumentException if validation fails
     */
    private void validate() {
        List<String> errors = new ArrayList<>();

        // Validate timing parameters
        if (heartbeatInterval <= 0) {
            errors.add("Heartbeat interval must be positive, got: " + heartbeatInterval);
        }

        if (electionTimeoutMin <= 0) {
            errors.add("Election timeout min must be positive, got: " + electionTimeoutMin);
        }

        if (electionTimeoutMax <= 0) {
            errors.add("Election timeout max must be positive, got: " + electionTimeoutMax);
        }

        if (electionTimeoutMax <= electionTimeoutMin) {
            errors.add("Election timeout max (" + electionTimeoutMax +
                    ") must be greater than min (" + electionTimeoutMin + ")");
        }

        // Raft safety: heartbeat interval must be << election timeout
        // Rule of thumb: heartbeat should be at least 3x faster than election timeout
        // Allow exactly heartbeat * 3 == election_timeout (that's the sweet spot)
        if (heartbeatInterval * 3 > electionTimeoutMin) {
            errors.add("Heartbeat interval (" + heartbeatInterval +
                    "ms) must be much smaller than election timeout min (" +
                    electionTimeoutMin + "ms). Recommended: heartbeat * 3 <= election_timeout_min");
        }

        // Validate cluster size
        if (nodes == null || nodes.isEmpty()) {
            errors.add("Cluster must have at least one node");
        } else if (nodes.size() < 3) {
            logger.warn("Cluster has only {} nodes. Raft requires at least 3 nodes for fault tolerance. " +
                    "This configuration is only suitable for testing.", nodes.size());
        }

        // Validate no duplicate node IDs
        Set<String> nodeIds = new HashSet<>();
        for (NodeInfo node : nodes) {
            if (!nodeIds.add(node.getId())) {
                errors.add("Duplicate node ID found: " + node.getId());
            }
        }

        // Validate no duplicate host:port combinations
        Set<String> addresses = new HashSet<>();
        for (NodeInfo node : nodes) {
            String address = node.getAddress();
            if (!addresses.add(address)) {
                errors.add("Duplicate address found: " + address);
            }
        }

        // If any validation errors, throw exception with all error messages
        if (!errors.isEmpty()) {
            String errorMessage = "Cluster configuration validation failed:\n" +
                    errors.stream()
                            .map(e -> "  - " + e)
                            .collect(Collectors.joining("\n"));
            throw new IllegalArgumentException(errorMessage);
        }

        logger.debug("Cluster configuration validation passed");
    }

    /**
     * Get node information by node ID.
     *
     * @param nodeId The node ID to look up
     * @return NodeInfo for the specified node, or null if not found
     */
    public NodeInfo getNodeById(String nodeId) {
        return nodeIndex.get(nodeId);
    }

    /**
     * Get all nodes in the cluster.
     *
     * @return Unmodifiable list of all nodes
     */
    public List<NodeInfo> getAllNodes() {
        return nodes;
    }

    /**
     * Get all nodes except the specified one.
     *
     * This is useful for getting the list of peers (all nodes except self).
     *
     * @param excludeId Node ID to exclude
     * @return List of all nodes except the one with excludeId
     */
    public List<NodeInfo> getOtherNodes(String excludeId) {
        return nodes.stream()
                .filter(node -> !node.getId().equals(excludeId))
                .collect(Collectors.toList());
    }

    /**
     * @return Minimum election timeout in milliseconds
     */
    public int getElectionTimeoutMin() {
        return electionTimeoutMin;
    }

    /**
     * @return Maximum election timeout in milliseconds
     */
    public int getElectionTimeoutMax() {
        return electionTimeoutMax;
    }

    /**
     * @return Heartbeat interval in milliseconds
     */
    public int getHeartbeatInterval() {
        return heartbeatInterval;
    }

    /**
     * @return Total number of nodes in the cluster
     */
    public int getClusterSize() {
        return nodes.size();
    }

    /**
     * Calculate the majority (quorum) size for this cluster.
     *
     * Raft requires majority agreement for leader election and log commits.
     * Majority = floor(N/2) + 1
     *
     * Examples:
     * - 3 nodes → majority = 2
     * - 5 nodes → majority = 3
     * - 7 nodes → majority = 4
     *
     * @return Number of nodes required for quorum
     */
    public int getMajoritySize() {
        return (nodes.size() / 2) + 1;
    }

    @Override
    public String toString() {
        return "ClusterConfig{" +
                "electionTimeoutMin=" + electionTimeoutMin +
                "ms, electionTimeoutMax=" + electionTimeoutMax +
                "ms, heartbeatInterval=" + heartbeatInterval +
                "ms, nodes=" + nodes.size() +
                ", majority=" + getMajoritySize() +
                '}';
    }
}