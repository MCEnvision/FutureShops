package com.enviouse.futureshops.money;

import com.enviouse.futureshops.server.SavedDataMigrations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * World-level {@link SavedData} that persists the registry of all minted coin
 * batches, allowing the server to decrement per-mint remaining counts and
 * detect fully-consumed (or tampered/excess) deposits.
 *
 * <p>Stored in the overworld data directory as {@code futureshops_coin_mints.dat}.
 *
 * <p>Usage contract:
 * <ul>
 *   <li>On {@code /withdraw}: call {@link #registerMint} once per minted stack
 *       with the full batch size as {@code authorizedCount}.</li>
 *   <li>On deposit sinks: call {@link #consume} with the stack size to atomically
 *       decrement the remaining count and learn how many coins were actually
 *       accepted (the rest are counterfeit excess).</li>
 * </ul>
 */
public final class SpentMintsSavedData extends SavedData {

    public static final String DATA_NAME = "futureshops_coin_mints";
    private static final int CURRENT_VERSION = 2;

    private final Map<String, MoneyMintRecord> registry = new HashMap<>();

    // -------------------------------------------------------------------------
    // SavedData serialization
    // -------------------------------------------------------------------------

    public static SpentMintsSavedData load(CompoundTag tag) {
        SpentMintsSavedData data = new SpentMintsSavedData();
        int version = SavedDataMigrations.readVersion(tag);
        boolean migrating = SavedDataMigrations.needsMigration(DATA_NAME, version, CURRENT_VERSION);
        ListTag entries = tag.getList("mints", Tag.TAG_COMPOUND);
        for (Tag entryTag : entries) {
            CompoundTag entry = (CompoundTag) entryTag;
            String mintId = entry.getString("id");
            if (mintId.isEmpty()) continue;
            UUID playerUUID = entry.hasUUID("player") ? entry.getUUID("player") : new UUID(0L, 0L);
            long denomination = entry.getLong("denomination");
            long mintedAt = entry.getLong("minted_at");
            long consumedAt = entry.getLong("consumed_at");
            String serverId = entry.getString("server");

            int authorizedCount;
            int remainingCount;
            if (version >= 2) {
                authorizedCount = entry.getInt("authorized_count");
                remainingCount = entry.getInt("remaining_count");
            } else {
                // v1 migration: treat legacy "count" as the full batch size, and
                // derive remaining from the consumedAt stamp.
                int legacyCount = entry.getInt("count");
                authorizedCount = legacyCount;
                remainingCount = consumedAt > 0L ? 0 : legacyCount;
            }

            data.registry.put(mintId,
                    new MoneyMintRecord(mintId, playerUUID, denomination, authorizedCount,
                            remainingCount, mintedAt, consumedAt, serverId));
        }
        if (migrating) {
            data.setDirty();
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        ListTag entries = new ListTag();
        for (MoneyMintRecord record : registry.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", record.mintId());
            entry.putUUID("player", record.playerUUID());
            entry.putLong("denomination", record.denomination());
            entry.putInt("authorized_count", record.authorizedCount());
            entry.putInt("remaining_count", record.remainingCount());
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
     * Records a newly minted coin batch. Idempotent — silently ignored if the
     * mint ID is already registered (should never happen with UUID generation).
     */
    public void registerMint(String mintId, UUID playerUUID, long denomination,
                              int authorizedCount, long mintedAt, String serverId) {
        if (!registry.containsKey(mintId)) {
            registry.put(mintId,
                    new MoneyMintRecord(mintId, playerUUID, denomination, authorizedCount,
                            authorizedCount, mintedAt, 0L, serverId));
            setDirty();
        }
    }

    // -------------------------------------------------------------------------
    // Consumption (called at deposit time, per stack)
    // -------------------------------------------------------------------------

    /**
     * Attempts to consume up to {@code requestedCount} coins against the given
     * mint record. Returns an outcome describing how many the ledger accepted
     * versus rejected (unknown mint, tampered denomination/authorized count, or
     * excess beyond the remaining balance — all counterfeit).
     *
     * <p>Fully-consumed records stay in the registry with a non-zero
     * {@code consumed_at} timestamp for audit purposes.</p>
     */
    public ConsumeResult consume(String mintId, int requestedCount, long expectedDenomination,
                                 int expectedAuthorizedCount) {
        if (requestedCount <= 0) {
            return new ConsumeResult(0, 0, "EMPTY");
        }
        MoneyMintRecord record = registry.get(mintId);
        if (record == null) {
            return new ConsumeResult(0, requestedCount, "UNKNOWN_MINT");
        }
        if (record.denomination() != expectedDenomination
                || record.authorizedCount() != expectedAuthorizedCount) {
            return new ConsumeResult(0, requestedCount, "TAMPERED");
        }
        int remaining = record.remainingCount();
        if (remaining <= 0) {
            return new ConsumeResult(0, requestedCount, "ALREADY_CONSUMED");
        }
        int accepted = Math.min(requestedCount, remaining);
        int rejected = requestedCount - accepted;
        int newRemaining = remaining - accepted;
        MoneyMintRecord updated = record.withRemainingCount(newRemaining);
        if (newRemaining == 0) {
            updated = updated.withConsumedAt(Instant.now().getEpochSecond());
        }
        registry.put(mintId, updated);
        setDirty();
        return new ConsumeResult(accepted, rejected, rejected > 0 ? "EXCESS" : "");
    }

    /** Outcome of a {@link #consume} call. */
    public record ConsumeResult(int accepted, int rejected, String errorCode) {
    }

    // -------------------------------------------------------------------------
    // Read-only snapshot for audit commands
    // -------------------------------------------------------------------------

    public Map<String, MoneyMintRecord> snapshotRegistry() {
        return Collections.unmodifiableMap(new HashMap<>(registry));
    }

    /** Non-destructive lookup used by deposit code to plan per-stack consumption. */
    public int remainingCount(String mintId) {
        MoneyMintRecord record = registry.get(mintId);
        return record != null ? record.remainingCount() : 0;
    }
}

