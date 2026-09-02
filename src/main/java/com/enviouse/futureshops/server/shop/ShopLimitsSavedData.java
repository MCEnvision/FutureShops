package com.enviouse.futureshops.server.shop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists per-player max shop block limits.
 * Default value -1 means unlimited (no restriction).
 */
public final class ShopLimitsSavedData extends SavedData {
    private static final String DATA_NAME = "futureshops_shop_limits";
    private static final int MAXIMUM_PLAYERS = 100_000;

    /** Per-player max shop blocks. -1 = unlimited. */
    private final Map<UUID, Integer> maxShopBlocks = new HashMap<>();

    public static ShopLimitsSavedData load(CompoundTag tag) {
        ShopLimitsSavedData data = new ShopLimitsSavedData();
        if (tag.contains("MaxShopBlocks") && !tag.contains("MaxShopBlocks", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Shop limit map is invalid");
        }
        CompoundTag limits = tag.getCompound("MaxShopBlocks");
        if (limits.getAllKeys().size() > MAXIMUM_PLAYERS) {
            throw new IllegalArgumentException("Shop limit map is too large");
        }
        for (String key : limits.getAllKeys()) {
            if (!limits.contains(key, Tag.TAG_INT)) {
                throw new IllegalArgumentException("Shop limit value is invalid");
            }
            int value = limits.getInt(key);
            if (value < 0) {
                throw new IllegalArgumentException("Shop limit value is invalid");
            }
            UUID player;
            try {
                player = UUID.fromString(key);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Shop limit player id is invalid", exception);
            }
            data.maxShopBlocks.put(player, value);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (maxShopBlocks.size() > MAXIMUM_PLAYERS) {
            throw new IllegalStateException("Shop limit map is too large");
        }
        CompoundTag limits = new CompoundTag();
        maxShopBlocks.forEach((uuid, max) -> limits.putInt(uuid.toString(), max));
        tag.put("MaxShopBlocks", limits);
        return tag;
    }

    public static ShopLimitsSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                ShopLimitsSavedData::load,
                ShopLimitsSavedData::new,
                DATA_NAME);
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
