package com.distributed.cache.network;

import com.distributed.cache.config.ClusterConfig;
import com.distributed.cache.config.NodeInfo;
import com.distributed.cache.raft.Message;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PeerManager manages connections to all peer nodes in the Raft cluster.
 *
 * Responsibilities:
 * - Maintain persistent connections to all peers
 * - Handle connection failures with exponential backoff retry
 * - Provide methods to send messages to specific peers or broadcast to all
 * - Monitor connection health and automatically reconnect
 * - Thread-safe operations for concurrent access
 *
 * Design decisions:
 * - Connection pooling: Reuse existing channels, don't create new ones per message
 * - Exponential backoff: Retry with increasing delays (1s, 2s, 4s, 8s, max 30s)
 * - Background reconnection: Failed connections are retried in background
 * - Health monitoring: Periodic checks to detect dead connections
 * - Thread-safe: All operations are synchronized or use concurrent collections
 */
public class PeerManager {
    private static final Logger logger = LoggerFactory.getLogger(PeerManager.class);

    // Configuration
    private final String myNodeId;
    private final ClusterConfig config;

    // Connection management
    private final Map<String, Channel> peerChannels = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> retryCounters = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> reconnectTasks = new ConcurrentHashMap<>();

    // Netty event loop for outbound connections
    private final EventLoopGroup workerGroup;

    // Executor for retry logic
    private final ScheduledExecutorService retryExecutor;

    // Retry configuration
    private static final long INITIAL_RETRY_DELAY_MS = 1000;  // 1 second
    private static final long MAX_RETRY_DELAY_MS = 30000;     // 30 seconds
    private static final int MAX_RETRY_ATTEMPTS = Integer.MAX_VALUE;  // Keep retrying forever

    // Health check configuration
    private static final long HEALTH_CHECK_INTERVAL_MS = 5000;  // Check every 5 seconds
    private ScheduledFuture<?> healthCheckTask;

    // Message handler for incoming messages on peer connections
    private final ChannelInboundHandler messageHandler;

    /**
     * Create a new PeerManager.
     *
     * @param myNodeId       ID of this node (to avoid connecting to self)
     * @param config         Cluster configuration
     * @param messageHandler Handler for incoming messages on peer connections
     */
    public PeerManager(String myNodeId, ClusterConfig config, ChannelInboundHandler messageHandler) {
        this.myNodeId = myNodeId;
        this.config = config;
        this.messageHandler = messageHandler;
        this.workerGroup = new NioEventLoopGroup();
        this.retryExecutor = Executors.newScheduledThreadPool(2,
                r -> new Thread(r, "PeerManager-Retry-" + myNodeId));

        logger.info("PeerManager initialized for node {}", myNodeId);
    }

    /**
     * Connect to all peers in the cluster.
     *
     * This should be called after the local server is started.
     * Connections are initiated in parallel and retried on failure.
     */
    public void connectToAllPeers() {
        List<NodeInfo> peers = config.getOtherNodes(myNodeId);
        logger.info("Connecting to {} peers", peers.size());

        for (NodeInfo peer : peers) {
            connectToPeer(peer);
        }

        // Start health check task
        startHealthCheck();
    }

    /**
     * Connect to a specific peer node.
     *
     * If connection fails, it will be retried with exponential backoff.
     *
     * @param peerInfo Information about the peer to connect to
     */
    public void connectToPeer(NodeInfo peerInfo) {
        String peerId = peerInfo.getId();

        // Don't connect to self
        if (peerId.equals(myNodeId)) {
            logger.warn("Attempted to connect to self ({}), skipping", myNodeId);
            return;
        }

        // Check if already connected
        Channel existingChannel = peerChannels.get(peerId);
        if (existingChannel != null && existingChannel.isActive()) {
            logger.debug("Already connected to peer {}", peerId);
            return;
        }

        logger.info("Initiating connection to peer {} at {}:{}", peerId, peerInfo.getHost(), peerInfo.getPort());
        attemptConnection(peerInfo, 0);
    }

    /**
     * Attempt to connect to a peer with retry logic.
     *
     * @param peerInfo    Peer to connect to
     * @param retryCount  Current retry attempt number
     */
    private void attemptConnection(NodeInfo peerInfo, int retryCount) {
        String peerId = peerInfo.getId();

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();

                        // Frame-based encoding/decoding (4-byte length prefix)
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 4));
                        pipeline.addLast(new LengthFieldPrepender(4));

                        // String encoding/decoding (UTF-8)
                        pipeline.addLast(new StringDecoder(CharsetUtil.UTF_8));
                        pipeline.addLast(new StringEncoder(CharsetUtil.UTF_8));

                        // Message handler
                        pipeline.addLast(messageHandler);
                    }
                });

        // Attempt connection
        ChannelFuture connectFuture = bootstrap.connect(peerInfo.getHost(), peerInfo.getPort());

        connectFuture.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                onConnectionSuccess(peerInfo, future.channel(), retryCount);
            } else {
                onConnectionFailure(peerInfo, retryCount, future.cause());
            }
        });
    }

    /**
     * Handle successful connection to a peer.
     *
     * @param peerInfo   Peer that was connected to
     * @param channel    The established channel
     * @param retryCount Number of retries it took to connect
     */
    private void onConnectionSuccess(NodeInfo peerInfo, Channel channel, int retryCount) {
        String peerId = peerInfo.getId();

        logger.info("Successfully connected to peer {} at {}:{} (retry count: {})",
                peerId, peerInfo.getHost(), peerInfo.getPort(), retryCount);

        // Store the channel
        peerChannels.put(peerId, channel);

        // Reset retry counter
        retryCounters.remove(peerId);

        // Cancel any pending reconnect task
        ScheduledFuture<?> reconnectTask = reconnectTasks.remove(peerId);
        if (reconnectTask != null) {
            reconnectTask.cancel(false);
        }

        // Add close listener to handle disconnections
        channel.closeFuture().addListener(closeFuture -> {
            logger.warn("Connection to peer {} closed", peerId);
            peerChannels.remove(peerId);

            // Schedule reconnection
            scheduleReconnect(peerInfo, 0);
        });
    }

    /**
     * Handle connection failure to a peer.
     *
     * @param peerInfo   Peer that failed to connect
     * @param retryCount Current retry attempt
     * @param cause      Exception that caused the failure
     */
    private void onConnectionFailure(NodeInfo peerInfo, int retryCount, Throwable cause) {
        String peerId = peerInfo.getId();

        if (retryCount < MAX_RETRY_ATTEMPTS) {
            logger.warn("Failed to connect to peer {} at {}:{} (attempt {}/{}): {}",
                    peerId, peerInfo.getHost(), peerInfo.getPort(),
                    retryCount + 1, MAX_RETRY_ATTEMPTS, cause.getMessage());

            scheduleReconnect(peerInfo, retryCount + 1);
        } else {
            logger.error("Gave up connecting to peer {} after {} attempts",
                    peerId, retryCount);
        }
    }

    /**
     * Schedule a reconnection attempt with exponential backoff.
     *
     * Delay formula: min(INITIAL_DELAY * 2^retryCount, MAX_DELAY)
     *
     * @param peerInfo   Peer to reconnect to
     * @param retryCount Current retry attempt number
     */
    private void scheduleReconnect(NodeInfo peerInfo, int retryCount) {
        String peerId = peerInfo.getId();

        // Calculate exponential backoff delay
        long delay = Math.min(
                INITIAL_RETRY_DELAY_MS * (1L << Math.min(retryCount, 5)),  // Cap at 2^5 = 32
                MAX_RETRY_DELAY_MS
        );

        logger.info("Scheduling reconnection to peer {} in {}ms (retry #{})",
                peerId, delay, retryCount + 1);

        // Cancel any existing reconnect task
        ScheduledFuture<?> existingTask = reconnectTasks.get(peerId);
        if (existingTask != null) {
            existingTask.cancel(false);
        }

        // Schedule new reconnect attempt
        ScheduledFuture<?> reconnectTask = retryExecutor.schedule(
                () -> attemptConnection(peerInfo, retryCount),
                delay,
                TimeUnit.MILLISECONDS
        );

        reconnectTasks.put(peerId, reconnectTask);
        retryCounters.put(peerId, new AtomicInteger(retryCount));
    }

    /**
     * Send a message to a specific peer.
     *
     * @param peerId  ID of the peer to send to
     * @param message Message to send
     * @return true if message was sent successfully, false if peer is not connected
     */
    public boolean sendMessage(String peerId, Message message) {
        Channel channel = peerChannels.get(peerId);

        if (channel == null || !channel.isActive()) {
            logger.warn("Cannot send message to peer {}: not connected", peerId);
            return false;
        }

        try {
            String jsonMessage = MessageSerializer.serialize(message);
            channel.writeAndFlush(jsonMessage).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    logger.trace("Sent {} to peer {}", message.getType(), peerId);
                } else {
                    logger.error("Failed to send message to peer {}: {}",
                            peerId, future.cause().getMessage());
                }
            });
            return true;
        } catch (Exception e) {
            logger.error("Error serializing message for peer {}: {}", peerId, e.getMessage());
            return false;
        }
    }

    /**
     * Broadcast a message to all connected peers.
     *
     * @param message Message to broadcast
     * @return Number of peers the message was successfully sent to
     */
    public int broadcast(Message message) {
        List<NodeInfo> peers = config.getOtherNodes(myNodeId);
        int successCount = 0;

        logger.debug("Broadcasting {} to {} peers", message.getType(), peers.size());

        for (NodeInfo peer : peers) {
            if (sendMessage(peer.getId(), message)) {
                successCount++;
            }
        }

        if (successCount < peers.size()) {
            logger.warn("Broadcast reached only {}/{} peers", successCount, peers.size());
        }

        return successCount;
    }

    /**
     * Check if a specific peer is connected.
     *
     * @param peerId ID of the peer to check
     * @return true if connected and channel is active, false otherwise
     */
    public boolean isPeerConnected(String peerId) {
        Channel channel = peerChannels.get(peerId);
        return channel != null && channel.isActive();
    }

    /**
     * Get the connection status of all peers.
     *
     * @return Map of peer ID to connection status (true = connected, false = disconnected)
     */
    public Map<String, Boolean> getPeerStatus() {
        Map<String, Boolean> status = new HashMap<>();
        List<NodeInfo> peers = config.getOtherNodes(myNodeId);

        for (NodeInfo peer : peers) {
            status.put(peer.getId(), isPeerConnected(peer.getId()));
        }

        return status;
    }

    /**
     * Get the number of currently connected peers.
     *
     * @return Number of active peer connections
     */
    public int getConnectedPeerCount() {
        return (int) peerChannels.values().stream()
                .filter(Channel::isActive)
                .count();
    }

    /**
     * Start periodic health check to detect and close stale connections.
     *
     * This runs every 5 seconds and checks if channels are still active.
     * Inactive channels are removed and reconnection is triggered.
     */
    private void startHealthCheck() {
        healthCheckTask = retryExecutor.scheduleAtFixedRate(
                this::performHealthCheck,
                HEALTH_CHECK_INTERVAL_MS,
                HEALTH_CHECK_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );

        logger.info("Started connection health check (interval: {}ms)", HEALTH_CHECK_INTERVAL_MS);
    }

    /**
     * Perform health check on all peer connections.
     */
    private void performHealthCheck() {
        logger.trace("Performing connection health check");

        for (NodeInfo peer : config.getOtherNodes(myNodeId)) {
            String peerId = peer.getId();
            Channel channel = peerChannels.get(peerId);

            if (channel == null) {
                // Not connected, check if we have a pending reconnect
                if (!reconnectTasks.containsKey(peerId)) {
                    logger.debug("Peer {} not connected and no reconnect scheduled, initiating connection",
                            peerId);
                    connectToPeer(peer);
                }
            } else if (!channel.isActive()) {
                // Channel exists but is not active - remove it and reconnect
                logger.warn("Detected inactive channel to peer {}, triggering reconnection", peerId);
                peerChannels.remove(peerId);
                scheduleReconnect(peer, 0);
            }
        }
    }

    /**
     * Register an inbound connection from a peer.
     *
     * This is called by NetworkBase when a peer connects to us.
     * We use this connection for sending messages instead of creating our own.
     *
     * @param peerId  ID of the peer that connected
     * @param channel The inbound channel
     */
    public void registerInboundConnection(String peerId, Channel channel) {
        Channel existingChannel = peerChannels.get(peerId);

        // Only register if we don't already have an active outbound connection
        if (existingChannel == null || !existingChannel.isActive()) {
            logger.info("Registered inbound connection from peer {}", peerId);
            peerChannels.put(peerId, channel);

            // Cancel any pending outbound connection attempts
            ScheduledFuture<?> reconnectTask = reconnectTasks.remove(peerId);
            if (reconnectTask != null) {
                reconnectTask.cancel(false);
            }

            // Add close listener
            channel.closeFuture().addListener(closeFuture -> {
                // Only remove if this is still the registered channel
                if (peerChannels.get(peerId) == channel) {
                    logger.warn("Inbound connection from peer {} closed", peerId);
                    peerChannels.remove(peerId);

                    // Try to reconnect
                    NodeInfo peerInfo = config.getNodeById(peerId);
                    if (peerInfo != null) {
                        scheduleReconnect(peerInfo, 0);
                    }
                }
            });
        } else {
            logger.debug("Inbound connection from peer {} ignored (already have active connection)", peerId);
        }
    }

    /**
     * Shutdown the peer manager and close all connections.
     */
    public void shutdown() {
        logger.info("Shutting down PeerManager for node {}", myNodeId);

        // Cancel health check
        if (healthCheckTask != null) {
            healthCheckTask.cancel(false);
        }

        // Cancel all pending reconnect tasks
        for (ScheduledFuture<?> task : reconnectTasks.values()) {
            task.cancel(false);
        }
        reconnectTasks.clear();

        // Close all peer connections
        for (Channel channel : peerChannels.values()) {
            if (channel.isActive()) {
                channel.close();
            }
        }
        peerChannels.clear();

        // Shutdown executors
        retryExecutor.shutdown();
        workerGroup.shutdownGracefully();

        logger.info("PeerManager shutdown complete");
    }

    /**
     * Get statistics about peer connections for debugging/monitoring.
     *
     * @return Map with statistics (connected, disconnected, retrying, etc.)
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        List<NodeInfo> peers = config.getOtherNodes(myNodeId);

        stats.put("total_peers", peers.size());
        stats.put("connected", getConnectedPeerCount());
        stats.put("disconnected", peers.size() - getConnectedPeerCount());
        stats.put("pending_reconnects", reconnectTasks.size());
        stats.put("peer_status", getPeerStatus());

        return stats;
    }
}