package com.enviouse.futureshops.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmWithdrawalTrackerTest {
    private static final String SIGNATURE = "a".repeat(64);
    private static final UUID FIRST = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString(
            "10000000-0000-0000-0000-000000000002");
    private static final UUID THIRD = UUID.fromString(
            "10000000-0000-0000-0000-000000000003");

    @Test
    void timeoutAndRetryPreserveIdentityAndPayload() {
        AtomicLong now = new AtomicLong();
        AtmWithdrawalTracker tracker = tracker(now, FIRST);
        List<Integer> counts = List.of(1, 0, 3);

        AtmWithdrawalTracker.PendingRequest first = tracker.begin(
                SIGNATURE, counts, 325L);
        now.set(11L);

        assertEquals(AtmWithdrawalTracker.PendingState.RETRYABLE,
                tracker.state());
        AtmWithdrawalTracker.PendingRequest retry = tracker.retry();
        assertEquals(first.requestId(), retry.requestId());
        assertEquals(first.currencySignature(), retry.currencySignature());
        assertEquals(first.denominationCounts(), retry.denominationCounts());
        assertEquals(first.amountMinor(), retry.amountMinor());
        assertEquals(2, retry.attempts());
        assertEquals(11L, retry.lastSentAtNanos());
        assertEquals(AtmWithdrawalTracker.PendingState.AWAITING,
                tracker.state());
    }

    @Test
    void timeoutRemainsCorrectAcrossNanoTimeWraparound() {
        AtomicLong now = new AtomicLong(Long.MAX_VALUE - 5L);
        AtmWithdrawalTracker tracker = tracker(now, FIRST);
        AtmWithdrawalTracker.PendingRequest original = tracker.begin(
                SIGNATURE, List.of(1), 100L);

        now.set(Long.MAX_VALUE);
        assertEquals(AtmWithdrawalTracker.PendingState.AWAITING,
                tracker.state());
        now.set(Long.MIN_VALUE + 4L);
        assertEquals(AtmWithdrawalTracker.PendingState.RETRYABLE,
                tracker.state());
        assertEquals(original.requestId(), tracker.retry().requestId());
    }

    @Test
    void requestPayloadIsDefensivelyCopied() {
        AtomicLong now = new AtomicLong();
        AtmWithdrawalTracker tracker = tracker(now, FIRST);
        java.util.ArrayList<Integer> counts = new java.util.ArrayList<>(
                List.of(1, 2));

        AtmWithdrawalTracker.PendingRequest pending = tracker.begin(
                SIGNATURE, counts, 300L);
        counts.set(0, 99);

        assertEquals(List.of(1, 2), pending.denominationCounts());
        assertThrows(UnsupportedOperationException.class,
                () -> pending.denominationCounts().set(0, 4));
    }

    @Test
    void retryBeforeTimeoutIsRejected() {
        AtomicLong now = new AtomicLong();
        AtmWithdrawalTracker tracker = tracker(now, FIRST);
        tracker.begin(SIGNATURE, List.of(1), 100L);

        assertThrows(IllegalStateException.class, tracker::retry);
    }

    @Test
    void retryableResultKeepsTheSameRequest() {
        AtomicLong now = new AtomicLong();
        AtmWithdrawalTracker tracker = tracker(now, FIRST);
        AtmWithdrawalTracker.PendingRequest pending = tracker.begin(
                SIGNATURE, List.of(1), 100L);

        assertEquals(AtmWithdrawalTracker.ResultDecision.ACCEPT_RETRYABLE,
                tracker.evaluateResult(FIRST, true, "ESCROW_UNAVAILABLE"));
        assertEquals(FIRST, tracker.pending().orElseThrow().requestId());
        assertEquals(AtmWithdrawalTracker.PendingState.RETRYABLE,
                tracker.state());
        assertEquals(pending.requestId(), tracker.retry().requestId());
    }

    @Test
    void rateLimitDelayPreservesPayloadAndBlocksEarlyRetry() {
        AtomicLong now = new AtomicLong();
        AtmWithdrawalTracker tracker = tracker(now, FIRST);
        AtmWithdrawalTracker.PendingRequest original = tracker.begin(
                SIGNATURE, List.of(1), 100L);

        assertEquals(AtmWithdrawalTracker.ResultDecision.ACCEPT_RETRYABLE,
                tracker.evaluateResult(FIRST, SIGNATURE, true,
                        2L, "RATE_LIMITED,2"));
        assertEquals(AtmWithdrawalTracker.PendingState.AWAITING,
                tracker.state());
        assertThrows(IllegalStateException.class, tracker::retry);
        now.set(1_999_999L);
        assertEquals(AtmWithdrawalTracker.PendingState.AWAITING,
                tracker.state());
        now.set(2_000_000L);
        assertEquals(AtmWithdrawalTracker.PendingState.RETRYABLE,
                tracker.state());

        AtmWithdrawalTracker.PendingRequest retry = tracker.retry();
        assertEquals(original.requestId(), retry.requestId());
        assertEquals(original.currencySignature(),
                retry.currencySignature());
        assertEquals(original.denominationCounts(),
                retry.denominationCounts());
        assertEquals(original.amountMinor(), retry.amountMinor());

        assertEquals(AtmWithdrawalTracker.ResultDecision.ACCEPT_RETRYABLE,
                tracker.evaluateResult(FIRST, SIGNATURE, true,
                        1L, "RATE_LIMITED,1"));
        assertEquals(AtmWithdrawalTracker.PendingState.AWAITING,
                tracker.state());
    }

    @Test
    void serverRetryDelayRemainsCorrectAcrossNanoTimeWraparound() {
        AtomicLong now = new AtomicLong(
                Long.MAX_VALUE - 500_000L);
        AtmWithdrawalTracker tracker = tracker(now, FIRST);
        tracker.begin(SIGNATURE, List.of(1), 100L);
        assertEquals(AtmWithdrawalTracker.ResultDecision.ACCEPT_RETRYABLE,
                tracker.evaluateResult(FIRST, SIGNATURE, true,
                        1L, "RATE_LIMITED,1"));

        now.set(Long.MIN_VALUE + 499_998L);
        assertEquals(AtmWithdrawalTracker.PendingState.AWAITING,
                tracker.state());
        now.set(Long.MIN_VALUE + 499_999L);
        assertEquals(AtmWithdrawalTracker.PendingState.RETRYABLE,
                tracker.state());
        assertEquals(FIRST, tracker.retry().requestId());
    }

    @Test
    void retryDelayValidationIsBoundedAndRequiresRetryableResult() {
        AtomicLong now = new AtomicLong();
        AtmWithdrawalTracker tracker = tracker(now, FIRST);
        tracker.begin(SIGNATURE, List.of(1), 100L);

        assertThrows(IllegalArgumentException.class,
                () -> tracker.evaluateResult(FIRST, SIGNATURE, false,
                        1L, "CLAIMED"));
        assertThrows(IllegalArgumentException.class,
                () -> tracker.evaluateResult(FIRST, SIGNATURE, true,
                        AtmWithdrawalTracker.MAX_RETRY_AFTER_MILLIS + 1L,
                        "RATE_LIMITED"));
    }

    @Test
    void duplicateAndMismatchedResultsDoNotMutatePendingState() {
        AtomicLong now = new AtomicLong();
        AtmWithdrawalTracker tracker = tracker(now, FIRST);
        tracker.begin(SIGNATURE, List.of(1), 100L);

        assertEquals(AtmWithdrawalTracker.ResultDecision.MISMATCHED,
                tracker.evaluateResult(SECOND, false, "DELIVERED"));
        assertEquals(FIRST, tracker.pending().orElseThrow().requestId());
        assertEquals(AtmWithdrawalTracker.ResultDecision.ACCEPT_RETRYABLE,
                tracker.evaluateResult(FIRST, true, "RECOVERY_PENDING"));
        assertEquals(AtmWithdrawalTracker.ResultDecision.DUPLICATE,
                tracker.evaluateResult(FIRST, true, "RECOVERY_PENDING"));
        assertTrue(tracker.pending().isPresent());
    }

    @Test
    void mismatchedCurrencySignatureCannotCompleteTheRequest() {
        AtomicLong now = new AtomicLong();
        AtmWithdrawalTracker tracker = tracker(now, FIRST);
        tracker.begin(SIGNATURE, List.of(1), 100L);

        assertEquals(AtmWithdrawalTracker.ResultDecision.MISMATCHED,
                tracker.evaluateResult(FIRST, "b".repeat(64),
                        false, "CLAIMED"));
        assertEquals(FIRST, tracker.pending().orElseThrow().requestId());
        assertEquals(AtmWithdrawalTracker.ResultDecision.ACCEPT_TERMINAL,
                tracker.evaluateResult(FIRST, SIGNATURE,
                        false, "CLAIMED"));
    }

    @Test
    void terminalResultClearsPendingAndRejectsDuplicates() {
        AtomicLong now = new AtomicLong();
        AtmWithdrawalTracker tracker = tracker(now, FIRST);
        tracker.begin(SIGNATURE, List.of(1), 100L);

        assertEquals(AtmWithdrawalTracker.ResultDecision.ACCEPT_TERMINAL,
                tracker.evaluateResult(FIRST, false, "CLAIMED"));
        assertFalse(tracker.pending().isPresent());
        assertEquals(AtmWithdrawalTracker.PendingState.NONE,
                tracker.state());
        assertEquals(AtmWithdrawalTracker.ResultDecision.DUPLICATE,
                tracker.evaluateResult(FIRST, false, "CLAIMED"));
    }

    @Test
    void completedRequestCacheIsBounded() {
        AtomicLong now = new AtomicLong();
        ArrayDeque<UUID> ids = new ArrayDeque<>(List.of(
                FIRST, SECOND, THIRD));
        AtmWithdrawalTracker tracker = new AtmWithdrawalTracker(
                ids::removeFirst, now::get, 10L, 2);
        for (UUID id : List.of(FIRST, SECOND, THIRD)) {
            tracker.begin(SIGNATURE, List.of(1), 100L);
            assertEquals(AtmWithdrawalTracker.ResultDecision.ACCEPT_TERMINAL,
                    tracker.evaluateResult(id, false, "CLAIMED"));
        }

        assertEquals(AtmWithdrawalTracker.ResultDecision.UNTRACKED,
                tracker.evaluateResult(FIRST, false, "CLAIMED"));
        assertEquals(AtmWithdrawalTracker.ResultDecision.DUPLICATE,
                tracker.evaluateResult(SECOND, false, "CLAIMED"));
        assertEquals(AtmWithdrawalTracker.ResultDecision.DUPLICATE,
                tracker.evaluateResult(THIRD, false, "CLAIMED"));
    }

    @Test
    void clearDropsPendingAndCompletedState() {
        AtomicLong now = new AtomicLong();
        AtmWithdrawalTracker tracker = tracker(now, FIRST);
        tracker.begin(SIGNATURE, List.of(1), 100L);
        tracker.evaluateResult(FIRST, false, "CLAIMED");

        tracker.clear();

        assertEquals(AtmWithdrawalTracker.ResultDecision.UNTRACKED,
                tracker.evaluateResult(FIRST, false, "CLAIMED"));
    }

    private static AtmWithdrawalTracker tracker(AtomicLong now, UUID id) {
        return new AtmWithdrawalTracker(() -> id, now::get, 10L, 4);
    }
}
