package com.enviouse.futureshops.client;

import com.enviouse.futureshops.network.packets.C2SAtmDepositPacket;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtmDepositTrackerTest {
    private static final UUID REQUEST_ID = UUID.fromString(
            "53000000-0000-0000-0000-000000000001");
    private static final String SIGNATURE = "a".repeat(64);

    @Test
    void retryPreservesUuidSourceAndExactAmount() {
        AtomicLong now = new AtomicLong();
        AtmDepositTracker tracker = tracker(now);
        AtmDepositTracker.PendingRequest first = tracker.begin(
                SIGNATURE,
                C2SAtmDepositPacket.Source.MAIN_HAND,
                OptionalLong.of(2_500L));
        now.set(11L);

        AtmDepositTracker.PendingRequest retry = tracker.retry();

        assertEquals(first.requestId(), retry.requestId());
        assertEquals(first.source(), retry.source());
        assertEquals(SIGNATURE, retry.currencySignature());
        assertEquals(first.requestedMinorUnits(),
                retry.requestedMinorUnits());
        assertEquals(2, retry.attempts());
    }

    @Test
    void malformedCatalogSignatureCannotBecomePending() {
        AtmDepositTracker tracker = tracker(new AtomicLong());

        assertThrows(IllegalArgumentException.class, () -> tracker.begin(
                "stale", C2SAtmDepositPacket.Source.INVENTORY,
                OptionalLong.empty()));
    }

    @Test
    void timeoutRemainsCorrectAcrossNanoTimeWraparound() {
        AtomicLong now = new AtomicLong(Long.MAX_VALUE - 5L);
        AtmDepositTracker tracker = tracker(now);
        AtmDepositTracker.PendingRequest first = tracker.begin(
                SIGNATURE,
                C2SAtmDepositPacket.Source.MAIN_HAND,
                OptionalLong.of(100L));

        now.set(Long.MAX_VALUE);
        assertEquals(AtmDepositTracker.PendingState.AWAITING,
                tracker.state());
        now.set(Long.MIN_VALUE + 4L);
        assertEquals(AtmDepositTracker.PendingState.RETRYABLE,
                tracker.state());
        assertEquals(first.requestId(), tracker.retry().requestId());
    }

    @Test
    void depositAllPayloadRemainsEmptyAcrossRetry() {
        AtomicLong now = new AtomicLong();
        AtmDepositTracker tracker = tracker(now);
        AtmDepositTracker.PendingRequest first = tracker.begin(
                SIGNATURE,
                C2SAtmDepositPacket.Source.OFF_HAND,
                OptionalLong.empty());
        tracker.evaluateResult(first.requestId(), true, 0L,
                "ESCROW_UNAVAILABLE");

        AtmDepositTracker.PendingRequest retry = tracker.retry();

        assertEquals(OptionalLong.empty(), retry.requestedMinorUnits());
        assertEquals(C2SAtmDepositPacket.Source.OFF_HAND, retry.source());
    }

    @Test
    void rateLimitDelayBlocksEarlyRetry() {
        AtomicLong now = new AtomicLong(100L);
        AtmDepositTracker tracker = tracker(now);
        AtmDepositTracker.PendingRequest request = tracker.begin(
                SIGNATURE,
                C2SAtmDepositPacket.Source.INVENTORY,
                OptionalLong.empty());

        assertEquals(AtmDepositTracker.ResultDecision.ACCEPT_RETRYABLE,
                tracker.evaluateResult(request.requestId(), true,
                        2_000L, "RATE_LIMITED,2000"));
        assertEquals(AtmDepositTracker.PendingState.AWAITING,
                tracker.state());
        assertThrows(IllegalStateException.class, tracker::retry);
        now.addAndGet(2_000_000_000L);
        assertEquals(AtmDepositTracker.PendingState.RETRYABLE,
                tracker.state());
    }

    @Test
    void serverRetryDelayRemainsCorrectAcrossNanoTimeWraparound() {
        AtomicLong now = new AtomicLong(
                Long.MAX_VALUE - 500_000L);
        AtmDepositTracker tracker = tracker(now);
        AtmDepositTracker.PendingRequest request = tracker.begin(
                SIGNATURE,
                C2SAtmDepositPacket.Source.INVENTORY,
                OptionalLong.empty());
        assertEquals(AtmDepositTracker.ResultDecision.ACCEPT_RETRYABLE,
                tracker.evaluateResult(request.requestId(), true,
                        1L, "RATE_LIMITED,1"));

        now.set(Long.MIN_VALUE + 499_998L);
        assertEquals(AtmDepositTracker.PendingState.AWAITING,
                tracker.state());
        now.set(Long.MIN_VALUE + 499_999L);
        assertEquals(AtmDepositTracker.PendingState.RETRYABLE,
                tracker.state());
        assertEquals(request.requestId(), tracker.retry().requestId());
    }

    @Test
    void duplicateAndMismatchedResultsCannotClearPendingRequest() {
        AtmDepositTracker tracker = tracker(new AtomicLong());
        AtmDepositTracker.PendingRequest request = tracker.begin(
                SIGNATURE,
                C2SAtmDepositPacket.Source.INVENTORY,
                OptionalLong.of(100L));

        assertEquals(AtmDepositTracker.ResultDecision.MISMATCHED,
                tracker.evaluateResult(UUID.randomUUID(), false, 0L,
                        "SUCCESS"));
        assertEquals(AtmDepositTracker.ResultDecision.ACCEPT_RETRYABLE,
                tracker.evaluateResult(request.requestId(), true, 0L,
                        "RECOVERY_REQUIRED"));
        assertEquals(AtmDepositTracker.ResultDecision.DUPLICATE,
                tracker.evaluateResult(request.requestId(), true, 0L,
                        "RECOVERY_REQUIRED"));
        tracker.retry();
        assertEquals(AtmDepositTracker.ResultDecision.ACCEPT_TERMINAL,
                tracker.evaluateResult(request.requestId(), false, 0L,
                        "SUCCESS"));
        assertEquals(AtmDepositTracker.ResultDecision.DUPLICATE,
                tracker.evaluateResult(request.requestId(), false, 0L,
                        "SUCCESS"));
    }

    private static AtmDepositTracker tracker(AtomicLong now) {
        return new AtmDepositTracker(
                () -> REQUEST_ID, now::get, 10L, 4);
    }
}
