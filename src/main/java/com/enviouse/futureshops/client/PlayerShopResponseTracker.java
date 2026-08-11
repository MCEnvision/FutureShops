package com.enviouse.futureshops.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PlayerShopResponseTracker {
    public static final int MAXIMUM_PENDING = 128;
    public static final int MAXIMUM_RESPONSE_TOKEN = 2_303;

    private static final UUID ZERO = new UUID(0L, 0L);

    private final Map<UUID, PendingRequest> pending =
            new LinkedHashMap<>();

    public synchronized PendingRequest begin(
            Operation operation,
            int responseToken
    ) {
        Objects.requireNonNull(operation, "operation");
        if (responseToken < 0
                || responseToken > MAXIMUM_RESPONSE_TOKEN) {
            throw new IllegalArgumentException(
                    "Player shop response token is invalid");
        }
        UUID requestId;
        do {
            requestId = UUID.randomUUID();
        } while (ZERO.equals(requestId) || pending.containsKey(requestId));
        if (pending.size() >= MAXIMUM_PENDING) {
            UUID oldest = pending.keySet().iterator().next();
            pending.remove(oldest);
        }
        PendingRequest request = new PendingRequest(
                requestId, responseToken, operation);
        pending.put(requestId, request);
        return request;
    }

    public synchronized Match consume(UUID requestId, int responseToken) {
        Objects.requireNonNull(requestId, "requestId");
        PendingRequest expected = pending.get(requestId);
        if (expected == null) {
            return Match.STALE;
        }
        if (expected.responseToken() != responseToken) {
            return Match.TOKEN_MISMATCH;
        }
        pending.remove(requestId);
        return Match.MATCHED;
    }

    public synchronized Optional<PendingRequest> pending(UUID requestId) {
        return Optional.ofNullable(pending.get(
                Objects.requireNonNull(requestId, "requestId")));
    }

    public synchronized int size() {
        return pending.size();
    }

    public synchronized void clear() {
        pending.clear();
    }

    public enum Operation {
        PURCHASE,
        BUYBACK,
        SETTLEMENT
    }

    public enum Match {
        MATCHED,
        STALE,
        TOKEN_MISMATCH
    }

    public record PendingRequest(
            UUID requestId,
            int responseToken,
            Operation operation
    ) {
        public PendingRequest {
            requestId = Objects.requireNonNull(requestId, "requestId");
            operation = Objects.requireNonNull(operation, "operation");
            if (ZERO.equals(requestId) || responseToken < 0
                    || responseToken > MAXIMUM_RESPONSE_TOKEN) {
                throw new IllegalArgumentException(
                        "Player shop pending request is invalid");
            }
        }
    }
}
