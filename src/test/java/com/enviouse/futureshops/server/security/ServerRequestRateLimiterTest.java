package com.enviouse.futureshops.server.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerRequestRateLimiterTest {
    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void rejectsBurstBeyondCapacityWithDeterministicRetry() {
        MutableClock clock = new MutableClock(0L);
        ServerRequestRateLimiter limiter = limiter(
                Map.of("atm", policy(3, 1, 10L)),
                10,
                100L,
                clock
        );

        assertAllowed(limiter.tryAcquire(PLAYER_ONE, "atm"), 2);
        assertAllowed(limiter.tryAcquire(PLAYER_ONE, "atm"), 1);
        assertAllowed(limiter.tryAcquire(PLAYER_ONE, "atm"), 0);

        ServerRequestRateLimiter.Decision rejected = limiter.tryAcquire(PLAYER_ONE, "atm");
        assertFalse(rejected.allowed());
        assertEquals(ServerRequestRateLimiter.Reason.RATE_LIMITED, rejected.reason());
        assertEquals(Duration.ofNanos(10L), rejected.retryAfter());
        assertEquals(0, rejected.remainingTokens());
    }

    @Test
    void refillsInDiscretePeriodsAndCapsAccumulatedTokens() {
        MutableClock clock = new MutableClock(0L);
        ServerRequestRateLimiter limiter = limiter(
                Map.of("atm", policy(3, 1, 10L)),
                10,
                100L,
                clock
        );

        assertAllowed(limiter.tryAcquire(PLAYER_ONE, "atm"), 2);
        assertAllowed(limiter.tryAcquire(PLAYER_ONE, "atm"), 1);
        assertAllowed(limiter.tryAcquire(PLAYER_ONE, "atm"), 0);

        clock.set(9L);
        assertEquals(Duration.ofNanos(1L), limiter.tryAcquire(PLAYER_ONE, "atm").retryAfter());

        clock.set(10L);
        assertAllowed(limiter.tryAcquire(PLAYER_ONE, "atm"), 0);

        clock.set(40L);
        assertAllowed(limiter.tryAcquire(PLAYER_ONE, "atm"), 2);
    }

    @Test
    void keepsPlayerAndActionBucketsIndependent() {
        MutableClock clock = new MutableClock(0L);
        Map<String, ServerRequestRateLimiter.BucketPolicy> policies = new LinkedHashMap<>();
        policies.put("atm", policy(1, 1, 10L));
        policies.put("shop", policy(2, 1, 10L));
        ServerRequestRateLimiter limiter = limiter(policies, 10, 100L, clock);

        assertAllowed(limiter.tryAcquire(PLAYER_ONE, "atm"), 0);
        assertEquals(
                ServerRequestRateLimiter.Reason.RATE_LIMITED,
                limiter.tryAcquire(PLAYER_ONE, "atm").reason()
        );
        assertAllowed(limiter.tryAcquire(PLAYER_ONE, "shop"), 1);
        assertAllowed(limiter.tryAcquire(PLAYER_TWO, "atm"), 0);
        assertEquals(3, limiter.keyCount());
    }

    @Test
    void clockRollbackCannotRefillOrShortenRetry() {
        MutableClock clock = new MutableClock(100L);
        ServerRequestRateLimiter limiter = limiter(
                Map.of("atm", policy(1, 1, 10L)),
                10,
                100L,
                clock
        );

        assertAllowed(limiter.tryAcquire(PLAYER_ONE, "atm"), 0);

        clock.set(105L);
        assertEquals(Duration.ofNanos(5L), limiter.tryAcquire(PLAYER_ONE, "atm").retryAfter());

        clock.set(90L);
        assertEquals(Duration.ofNanos(5L), limiter.tryAcquire(PLAYER_ONE, "atm").retryAfter());

        clock.set(109L);
        assertEquals(Duration.ofNanos(1L), limiter.tryAcquire(PLAYER_ONE, "atm").retryAfter());

        clock.set(110L);
        assertAllowed(limiter.tryAcquire(PLAYER_ONE, "atm"), 0);
    }

    @Test
    void fullCacheFailsClosedWithoutResettingActiveBucket() {
        MutableClock clock = new MutableClock(0L);
        ServerRequestRateLimiter limiter = limiter(
                Map.of("atm", policy(2, 1, 10L)),
                1,
                100L,
                clock
        );

        assertAllowed(limiter.tryAcquire(PLAYER_ONE, "atm"), 1);

        ServerRequestRateLimiter.Decision cacheFull = limiter.tryAcquire(PLAYER_TWO, "atm");
        assertEquals(ServerRequestRateLimiter.Reason.CACHE_FULL, cacheFull.reason());
        assertEquals(Duration.ofNanos(100L), cacheFull.retryAfter());
        assertEquals(1, limiter.keyCount());

        assertAllowed(limiter.tryAcquire(PLAYER_ONE, "atm"), 0);
        assertEquals(
                ServerRequestRateLimiter.Reason.RATE_LIMITED,
                limiter.tryAcquire(PLAYER_ONE, "atm").reason()
        );
    }

    @Test
    void reclaimsOnlyFullyRefilledIdleEntryAtDeterministicBoundary() {
        MutableClock clock = new MutableClock(0L);
        ServerRequestRateLimiter limiter = limiter(
                Map.of("atm", policy(2, 1, 10L)),
                1,
                100L,
                clock
        );

        assertAllowed(limiter.tryAcquire(PLAYER_ONE, "atm"), 1);

        clock.set(99L);
        ServerRequestRateLimiter.Decision early = limiter.tryAcquire(PLAYER_TWO, "atm");
        assertEquals(ServerRequestRateLimiter.Reason.CACHE_FULL, early.reason());
        assertEquals(Duration.ofNanos(1L), early.retryAfter());

        clock.set(100L);
        assertAllowed(limiter.tryAcquire(PLAYER_TWO, "atm"), 1);
        assertEquals(1, limiter.keyCount());
    }

    @Test
    void unknownActionDoesNotConsumeCacheCapacity() {
        MutableClock clock = new MutableClock(0L);
        ServerRequestRateLimiter limiter = limiter(
                Map.of("atm", policy(1, 1, 10L)),
                1,
                100L,
                clock
        );

        ServerRequestRateLimiter.Decision decision = limiter.tryAcquire(PLAYER_ONE, "missing");
        assertEquals(ServerRequestRateLimiter.Reason.UNKNOWN_ACTION, decision.reason());
        assertEquals(Duration.ZERO, decision.retryAfter());
        assertEquals(0, limiter.keyCount());
    }

    @Test
    void hugeElapsedTimeSaturatesWithoutArithmeticWraparound() {
        MutableClock clock = new MutableClock(Long.MIN_VALUE);
        ServerRequestRateLimiter limiter = limiter(
                Map.of("atm", policy(2, 2, Long.MAX_VALUE)),
                2,
                Long.MAX_VALUE,
                clock
        );

        assertAllowed(limiter.tryAcquire(PLAYER_ONE, "atm"), 1);
        assertAllowed(limiter.tryAcquire(PLAYER_ONE, "atm"), 0);

        clock.set(Long.MAX_VALUE);
        assertAllowed(limiter.tryAcquire(PLAYER_ONE, "atm"), 1);
    }

    @Test
    void removePlayerAndClearReleaseOnlyInstanceOwnedState() {
        MutableClock clock = new MutableClock(0L);
        ServerRequestRateLimiter limiter = limiter(
                Map.of("atm", policy(1, 1, 10L), "shop", policy(1, 1, 10L)),
                10,
                100L,
                clock
        );

        assertAllowed(limiter.tryAcquire(PLAYER_ONE, "atm"), 0);
        assertAllowed(limiter.tryAcquire(PLAYER_ONE, "shop"), 0);
        assertAllowed(limiter.tryAcquire(PLAYER_TWO, "atm"), 0);

        assertEquals(2, limiter.removePlayer(PLAYER_ONE));
        assertEquals(1, limiter.keyCount());
        limiter.clear();
        assertEquals(0, limiter.keyCount());
    }

    @Test
    void rejectsInvalidConfigurationBounds() {
        MutableClock clock = new MutableClock(0L);
        ServerRequestRateLimiter.BucketPolicy valid = policy(1, 1, 10L);

        assertThrows(IllegalArgumentException.class,
                () -> limiter(Map.of("atm", valid), 0, 100L, clock));
        assertThrows(IllegalArgumentException.class,
                () -> new ServerRequestRateLimiter(Map.of(), 1, Duration.ofNanos(1L), clock));
        assertThrows(IllegalArgumentException.class,
                () -> new ServerRequestRateLimiter(Map.of(" ", valid), 1, Duration.ofNanos(1L), clock));
        assertThrows(IllegalArgumentException.class,
                () -> new ServerRequestRateLimiter(Map.of("atm", valid), 1, Duration.ZERO, clock));
        assertThrows(IllegalArgumentException.class,
                () -> new ServerRequestRateLimiter(
                        Map.of("atm", valid),
                        1,
                        Duration.ofSeconds(Long.MAX_VALUE),
                        clock
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new ServerRequestRateLimiter.BucketPolicy(0, 1, Duration.ofNanos(1L)));
        assertThrows(IllegalArgumentException.class,
                () -> new ServerRequestRateLimiter.BucketPolicy(1, 0, Duration.ofNanos(1L)));
        assertThrows(IllegalArgumentException.class,
                () -> new ServerRequestRateLimiter.BucketPolicy(1, 2, Duration.ofNanos(1L)));
        assertThrows(IllegalArgumentException.class,
                () -> new ServerRequestRateLimiter.BucketPolicy(1, 1, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new ServerRequestRateLimiter.BucketPolicy(
                        1,
                        1,
                        Duration.ofSeconds(Long.MAX_VALUE)
                ));
    }

    private static ServerRequestRateLimiter limiter(
            Map<String, ServerRequestRateLimiter.BucketPolicy> policies,
            int maxKeys,
            long idleRetentionNanos,
            MutableClock clock
    ) {
        return new ServerRequestRateLimiter(
                policies,
                maxKeys,
                Duration.ofNanos(idleRetentionNanos),
                clock
        );
    }

    private static ServerRequestRateLimiter.BucketPolicy policy(
            int capacity,
            int refillTokens,
            long refillPeriodNanos
    ) {
        return new ServerRequestRateLimiter.BucketPolicy(
                capacity,
                refillTokens,
                Duration.ofNanos(refillPeriodNanos)
        );
    }

    private static void assertAllowed(ServerRequestRateLimiter.Decision decision, int remainingTokens) {
        assertTrue(decision.allowed());
        assertEquals(ServerRequestRateLimiter.Reason.ALLOWED, decision.reason());
        assertEquals(Duration.ZERO, decision.retryAfter());
        assertEquals(remainingTokens, decision.remainingTokens());
    }

    private static final class MutableClock implements ServerRequestRateLimiter.NanoClock {
        private long now;

        private MutableClock(long now) {
            this.now = now;
        }

        private void set(long now) {
            this.now = now;
        }

        @Override
        public long nanoTime() {
            return now;
        }
    }
}
