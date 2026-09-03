package com.enviouse.futureshopsp.server.shop;

import org.junit.jupiter.api.Test;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

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
        assertTrue(restored.integrityValid());
        assertEquals(first, restored.beginClaim(owner, 42L));

        data.recordSale(owner, 42L, 25L, "minecraft:dirt", 1);
        assertTrue(data.completeClaim(owner, 42L, first.requestId(), first.amountMinor()));
        assertEquals(25L, data.snapshot(owner, 42L, 6).pendingMinor());
        assertFalse(data.completeClaim(owner, 42L, first.requestId(), first.amountMinor()));
    }

    @Test
    void settlementClaimIdentityIsRecoverableFromStablePendingState() {
        UUID owner = UUID.randomUUID();
        PlayerShopSettlementSavedData first = new PlayerShopSettlementSavedData();
        PlayerShopSettlementSavedData second = new PlayerShopSettlementSavedData();
        assertTrue(first.recordSale(owner, 42L, 100L, "minecraft:stone", 1));
        assertTrue(second.recordSale(owner, 42L, 100L, "minecraft:stone", 1));

        assertEquals(first.beginClaim(owner, 42L), second.beginClaim(owner, 42L));
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

    @Test
    void checksumTamperingBlocksSettlementRecovery() {
        UUID owner = UUID.randomUUID();
        PlayerShopSettlementSavedData data = new PlayerShopSettlementSavedData();
        assertTrue(data.recordSale(owner, 42L, 100L, "minecraft:stone", 1));
        data.markUnclean();

        CompoundTag saved = data.save(new CompoundTag(), null);
        ListTag settlements = saved.getList("settlements", Tag.TAG_COMPOUND);
        ((CompoundTag) settlements.get(0)).putLong("pending", 1L);

        PlayerShopSettlementSavedData restored = PlayerShopSettlementSavedData.load(saved, null);
        assertFalse(restored.integrityValid());
        assertFalse(restored.cleanMarkerValid());
        assertEquals(0L, restored.snapshot(owner, 42L, 6).pendingMinor());
    }

    @Test
    void oversizedPersistedRevenueTextIsReadOnly() {
        UUID owner = UUID.randomUUID();
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("schemaVersion", 1);
        CompoundTag ownerTag = new CompoundTag();
        ownerTag.putUUID("owner", owner);
        ListTag rows = new ListTag();
        CompoundTag row = new CompoundTag();
        row.putLong("ts", 1L);
        row.putLong("shopPos", 42L);
        row.putLong("amount", 1L);
        row.putString("type", "x".repeat(65));
        row.putString("itemId", "minecraft:stone");
        row.putInt("quantity", 1);
        rows.add(row);
        ownerTag.put("rows", rows);
        ListTag ownerRows = new ListTag();
        ownerRows.add(ownerTag);
        legacy.put("ownerRows", ownerRows);

        PlayerShopSettlementSavedData restored = PlayerShopSettlementSavedData.load(legacy, null);

        assertFalse(restored.integrityValid());
        assertTrue(restored.snapshot(owner, 42L, 6).rows().isEmpty());
    }

    @Test
    void legacySettlementDataMigratesToChecksummedSchema() {
        UUID owner = UUID.randomUUID();
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("schemaVersion", 1);
        ListTag settlements = new ListTag();
        CompoundTag settlement = new CompoundTag();
        settlement.putLong("shopPos", 42L);
        settlement.putUUID("owner", owner);
        settlement.putLong("pending", 100L);
        settlement.putLong("lifetime", 100L);
        settlements.add(settlement);
        legacy.put("settlements", settlements);
        legacy.put("ownerRows", new ListTag());

        PlayerShopSettlementSavedData restored = PlayerShopSettlementSavedData.load(legacy, null);
        assertTrue(restored.integrityValid());
        assertEquals(100L, restored.snapshot(owner, 42L, 6).pendingMinor());

        CompoundTag migrated = restored.save(new CompoundTag(), null);
        assertEquals(2, migrated.getInt("schemaVersion"));
        assertTrue(migrated.contains("checksum", Tag.TAG_STRING));
        assertTrue(PlayerShopSettlementSavedData.load(migrated, null).integrityValid());
    }

    @Test
    void wrongSettlementTagTypeBlocksRecovery() {
        CompoundTag saved = new CompoundTag();
        saved.putString("settlements", "not a list");

        PlayerShopSettlementSavedData restored = PlayerShopSettlementSavedData.load(saved, null);

        assertFalse(restored.integrityValid());
        assertTrue(restored.snapshot(UUID.randomUUID(), 42L, 6).rows().isEmpty());
    }

    @Test
    void wrongOwnerRowsTagTypeBlocksRecovery() {
        CompoundTag saved = new CompoundTag();
        saved.put("settlements", new ListTag());
        saved.putString("ownerRows", "not a list");

        PlayerShopSettlementSavedData restored = PlayerShopSettlementSavedData.load(saved, null);

        assertFalse(restored.integrityValid());
        assertTrue(restored.snapshot(UUID.randomUUID(), 42L, 6).rows().isEmpty());
    }
}
