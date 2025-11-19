package com.distributed.cache.raft.api;

public class ClientRequest {
    private String clientId;
    private long sequenceNumber;
    private String key;
    private String value;

    public String getClientId() {
        return clientId;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}
