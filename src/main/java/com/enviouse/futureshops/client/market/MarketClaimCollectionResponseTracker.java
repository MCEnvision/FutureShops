package com.enviouse.futureshops.client.market;

import com.enviouse.futureshops.server.market.claim.MarketClaimCollectionCommand;
import com.enviouse.futureshops.server.market.claim.MarketClaimCollectionResult;
import com.enviouse.futureshops.server.market.claim.MarketClaimPresentationKind;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MarketClaimCollectionResponseTracker {
    public static final int MAXIMUM_TRACKED_REQUESTS = 4096;

    private final int maximumTrackedRequests;
    private final LinkedHashMap<UUID, Request> requests =
            new LinkedHashMap<>();
    private MarketClaimCollectionResult latest;

    public MarketClaimCollectionResponseTracker(
            int maximumTrackedRequests
    ) {
        if (maximumTrackedRequests <= 0
                || maximumTrackedRequests > MAXIMUM_TRACKED_REQUESTS) {
            throw new IllegalArgumentException(
                    "Market claim tracking limit is invalid");
        }
        this.maximumTrackedRequests = maximumTrackedRequests;
    }

    public synchronized void begin(
            MarketClaimCollectionCommand command
    ) {
        MarketClaimCollectionCommand value = Objects.requireNonNull(
                command, "command");
        Request request = new Request(value.routeNonce(),
                value.module(), value.view(), value.claimId(),
                value.fingerprint(), null);
        Request existing = requests.get(value.requestId());
        if (existing != null) {
            if (!existing.sameRequest(request)) {
                throw new IllegalArgumentException(
                        "Market claim request identity was reused");
            }
            return;
        }
        requests.put(value.requestId(), request);
        trim();
    }

    public synchronized Decision accept(
            MarketClaimCollectionResult result
    ) {
        MarketClaimCollectionResult value = Objects.requireNonNull(
                result, "result");
        Request request = requests.get(value.requestId());
        if (request == null) {
            return Decision.UNKNOWN_REQUEST;
        }
        if (!request.routeNonce().equals(value.routeNonce())
                || request.module() != value.module()
                || !request.view().equals(value.view())
                || !request.claimId().equals(value.claimId())) {
            return Decision.CORRELATION_MISMATCH;
        }
        MarketClaimCollectionResult previous = request.result();
        if (value.equals(previous)) {
            return Decision.DUPLICATE_RESPONSE;
        }
        if (previous != null && previous.terminal()) {
            return Decision.TERMINAL_CONFLICT;
        }
        if (previous != null
                && previous.kind() != MarketClaimPresentationKind.UNKNOWN
                && value.kind() != MarketClaimPresentationKind.UNKNOWN
                && previous.kind() != value.kind()) {
            return Decision.CORRELATION_MISMATCH;
        }
        if (previous != null && previous.remainingUnits() > 0L
                && value.remainingUnits()
                > previous.remainingUnits()) {
            return Decision.STATE_REGRESSION;
        }
        requests.put(value.requestId(), request.withResult(value));
        latest = value;
        return Decision.ACCEPT;
    }

    public synchronized Optional<MarketClaimCollectionResult> latest() {
        return Optional.ofNullable(latest);
    }

    public synchronized Optional<MarketClaimCollectionResult> result(
            UUID requestId
    ) {
        Request request = requests.get(Objects.requireNonNull(
                requestId, "requestId"));
        return request == null
                ? Optional.empty() : Optional.ofNullable(request.result());
    }

    public synchronized int trackedRequestCount() {
        return requests.size();
    }

    public synchronized void clear() {
        requests.clear();
        latest = null;
    }

    private void trim() {
        while (requests.size() > maximumTrackedRequests) {
            Map.Entry<UUID, Request> eldest = requests.entrySet()
                    .iterator().next();
            requests.remove(eldest.getKey());
        }
    }

    public enum Decision {
        ACCEPT,
        UNKNOWN_REQUEST,
        DUPLICATE_RESPONSE,
        CORRELATION_MISMATCH,
        TERMINAL_CONFLICT,
        STATE_REGRESSION
    }

    private record Request(
            UUID routeNonce,
            MarketModule module,
            String view,
            UUID claimId,
            String fingerprint,
            MarketClaimCollectionResult result
    ) {
        private Request {
            routeNonce = Objects.requireNonNull(routeNonce,
                    "routeNonce");
            module = Objects.requireNonNull(module, "module");
            view = Objects.requireNonNull(view, "view");
            claimId = Objects.requireNonNull(claimId, "claimId");
            fingerprint = Objects.requireNonNull(fingerprint,
                    "fingerprint");
        }

        private boolean sameRequest(Request other) {
            return routeNonce.equals(other.routeNonce)
                    && module == other.module
                    && view.equals(other.view)
                    && claimId.equals(other.claimId)
                    && fingerprint.equals(other.fingerprint);
        }

        private Request withResult(MarketClaimCollectionResult value) {
            return new Request(routeNonce, module, view, claimId,
                    fingerprint, value);
        }
    }
}
