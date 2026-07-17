package com.enviouse.futureshops.server.security;

import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ServerRequestRateLimiter {
    private final Map<String, BucketPolicy> policies;
    private final int maxKeys;
    private final long idleRetentionNanos;
    private final NanoClock clock;
    private final LinkedHashMap<RequestKey, Bucket> buckets = new LinkedHashMap<>();

    private boolean clockInitialized;
    private long lastObservedNanos;

    public ServerRequestRateLimiter(
            Map<String, BucketPolicy> policies,
            int maxKeys,
            Duration idleRetention,
            NanoClock clock
    ) {
        if (maxKeys <= 0) {
            throw new IllegalArgumentException("maxKeys must be positive");
        }

        Objects.requireNonNull(policies, "policies");
        if (policies.isEmpty()) {
            throw new IllegalArgumentException("policies must not be empty");
        }

        LinkedHashMap<String, BucketPolicy> checkedPolicies = new LinkedHashMap<>();
        for (Map.Entry<String, BucketPolicy> entry : policies.entrySet()) {
            String action = requireAction(entry.getKey());
            checkedPolicies.put(action, Objects.requireNonNull(entry.getValue(), "policy"));
        }

        this.policies = Collections.unmodifiableMap(checkedPolicies);
        this.maxKeys = maxKeys;
        this.idleRetentionNanos = requirePositiveNanos(idleRetention, "idleRetention");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized Decision tryAcquire(UUID playerId, String action) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(action, "action");

        BucketPolicy policy = policies.get(action);
        if (policy == null) {
            return Decision.rejected(Reason.UNKNOWN_ACTION, Duration.ZERO, 0);
        }

        long now = monotonicNow();
        RequestKey key = new RequestKey(playerId, action);
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            pruneReclaimableBuckets(now);
            if (buckets.size() >= maxKeys) {
                return Decision.rejected(Reason.CACHE_FULL, cacheRetryAfter(now), 0);
            }

            bucket = new Bucket(policy.capacity(), now);
            buckets.put(key, bucket);
        }

        refill(bucket, policy, now);
        bucket.lastAccessNanos = now;
        if (bucket.tokens == 0) {
            return Decision.rejected(
                    Reason.RATE_LIMITED,
                    Duration.ofNanos(nanosUntilNextToken(bucket, policy, now)),
                    0
            );
        }

        bucket.tokens = Math.subtractExact(bucket.tokens, 1);
        return Decision.allowed(bucket.tokens);
    }

    public synchronized int keyCount() {
        return buckets.size();
    }

    public synchronized int removePlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        int removed = 0;
        Iterator<RequestKey> iterator = buckets.keySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().playerId().equals(playerId)) {
                iterator.remove();
                removed = Math.addExact(removed, 1);
            }
        }
        return removed;
    }

    public synchronized void clear() {
        buckets.clear();
    }

    public Set<String> configuredActions() {
        return policies.keySet();
    }

    private long monotonicNow() {
        long observed = clock.nanoTime();
        if (!clockInitialized) {
            clockInitialized = true;
            lastObservedNanos = observed;
        } else if (observed > lastObservedNanos) {
            lastObservedNanos = observed;
        }
        return lastObservedNanos;
    }

    private void pruneReclaimableBuckets(long now) {
        Iterator<Map.Entry<RequestKey, Bucket>> iterator = buckets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<RequestKey, Bucket> entry = iterator.next();
            BucketPolicy policy = policies.get(entry.getKey().action());
            Bucket bucket = entry.getValue();
            refill(bucket, policy, now);
            if (bucket.tokens == policy.capacity()
                    && elapsedNanos(now, bucket.lastAccessNanos) >= idleRetentionNanos) {
                iterator.remove();
            }
        }
    }

    private Duration cacheRetryAfter(long now) {
        long shortestWait = Long.MAX_VALUE;
        for (Map.Entry<RequestKey, Bucket> entry : buckets.entrySet()) {
            BucketPolicy policy = policies.get(entry.getKey().action());
            Bucket bucket = entry.getValue();
            refill(bucket, policy, now);

            long idleElapsed = elapsedNanos(now, bucket.lastAccessNanos);
            long idleWait = idleElapsed >= idleRetentionNanos
                    ? 0L
                    : Math.subtractExact(idleRetentionNanos, idleElapsed);
            long refillWait = nanosUntilFull(bucket, policy, now);
            long candidate = Math.max(idleWait, refillWait);
            shortestWait = Math.min(shortestWait, candidate);
        }
        return Duration.ofNanos(shortestWait);
    }

    private static void refill(Bucket bucket, BucketPolicy policy, long now) {
        long elapsed = elapsedNanos(now, bucket.lastRefillNanos);
        long periods = elapsed / policy.refillPeriodNanos();
        if (periods == 0L) {
            return;
        }

        if (bucket.tokens < policy.capacity()) {
            int missing = Math.subtractExact(policy.capacity(), bucket.tokens);
            long periodsToFull = ceilDivide(missing, policy.refillTokens());
            if (periods >= periodsToFull) {
                bucket.tokens = policy.capacity();
            } else {
                long added = Math.multiplyExact(periods, (long) policy.refillTokens());
                bucket.tokens = Math.addExact(bucket.tokens, Math.toIntExact(added));
            }
        }

        try {
            long advanced = Math.multiplyExact(periods, policy.refillPeriodNanos());
            bucket.lastRefillNanos = Math.addExact(bucket.lastRefillNanos, advanced);
        } catch (ArithmeticException ignored) {
            bucket.lastRefillNanos = now;
        }
        if (bucket.lastRefillNanos > now) {
            bucket.lastRefillNanos = now;
        }
    }

    private static long nanosUntilNextToken(Bucket bucket, BucketPolicy policy, long now) {
        long elapsed = elapsedNanos(now, bucket.lastRefillNanos);
        if (elapsed >= policy.refillPeriodNanos()) {
            return 0L;
        }
        return Math.subtractExact(policy.refillPeriodNanos(), elapsed);
    }

    private static long nanosUntilFull(Bucket bucket, BucketPolicy policy, long now) {
        if (bucket.tokens >= policy.capacity()) {
            return 0L;
        }

        int missing = Math.subtractExact(policy.capacity(), bucket.tokens);
        long periods = ceilDivide(missing, policy.refillTokens());
        long totalWait = saturatingMultiply(periods, policy.refillPeriodNanos());
        long elapsed = elapsedNanos(now, bucket.lastRefillNanos);
        if (elapsed >= totalWait) {
            return 0L;
        }
        return Math.subtractExact(totalWait, elapsed);
    }

    private static long ceilDivide(long value, long divisor) {
        return Math.addExact(1L, Math.subtractExact(value, 1L) / divisor);
    }

    private static long elapsedNanos(long now, long earlier) {
        if (now < earlier) {
            return 0L;
        }
        try {
            return Math.subtractExact(now, earlier);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static long saturatingMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static String requireAction(String action) {
        Objects.requireNonNull(action, "action");
        if (action.isBlank()) {
            throw new IllegalArgumentException("action must not be blank");
        }
        return action;
    }

    private static long requirePositiveNanos(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        final long nanos;
        try {
            nanos = duration.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " is too large", exception);
        }
        if (nanos <= 0L) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return nanos;
    }

    @FunctionalInterface
    public interface NanoClock {
        long nanoTime();
    }

    public static final class BucketPolicy {
        private final int capacity;
        private final int refillTokens;
        private final Duration refillPeriod;
        private final long refillPeriodNanos;

        public BucketPolicy(int capacity, int refillTokens, Duration refillPeriod) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be positive");
            }
            if (refillTokens <= 0 || refillTokens > capacity) {
                throw new IllegalArgumentException("refillTokens must be positive and not exceed capacity");
            }
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.refillPeriod = Objects.requireNonNull(refillPeriod, "refillPeriod");
            this.refillPeriodNanos = requirePositiveNanos(refillPeriod, "refillPeriod");
        }

        public int capacity() {
            return capacity;
        }

        public int refillTokens() {
            return refillTokens;
        }

        public Duration refillPeriod() {
            return refillPeriod;
        }

        private long refillPeriodNanos() {
            return refillPeriodNanos;
        }
    }

    public record Decision(boolean allowed, Reason reason, Duration retryAfter, int remainingTokens) {
        public Decision {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(retryAfter, "retryAfter");
            if (allowed != (reason == Reason.ALLOWED)) {
                throw new IllegalArgumentException("allowed must match reason");
            }
            if (retryAfter.isNegative()) {
                throw new IllegalArgumentException("retryAfter must not be negative");
            }
            if (remainingTokens < 0) {
                throw new IllegalArgumentException("remainingTokens must not be negative");
            }
        }

        private static Decision allowed(int remainingTokens) {
            return new Decision(true, Reason.ALLOWED, Duration.ZERO, remainingTokens);
        }

        private static Decision rejected(Reason reason, Duration retryAfter, int remainingTokens) {
            return new Decision(false, reason, retryAfter, remainingTokens);
        }
    }

    public enum Reason {
        ALLOWED,
        RATE_LIMITED,
        CACHE_FULL,
        UNKNOWN_ACTION
    }

    private record RequestKey(UUID playerId, String action) {
    }

    private static final class Bucket {
        private int tokens;
        private long lastRefillNanos;
        private long lastAccessNanos;

        private Bucket(int tokens, long now) {
            this.tokens = tokens;
            this.lastRefillNanos = now;
            this.lastAccessNanos = now;
        }
    }
}
