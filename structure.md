# Project Structure

raft-cache
├── data
│ └── node-node-1
│ ├── persistent-state.properties
│ └── raft.log
├── logs
│ ├── node1.log
│ ├── node2.log
│ ├── node3.log
│ └── raft-cache.log
├── scripts
│ ├── start-cluster.sh
│ ├── stop-cluster.sh
│ └── test-client.sh
├── src
│ ├── main
│ │ ├── java
│ │ │ └── com
│ │ │ └── distributed
│ │ │ └── cache
│ │ │ ├── cache
│ │ │ │ ├── CacheEntry.java
│ │ │ │ └── CacheStore.java
│ │ │ ├── config
│ │ │ │ ├── ClusterConfig.java
│ │ │ │ └── NodeInfo.java
│ │ │ ├── demo
│ │ │ │ ├── KVStoreDemo.java
│ │ │ │ └── RaftDemo.java
│ │ │ ├── ml
│ │ │ │ └── MLClient.java
│ │ │ ├── network
│ │ │ │ ├── MessageSerializer.java
│ │ │ │ ├── NetworkBase.java
│ │ │ │ └── PeerManager.java
│ │ │ ├── persistence
│ │ │ │ └── PersistentState.java
│ │ │ ├── raft
│ │ │ │ ├── api
│ │ │ │ │ ├── CacheRESTServer.java
│ │ │ │ │ ├── ClientRequest.java
│ │ │ │ │ ├── ClientResponse.java
│ │ │ │ │ └── LeaderProxy.java
│ │ │ │ ├── client
│ │ │ │ │ ├── CacheClient.java
│ │ │ │ │ └── ClientConfig.java
│ │ │ │ ├── config
│ │ │ │ │ └── NodeConfiguration.java
│ │ │ │ ├── LogEntry.java
│ │ │ │ ├── LogEntryType.java
│ │ │ │ ├── Message.java
│ │ │ │ ├── RaftNode.java
│ │ │ │ └── RaftState.java
│ │ │ ├── replication
│ │ │ │ ├── AppendEntriesRequest.java
│ │ │ │ ├── AppendEntriesResponse.java
│ │ │ │ ├── FollowerReplicator.java
│ │ │ │ ├── LeaderReplicator.java
│ │ │ │ └── RaftLog.java
│ │ │ ├── storage
│ │ │ │ └── LogPersistence.java
│ │ │ ├── store
│ │ │ │ ├── CommandType.java
│ │ │ │ ├── KeyValueCommand.java
│ │ │ │ └── KeyValueStore.java
│ │ │ ├── .DS_Store
│ │ │ └── Main.java
│ │ └── resources
│ │ ├── client-config.yaml
│ │ ├── logback.xml
│ │ ├── node-1-config.yaml
│ │ ├── node-2-config.yaml
│ │ └── node-3-config.yaml
│ └── test
│ ├── java
│ │ └── com
│ │ └── distributed
│ │ └── cache
│ │ ├── cache
│ │ │ └── CacheStoreTest.java
│ │ ├── config
│ │ │ └── ClusterConfigTest.java
│ │ ├── network
│ │ │ └── NetworkBaseTest.java
│ │ ├── persistence
│ │ │ └── PersistentStateTest.java
│ │ ├── raft
│ │ │ ├── RaftNodeTest.java
│ │ │ └── TermManagementTest.java
│ │ └── replication
│ │ └── ManualReplicationTest.java
│ └── resources
│ └── test-cluster-config.yaml
├── target
│ ├── classes
│ │ ├── com
│ │ │ └── distributed
│ │ │ └── cache
│ │ │ ├── cache
│ │ │ │ ├── CacheEntry.class
│ │ │ │ └── CacheStore.class
│ │ │ ├── config
│ │ │ │ ├── ClusterConfig.class
│ │ │ │ └── NodeInfo.class
│ │ │ ├── demo
│ │ │ │ ├── KVStoreDemo.class
│ │ │ │ └── RaftDemo.class
│ │ │ ├── ml
│ │ │ │ └── MLClient.class
│ │ │ ├── network
│ │ │ │ ├── MessageSerializer.class
│ │ │ │ ├── NetworkBase.class
│ │ │ │ ├── NetworkBase$1.class
│   │   │           │   ├── NetworkBase$2.class
│   │   │           │   ├── NetworkBase$InboundMessageHandler.class
│ │ │ │ ├── PeerManager.class
│ │ │ │ └── PeerManager$1.class
│   │   │           ├── persistence
│   │   │           │   └── PersistentState.class
│   │   │           ├── raft
│   │   │           │   ├── api
│   │   │           │   │   ├── CacheRESTServer.class
│   │   │           │   │   ├── ClientRequest.class
│   │   │           │   │   ├── ClientResponse.class
│   │   │           │   │   └── LeaderProxy.class
│   │   │           │   ├── client
│   │   │           │   │   ├── CacheClient.class
│   │   │           │   │   └── ClientConfig.class
│   │   │           │   ├── config
│   │   │           │   │   └── NodeConfiguration.class
│   │   │           │   ├── LogEntry.class
│   │   │           │   ├── LogEntryType.class
│   │   │           │   ├── Message.class
│   │   │           │   ├── Message$MessageType.class
│ │ │ │ ├── RaftNode.class
│ │ │ │ ├── RaftNode$1.class
│   │   │           │   └── RaftState.class
│   │   │           ├── replication
│   │   │           │   ├── AppendEntriesRequest.class
│   │   │           │   ├── AppendEntriesResponse.class
│   │   │           │   ├── FollowerReplicator.class
│   │   │           │   ├── LeaderReplicator.class
│   │   │           │   └── RaftLog.class
│   │   │           ├── storage
│   │   │           │   └── LogPersistence.class
│   │   │           ├── store
│   │   │           │   ├── CommandType.class
│   │   │           │   ├── KeyValueCommand.class
│   │   │           │   ├── KeyValueStore.class
│   │   │           │   ├── KeyValueStore$1.class
│   │   │           │   └── KeyValueStore$NotLeaderException.class
│ │ │ └── Main.class
│ │ ├── client-config.yaml
│ │ ├── logback.xml
│ │ ├── node-1-config.yaml
│ │ ├── node-2-config.yaml
│ │ └── node-3-config.yaml
│ ├── generated-sources
│ │ └── annotations
│ ├── generated-test-sources
│ │ └── test-annotations
│ ├── maven-archiver
│ │ └── pom.properties
│ ├── maven-status
│ │ └── maven-compiler-plugin
│ │ ├── compile
│ │ │ └── default-compile
│ │ │ ├── createdFiles.lst
│ │ │ └── inputFiles.lst
│ │ └── testCompile
│ │ └── default-testCompile
│ │ ├── createdFiles.lst
│ │ └── inputFiles.lst
│ ├── test-classes
│ │ ├── com
│ │ │ └── distributed
│ │ │ └── cache
│ │ │ ├── cache
│ │ │ │ └── CacheStoreTest.class
│ │ │ ├── config
│ │ │ │ └── ClusterConfigTest.class
│ │ │ ├── network
│ │ │ │ └── NetworkBaseTest.class
│ │ │ ├── persistence
│ │ │ │ └── PersistentStateTest.class
│ │ │ ├── raft
│ │ │ │ ├── RaftNodeElectionTest.class
│ │ │ │ └── TermManagementTest.class
│ │ │ └── replication
│ │ │ └── ManualReplicationTest.class
│ │ └── test-cluster-config.yaml
│ ├── original-raft-cache-1.0-SNAPSHOT.jar
│ └── raft-cache-1.0-SNAPSHOT.jar
├── .DS_Store
├── .gitattributes
├── .gitignore
├── BALANCED_DIVISION.md
├── cluster-config.yaml
├── COMMIT_MESSAGE.txt
├── CROSS_LEARNING_GUIDE.md
├── DAY2_ELECTION_IMPLEMENTATION.md
├── DAY2_HEARTBEAT_IMPLEMENTATION.md
├── DAY5_IMPLEMENTATION_SUMMARY.md
├── DAY5_TERM_MANAGEMENT_IMPLEMENTATION.md
├── DAY5.md
├── DAY6_IMPLEMENTATION_SUMMARY.md
├── dependency-reduced-pom.xml
├── pom.xml
├── PROJECT_STRUCTURE.txt
├── QUICK_REFERENCE.md
├── QUICKSTART.md
├── README.md
├── RUNNING_THE_KV_STORE.md
├── SETUP.md
├── START_HERE.md
├── structure.md
├── TASK_TRACKING.md
├── TEAM_DIVISION.md
├── TESTING_GUIDE.md
├── VISUAL_TIMELINE.md
└── WEEK1_GUIDE.md
