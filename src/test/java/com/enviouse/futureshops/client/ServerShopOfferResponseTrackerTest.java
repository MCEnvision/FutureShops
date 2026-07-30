package com.enviouse.futureshops.client;

import com.enviouse.futureshops.catalog.offer.OfferAction;
import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.network.packets.C2SServerShopOfferPacket;
import com.enviouse.futureshops.network.packets.S2CServerShopOfferResultPacket;
import com.enviouse.futureshops.server.escrow.runtime
        .ServerShopOfferService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerShopOfferResponseTrackerTest {
    @Test
    void recoveryRetryKeepsOriginalRequestIdentity() {
        ServerShopOfferResponseTracker tracker =
                new ServerShopOfferResponseTracker();
        C2SServerShopOfferPacket original = begin(tracker);

        assertTrue(tracker.accept(result(
                original,
                ServerShopOfferService.Status.RECOVERY_REQUIRED)));
        assertTrue(tracker.pending().isPresent());

        C2SServerShopOfferPacket retry = begin(tracker);

        assertEquals(original.requestId(), retry.requestId());
        assertEquals(original, retry);
        assertFalse(tracker.lastResult().isPresent());
    }

    @Test
    void terminalResponseClearsIdentityForNextRequest() {
        ServerShopOfferResponseTracker tracker =
                new ServerShopOfferResponseTracker();
        C2SServerShopOfferPacket original = begin(tracker);

        assertTrue(tracker.accept(result(
                original, ServerShopOfferService.Status.SUCCESS)));
        assertFalse(tracker.pending().isPresent());

        C2SServerShopOfferPacket next = begin(tracker);

        assertNotEquals(original.requestId(), next.requestId());
    }

    private static C2SServerShopOfferPacket begin(
            ServerShopOfferResponseTracker tracker
    ) {
        return tracker.begin(
                "default", "iron", "money",
                OfferAction.ACQUIRE_FROM_SHOP, 2, 7L,
                Optional.of(PaymentSource.WALLET));
    }

    private static S2CServerShopOfferResultPacket result(
            C2SServerShopOfferPacket request,
            ServerShopOfferService.Status status
    ) {
        return new S2CServerShopOfferResultPacket(
                request.requestId(), request.shopId(),
                request.listingId(), request.optionId(),
                status, 100L, false);
    }
}
