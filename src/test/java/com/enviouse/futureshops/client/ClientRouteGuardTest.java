package com.enviouse.futureshops.client;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientRouteGuardTest {
    private static final long ACTIVE = 10L;
    private static final long RETENTION = 100L;

    @Test
    void matchingStorefrontResponseIsAcceptedOnce() {
        AtomicLong now = new AtomicLong();
        ClientRouteGuard guard = guard(now);
        Object origin = new Object();

        guard.recordStorefront(origin, 42L);

        assertEquals(ClientRouteGuard.ResponseDecision.ACCEPT,
                guard.evaluateStorefront(origin, 42L));
        assertEquals(ClientRouteGuard.ResponseDecision.REJECT,
                guard.evaluateStorefront(origin, 42L));
    }

    @Test
    void navigationAwayRejectsTheDelayedStorefrontResponse() {
        AtomicLong now = new AtomicLong();
        ClientRouteGuard guard = guard(now);

        guard.recordStorefront(new Object(), 42L);

        assertEquals(ClientRouteGuard.ResponseDecision.REJECT,
                guard.evaluateStorefront(new Object(), 42L));
    }

    @Test
    void aNewRequestSupersedesTheOlderTarget() {
        AtomicLong now = new AtomicLong();
        ClientRouteGuard guard = guard(now);
        Object origin = new Object();

        guard.recordStorefront(origin, 1L);
        guard.recordStorefront(origin, 2L);

        assertEquals(ClientRouteGuard.ResponseDecision.REJECT,
                guard.evaluateStorefront(origin, 1L));
        assertEquals(ClientRouteGuard.ResponseDecision.ACCEPT,
                guard.evaluateStorefront(origin, 2L));
    }

    @Test
    void expiredTrackedResponseIsRejectedUntilTheBoundedRetentionEnds() {
        AtomicLong now = new AtomicLong();
        ClientRouteGuard guard = guard(now);
        Object origin = new Object();

        guard.recordStorefront(origin, 42L);
        now.set(ACTIVE + 1L);
        assertEquals(ClientRouteGuard.ResponseDecision.REJECT,
                guard.evaluateStorefront(origin, 42L));

        now.set(ACTIVE + RETENTION + 2L);
        assertEquals(ClientRouteGuard.ResponseDecision.UNTRACKED,
                guard.evaluateStorefront(origin, 42L));
    }

    @Test
    void activeAndRetentionWindowsSurviveNanoTimeWraparound() {
        AtomicLong now = new AtomicLong(Long.MAX_VALUE - 5L);
        ClientRouteGuard activeGuard = guard(now);
        Object origin = new Object();
        activeGuard.recordAtm(origin);

        now.set(Long.MIN_VALUE + 4L);
        assertEquals(ClientRouteGuard.ResponseDecision.ACCEPT,
                activeGuard.evaluateAtm(origin));

        now.set(Long.MAX_VALUE - 5L);
        ClientRouteGuard retentionGuard = guard(now);
        retentionGuard.recordStorefront(origin, 42L);
        now.set(Long.MIN_VALUE + 5L);
        assertEquals(ClientRouteGuard.ResponseDecision.REJECT,
                retentionGuard.evaluateStorefront(origin, 42L));
        now.set(Long.MIN_VALUE + 106L);
        assertEquals(ClientRouteGuard.ResponseDecision.UNTRACKED,
                retentionGuard.evaluateStorefront(origin, 42L));
    }

    @Test
    void cancelledBalanceResponseCannotOpenTheAtm() {
        AtomicLong now = new AtomicLong();
        ClientRouteGuard guard = guard(now);
        Object origin = new Object();

        guard.recordAtm(origin);
        guard.cancelOrigin(origin);

        ClientRouteGuard.ResponseDecision decision = guard.evaluateAtm(origin);
        assertEquals(ClientRouteGuard.ResponseDecision.UNTRACKED, decision);
        assertFalse(ClientRouteGuard.allowsAtmOpen(decision, false, true));
    }

    @Test
    void guardedBalanceResponseMayReplaceItsOriginScreen() {
        assertTrue(ClientRouteGuard.allowsAtmOpen(
                ClientRouteGuard.ResponseDecision.ACCEPT, false, false));
    }

    @Test
    void untrackedCommandResponseOnlyOpensWithoutACompetingScreen() {
        assertTrue(ClientRouteGuard.allowsAtmOpen(
                ClientRouteGuard.ResponseDecision.UNTRACKED, true, true));
        assertFalse(ClientRouteGuard.allowsAtmOpen(
                ClientRouteGuard.ResponseDecision.UNTRACKED, true, false));
        assertFalse(ClientRouteGuard.allowsAtmOpen(
                ClientRouteGuard.ResponseDecision.UNTRACKED, false, true));
    }

    @Test
    void rejectedAtmResponseNeverUsesServerOpenIntentAsABypass() {
        assertFalse(ClientRouteGuard.allowsAtmOpen(
                ClientRouteGuard.ResponseDecision.REJECT, true, true));
    }

    @Test
    void completedBalanceRouteDoesNotPoisonALaterCommandOpen() {
        AtomicLong now = new AtomicLong();
        ClientRouteGuard guard = guard(now);
        Object origin = new Object();

        guard.recordAtm(origin);
        assertEquals(ClientRouteGuard.ResponseDecision.ACCEPT,
                guard.evaluateAtm(origin));

        ClientRouteGuard.ResponseDecision commandDecision = guard.evaluateAtm(null);
        assertEquals(ClientRouteGuard.ResponseDecision.UNTRACKED, commandDecision);
        assertTrue(ClientRouteGuard.allowsAtmOpen(commandDecision, true, true));
    }

    @Test
    void clearingRoutesDropsAtmAndStorefrontTracking() {
        AtomicLong now = new AtomicLong();
        ClientRouteGuard guard = guard(now);
        Object origin = new Object();

        guard.recordStorefront(origin, 42L);
        guard.recordAtm(origin);
        guard.clearTrackedRoutes();

        assertEquals(ClientRouteGuard.ResponseDecision.UNTRACKED,
                guard.evaluateStorefront(origin, 42L));
        assertEquals(ClientRouteGuard.ResponseDecision.UNTRACKED,
                guard.evaluateAtm(origin));
    }

    private static ClientRouteGuard guard(AtomicLong now) {
        return new ClientRouteGuard(now::get, ACTIVE, RETENTION, 4);
    }
}
