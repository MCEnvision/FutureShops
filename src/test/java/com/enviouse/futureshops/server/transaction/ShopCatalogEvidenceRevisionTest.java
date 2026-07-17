package com.enviouse.futureshops.server.transaction;

import com.enviouse.futureshops.catalog.BarterIngredientDef;
import com.enviouse.futureshops.catalog.BarterRecipeDef;
import com.enviouse.futureshops.catalog.ItemDef;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopBarterCommit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopCatalogEvidenceRevisionTest {
    @Test
    void equivalentCatalogEvidenceHasAStableBoundedRevision() {
        ItemDef first = item(100L);
        ItemDef second = item(100L);

        long revision = ShopCatalogEvidenceRevision.item(first);

        assertEquals(revision,
                ShopCatalogEvidenceRevision.item(second));
        assertTrue(revision >= 0L);
        assertTrue(revision <= ServerShopBarterCommit.MAX_REVISION);
    }

    @Test
    void priceNbtAndRecipeChangesMoveTheirRevisions() {
        ItemDef first = item(100L);
        ItemDef priceChanged = item(101L);
        ItemDef nbtChanged = new ItemDef("diamond.offer",
                "minecraft:diamond", "Diamond", 200L, 100L,
                20, true, "materials", 0, "{quality:2}", 0L);
        BarterRecipeDef recipe = new BarterRecipeDef("trade",
                "diamond.offer", 1, List.of(
                new BarterIngredientDef("minecraft:emerald", 2, "")));
        BarterRecipeDef changed = new BarterRecipeDef("trade",
                "diamond.offer", 1, List.of(
                new BarterIngredientDef("minecraft:emerald", 3, "")));

        assertNotEquals(ShopCatalogEvidenceRevision.item(first),
                ShopCatalogEvidenceRevision.item(priceChanged));
        assertNotEquals(ShopCatalogEvidenceRevision.item(first),
                ShopCatalogEvidenceRevision.item(nbtChanged));
        assertNotEquals(ShopCatalogEvidenceRevision.barter(recipe, first),
                ShopCatalogEvidenceRevision.barter(changed, first));
    }

    private static ItemDef item(long sellPrice) {
        return new ItemDef("diamond.offer", "minecraft:diamond",
                "Diamond", 200L, sellPrice, 20, true,
                "materials", 0, "{quality:1}", 0L);
    }
}
