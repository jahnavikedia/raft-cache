package com.distributed.cache.network;

import com.distributed.cache.raft.Message;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * NetworkBase provides the foundation for all network communication in the Raft system.
 * It handles:
 * - Starting a server to receive messages from other nodes
 * - Connecting to other nodes as a client
 * - Routing incoming messages to registered handlers
 * - Managing persistent connections to peers
 *
 * This is used by both election and heartbeat features.
 */
public class NetworkBase {
    private static final Logger logger = LoggerFactory.getLogger(NetworkBase.class);

    // Netty event loop groups for async I/O
    private final EventLoopGroup bossGroup;      // Accepts incoming connections
    private final EventLoopGroup workerGroup;    // Handles I/O operations

    private final String nodeId;
    private final int port;

    // Channels for communicating with other nodes (key: nodeId, value: channel)
    private final Map<String, Channel> peerChannels = new ConcurrentHashMap<>();

    // Message handlers registered by features (election, heartbeat, etc.)
    private final Map<Message.MessageType, Consumer<Message>> messageHandlers = new ConcurrentHashMap<>();

    // Server channel that listens for incoming connections
    private Channel serverChannel;

    public NetworkBase(String nodeId, int port) {
        this.nodeId = nodeId;
        this.port = port;
        this.bossGroup = new NioEventLoopGroup(1);
        this.workerGroup = new NioEventLoopGroup();

        logger.info("NetworkBase initialized for node {} on port {}", nodeId, port);
    }

    /**
     * Start the network server to accept incoming connections from other nodes
     */
    public void startServer() throws InterruptedException {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();

                        // Frame decoder: messages are prefixed with length (4 bytes)
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 4));
                        pipeline.addLast(new LengthFieldPrepender(4));

                        // String encoding/decoding (JSON messages are strings)
                        pipeline.addLast(new StringDecoder(CharsetUtil.UTF_8));
                        pipeline.addLast(new StringEncoder(CharsetUtil.UTF_8));

                        // Our custom handler for processing incoming messages
                        pipeline.addLast(new InboundMessageHandler());
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        // Bind to the port and start accepting connections
        ChannelFuture future = serverBootstrap.bind(port).sync();
        serverChannel = future.channel();

        logger.info("Server started on port {} for node {}", port, nodeId);
    }

    /**
     * Connect to another node in the cluster
     *
     * @param peerId The ID of the peer node
     * @param host The hostname/IP of the peer
     * @param peerPort The port of the peer
     */
    public void connectToPeer(String peerId, String host, int peerPort) {
        // Don't connect to ourselves
        if (peerId.equals(nodeId)) {
            return;
        }

        // Check if already connected
        if (peerChannels.containsKey(peerId) && peerChannels.get(peerId).isActive()) {
            logger.debug("Already connected to peer {}", peerId);
            return;
        }

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();

                        // Same pipeline as server
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 4));
                        pipeline.addLast(new LengthFieldPrepender(4));
                        pipeline.addLast(new StringDecoder(CharsetUtil.UTF_8));
                        pipeline.addLast(new StringEncoder(CharsetUtil.UTF_8));
                        pipeline.addLast(new InboundMessageHandler());
                    }
                });

        // Connect asynchronously
        bootstrap.connect(host, peerPort).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                Channel channel = future.channel();
                peerChannels.put(peerId, channel);
                logger.info("Connected to peer {} at {}:{}", peerId, host, peerPort);

                // Handle channel closure
                channel.closeFuture().addListener(closeFuture -> {
                    peerChannels.remove(peerId);
                    logger.warn("Connection to peer {} closed", peerId);
                });
            } else {
                logger.error("Failed to connect to peer {} at {}:{}", peerId, host, peerPort, future.cause());
            }
        });
    }

    /**
     * Send a message to a specific peer
     *
     * @param peerId The ID of the peer to send to
     * @param message The message to send
     */
    public void sendMessage(String peerId, Message message) {
        Channel channel = peerChannels.get(peerId);

        if (channel == null || !channel.isActive()) {
            logger.warn("No active connection to peer {}, cannot send message", peerId);
            return;
        }

        try {
            String jsonMessage = MessageSerializer.serialize(message);
            channel.writeAndFlush(jsonMessage).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    logger.debug("Sent {} to peer {}", message.getType(), peerId);
                } else {
                    logger.error("Failed to send message to peer {}", peerId, future.cause());
                }
            });
        } catch (Exception e) {
            logger.error("Error serializing message for peer {}", peerId, e);
        }
    }

    /**
     * Broadcast a message to all connected peers
     *
     * @param message The message to broadcast
     */
    public void broadcastMessage(Message message) {
        logger.debug("Broadcasting {} to {} peers", message.getType(), peerChannels.size());

        for (String peerId : peerChannels.keySet()) {
            sendMessage(peerId, message);
        }
    }

    /**
     * Register a handler for a specific message type
     * This is how the election and heartbeat features register their handlers
     *
     * @param messageType The type of message to handle
     * @param handler The handler function
     */
    public void registerMessageHandler(Message.MessageType messageType, Consumer<Message> handler) {
        messageHandlers.put(messageType, handler);
        logger.info("Registered handler for message type: {}", messageType);
    }

    /**
     * Shutdown the network layer gracefully
     */
    public void shutdown() {
        logger.info("Shutting down NetworkBase for node {}", nodeId);

        // Close all peer connections
        for (Channel channel : peerChannels.values()) {
            if (channel.isActive()) {
                channel.close();
            }
        }
        peerChannels.clear();

        // Close server channel
        if (serverChannel != null) {
            serverChannel.close();
        }

        // Shutdown event loop groups
        workerGroup.shutdownGracefully();
        bossGroup.shutdownGracefully();

        logger.info("NetworkBase shutdown complete");
    }

    /**
     * Get the number of active peer connections
     */
    public int getActivePeerCount() {
        return (int) peerChannels.values().stream()
                .filter(Channel::isActive)
                .count();
    }

    /**
     * Check if connected to a specific peer
     */
    public boolean isConnectedToPeer(String peerId) {
        Channel channel = peerChannels.get(peerId);
        return channel != null && channel.isActive();
    }

    /**
     * Netty handler for processing incoming messages
     * This routes messages to the appropriate handler based on message type
     */
    private class InboundMessageHandler extends SimpleChannelInboundHandler<String> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String jsonMessage) {
            try {
                // Deserialize the JSON message
                Message message = MessageSerializer.deserialize(jsonMessage);

                logger.debug("Received {} from {}", message.getType(), message.getSenderId());

                // Route to the appropriate handler
                Consumer<Message> handler = messageHandlers.get(message.getType());
                if (handler != null) {
                    handler.accept(message);
                } else {
                    logger.warn("No handler registered for message type: {}", message.getType());
                }

            } catch (Exception e) {
                logger.error("Error processing incoming message", e);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("Exception in network handler", cause);
            ctx.close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            logger.debug("Channel became inactive: {}", ctx.channel().remoteAddress());
            ctx.fireChannelInactive();
        }
    }
}
