package com.enviouse.futureshops.server.economy;

import com.enviouse.futureshops.server.SavedDataMigrations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public class InternalBalanceSavedData extends SavedData {
    public static final String DATA_NAME = "futureshops_balances";
    private static final int CURRENT_VERSION = 2;
    private static final int MAXIMUM_ENTRIES = 1_000_000;

    private final Map<UUID, Long> balances = new HashMap<>();
    private boolean migrationSourceSealed;
    private boolean migrationArchiveReadOnly;
    private String migrationSnapshotFingerprint = "";

    public static InternalBalanceSavedData load(CompoundTag tag) {
        InternalBalanceSavedData data = new InternalBalanceSavedData();
        if (tag.contains("schemaVersion")
                && !tag.contains("schemaVersion", Tag.TAG_INT)) {
            throw new IllegalStateException(
                    "Internal balance schema is malformed");
        }
        int version = SavedDataMigrations.readVersion(tag);
        if (version < 0 || version > CURRENT_VERSION) {
            throw new IllegalStateException(
                    "Internal balance schema is unsupported");
        }
        SavedDataMigrations.needsMigration(DATA_NAME, version, CURRENT_VERSION);
        if (tag.contains("balances")
                && !tag.contains("balances", Tag.TAG_LIST)) {
            throw new IllegalStateException(
                    "Internal balance entries are malformed");
        }
        ListTag entries = tag.contains("balances", Tag.TAG_LIST)
                ? (ListTag) tag.get("balances") : new ListTag();
        if (entries.size() > MAXIMUM_ENTRIES) {
            throw new IllegalStateException(
                    "Internal balance entries exceed the limit");
        }
        for (Tag entryTag : entries) {
            if (!(entryTag instanceof CompoundTag entry)
                    || !entry.hasUUID("player")
                    || !entry.contains("balance", Tag.TAG_LONG)) {
                throw new IllegalStateException(
                        "Internal balance entry is malformed");
            }
            UUID playerId = entry.getUUID("player");
            if (data.balances.putIfAbsent(
                    playerId, entry.getLong("balance")) != null) {
                throw new IllegalStateException(
                        "Internal balance entry is duplicated");
            }
        }
        if (version >= 2) {
            if (!tag.contains("migrationSourceSealed", Tag.TAG_BYTE)
                    || !tag.contains("migrationArchiveReadOnly", Tag.TAG_BYTE)
                    || !tag.contains("migrationSnapshotFingerprint", Tag.TAG_STRING)) {
                throw new IllegalStateException(
                        "Internal balance migration metadata is malformed");
            }
            data.migrationSourceSealed = tag.getBoolean("migrationSourceSealed");
            data.migrationArchiveReadOnly =
                    tag.getBoolean("migrationArchiveReadOnly");
            data.migrationSnapshotFingerprint =
                    tag.getString("migrationSnapshotFingerprint");
            data.validateMigrationMetadata();
        } else {
            data.setDirty();
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (balances.size() > MAXIMUM_ENTRIES) {
            throw new IllegalStateException(
                    "Internal balance entries exceed the limit");
        }
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        ListTag entries = new ListTag();
        for (Map.Entry<UUID, Long> entry : balances.entrySet().stream()
                .sorted(Comparator.comparing(value -> value.getKey().toString()))
                .toList()) {
            CompoundTag balanceTag = new CompoundTag();
            balanceTag.putUUID("player", entry.getKey());
            balanceTag.putLong("balance", entry.getValue());
            entries.add(balanceTag);
        }
        tag.put("balances", entries);
        tag.putBoolean("migrationSourceSealed", migrationSourceSealed);
        tag.putBoolean("migrationArchiveReadOnly", migrationArchiveReadOnly);
        tag.putString("migrationSnapshotFingerprint",
                migrationSnapshotFingerprint);
        return tag;
    }

    public synchronized long getBalanceOrDefault(UUID playerUUID,
                                                 long defaultBalance) {
        Long stored = balances.get(playerUUID);
        if (stored != null) {
            return stored;
        }

        requireMutable();
        balances.put(playerUUID, defaultBalance);
        setDirty();
        return defaultBalance;
    }

    public synchronized void setBalance(UUID playerUUID, long amountMinorUnits) {
        requireMutable();
        balances.put(playerUUID, amountMinorUnits);
        setDirty();
    }

    public synchronized Map<UUID, Long> snapshotBalances() {
        return Collections.unmodifiableMap(new HashMap<>(balances));
    }

    public synchronized OptionalLong findBalance(UUID playerUUID) {
        Long balance = balances.get(Objects.requireNonNull(
                playerUUID, "playerUUID"));
        return balance == null
                ? OptionalLong.empty()
                : OptionalLong.of(balance);
    }

    public synchronized void sealMigrationSource(String snapshotFingerprint) {
        String fingerprint = requireFingerprint(snapshotFingerprint);
        if (migrationSourceSealed) {
            if (!migrationSnapshotFingerprint.equals(fingerprint)) {
                throw new IllegalStateException(
                        "Internal balance migration seal conflicts");
            }
            return;
        }
        migrationSourceSealed = true;
        migrationSnapshotFingerprint = fingerprint;
        setDirty();
    }

    public synchronized void markMigrationArchiveReadOnly(
            String snapshotFingerprint
    ) {
        String fingerprint = requireFingerprint(snapshotFingerprint);
        if (!migrationSourceSealed
                || !migrationSnapshotFingerprint.equals(fingerprint)) {
            throw new IllegalStateException(
                    "Internal balance archive fingerprint conflicts");
        }
        if (!migrationArchiveReadOnly) {
            migrationArchiveReadOnly = true;
            setDirty();
        }
    }

    public synchronized boolean isMigrationSourceSealed() {
        return migrationSourceSealed;
    }

    public synchronized boolean isMigrationArchiveReadOnly() {
        return migrationArchiveReadOnly;
    }

    public synchronized Optional<String> migrationSnapshotFingerprint() {
        return migrationSourceSealed
                ? Optional.of(migrationSnapshotFingerprint)
                : Optional.empty();
    }

    private void requireMutable() {
        if (migrationSourceSealed) {
            throw new IllegalStateException(
                    "Internal balances are sealed for wallet migration");
        }
    }

    private void validateMigrationMetadata() {
        if (migrationArchiveReadOnly && !migrationSourceSealed) {
            throw new IllegalStateException(
                    "Internal balance archive is not sealed");
        }
        if (migrationSourceSealed) {
            requireFingerprint(migrationSnapshotFingerprint);
        } else if (!migrationSnapshotFingerprint.isEmpty()) {
            throw new IllegalStateException(
                    "Internal balance fingerprint exists without a seal");
        }
    }

    private static String requireFingerprint(String value) {
        String fingerprint = Objects.requireNonNull(
                value, "snapshotFingerprint").trim();
        if (!fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "Internal balance fingerprint is invalid");
        }
        return fingerprint;
    }
}
