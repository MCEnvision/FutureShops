package com.enviouse.futureshops.client.market;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MarketCapabilityResponseTracker {
    public static final int MAXIMUM_TRACKED_REQUESTS = 4096;

    private static final UUID ZERO = new UUID(0L, 0L);

    private final int maximumTrackedRequests;
    private final LinkedHashMap<UUID, Request> requests =
            new LinkedHashMap<>();
    private long nextSequence;
    private long latestSequence;
    private MarketCapabilitiesSnapshot latest;
    private boolean open = true;

    public MarketCapabilityResponseTracker(int maximumTrackedRequests) {
        if (maximumTrackedRequests <= 0
                || maximumTrackedRequests > MAXIMUM_TRACKED_REQUESTS) {
            throw new IllegalArgumentException(
                    "Market capability tracking limit is invalid");
        }
        this.maximumTrackedRequests = maximumTrackedRequests;
    }

    public synchronized void begin(UUID requestId) {
        requireOpen();
        UUID id = requireId(requestId);
        Request existing = requests.get(id);
        if (existing != null) {
            if (existing.consumed()) {
                throw new IllegalArgumentException(
                        "Market capability request identity was already consumed");
            }
            return;
        }
        long sequence = Math.incrementExact(nextSequence);
        nextSequence = sequence;
        latestSequence = sequence;
        requests.put(id, new Request(sequence, false));
        trim();
    }

    public synchronized Decision accept(
            MarketCapabilitiesSnapshot snapshot
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!open) {
            return Decision.CLOSED;
        }
        Request request = requests.get(snapshot.requestId());
        if (request == null) {
            return Decision.UNKNOWN_REQUEST;
        }
        if (request.consumed()) {
            return Decision.DUPLICATE_RESPONSE;
        }
        requests.put(snapshot.requestId(), request.consumedCopy());
        if (request.sequence() != latestSequence) {
            return Decision.STALE_REQUEST;
        }
        if (latest != null && snapshot.revision() < latest.revision()) {
            return Decision.STALE_REVISION;
        }
        if (latest != null && snapshot.revision() == latest.revision()
                && !sameState(snapshot, latest)) {
            return Decision.REVISION_CONFLICT;
        }
        latest = snapshot;
        return Decision.ACCEPT;
    }

    public synchronized Optional<MarketCapabilitiesSnapshot> latest() {
        return Optional.ofNullable(latest);
    }

    public synchronized int trackedRequestCount() {
        return requests.size();
    }

    public synchronized void clear() {
        requests.clear();
        nextSequence = 0L;
        latestSequence = 0L;
        latest = null;
        open = true;
    }

    public synchronized void close() {
        requests.clear();
        latest = null;
        open = false;
    }

    private void trim() {
        while (requests.size() > maximumTrackedRequests) {
            Map.Entry<UUID, Request> eldest =
                    requests.entrySet().iterator().next();
            requests.remove(eldest.getKey());
        }
    }

    private void requireOpen() {
        if (!open) {
            throw new IllegalStateException(
                    "Market capability response tracker is closed");
        }
    }

    private static UUID requireId(UUID requestId) {
        UUID result = Objects.requireNonNull(requestId, "requestId");
        if (ZERO.equals(result)) {
            throw new IllegalArgumentException(
                    "Market capability request identity is invalid");
        }
        return result;
    }

    private static boolean sameState(
            MarketCapabilitiesSnapshot first,
            MarketCapabilitiesSnapshot second
    ) {
        return first.showNavigation() == second.showNavigation()
                && first.defaultModule() == second.defaultModule()
                && first.walletBalanceMinorUnits()
                == second.walletBalanceMinorUnits()
                && first.walletBalanceKnown()
                == second.walletBalanceKnown()
                && first.currencyName().equals(second.currencyName())
                && first.currencyDecimals() == second.currencyDecimals()
                && first.modules().equals(second.modules());
    }

    public enum Decision {
        ACCEPT,
        UNKNOWN_REQUEST,
        DUPLICATE_RESPONSE,
        STALE_REQUEST,
        STALE_REVISION,
        REVISION_CONFLICT,
        CLOSED
    }

    private record Request(long sequence, boolean consumed) {
        private Request {
            if (sequence <= 0L) {
                throw new IllegalArgumentException(
                        "Market capability request sequence is invalid");
            }
        }

        private Request consumedCopy() {
            return new Request(sequence, true);
        }
    }
}
