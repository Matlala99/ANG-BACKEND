package com.agc.cms.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_SECONDS = 60; // 1 minute lockout

    private static class AttemptRecord {
        int count;
        Instant firstAttempt;
        Instant lockedUntil;

        AttemptRecord() {
            this.count = 1;
            this.firstAttempt = Instant.now();
            this.lockedUntil = null;
        }
    }

    private final Map<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

    public boolean isAllowed(String clientKey) {
        cleanExpired();
        AttemptRecord record = attempts.get(clientKey);
        if (record == null) {
            return true;
        }

        Instant now = Instant.now();
        if (record.lockedUntil != null) {
            if (now.isBefore(record.lockedUntil)) {
                return false;
            } else {
                // Lockout expired, reset
                attempts.remove(clientKey);
                return true;
            }
        }

        // Check if window has passed
        if (record.firstAttempt.plusSeconds(LOCKOUT_DURATION_SECONDS).isBefore(now)) {
            attempts.remove(clientKey);
            return true;
        }

        return record.count <= MAX_ATTEMPTS;
    }

    public void recordFailedAttempt(String clientKey) {
        Instant now = Instant.now();
        attempts.compute(clientKey, (key, existing) -> {
            if (existing == null || existing.firstAttempt.plusSeconds(LOCKOUT_DURATION_SECONDS).isBefore(now)) {
                return new AttemptRecord();
            }
            existing.count++;
            if (existing.count > MAX_ATTEMPTS && existing.lockedUntil == null) {
                existing.lockedUntil = now.plusSeconds(LOCKOUT_DURATION_SECONDS);
            }
            return existing;
        });
    }

    public void reset(String clientKey) {
        attempts.remove(clientKey);
    }

    public long getSecondsRemaining(String clientKey) {
        AttemptRecord record = attempts.get(clientKey);
        if (record != null && record.lockedUntil != null) {
            long remaining = java.time.Duration.between(Instant.now(), record.lockedUntil).toSeconds();
            return Math.max(remaining, 1);
        }
        return LOCKOUT_DURATION_SECONDS;
    }

    private void cleanExpired() {
        Instant now = Instant.now();
        attempts.entrySet().removeIf(entry -> {
            AttemptRecord r = entry.getValue();
            if (r.lockedUntil != null) {
                return now.isAfter(r.lockedUntil);
            }
            return r.firstAttempt.plusSeconds(LOCKOUT_DURATION_SECONDS * 2).isBefore(now);
        });
    }
}
