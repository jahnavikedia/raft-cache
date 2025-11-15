package com.distributed.cache.demo;

import com.distributed.cache.raft.RaftNode;
import com.distributed.cache.raft.RaftState;

import java.util.Map;
import java.util.Scanner;

/**
 * Interactive demo to see Raft consensus in action!
 *
 * This demo creates a 3-node Raft cluster and lets you observe:
 * - Leader election
 * - Heartbeat mechanism
 * - Failure detection and recovery
 */
public class RaftDemo {

    private static RaftNode node1;
    private static RaftNode node2;
    private static RaftNode node3;

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("   Raft Consensus Algorithm Demo");
        System.out.println("========================================\n");

        // Create 3 nodes
        System.out.println("Creating 3-node Raft cluster...");
        node1 = new RaftNode("Alice", 9001);
        node2 = new RaftNode("Bob", 9002);
        node3 = new RaftNode("Charlie", 9003);

        // Configure peers
        node1.configurePeers(Map.of("Bob", "localhost:9002", "Charlie", "localhost:9003"));
        node2.configurePeers(Map.of("Alice", "localhost:9001", "Charlie", "localhost:9003"));
        node3.configurePeers(Map.of("Alice", "localhost:9001", "Bob", "localhost:9002"));

        // Start all nodes
        System.out.println("Starting nodes...");
        node1.start();
        node2.start();
        node3.start();

        // Wait for connections
        System.out.println("Waiting for peer connections...\n");
        Thread.sleep(2000);

        System.out.println("Cluster is ready! Watching for leader election...\n");

        // Run interactive demo
        runDemo();
    }

    private static void runDemo() throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\n========================================");
            System.out.println("Options:");
            System.out.println("  1 - Show cluster status");
            System.out.println("  2 - Show detailed statistics");
            System.out.println("  3 - Kill the leader (simulate failure)");
            System.out.println("  4 - Restart a dead node");
            System.out.println("  5 - Watch cluster for 10 seconds");
            System.out.println("  q - Quit");
            System.out.println("========================================");
            System.out.print("\nYour choice: ");

            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    showStatus();
                    break;
                case "2":
                    showDetailedStats();
                    break;
                case "3":
                    killLeader();
                    break;
                case "4":
                    restartNode(scanner);
                    break;
                case "5":
                    watchCluster();
                    break;
                case "q":
                case "Q":
                    shutdown();
                    return;
                default:
                    System.out.println("Invalid choice!");
            }
        }
    }

    private static void showStatus() {
        System.out.println("\n=== CLUSTER STATUS ===");
        printNodeStatus("Alice", node1);
        printNodeStatus("Bob", node2);
        printNodeStatus("Charlie", node3);
    }

    private static void printNodeStatus(String name, RaftNode node) {
        if (node == null) {
            System.out.println(name + ": [DEAD]");
            return;
        }

        String status = String.format("%-8s | State: %-10s | Term: %d | Connections: %d/2",
                name,
                node.getState(),
                node.getCurrentTerm(),
                node.getConnectedPeerCount());

        if (node.getState() == RaftState.LEADER) {
            status += " ⭐ LEADER";
        }

        System.out.println(status);
    }

    private static void showDetailedStats() {
        System.out.println("\n=== DETAILED STATISTICS ===\n");
        printDetailedStats("Alice", node1);
        printDetailedStats("Bob", node2);
        printDetailedStats("Charlie", node3);
    }

    private static void printDetailedStats(String name, RaftNode node) {
        if (node == null) {
            System.out.println(name + ": [DEAD]\n");
            return;
        }

        System.out.println(name + ":");
        System.out.println("  State: " + node.getState());
        System.out.println("  Term: " + node.getCurrentTerm());
        System.out.println("  Voted for: " + (node.getVotedFor() != null ? node.getVotedFor() : "nobody"));
        System.out.println("  Connections: " + node.getConnectedPeerCount() + "/2");
        System.out.println("  Elections started: " + node.getElectionsStarted());

        if (node.getState() == RaftState.LEADER) {
            System.out.println("  Heartbeats sent: " + node.getHeartbeatsSent());
        } else {
            System.out.println("  Heartbeats received: " + node.getHeartbeatsReceived());
            long timeSinceLastHB = System.currentTimeMillis() - node.getLastHeartbeatReceived();
            System.out.println("  Time since last heartbeat: " + timeSinceLastHB + "ms");
        }
        System.out.println();
    }

    private static void killLeader() throws Exception {
        System.out.println("\n=== KILLING LEADER ===");

        RaftNode leader = findLeader();
        if (leader == null) {
            System.out.println("No leader found yet! Wait a moment for election to complete.");
            return;
        }

        String leaderName = getNodeName(leader);
        System.out.println("Leader is: " + leaderName);
        System.out.println("Shutting down " + leaderName + "...");

        leader.shutdown();
        setNodeToNull(leader);

        System.out.println(leaderName + " is DOWN!");
        System.out.println("\nWatching for re-election...");

        Thread.sleep(1000);

        RaftNode newLeader = findLeader();
        if (newLeader != null) {
            System.out.println("✓ New leader elected: " + getNodeName(newLeader));
        } else {
            System.out.println("No new leader yet (election in progress)");
        }
    }

    private static void restartNode(Scanner scanner) throws Exception {
        System.out.print("\nWhich node to restart? (Alice/Bob/Charlie): ");
        String name = scanner.nextLine().trim();

        if (name.equalsIgnoreCase("Alice") && node1 == null) {
            System.out.println("Restarting Alice...");
            node1 = new RaftNode("Alice", 9001);
            node1.configurePeers(Map.of("Bob", "localhost:9002", "Charlie", "localhost:9003"));
            node1.start();
            Thread.sleep(1000);
            System.out.println("✓ Alice is back online!");
        } else if (name.equalsIgnoreCase("Bob") && node2 == null) {
            System.out.println("Restarting Bob...");
            node2 = new RaftNode("Bob", 9002);
            node2.configurePeers(Map.of("Alice", "localhost:9001", "Charlie", "localhost:9003"));
            node2.start();
            Thread.sleep(1000);
            System.out.println("✓ Bob is back online!");
        } else if (name.equalsIgnoreCase("Charlie") && node3 == null) {
            System.out.println("Restarting Charlie...");
            node3 = new RaftNode("Charlie", 9003);
            node3.configurePeers(Map.of("Alice", "localhost:9001", "Bob", "localhost:9002"));
            node3.start();
            Thread.sleep(1000);
            System.out.println("✓ Charlie is back online!");
        } else {
            System.out.println("Node not found or already running!");
        }
    }

    private static void watchCluster() throws Exception {
        System.out.println("\n=== WATCHING CLUSTER FOR 10 SECONDS ===\n");

        for (int i = 0; i < 10; i++) {
            System.out.print("t=" + i + "s: ");

            RaftNode leader = findLeader();
            if (leader != null) {
                String leaderName = getNodeName(leader);
                System.out.print("Leader=" + leaderName + " | ");
                System.out.print("Heartbeats sent=" + leader.getHeartbeatsSent());
            } else {
                System.out.print("No leader (election in progress)");
            }

            System.out.println();
            Thread.sleep(1000);
        }

        System.out.println("\nDone watching!\n");
    }

    private static RaftNode findLeader() {
        if (node1 != null && node1.getState() == RaftState.LEADER) return node1;
        if (node2 != null && node2.getState() == RaftState.LEADER) return node2;
        if (node3 != null && node3.getState() == RaftState.LEADER) return node3;
        return null;
    }

    private static String getNodeName(RaftNode node) {
        if (node == node1) return "Alice";
        if (node == node2) return "Bob";
        if (node == node3) return "Charlie";
        return "Unknown";
    }

    private static void setNodeToNull(RaftNode node) {
        if (node == node1) node1 = null;
        else if (node == node2) node2 = null;
        else if (node == node3) node3 = null;
    }

    private static void shutdown() {
        System.out.println("\nShutting down cluster...");
        if (node1 != null) node1.shutdown();
        if (node2 != null) node2.shutdown();
        if (node3 != null) node3.shutdown();
        System.out.println("Goodbye!");
    }
}