package com.enviouse.futureshopsp.server.shop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists per-player max shop block limits.
 * Default value -1 means unlimited (no restriction).
 */
public final class ShopLimitsSavedData extends SavedData {
    private static final String DATA_NAME = "futureshops_shop_limits";

    /** Per-player max shop blocks. -1 = unlimited. */
    private final Map<UUID, Integer> maxShopBlocks = new HashMap<>();

    public static ShopLimitsSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        ShopLimitsSavedData data = new ShopLimitsSavedData();
        CompoundTag limits = tag.getCompound("MaxShopBlocks");
        for (String key : limits.getAllKeys()) {
            try {
                data.maxShopBlocks.put(UUID.fromString(key), limits.getInt(key));
            } catch (IllegalArgumentException ignored) {}
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag limits = new CompoundTag();
        maxShopBlocks.forEach((uuid, max) -> limits.putInt(uuid.toString(), max));
        tag.put("MaxShopBlocks", limits);
        return tag;
    }

    public static ShopLimitsSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(ShopLimitsSavedData::new, ShopLimitsSavedData::load, null), DATA_NAME);
    }

    /** Get max shop blocks for a player. Returns -1 (unlimited) if not set. */
    public int getMaxShopBlocks(UUID player) {
        return maxShopBlocks.getOrDefault(player, -1);
    }

    /** Set max shop blocks for a player. -1 = unlimited. */
    public void setMaxShopBlocks(UUID player, int max) {
        if (max < 0) {
            maxShopBlocks.remove(player);
        } else {
            maxShopBlocks.put(player, max);
        }
        setDirty();
    }

    /** Check if a player can place another shop block. */
    public boolean canPlace(UUID player, int currentCount) {
        int max = getMaxShopBlocks(player);
        return max < 0 || currentCount < max;
    }
}

