package com.enviouse.futureshopsp.server.shop;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopLimitsSavedDataTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000051");

    @Test
    void limitsRoundTripWithVersion() {
        ShopLimitsSavedData data = new ShopLimitsSavedData();
        data.setMaxShopBlocks(PLAYER, 12);

        ShopLimitsSavedData loaded = ShopLimitsSavedData.load(data.save(new CompoundTag(), null), null);

        assertTrue(loaded.integrityValid());
        assertEquals(12, loaded.getMaxShopBlocks(PLAYER));
        assertTrue(loaded.canPlace(PLAYER, 11));
        assertFalse(loaded.canPlace(PLAYER, 12));
    }

    @Test
    void wrongLimitsContainerBlocksRecovery() {
        CompoundTag saved = new CompoundTag();
        saved.putString("MaxShopBlocks", "not a compound");

        ShopLimitsSavedData loaded = ShopLimitsSavedData.load(saved, null);

        assertFalse(loaded.integrityValid());
        assertEquals(-1, loaded.getMaxShopBlocks(PLAYER));
    }

    @Test
    void malformedLimitDoesNotPartiallyReconstruct() {
        ShopLimitsSavedData data = new ShopLimitsSavedData();
        data.setMaxShopBlocks(PLAYER, 12);
        CompoundTag saved = data.save(new CompoundTag(), null);
        CompoundTag limits = saved.getCompound("MaxShopBlocks");
        limits.putString("not-a-uuid", "wrong type");

        ShopLimitsSavedData loaded = ShopLimitsSavedData.load(saved, null);

        assertFalse(loaded.integrityValid());
        assertEquals(-1, loaded.getMaxShopBlocks(PLAYER));
    }

    @Test
    void newerSchemaAndInvalidArgumentsFailClosed() {
        ShopLimitsSavedData data = new ShopLimitsSavedData();
        data.setMaxShopBlocks(PLAYER, 1_000_001);
        assertEquals(-1, data.getMaxShopBlocks(PLAYER));
        assertFalse(data.canPlace(null, 0));

        CompoundTag saved = data.save(new CompoundTag(), null);
        saved.putInt("schemaVersion", 2);
        assertFalse(ShopLimitsSavedData.load(saved, null).integrityValid());
    }
}
