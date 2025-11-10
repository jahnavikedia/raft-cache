package com.distributed.cache;

import com.distributed.cache.raft.RaftNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the Distributed Raft Cache
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("Starting Distributed Raft Cache...");
        
        // Parse command line arguments
        if (args.length < 2) {
            System.err.println("Usage: java -jar raft-cache.jar <node-id> <port>");
            System.err.println("Example: java -jar raft-cache.jar node1 8001");
            System.exit(1);
        }
        
        String nodeId = args[0];
        int port = Integer.parseInt(args[1]);
        
        try {
            // Initialize and start the Raft node
            RaftNode node = new RaftNode(nodeId, port);
            node.start();
            
            logger.info("Node {} started on port {}", nodeId, port);
            
            // Keep the application running
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down node {}...", nodeId);
                node.shutdown();
            }));
            
            // Keep main thread alive
            Thread.currentThread().join();
            
        } catch (Exception e) {
            logger.error("Failed to start node: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
