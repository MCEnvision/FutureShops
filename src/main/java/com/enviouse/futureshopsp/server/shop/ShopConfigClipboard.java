package com.enviouse.futureshopsp.server.shop;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player, server-side, in-memory clipboard for shop configuration snapshots produced by
 * {@link com.enviouse.futureshopsp.block.ShopBlockEntity#exportConfigSnapshot()}. Not persisted —
 * clears on server restart, which is the desired "session-scoped" behavior; owners never
 * expect a copied config to survive a world reload.
 *
 * <p>Concurrent map so handlers on worker threads (e.g. async tick of a network handler) can
 * safely read/write without additional locking, even though the current packet pipeline is
 * single-threaded.
 */
public final class ShopConfigClipboard {

    private static final Map<UUID, CompoundTag> CLIPBOARDS = new ConcurrentHashMap<>();

    private ShopConfigClipboard() {}

    /** Overwrites (or installs) the clipboard for {@code player}. Stores an independent copy. */
    public static void store(UUID player, CompoundTag snapshot) {
        if (player == null || snapshot == null) return;
        CLIPBOARDS.put(player, snapshot.copy());
    }

    /** Returns an independent copy of the stored snapshot, or {@code null} if empty. */
    @Nullable
    public static CompoundTag snapshot(UUID player) {
        CompoundTag tag = CLIPBOARDS.get(player);
        return tag == null ? null : tag.copy();
    }

    public static boolean has(UUID player) {
        return player != null && CLIPBOARDS.containsKey(player);
    }

    public static void clear(UUID player) {
        if (player != null) CLIPBOARDS.remove(player);
    }

    public static void clearAll() {
        CLIPBOARDS.clear();
    }
}

