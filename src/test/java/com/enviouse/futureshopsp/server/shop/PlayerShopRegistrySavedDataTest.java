package com.enviouse.futureshopsp.server.shop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShopRegistrySavedDataTest {
    @Test
    void malformedOwnerRecordDoesNotPartiallyLoadRegistry() {
        UUID owner = UUID.randomUUID();
        CompoundTag saved = new CompoundTag();
        saved.putInt("schemaVersion", 1);
        ListTag owners = new ListTag();
        CompoundTag validOwner = new CompoundTag();
        validOwner.putUUID("owner", owner);
        validOwner.put("shops", new ListTag());
        owners.add(validOwner);
        owners.add(new CompoundTag());
        saved.put("owners", owners);

        PlayerShopRegistrySavedData restored = PlayerShopRegistrySavedData.load(saved, null);

        assertFalse(restored.integrityValid());
        assertTrue(restored.snapshot().isEmpty());
    }

    @Test
    void malformedDimensionDoesNotCrashOrPartiallyLoadRegistry() {
        UUID owner = UUID.randomUUID();
        CompoundTag saved = new CompoundTag();
        saved.putInt("schemaVersion", 1);
        ListTag shops = new ListTag();
        CompoundTag shop = new CompoundTag();
        shop.putString("dimension", "not a valid dimension");
        shop.putLong("pos", 42L);
        shops.add(shop);
        CompoundTag ownerTag = new CompoundTag();
        ownerTag.putUUID("owner", owner);
        ownerTag.put("shops", shops);
        ListTag owners = new ListTag();
        owners.add(ownerTag);
        saved.put("owners", owners);

        PlayerShopRegistrySavedData restored = PlayerShopRegistrySavedData.load(saved, null);

        assertFalse(restored.integrityValid());
        assertTrue(restored.getAllShops().isEmpty());
    }

    @Test
    void newerRegistrySchemaIsReadOnlyAndEmpty() {
        CompoundTag saved = new CompoundTag();
        saved.putInt("schemaVersion", 2);
        saved.put("owners", new ListTag());

        PlayerShopRegistrySavedData restored = PlayerShopRegistrySavedData.load(saved, null);

        assertFalse(restored.integrityValid());
        assertTrue(restored.snapshot().isEmpty());
    }
}
