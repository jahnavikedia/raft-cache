# DAY 6 – HTTP API, Client Integration, and Cluster Launch

## Overview

On **Day 6**, the Raft-based distributed cache was extended with a fully functional **HTTP layer**, **client interface**, and **configuration-driven cluster management**.  
This layer allows external applications (or human users) to interact with the distributed key-value store through RESTful APIs, while internally leveraging the Raft consensus logic for consistency.

---

## 1. Added Dependencies

### pom.xml Updates

The project now includes HTTP server, JSON serialization, and logging support:

```xml
<dependency>
    <groupId>io.javalin</groupId>
    <artifactId>javalin</artifactId>
    <version>5.6.3</version>
</dependency>
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-simple</artifactId>
    <version>2.0.9</version>
</dependency>
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.10.1</version>
</dependency>
```

## 2. HTTP REST Server

File: CacheRESTServer.java

Uses Javalin as the embedded web server.

Provides a REST interface to interact with the Raft-backed key-value store.

Includes full JSON input/output and leader redirection for Raft consistency.

| Method                | Endpoint                                                                         | Description |
| --------------------- | -------------------------------------------------------------------------------- | ----------- |
| `POST /cache/{key}`   | Create or update key-value entries. Redirects to leader if called on a follower. |             |
| `GET /cache/{key}`    | Retrieve a value directly (eventually consistent read).                          |             |
| `DELETE /cache/{key}` | Remove a key. Redirects to leader if not the leader node.                        |             |
| `GET /status`         | Returns node state, term, role, and log size.                                    |             |
| `GET /health`         | Returns 200 if healthy or 503 if isolated.                                       |             |
| `GET /metrics`        | Placeholder endpoint for request metrics (to be implemented later).              |             |

Key Behaviors

Leader detection is done via RaftState.

Follower nodes respond with 307 Temporary Redirect including the leader’s HTTP address.

Timeout handling for commit acknowledgments (5 seconds default).

Integrated JSON serialization via Gson.

## 3. Leader Proxy for Forwarding

File: LeaderProxy.java

Encapsulates HTTP forwarding to the cluster leader when the local node is a follower.

Tracks the current leader’s HTTP address and automatically retries on redirect.

Handles both PUT and DELETE forwarding with JSON payloads.

Methods:

forwardPutToLeader(String key, String value, String clientId, long seqNum)

forwardDeleteToLeader(String key, String clientId, long seqNum)

updateLeader(String leaderId)

isLeaderKnown() and getLeaderHttpAddress()

## 4. Client CLI Implementation

File: CacheClient.java

A command-line interface to test the REST API cluster.

Supported Commands:

```bash
put <key> <value>
get <key>
delete <key>
status
connect <nodeIndex>
```

Features:

Automatic retries on 307 redirects or 503 service unavailability.

Tracks request latency and success messages.

Can query cluster status to show all nodes and their roles.

Built using Java’s native HttpClient (Java 11+).

## 5. Configuration System

File: NodeConfiguration.java

Loads per-node configuration from YAML files under src/main/resources/.

Supports:

Node ID, Raft port, HTTP port

Cluster node list (ID, raftAddress, httpAddress)

Election/heartbeat timing

Validates port conflicts and allows environment overrides.

Example YAML (node-1-config.yaml)

```yaml
node:
    id: node-1
    raftPort: 9091
    httpPort: 8081
    dataDir: ./data/node-1

cluster:
    nodes:
        - id: node-1
          raftAddress: localhost:9091
          httpAddress: localhost:8081
        - id: node-2
          raftAddress: localhost:9092
          httpAddress: localhost:8082
        - id: node-3
          raftAddress: localhost:9093
          httpAddress: localhost:8083

    electionTimeoutMin: 1500
    electionTimeoutMax: 3000
    heartbeatInterval: 500
```

## 6. Cluster Startup and Shutdown Scripts

Scripts Added:

scripts/start-cluster.sh
Starts all 3 Raft nodes with short delays and prints their PIDs.

```bash
./scripts/start-cluster.sh
```

scripts/stop-cluster.sh
Gracefully terminates all nodes.

```bash
./scripts/stop-cluster.sh
```

scripts/test-client.sh
Runs the client with simple commands:

```bash
./scripts/test-client.sh put user:1 "Alice"
./scripts/test-client.sh get user:1
./scripts/test-client.sh status
```

## 7. Verification Tests

Manual Testing

After cluster startup:

```bash
curl http://localhost:8081/status
curl -X POST -H "Content-Type: application/json" \
     -d '{"value":"Alice","clientId":"client-1","sequenceNumber":1}' \
     http://localhost:8081/cache/user:1

```

Expected:

Leader node handles the request and returns success.

Followers return redirects (307) or consistent reads for GET.

Logs show AppendEntries being broadcast by leader.

## 8. Observed Results

HTTP servers started correctly on ports 8081, 8082, and 8083.

Leader election occurs automatically.

/status endpoint correctly reports Raft role and term.

Client commands print correct leader/follower information.

Cluster logs show stable heartbeats and leader activity.

## 9. Next Steps (Day 7)

Fix log replication commit advancement (commitIndex updates).

Implement advanceCommitIndex() logic in LeaderReplicator.

Ensure persisted logs are consistent across restarts.

## ✅ Summary

Day 6 deliverables achieved:

RESTful cache API over Raft.

Client CLI for cluster testing.

Leader forwarding with automatic retry.

YAML-based configuration and startup scripts.

Successful multi-node cluster startup and API-level communication.

Cluster is now externally accessible and functionally testable — next step is ensuring replicated data commits correctly across nodes.
