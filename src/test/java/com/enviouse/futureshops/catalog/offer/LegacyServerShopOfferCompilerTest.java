package com.enviouse.futureshops.catalog.offer;

import com.enviouse.futureshops.catalog.BarterIngredientDef;
import com.enviouse.futureshops.catalog.BarterRecipeDef;
import com.enviouse.futureshops.catalog.ItemDef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyServerShopOfferCompilerTest {
    @Test
    void convertsMoneyBarterAndSellWithoutMakingZeroFree() {
        ItemDef item = new ItemDef("diamond_offer", "minecraft:diamond",
                "Diamond", 500L, 250L, 12, true, "materials",
                60, "", 0L);
        BarterRecipeDef recipe = new BarterRecipeDef("iron_trade",
                "diamond_offer", 2, List.of(
                new BarterIngredientDef("minecraft:iron_ingot", 4),
                new BarterIngredientDef("minecraft:stick", 1)));
        ServerShopOfferListing offer =
                LegacyServerShopOfferCompiler.compile(item, List.of(recipe));

        assertEquals("diamond_offer", offer.listingId());
        assertEquals(2, offer.acquireOptions().size());
        assertEquals(1, offer.sellOptions().size());
        assertFalse(offer.acquireOptions().stream()
                .anyMatch(AcquireOfferOption::free));
        AcquireOfferOption barter = offer.acquireOptions().get(1);
        assertEquals(2, barter.itemCosts().size());
        assertEquals(2, barter.outputMultiplier());
        assertEquals(12L, offer.stockPolicy().quantity());
        assertEquals(60L, offer.stockPolicy().refreshSeconds());
        assertTrue(ServerShopOfferValidator.validate(offer).valid());
    }

    @Test
    void inertLegacyListingIsExcludedFromCatalogCompilation() {
        ItemDef inert = new ItemDef("minecraft:barrier", "Barrier",
                0L, 0L, -1, false, "all");
        assertTrue(LegacyServerShopOfferCompiler.compile(
                List.of(inert), List.of()).isEmpty());
    }

    @Test
    void normalizerMergesEquivalentComponentsWithCheckedArithmetic() {
        List<OfferItemComponent> normalized =
                OfferComponentNormalizer.normalize(List.of(
                        new OfferItemComponent("first",
                                "minecraft:iron_ingot", 2, ""),
                        new OfferItemComponent("second",
                                "minecraft:iron_ingot", 3, "")));
        assertEquals(1, normalized.size());
        assertEquals("first", normalized.get(0).componentId());
        assertEquals(5, normalized.get(0).count());
    }
}
