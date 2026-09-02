package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.server.SavedDataMigrations;
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
    private static final int MAXIMUM_PLAYERS = 100_000;
    private static final int MAXIMUM_SNAPSHOT_NBT_CHARACTERS = 262_144;

    /** playerUUID → (name → snapshot), insertion-ordered so the client list is stable. */
    private final Map<UUID, LinkedHashMap<String, CompoundTag>> byPlayer = new LinkedHashMap<>();

    public static PlayerShopSavedConfigs load(CompoundTag tag) {
        PlayerShopSavedConfigs data = new PlayerShopSavedConfigs();
        if (tag.contains("Players") && !tag.contains("Players", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Saved shop configuration players are invalid");
        }
        CompoundTag players = tag.getCompound("Players");
        if (players.getAllKeys().size() > MAXIMUM_PLAYERS) {
            throw new IllegalArgumentException("Saved shop configuration player limit is exceeded");
        }
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
            var order = SavedDataMigrations.requireList(
                    named, "Order", net.minecraft.nbt.Tag.TAG_STRING,
                    MAX_PER_PLAYER, "Saved shop configuration order");
            CompoundTag entries = named.getCompound("Entries");
            if (named.contains("Entries") && !named.contains("Entries", net.minecraft.nbt.Tag.TAG_COMPOUND)
                    || entries.getAllKeys().size() > MAX_PER_PLAYER) {
                throw new IllegalArgumentException("Saved shop configuration entries are invalid");
            }
            java.util.HashSet<String> names = new java.util.HashSet<>();
            for (int i = 0; i < order.size(); i++) {
                String name = order.getString(i);
                if (name.isBlank() || name.length() > MAX_NAME_LENGTH || !names.add(name)
                        || !entries.contains(name, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                    throw new IllegalArgumentException("Saved shop configuration name is invalid");
                }
                CompoundTag snapshot = entries.getCompound(name).copy();
                validateSnapshot(snapshot);
                map.put(name, snapshot);
            }
            if (!map.isEmpty()) data.byPlayer.put(uuid, map);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (byPlayer.size() > MAXIMUM_PLAYERS) {
            throw new IllegalStateException("Saved shop configuration player limit is exceeded");
        }
        CompoundTag players = new CompoundTag();
        byPlayer.forEach((uuid, map) -> {
            if (map.size() > MAX_PER_PLAYER) {
                throw new IllegalStateException("Saved shop configuration limit is exceeded");
            }
            CompoundTag named = new CompoundTag();
            var order = new net.minecraft.nbt.ListTag();
            CompoundTag entries = new CompoundTag();
            map.forEach((name, snap) -> {
                if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
                    throw new IllegalStateException("Saved shop configuration name is invalid");
                }
                validateSnapshot(snap);
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
        try {
            validateSnapshot(snapshot);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        String safe = normalizeName(name);
        if (safe.isEmpty()) return false;
        LinkedHashMap<String, CompoundTag> map = byPlayer.computeIfAbsent(player, ignored -> new LinkedHashMap<>());
        if (!map.containsKey(safe) && map.size() >= MAX_PER_PLAYER) return false;
        map.put(safe, snapshot.copy());
        setDirty();
        return true;
    }

    private static void validateSnapshot(CompoundTag snapshot) {
        if (snapshot == null || snapshot.toString().length() > MAXIMUM_SNAPSHOT_NBT_CHARACTERS) {
            throw new IllegalArgumentException("Saved shop configuration snapshot is too large");
        }
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
