package com.distributed.cache.demo;

import com.distributed.cache.raft.RaftNode;
import com.distributed.cache.raft.RaftState;
import com.distributed.cache.store.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

/**
 * Demo application for the Raft-based Key-Value Store.
 *
 * Usage:
 *   1. Start 3 terminals
 *   2. In each terminal, run: java -cp target/raft-cache-1.0-SNAPSHOT.jar com.distributed.cache.demo.KVStoreDemo <node-id> <port>
 *      Terminal 1: java ... KVStoreDemo node1 7001
 *      Terminal 2: java ... KVStoreDemo node2 7002
 *      Terminal 3: java ... KVStoreDemo node3 7003
 *   3. Wait for leader election
 *   4. Use commands on the leader: PUT key value, GET key, DELETE key, STATUS, QUIT
 */
public class KVStoreDemo {
    private static final Logger logger = LoggerFactory.getLogger(KVStoreDemo.class);

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java KVStoreDemo <node-id> <port>");
            System.err.println("Example: java KVStoreDemo node1 7001");
            System.exit(1);
        }

        String nodeId = args[0];
        int port = Integer.parseInt(args[1]);

        // Create node
        RaftNode node = new RaftNode(nodeId, port);

        // Configure peers based on node ID
        Map<String, String> peers = new HashMap<>();
        if (nodeId.equals("node1")) {
            peers.put("node2", "localhost:7002");
            peers.put("node3", "localhost:7003");
        } else if (nodeId.equals("node2")) {
            peers.put("node1", "localhost:7001");
            peers.put("node3", "localhost:7003");
        } else if (nodeId.equals("node3")) {
            peers.put("node1", "localhost:7001");
            peers.put("node2", "localhost:7002");
        }

        node.configurePeers(peers);

        // Start node
        logger.info("Starting {} on port {}", nodeId, port);
        node.start();

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down {}...", nodeId);
            node.shutdown();
        }));

        // Wait for leader election
        logger.info("Waiting for leader election...");
        Thread.sleep(2000);

        // Interactive CLI
        KeyValueStore kvStore = node.getKvStore();
        Scanner scanner = new Scanner(System.in);

        printHelp();

        while (true) {
            printPrompt(node);
            String line = scanner.nextLine().trim();

            if (line.isEmpty()) {
                continue;
            }

            String[] parts = line.split("\\s+", 3);
            String command = parts[0].toUpperCase();

            try {
                switch (command) {
                    case "PUT":
                        if (parts.length < 3) {
                            System.out.println("Usage: PUT <key> <value>");
                            break;
                        }
                        handlePut(kvStore, parts[1], parts[2], nodeId);
                        break;

                    case "GET":
                        if (parts.length < 2) {
                            System.out.println("Usage: GET <key>");
                            break;
                        }
                        handleGet(kvStore, parts[1]);
                        break;

                    case "DELETE":
                        if (parts.length < 2) {
                            System.out.println("Usage: DELETE <key>");
                            break;
                        }
                        handleDelete(kvStore, parts[1], nodeId);
                        break;

                    case "STATUS":
                        handleStatus(node);
                        break;

                    case "HELP":
                        printHelp();
                        break;

                    case "QUIT":
                    case "EXIT":
                        logger.info("Exiting...");
                        node.shutdown();
                        System.exit(0);
                        break;

                    default:
                        System.out.println("Unknown command: " + command);
                        System.out.println("Type HELP for available commands");
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    private static void handlePut(KeyValueStore kvStore, String key, String value, String nodeId) {
        try {
            System.out.println("Executing PUT " + key + " = " + value);
            long seq = System.currentTimeMillis();
            CompletableFuture<String> future = kvStore.put(key, value, nodeId, seq);
            String result = future.get();
            System.out.println("SUCCESS: " + key + " = " + result);
        } catch (KeyValueStore.NotLeaderException e) {
            System.err.println("ERROR: This node is not the leader. Please try on the leader node.");
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
        }
    }

    private static void handleGet(KeyValueStore kvStore, String key) {
        String value = kvStore.get(key);
        if (value != null) {
            System.out.println(key + " = " + value);
        } else {
            System.out.println(key + " not found");
        }
    }

    private static void handleDelete(KeyValueStore kvStore, String key, String nodeId) {
        try {
            System.out.println("Executing DELETE " + key);
            long seq = System.currentTimeMillis();
            CompletableFuture<Boolean> future = kvStore.delete(key, nodeId, seq);
            Boolean result = future.get();
            if (result) {
                System.out.println("SUCCESS: Deleted " + key);
            } else {
                System.out.println("FAILED: Could not delete " + key);
            }
        } catch (KeyValueStore.NotLeaderException e) {
            System.err.println("ERROR: This node is not the leader. Please try on the leader node.");
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
        }
    }

    private static void handleStatus(RaftNode node) {
        System.out.println("===== Node Status =====");
        System.out.println("Node ID: " + node.getNodeId());
        System.out.println("State: " + node.getState());
        System.out.println("Term: " + node.getCurrentTerm());
        System.out.println("Voted For: " + node.getVotedFor());
        System.out.println("Commit Index: " + node.getCommitIndex());
        System.out.println("Log Size: " + node.getRaftLog().size());
        System.out.println("Last Applied: " + node.getRaftLog().getLastApplied());
        System.out.println("Connected Peers: " + node.getConnectedPeerCount());
        System.out.println("KV Store Size: " + node.getKvStore().size());
        System.out.println("======================");
    }

    private static void printPrompt(RaftNode node) {
        String state = node.getState() == RaftState.LEADER ? "LEADER" :
                      node.getState() == RaftState.CANDIDATE ? "CANDIDATE" : "FOLLOWER";
        System.out.print(String.format("[%s:%s] > ", node.getNodeId(), state));
    }

    private static void printHelp() {
        System.out.println("\n===== Raft Key-Value Store Commands =====");
        System.out.println("PUT <key> <value>   - Insert or update a key-value pair (leader only)");
        System.out.println("GET <key>           - Retrieve value for a key (local read)");
        System.out.println("DELETE <key>        - Delete a key (leader only)");
        System.out.println("STATUS              - Show node status and statistics");
        System.out.println("HELP                - Show this help message");
        System.out.println("QUIT                - Exit the program");
        System.out.println("==========================================\n");
    }
}
