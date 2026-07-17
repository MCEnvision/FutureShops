package com.enviouse.futureshops.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CartResponsePolicy {
    public static final UUID UNCORRELATED_REQUEST_ID = new UUID(0L, 0L);

    public enum BeginDecision {
        STARTED,
        EMPTY,
        ALREADY_PENDING
    }

    public enum ResponseDecision {
        STALE,
        NON_TERMINAL,
        LINE_SUCCEEDED,
        LINE_FAILED,
        CHECKOUT_SUCCEEDED,
        CHECKOUT_FINISHED_WITH_FAILURES
    }

    public enum TimeoutDecision {
        NOT_PENDING,
        WAITING,
        TIMED_OUT
    }

    public enum RetryDecision {
        NOTHING_TO_RETRY,
        ALREADY_PENDING,
        RETRIED
    }

    public record Line(int responseToken, String key, int quantity) {
        public Line {
            if (responseToken < 0) {
                throw new IllegalArgumentException("responseToken must be nonnegative");
            }
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("key must not be blank");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
        }
    }

    public record ResponseResult(
            ResponseDecision decision,
            List<Line> linesToClear,
            boolean matched,
            boolean checkoutComplete,
            boolean checkoutSuccessful
    ) {
        public ResponseResult {
            linesToClear = List.copyOf(linesToClear);
        }

        private static ResponseResult stale() {
            return new ResponseResult(ResponseDecision.STALE, List.of(), false, false, false);
        }
    }

    private PendingCheckout pending;

    public synchronized BeginDecision begin(
            UUID requestId,
            List<Line> lines,
            long nowMillis,
            long timeoutMillis
    ) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(lines, "lines");
        if (timeoutMillis <= 0L) {
            throw new IllegalArgumentException("timeoutMillis must be positive");
        }
        if (UNCORRELATED_REQUEST_ID.equals(requestId)) {
            throw new IllegalArgumentException("requestId must be correlated");
        }
        if (pending != null) {
            return BeginDecision.ALREADY_PENDING;
        }
        if (lines.isEmpty()) {
            return BeginDecision.EMPTY;
        }

        Map<Integer, List<Line>> linesByToken = new LinkedHashMap<>();
        Set<String> uniqueKeys = new LinkedHashSet<>();
        for (Line line : lines) {
            Objects.requireNonNull(line, "line");
            if (!uniqueKeys.add(line.key())) {
                throw new IllegalArgumentException("cart line keys must be unique");
            }
            linesByToken.computeIfAbsent(line.responseToken(), ignored -> new ArrayList<>()).add(line);
        }

        Map<Integer, List<Line>> immutableLines = new LinkedHashMap<>();
        linesByToken.forEach((token, tokenLines) -> immutableLines.put(token, List.copyOf(tokenLines)));
        pending = new PendingCheckout(
                requestId,
                Math.addExact(nowMillis, timeoutMillis),
                Map.copyOf(immutableLines),
                new LinkedHashSet<>(),
                false,
                true);
        return BeginDecision.STARTED;
    }

    public synchronized ResponseResult onResponse(
            UUID requestId,
            int responseToken,
            boolean success,
            boolean terminal,
            long nowMillis
    ) {
        if (pending != null && pending.active && nowMillis >= pending.deadlineMillis) {
            pending.active = false;
        }
        if (pending == null
                || requestId == null
                || !pending.requestId.equals(requestId)
                || !pending.linesByToken.containsKey(responseToken)
                || pending.resolvedTokens.contains(responseToken)) {
            return ResponseResult.stale();
        }

        if (!terminal) {
            return new ResponseResult(
                    ResponseDecision.NON_TERMINAL, List.of(), true, false, false);
        }

        pending.resolvedTokens.add(responseToken);
        List<Line> linesToClear = success
                ? pending.linesByToken.get(responseToken)
                : List.of();
        if (!success) {
            pending.sawFailure = true;
        }

        boolean complete = pending.resolvedTokens.size() == pending.linesByToken.size();
        boolean checkoutSuccessful = complete && !pending.sawFailure;
        ResponseDecision decision;
        if (checkoutSuccessful) {
            decision = ResponseDecision.CHECKOUT_SUCCEEDED;
        } else if (complete) {
            decision = ResponseDecision.CHECKOUT_FINISHED_WITH_FAILURES;
        } else if (success) {
            decision = ResponseDecision.LINE_SUCCEEDED;
        } else {
            decision = ResponseDecision.LINE_FAILED;
        }

        if (complete) {
            pending = null;
        }
        return new ResponseResult(
                decision, linesToClear, true, complete, checkoutSuccessful);
    }

    public synchronized TimeoutDecision expire(long nowMillis) {
        if (pending == null || !pending.active) {
            return TimeoutDecision.NOT_PENDING;
        }
        if (nowMillis < pending.deadlineMillis) {
            return TimeoutDecision.WAITING;
        }
        pending.active = false;
        return TimeoutDecision.TIMED_OUT;
    }

    public synchronized RetryDecision retry(long nowMillis, long timeoutMillis) {
        if (timeoutMillis <= 0L) {
            throw new IllegalArgumentException("timeoutMillis must be positive");
        }
        if (pending == null) {
            return RetryDecision.NOTHING_TO_RETRY;
        }
        if (pending.active) {
            return RetryDecision.ALREADY_PENDING;
        }
        pending.deadlineMillis = Math.addExact(nowMillis, timeoutMillis);
        pending.active = true;
        return RetryDecision.RETRIED;
    }

    public synchronized boolean isPending() {
        return pending != null && pending.active;
    }

    public synchronized boolean hasTrackedRequest() {
        return pending != null;
    }

    public synchronized Optional<UUID> pendingRequestId() {
        return pending == null ? Optional.empty() : Optional.of(pending.requestId);
    }

    public synchronized void reset() {
        pending = null;
    }

    private static final class PendingCheckout {
        private final UUID requestId;
        private long deadlineMillis;
        private final Map<Integer, List<Line>> linesByToken;
        private final Set<Integer> resolvedTokens;
        private boolean sawFailure;
        private boolean active;

        private PendingCheckout(
                UUID requestId,
                long deadlineMillis,
                Map<Integer, List<Line>> linesByToken,
                Set<Integer> resolvedTokens,
                boolean sawFailure,
                boolean active
        ) {
            this.requestId = requestId;
            this.deadlineMillis = deadlineMillis;
            this.linesByToken = linesByToken;
            this.resolvedTokens = resolvedTokens;
            this.sawFailure = sawFailure;
            this.active = active;
        }
    }
}
