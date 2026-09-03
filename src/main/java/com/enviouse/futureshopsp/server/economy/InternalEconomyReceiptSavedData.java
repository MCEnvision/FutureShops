package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.MutationKind;
import com.enviouse.futureshopsp.api.economy.MutationReceipt;
import com.enviouse.futureshopsp.api.economy.RequestId;
import com.enviouse.futureshopsp.server.SavedDataMigrations;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/** Versioned and checksummed receipt store for internal provider outcomes. */
public final class InternalEconomyReceiptSavedData extends SavedData implements InternalEconomyReceiptStore {
    public static final String DATA_NAME = "futureshops_internal_economy_receipts";
    private static final int CURRENT_VERSION = 1;
    private static final int MAX_RECORDS = 10_000;
    private static final int MAX_OPERATION_LENGTH = 256;

    private final Map<RequestId, MutationReceipt> receipts = new LinkedHashMap<>();
    private boolean integrityValid = true;
    private boolean cleanMarkerValid = true;

    public static InternalEconomyReceiptSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(InternalEconomyReceiptSavedData::new,
                        InternalEconomyReceiptSavedData::load, null), DATA_NAME);
    }

    public static InternalEconomyReceiptSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        InternalEconomyReceiptSavedData data = new InternalEconomyReceiptSavedData();
        int version = SavedDataMigrations.readVersion(tag);
        if (version > CURRENT_VERSION) {
            data.integrityValid = false;
            return data;
        }
        SavedDataMigrations.needsMigration(DATA_NAME, version, CURRENT_VERSION);
        if (tag.contains("cleanMarker", Tag.TAG_BYTE)) {
            data.cleanMarkerValid = tag.getBoolean("cleanMarker");
        }
        Tag rawEntries = tag.get("receipts");
        if (rawEntries != null && !(rawEntries instanceof ListTag)) {
            data.integrityValid = false;
            return data;
        }
        ListTag entries = rawEntries instanceof ListTag list ? list : new ListTag();
        if (entries.size() > MAX_RECORDS) {
            data.integrityValid = false;
            return data;
        }
        Map<RequestId, MutationReceipt> loaded = new LinkedHashMap<>();
        for (Tag raw : entries) {
            if (!(raw instanceof CompoundTag entry)) {
                data.integrityValid = false;
                continue;
            }
            try {
                MutationReceipt receipt = readEntry(entry);
                if (!entry.getString("checksum").equals(checksum(receipt))) {
                    data.integrityValid = false;
                    continue;
                }
                if (loaded.put(receipt.requestId(), receipt) != null) {
                    data.integrityValid = false;
                }
            } catch (RuntimeException exception) {
                data.integrityValid = false;
            }
        }
        if (data.integrityValid) {
            data.receipts.putAll(loaded);
        }
        return data;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        ListTag entries = new ListTag();
        for (MutationReceipt receipt : receipts.values()) {
            entries.add(writeEntry(receipt));
        }
        tag.put("receipts", entries);
        tag.putBoolean("cleanMarker", cleanMarkerValid);
        return tag;
    }

    @Override
    public synchronized Optional<MutationReceipt> find(RequestId requestId) {
        return Optional.ofNullable(receipts.get(requestId));
    }

    @Override
    public synchronized void put(MutationReceipt receipt) {
        if (receipts.size() >= MAX_RECORDS && !receipts.containsKey(receipt.requestId())) {
            throw new IllegalStateException("receipt record limit reached");
        }
        MutationReceipt existing = receipts.get(receipt.requestId());
        if (existing != null && !existing.equals(receipt)) {
            throw new IllegalStateException("receipt request conflicts with existing outcome");
        }
        if (existing == null) {
            receipts.put(receipt.requestId(), receipt);
            setDirty();
        }
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

    private static MutationReceipt readEntry(CompoundTag entry) {
        if (!entry.hasUUID("request") || !entry.contains("kind", Tag.TAG_STRING)
                || !entry.contains("amount", Tag.TAG_LONG) || !entry.contains("operation", Tag.TAG_STRING)) {
            throw new IllegalArgumentException("receipt record is incomplete");
        }
        String operation = entry.getString("operation");
        if (operation.isBlank() || operation.length() > MAX_OPERATION_LENGTH) {
            throw new IllegalArgumentException("receipt operation is invalid");
        }
        OptionalLong resulting = entry.contains("resultingBalance", Tag.TAG_LONG)
                ? OptionalLong.of(entry.getLong("resultingBalance")) : OptionalLong.empty();
        return new MutationReceipt(new RequestId(entry.getUUID("request")),
                MutationKind.valueOf(entry.getString("kind")), entry.getLong("amount"), operation, resulting);
    }

    private static CompoundTag writeEntry(MutationReceipt receipt) {
        CompoundTag entry = new CompoundTag();
        entry.putUUID("request", receipt.requestId().value());
        entry.putString("kind", receipt.kind().name());
        entry.putLong("amount", receipt.amountMinorUnits());
        entry.putString("operation", receipt.externalOperationId());
        if (receipt.resultingBalanceMinorUnits().isPresent()) {
            entry.putLong("resultingBalance", receipt.resultingBalanceMinorUnits().getAsLong());
        }
        entry.putString("checksum", checksum(receipt));
        return entry;
    }

    private static String checksum(MutationReceipt receipt) {
        return EconomyRecordChecksum.sha256(receipt.requestId().value() + "|" + receipt.kind() + "|"
                + receipt.amountMinorUnits() + "|" + receipt.externalOperationId() + "|"
                + (receipt.resultingBalanceMinorUnits().isPresent()
                ? Long.toString(receipt.resultingBalanceMinorUnits().getAsLong()) : ""));
    }
}
