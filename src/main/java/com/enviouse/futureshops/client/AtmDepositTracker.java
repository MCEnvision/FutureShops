package com.enviouse.futureshops.client;

import com.enviouse.futureshops.network.packets.C2SAtmDepositPacket;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class AtmDepositTracker {
    public static final long MAX_RETRY_AFTER_MILLIS = 3_600_000_000L;

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
            C2SAtmDepositPacket.Source source,
            OptionalLong requestedMinorUnits,
            int attempts,
            long lastSentAtNanos
    ) {
        public PendingRequest {
            Objects.requireNonNull(requestId, "requestId");
            currencySignature = Objects.requireNonNull(
                    currencySignature, "currencySignature");
            Objects.requireNonNull(source, "source");
            requestedMinorUnits = Objects.requireNonNull(
                    requestedMinorUnits, "requestedMinorUnits");
            if (requestId.equals(new UUID(0L, 0L))
                    || !SIGNATURE.matcher(currencySignature).matches()
                    || requestedMinorUnits.isPresent()
                    && requestedMinorUnits.getAsLong() <= 0L
                    || attempts <= 0) {
                throw new IllegalArgumentException(
                        "ATM deposit pending request is invalid");
            }
        }

        private PendingRequest retried(long now) {
            return new PendingRequest(requestId, currencySignature, source,
                    requestedMinorUnits, Math.addExact(attempts, 1), now);
        }
    }

    private static final long DEFAULT_TIMEOUT_NANOS =
            Duration.ofSeconds(10L).toNanos();
    private static final int DEFAULT_COMPLETED_LIMIT = 32;
    private static final Pattern SIGNATURE = Pattern.compile("[0-9a-f]{64}");

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

    public AtmDepositTracker() {
        this(UUID::randomUUID, System::nanoTime,
                DEFAULT_TIMEOUT_NANOS, DEFAULT_COMPLETED_LIMIT);
    }

    AtmDepositTracker(
            Supplier<UUID> requestIds,
            LongSupplier clock,
            long timeoutNanos,
            int completedLimit
    ) {
        this.requestIds = Objects.requireNonNull(requestIds, "requestIds");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (timeoutNanos <= 0L || completedLimit <= 0) {
            throw new IllegalArgumentException(
                    "ATM deposit tracker limits are invalid");
        }
        this.timeoutNanos = timeoutNanos;
        this.completedLimit = completedLimit;
    }

    public synchronized PendingRequest begin(
            String currencySignature,
            C2SAtmDepositPacket.Source source,
            OptionalLong requestedMinorUnits
    ) {
        if (pending != null) {
            throw new IllegalStateException(
                    "An ATM deposit request is already pending");
        }
        UUID requestId = Objects.requireNonNull(
                requestIds.get(), "requestId");
        PendingRequest request = new PendingRequest(
                requestId, currencySignature, source, requestedMinorUnits,
                1, clock.getAsLong());
        if (completed.containsKey(requestId)) {
            throw new IllegalStateException(
                    "ATM deposit request ID is unavailable");
        }
        pending = request;
        serverRetryable = false;
        retryDelayStartedAtNanos = 0L;
        retryDelayNanos = 0L;
        pendingResultKeys.clear();
        return request;
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
                    "ATM deposit request is not retryable");
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
            long retryAfterMillis,
            String resultKey
    ) {
        Objects.requireNonNull(requestId, "requestId");
        String key = Objects.requireNonNull(resultKey, "resultKey");
        if (key.isEmpty() || key.length() > 1024
                || retryAfterMillis < 0L
                || retryAfterMillis > MAX_RETRY_AFTER_MILLIS
                || !retryable && retryAfterMillis != 0L) {
            throw new IllegalArgumentException(
                    "ATM deposit result is invalid");
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
        if (!pendingResultKeys.add(key)) {
            return ResultDecision.DUPLICATE;
        }
        if (retryable) {
            serverRetryable = true;
            retryDelayStartedAtNanos = clock.getAsLong();
            retryDelayNanos = Math.multiplyExact(
                    retryAfterMillis, 1_000_000L);
            return ResultDecision.ACCEPT_RETRYABLE;
        }
        completed.put(requestId, Boolean.TRUE);
        while (completed.size() > completedLimit) {
            completed.remove(completed.keySet().iterator().next());
        }
        pending = null;
        serverRetryable = false;
        retryDelayStartedAtNanos = 0L;
        retryDelayNanos = 0L;
        pendingResultKeys.clear();
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

    private static boolean elapsedAtLeast(
            long now,
            long startedAt,
            long duration
    ) {
        return now - startedAt >= duration;
    }
}
