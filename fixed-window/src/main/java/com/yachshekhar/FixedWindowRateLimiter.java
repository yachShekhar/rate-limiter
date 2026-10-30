package com.yachshekhar;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
interface Clock {
    Instant nowInstant();
}

class SystemClock implements Clock {

    @Override
    public Instant nowInstant() {
        return Instant.now();
    }
}

class FakeClock implements Clock {
    private Instant instant;
    FakeClock(Instant instant){
        this.instant = instant;
    }

    @Override
    public Instant nowInstant() {
        return this.instant;
    }
}
public class FixedWindowRateLimiter implements RateLimiterStrategy {
    private final Map<String, CustomerUsage> customerUsageMap = new HashMap<>();
    private final Clock clock;
    private final Long windowSize;
    private final Duration windowDuration;
    FixedWindowRateLimiter(Duration windowDuration, Long windowSize, Clock clock) {
        this.windowDuration = Objects.requireNonNull(windowDuration);
        this.clock = Objects.requireNonNull(clock);
        if (windowSize == null || windowSize <= 0)
            throw new IllegalArgumentException("limit must not be less or equals to zero");
        this.windowSize = windowSize;
    }
    @Override
    public boolean rateLimit(String customerId) {
        if (customerId == null || customerId.isBlank()) throw  new IllegalArgumentException("customerId must not be null or empty");
        long windowNo = (long) Math.ceil((double) clock.nowInstant().toEpochMilli() / this.windowDuration.toMillis());
        CustomerUsage customerUsage = customerUsageMap.computeIfAbsent(customerId,_ -> new CustomerUsage(customerId, windowNo));
        customerUsage.resetWindowIfNeeded(windowNo);
        if (customerUsage.getUsage() + 1 > this.windowSize)
            return false;
        customerUsage.incrUsage();
        return true;
    }
    static void main() {
        //TIP Press <shortcut actionId="ShowIntentionActions"/> with your caret at the highlighted text
        // to see how IntelliJ IDEA suggests fixing it.
        IO.println(String.format("Hello and welcome!"));

        for (int i = 1; i <= 5; i++) {
            //TIP Press <shortcut actionId="Debug"/> to start debugging your code. We have set one <icon src="AllIcons.Debugger.Db_set_breakpoint"/> breakpoint
            // for you, but you can always add more by pressing <shortcut actionId="ToggleLineBreakpoint"/>.
            IO.println("i = " + i);
        }
    }
}
interface RateLimiterStrategy {
    boolean rateLimit(String customerId);
}
class CustomerUsage {
    private String customerId;
    private long usage;
    private long windowNo;

    CustomerUsage(String customerId, long windowNo) {
        this.customerId = Objects.requireNonNull(customerId);
        if (windowNo <= 0) throw new IllegalStateException("windowNo must not be null or empty");
        this.windowNo = windowNo;
    }
    public void incrUsage(){
        this.usage++;
    }

    public void resetWindowIfNeeded(long windowNo) {
        if (this.windowNo < windowNo) {
            this.windowNo = windowNo;
            this.usage = 0;
        }
    }

    public long getUsage() {
        return this.usage;
    }
    public long getWindowNo() {
        return this.windowNo;
    }
}


