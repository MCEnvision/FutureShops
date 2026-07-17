package com.enviouse.futureshops.client;

import com.enviouse.futureshops.money.CurrencyWithdrawalService;
import com.enviouse.futureshops.server.escrow.runtime.AtmWithdrawalOutcome;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class AtmWithdrawalTracker {
    public static final long MAX_RETRY_AFTER_MILLIS =
            AtmWithdrawalOutcome.MAX_RETRY_AFTER_MILLIS;

    public enum PendingState {
        NONE,
        AWAITING,
        RETRYABLE
    }

    public enum ResultDecision {
        ACCEPT_RETRYABLE,
        ACCEPT_TERMINAL,
        DUPLICATE,
        MISMATCHED,
        UNTRACKED
    }

    public record PendingRequest(
            UUID requestId,
            String currencySignature,
            List<Integer> denominationCounts,
            long amountMinor,
            int attempts,
            long lastSentAtNanos
    ) {
        public PendingRequest {
            Objects.requireNonNull(requestId, "requestId");
            currencySignature = Objects.requireNonNull(
                    currencySignature, "currencySignature");
            denominationCounts = List.copyOf(Objects.requireNonNull(
                    denominationCounts, "denominationCounts"));
            if (amountMinor <= 0L || attempts <= 0) {
                throw new IllegalArgumentException(
                        "ATM pending request values are invalid");
            }
        }

        private PendingRequest retried(long now) {
            return new PendingRequest(requestId, currencySignature,
                    denominationCounts, amountMinor,
                    Math.addExact(attempts, 1), now);
        }
    }

    private static final Pattern SIGNATURE = Pattern.compile("[0-9a-f]{64}");
    private static final long DEFAULT_TIMEOUT_NANOS =
            Duration.ofSeconds(10).toNanos();
    private static final int DEFAULT_COMPLETED_LIMIT = 32;

    private final Supplier<UUID> requestIds;
    private final LongSupplier clock;
    private final long timeoutNanos;
    private final int completedLimit;
    private final LinkedHashMap<UUID, Boolean> completed =
            new LinkedHashMap<>();
    private final Set<String> pendingResultKeys = new LinkedHashSet<>();

    private PendingRequest pending;
    private boolean serverRetryable;
    private long retryDelayStartedAtNanos;
    private long retryDelayNanos;

    public AtmWithdrawalTracker() {
        this(UUID::randomUUID, System::nanoTime,
                DEFAULT_TIMEOUT_NANOS, DEFAULT_COMPLETED_LIMIT);
    }

    AtmWithdrawalTracker(Supplier<UUID> requestIds,
                         LongSupplier clock,
                         long timeoutNanos,
                         int completedLimit) {
        this.requestIds = Objects.requireNonNull(requestIds, "requestIds");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (timeoutNanos <= 0L || completedLimit <= 0) {
            throw new IllegalArgumentException(
                    "ATM tracker limits are invalid");
        }
        this.timeoutNanos = timeoutNanos;
        this.completedLimit = completedLimit;
    }

    public synchronized PendingRequest begin(
            String currencySignature,
            List<Integer> denominationCounts,
            long amountMinor
    ) {
        if (pending != null) {
            throw new IllegalStateException(
                    "An ATM withdrawal request is already pending");
        }
        validatePayload(currencySignature, denominationCounts, amountMinor);
        UUID requestId = Objects.requireNonNull(
                requestIds.get(), "requestId");
        if (requestId.equals(new UUID(0L, 0L))
                || completed.containsKey(requestId)) {
            throw new IllegalStateException("ATM request ID is unavailable");
        }
        pending = new PendingRequest(requestId, currencySignature,
                denominationCounts, amountMinor, 1, clock.getAsLong());
        serverRetryable = false;
        retryDelayStartedAtNanos = 0L;
        retryDelayNanos = 0L;
        pendingResultKeys.clear();
        return pending;
    }

    public synchronized Optional<PendingRequest> pending() {
        return Optional.ofNullable(pending);
    }

    public synchronized PendingState state() {
        if (pending == null) {
            return PendingState.NONE;
        }
        long now = clock.getAsLong();
        if (serverRetryable) {
            return elapsedAtLeast(now, retryDelayStartedAtNanos,
                    retryDelayNanos)
                    ? PendingState.RETRYABLE : PendingState.AWAITING;
        }
        if (elapsedAtLeast(
                now, pending.lastSentAtNanos(), timeoutNanos)) {
            return PendingState.RETRYABLE;
        }
        return PendingState.AWAITING;
    }

    public synchronized PendingRequest retry() {
        if (pending == null || state() != PendingState.RETRYABLE) {
            throw new IllegalStateException(
                    "ATM withdrawal request is not retryable");
        }
        pending = pending.retried(clock.getAsLong());
        serverRetryable = false;
        retryDelayStartedAtNanos = 0L;
        retryDelayNanos = 0L;
        pendingResultKeys.clear();
        return pending;
    }

    public synchronized ResultDecision evaluateResult(
            UUID requestId,
            boolean retryable,
            String resultKey
    ) {
        String expectedSignature = pending == null
                ? null : pending.currencySignature();
        return evaluateResult(requestId, expectedSignature,
                retryable, 0L, resultKey);
    }

    public synchronized ResultDecision evaluateResult(
            UUID requestId,
            String currencySignature,
            boolean retryable,
            String resultKey
    ) {
        return evaluateResult(requestId, currencySignature,
                retryable, 0L, resultKey);
    }

    public synchronized ResultDecision evaluateResult(
            UUID requestId,
            String currencySignature,
            boolean retryable,
            long retryAfterMillis,
            String resultKey
    ) {
        Objects.requireNonNull(requestId, "requestId");
        String safeResultKey = Objects.requireNonNull(
                resultKey, "resultKey");
        if (safeResultKey.isEmpty() || safeResultKey.length() > 1024
                || retryAfterMillis < 0L
                || retryAfterMillis > MAX_RETRY_AFTER_MILLIS
                || !retryable && retryAfterMillis != 0L) {
            throw new IllegalArgumentException("ATM result key is invalid");
        }
        if (completed.containsKey(requestId)) {
            return ResultDecision.DUPLICATE;
        }
        if (pending == null) {
            return ResultDecision.UNTRACKED;
        }
        if (!pending.requestId().equals(requestId)) {
            return ResultDecision.MISMATCHED;
        }
        if (!pending.currencySignature().equals(currencySignature)) {
            return ResultDecision.MISMATCHED;
        }
        if (!pendingResultKeys.add(safeResultKey)) {
            return ResultDecision.DUPLICATE;
        }
        if (retryable) {
            serverRetryable = true;
            retryDelayStartedAtNanos = clock.getAsLong();
            retryDelayNanos = Math.multiplyExact(
                    retryAfterMillis, 1_000_000L);
            return ResultDecision.ACCEPT_RETRYABLE;
        }
        complete(requestId);
        return ResultDecision.ACCEPT_TERMINAL;
    }

    public synchronized void clear() {
        pending = null;
        serverRetryable = false;
        retryDelayStartedAtNanos = 0L;
        retryDelayNanos = 0L;
        pendingResultKeys.clear();
        completed.clear();
    }

    private void complete(UUID requestId) {
        completed.put(requestId, Boolean.TRUE);
        while (completed.size() > completedLimit) {
            UUID oldest = completed.keySet().iterator().next();
            completed.remove(oldest);
        }
        pending = null;
        serverRetryable = false;
        retryDelayStartedAtNanos = 0L;
        retryDelayNanos = 0L;
        pendingResultKeys.clear();
    }

    private static boolean elapsedAtLeast(
            long now,
            long startedAt,
            long duration
    ) {
        return now - startedAt >= duration;
    }

    private static void validatePayload(
            String currencySignature,
            List<Integer> denominationCounts,
            long amountMinor
    ) {
        if (currencySignature == null
                || !SIGNATURE.matcher(currencySignature).matches()
                || denominationCounts == null
                || denominationCounts.isEmpty()
                || denominationCounts.size()
                > CurrencyWithdrawalService.MAX_DENOMINATIONS
                || amountMinor <= 0L) {
            throw new IllegalArgumentException(
                    "ATM withdrawal payload is invalid");
        }
        int selected = 0;
        for (Integer count : denominationCounts) {
            if (count == null || count < 0
                    || count > CurrencyWithdrawalService.MAX_SELECTED_ITEMS) {
                throw new IllegalArgumentException(
                        "ATM denomination selection is invalid");
            }
            selected = Math.addExact(selected, count);
            if (selected > CurrencyWithdrawalService.MAX_SELECTED_ITEMS) {
                throw new IllegalArgumentException(
                        "ATM denomination selection exceeds its limit");
            }
        }
        if (selected == 0) {
            throw new IllegalArgumentException(
                    "ATM denomination selection is empty");
        }
    }
}
