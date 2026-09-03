package com.enviouse.futureshopsp.server.shop;

import com.enviouse.futureshopsp.block.ShopBlockEntity;
import net.minecraft.core.component.DataComponentPatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShopEntitlementTest {
    @Test
    void checkedDeliveryCountRejectsInvalidAndOverflowValues() {
        assertEquals(-1, PlayerShopBlockService.checkedDeliveryCount(0, 1));
        assertEquals(-1, PlayerShopBlockService.checkedDeliveryCount(1, 0));
        assertEquals(-1, PlayerShopBlockService.checkedDeliveryCount(Integer.MAX_VALUE, 2));
        assertEquals(12, PlayerShopBlockService.checkedDeliveryCount(3, 4));
    }

    @Test
    void bundleEntitlementCountsEveryOutputAndHashesThePlan() {
        ShopBlockEntity.Listing listing = new ShopBlockEntity.Listing("minecraft:chest");
        listing.addBundleOutput("minecraft:diamond", 2, DataComponentPatch.EMPTY);
        listing.addBundleOutput("minecraft:emerald", 4, DataComponentPatch.EMPTY);

        assertEquals(18L, PlayerShopBlockService.deliveryEntitlementQuantity(listing, 3, 6));
        String firstHash = PlayerShopBlockService.deliveryEntitlementHash(listing, 3, 18L);

        listing.addBundleOutput("minecraft:gold_ingot", 1, DataComponentPatch.EMPTY);
        assertEquals(21L, PlayerShopBlockService.deliveryEntitlementQuantity(listing, 3, 6));
        String secondHash = PlayerShopBlockService.deliveryEntitlementHash(listing, 3, 21L);
        assertNotEquals(firstHash, secondHash);
    }

    @Test
    void bundleEntitlementRejectsLongOverflow() {
        ShopBlockEntity.Listing listing = new ShopBlockEntity.Listing("minecraft:chest");
        for (int i = 0; i < ShopBlockEntity.MAX_BUNDLE_OUTPUTS; i++) {
            listing.addBundleOutput("minecraft:diamond", Integer.MAX_VALUE, DataComponentPatch.EMPTY);
        }

        assertTrue(PlayerShopBlockService.deliveryEntitlementQuantity(listing, Integer.MAX_VALUE, 1) < 0L);
    }
}
