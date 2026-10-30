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

/** A per-customer token bucket with continuous token replenishment. */
public class TokenBucketRateLimiter implements RateLimiterStrategy {
    private final Map<String, Bucket> buckets = new HashMap<>();
    private final long capacity;
    private final double tokensPerNano;
    private final Clock clock;

    /**
     * @param capacity maximum number of tokens a customer can accumulate
     * @param refillTokens number of tokens replenished during {@code refillPeriod}
     * @param refillPeriod duration over which {@code refillTokens} are replenished
     */
    public TokenBucketRateLimiter(long capacity, long refillTokens, Duration refillPeriod) {
        this(capacity, refillTokens, refillPeriod, new SystemClock());
    }
    /**
     * @param capacity maximum number of tokens a customer can accumulate
     * @param refillTokens number of tokens replenished during {@code refillPeriod}
     * @param refillPeriod duration over which {@code refillTokens} are replenished
     * @param clock time source, injectable for deterministic use
     */
    public TokenBucketRateLimiter(long capacity, long refillTokens, Duration refillPeriod, Clock clock) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be greater than zero");
        }
        if (refillTokens <= 0) {
            throw new IllegalArgumentException("refillTokens must be greater than zero");
        }
        Objects.requireNonNull(refillPeriod, "refillPeriod");
        if (refillPeriod.isZero() || refillPeriod.isNegative()) {
            throw new IllegalArgumentException("refillPeriod must be positive");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
        this.capacity = capacity;
        double periodNanos = refillPeriod.getSeconds() * 1_000_000_000d + refillPeriod.getNano();
        this.tokensPerNano = refillTokens / periodNanos;
    }

    @Override
    public synchronized boolean rateLimit(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be null or empty");
        }
        Instant now = clock.nowInstant();
        Bucket bucket = buckets.computeIfAbsent(customerId, _ -> new Bucket(capacity, now));
        bucket.refill(now, capacity, tokensPerNano);
        if (bucket.getTokens() < 1.0) {
            return false;
        }
        bucket.decrToken();
        return true;
    }
}

class Bucket {
    private double tokens;
    private Instant lastRefillAt;

    public Bucket(long capacity, Instant now) {
        this.tokens = capacity;
        this.lastRefillAt = now;
    }

    public void refill(Instant now, long capacity, double tokensPerNano) {
        // A clock moving backwards must not remove tokens or move the refill point backwards.
        if (now.isBefore(lastRefillAt)) {
            return;
        }
        Duration elapsed = Duration.between(lastRefillAt, now);
        double elapsedNanos = elapsed.getSeconds() * 1_000_000_000d + elapsed.getNano();
        tokens = Math.min(capacity, tokens + elapsedNanos * tokensPerNano);
        lastRefillAt = now;
    }
    public void decrToken() {tokens--;}
    public double getTokens() {return tokens;}
}


