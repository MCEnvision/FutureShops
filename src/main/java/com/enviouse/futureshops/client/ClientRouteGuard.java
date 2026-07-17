package com.enviouse.futureshops.client;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

public final class ClientRouteGuard {
    public enum ResponseDecision {
        UNTRACKED,
        ACCEPT,
        REJECT
    }

    private static final int DEFAULT_MAXIMUM_STOREFRONT_REQUESTS = 32;
    private static final long DEFAULT_ACTIVE_NANOS = Duration.ofSeconds(15).toNanos();
    private static final long DEFAULT_RETENTION_NANOS = Duration.ofMinutes(2).toNanos();
    private static final ClientRouteGuard LIVE = new ClientRouteGuard(
            System::nanoTime,
            DEFAULT_ACTIVE_NANOS,
            DEFAULT_RETENTION_NANOS,
            DEFAULT_MAXIMUM_STOREFRONT_REQUESTS);

    private final LongSupplier clock;
    private final long activeNanos;
    private final long retentionNanos;
    private final int maximumStorefrontRequests;
    private final LinkedHashMap<Long, Request> storefrontRequests = new LinkedHashMap<>();
    private Request atmRequest;

    ClientRouteGuard(LongSupplier clock, long activeNanos, long retentionNanos,
                     int maximumStorefrontRequests) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (activeNanos <= 0L || retentionNanos < activeNanos
                || maximumStorefrontRequests <= 0) {
            throw new IllegalArgumentException("Client route guard limits are invalid");
        }
        this.activeNanos = activeNanos;
        this.retentionNanos = retentionNanos;
        this.maximumStorefrontRequests = maximumStorefrontRequests;
    }

    public static void expectStorefront(Object origin, long shopPosition) {
        LIVE.recordStorefront(origin, shopPosition);
    }

    public static ResponseDecision acceptStorefrontResponse(Object currentScreen,
                                                              long shopPosition) {
        return LIVE.evaluateStorefront(currentScreen, shopPosition);
    }

    public static void expectAtm(Object origin) {
        LIVE.recordAtm(origin);
    }

    public static ResponseDecision acceptAtmResponse(Object currentScreen) {
        return LIVE.evaluateAtm(currentScreen);
    }

    public static boolean allowsAtmOpen(ResponseDecision decision, boolean serverOpenIntent,
                                        boolean noCompetingScreen) {
        Objects.requireNonNull(decision, "decision");
        return decision == ResponseDecision.ACCEPT
                || decision == ResponseDecision.UNTRACKED
                && serverOpenIntent
                && noCompetingScreen;
    }

    public static void cancelFor(Object origin) {
        LIVE.cancelOrigin(origin);
    }

    synchronized void recordStorefront(Object origin, long shopPosition) {
        long now = clock.getAsLong();
        purge(now);
        cancelPending(now);
        storefrontRequests.put(shopPosition, Request.pending(origin, now, activeNanos, retentionNanos));
        trimStorefrontRequests();
    }

    synchronized ResponseDecision evaluateStorefront(Object currentScreen, long shopPosition) {
        long now = clock.getAsLong();
        purge(now);
        Request request = storefrontRequests.get(shopPosition);
        if (request == null) {
            return ResponseDecision.UNTRACKED;
        }
        ResponseDecision decision = request.accepts(currentScreen, now)
                ? ResponseDecision.ACCEPT
                : ResponseDecision.REJECT;
        storefrontRequests.put(shopPosition, request.consumed(now, retentionNanos));
        return decision;
    }

    synchronized void recordAtm(Object origin) {
        long now = clock.getAsLong();
        purge(now);
        cancelPending(now);
        atmRequest = Request.pending(origin, now, activeNanos, retentionNanos);
    }

    synchronized ResponseDecision evaluateAtm(Object currentScreen) {
        long now = clock.getAsLong();
        purge(now);
        if (atmRequest == null) {
            return ResponseDecision.UNTRACKED;
        }
        ResponseDecision decision = atmRequest.accepts(currentScreen, now)
                ? ResponseDecision.ACCEPT
                : ResponseDecision.REJECT;
        atmRequest = null;
        return decision;
    }

    synchronized void cancelOrigin(Object origin) {
        if (origin == null) {
            return;
        }
        long now = clock.getAsLong();
        purge(now);
        storefrontRequests.replaceAll((ignored, request) -> request.origin == origin
                ? request.cancelled(now, retentionNanos)
                : request);
        if (atmRequest != null && atmRequest.origin == origin) {
            atmRequest = null;
        }
    }

    private void cancelPending(long now) {
        storefrontRequests.replaceAll((ignored, request) -> request.pending
                ? request.cancelled(now, retentionNanos)
                : request);
        if (atmRequest != null && atmRequest.pending) {
            atmRequest = null;
        }
    }

    private void purge(long now) {
        storefrontRequests.entrySet().removeIf(entry -> now > entry.getValue().retainUntilNanos);
        if (atmRequest != null && now > atmRequest.retainUntilNanos) {
            atmRequest = null;
        }
    }

    private void trimStorefrontRequests() {
        Iterator<Map.Entry<Long, Request>> iterator = storefrontRequests.entrySet().iterator();
        while (storefrontRequests.size() > maximumStorefrontRequests && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static final class Request {
        private final Object origin;
        private final long acceptUntilNanos;
        private final long retainUntilNanos;
        private final boolean pending;

        private Request(Object origin, long acceptUntilNanos, long retainUntilNanos,
                        boolean pending) {
            this.origin = origin;
            this.acceptUntilNanos = acceptUntilNanos;
            this.retainUntilNanos = retainUntilNanos;
            this.pending = pending;
        }

        private static Request pending(Object origin, long now, long activeNanos,
                                       long retentionNanos) {
            return new Request(Objects.requireNonNull(origin, "origin"),
                    Math.addExact(now, activeNanos), Math.addExact(now, retentionNanos), true);
        }

        private boolean accepts(Object currentScreen, long now) {
            return pending && currentScreen == origin && now <= acceptUntilNanos;
        }

        private Request consumed(long now, long retentionNanos) {
            return new Request(origin, acceptUntilNanos,
                    Math.addExact(now, retentionNanos), false);
        }

        private Request cancelled(long now, long retentionNanos) {
            return consumed(now, retentionNanos);
        }
    }
}
