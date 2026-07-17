package com.enviouse.futureshops.server.escrow.admin;

import com.enviouse.futureshops.server.SavedDataMigrations;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import com.enviouse.futureshops.server.escrow.runtime.EscrowManagedSavedData;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeStoreBinding;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class EscrowAdministrativeAuditSavedData extends EscrowManagedSavedData {
    public static final String DATA_NAME = "futureshops_escrow_administrative_audit";
    private static final int CURRENT_VERSION = 1;
    private static final int MAX_RECORDS = 1_000_000;

    private final Map<UUID, EscrowAdministrativeRecord> records = new LinkedHashMap<>();

    public static EscrowAdministrativeAuditSavedData load(CompoundTag tag) {
        if (tag.contains("schemaVersion")
                && !tag.contains("schemaVersion", Tag.TAG_INT)) {
            throw new IllegalStateException("Escrow administrative audit schema has the wrong type");
        }
        int version = SavedDataMigrations.readVersion(tag);
        if (version > CURRENT_VERSION) {
            throw new IllegalStateException("Escrow administrative audit schema is newer than this build");
        }
        if (version < 0) {
            throw new IllegalStateException("Escrow administrative audit schema is invalid");
        }
        SavedDataMigrations.needsMigration(DATA_NAME, version, CURRENT_VERSION);
        EscrowAdministrativeAuditSavedData data = new EscrowAdministrativeAuditSavedData();
        ListTag entries = readList(tag, version);
        if (entries.size() > MAX_RECORDS) {
            throw new IllegalStateException("Escrow administrative audit exceeds its record limit");
        }
        for (Tag raw : entries) {
            if (!(raw instanceof CompoundTag entry) || !entry.hasUUID("request")) {
                throw new IllegalStateException("Escrow administrative audit record is invalid");
            }
            EscrowAdministrativeAction action;
            try {
                action = EscrowAdministrativeAction.valueOf(requireString(entry, "action"));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Escrow administrative audit action is invalid", exception);
            }
            if (entry.contains("transaction") && !entry.hasUUID("transaction")) {
                throw new IllegalStateException("Escrow administrative audit transaction is invalid");
            }
            Optional<EscrowTransactionId> transactionId = entry.hasUUID("transaction")
                    ? Optional.of(new EscrowTransactionId(entry.getUUID("transaction")))
                    : Optional.empty();
            EscrowAdministrativeRecord record = new EscrowAdministrativeRecord(
                    entry.getUUID("request"),
                    requireString(entry, "actor"),
                    action,
                    transactionId,
                    requireString(entry, "reason"),
                    readInstant(entry),
                    requireBoolean(entry, "successful"),
                    requireString(entry, "outcome"));
            if (data.records.put(record.requestId(), record) != null) {
                throw new IllegalStateException("Duplicate escrow administrative audit request");
            }
        }
        if (version < CURRENT_VERSION) {
            data.setDirty();
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        ListTag entries = new ListTag();
        for (EscrowAdministrativeRecord record : records.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("request", record.requestId());
            entry.putString("actor", record.actor());
            entry.putString("action", record.action().name());
            record.transactionId().ifPresent(value -> entry.putUUID("transaction", value.value()));
            entry.putString("reason", record.reason());
            entry.putLong("created_seconds", record.createdAt().getEpochSecond());
            entry.putInt("created_nanos", record.createdAt().getNano());
            entry.putBoolean("successful", record.successful());
            entry.putString("outcome", record.outcome());
            entries.add(entry);
        }
        tag.put("records", entries);
        return tag;
    }

    public static EscrowAdministrativeAuditSavedData get(MinecraftServer server) {
        return EscrowRuntimeStoreBinding.bind(server,
                server.overworld().getDataStorage().computeIfAbsent(
                EscrowAdministrativeAuditSavedData::load,
                EscrowAdministrativeAuditSavedData::new,
                DATA_NAME));
    }

    public synchronized void replaceFromValidated(
            EscrowAdministrativeAuditSavedData source) {
        requireEscrowMutationPermit();
        Objects.requireNonNull(source, "source");
        if (source == this) {
            return;
        }
        Map<UUID, EscrowAdministrativeRecord> restored = source.snapshotForRestore();
        records.clear();
        records.putAll(restored);
        setDirty();
    }

    public synchronized AdminAuditApplyResult append(EscrowAdministrativeRecord record) {
        requireEscrowMutationPermit();
        return evaluate(record, true);
    }

    public synchronized AdminAuditApplyResult preflightAppend(EscrowAdministrativeRecord record) {
        return evaluate(record, false);
    }

    private AdminAuditApplyResult evaluate(EscrowAdministrativeRecord record, boolean commit) {
        java.util.Objects.requireNonNull(record, "record");
        EscrowAdministrativeRecord existing = records.get(record.requestId());
        if (existing != null) {
            if (!existing.equals(record)) {
                throw new AdminAuditConflictException("Administrative request ID was reused");
            }
            return new AdminAuditApplyResult(existing, false, true);
        }
        if (records.size() >= MAX_RECORDS) {
            throw new IllegalStateException("Escrow administrative audit exceeds its record limit");
        }
        if (commit) {
            records.put(record.requestId(), record);
            setDirty();
        }
        return new AdminAuditApplyResult(record, true, false);
    }

    public synchronized EscrowAdministrativeRecord getRecord(UUID requestId) {
        return records.get(requestId);
    }

    public synchronized List<EscrowAdministrativeRecord> latest(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 256));
        return records.values().stream()
                .skip(Math.max(0, records.size() - safeLimit))
                .toList();
    }

    public synchronized boolean hasMaterializedState() {
        return !records.isEmpty();
    }

    private static ListTag readList(CompoundTag tag, int version) {
        if (!tag.contains("records")) {
            if (version == CURRENT_VERSION) {
                throw new IllegalStateException("Escrow administrative audit records are missing");
            }
            return new ListTag();
        }
        Tag raw = tag.get("records");
        if (!(raw instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalStateException("Escrow administrative audit records have the wrong type");
        }
        return list;
    }

    private static String requireString(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            throw new IllegalStateException("Escrow administrative audit text is missing");
        }
        return tag.getString(key);
    }

    private static boolean requireBoolean(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_BYTE)) {
            throw new IllegalStateException("Escrow administrative audit result is missing");
        }
        byte value = tag.getByte(key);
        if (value != 0 && value != 1) {
            throw new IllegalStateException("Escrow administrative audit result is invalid");
        }
        return value == 1;
    }

    private static Instant readInstant(CompoundTag tag) {
        if (!tag.contains("created_seconds", Tag.TAG_LONG)
                || !tag.contains("created_nanos", Tag.TAG_INT)) {
            throw new IllegalStateException("Escrow administrative audit time is missing");
        }
        int nanos = tag.getInt("created_nanos");
        if (nanos < 0 || nanos > 999_999_999) {
            throw new IllegalStateException("Escrow administrative audit time is invalid");
        }
        try {
            return Instant.ofEpochSecond(tag.getLong("created_seconds"), nanos);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Escrow administrative audit time is invalid", exception);
        }
    }

    private synchronized Map<UUID, EscrowAdministrativeRecord> snapshotForRestore() {
        return new LinkedHashMap<>(records);
    }
}
