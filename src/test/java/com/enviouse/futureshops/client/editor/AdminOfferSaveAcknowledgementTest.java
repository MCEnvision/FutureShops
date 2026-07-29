package com.enviouse.futureshops.client.editor;

import com.enviouse.futureshops.catalog.AdminShopOfferConfigWriter;
import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.catalog.offer.OfferSchedule;
import com.enviouse.futureshops.catalog.offer.OfferStockPolicy;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.network.packets
        .S2CAdminOfferSaveResultPacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminOfferSaveAcknowledgementTest {
    @Test
    void applyAndSaveCloseHaveDifferentAcknowledgedOutcomes() {
        UUID requestId = UUID.randomUUID();
        S2CAdminOfferSaveResultPacket result = success(
                requestId, Optional.of(listing()));

        assertEquals(
                AdminOfferSaveAcknowledgement.Decision
                        .ACKNOWLEDGED_KEEP_OPEN,
                AdminOfferSaveAcknowledgement.decide(
                        requestId,
                        AdminShopOfferConfigWriter.Operation.UPDATE,
                        false, result));
        assertEquals(
                AdminOfferSaveAcknowledgement.Decision
                        .ACKNOWLEDGED_CLOSE,
                AdminOfferSaveAcknowledgement.decide(
                        requestId,
                        AdminShopOfferConfigWriter.Operation.UPDATE,
                        true, result));
    }

    @Test
    void mismatchedAndStaleResultsNeverAcknowledgeDraft() {
        UUID requestId = UUID.randomUUID();
        S2CAdminOfferSaveResultPacket stale =
                new S2CAdminOfferSaveResultPacket(
                        requestId,
                        AdminShopOfferConfigWriter.Status.STALE,
                        false, listing().revision(),
                        Optional.of(listing()), List.of());

        assertEquals(AdminOfferSaveAcknowledgement.Decision.IGNORED,
                AdminOfferSaveAcknowledgement.decide(
                        UUID.randomUUID(),
                        AdminShopOfferConfigWriter.Operation.UPDATE,
                        true, stale));
        assertEquals(AdminOfferSaveAcknowledgement.Decision.STALE,
                AdminOfferSaveAcknowledgement.decide(
                        requestId,
                        AdminShopOfferConfigWriter.Operation.UPDATE,
                        true, stale));
    }

    @Test
    void removeRequiresMatchingSuccessfulAcknowledgement() {
        UUID requestId = UUID.randomUUID();

        assertEquals(AdminOfferSaveAcknowledgement.Decision.REMOVED,
                AdminOfferSaveAcknowledgement.decide(
                        requestId,
                        AdminShopOfferConfigWriter.Operation.REMOVE,
                        false, success(requestId, Optional.empty())));
    }

    private static S2CAdminOfferSaveResultPacket success(
            UUID requestId,
            Optional<ServerShopOfferListing> snapshot
    ) {
        return new S2CAdminOfferSaveResultPacket(
                requestId, AdminShopOfferConfigWriter.Status.SUCCESS,
                true, snapshot.map(ServerShopOfferListing::revision)
                .orElse(0L), snapshot, List.of());
    }

    private static ServerShopOfferListing listing() {
        return new ServerShopOfferListing(
                "listing", 1L, "Listing", "", "all",
                "minecraft:stone", "", true, 0L, "",
                List.of(new OfferItemComponent(
                        "output", "minecraft:stone", 1, "")),
                List.of(AcquireOfferOption.money("money", 1L)),
                List.of(), OfferStockPolicy.unlimited(),
                OfferLimitPolicy.defaults(), OfferSchedule.always(),
                List.of());
    }
}
