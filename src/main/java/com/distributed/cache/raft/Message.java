package com.distributed.cache.raft;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents messages exchanged between Raft nodes
 */
public class Message {

    public enum MessageType {
        REQUEST_VOTE, // Candidate requesting votes
        REQUEST_VOTE_RESPONSE, // Response to vote request
        APPEND_ENTRIES, // Leader sending log entries (also heartbeat)
        APPEND_ENTRIES_RESPONSE // Response to append entries
    }

    @JsonProperty("type")
    private MessageType type;

    @JsonProperty("term")
    private long term;

    @JsonProperty("senderId")
    private String senderId;

    @JsonProperty("success")
    private boolean success;

    // Explicitly include senderTerm for Raft safety (used in term updates)
    @JsonProperty("senderTerm")
    private long senderTerm;

    // For RequestVote
    @JsonProperty("candidateId")
    private String candidateId;

    @JsonProperty("lastLogIndex")
    private long lastLogIndex;

    @JsonProperty("lastLogTerm")
    private long lastLogTerm;

    // For AppendEntries
    @JsonProperty("leaderId")
    private String leaderId;

    @JsonProperty("prevLogIndex")
    private long prevLogIndex;

    @JsonProperty("prevLogTerm")
    private long prevLogTerm;

    @JsonProperty("leaderCommit")
    private long leaderCommit;

    @JsonProperty("entries")
    private List<LogEntry> entries;

    @JsonProperty("matchIndex")
    private long matchIndex;

    // Default constructor for Jackson
    public Message() {
        this.entries = new ArrayList<>();
    }

    public Message(MessageType type, long term, String senderId) {
        this.type = type;
        this.term = term;
        this.senderId = senderId;
        this.senderTerm = term; // ensure senderTerm is set for all outgoing messages
    }

    // Getters and setters
    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public long getTerm() {
        return term;
    }

    public void setTerm(long term) {
        this.term = term;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public long getSenderTerm() {
        return senderTerm;
    }

    public void setSenderTerm(long senderTerm) {
        this.senderTerm = senderTerm;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }

    public long getLastLogIndex() {
        return lastLogIndex;
    }

    public void setLastLogIndex(long lastLogIndex) {
        this.lastLogIndex = lastLogIndex;
    }

    public long getLastLogTerm() {
        return lastLogTerm;
    }

    public void setLastLogTerm(long lastLogTerm) {
        this.lastLogTerm = lastLogTerm;
    }

    public String getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(String leaderId) {
        this.leaderId = leaderId;
    }

    public long getPrevLogIndex() {
        return prevLogIndex;
    }

    public void setPrevLogIndex(long prevLogIndex) {
        this.prevLogIndex = prevLogIndex;
    }

    public long getPrevLogTerm() {
        return prevLogTerm;
    }

    public void setPrevLogTerm(long prevLogTerm) {
        this.prevLogTerm = prevLogTerm;
    }

    public long getLeaderCommit() {
        return leaderCommit;
    }

    public void setLeaderCommit(long leaderCommit) {
        this.leaderCommit = leaderCommit;
    }

    public List<LogEntry> getEntries() {
        return entries != null ? entries : new ArrayList<>();
    }

    public void setEntries(List<LogEntry> entries) {
        this.entries = entries;
    }

    public long getMatchIndex() {
        return matchIndex;
    }

    public void setMatchIndex(long matchIndex) {
        this.matchIndex = matchIndex;
    }

    @Override
    public String toString() {
        return String.format("Message{type=%s, term=%d, senderId='%s', senderTerm=%d}",
                type, term, senderId, senderTerm);
    }
}
