package com.enviouse.futureshops.server.shop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent, per-player named shop-configuration snapshots (the Storefront/Payouts "Saved
 * configurations" feature). Unlike {@link ShopConfigClipboard} (a single, session-scoped slot),
 * these are named and survive restarts. Each snapshot is a {@link CompoundTag} produced by
 * {@code ShopBlockEntity.exportConfigSnapshot()} and applied via {@code applyConfigSnapshot}.
 */
public final class PlayerShopSavedConfigs extends SavedData {
    private static final String DATA_NAME = "futureshops_saved_configs";
    /** Cap per player so a hostile/careless client can't grow the save file unbounded. */
    public static final int MAX_PER_PLAYER = 16;
    public static final int MAX_NAME_LENGTH = 24;

    /** playerUUID → (name → snapshot), insertion-ordered so the client list is stable. */
    private final Map<UUID, LinkedHashMap<String, CompoundTag>> byPlayer = new LinkedHashMap<>();

    public static PlayerShopSavedConfigs load(CompoundTag tag) {
        PlayerShopSavedConfigs data = new PlayerShopSavedConfigs();
        CompoundTag players = tag.getCompound("Players");
        for (String uuidKey : players.getAllKeys()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidKey);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            CompoundTag named = players.getCompound(uuidKey);
            LinkedHashMap<String, CompoundTag> map = new LinkedHashMap<>();
            // "Order" preserves insertion order across save/load (compound key order is unspecified).
            var order = named.getList("Order", net.minecraft.nbt.Tag.TAG_STRING);
            CompoundTag entries = named.getCompound("Entries");
            for (int i = 0; i < order.size(); i++) {
                String name = order.getString(i);
                if (entries.contains(name, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                    map.put(name, entries.getCompound(name).copy());
                }
            }
            if (!map.isEmpty()) data.byPlayer.put(uuid, map);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag players = new CompoundTag();
        byPlayer.forEach((uuid, map) -> {
            CompoundTag named = new CompoundTag();
            var order = new net.minecraft.nbt.ListTag();
            CompoundTag entries = new CompoundTag();
            map.forEach((name, snap) -> {
                order.add(net.minecraft.nbt.StringTag.valueOf(name));
                entries.put(name, snap.copy());
            });
            named.put("Order", order);
            named.put("Entries", entries);
            players.put(uuid.toString(), named);
        });
        tag.put("Players", players);
        return tag;
    }

    public static PlayerShopSavedConfigs get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                PlayerShopSavedConfigs::load,
                PlayerShopSavedConfigs::new,
                DATA_NAME);
    }

    /** Trim + length-clamp a name so save/get/delete key it identically (symmetric normalization). */
    private static String normalizeName(String name) {
        if (name == null) return "";
        String safe = name.trim();
        return safe.length() > MAX_NAME_LENGTH ? safe.substring(0, MAX_NAME_LENGTH) : safe;
    }

    /** Ordered names for {@code player} (empty if none). */
    public List<String> names(UUID player) {
        LinkedHashMap<String, CompoundTag> map = byPlayer.get(player);
        return map == null ? List.of() : new ArrayList<>(map.keySet());
    }

    /**
     * Saves {@code snapshot} under {@code name} for {@code player}. Overwrites an existing name.
     * Rejects (returns false) a blank name or exceeding {@link #MAX_PER_PLAYER} distinct names.
     */
    public boolean save(UUID player, String name, CompoundTag snapshot) {
        if (player == null || snapshot == null) return false;
        String safe = normalizeName(name);
        if (safe.isEmpty()) return false;
        LinkedHashMap<String, CompoundTag> map = byPlayer.computeIfAbsent(player, ignored -> new LinkedHashMap<>());
        if (!map.containsKey(safe) && map.size() >= MAX_PER_PLAYER) return false;
        map.put(safe, snapshot.copy());
        setDirty();
        return true;
    }

    /** Returns an independent copy of the named snapshot, or null if absent. */
    @Nullable
    public CompoundTag get(UUID player, String name) {
        LinkedHashMap<String, CompoundTag> map = byPlayer.get(player);
        if (map == null || name == null) return null;
        CompoundTag snap = map.get(normalizeName(name));
        return snap == null ? null : snap.copy();
    }

    /** Deletes the named snapshot; returns true if something was removed. */
    public boolean delete(UUID player, String name) {
        LinkedHashMap<String, CompoundTag> map = byPlayer.get(player);
        if (map == null || name == null) return false;
        boolean removed = map.remove(normalizeName(name)) != null;
        if (removed) {
            if (map.isEmpty()) byPlayer.remove(player);
            setDirty();
        }
        return removed;
    }
}
