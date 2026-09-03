package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.RequestId;
import com.enviouse.futureshopsp.server.SavedDataMigrations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.HolderLookup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Versioned and checksummed durable item custody index. */
public final class EconomyCustodySavedData extends SavedData implements EconomyCustodyStore {
    public static final String DATA_NAME = "futureshops_economy_custody";
    private static final int CURRENT_VERSION = 1;
    private static final int MAX_RECORDS = 10_000;

    private final Map<RequestId, CustodyRecord> records = new LinkedHashMap<>();
    private boolean integrityValid = true;
    private boolean cleanMarkerValid = true;

    public static EconomyCustodySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(EconomyCustodySavedData::new, EconomyCustodySavedData::load, null), DATA_NAME);
    }

    public static EconomyCustodySavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        EconomyCustodySavedData data = new EconomyCustodySavedData();
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
        if (version < 0 || (version == CURRENT_VERSION && rawEntries == null)) {
            data.integrityValid = false;
            return data;
        }
        if (rawEntries != null && !(rawEntries instanceof ListTag)) {
            data.integrityValid = false;
            return data;
        }
        ListTag entries = rawEntries instanceof ListTag list ? list : new ListTag();
        if (entries.size() > MAX_RECORDS) {
            data.integrityValid = false;
            return data;
        }
        Map<RequestId, CustodyRecord> loaded = new LinkedHashMap<>();
        for (Tag raw : entries) {
            if (!(raw instanceof CompoundTag entry)) {
                data.integrityValid = false;
                continue;
            }
            try {
                CustodyRecord record = readEntry(entry);
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
        for (CustodyRecord record : records.values()) {
            entries.add(writeEntry(record));
        }
        tag.put("records", entries);
        tag.putBoolean("cleanMarker", cleanMarkerValid);
        return tag;
    }

    @Override
    public synchronized Optional<CustodyRecord> find(RequestId requestId) {
        return Optional.ofNullable(records.get(requestId));
    }

    @Override
    public synchronized CustodyRecord hold(RequestId requestId, UUID owner, String itemKey,
                                            long quantity, String contentHash) {
        if (records.size() >= MAX_RECORDS) {
            throw new IllegalStateException("custody record limit reached");
        }
        if (records.containsKey(requestId)) {
            throw new IllegalStateException("custody already exists");
        }
        CustodyRecord record = new CustodyRecord(requestId, requestId.value().toString(), owner,
                itemKey, quantity, contentHash, CustodyState.HELD);
        records.put(requestId, record);
        setDirty();
        return record;
    }

    @Override
    public synchronized CustodyRecord transition(RequestId requestId, CustodyState expected, CustodyState next) {
        CustodyRecord current = records.get(requestId);
        if (current == null || current.state() != expected || !allowed(expected, next)) {
            throw new IllegalStateException("invalid custody transition");
        }
        CustodyRecord updated = new CustodyRecord(current.requestId(), current.custodyId(), current.owner(),
                current.itemKey(), current.quantity(), current.contentHash(), next);
        records.put(requestId, updated);
        setDirty();
        return updated;
    }

    @Override
    public synchronized List<CustodyRecord> snapshot() {
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

    private static CustodyRecord readEntry(CompoundTag entry) {
        if (!entry.hasUUID("request") || !entry.hasUUID("owner")
                || !entry.contains("custodyId", Tag.TAG_STRING) || !entry.contains("itemKey", Tag.TAG_STRING)
                || !entry.contains("quantity", Tag.TAG_LONG) || !entry.contains("contentHash", Tag.TAG_STRING)
                || !entry.contains("state", Tag.TAG_STRING) || !entry.contains("checksum", Tag.TAG_STRING)) {
            throw new IllegalArgumentException("custody record is incomplete");
        }
        return new CustodyRecord(new RequestId(entry.getUUID("request")), entry.getString("custodyId"),
                entry.getUUID("owner"), entry.getString("itemKey"), entry.getLong("quantity"),
                entry.getString("contentHash"), CustodyState.valueOf(entry.getString("state")));
    }

    private static CompoundTag writeEntry(CustodyRecord record) {
        CompoundTag entry = new CompoundTag();
        entry.putUUID("request", record.requestId().value());
        entry.putString("custodyId", record.custodyId());
        entry.putUUID("owner", record.owner());
        entry.putString("itemKey", record.itemKey());
        entry.putLong("quantity", record.quantity());
        entry.putString("contentHash", record.contentHash());
        entry.putString("state", record.state().name());
        entry.putString("checksum", checksum(record));
        return entry;
    }

    private static String checksum(CustodyRecord record) {
        return EconomyRecordChecksum.sha256(record.requestId().value() + "|" + record.custodyId() + "|"
                + record.owner() + "|" + record.itemKey() + "|" + record.quantity() + "|"
                + record.contentHash() + "|" + record.state());
    }

    private static boolean allowed(CustodyState expected, CustodyState next) {
        return switch (expected) {
            case HELD -> next == CustodyState.DELIVERED || next == CustodyState.RELEASED;
            case DELIVERED -> next == CustodyState.CLAIMED;
            case CLAIMED, RELEASED -> false;
        };
    }
}
