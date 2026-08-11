package com.enviouse.futureshops.client.market;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

public final class MarketRequestGate {
    public enum Decision {
        ACCEPT,
        STALE_ROUTE,
        STALE_REQUEST,
        UNKNOWN_REQUEST,
        DUPLICATE_RESPONSE,
        CLOSED
    }

    private final int maximumTrackedRequests;
    private final LinkedHashMap<UUID, Request> requests = new LinkedHashMap<>();
    private final LinkedHashMap<RouteKey, Long> latestSequences = new LinkedHashMap<>();
    private final Set<UUID> retiredRouteNonces = new HashSet<>();
    private UUID currentRouteNonce;
    private long nextSequence;
    private boolean open = true;

    public MarketRequestGate(UUID routeNonce, int maximumTrackedRequests) {
        currentRouteNonce = requireId(routeNonce, "route nonce");
        if (maximumTrackedRequests <= 0 || maximumTrackedRequests > 4096) {
            throw new IllegalArgumentException("Market request tracking limit is invalid.");
        }
        this.maximumTrackedRequests = maximumTrackedRequests;
    }

    public synchronized void enterRoute(UUID routeNonce) {
        requireOpen();
        UUID next = requireId(routeNonce, "route nonce");
        if (currentRouteNonce.equals(next)) {
            return;
        }
        if (retiredRouteNonces.size() >= 4096 || retiredRouteNonces.contains(next)) {
            throw new IllegalArgumentException("Market route nonce was already retired.");
        }
        retiredRouteNonces.add(currentRouteNonce);
        currentRouteNonce = next;
    }

    public synchronized void begin(UUID requestId, MarketModule module, UUID routeNonce) {
        begin(requestId, module, routeNonce, MarketResponseFamily.CONTENT);
    }

    public synchronized void begin(
        UUID requestId,
        MarketModule module,
        UUID routeNonce,
        MarketResponseFamily family
    ) {
        requireOpen();
        UUID id = requireId(requestId, "request identifier");
        UUID route = requireId(routeNonce, "route nonce");
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(family, "family");
        if (!currentRouteNonce.equals(route)) {
            throw new IllegalArgumentException("Market requests require the active route nonce.");
        }
        Request previous = requests.get(id);
        if (previous != null) {
            if (previous.module != module || !previous.routeNonce.equals(route)
                || previous.family != family || previous.consumed) {
                throw new IllegalArgumentException("Market request identifier has conflicting semantics.");
            }
            return;
        }
        long sequence = Math.incrementExact(nextSequence);
        nextSequence = sequence;
        Request next = new Request(module, route, family, sequence, false);
        requests.put(id, next);
        latestSequences.put(new RouteKey(module, route, family), sequence);
        trim();
    }

    public synchronized Decision accept(UUID requestId, MarketModule module, UUID routeNonce) {
        return accept(requestId, module, routeNonce, MarketResponseFamily.CONTENT);
    }

    public synchronized Decision accept(
        UUID requestId,
        MarketModule module,
        UUID routeNonce,
        MarketResponseFamily family
    ) {
        if (!open) {
            return Decision.CLOSED;
        }
        UUID id = requireId(requestId, "request identifier");
        UUID route = requireId(routeNonce, "route nonce");
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(family, "family");
        Request request = requests.get(id);
        if (request == null || request.module != module
            || !request.routeNonce.equals(route) || request.family != family) {
            return Decision.UNKNOWN_REQUEST;
        }
        if (request.consumed) {
            return Decision.DUPLICATE_RESPONSE;
        }
        requests.put(id, request.asConsumed());
        if (!currentRouteNonce.equals(route)) {
            return Decision.STALE_ROUTE;
        }
        Long latest = latestSequences.get(new RouteKey(module, route, family));
        if (latest == null || request.sequence != latest) {
            return Decision.STALE_REQUEST;
        }
        return Decision.ACCEPT;
    }

    public synchronized void close() {
        open = false;
        requests.clear();
        latestSequences.clear();
        retiredRouteNonces.clear();
    }

    private void trim() {
        while (requests.size() > maximumTrackedRequests) {
            Map.Entry<UUID, Request> eldest = requests.entrySet().iterator().next();
            requests.remove(eldest.getKey());
        }
        latestSequences.entrySet().removeIf(entry -> requests.values().stream()
            .noneMatch(request -> request.module == entry.getKey().module
                && request.routeNonce.equals(entry.getKey().routeNonce)
                && request.family == entry.getKey().family
                && request.sequence == entry.getValue()));
    }

    private void requireOpen() {
        if (!open) {
            throw new IllegalStateException("Market request gate is closed.");
        }
    }

    private static UUID requireId(UUID value, String label) {
        if (value == null || value.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("Market " + label + " is required.");
        }
        return value;
    }

    private record Request(
        MarketModule module,
        UUID routeNonce,
        MarketResponseFamily family,
        long sequence,
        boolean consumed
    ) {
        private Request asConsumed() {
            return new Request(module, routeNonce, family, sequence, true);
        }
    }

    private record RouteKey(
        MarketModule module,
        UUID routeNonce,
        MarketResponseFamily family
    ) {
        private RouteKey {
            Objects.requireNonNull(module, "module");
            Objects.requireNonNull(routeNonce, "routeNonce");
            Objects.requireNonNull(family, "family");
        }
    }
}
