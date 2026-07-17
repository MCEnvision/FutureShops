package com.enviouse.futureshops.server.economy.migration;

import com.enviouse.futureshops.server.SavedDataMigrations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class LegacyBalanceMigrationSavedData extends SavedData {
    public static final String DATA_NAME = "futureshops_legacy_balance_migration";
    private static final int CURRENT_VERSION = 1;
    private static final int MAXIMUM_DETAIL_LENGTH = 512;

    private LegacyBalanceMigrationStage stage =
            LegacyBalanceMigrationStage.UNINITIALIZED;
    private List<LegacyBalanceEntry> snapshotEntries = List.of();
    private String snapshotFingerprint = "";
    private int nextEntryIndex;
    private UUID lastCompletedPlayer;
    private UUID lastCompletedRequest;
    private LegacyBalanceMigrationFailure failure =
            LegacyBalanceMigrationFailure.NONE;
    private String failureDetail = "";

    public static LegacyBalanceMigrationSavedData load(CompoundTag tag) {
        requireType(tag, "schemaVersion", Tag.TAG_INT);
        int version = SavedDataMigrations.readVersion(tag);
        if (version != CURRENT_VERSION) {
            throw new IllegalStateException(
                    "Legacy balance migration schema is unsupported");
        }
        requireType(tag, "stage", Tag.TAG_STRING);
        requireType(tag, "snapshotFingerprint", Tag.TAG_STRING);
        requireType(tag, "nextEntryIndex", Tag.TAG_INT);
        requireType(tag, "failure", Tag.TAG_STRING);
        requireType(tag, "failureDetail", Tag.TAG_STRING);
        requireType(tag, "snapshotEntries", Tag.TAG_LIST);
        if (tag.contains("lastCompletedPlayer")
                && !tag.hasUUID("lastCompletedPlayer")) {
            throw new IllegalStateException(
                    "Legacy migration last player is malformed");
        }
        if (tag.contains("lastCompletedRequest")
                && !tag.hasUUID("lastCompletedRequest")) {
            throw new IllegalStateException(
                    "Legacy migration last request is malformed");
        }

        LegacyBalanceMigrationSavedData data =
                new LegacyBalanceMigrationSavedData();
        data.stage = parseStage(tag.getString("stage"));
        data.snapshotFingerprint = tag.getString("snapshotFingerprint");
        data.nextEntryIndex = tag.getInt("nextEntryIndex");
        data.failure = parseFailure(tag.getString("failure"));
        data.failureDetail = tag.getString("failureDetail");
        data.lastCompletedPlayer = tag.hasUUID("lastCompletedPlayer")
                ? tag.getUUID("lastCompletedPlayer") : null;
        data.lastCompletedRequest = tag.hasUUID("lastCompletedRequest")
                ? tag.getUUID("lastCompletedRequest") : null;

        ListTag entriesTag = (ListTag) tag.get("snapshotEntries");
        if (entriesTag.size() > LegacyBalanceSnapshot.MAXIMUM_ENTRIES) {
            throw new IllegalStateException(
                    "Legacy migration snapshot exceeds its entry limit");
        }
        List<LegacyBalanceEntry> entries = new ArrayList<>(entriesTag.size());
        for (Tag rawEntry : entriesTag) {
            if (!(rawEntry instanceof CompoundTag entryTag)
                    || !entryTag.hasUUID("player")
                    || !entryTag.contains("balance", Tag.TAG_LONG)) {
                throw new IllegalStateException(
                        "Legacy migration snapshot entry is malformed");
            }
            entries.add(new LegacyBalanceEntry(
                    entryTag.getUUID("player"), entryTag.getLong("balance")));
        }
        data.snapshotEntries = List.copyOf(entries);
        data.validateState();
        return data;
    }

    public static LegacyBalanceMigrationSavedData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(
                LegacyBalanceMigrationSavedData::load,
                LegacyBalanceMigrationSavedData::new,
                DATA_NAME);
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        tag.putString("stage", stage.name());
        tag.putString("snapshotFingerprint", snapshotFingerprint);
        tag.putInt("nextEntryIndex", nextEntryIndex);
        tag.putString("failure", failure.name());
        tag.putString("failureDetail", failureDetail);
        if (lastCompletedPlayer != null) {
            tag.putUUID("lastCompletedPlayer", lastCompletedPlayer);
            tag.putUUID("lastCompletedRequest", lastCompletedRequest);
        }
        ListTag entriesTag = new ListTag();
        for (LegacyBalanceEntry entry : snapshotEntries) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("player", entry.playerId());
            entryTag.putLong("balance", entry.balanceMinorUnits());
            entriesTag.add(entryTag);
        }
        tag.put("snapshotEntries", entriesTag);
        return tag;
    }

    public synchronized void initializeSnapshot(LegacyBalanceSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (stage != LegacyBalanceMigrationStage.UNINITIALIZED) {
            requireSnapshot(snapshot);
            return;
        }
        snapshotEntries = snapshot.entries();
        snapshotFingerprint = snapshot.fingerprint();
        stage = LegacyBalanceMigrationStage.SNAPSHOT_PENDING;
        setDirty();
    }

    public synchronized void markSnapshotDurable() {
        if (stage == LegacyBalanceMigrationStage.IMPORTING) {
            return;
        }
        requireStage(LegacyBalanceMigrationStage.SNAPSHOT_PENDING);
        stage = LegacyBalanceMigrationStage.IMPORTING;
        setDirty();
    }

    public synchronized void advance(LegacyBalanceEntry entry, UUID requestId) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(requestId, "requestId");
        requireStage(LegacyBalanceMigrationStage.IMPORTING);
        if (nextEntryIndex >= snapshotEntries.size()
                || !snapshotEntries.get(nextEntryIndex).equals(entry)
                || !WalletInitializationIds.legacyBalance(entry.playerId())
                .equals(requestId)) {
            throw new IllegalStateException(
                    "Legacy migration cursor advance does not match its snapshot");
        }
        nextEntryIndex++;
        lastCompletedPlayer = entry.playerId();
        lastCompletedRequest = requestId;
        setDirty();
    }

    public synchronized void markImportsComplete() {
        if (stage == LegacyBalanceMigrationStage.IMPORTS_COMPLETE
                || stage == LegacyBalanceMigrationStage.COMPLETE) {
            return;
        }
        requireStage(LegacyBalanceMigrationStage.IMPORTING);
        if (nextEntryIndex != snapshotEntries.size()) {
            throw new IllegalStateException(
                    "Legacy migration still has entries to import");
        }
        stage = LegacyBalanceMigrationStage.IMPORTS_COMPLETE;
        setDirty();
    }

    public synchronized void markComplete() {
        if (stage == LegacyBalanceMigrationStage.COMPLETE) {
            return;
        }
        requireStage(LegacyBalanceMigrationStage.IMPORTS_COMPLETE);
        stage = LegacyBalanceMigrationStage.COMPLETE;
        setDirty();
    }

    public synchronized void fail(LegacyBalanceMigrationFailure failure,
                                  String detail) {
        Objects.requireNonNull(failure, "failure");
        String safeDetail = Objects.requireNonNull(detail, "detail").trim();
        if (failure == LegacyBalanceMigrationFailure.NONE
                || safeDetail.length() > MAXIMUM_DETAIL_LENGTH) {
            throw new IllegalArgumentException(
                    "Legacy migration failure is invalid");
        }
        if (stage == LegacyBalanceMigrationStage.FAILED) {
            return;
        }
        this.failure = failure;
        failureDetail = safeDetail;
        stage = LegacyBalanceMigrationStage.FAILED;
        setDirty();
    }

    public synchronized LegacyBalanceMigrationStage stage() {
        return stage;
    }

    public synchronized LegacyBalanceMigrationFailure failure() {
        return failure;
    }

    public synchronized String failureDetail() {
        return failureDetail;
    }

    public synchronized int nextEntryIndex() {
        return nextEntryIndex;
    }

    public synchronized int totalEntries() {
        return snapshotEntries.size();
    }

    public synchronized LegacyBalanceSnapshot snapshot() {
        if (stage == LegacyBalanceMigrationStage.UNINITIALIZED) {
            throw new IllegalStateException(
                    "Legacy migration snapshot is not initialized");
        }
        return new LegacyBalanceSnapshot(
                snapshotEntries, snapshotFingerprint);
    }

    public synchronized Optional<LegacyBalanceEntry> nextEntry() {
        if (nextEntryIndex >= snapshotEntries.size()) {
            return Optional.empty();
        }
        return Optional.of(snapshotEntries.get(nextEntryIndex));
    }

    public synchronized List<UUID> negativeBalancePlayers() {
        return snapshotEntries.stream()
                .filter(entry -> entry.balanceMinorUnits() < 0L)
                .map(LegacyBalanceEntry::playerId)
                .toList();
    }

    public synchronized LegacyBalanceMigrationBatchResult result(
            int processedEntries,
            String detail
    ) {
        List<UUID> affectedPlayers = failure
                == LegacyBalanceMigrationFailure.NEGATIVE_LEGACY_BALANCE
                ? negativeBalancePlayers() : List.of();
        return new LegacyBalanceMigrationBatchResult(
                stage, processedEntries, nextEntryIndex,
                snapshotEntries.size(), failure, detail, affectedPlayers);
    }

    private void requireSnapshot(LegacyBalanceSnapshot snapshot) {
        if (!snapshotEntries.equals(snapshot.entries())
                || !snapshotFingerprint.equals(snapshot.fingerprint())) {
            throw new IllegalStateException(
                    "Legacy migration snapshot conflicts with persisted state");
        }
    }

    private void validateState() {
        if (failureDetail.length() > MAXIMUM_DETAIL_LENGTH
                || nextEntryIndex < 0
                || nextEntryIndex > snapshotEntries.size()) {
            throw new IllegalStateException(
                    "Legacy migration state is invalid");
        }
        if (stage == LegacyBalanceMigrationStage.UNINITIALIZED) {
            if (!snapshotEntries.isEmpty() || !snapshotFingerprint.isEmpty()
                    || nextEntryIndex != 0 || lastCompletedPlayer != null
                    || lastCompletedRequest != null
                    || failure != LegacyBalanceMigrationFailure.NONE
                    || !failureDetail.isEmpty()) {
                throw new IllegalStateException(
                        "Uninitialized legacy migration has persisted state");
            }
            return;
        }
        if (stage == LegacyBalanceMigrationStage.FAILED
                && snapshotEntries.isEmpty()
                && snapshotFingerprint.isEmpty()) {
            if (nextEntryIndex != 0 || lastCompletedPlayer != null
                    || lastCompletedRequest != null
                    || failure == LegacyBalanceMigrationFailure.NONE) {
                throw new IllegalStateException(
                        "Failed legacy migration has invalid empty state");
            }
            return;
        }
        try {
            new LegacyBalanceSnapshot(snapshotEntries, snapshotFingerprint);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Legacy migration snapshot is corrupt", exception);
        }
        if ((lastCompletedPlayer == null) != (lastCompletedRequest == null)
                || (nextEntryIndex == 0) != (lastCompletedPlayer == null)) {
            throw new IllegalStateException(
                    "Legacy migration cursor receipt is incomplete");
        }
        if (nextEntryIndex > 0) {
            LegacyBalanceEntry last = snapshotEntries.get(nextEntryIndex - 1);
            if (!last.playerId().equals(lastCompletedPlayer)
                    || !WalletInitializationIds.legacyBalance(last.playerId())
                    .equals(lastCompletedRequest)) {
                throw new IllegalStateException(
                        "Legacy migration cursor receipt is invalid");
            }
        }
        if ((stage == LegacyBalanceMigrationStage.IMPORTS_COMPLETE
                || stage == LegacyBalanceMigrationStage.COMPLETE)
                && nextEntryIndex != snapshotEntries.size()) {
            throw new IllegalStateException(
                    "Completed legacy imports have an incomplete cursor");
        }
        if (stage == LegacyBalanceMigrationStage.FAILED) {
            if (failure == LegacyBalanceMigrationFailure.NONE) {
                throw new IllegalStateException(
                        "Failed legacy migration is missing its failure code");
            }
        } else if (failure != LegacyBalanceMigrationFailure.NONE
                || !failureDetail.isEmpty()) {
            throw new IllegalStateException(
                    "Active legacy migration has failure metadata");
        }
    }

    private void requireStage(LegacyBalanceMigrationStage required) {
        if (stage != required) {
            throw new IllegalStateException(
                    "Legacy migration stage does not allow this transition");
        }
    }

    private static LegacyBalanceMigrationStage parseStage(String raw) {
        try {
            return LegacyBalanceMigrationStage.valueOf(raw);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Legacy migration stage is invalid", exception);
        }
    }

    private static LegacyBalanceMigrationFailure parseFailure(String raw) {
        try {
            return LegacyBalanceMigrationFailure.valueOf(raw);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Legacy migration failure is invalid", exception);
        }
    }

    private static void requireType(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) {
            throw new IllegalStateException(
                    "Legacy migration field is missing or malformed");
        }
    }
}
