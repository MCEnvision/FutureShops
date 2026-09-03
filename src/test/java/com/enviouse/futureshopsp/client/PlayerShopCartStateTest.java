package com.enviouse.futureshopsp.client;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShopCartStateTest {

    private static final BlockPos SHOP = new BlockPos(1, 2, 3);

    @AfterEach
    void clearCart() {
        PlayerShopCartState.clear();
    }

    @Test
    void cartQuantityAndEntryCountAreBounded() {
        PlayerShopCartState.addToCart(SHOP, 0, 2304, "minecraft:stone", "shop", 1L, 1,
                "MONEY", "", 0, "", false);
        PlayerShopCartState.addToCart(SHOP, 0, 1, "minecraft:stone", "shop", 1L, 1,
                "MONEY", "", 0, "", false);

        assertEquals(2304, PlayerShopCartState.getEntries().getFirst().quantity());
        assertEquals(2304, PlayerShopCartState.totalQuantity());
    }

    @Test
    void totalsSaturateInsteadOfWrapping() {
        PlayerShopCartState.CartEntry entry = new PlayerShopCartState.CartEntry(
                SHOP, 0, 2, "minecraft:stone", "shop", Long.MAX_VALUE, Integer.MAX_VALUE,
                "MONEY", "", 0, "", "", false);

        assertEquals(Long.MAX_VALUE, entry.totalPrice());
        assertEquals((long) Integer.MAX_VALUE * 2L, entry.totalItems());

        PlayerShopCartState.addToCart(SHOP, 0, 2, "minecraft:stone", "shop", Long.MAX_VALUE, 1,
                "MONEY", "", 0, "", false);
        PlayerShopCartState.CartSummary summary = PlayerShopCartState.buildSummary();
        assertEquals(Long.MAX_VALUE, summary.moneyTotal());
        assertTrue(PlayerShopCartState.totalPrice() >= 0L);
    }

    @Test
    void barterTotalsSaturateInsteadOfWrapping() {
        PlayerShopCartState.addToCart(SHOP, 0, 2304, "minecraft:stone", "shop", 1L, 1,
                "BARTER", "minecraft:diamond", Integer.MAX_VALUE, "", false);

        assertEquals(Integer.MAX_VALUE,
                PlayerShopCartState.buildSummary().barterTotals().get("minecraft:diamond"));
    }
}
