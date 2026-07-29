package com.enviouse.futureshops.client.editor;

import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.catalog.offer.OfferSchedule;
import com.enviouse.futureshops.catalog.offer.OfferStockPolicy;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferEditorStaleReviewTest {
    @Test
    void comparesLocalAndServerValuesWithoutMutatingEitherSnapshot() {
        ServerShopOfferListing local = listing(
                10L, "Local name", 2);
        ServerShopOfferListing server = listing(
                11L, "Server name", 4);

        List<OfferEditorStaleReview.Change> changes =
                OfferEditorStaleReview.compare(local, server);

        assertEquals(List.of("revision", "displayName", "outputs"),
                changes.stream()
                        .map(OfferEditorStaleReview.Change::path)
                        .toList());
        assertEquals("Local name", local.displayName());
        assertEquals("Server name", server.displayName());
        assertTrue(changes.stream().anyMatch(change ->
                change.path().equals("displayName")
                        && change.localValue().equals("Local name")
                        && change.serverValue().equals("Server name")));
    }

    private static ServerShopOfferListing listing(
            long revision,
            String name,
            int outputCount
    ) {
        return new ServerShopOfferListing(
                "test_offer", revision, name, "", "all",
                "minecraft:stone", "", true, 0L, "",
                List.of(new OfferItemComponent(
                        "output", "minecraft:stone", outputCount, "")),
                List.of(AcquireOfferOption.money("money", 100L)),
                List.of(), OfferStockPolicy.unlimited(),
                OfferLimitPolicy.defaults(), OfferSchedule.always(),
                List.of());
    }
}
