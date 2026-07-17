package com.enviouse.futureshops.client;

import com.enviouse.futureshops.network.packets.S2CAtmCollectCashResultPacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmCashClaimCollectionTrackerTest {
    private static final UUID PLAYER_ID = UUID.fromString(
            "50000000-0000-0000-0000-000000000002");
    private static final UUID CLAIM_ONE = UUID.fromString(
            "50000000-0000-0000-0000-000000000011");
    private static final UUID CLAIM_TWO = UUID.fromString(
            "50000000-0000-0000-0000-000000000012");

    @Test
    void timeoutRetryPreservesRequestAndExactClaimIds() {
        AtomicLong now = new AtomicLong();
        AtmCashClaimCollectionTracker tracker = tracker(now);
        AtmCashClaimCollectionTracker.PendingRequest first = tracker.begin(
                PLAYER_ID, List.of(CLAIM_ONE, CLAIM_TWO));
        now.set(11L);

        assertEquals(AtmCashClaimCollectionTracker.PendingState.RETRYABLE,
                tracker.state());
        AtmCashClaimCollectionTracker.PendingRequest retry = tracker.retry();
        assertEquals(first.requestId(), retry.requestId());
        assertEquals(first.claimIds(), retry.claimIds());
        assertEquals(2, retry.attempts());
    }

    @Test
    void timeoutRemainsCorrectAcrossNanoTimeWraparound() {
        AtomicLong now = new AtomicLong(Long.MAX_VALUE - 5L);
        AtmCashClaimCollectionTracker tracker = tracker(now);
        AtmCashClaimCollectionTracker.PendingRequest first = tracker.begin(
                PLAYER_ID, List.of(CLAIM_ONE));

        now.set(Long.MAX_VALUE);
        assertEquals(AtmCashClaimCollectionTracker.PendingState.AWAITING,
                tracker.state());
        now.set(Long.MIN_VALUE + 4L);
        assertEquals(AtmCashClaimCollectionTracker.PendingState.RETRYABLE,
                tracker.state());
        assertEquals(first.requestId(), tracker.retry().requestId());
    }

    @Test
    void trackerRejectsDuplicateAndMoreThanFourClaimIds() {
        AtmCashClaimCollectionTracker tracker = tracker(new AtomicLong());
        assertThrows(IllegalArgumentException.class,
                () -> tracker.begin(
                        PLAYER_ID, List.of(CLAIM_ONE, CLAIM_ONE)));
        assertThrows(IllegalArgumentException.class,
                () -> tracker.begin(PLAYER_ID, List.of(UUID.randomUUID(),
                        UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), UUID.randomUUID())));
    }

    @Test
    void correlatedRetryableAndTerminalResultsHaveStablePolicy() {
        AtmCashClaimCollectionTracker tracker = tracker(new AtomicLong());
        UUID requestId = tracker.begin(
                PLAYER_ID, List.of(CLAIM_ONE)).requestId();

        assertEquals(
                AtmCashClaimCollectionTracker.ResultDecision.MISMATCHED,
                tracker.evaluateResult(UUID.randomUUID(), false,
                        "DELIVERED"));
        assertEquals(
                AtmCashClaimCollectionTracker.ResultDecision.ACCEPT_RETRYABLE,
                tracker.evaluateResult(requestId, true, "RETRYABLE"));
        assertEquals(
                AtmCashClaimCollectionTracker.ResultDecision.DUPLICATE,
                tracker.evaluateResult(requestId, true, "RETRYABLE"));
        tracker.retry();
        assertEquals(
                AtmCashClaimCollectionTracker.ResultDecision.ACCEPT_RETRYABLE,
                tracker.evaluateResult(requestId, true, "RETRYABLE"));
        tracker.retry();
        assertEquals(
                AtmCashClaimCollectionTracker.ResultDecision.ACCEPT_TERMINAL,
                tracker.evaluateResult(requestId, false, "DELIVERED"));
        assertEquals(
                AtmCashClaimCollectionTracker.ResultDecision.DUPLICATE,
                tracker.evaluateResult(requestId, false, "DELIVERED"));
    }

    @Test
    void rateLimitDelayBlocksEarlyRetryAndPreservesExactRequest() {
        AtomicLong now = new AtomicLong(1_000_000L);
        AtmCashClaimCollectionTracker tracker = tracker(now);
        AtmCashClaimCollectionTracker.PendingRequest first = tracker.begin(
                PLAYER_ID, List.of(CLAIM_ONE, CLAIM_TWO));

        assertEquals(
                AtmCashClaimCollectionTracker.ResultDecision.ACCEPT_RETRYABLE,
                tracker.evaluateResult(first.requestId(), true, 2_000L,
                        "RATE_LIMITED,2000"));
        assertEquals(AtmCashClaimCollectionTracker.PendingState.AWAITING,
                tracker.state());
        assertThrows(IllegalStateException.class, tracker::retry);

        now.addAndGet(1_999_999_999L);
        assertEquals(AtmCashClaimCollectionTracker.PendingState.AWAITING,
                tracker.state());
        now.incrementAndGet();
        assertEquals(AtmCashClaimCollectionTracker.PendingState.RETRYABLE,
                tracker.state());
        AtmCashClaimCollectionTracker.PendingRequest retry = tracker.retry();
        assertEquals(first.requestId(), retry.requestId());
        assertEquals(first.claimIds(), retry.claimIds());
    }

    @Test
    void serverRetryDelayRemainsCorrectAcrossNanoTimeWraparound() {
        AtomicLong now = new AtomicLong(
                Long.MAX_VALUE - 500_000L);
        AtmCashClaimCollectionTracker tracker = tracker(now);
        AtmCashClaimCollectionTracker.PendingRequest first = tracker.begin(
                PLAYER_ID, List.of(CLAIM_ONE));
        assertEquals(
                AtmCashClaimCollectionTracker.ResultDecision.ACCEPT_RETRYABLE,
                tracker.evaluateResult(first.requestId(), true,
                        1L, "RATE_LIMITED,1"));

        now.set(Long.MIN_VALUE + 499_998L);
        assertEquals(AtmCashClaimCollectionTracker.PendingState.AWAITING,
                tracker.state());
        now.set(Long.MIN_VALUE + 499_999L);
        assertEquals(AtmCashClaimCollectionTracker.PendingState.RETRYABLE,
                tracker.state());
        assertEquals(first.requestId(), tracker.retry().requestId());
    }

    @Test
    void completedExactClaimSetCannotBeSubmittedAgain() {
        AtmCashClaimCollectionTracker tracker = tracker(new AtomicLong());
        AtmCashClaimCollectionTracker.PendingRequest request = tracker.begin(
                PLAYER_ID, List.of(CLAIM_ONE));
        tracker.evaluateResult(request.requestId(), false, "DELIVERED");

        assertTrue(tracker.beginIfFresh(
                PLAYER_ID, List.of(CLAIM_ONE)).isEmpty());
    }

    @Test
    void retryDelayIsBoundedAndRequiresRetryableResult() {
        AtmCashClaimCollectionTracker tracker = tracker(new AtomicLong());
        UUID requestId = tracker.begin(
                PLAYER_ID, List.of(CLAIM_ONE)).requestId();

        assertThrows(IllegalArgumentException.class, () ->
                tracker.evaluateResult(requestId, true,
                        S2CAtmCollectCashResultPacket
                                .MAX_RETRY_AFTER_MILLIS + 1L,
                        "RATE_LIMITED"));
        assertThrows(IllegalArgumentException.class, () ->
                tracker.evaluateResult(requestId, false, 1L,
                        "DELIVERED"));
    }

    private static AtmCashClaimCollectionTracker tracker(AtomicLong now) {
        return new AtmCashClaimCollectionTracker(
                now::get, 10L, 4);
    }
}
