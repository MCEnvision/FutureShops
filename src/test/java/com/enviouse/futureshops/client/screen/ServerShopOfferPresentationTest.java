package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.catalog.offer.OfferSchedule;
import com.enviouse.futureshops.catalog.offer.OfferStockPolicy;
import com.enviouse.futureshops.catalog.offer.SellOfferOption;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerShopOfferPresentationTest {
    @BeforeAll
    static void initializeMinecraftRegistries() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void editorAndVisitorProjectionContainsEveryTradeDirection() {
        ServerShopOfferPresentation.Projection projection =
                ServerShopOfferPresentation.project(
                        offer(),
                        ServerShopOfferPresentation.PreviewState.ACTIVE,
                        "Coins", Map.of());

        assertEquals(1, projection.acquireRows().size());
        assertEquals(1, projection.sellRows().size());
        assertFalse(projection.sellOnly());
        assertTrue(projection.acquireRows().get(0)
                .getString().contains("Coins"));
        assertTrue(projection.sellRows().get(0)
                .getString().contains("Coins"));
    }

    @Test
    void allVisitorStatusStatesAreReachableWithoutMutation() {
        ServerShopOfferListing offer = offer();
        ServerShopOfferPresentation.PreviewState state =
                ServerShopOfferPresentation.PreviewState.ACTIVE;

        for (int index = 0;
             index < ServerShopOfferPresentation.PreviewState
                     .values().length; index++) {
            ServerShopOfferPresentation.Projection projection =
                    ServerShopOfferPresentation.project(
                            offer, state, "Coins", Map.of());
            assertEquals(offer.displayName(),
                    projection.title().getString());
            state = state.next();
        }

        assertEquals(ServerShopOfferPresentation.PreviewState.ACTIVE,
                state);
        assertEquals("Both Directions", offer.displayName());
    }

    @Test
    void quantityAwareSummariesUseCheckedArithmetic() {
        ServerShopOfferListing offer = offer();
        AcquireOfferOption acquire =
                offer.acquireOptions().get(0);
        SellOfferOption sell = offer.sellOptions().get(0);

        String acquireSummary =
                ServerShopOfferPresentation.acquireSummary(
                        acquire, offer, "Coins", 3);
        String sellSummary =
                ServerShopOfferPresentation.sellSummary(
                        sell, "Coins", 3);

        assertTrue(acquireSummary.contains("3.00 Coins"));
        assertTrue(acquireSummary.contains("3 Diamond"));
        assertTrue(sellSummary.contains("6 Iron Ingot"));
        assertTrue(sellSummary.contains("1.50 Coins"));
        AcquireOfferOption overflow = AcquireOfferOption.money(
                "overflow", Long.MAX_VALUE);
        assertThrows(ArithmeticException.class,
                () -> ServerShopOfferPresentation.acquireSummary(
                        overflow, offer, "Coins", 2));
        assertThrows(IllegalArgumentException.class,
                () -> ServerShopOfferPresentation.sellSummary(
                        sell, "Coins", 0));
    }

    private static ServerShopOfferListing offer() {
        OfferItemComponent output = new OfferItemComponent(
                "output", "minecraft:diamond", 1, "");
        OfferItemComponent input = new OfferItemComponent(
                "input", "minecraft:iron_ingot", 2, "");
        AcquireOfferOption acquire = AcquireOfferOption.money(
                "money", 100L);
        SellOfferOption sell = new SellOfferOption(
                "sell", "Sell to Shop", List.of(input),
                50L, 0L, OfferLimitPolicy.defaults(),
                OfferSchedule.always(), "");
        return new ServerShopOfferListing(
                "both", 1L, "Both Directions", "",
                "all", "minecraft:diamond", "", true,
                0L, "", List.of(output), List.of(acquire),
                List.of(sell), OfferStockPolicy.unlimited(),
                OfferLimitPolicy.defaults(), OfferSchedule.always(),
                List.of());
    }
}
