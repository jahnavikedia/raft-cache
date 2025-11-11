package com.distributed.cache.network;

import com.distributed.cache.raft.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test basic network communication between two nodes
 * This verifies that NetworkBase and MessageSerializer work correctly
 */
class NetworkBaseTest {

    private NetworkBase node1;
    private NetworkBase node2;

    @BeforeEach
    void setUp() throws InterruptedException {
        // Create two network nodes
        node1 = new NetworkBase("node1", 9001);
        node2 = new NetworkBase("node2", 9002);

        // Start both servers
        node1.startServer();
        node2.startServer();

        // Give servers time to start
        Thread.sleep(100);
    }

    @AfterEach
    void tearDown() {
        if (node1 != null) {
            node1.shutdown();
        }
        if (node2 != null) {
            node2.shutdown();
        }
    }

    @Test
    void testBasicMessageExchange() throws InterruptedException {
        // This test verifies that:
        // 1. Nodes can connect to each other
        // 2. Messages can be sent and received
        // 3. Message handlers are invoked correctly

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Message> receivedMessage = new AtomicReference<>();

        // Node 2 registers a handler for heartbeat messages
        node2.registerMessageHandler(Message.MessageType.APPEND_ENTRIES, message -> {
            receivedMessage.set(message);
            latch.countDown();
        });

        // Node 1 connects to Node 2
        node1.connectToPeer("node2", "localhost", 9002);

        // Wait for connection to establish
        Thread.sleep(200);

        // Verify connection is active
        assertTrue(node1.isConnectedToPeer("node2"), "Node 1 should be connected to Node 2");

        // Node 1 sends a heartbeat to Node 2
        Message heartbeat = MessageSerializer.createHeartbeat("node1", 1, 0);
        node1.sendMessage("node2", heartbeat);

        // Wait for message to be received (with timeout)
        boolean received = latch.await(2, TimeUnit.SECONDS);
        assertTrue(received, "Message should be received within timeout");

        // Verify the received message
        assertNotNull(receivedMessage.get(), "Should have received a message");
        assertEquals(Message.MessageType.APPEND_ENTRIES, receivedMessage.get().getType());
        assertEquals("node1", receivedMessage.get().getLeaderId());
        assertEquals(1, receivedMessage.get().getTerm());
    }

    @Test
    void testBidirectionalCommunication() throws InterruptedException {
        // Test that both nodes can send and receive messages

        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);

        // Node 1 expects a vote request
        node1.registerMessageHandler(Message.MessageType.REQUEST_VOTE, message -> {
            latch1.countDown();
        });

        // Node 2 expects a heartbeat
        node2.registerMessageHandler(Message.MessageType.APPEND_ENTRIES, message -> {
            latch2.countDown();
        });

        // Connect both nodes
        node1.connectToPeer("node2", "localhost", 9002);
        node2.connectToPeer("node1", "localhost", 9001);

        Thread.sleep(200);

        // Node 1 sends heartbeat to Node 2
        Message heartbeat = MessageSerializer.createHeartbeat("node1", 1, 0);
        node1.sendMessage("node2", heartbeat);

        // Node 2 sends vote request to Node 1
        Message voteRequest = MessageSerializer.createRequestVote("node2", 1, 0, 0);
        node2.sendMessage("node1", voteRequest);

        // Both should receive their messages
        assertTrue(latch1.await(2, TimeUnit.SECONDS), "Node 1 should receive vote request");
        assertTrue(latch2.await(2, TimeUnit.SECONDS), "Node 2 should receive heartbeat");
    }

    @Test
    void testBroadcastMessage() throws InterruptedException {
        // Test broadcasting to multiple peers

        NetworkBase node3 = new NetworkBase("node3", 9003);
        node3.startServer();
        Thread.sleep(100);

        try {
            CountDownLatch latch = new CountDownLatch(2);

            // Both node2 and node3 register handlers
            node2.registerMessageHandler(Message.MessageType.APPEND_ENTRIES, message -> latch.countDown());
            node3.registerMessageHandler(Message.MessageType.APPEND_ENTRIES, message -> latch.countDown());

            // Node 1 connects to both
            node1.connectToPeer("node2", "localhost", 9002);
            node1.connectToPeer("node3", "localhost", 9003);

            Thread.sleep(200);

            // Broadcast a heartbeat
            Message heartbeat = MessageSerializer.createHeartbeat("node1", 1, 0);
            node1.broadcastMessage(heartbeat);

            // Both should receive the message
            assertTrue(latch.await(2, TimeUnit.SECONDS), "Both nodes should receive broadcast");

        } finally {
            node3.shutdown();
        }
    }

    @Test
    void testActivePeerCount() throws InterruptedException {
        // Initially no peers connected
        assertEquals(0, node1.getActivePeerCount());

        // Connect to node2
        node1.connectToPeer("node2", "localhost", 9002);
        Thread.sleep(200);

        // Should have 1 active peer
        assertEquals(1, node1.getActivePeerCount());
    }

    @Test
    void testMessageSerialization() throws Exception {
        // Test that messages can be serialized and deserialized correctly

        // Test heartbeat
        Message heartbeat = MessageSerializer.createHeartbeat("leader1", 5, 10);
        String json = MessageSerializer.serialize(heartbeat);
        Message deserialized = MessageSerializer.deserialize(json);

        assertEquals(Message.MessageType.APPEND_ENTRIES, deserialized.getType());
        assertEquals("leader1", deserialized.getLeaderId());
        assertEquals(5, deserialized.getTerm());
        assertEquals(10, deserialized.getLeaderCommit());

        // Test vote request
        Message voteRequest = MessageSerializer.createRequestVote("candidate1", 3, 5, 2);
        json = MessageSerializer.serialize(voteRequest);
        deserialized = MessageSerializer.deserialize(json);

        assertEquals(Message.MessageType.REQUEST_VOTE, deserialized.getType());
        assertEquals("candidate1", deserialized.getCandidateId());
        assertEquals(3, deserialized.getTerm());
        assertEquals(5, deserialized.getLastLogIndex());
        assertEquals(2, deserialized.getLastLogTerm());
    }
}
