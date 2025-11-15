package com.distributed.cache.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * NodeInfo represents a single node in the Raft cluster.
 *
 * This POJO holds the essential information needed to identify and connect to a peer node:
 * - id: Unique identifier for the node (e.g., "node1", "node2")
 * - host: Hostname or IP address where the node is reachable
 * - port: TCP port the node listens on
 *
 * Design decisions:
 * - Immutable: All fields are final to prevent accidental modification
 * - Jackson annotations: Enables seamless YAML/JSON deserialization
 * - equals/hashCode: Based on id only (id uniquely identifies a node)
 * - Validation: Constructor validates inputs to fail fast on invalid config
 */
public class NodeInfo {
    private final String id;
    private final String host;
    private final int port;

    /**
     * Constructor for Jackson deserialization and programmatic creation.
     *
     * @param id   Unique node identifier (must not be null or empty)
     * @param host Hostname or IP address (must not be null or empty)
     * @param port TCP port number (must be in valid range 1024-65535)
     * @throws IllegalArgumentException if any parameter is invalid
     */
    @JsonCreator
    public NodeInfo(
            @JsonProperty("id") String id,
            @JsonProperty("host") String host,
            @JsonProperty("port") int port) {

        // Validate id
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Node id cannot be null or empty");
        }

        // Validate host
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Node host cannot be null or empty");
        }

        // Validate port range (use 1024-65535 to avoid reserved ports)
        if (port < 1024 || port > 65535) {
            throw new IllegalArgumentException(
                "Node port must be between 1024 and 65535, got: " + port);
        }

        this.id = id.trim();
        this.host = host.trim();
        this.port = port;
    }

    /**
     * @return The unique identifier for this node
     */
    public String getId() {
        return id;
    }

    /**
     * @return The hostname or IP address of this node
     */
    public String getHost() {
        return host;
    }

    /**
     * @return The TCP port this node listens on
     */
    public int getPort() {
        return port;
    }

    /**
     * @return A network address string in the format "host:port"
     */
    public String getAddress() {
        return host + ":" + port;
    }

    /**
     * Two NodeInfo objects are equal if they have the same id.
     * This is because id uniquely identifies a node in the cluster.
     *
     * Note: We intentionally don't include host/port in equals() because
     * a node's network location might change (e.g., during migration),
     * but its logical identity (id) remains the same.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeInfo nodeInfo = (NodeInfo) o;
        return Objects.equals(id, nodeInfo.id);
    }

    /**
     * Hash code based on id only (consistent with equals()).
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * @return A human-readable string representation of this node
     */
    @Override
    public String toString() {
        return "NodeInfo{" +
                "id='" + id + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                '}';
    }
}