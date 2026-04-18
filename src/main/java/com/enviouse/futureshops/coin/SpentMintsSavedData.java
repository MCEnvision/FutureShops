package com.enviouse.futureshops.coin;

import com.enviouse.futureshops.server.SavedDataMigrations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * World-level {@link SavedData} that persists the registry of all minted coin
 * IDs, allowing the server to detect previously-deposited (consumed) mints.
 *
 * <p>Stored in the overworld data directory as {@code futureshops_coin_mints.dat}.
 *
 * <p>Usage contract:
 * <ul>
 *   <li>On {@code /withdraw}: call {@link #registerMint} for each new stack.</li>
 *   <li>On {@code /deposit}: call {@link #isKnownAndUnconsumed} before accepting
 *       a coin, then call {@link #consumeMints} on all deposited mint IDs.</li>
 * </ul>
 */
public final class SpentMintsSavedData extends SavedData {

    public static final String DATA_NAME = "futureshops_coin_mints";
    private static final int CURRENT_VERSION = 1;

    private final Map<String, CoinMintRecord> registry = new HashMap<>();

    // -------------------------------------------------------------------------
    // SavedData serialization
    // -------------------------------------------------------------------------

    public static SpentMintsSavedData load(CompoundTag tag) {
        SpentMintsSavedData data = new SpentMintsSavedData();
        int version = SavedDataMigrations.readVersion(tag);
        SavedDataMigrations.needsMigration(DATA_NAME, version, CURRENT_VERSION);
        ListTag entries = tag.getList("mints", Tag.TAG_COMPOUND);
        for (Tag entryTag : entries) {
            CompoundTag entry = (CompoundTag) entryTag;
            String mintId = entry.getString("id");
            if (!mintId.isEmpty()) {
                UUID playerUUID = entry.hasUUID("player")
                        ? entry.getUUID("player")
                        : new UUID(0L, 0L);
                long denomination = entry.getLong("denomination");
                int count = entry.getInt("count");
                long mintedAt = entry.getLong("minted_at");
                long consumedAt = entry.getLong("consumed_at");
                String serverId = entry.getString("server");
                data.registry.put(mintId,
                        new CoinMintRecord(mintId, playerUUID, denomination, count, mintedAt, consumedAt, serverId));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        ListTag entries = new ListTag();
        for (CoinMintRecord record : registry.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", record.mintId());
            entry.putUUID("player", record.playerUUID());
            entry.putLong("denomination", record.denomination());
            entry.putInt("count", record.count());
            entry.putLong("minted_at", record.mintedAt());
            entry.putLong("consumed_at", record.consumedAt());
            entry.putString("server", record.serverId());
            entries.add(entry);
        }
        tag.put("mints", entries);
        return tag;
    }

    // -------------------------------------------------------------------------
    // Static accessor — always retrieves/creates from overworld data storage
    // -------------------------------------------------------------------------

    public static SpentMintsSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                SpentMintsSavedData::load,
                SpentMintsSavedData::new,
                DATA_NAME);
    }

    // -------------------------------------------------------------------------
    // Mint registration (called at withdraw time)
    // -------------------------------------------------------------------------

    /**
     * Records a newly minted coin stack. Idempotent — silently ignored if the
     * mint ID is already registered (should never happen with UUID generation).
     */
    public void registerMint(String mintId, UUID playerUUID, long denomination,
                              int count, long mintedAt, String serverId) {
        if (!registry.containsKey(mintId)) {
            registry.put(mintId,
                    new CoinMintRecord(mintId, playerUUID, denomination, count, mintedAt, 0L, serverId));
            setDirty();
        }
    }

    // -------------------------------------------------------------------------
    // Validation helpers (called at deposit time)
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} when the mint ID is present in the registry AND has
     * not yet been consumed.  Any other result (unknown or already consumed)
     * means the coin stack must be destroyed.
     */
    public boolean isKnownAndUnconsumed(String mintId) {
        CoinMintRecord record = registry.get(mintId);
        return record != null && !record.consumed();
    }

    /**
     * Returns the original coin count for the given mint ID, used for dupe
     * detection.  Returns {@code 0} when the mint ID is unknown.
     */
    public int getOriginalCount(String mintId) {
        CoinMintRecord record = registry.get(mintId);
        return record != null ? record.count() : 0;
    }

    // -------------------------------------------------------------------------
    // Consumption (called after a successful deposit)
    // -------------------------------------------------------------------------

    /**
     * Stamps the given mint IDs with the current time as {@code consumed_at},
     * permanently preventing future deposits of these coins.
     */
    public void consumeMints(Collection<String> mintIds) {
        if (mintIds.isEmpty()) return;
        long now = Instant.now().getEpochSecond();
        boolean dirty = false;
        for (String mintId : mintIds) {
            CoinMintRecord record = registry.get(mintId);
            if (record != null && !record.consumed()) {
                registry.put(mintId, record.withConsumedAt(now));
                dirty = true;
            }
        }
        if (dirty) {
            setDirty();
        }
    }

    // -------------------------------------------------------------------------
    // Read-only snapshot for audit commands
    // -------------------------------------------------------------------------

    public Map<String, CoinMintRecord> snapshotRegistry() {
        return Collections.unmodifiableMap(new HashMap<>(registry));
    }
}

