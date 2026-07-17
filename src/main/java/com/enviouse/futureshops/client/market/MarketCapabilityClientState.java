package com.enviouse.futureshops.client.market;

import java.util.Optional;
import java.util.UUID;

public final class MarketCapabilityClientState {
    public static final int DEFAULT_TRACKED_REQUESTS = 128;

    private static final MarketCapabilityResponseTracker RESPONSES =
            new MarketCapabilityResponseTracker(DEFAULT_TRACKED_REQUESTS);

    private MarketCapabilityClientState() {
    }

    public static UUID beginRequest() {
        UUID requestId;
        do {
            requestId = UUID.randomUUID();
        } while (requestId.equals(new UUID(0L, 0L)));
        RESPONSES.begin(requestId);
        return requestId;
    }

    public static void beginRequest(UUID requestId) {
        RESPONSES.begin(requestId);
    }

    public static MarketCapabilityResponseTracker.Decision accept(
            MarketCapabilitiesSnapshot snapshot
    ) {
        return RESPONSES.accept(snapshot);
    }

    public static Optional<MarketCapabilitiesSnapshot> latest() {
        return RESPONSES.latest();
    }

    public static void clear() {
        RESPONSES.clear();
    }
}
