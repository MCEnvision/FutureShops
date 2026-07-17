package com.enviouse.futureshops.client.market;

import com.enviouse.futureshops.server.market.claim.MarketClaimCollectionCommand;
import com.enviouse.futureshops.server.market.claim.MarketClaimCollectionResult;

import java.util.Optional;

public final class MarketClaimCollectionClientState {
    public static final int DEFAULT_TRACKED_REQUESTS = 128;

    private static final MarketClaimCollectionResponseTracker RESPONSES =
            new MarketClaimCollectionResponseTracker(
                    DEFAULT_TRACKED_REQUESTS);

    private MarketClaimCollectionClientState() {
    }

    public static void begin(MarketClaimCollectionCommand command) {
        RESPONSES.begin(command);
    }

    public static MarketClaimCollectionResponseTracker.Decision accept(
            MarketClaimCollectionResult result
    ) {
        return RESPONSES.accept(result);
    }

    public static Optional<MarketClaimCollectionResult> latest() {
        return RESPONSES.latest();
    }

    public static void clear() {
        RESPONSES.clear();
    }
}
