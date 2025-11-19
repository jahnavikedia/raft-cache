package com.distributed.cache;

import com.distributed.cache.raft.RaftNode;
import com.distributed.cache.raft.api.CacheRESTServer;
import com.distributed.cache.raft.config.NodeConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the Distributed Raft Cache
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("Starting Distributed Raft Cache...");

        try {
            // ----------------------------------------------------------
            // Parse command line arguments
            // ----------------------------------------------------------
            String configPath = null;

            if (args.length >= 2 && args[0].equals("--config")) {
                configPath = args[1];
            } else {
                System.err.println("Usage: java -jar raft-cache.jar --config <path-to-yaml>");
                System.exit(1);
            }

            // ----------------------------------------------------------
            // Load node configuration
            // ----------------------------------------------------------
            NodeConfiguration config = NodeConfiguration.load(configPath);
            logger.info("Loaded configuration for {}: raftPort={}, httpPort={}, dataDir={}",
                    config.getNodeId(), config.getRaftPort(), config.getHttpPort(), config.getDataDir());

            // ----------------------------------------------------------
            // Initialize Raft node and HTTP server
            // ----------------------------------------------------------
            RaftNode node = new RaftNode(config.getNodeId(), config.getRaftPort());
            node.configurePeers(config.getPeerMap());
            node.start();

            CacheRESTServer httpServer = new CacheRESTServer(node, config.getHttpPort(), config.getHttpPeerMap());
            httpServer.start();

            logger.info("Node {} started successfully (Raft on port {}, HTTP on port {})",
                    config.getNodeId(), config.getRaftPort(), config.getHttpPort());

            // ----------------------------------------------------------
            // Handle graceful shutdown
            // ----------------------------------------------------------
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down node {}...", config.getNodeId());
                httpServer.stop();
                node.shutdown();
            }));

            // Keep the JVM running
            Thread.currentThread().join();

        } catch (Exception e) {
            logger.error("Failed to start node: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
