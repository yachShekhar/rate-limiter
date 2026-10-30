package com.yachshekhar;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
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
public class CreditBasedRateLimiter implements RateLimiterStrategy {
    private final Map<String, CustomerUsage> customerUsageMap = new ConcurrentHashMap<>();
    private final Clock clock;
    private final long windowSize;
    private final Duration windowDuration;
    private final long maxCredits;
    CreditBasedRateLimiter(Duration windowDuration, long windowSize, Clock clock, long maxCredits) {
        this.windowDuration = Objects.requireNonNull(windowDuration);
        this.clock = Objects.requireNonNull(clock);
        if (windowSize <= 0)
            throw new IllegalArgumentException("limit must not be less or equals to zero");
        this.windowSize = windowSize;
        if (maxCredits < 0) throw new IllegalArgumentException("maxCredits must not be negative");
        this.maxCredits = maxCredits;
    }
    @Override
    public boolean rateLimit(String customerId) {
        if (customerId == null || customerId.isBlank()) throw  new IllegalArgumentException("customerId must not be null or empty");
        long windowNo = (long) Math.ceil((double) clock.nowInstant().toEpochMilli() / this.windowDuration.toMillis());
        CustomerUsage customerUsage = customerUsageMap.computeIfAbsent(customerId,_ -> new CustomerUsage(customerId, windowNo));
        customerUsage.resetWindowIfNeeded(windowNo, windowSize, maxCredits);
        if (customerUsage.getUsage() < this.windowSize){
            customerUsage.incrUsage();
            return true;
        }else if (customerUsage.getCredits() > 0){
            customerUsage.decrCredits();
            return true;
        }
        return false;
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
    private final String customerId;
    private long usage;
    private long windowNo;
    private long credits;

    CustomerUsage(String customerId, long windowNo) {
        this.customerId = Objects.requireNonNull(customerId);
        if (windowNo <= 0) throw new IllegalStateException("windowNo must not be null or empty");
        this.windowNo = windowNo;
    }
    public void incrUsage(){
        this.usage++;
    }
    public void decrCredits(){
        this.credits--;
    }
    public void resetWindowIfNeeded(long windowNo, long windowSize, long maxCredits) {
        if (this.windowNo < windowNo) {
            long fullWindows = windowNo - this.windowNo - 1;
            long creditsFromFullWindows = fullWindows * windowSize;
            long unusedCreditsFromPreviousWindow = this.credits + (windowSize - this.usage);
            long replenishedCredits = creditsFromFullWindows + unusedCreditsFromPreviousWindow;
            this.windowNo = windowNo;
            this.usage = 0;
            this.credits = Math.min(replenishedCredits, maxCredits);
        }
    }

    public long getUsage() {
        return this.usage;
    }

    public long getWindowNo() {
        return this.windowNo;
    }
    public long getCredits() {
        return this.credits;
    }
}


