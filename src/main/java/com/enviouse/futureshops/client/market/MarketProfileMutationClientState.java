package com.enviouse.futureshops.client.market;

import com.enviouse.futureshops.server.market.profile.MarketProfileMutationCommand;
import com.enviouse.futureshops.server.market.profile.MarketProfileMutationResult;

import java.util.Optional;

public final class MarketProfileMutationClientState {
    public static final int DEFAULT_TRACKED_REQUESTS = 128;

    private static final MarketProfileMutationResponseTracker RESPONSES =
            new MarketProfileMutationResponseTracker(
                    DEFAULT_TRACKED_REQUESTS);

    private MarketProfileMutationClientState() {
    }

    public static void begin(MarketProfileMutationCommand command) {
        RESPONSES.begin(command);
    }

    public static MarketProfileMutationResponseTracker.Decision accept(
            MarketProfileMutationResult result
    ) {
        return RESPONSES.accept(result);
    }

    public static Optional<MarketProfileMutationResult> latest() {
        return RESPONSES.latest();
    }

    public static void clear() {
        RESPONSES.clear();
    }
}
