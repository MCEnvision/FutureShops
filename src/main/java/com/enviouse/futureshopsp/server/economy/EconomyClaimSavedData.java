package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.RequestId;
import com.enviouse.futureshopsp.server.SavedDataMigrations;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Versioned and checksummed durable offline delivery claims. */
public final class EconomyClaimSavedData extends SavedData implements EconomyClaimStore {
    public static final String DATA_NAME = "futureshops_economy_claims";
    private static final int CURRENT_VERSION = 1;
    private static final int MAX_RECORDS = 10_000;

    private final Map<RequestId, ClaimRecord> records = new LinkedHashMap<>();
    private boolean integrityValid = true;
    private boolean cleanMarkerValid = true;

    public static EconomyClaimSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(EconomyClaimSavedData::new, EconomyClaimSavedData::load, null), DATA_NAME);
    }

    public static EconomyClaimSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        EconomyClaimSavedData data = new EconomyClaimSavedData();
        int version = SavedDataMigrations.readVersion(tag);
        if (version > CURRENT_VERSION) {
            data.integrityValid = false;
            return data;
        }
        SavedDataMigrations.needsMigration(DATA_NAME, version, CURRENT_VERSION);
        if (tag.contains("cleanMarker", Tag.TAG_BYTE)) {
            data.cleanMarkerValid = tag.getBoolean("cleanMarker");
        }
        Tag rawEntries = tag.get("records");
        if (rawEntries != null && !(rawEntries instanceof ListTag)) {
            data.integrityValid = false;
            return data;
        }
        ListTag entries = rawEntries instanceof ListTag list ? list : new ListTag();
        if (entries.size() > MAX_RECORDS) {
            data.integrityValid = false;
            return data;
        }
        Map<RequestId, ClaimRecord> loaded = new LinkedHashMap<>();
        for (Tag raw : entries) {
            if (!(raw instanceof CompoundTag entry)) {
                data.integrityValid = false;
                continue;
            }
            try {
                ClaimRecord record = readEntry(entry);
                if (!entry.getString("checksum").equals(checksum(record))) {
                    data.integrityValid = false;
                    continue;
                }
                if (loaded.put(record.requestId(), record) != null) {
                    data.integrityValid = false;
                }
            } catch (RuntimeException exception) {
                data.integrityValid = false;
            }
        }
        if (data.integrityValid) {
            data.records.putAll(loaded);
        }
        return data;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        ListTag entries = new ListTag();
        for (ClaimRecord record : records.values()) {
            entries.add(writeEntry(record));
        }
        tag.put("records", entries);
        tag.putBoolean("cleanMarker", cleanMarkerValid);
        return tag;
    }

    @Override
    public synchronized Optional<ClaimRecord> find(RequestId requestId) {
        return Optional.ofNullable(records.get(requestId));
    }

    @Override
    public synchronized ClaimRecord create(RequestId requestId, UUID claimant, long amountMinorUnits,
                                            String description) {
        if (records.size() >= MAX_RECORDS) {
            throw new IllegalStateException("claim record limit reached");
        }
        if (records.containsKey(requestId)) {
            throw new IllegalStateException("claim already exists");
        }
        ClaimRecord record = new ClaimRecord(requestId, claimant, amountMinorUnits, description, ClaimState.PENDING);
        records.put(requestId, record);
        setDirty();
        return record;
    }

    @Override
    public synchronized ClaimRecord transition(RequestId requestId, ClaimState expected, ClaimState next) {
        ClaimRecord current = records.get(requestId);
        if (current == null || current.state() != expected || !allowed(expected, next)) {
            throw new IllegalStateException("invalid claim transition");
        }
        ClaimRecord updated = new ClaimRecord(current.requestId(), current.claimant(), current.amountMinorUnits(),
                current.description(), next);
        records.put(requestId, updated);
        setDirty();
        return updated;
    }

    @Override
    public synchronized List<ClaimRecord> snapshot() {
        return List.copyOf(new ArrayList<>(records.values()));
    }

    @Override
    public synchronized boolean integrityValid() {
        return integrityValid;
    }

    @Override
    public synchronized boolean cleanMarkerValid() {
        return cleanMarkerValid;
    }

    @Override
    public synchronized void markUnclean() {
        cleanMarkerValid = false;
        setDirty();
    }

    @Override
    public synchronized void markCleanMarker() {
        cleanMarkerValid = true;
        setDirty();
    }

    private static ClaimRecord readEntry(CompoundTag entry) {
        if (!entry.hasUUID("request") || !entry.hasUUID("claimant")
                || !entry.contains("amount", Tag.TAG_LONG) || !entry.contains("description", Tag.TAG_STRING)
                || !entry.contains("state", Tag.TAG_STRING) || !entry.contains("checksum", Tag.TAG_STRING)) {
            throw new IllegalArgumentException("claim record is incomplete");
        }
        return new ClaimRecord(new RequestId(entry.getUUID("request")), entry.getUUID("claimant"),
                entry.getLong("amount"), entry.getString("description"), ClaimState.valueOf(entry.getString("state")));
    }

    private static CompoundTag writeEntry(ClaimRecord record) {
        CompoundTag entry = new CompoundTag();
        entry.putUUID("request", record.requestId().value());
        entry.putUUID("claimant", record.claimant());
        entry.putLong("amount", record.amountMinorUnits());
        entry.putString("description", record.description());
        entry.putString("state", record.state().name());
        entry.putString("checksum", checksum(record));
        return entry;
    }

    private static String checksum(ClaimRecord record) {
        return EconomyRecordChecksum.sha256(record.requestId().value() + "|" + record.claimant() + "|"
                + record.amountMinorUnits() + "|" + record.description() + "|" + record.state());
    }

    private static boolean allowed(ClaimState expected, ClaimState next) {
        return switch (expected) {
            case PENDING -> next == ClaimState.DELIVERED || next == ClaimState.RESOLVED;
            case DELIVERED -> next == ClaimState.RESOLVED;
            case RESOLVED -> false;
        };
    }
}
