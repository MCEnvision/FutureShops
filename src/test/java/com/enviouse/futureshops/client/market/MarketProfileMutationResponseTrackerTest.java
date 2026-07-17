package com.enviouse.futureshops.client.market;

import com.enviouse.futureshops.server.market.profile.MarketProfileMutation;
import com.enviouse.futureshops.server.market.profile.MarketProfileMutationCommand;
import com.enviouse.futureshops.server.market.profile.MarketProfileMutationResult;
import com.enviouse.futureshops.server.market.profile.MarketProfileMutationResultCode;
import com.enviouse.futureshops.server.market.profile.MarketProfileSavedData;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketProfileMutationResponseTrackerTest {
    private static final MarketProfileSavedData.ProductKey PRODUCT =
            new MarketProfileSavedData.ProductKey(
                    "minecraft:emerald", 1L);

    @Test
    void onlyExactlyCorrelatedResponseIsAccepted() {
        MarketProfileMutationResponseTracker tracker =
                new MarketProfileMutationResponseTracker(8);
        MarketProfileMutationCommand command = command(
                UUID.randomUUID(), UUID.randomUUID(), true);
        tracker.begin(command);
        MarketProfileMutationResult response = result(command, 1L);

        MarketProfileMutationResult wrongRoute =
                new MarketProfileMutationResult(response.requestId(),
                        UUID.randomUUID(), response.module(),
                        response.type(), response.resultCode(),
                        response.profileRevision(), 0, 1, 0, 0, 0,
                        1, true, false);
        assertEquals(MarketProfileMutationResponseTracker.Decision
                        .CORRELATION_MISMATCH,
                tracker.accept(wrongRoute));
        assertEquals(MarketProfileMutationResponseTracker.Decision.ACCEPT,
                tracker.accept(response));
        assertEquals(MarketProfileMutationResponseTracker.Decision
                        .DUPLICATE_RESPONSE,
                tracker.accept(response));
        assertEquals(response, tracker.latest().orElseThrow());
    }

    @Test
    void requestIdentityReuseAndUnknownResponsesFailClosed() {
        MarketProfileMutationResponseTracker tracker =
                new MarketProfileMutationResponseTracker(2);
        UUID request = UUID.randomUUID();
        UUID route = UUID.randomUUID();
        MarketProfileMutationCommand command = command(request, route,
                true);
        tracker.begin(command);
        tracker.begin(command);
        assertThrows(IllegalArgumentException.class, () ->
                tracker.begin(command(request, route, false)));
        assertEquals(MarketProfileMutationResponseTracker.Decision
                        .UNKNOWN_REQUEST,
                tracker.accept(result(command(UUID.randomUUID(), route,
                        true), 0L)));
    }

    @Test
    void revisionAndRequestTrackingAreBounded() {
        MarketProfileMutationResponseTracker tracker =
                new MarketProfileMutationResponseTracker(2);
        MarketProfileMutationCommand first = command(
                UUID.randomUUID(), UUID.randomUUID(), true);
        MarketProfileMutationCommand second = command(
                UUID.randomUUID(), first.routeNonce(), true);
        MarketProfileMutationCommand third = command(
                UUID.randomUUID(), first.routeNonce(), true);
        tracker.begin(first);
        assertEquals(MarketProfileMutationResponseTracker.Decision.ACCEPT,
                tracker.accept(result(first, 3L)));
        tracker.begin(second);
        assertEquals(MarketProfileMutationResponseTracker.Decision
                        .STALE_REVISION,
                tracker.accept(result(second, 2L)));
        tracker.begin(third);

        assertEquals(2, tracker.trackedRequestCount());
        assertEquals(MarketProfileMutationResponseTracker.Decision
                        .UNKNOWN_REQUEST,
                tracker.accept(result(first, 3L)));
    }

    private static MarketProfileMutationCommand command(
            UUID request,
            UUID route,
            boolean favorite
    ) {
        return new MarketProfileMutationCommand(request, route,
                MarketModule.BAZAAR, "products", 0L,
                new MarketProfileMutation.BazaarFavorite(
                        PRODUCT, favorite));
    }

    private static MarketProfileMutationResult result(
            MarketProfileMutationCommand command,
            long revision
    ) {
        return new MarketProfileMutationResult(command.requestId(),
                command.routeNonce(), command.module(),
                command.mutation().type(),
                MarketProfileMutationResultCode.SUCCESS, revision,
                0, 1, 0, 0, 0, 1, true, false);
    }
}
