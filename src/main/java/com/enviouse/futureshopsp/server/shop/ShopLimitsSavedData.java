package com.enviouse.futureshopsp.server.shop;

import com.enviouse.futureshopsp.server.SavedDataMigrations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
    private static final int CURRENT_VERSION = 1;
    private static final int MAX_PLAYERS = 10_000;
    private static final int MAX_LIMIT = 1_000_000;

    /** Per-player max shop blocks. -1 = unlimited. */
    private final Map<UUID, Integer> maxShopBlocks = new HashMap<>();
    private boolean integrityValid = true;

    public static ShopLimitsSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        ShopLimitsSavedData data = new ShopLimitsSavedData();
        int version = SavedDataMigrations.readVersion(tag);
        if (version > CURRENT_VERSION) {
            data.integrityValid = false;
            return data;
        }
        SavedDataMigrations.needsMigration(DATA_NAME, version, CURRENT_VERSION);
        Tag rawLimits = tag.get("MaxShopBlocks");
        if (rawLimits != null && !(rawLimits instanceof CompoundTag)) {
            data.integrityValid = false;
            return data;
        }
        if (!(rawLimits instanceof CompoundTag limits)) {
            return data;
        }
        if (limits.size() > MAX_PLAYERS) {
            data.integrityValid = false;
            return data;
        }
        Map<UUID, Integer> staged = new HashMap<>();
        for (String key : limits.getAllKeys()) {
            if (!limits.contains(key, Tag.TAG_INT)) {
                data.integrityValid = false;
                return data;
            }
            UUID player;
            try {
                player = UUID.fromString(key);
            } catch (IllegalArgumentException exception) {
                data.integrityValid = false;
                return data;
            }
            int max = limits.getInt(key);
            if (max < -1 || max > MAX_LIMIT || staged.put(player, max) != null) {
                data.integrityValid = false;
                return data;
            }
        }
        data.maxShopBlocks.putAll(staged);
        return data;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
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
    public synchronized int getMaxShopBlocks(UUID player) {
        if (player == null) return -1;
        return maxShopBlocks.getOrDefault(player, -1);
    }

    /** Set max shop blocks for a player. -1 = unlimited. */
    public synchronized void setMaxShopBlocks(UUID player, int max) {
        if (player == null) return;
        if (max > MAX_LIMIT) return;
        if (max < 0) {
            maxShopBlocks.remove(player);
        } else {
            maxShopBlocks.put(player, max);
        }
        setDirty();
    }

    /** Check if a player can place another shop block. */
    public synchronized boolean canPlace(UUID player, int currentCount) {
        if (player == null || currentCount < 0) return false;
        int max = getMaxShopBlocks(player);
        return max < 0 || currentCount < max;
    }

    public synchronized boolean integrityValid() {
        return integrityValid;
    }
}
