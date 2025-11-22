package com.distributed.cache.raft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages read lease for fast, safe reads.
 * Leader maintains a lease during which it can serve reads without heartbeat confirmation.
 */
public class ReadLease {
    private static final Logger logger = LoggerFactory.getLogger(ReadLease.class);
    private static final long LEASE_DURATION_MS = 1000;  // 1 second lease

    private volatile long leaseExpiresAt = 0;
    private final Object lock = new Object();

    /**
     * Renews the lease for LEASE_DURATION_MS from now.
     * Called after successful heartbeat to majority.
     */
    public void renewLease() {
        synchronized (lock) {
            long newExpiry = System.currentTimeMillis() + LEASE_DURATION_MS;
            this.leaseExpiresAt = newExpiry;
            logger.debug("Lease renewed until {} ({}ms from now)",
                        newExpiry, LEASE_DURATION_MS);
        }
    }

    /**
     * Checks if the lease is currently valid.
     */
    public boolean isValid() {
        long remaining = leaseExpiresAt - System.currentTimeMillis();
        boolean valid = remaining > 0;
        if (valid) {
            logger.trace("Lease valid, {}ms remaining", remaining);
        }
        return valid;
    }

    /**
     * Invalidates the lease immediately.
     * Called when stepping down from leader.
     */
    public void invalidate() {
        synchronized (lock) {
            this.leaseExpiresAt = 0;
            logger.info("Lease invalidated");
        }
    }

    /**
     * Returns remaining lease time in milliseconds.
     */
    public long getRemainingMs() {
        long remaining = leaseExpiresAt - System.currentTimeMillis();
        return Math.max(0, remaining);
    }
}
