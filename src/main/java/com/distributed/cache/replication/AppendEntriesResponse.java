package com.distributed.cache.replication;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * AppendEntries RPC response.
 *
 * Sent by followers in response to AppendEntries requests from the leader.
 */
public class AppendEntriesResponse {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @JsonProperty("term")
    private final int term;

    @JsonProperty("success")
    private final boolean success;

    @JsonProperty("matchIndex")
    private final long matchIndex;

    @JsonProperty("followerId")
    private final String followerId;

    public AppendEntriesResponse(
            @JsonProperty("term") int term,
            @JsonProperty("success") boolean success,
            @JsonProperty("matchIndex") long matchIndex,
            @JsonProperty("followerId") String followerId) {
        this.term = term;
        this.success = success;
        this.matchIndex = matchIndex;
        this.followerId = followerId;
    }

    public int getTerm() {
        return term;
    }

    public boolean isSuccess() {
        return success;
    }

    public long getMatchIndex() {
        return matchIndex;
    }

    public String getFollowerId() {
        return followerId;
    }

    /**
     * Serialize to JSON
     */
    public String toJson() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize AppendEntriesResponse", e);
        }
    }

    /**
     * Deserialize from JSON
     */
    public static AppendEntriesResponse fromJson(String json) {
        try {
            return objectMapper.readValue(json, AppendEntriesResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize AppendEntriesResponse", e);
        }
    }

    @Override
    public String toString() {
        return String.format("AppendEntriesResponse{term=%d, success=%s, matchIndex=%d, followerId='%s'}",
                term, success, matchIndex, followerId);
    }
}
