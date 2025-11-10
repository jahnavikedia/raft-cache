# Week 1 Implementation Guide

## Overview
This week focuses on implementing the core Raft consensus mechanism: leader election and heartbeats.

## Day 1-2: Leader Election

### Task 1.1: Add Election Timer to RaftNode

**File**: `src/main/java/com/distributed/cache/raft/RaftNode.java`

Add these fields:
```java
private long currentTerm = 0;
private String votedFor = null;
private Timer electionTimer;
private final Random random = new Random();
private static final int ELECTION_TIMEOUT_MIN = 150; // milliseconds
private static final int ELECTION_TIMEOUT_MAX = 300;
```

Add method:
```java
private void resetElectionTimer() {
    if (electionTimer != null) {
        electionTimer.cancel();
    }
    electionTimer = new Timer();
    int timeout = ELECTION_TIMEOUT_MIN + 
                 random.nextInt(ELECTION_TIMEOUT_MAX - ELECTION_TIMEOUT_MIN);
    electionTimer.schedule(new TimerTask() {
        @Override
        public void run() {
            startElection();
        }
    }, timeout);
}
```

### Task 1.2: Implement startElection()

```java
private void startElection() {
    logger.info("Starting election for term {}", currentTerm + 1);
    
    // Transition to CANDIDATE
    state = RaftState.CANDIDATE;
    currentTerm++;
    votedFor = nodeId;
    int votesReceived = 1; // Vote for self
    
    // TODO: Send RequestVote messages to all other nodes
    // TODO: Collect votes
    // TODO: If majority votes received, become LEADER
    
    // For now, just log
    logger.info("Node {} is now CANDIDATE for term {}", nodeId, currentTerm);
    resetElectionTimer();
}
```

### Task 1.3: Test Single Node Election

Run:
```bash
mvn exec:java -Dexec.mainClass="com.distributed.cache.Main" -Dexec.args="node1 8001"
```

You should see election timeouts triggering in logs.

## Day 3-4: Network Communication

### Task 2.1: Create NetworkServer

**File**: `src/main/java/com/distributed/cache/network/NetworkServer.java`

```java
package com.distributed.cache.network;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkServer {
    private static final Logger logger = LoggerFactory.getLogger(NetworkServer.class);
    
    private final int port;
    private final MessageHandler messageHandler;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    
    public NetworkServer(int port, MessageHandler messageHandler) {
        this.port = port;
        this.messageHandler = messageHandler;
    }
    
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(
                            new StringDecoder(),
                            new StringEncoder(),
                            new MessageHandlerAdapter(messageHandler)
                        );
                    }
                });
            
            ChannelFuture future = bootstrap.bind(port).sync();
            logger.info("Network server started on port {}", port);
            
        } catch (Exception e) {
            logger.error("Failed to start network server", e);
            throw e;
        }
    }
    
    public void shutdown() {
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
    }
}
```

### Task 2.2: Create MessageHandler Interface

**File**: `src/main/java/com/distributed/cache/network/MessageHandler.java`

```java
package com.distributed.cache.network;

import com.distributed.cache.raft.Message;

public interface MessageHandler {
    void handleMessage(Message message);
}
```

### Task 2.3: Create MessageHandlerAdapter

**File**: `src/main/java/com/distributed/cache/network/MessageHandlerAdapter.java`

```java
package com.distributed.cache.network;

import com.distributed.cache.raft.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageHandlerAdapter extends SimpleChannelInboundHandler<String> {
    private static final Logger logger = LoggerFactory.getLogger(MessageHandlerAdapter.class);
    private final MessageHandler messageHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public MessageHandlerAdapter(MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        try {
            Message message = objectMapper.readValue(msg, Message.class);
            messageHandler.handleMessage(message);
        } catch (Exception e) {
            logger.error("Failed to parse message", e);
        }
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Channel exception", cause);
        ctx.close();
    }
}
```

### Task 2.4: Create NetworkClient

**File**: `src/main/java/com/distributed/cache/network/NetworkClient.java`

```java
package com.distributed.cache.network;

import com.distributed.cache.raft.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkClient {
    private static final Logger logger = LoggerFactory.getLogger(NetworkClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EventLoopGroup group = new NioEventLoopGroup();
    
    public void sendMessage(String host, int port, Message message) {
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(
                            new StringEncoder(),
                            new StringDecoder()
                        );
                    }
                });
            
            ChannelFuture future = bootstrap.connect(host, port).sync();
            String json = objectMapper.writeValueAsString(message);
            future.channel().writeAndFlush(json + "\n");
            future.channel().closeFuture().sync();
            
            logger.debug("Sent message to {}:{}", host, port);
            
        } catch (Exception e) {
            logger.error("Failed to send message to {}:{}", host, port, e);
        }
    }
    
    public void shutdown() {
        group.shutdownGracefully();
    }
}
```

### Task 2.5: Integrate Network Layer into RaftNode

Update `RaftNode.java`:
```java
private NetworkServer networkServer;
private NetworkClient networkClient;

public void start() throws InterruptedException {
    logger.info("Starting Raft node: {}", nodeId);
    
    // Start network server
    networkServer = new NetworkServer(port, this::handleMessage);
    networkServer.start();
    
    // Create network client
    networkClient = new NetworkClient();
    
    // Start election timer
    resetElectionTimer();
}

private void handleMessage(Message message) {
    logger.info("Received message: {}", message);
    // TODO: Handle different message types
}
```

## Day 5-7: Heartbeat and Leader Responsibilities

### Task 3.1: Add Heartbeat Timer

In `RaftNode.java`:
```java
private Timer heartbeatTimer;
private static final int HEARTBEAT_INTERVAL = 50; // milliseconds

private void startHeartbeatTimer() {
    if (heartbeatTimer != null) {
        heartbeatTimer.cancel();
    }
    heartbeatTimer = new Timer();
    heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
        @Override
        public void run() {
            sendHeartbeats();
        }
    }, 0, HEARTBEAT_INTERVAL);
}

private void sendHeartbeats() {
    if (state != RaftState.LEADER) {
        return;
    }
    
    logger.debug("Sending heartbeats");
    
    // Create empty AppendEntries message (heartbeat)
    Message heartbeat = new Message(Message.MessageType.APPEND_ENTRIES, currentTerm, nodeId);
    heartbeat.setLeaderId(nodeId);
    
    // TODO: Send to all followers
    // For now, just log
    logger.debug("Leader {} sent heartbeat for term {}", nodeId, currentTerm);
}
```

### Task 3.2: Test with Multiple Nodes

Create `test-cluster.sh`:
```bash
#!/bin/bash

# Start 3 nodes in background
java -jar target/raft-cache-1.0-SNAPSHOT.jar node1 8001 &
PID1=$!

java -jar target/raft-cache-1.0-SNAPSHOT.jar node2 8002 &
PID2=$!

java -jar target/raft-cache-1.0-SNAPSHOT.jar node3 8003 &
PID3=$!

echo "Started nodes with PIDs: $PID1 $PID2 $PID3"
echo "Press Ctrl+C to stop all nodes"

# Wait for Ctrl+C
trap "kill $PID1 $PID2 $PID3; exit" SIGINT
wait
```

Run: `chmod +x test-cluster.sh && ./test-cluster.sh`

## Testing Checklist

- [ ] Single node starts and triggers election timeout
- [ ] Network server accepts connections
- [ ] Network client can send messages
- [ ] Multiple nodes can communicate
- [ ] Leader sends heartbeats periodically
- [ ] Followers reset election timer on heartbeat

## Week 1 Deliverables

By end of week 1:
1. ✅ Project structure with all base classes
2. ✅ Leader election timeout mechanism
3. ✅ Netty-based network communication
4. ✅ Message serialization/deserialization
5. ✅ Heartbeat mechanism from leader
6. ✅ Three nodes running and communicating

## Common Issues

**Issue**: "Address already in use"
- Solution: Kill existing processes on ports 8001-8003

**Issue**: "Connection refused"
- Solution: Make sure server node is started first

**Issue**: "Class not found"
- Solution: Run `mvn clean install` after adding new classes

## Next Week Preview

Week 2 will focus on:
- Complete vote request/response logic
- Log replication (AppendEntries with actual entries)
- Committing log entries to state machine
- Integrating cache operations with Raft log

Keep going! You're building a real distributed system! 🎯
