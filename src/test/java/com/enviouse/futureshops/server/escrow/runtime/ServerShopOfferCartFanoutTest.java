package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.catalog.offer.OfferSchedule;
import com.enviouse.futureshops.catalog.offer.OfferStockPolicy;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.server.transaction
        .ServerShopOfferIntentFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerShopOfferCartFanoutTest {
    @Test
    void aggregateCartFanoutAcceptsBoundaryAndRejectsOverflow() {
        List<ServerShopOfferIntentFactory.AcquireLine> boundary =
                List.of(
                        line("one", "minecraft:iron_sword"),
                        line("two", "minecraft:iron_pickaxe"),
                        line("three", "minecraft:iron_shovel"));
        assertTrue(ServerShopOfferCartService.escrowFanoutFits(
                boundary, ignored -> 1));

        List<ServerShopOfferIntentFactory.AcquireLine> oversized =
                new java.util.ArrayList<>(boundary);
        oversized.add(line("four", "minecraft:iron_hoe"));
        assertFalse(ServerShopOfferCartService.escrowFanoutFits(
                oversized, ignored -> 1));
    }

    private static ServerShopOfferIntentFactory.AcquireLine line(
            String id,
            String itemId
    ) {
        AcquireOfferOption option = AcquireOfferOption.money(
                "money", 1L);
        ServerShopOfferListing listing = new ServerShopOfferListing(
                id, 0L, id, "", "all", itemId, "",
                true, 0L, "",
                List.of(new OfferItemComponent(
                        "output", itemId, 1, "")),
                List.of(option), List.of(),
                OfferStockPolicy.unlimited(),
                OfferLimitPolicy.defaults(),
                OfferSchedule.always(), List.of());
        return new ServerShopOfferIntentFactory.AcquireLine(
                listing, option, 2_304, 2_304L);
    }
}
