package com.enviouse.futureshopsp.server.shop;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShopSettlementSavedDataPagingTest {
    @Test
    void hostilePageValuesCannotOverflowTheSliceOffset() {
        UUID owner = UUID.randomUUID();
        PlayerShopSettlementSavedData data = new PlayerShopSettlementSavedData();
        data.recordSale(owner, 42L, 1L, "minecraft:stone", 1);

        assertTrue(data.getPage(owner, 42L, Integer.MAX_VALUE, Integer.MAX_VALUE).isEmpty());
    }
}
