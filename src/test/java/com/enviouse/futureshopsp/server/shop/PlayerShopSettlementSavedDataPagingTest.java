package com.enviouse.futureshopsp.server.shop;

import org.junit.jupiter.api.Test;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShopSettlementSavedDataPagingTest {
    @Test
    void hostilePageValuesCannotOverflowTheSliceOffset() {
        UUID owner = UUID.randomUUID();
        PlayerShopSettlementSavedData data = new PlayerShopSettlementSavedData();
        data.recordSale(owner, 42L, 1L, "minecraft:stone", 1);

        assertTrue(data.getPage(owner, 42L, Integer.MAX_VALUE, Integer.MAX_VALUE).isEmpty());
    }

    @Test
    void settlementClaimIdentitySurvivesNewSalesAndCompletesOnce() {
        UUID owner = UUID.randomUUID();
        PlayerShopSettlementSavedData data = new PlayerShopSettlementSavedData();
        data.recordSale(owner, 42L, 100L, "minecraft:stone", 1);

        PlayerShopSettlementSavedData.SettlementClaim first = data.beginClaim(owner, 42L);
        assertNotNull(first);
        PlayerShopSettlementSavedData.SettlementClaim retry = data.beginClaim(owner, 42L);
        assertEquals(first, retry);

        CompoundTag saved = data.save(new CompoundTag(), null);
        PlayerShopSettlementSavedData restored = PlayerShopSettlementSavedData.load(saved, null);
        assertEquals(first, restored.beginClaim(owner, 42L));

        data.recordSale(owner, 42L, 25L, "minecraft:dirt", 1);
        assertTrue(data.completeClaim(owner, 42L, first.requestId(), first.amountMinor()));
        assertEquals(25L, data.snapshot(owner, 42L, 6).pendingMinor());
        assertFalse(data.completeClaim(owner, 42L, first.requestId(), first.amountMinor()));
    }

    @Test
    void settlementOverflowIsRejectedBeforeMutation() {
        UUID owner = UUID.randomUUID();
        PlayerShopSettlementSavedData data = new PlayerShopSettlementSavedData();
        assertTrue(data.recordSale(owner, 42L, Long.MAX_VALUE, "minecraft:stone", 1));
        assertFalse(data.canRecordSale(owner, 42L, 1L));
        assertFalse(data.recordSale(owner, 42L, 1L, "minecraft:dirt", 1));
        assertEquals(Long.MAX_VALUE, data.snapshot(owner, 42L, 6).pendingMinor());
    }
}
