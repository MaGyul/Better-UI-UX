package dev.magyul.betteruiux.core.sync;

import java.util.function.LongSupplier;

/** 토큰 버킷 속도 제한(초당 {@code perSecond} 개, 순간 최대 {@code burst} 개). 메시지/액션 폭주를 막는다. */
public final class RateLimiter {
    private final double perSecond;
    private final double burst;
    private final LongSupplier nanos;
    private double tokens;
    private long last;

    public RateLimiter(double perSecond, int burst, LongSupplier nanos) {
        this.perSecond = perSecond;
        this.burst = burst;
        this.nanos = nanos;
        this.tokens = burst;
        this.last = nanos.getAsLong();
    }

    public RateLimiter(double perSecond, int burst) { this(perSecond, burst, System::nanoTime); }

    /** 하나 쓸 수 있으면 쓰고 true, 한도를 넘었으면 false. */
    public synchronized boolean allow() {
        long now = nanos.getAsLong();
        tokens = Math.min(burst, tokens + (now - last) / 1e9 * perSecond);
        last = now;
        if (tokens < 1) return false;
        tokens -= 1;
        return true;
    }
}
