package com.enviouse.futureshops.client.market;

import com.enviouse.futureshops.server.market.claim.MarketClaimCollectionCode;
import com.enviouse.futureshops.server.market.claim.MarketClaimCollectionCommand;
import com.enviouse.futureshops.server.market.claim.MarketClaimCollectionResult;
import com.enviouse.futureshops.server.market.claim.MarketClaimPresentationKind;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketClaimCollectionResponseTrackerTest {
    @Test
    void responsesAreCorrelatedAndProgressCanBeRetried() {
        MarketClaimCollectionResponseTracker tracker =
                new MarketClaimCollectionResponseTracker(4);
        MarketClaimCollectionCommand command = command();
        tracker.begin(command);

        MarketClaimCollectionResult full = result(command,
                MarketClaimCollectionCode.INVENTORY_FULL, 0L, 4L,
                false);
        assertEquals(MarketClaimCollectionResponseTracker.Decision.ACCEPT,
                tracker.accept(full));
        assertEquals(MarketClaimCollectionResponseTracker.Decision
                        .DUPLICATE_RESPONSE,
                tracker.accept(full));

        MarketClaimCollectionResult partial = result(command,
                MarketClaimCollectionCode.PARTIALLY_COLLECTED,
                2L, 2L, false);
        assertEquals(MarketClaimCollectionResponseTracker.Decision.ACCEPT,
                tracker.accept(partial));
        MarketClaimCollectionResult completed = result(command,
                MarketClaimCollectionCode.COLLECTED, 2L, 0L, true);
        assertEquals(MarketClaimCollectionResponseTracker.Decision.ACCEPT,
                tracker.accept(completed));
        assertEquals(completed, tracker.latest().orElseThrow());

        assertEquals(MarketClaimCollectionResponseTracker.Decision
                        .TERMINAL_CONFLICT,
                tracker.accept(partial));
    }

    @Test
    void mismatchesRegressionsAndUnknownRequestsAreRejected() {
        MarketClaimCollectionResponseTracker tracker =
                new MarketClaimCollectionResponseTracker(2);
        MarketClaimCollectionCommand command = command();
        tracker.begin(command);
        assertEquals(MarketClaimCollectionResponseTracker.Decision.ACCEPT,
                tracker.accept(result(command,
                        MarketClaimCollectionCode.INVENTORY_FULL,
                        0L, 2L, false)));
        assertEquals(MarketClaimCollectionResponseTracker.Decision
                        .STATE_REGRESSION,
                tracker.accept(result(command,
                        MarketClaimCollectionCode.INVENTORY_FULL,
                        0L, 3L, false)));

        MarketClaimCollectionCommand other = command();
        assertEquals(MarketClaimCollectionResponseTracker.Decision
                        .UNKNOWN_REQUEST,
                tracker.accept(result(other,
                        MarketClaimCollectionCode.INVENTORY_FULL,
                        0L, 1L, false)));

        assertThrows(IllegalArgumentException.class, () ->
                tracker.begin(new MarketClaimCollectionCommand(
                        command.requestId(), command.routeNonce(),
                        command.module(), command.view(),
                        UUID.randomUUID())));
    }

    @Test
    void trackingIsBounded() {
        MarketClaimCollectionResponseTracker tracker =
                new MarketClaimCollectionResponseTracker(2);
        MarketClaimCollectionCommand first = command();
        MarketClaimCollectionCommand second = command();
        MarketClaimCollectionCommand third = command();
        tracker.begin(first);
        tracker.begin(second);
        tracker.begin(third);
        assertEquals(2, tracker.trackedRequestCount());
        assertEquals(MarketClaimCollectionResponseTracker.Decision
                        .UNKNOWN_REQUEST,
                tracker.accept(result(first,
                        MarketClaimCollectionCode.INVENTORY_FULL,
                        0L, 1L, false)));
    }

    private static MarketClaimCollectionCommand command() {
        return new MarketClaimCollectionCommand(UUID.randomUUID(),
                UUID.randomUUID(), MarketModule.BAZAAR, "claims",
                UUID.randomUUID());
    }

    private static MarketClaimCollectionResult result(
            MarketClaimCollectionCommand command,
            MarketClaimCollectionCode code,
            long delivered,
            long remaining,
            boolean replayed
    ) {
        return new MarketClaimCollectionResult(command.requestId(),
                command.routeNonce(), command.module(), command.view(),
                command.claimId(), MarketClaimPresentationKind.ITEM,
                code, delivered, remaining, OptionalLong.empty(),
                replayed, code.refreshClaims());
    }
}
