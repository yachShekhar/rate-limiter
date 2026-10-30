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

/** A per-customer sliding-window counter rate limiter. */
public class SlidingWindowCounterRateLimiter implements RateLimiterStrategy {
    private final Map<String, CustomerUsage> customerUsageById = new HashMap<>();
    private final Duration windowDuration;
    private final long requestLimit;
    private final Clock clock;

    public SlidingWindowCounterRateLimiter(Duration windowDuration, long requestLimit) {
        this(windowDuration, requestLimit, new SystemClock());
    }

    SlidingWindowCounterRateLimiter(Duration windowDuration, long requestLimit, Clock clock) {
        Objects.requireNonNull(windowDuration, "windowDuration");
        if (windowDuration.isNegative() || windowDuration.isZero() || windowDuration.toMillis() <= 0) {
            throw new IllegalArgumentException("windowDuration must be at least one millisecond");
        }
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("requestLimit must be greater than zero");
        }
        this.windowDuration = windowDuration;
        this.requestLimit = requestLimit;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public synchronized boolean rateLimit(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be null or empty");
        }
        long nowMillis = clock.nowInstant().toEpochMilli();
        long windowDurationMillis = windowDuration.toMillis();
        long windowNo = Math.floorDiv(nowMillis, windowDurationMillis);
        CustomerUsage usage = customerUsageById.computeIfAbsent(
                customerId, ignored -> new CustomerUsage(windowNo));
        usage.resetWindow(windowNo);
        long elapsedMillis = Math.floorMod(nowMillis, windowDurationMillis);
        double previousWindowWeight =
                (windowDurationMillis - elapsedMillis) / (double) windowDurationMillis;
        double estimatedCount = usage.getCurrentWindowCount()
                + usage.getPreviousWindowCount() * previousWindowWeight;
        if (estimatedCount + 1.0 > requestLimit) {
            return false;
        }
        usage.incrementCurrentWindowCount();
        return true;
    }
}

class CustomerUsage {
    private long windowNo;
    private long currentWindowCount;
    private long previousWindowCount;

    public CustomerUsage(long windowNo) {
        this.windowNo = windowNo;
    }

    public void resetWindow(long newWindowNo) {
        if (newWindowNo <= windowNo) {
            return;
        }
        previousWindowCount = newWindowNo == windowNo + 1 ? currentWindowCount : 0;
        currentWindowCount = 0;
        windowNo = newWindowNo;
    }

    public long getCurrentWindowCount() {
        return currentWindowCount;
    }

    public long getPreviousWindowCount() {
        return previousWindowCount;
    }

    public void incrementCurrentWindowCount() {
        currentWindowCount++;
    }
}
