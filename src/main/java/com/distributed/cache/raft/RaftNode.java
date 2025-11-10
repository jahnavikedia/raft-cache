package com.distributed.cache.raft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a single node in the Raft cluster
 */
public class RaftNode {
    private static final Logger logger = LoggerFactory.getLogger(RaftNode.class);
    
    private final String nodeId;
    private final int port;
    private RaftState state;
    
    public RaftNode(String nodeId, int port) {
        this.nodeId = nodeId;
        this.port = port;
        this.state = RaftState.FOLLOWER;
        logger.info("RaftNode initialized: id={}, port={}", nodeId, port);
    }
    
    /**
     * Start the Raft node
     */
    public void start() {
        logger.info("Starting Raft node: {}", nodeId);
        // TODO: Initialize network server
        // TODO: Start election timer
        // TODO: Initialize log
    }
    
    /**
     * Shutdown the Raft node gracefully
     */
    public void shutdown() {
        logger.info("Shutting down Raft node: {}", nodeId);
        // TODO: Close network connections
        // TODO: Persist state
    }
    
    public String getNodeId() {
        return nodeId;
    }
    
    public int getPort() {
        return port;
    }
    
    public RaftState getState() {
        return state;
    }
}
