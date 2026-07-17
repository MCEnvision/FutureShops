package com.enviouse.futureshops.client;

import com.enviouse.futureshops.network.packets.C2SAtmCollectCashPacket;
import com.enviouse.futureshops.network.packets.S2CAtmCollectCashResultPacket;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class AtmCashClaimCollectionTracker {
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
            UUID playerId,
            List<UUID> claimIds,
            int attempts,
            long lastSentAtNanos
    ) {
        public PendingRequest {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(playerId, "playerId");
            claimIds = List.copyOf(Objects.requireNonNull(
                    claimIds, "claimIds"));
            if (attempts <= 0) {
                throw new IllegalArgumentException(
                        "ATM cash collection pending request is invalid");
            }
            new C2SAtmCollectCashPacket(requestId, claimIds);
            if (!C2SAtmCollectCashPacket.matchesRequestId(
                    requestId, playerId, claimIds)) {
                throw new IllegalArgumentException(
                        "ATM cash collection request identity is invalid");
            }
        }

        private PendingRequest retried(long now) {
            return new PendingRequest(requestId, playerId, claimIds,
                    Math.addExact(attempts, 1), now);
        }
    }

    private static final long DEFAULT_TIMEOUT_NANOS =
            Duration.ofSeconds(10).toNanos();
    private static final int DEFAULT_COMPLETED_LIMIT = 32;

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

    public AtmCashClaimCollectionTracker() {
        this(System::nanoTime,
                DEFAULT_TIMEOUT_NANOS, DEFAULT_COMPLETED_LIMIT);
    }

    AtmCashClaimCollectionTracker(LongSupplier clock,
                                  long timeoutNanos,
                                  int completedLimit) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (timeoutNanos <= 0L || completedLimit <= 0) {
            throw new IllegalArgumentException(
                    "ATM cash collection tracker limits are invalid");
        }
        this.timeoutNanos = timeoutNanos;
        this.completedLimit = completedLimit;
    }

    public synchronized PendingRequest begin(
            UUID playerId,
            List<UUID> claimIds
    ) {
        return beginIfFresh(playerId, claimIds).orElseThrow(() ->
                new IllegalStateException(
                        "ATM cash collection request is stale or pending"));
    }

    public synchronized Optional<PendingRequest> beginIfFresh(
            UUID playerId,
            List<UUID> claimIds
    ) {
        if (pending != null) {
            return Optional.empty();
        }
        List<UUID> exactClaims = List.copyOf(Objects.requireNonNull(
                claimIds, "claimIds"));
        if (exactClaims.isEmpty()
                || exactClaims.size() > C2SAtmCollectCashPacket.MAX_CLAIMS
                || exactClaims.stream().anyMatch(Objects::isNull)
                || new HashSet<>(exactClaims).size() != exactClaims.size()) {
            throw new IllegalArgumentException(
                    "ATM cash collection claims are invalid");
        }
        UUID owner = Objects.requireNonNull(playerId, "playerId");
        UUID requestId = C2SAtmCollectCashPacket.deriveRequestId(
                owner, exactClaims);
        if (completed.containsKey(requestId)) {
            return Optional.empty();
        }
        pending = new PendingRequest(requestId, owner, exactClaims,
                1, clock.getAsLong());
        serverRetryable = false;
        retryDelayStartedAtNanos = 0L;
        retryDelayNanos = 0L;
        pendingResultKeys.clear();
        return Optional.of(pending);
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
                    "ATM cash collection request is not retryable");
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
        return evaluateResult(requestId, retryable, 0L, resultKey);
    }

    public synchronized ResultDecision evaluateResult(
            UUID requestId,
            boolean retryable,
            long retryAfterMillis,
            String resultKey
    ) {
        Objects.requireNonNull(requestId, "requestId");
        String key = Objects.requireNonNull(resultKey, "resultKey");
        if (key.isEmpty() || key.length() > 1024) {
            throw new IllegalArgumentException(
                    "ATM cash collection result key is invalid");
        }
        if (retryAfterMillis < 0L
                || retryAfterMillis
                > S2CAtmCollectCashResultPacket.MAX_RETRY_AFTER_MILLIS
                || !retryable && retryAfterMillis != 0L) {
            throw new IllegalArgumentException(
                    "ATM cash collection retry delay is invalid");
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
