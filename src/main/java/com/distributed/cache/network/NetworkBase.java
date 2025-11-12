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
import java.util.concurrent.TimeUnit;

/**
 * NetworkBase provides the foundation for all network communication in the Raft
 * system. It handles server startup, outbound connects, routing incoming
 * messages to registered handlers, and maintaining peer channels.
 */
public class NetworkBase {
    private static final Logger logger = LoggerFactory.getLogger(NetworkBase.class);

    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;

    private final String nodeId;
    private final int port;

    // Map peerId -> Channel (active channel to that peer)
    private final Map<String, Channel> peerChannels = new ConcurrentHashMap<>();

    // Registered message handlers
    private final Map<Message.MessageType, Consumer<Message>> messageHandlers = new ConcurrentHashMap<>();

    // bounded reconnect attempts per peer
    private final Map<String, Integer> connectRetries = new ConcurrentHashMap<>();

    private Channel serverChannel;

    public NetworkBase(String nodeId, int port) {
        this.nodeId = nodeId;
        this.port = port;
        this.bossGroup = new NioEventLoopGroup(1);
        this.workerGroup = new NioEventLoopGroup();
        logger.info("NetworkBase initialized for node {} on port {}", nodeId, port);
    }

    public void startServer() throws InterruptedException {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 4));
                        pipeline.addLast(new LengthFieldPrepender(4));
                        pipeline.addLast(new StringDecoder(CharsetUtil.UTF_8));
                        pipeline.addLast(new StringEncoder(CharsetUtil.UTF_8));
                        pipeline.addLast(new InboundMessageHandler());
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        ChannelFuture future = serverBootstrap.bind(port).sync();
        serverChannel = future.channel();
        logger.info("Server started on port {} for node {}", port, nodeId);
    }

    public void connectToPeer(String peerId, String host, int peerPort) {
        if (peerId.equals(nodeId))
            return;

        Channel existing = peerChannels.get(peerId);
        if (existing != null && existing.isActive()) {
            logger.debug("Already connected to peer {}", peerId);
            return;
        }
        attemptConnect(peerId, host, peerPort);
    }

    private void attemptConnect(String peerId, String host, int peerPort) {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 4));
                        pipeline.addLast(new LengthFieldPrepender(4));
                        pipeline.addLast(new StringDecoder(CharsetUtil.UTF_8));
                        pipeline.addLast(new StringEncoder(CharsetUtil.UTF_8));
                        pipeline.addLast(new InboundMessageHandler());
                    }
                });

        bootstrap.connect(host, peerPort).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                Channel channel = future.channel();
                peerChannels.put(peerId, channel);
                connectRetries.remove(peerId);
                logger.info("Connected to peer {} at {}:{}", peerId, host, peerPort);

                // on close remove only if the same channel is registered
                channel.closeFuture().addListener(closeFuture -> {
                    if (peerChannels.get(peerId) == channel) {
                        peerChannels.remove(peerId);
                        logger.warn("Outbound connection to peer {} closed", peerId);
                    }
                });
            } else {
                int attempt = connectRetries.getOrDefault(peerId, 0) + 1;
                connectRetries.put(peerId, attempt);
                if (attempt <= 10) {
                    long delayMs = 200L;
                    logger.warn("Failed to connect to peer {} at {}:{} (attempt {}/10). Retrying in {} ms",
                            peerId, host, peerPort, attempt, delayMs);
                    workerGroup.schedule(
                            () -> attemptConnect(peerId, host, peerPort),
                            delayMs,
                            TimeUnit.MILLISECONDS);
                } else {
                    logger.error("Giving up on connecting to peer {} at {}:{} after {} attempts",
                            peerId, host, peerPort, attempt);
                }
            }
        });
    }

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

    public void broadcastMessage(Message message) {
        logger.debug("Broadcasting {} to {} peers", message.getType(), peerChannels.size());
        for (String peerId : peerChannels.keySet()) {
            sendMessage(peerId, message);
        }
    }

    public void registerMessageHandler(Message.MessageType messageType, Consumer<Message> handler) {
        messageHandlers.put(messageType, handler);
        logger.info("Registered handler for message type: {}", messageType);
    }

    public void shutdown() {
        logger.info("Shutting down NetworkBase for node {}", nodeId);
        for (Channel channel : peerChannels.values()) {
            if (channel.isActive()) {
                channel.close();
            }
        }
        peerChannels.clear();

        if (serverChannel != null) {
            serverChannel.close();
        }

        workerGroup.shutdownGracefully();
        bossGroup.shutdownGracefully();

        logger.info("NetworkBase shutdown complete");
    }

    public int getActivePeerCount() {
        return (int) peerChannels.values().stream().filter(Channel::isActive).count();
    }

    public boolean isConnectedToPeer(String peerId) {
        Channel channel = peerChannels.get(peerId);
        return channel != null && channel.isActive();
    }

    /**
     * Netty handler for incoming string messages (JSON string).
     * Important: register inbound channel for senderId BEFORE invoking business
     * handlers.
     */
    private class InboundMessageHandler extends SimpleChannelInboundHandler<String> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String jsonMessage) {
            try {
                Message message = MessageSerializer.deserialize(jsonMessage);
                logger.debug("Received {} from {}", message.getType(), message.getSenderId());

                // Register inbound channel for sender immediately so subsequent sends work
                String senderId = message.getSenderId();
                if (senderId != null && !senderId.equals(nodeId)) {
                    Channel inboundChannel = ctx.channel();
                    Channel existingChannel = peerChannels.get(senderId);

                    // register or replace inactive registration
                    if (existingChannel == null || !existingChannel.isActive()) {
                        peerChannels.put(senderId, inboundChannel);
                        logger.info("Registered inbound connection from peer {} at {}", senderId,
                                inboundChannel.remoteAddress());

                        inboundChannel.closeFuture().addListener(cf -> {
                            if (peerChannels.get(senderId) == inboundChannel) {
                                peerChannels.remove(senderId);
                                logger.warn("Inbound connection from peer {} closed", senderId);
                            }
                        });
                    }
                }

                // Now dispatch message to registered handler
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
