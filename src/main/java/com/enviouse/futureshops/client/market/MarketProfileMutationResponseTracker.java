package com.enviouse.futureshops.client.market;

import com.enviouse.futureshops.server.market.profile.MarketProfileMutationCommand;
import com.enviouse.futureshops.server.market.profile.MarketProfileMutationResult;
import com.enviouse.futureshops.server.market.profile.MarketProfileMutationType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MarketProfileMutationResponseTracker {
    public static final int MAXIMUM_TRACKED_REQUESTS = 4096;

    private final int maximumTrackedRequests;
    private final LinkedHashMap<UUID, Request> requests =
            new LinkedHashMap<>();
    private MarketProfileMutationResult latest;

    public MarketProfileMutationResponseTracker(
            int maximumTrackedRequests
    ) {
        if (maximumTrackedRequests <= 0
                || maximumTrackedRequests > MAXIMUM_TRACKED_REQUESTS) {
            throw new IllegalArgumentException(
                    "Market profile tracking limit is invalid");
        }
        this.maximumTrackedRequests = maximumTrackedRequests;
    }

    public synchronized void begin(
            MarketProfileMutationCommand command
    ) {
        MarketProfileMutationCommand value = Objects.requireNonNull(
                command, "command");
        Request request = new Request(value.routeNonce(),
                value.module(), value.mutation().type(),
                value.fingerprint(), false);
        Request existing = requests.get(value.requestId());
        if (existing != null) {
            if (!existing.sameRequest(request) || existing.consumed()) {
                throw new IllegalArgumentException(
                        "Market profile request identity was reused");
            }
            return;
        }
        requests.put(value.requestId(), request);
        trim();
    }

    public synchronized Decision accept(
            MarketProfileMutationResult result
    ) {
        MarketProfileMutationResult value = Objects.requireNonNull(
                result, "result");
        Request request = requests.get(value.requestId());
        if (request == null) {
            return Decision.UNKNOWN_REQUEST;
        }
        if (request.consumed()) {
            return Decision.DUPLICATE_RESPONSE;
        }
        if (!request.routeNonce().equals(value.routeNonce())
                || request.module() != value.module()
                || request.type() != value.type()) {
            return Decision.CORRELATION_MISMATCH;
        }
        requests.put(value.requestId(), request.consumedCopy());
        if (latest != null
                && value.profileRevision() < latest.profileRevision()) {
            return Decision.STALE_REVISION;
        }
        if (latest != null
                && value.profileRevision() == latest.profileRevision()
                && !sameCounts(value, latest)) {
            return Decision.REVISION_CONFLICT;
        }
        latest = value;
        return Decision.ACCEPT;
    }

    public synchronized Optional<MarketProfileMutationResult> latest() {
        return Optional.ofNullable(latest);
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

    private static boolean sameCounts(
            MarketProfileMutationResult first,
            MarketProfileMutationResult second
    ) {
        return first.watchedAuctionCount()
                == second.watchedAuctionCount()
                && first.favoriteProductCount()
                == second.favoriteProductCount()
                && first.priceAlertCount()
                == second.priceAlertCount()
                && first.notificationCount()
                == second.notificationCount()
                && first.unreadNotificationCount()
                == second.unreadNotificationCount();
    }

    public enum Decision {
        ACCEPT,
        UNKNOWN_REQUEST,
        DUPLICATE_RESPONSE,
        CORRELATION_MISMATCH,
        STALE_REVISION,
        REVISION_CONFLICT
    }

    private record Request(
            UUID routeNonce,
            MarketModule module,
            MarketProfileMutationType type,
            String fingerprint,
            boolean consumed
    ) {
        private Request {
            routeNonce = Objects.requireNonNull(routeNonce,
                    "routeNonce");
            module = Objects.requireNonNull(module, "module");
            type = Objects.requireNonNull(type, "type");
            fingerprint = Objects.requireNonNull(fingerprint,
                    "fingerprint");
        }

        private boolean sameRequest(Request other) {
            return routeNonce.equals(other.routeNonce)
                    && module == other.module && type == other.type
                    && fingerprint.equals(other.fingerprint);
        }

        private Request consumedCopy() {
            return new Request(routeNonce, module, type, fingerprint,
                    true);
        }
    }
}
