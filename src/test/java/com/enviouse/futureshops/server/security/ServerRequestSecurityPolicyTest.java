package com.enviouse.futureshops.server.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerRequestSecurityPolicyTest {
    private static final UUID PLAYER = UUID.fromString(
            "61000000-0000-0000-0000-000000000001");

    @Test
    void defaultsCoverEveryAtmActionWithBoundedConservativeLimits() {
        ServerRequestSecuritySettings defaults =
                ServerRequestSecuritySettings.defaults();
        assertEquals(EnumSet.allOf(ServerRequestAction.class),
                defaults.actionLimits().keySet());
        assertEquals(8_192, defaults.trackedKeyCap());
        assertEquals(Duration.ofMinutes(10L), defaults.idleRetention());

        assertLimit(defaults, ServerRequestAction.ATM_DATA,
                4, 1, Duration.ofSeconds(1L));
        assertLimit(defaults, ServerRequestAction.ATM_WITHDRAWAL,
                2, 1, Duration.ofSeconds(2L));
        assertLimit(defaults, ServerRequestAction.ATM_CASH_COLLECTION,
                2, 1, Duration.ofSeconds(2L));
        assertLimit(defaults, ServerRequestAction.ATM_DEPOSIT,
                2, 1, Duration.ofSeconds(2L));
        assertLimit(defaults, ServerRequestAction.PAY,
                4, 1, Duration.ofSeconds(1L));
        assertLimit(defaults, ServerRequestAction.SERVER_SHOP_OFFER,
                4, 1, Duration.ofSeconds(1L));
        assertLimit(defaults,
                ServerRequestAction.SERVER_SHOP_OFFER_ADMIN,
                4, 1, Duration.ofSeconds(1L));
        assertEquals("server_shop.offer",
                ServerRequestAction.SERVER_SHOP_OFFER.code());
        assertEquals("server_shop.offer_admin",
                ServerRequestAction.SERVER_SHOP_OFFER_ADMIN.code());
    }

    @Test
    void defaultActionBucketsRemainIndependent() {
        MutableClock clock = new MutableClock();
        ServerRequestRateLimiter limiter =
                ServerRequestSecurityPolicy.createLimiter(
                        clock, ServerRequestSecuritySettings.defaults());

        assertTrue(limiter.tryAcquire(PLAYER,
                ServerRequestAction.ATM_WITHDRAWAL.code()).allowed());
        assertTrue(limiter.tryAcquire(PLAYER,
                ServerRequestAction.ATM_WITHDRAWAL.code()).allowed());
        assertFalse(limiter.tryAcquire(PLAYER,
                ServerRequestAction.ATM_WITHDRAWAL.code()).allowed());

        assertTrue(limiter.tryAcquire(PLAYER,
                ServerRequestAction.ATM_DATA.code()).allowed());
        assertTrue(limiter.tryAcquire(PLAYER,
                ServerRequestAction.ATM_CASH_COLLECTION.code()).allowed());
        assertTrue(limiter.tryAcquire(PLAYER,
                ServerRequestAction.ATM_DEPOSIT.code()).allowed());
        assertTrue(limiter.tryAcquire(PLAYER,
                ServerRequestAction.PAY.code()).allowed());
        assertTrue(limiter.tryAcquire(PLAYER,
                ServerRequestAction.SERVER_SHOP_OFFER.code()).allowed());
        assertTrue(limiter.tryAcquire(PLAYER,
                ServerRequestAction.SERVER_SHOP_OFFER_ADMIN.code())
                .allowed());

        clock.now = Duration.ofSeconds(2L).toNanos();
        assertTrue(limiter.tryAcquire(PLAYER,
                ServerRequestAction.ATM_WITHDRAWAL.code()).allowed());
    }

    @Test
    void rejectsRefillTokensAboveConfiguredCapacity() {
        assertThrows(IllegalArgumentException.class,
                () -> new ServerRequestSecuritySettings.ActionLimit(
                        2, 3, Duration.ofSeconds(1L)));
        assertThrows(IllegalArgumentException.class,
                () -> new ServerRequestSecuritySettings.ActionLimit(
                        2, 0, Duration.ofSeconds(1L)));
        assertThrows(IllegalArgumentException.class,
                () -> new ServerRequestSecuritySettings.ActionLimit(
                        2, 1, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new ServerRequestSecuritySettings(
                        0, Duration.ofSeconds(1L),
                        limit(1), limit(1), limit(1), limit(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new ServerRequestSecuritySettings(
                        1, Duration.ZERO,
                        limit(1), limit(1), limit(1), limit(1)));
    }

    @Test
    void configuredLimitsDriveCapacityRefillAndTrackedKeyCap() {
        MutableClock clock = new MutableClock();
        ServerRequestSecuritySettings settings =
                new ServerRequestSecuritySettings(
                        1, Duration.ofNanos(100L),
                        limit(3), limit(1), limit(2), limit(1));
        ServerRequestRateLimiter limiter =
                ServerRequestSecurityPolicy.createLimiter(clock, settings);

        assertTrue(limiter.tryAcquire(PLAYER,
                ServerRequestAction.ATM_WITHDRAWAL.code()).allowed());
        assertEquals(ServerRequestRateLimiter.Reason.RATE_LIMITED,
                limiter.tryAcquire(PLAYER,
                        ServerRequestAction.ATM_WITHDRAWAL.code()).reason());
        assertEquals(ServerRequestRateLimiter.Reason.CACHE_FULL,
                limiter.tryAcquire(UUID.fromString(
                                "61000000-0000-0000-0000-000000000002"),
                        ServerRequestAction.ATM_WITHDRAWAL.code()).reason());

        clock.now = Duration.ofSeconds(1L).toNanos();
        assertTrue(limiter.tryAcquire(PLAYER,
                ServerRequestAction.ATM_WITHDRAWAL.code()).allowed());
    }

    private static void assertLimit(
            ServerRequestSecuritySettings settings,
            ServerRequestAction action,
            int capacity,
            int refillTokens,
            Duration refillPeriod
    ) {
        ServerRequestSecuritySettings.ActionLimit limit =
                settings.actionLimits().get(action);
        assertEquals(capacity, limit.capacity());
        assertEquals(refillTokens, limit.refillTokens());
        assertEquals(refillPeriod, limit.refillPeriod());
    }

    private static ServerRequestSecuritySettings.ActionLimit limit(
            int capacity
    ) {
        return new ServerRequestSecuritySettings.ActionLimit(
                capacity, 1, Duration.ofSeconds(1L));
    }

    private static final class MutableClock
            implements ServerRequestRateLimiter.NanoClock {
        private long now;

        @Override
        public long nanoTime() {
            return now;
        }
    }
}
