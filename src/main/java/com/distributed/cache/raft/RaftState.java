package com.distributed.cache.raft;

/**
 * Represents the three states a Raft node can be in
 */
public enum RaftState {
    FOLLOWER,   // Normal state, following the leader
    CANDIDATE,  // Requesting votes during election
    LEADER      // Elected leader, handling all client requests
}
