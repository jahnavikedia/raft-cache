# Project Structure

raft-cache
├── data
│   ├── node-node1
│   │   └── persistent-state.properties
│   ├── node-node2
│   │   └── persistent-state.properties
│   ├── node-node3
│   │   └── persistent-state.properties
│   ├── node-testNode
│   ├── node-testNode_missing
│   │   └── persistent-state.properties
│   └── node-testTerm
│       └── persistent-state.properties
├── logs
│   └── raft-cache.log
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com
│   │   │       └── distributed
│   │   │           └── cache
│   │   │               ├── cache
│   │   │               │   ├── CacheEntry.java
│   │   │               │   └── CacheStore.java
│   │   │               ├── config
│   │   │               │   ├── ClusterConfig.java
│   │   │               │   └── NodeInfo.java
│   │   │               ├── data
│   │   │               ├── demo
│   │   │               │   └── RaftDemo.java
│   │   │               ├── ml
│   │   │               │   └── MLClient.java
│   │   │               ├── network
│   │   │               │   ├── MessageSerializer.java
│   │   │               │   ├── NetworkBase.java
│   │   │               │   └── PeerManager.java
│   │   │               ├── persistence
│   │   │               │   └── PersistentState.java
│   │   │               ├── raft
│   │   │               │   ├── LogEntry.java
│   │   │               │   ├── Message.java
│   │   │               │   ├── RaftNode.java
│   │   │               │   └── RaftState.java
│   │   │               ├── .DS_Store
│   │   │               └── Main.java
│   │   └── resources
│   │       └── logback.xml
│   └── test
│       ├── java
│       │   └── com
│       │       └── distributed
│       │           └── cache
│       │               ├── cache
│       │               │   └── CacheStoreTest.java
│       │               ├── config
│       │               │   └── ClusterConfigTest.java
│       │               ├── network
│       │               │   └── NetworkBaseTest.java
│       │               ├── persistence
│       │               │   └── PersistentStateTest.java
│       │               └── raft
│       │                   ├── RaftNodeTest.java
│       │                   └── TermManagementTest.java
│       └── resources
│           └── test-cluster-config.yaml
├── target
│   ├── classes
│   │   ├── com
│   │   │   └── distributed
│   │   │       └── cache
│   │   │           ├── cache
│   │   │           │   ├── CacheEntry.class
│   │   │           │   └── CacheStore.class
│   │   │           ├── config
│   │   │           │   ├── ClusterConfig.class
│   │   │           │   └── NodeInfo.class
│   │   │           ├── demo
│   │   │           │   └── RaftDemo.class
│   │   │           ├── ml
│   │   │           │   └── MLClient.class
│   │   │           ├── network
│   │   │           │   ├── MessageSerializer.class
│   │   │           │   ├── NetworkBase.class
│   │   │           │   ├── NetworkBase$1.class
│   │   │           │   ├── NetworkBase$2.class
│   │   │           │   ├── NetworkBase$InboundMessageHandler.class
│   │   │           │   ├── PeerManager.class
│   │   │           │   └── PeerManager$1.class
│   │   │           ├── persistence
│   │   │           │   └── PersistentState.class
│   │   │           ├── raft
│   │   │           │   ├── LogEntry.class
│   │   │           │   ├── Message.class
│   │   │           │   ├── Message$MessageType.class
│   │   │           │   ├── RaftNode.class
│   │   │           │   ├── RaftNode$1.class
│   │   │           │   └── RaftState.class
│   │   │           └── Main.class
│   │   └── logback.xml
│   ├── generated-sources
│   │   └── annotations
│   ├── generated-test-sources
│   │   └── test-annotations
│   ├── maven-status
│   │   └── maven-compiler-plugin
│   │       ├── compile
│   │       │   └── default-compile
│   │       │       ├── createdFiles.lst
│   │       │       └── inputFiles.lst
│   │       └── testCompile
│   │           └── default-testCompile
│   │               ├── createdFiles.lst
│   │               └── inputFiles.lst
│   ├── surefire-reports
│   │   ├── com.distributed.cache.raft.TermManagementTest.txt
│   │   └── TEST-com.distributed.cache.raft.TermManagementTest.xml
│   └── test-classes
│       ├── com
│       │   └── distributed
│       │       └── cache
│       │           ├── cache
│       │           │   └── CacheStoreTest.class
│       │           ├── config
│       │           │   └── ClusterConfigTest.class
│       │           ├── network
│       │           │   └── NetworkBaseTest.class
│       │           ├── persistence
│       │           │   └── PersistentStateTest.class
│       │           └── raft
│       │               ├── RaftNodeElectionTest.class
│       │               └── TermManagementTest.class
│       └── test-cluster-config.yaml
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
├── DAY5.md
├── dependency-reduced-pom.xml
├── pom.xml
├── PROJECT_STRUCTURE.txt
├── QUICK_REFERENCE.md
├── QUICKSTART.md
├── README.md
├── SETUP.md
├── START_HERE.md
├── structure.md
├── TASK_TRACKING.md
├── TEAM_DIVISION.md
├── TESTING_GUIDE.md
├── VISUAL_TIMELINE.md
└── WEEK1_GUIDE.md
