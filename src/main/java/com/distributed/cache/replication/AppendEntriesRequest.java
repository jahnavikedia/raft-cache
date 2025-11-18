package com.distributed.cache.replication;

import com.distributed.cache.raft.LogEntry;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * AppendEntries RPC request.
 *
 * Sent by the leader to replicate log entries and provide heartbeats.
 * An empty entries list constitutes a heartbeat.
 */
public class AppendEntriesRequest {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @JsonProperty("term")
    private final int term;

    @JsonProperty("leaderId")
    private final String leaderId;

    @JsonProperty("prevLogIndex")
    private final long prevLogIndex;

    @JsonProperty("prevLogTerm")
    private final int prevLogTerm;

    @JsonProperty("entries")
    private final List<LogEntry> entries;

    @JsonProperty("leaderCommit")
    private final long leaderCommit;

    public AppendEntriesRequest(
            @JsonProperty("term") int term,
            @JsonProperty("leaderId") String leaderId,
            @JsonProperty("prevLogIndex") long prevLogIndex,
            @JsonProperty("prevLogTerm") int prevLogTerm,
            @JsonProperty("entries") List<LogEntry> entries,
            @JsonProperty("leaderCommit") long leaderCommit) {
        this.term = term;
        this.leaderId = leaderId;
        this.prevLogIndex = prevLogIndex;
        this.prevLogTerm = prevLogTerm;
        this.entries = entries != null ? entries : new ArrayList<>();
        this.leaderCommit = leaderCommit;
    }

    public int getTerm() {
        return term;
    }

    public String getLeaderId() {
        return leaderId;
    }

    public long getPrevLogIndex() {
        return prevLogIndex;
    }

    public int getPrevLogTerm() {
        return prevLogTerm;
    }

    public List<LogEntry> getEntries() {
        return entries;
    }

    public long getLeaderCommit() {
        return leaderCommit;
    }

    /**
     * Check if this is a heartbeat (no entries)
     */
    public boolean isHeartbeat() {
        return entries.isEmpty();
    }

    /**
     * Serialize to JSON
     */
    public String toJson() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize AppendEntriesRequest", e);
        }
    }

    /**
     * Deserialize from JSON
     */
    public static AppendEntriesRequest fromJson(String json) {
        try {
            return objectMapper.readValue(json, AppendEntriesRequest.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize AppendEntriesRequest", e);
        }
    }

    @Override
    public String toString() {
        return String.format("AppendEntriesRequest{term=%d, leaderId='%s', prevLogIndex=%d, prevLogTerm=%d, entries=%d, leaderCommit=%d}",
                term, leaderId, prevLogIndex, prevLogTerm, entries.size(), leaderCommit);
    }
}
