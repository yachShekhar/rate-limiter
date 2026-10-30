package com.yachshekhar;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

interface Clock {
    Instant nowInstant();
}

class SystemClock implements Clock {
    @Override
    public Instant nowInstant() {
        return Instant.now();
    }
}

interface RateLimiterStrategy {
    boolean rateLimit(String customerId);
}

/** A per-customer leaky bucket. Each accepted request adds one unit of water. */
public class LeakyBucketRateLimiter implements RateLimiterStrategy {
    private final Map<String, CustomerUsage> customerUsageById = new HashMap<>();
    private final long capacity;
    private final double leakUnitsPerNano;
    private final Clock clock;

    /** Creates a limiter whose buckets drain {@code leakUnits} during each leak period. */
    public LeakyBucketRateLimiter(long capacity, long leakUnits, Duration leakPeriod) {
        this(capacity, leakUnits, leakPeriod, new SystemClock());
    }

    /** Clock-injecting constructor for deterministic use. */
    LeakyBucketRateLimiter(long capacity, long leakUnits, Duration leakPeriod, Clock clock) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be greater than zero");
        }
        if (leakUnits <= 0) {
            throw new IllegalArgumentException("leakUnits must be greater than zero");
        }
        Objects.requireNonNull(leakPeriod, "leakPeriod");
        if (leakPeriod.isZero() || leakPeriod.isNegative()) {
            throw new IllegalArgumentException("leakPeriod must be positive");
        }
        this.capacity = capacity;
        double periodNanos = leakPeriod.getSeconds() * 1_000_000_000d + leakPeriod.getNano();
        this.leakUnitsPerNano = leakUnits / periodNanos;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public synchronized boolean rateLimit(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be null or empty");
        }
        Instant now = clock.nowInstant();
        CustomerUsage usage = customerUsageById.computeIfAbsent(
                customerId, ignored -> new CustomerUsage(now));
        usage.leak(now, leakUnitsPerNano);
        if (usage.getRequests() + 1.0 > capacity) {
            return false;
        }
        usage.addRequest();
        return true;
    }

    private static final class CustomerUsage {
        private double requests;
        private Instant lastLeakAt;

        private CustomerUsage(Instant now) {
            this.lastLeakAt = now;
        }

        private void leak(Instant now, double leakUnitsPerNano) {
            if (now.isBefore(lastLeakAt)) {
                return;
            }
            Duration elapsed = Duration.between(lastLeakAt, now);
            double elapsedNanos = elapsed.getSeconds() * 1_000_000_000d + elapsed.getNano();
            requests = Math.max(0.0, requests - elapsedNanos * leakUnitsPerNano);
            lastLeakAt = now;
        }

        private void addRequest() {
            requests += 1.0;
        }

        private double getRequests() {
            return requests;
        }
    }
}



