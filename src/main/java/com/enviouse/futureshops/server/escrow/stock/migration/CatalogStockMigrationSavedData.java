package com.enviouse.futureshops.server.escrow.stock.migration;

import com.enviouse.futureshops.server.SavedDataMigrations;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
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

public final class CatalogStockMigrationSavedData extends SavedData {
    public static final String DATA_NAME =
            "futureshops_catalog_stock_migration";
    public static final int CURRENT_VERSION = 1;

    private static final int MAXIMUM_DETAIL_LENGTH = 512;

    private CatalogStockMigrationStage stage =
            CatalogStockMigrationStage.UNINITIALIZED;
    private List<CatalogStockSeedEntry> snapshotEntries = List.of();
    private String snapshotFingerprint = "";
    private int nextEntryIndex;
    private StockKey lastCompletedKey;
    private UUID lastCompletedRequest;
    private CatalogStockMigrationFailure failure =
            CatalogStockMigrationFailure.NONE;
    private String failureDetail = "";
    private long completionSequence = -1L;

    public static CatalogStockMigrationSavedData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(
                CatalogStockMigrationSavedData::load,
                CatalogStockMigrationSavedData::new,
                DATA_NAME);
    }

    public static CatalogStockMigrationSavedData load(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        requireType(tag, "schemaVersion", Tag.TAG_INT);
        int version = SavedDataMigrations.readVersion(tag);
        if (version != CURRENT_VERSION) {
            throw new IllegalStateException(
                    "Catalog stock migration schema is unsupported");
        }
        requireType(tag, "stage", Tag.TAG_STRING);
        requireType(tag, "snapshotFingerprint", Tag.TAG_STRING);
        requireType(tag, "snapshotEntries", Tag.TAG_LIST);
        requireType(tag, "nextEntryIndex", Tag.TAG_INT);
        requireType(tag, "failure", Tag.TAG_STRING);
        requireType(tag, "failureDetail", Tag.TAG_STRING);
        requireType(tag, "completionSequence", Tag.TAG_LONG);
        if (tag.contains("lastCompletedShop")
                != tag.contains("lastCompletedListing")
                || tag.contains("lastCompletedShop")
                != tag.contains("lastCompletedRequest")) {
            throw new IllegalStateException(
                    "Catalog stock migration cursor is incomplete");
        }
        if (tag.contains("lastCompletedShop")) {
            requireType(tag, "lastCompletedShop", Tag.TAG_STRING);
            requireType(tag, "lastCompletedListing", Tag.TAG_STRING);
            if (!tag.hasUUID("lastCompletedRequest")) {
                throw new IllegalStateException(
                        "Catalog stock migration request is malformed");
            }
        }

        CatalogStockMigrationSavedData data =
                new CatalogStockMigrationSavedData();
        data.stage = parseStage(tag.getString("stage"));
        data.snapshotFingerprint = tag.getString("snapshotFingerprint");
        data.nextEntryIndex = tag.getInt("nextEntryIndex");
        data.failure = parseFailure(tag.getString("failure"));
        data.failureDetail = tag.getString("failureDetail");
        data.completionSequence = tag.getLong("completionSequence");
        if (tag.contains("lastCompletedShop")) {
            data.lastCompletedKey = new StockKey(
                    tag.getString("lastCompletedShop"),
                    tag.getString("lastCompletedListing"));
            data.lastCompletedRequest =
                    tag.getUUID("lastCompletedRequest");
        }

        ListTag encodedEntries = tag.getList(
                "snapshotEntries", Tag.TAG_COMPOUND);
        Tag rawEntries = tag.get("snapshotEntries");
        if (rawEntries instanceof ListTag rawList
                && !rawList.isEmpty()
                && rawList.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalStateException(
                    "Catalog stock migration entry list is malformed");
        }
        List<CatalogStockSeedEntry> entries =
                new ArrayList<>(encodedEntries.size());
        for (int index = 0; index < encodedEntries.size(); index++) {
            entries.add(readEntry(encodedEntries.getCompound(index)));
        }
        data.snapshotEntries = List.copyOf(entries);
        data.validateState();
        return data;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        tag.putString("stage", stage.name());
        tag.putString("snapshotFingerprint", snapshotFingerprint);
        ListTag entries = new ListTag();
        for (CatalogStockSeedEntry entry : snapshotEntries) {
            entries.add(writeEntry(entry));
        }
        tag.put("snapshotEntries", entries);
        tag.putInt("nextEntryIndex", nextEntryIndex);
        if (lastCompletedKey != null) {
            tag.putString("lastCompletedShop",
                    lastCompletedKey.shopId());
            tag.putString("lastCompletedListing",
                    lastCompletedKey.listingId());
            tag.putUUID("lastCompletedRequest", lastCompletedRequest);
        }
        tag.putString("failure", failure.name());
        tag.putString("failureDetail", failureDetail);
        tag.putLong("completionSequence", completionSequence);
        return tag;
    }

    public synchronized void initializeSnapshot(
            CatalogStockSeedSnapshot snapshot
    ) {
        requireStage(CatalogStockMigrationStage.UNINITIALIZED);
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        snapshotEntries = snapshot.entries();
        snapshotFingerprint = snapshot.fingerprint();
        stage = CatalogStockMigrationStage.SNAPSHOT_PENDING;
        setDirty();
    }

    public synchronized void markSnapshotDurable() {
        requireStage(CatalogStockMigrationStage.SNAPSHOT_PENDING);
        stage = CatalogStockMigrationStage.IMPORTING;
        setDirty();
    }

    public synchronized void advance(
            CatalogStockSeedEntry entry,
            UUID completionRequest
    ) {
        requireStage(CatalogStockMigrationStage.IMPORTING);
        entry = Objects.requireNonNull(entry, "entry");
        completionRequest = Objects.requireNonNull(
                completionRequest, "completionRequest");
        if (nextEntryIndex >= snapshotEntries.size()
                || !snapshotEntries.get(nextEntryIndex).equals(entry)) {
            throw new IllegalStateException(
                    "Catalog stock migration cursor does not match entry");
        }
        UUID expected = CatalogStockMigrationIds.entryCompletion(
                snapshot(), entry);
        if (!expected.equals(completionRequest)) {
            throw new IllegalArgumentException(
                    "Catalog stock migration completion request is invalid");
        }
        nextEntryIndex++;
        lastCompletedKey = entry.key();
        lastCompletedRequest = completionRequest;
        setDirty();
    }

    public synchronized void markImportsComplete() {
        requireStage(CatalogStockMigrationStage.IMPORTING);
        if (nextEntryIndex != snapshotEntries.size()) {
            throw new IllegalStateException(
                    "Catalog stock migration imports are incomplete");
        }
        stage = CatalogStockMigrationStage.IMPORTS_COMPLETE;
        setDirty();
    }

    public synchronized void markVerified(long sequence) {
        requireStage(CatalogStockMigrationStage.IMPORTS_COMPLETE);
        if (sequence < 0L) {
            throw new IllegalArgumentException(
                    "Catalog stock completion sequence is invalid");
        }
        completionSequence = sequence;
        stage = CatalogStockMigrationStage.VERIFIED;
        setDirty();
    }

    public synchronized void markComplete() {
        requireStage(CatalogStockMigrationStage.VERIFIED);
        stage = CatalogStockMigrationStage.COMPLETE;
        setDirty();
    }

    public synchronized void fail(
            CatalogStockMigrationFailure failure,
            String detail
    ) {
        failure = Objects.requireNonNull(failure, "failure");
        detail = Objects.requireNonNull(detail, "detail").trim();
        if (failure == CatalogStockMigrationFailure.NONE
                || detail.isEmpty()
                || detail.length() > MAXIMUM_DETAIL_LENGTH) {
            throw new IllegalArgumentException(
                    "Catalog stock migration failure is invalid");
        }
        if (stage == CatalogStockMigrationStage.COMPLETE) {
            throw new IllegalStateException(
                    "Completed catalog stock migration cannot fail");
        }
        if (stage == CatalogStockMigrationStage.VERIFIED) {
            throw new IllegalStateException(
                    "Verified catalog stock migration cannot fail");
        }
        if (stage == CatalogStockMigrationStage.FAILED) {
            return;
        }
        this.failure = failure;
        failureDetail = detail;
        stage = CatalogStockMigrationStage.FAILED;
        setDirty();
    }

    public synchronized boolean canRetryMaterializedState() {
        return stage == CatalogStockMigrationStage.FAILED
                && failure
                == CatalogStockMigrationFailure.STOCK_STORE_NOT_EMPTY
                && snapshotEntries.isEmpty()
                && snapshotFingerprint.isEmpty()
                && nextEntryIndex == 0
                && lastCompletedKey == null
                && lastCompletedRequest == null
                && completionSequence == -1L;
    }

    public synchronized void retryMaterializedState() {
        if (!canRetryMaterializedState()) {
            throw new IllegalStateException(
                    "Catalog stock migration failure cannot be retried");
        }
        failure = CatalogStockMigrationFailure.NONE;
        failureDetail = "";
        stage = CatalogStockMigrationStage.UNINITIALIZED;
        setDirty();
    }

    public synchronized void recordMaterializedStateFailure(
            String detail
    ) {
        if (!canRetryMaterializedState()) {
            throw new IllegalStateException(
                    "Catalog stock migration failure cannot be updated");
        }
        detail = Objects.requireNonNull(detail, "detail").trim();
        if (detail.isEmpty()
                || detail.length() > MAXIMUM_DETAIL_LENGTH) {
            throw new IllegalArgumentException(
                    "Catalog stock migration failure detail is invalid");
        }
        failureDetail = detail;
        setDirty();
    }

    public synchronized CatalogStockMigrationStage stage() {
        return stage;
    }

    public synchronized CatalogStockMigrationFailure failure() {
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

    public synchronized long completionSequence() {
        return completionSequence;
    }

    public synchronized CatalogStockSeedSnapshot snapshot() {
        if (stage == CatalogStockMigrationStage.UNINITIALIZED
                || snapshotFingerprint.isEmpty()) {
            throw new IllegalStateException(
                    "Catalog stock migration snapshot is unavailable");
        }
        return new CatalogStockSeedSnapshot(
                snapshotEntries, snapshotFingerprint);
    }

    public synchronized Optional<CatalogStockSeedEntry> nextEntry() {
        if (nextEntryIndex >= snapshotEntries.size()) {
            return Optional.empty();
        }
        return Optional.of(snapshotEntries.get(nextEntryIndex));
    }

    public synchronized CatalogStockMigrationResult result(
            int processedEntries,
            String detail
    ) {
        return new CatalogStockMigrationResult(
                stage, processedEntries, nextEntryIndex,
                snapshotEntries.size(), failure,
                Objects.requireNonNull(detail, "detail"),
                completionSequence);
    }

    private void validateState() {
        if (nextEntryIndex < 0
                || nextEntryIndex > snapshotEntries.size()
                || failureDetail.length() > MAXIMUM_DETAIL_LENGTH
                || completionSequence < -1L) {
            throw new IllegalStateException(
                    "Catalog stock migration state is invalid");
        }
        if (stage == CatalogStockMigrationStage.UNINITIALIZED) {
            if (!snapshotEntries.isEmpty()
                    || !snapshotFingerprint.isEmpty()
                    || nextEntryIndex != 0
                    || lastCompletedKey != null
                    || lastCompletedRequest != null
                    || failure != CatalogStockMigrationFailure.NONE
                    || !failureDetail.isEmpty()
                    || completionSequence != -1L) {
                throw new IllegalStateException(
                        "Uninitialized catalog stock migration has state");
            }
            return;
        }
        if (stage == CatalogStockMigrationStage.FAILED
                && snapshotFingerprint.isEmpty()) {
            if (!snapshotEntries.isEmpty()
                    || nextEntryIndex != 0
                    || lastCompletedKey != null
                    || lastCompletedRequest != null
                    || failure == CatalogStockMigrationFailure.NONE
                    || failureDetail.isEmpty()
                    || completionSequence != -1L) {
                throw new IllegalStateException(
                        "Failed catalog stock migration has invalid state");
            }
            return;
        }
        CatalogStockSeedSnapshot snapshot;
        try {
            snapshot = new CatalogStockSeedSnapshot(
                    snapshotEntries, snapshotFingerprint);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Catalog stock migration snapshot is corrupt", exception);
        }
        if ((lastCompletedKey == null) != (lastCompletedRequest == null)
                || (nextEntryIndex == 0) != (lastCompletedKey == null)) {
            throw new IllegalStateException(
                    "Catalog stock migration cursor receipt is incomplete");
        }
        if (nextEntryIndex > 0) {
            CatalogStockSeedEntry last =
                    snapshotEntries.get(nextEntryIndex - 1);
            if (!last.key().equals(lastCompletedKey)
                    || !CatalogStockMigrationIds.entryCompletion(
                    snapshot, last).equals(lastCompletedRequest)) {
                throw new IllegalStateException(
                        "Catalog stock migration cursor receipt is invalid");
            }
        }
        if ((stage == CatalogStockMigrationStage.IMPORTS_COMPLETE
                || stage == CatalogStockMigrationStage.VERIFIED
                || stage == CatalogStockMigrationStage.COMPLETE)
                && nextEntryIndex != snapshotEntries.size()) {
            throw new IllegalStateException(
                    "Catalog stock migration completion is premature");
        }
        if (stage == CatalogStockMigrationStage.VERIFIED
                || stage == CatalogStockMigrationStage.COMPLETE) {
            if (completionSequence < 0L) {
                throw new IllegalStateException(
                        "Catalog stock migration completion sequence is missing");
            }
        } else if (completionSequence != -1L) {
            throw new IllegalStateException(
                    "Catalog stock migration sequence is premature");
        }
        if (stage == CatalogStockMigrationStage.FAILED) {
            if (failure == CatalogStockMigrationFailure.NONE
                    || failureDetail.isEmpty()) {
                throw new IllegalStateException(
                        "Failed catalog stock migration lacks a reason");
            }
        } else if (failure != CatalogStockMigrationFailure.NONE
                || !failureDetail.isEmpty()) {
            throw new IllegalStateException(
                    "Active catalog stock migration has failure state");
        }
    }

    private static CompoundTag writeEntry(CatalogStockSeedEntry entry) {
        CompoundTag tag = new CompoundTag();
        tag.putString("shopId", entry.key().shopId());
        tag.putString("listingId", entry.key().listingId());
        tag.putBoolean("unlimited", entry.unlimited());
        tag.putLong("configuredQuantity", entry.configuredQuantity());
        tag.putLong("availableQuantity", entry.availableQuantity());
        tag.putString("configFingerprint", entry.configFingerprint());
        return tag;
    }

    private static CatalogStockSeedEntry readEntry(CompoundTag tag) {
        requireType(tag, "shopId", Tag.TAG_STRING);
        requireType(tag, "listingId", Tag.TAG_STRING);
        requireType(tag, "unlimited", Tag.TAG_BYTE);
        requireType(tag, "configuredQuantity", Tag.TAG_LONG);
        requireType(tag, "availableQuantity", Tag.TAG_LONG);
        requireType(tag, "configFingerprint", Tag.TAG_STRING);
        try {
            return new CatalogStockSeedEntry(
                    new StockKey(tag.getString("shopId"),
                            tag.getString("listingId")),
                    tag.getBoolean("unlimited"),
                    tag.getLong("configuredQuantity"),
                    tag.getLong("availableQuantity"),
                    tag.getString("configFingerprint"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Catalog stock migration entry is invalid", exception);
        }
    }

    private static CatalogStockMigrationStage parseStage(String value) {
        try {
            return CatalogStockMigrationStage.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Catalog stock migration stage is invalid", exception);
        }
    }

    private static CatalogStockMigrationFailure parseFailure(String value) {
        try {
            return CatalogStockMigrationFailure.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Catalog stock migration failure is invalid", exception);
        }
    }

    private static void requireType(
            CompoundTag tag,
            String key,
            int type
    ) {
        if (!tag.contains(key, type)) {
            throw new IllegalStateException(
                    "Catalog stock migration field is missing or malformed");
        }
    }

    private void requireStage(CatalogStockMigrationStage expected) {
        if (stage != expected) {
            throw new IllegalStateException(
                    "Catalog stock migration stage rejects transition");
        }
    }
}
