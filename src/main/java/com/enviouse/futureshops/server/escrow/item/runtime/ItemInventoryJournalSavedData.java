package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.server.SavedDataMigrations;
import com.enviouse.futureshops.server.escrow.runtime.EscrowManagedSavedData;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeStoreBinding;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ItemInventoryJournalSavedData
        extends EscrowManagedSavedData {
    public static final String DATA_NAME =
            "futureshops_escrow_item_inventory_journal";
    public static final int CURRENT_VERSION = 1;

    private static final String SNAPSHOT_KEY = "snapshot";

    private final PersistentItemInventoryJournal repository =
            new PersistentItemInventoryJournal();

    public static ItemInventoryJournalSavedData load(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        if (tag.contains("schemaVersion")
                && !tag.contains("schemaVersion", Tag.TAG_INT)) {
            throw new IllegalStateException(
                    "Item inventory journal schema has the wrong type");
        }
        int version = SavedDataMigrations.readVersion(tag);
        if (version > CURRENT_VERSION) {
            throw new IllegalStateException(
                    "Item inventory journal schema is newer than this build");
        }
        if (version < 0) {
            throw new IllegalStateException(
                    "Item inventory journal schema is invalid");
        }
        SavedDataMigrations.needsMigration(DATA_NAME, version,
                CURRENT_VERSION);
        ItemInventoryJournalSavedData data =
                new ItemInventoryJournalSavedData();
        if (version == 0 && !tag.contains(SNAPSHOT_KEY)) {
            data.setDirty();
            return data;
        }
        if (!tag.contains(SNAPSHOT_KEY, Tag.TAG_BYTE_ARRAY)) {
            throw new IllegalStateException(
                    "Item inventory journal snapshot is missing");
        }
        try {
            data.repository.rebuild(
                    ItemInventoryJournalSnapshotCodec.decode(
                            tag.getByteArray(SNAPSHOT_KEY)));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Item inventory journal snapshot failed validation",
                    exception);
        }
        if (version < CURRENT_VERSION) {
            data.setDirty();
        }
        return data;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        tag.putByteArray(SNAPSHOT_KEY,
                ItemInventoryJournalSnapshotCodec.encode(
                        repository.snapshot()));
        return tag;
    }

    public static ItemInventoryJournalSavedData get(
            MinecraftServer server
    ) {
        return EscrowRuntimeStoreBinding.bind(server,
                server.overworld().getDataStorage().computeIfAbsent(
                        ItemInventoryJournalSavedData::load,
                        ItemInventoryJournalSavedData::new,
                        DATA_NAME));
    }

    public synchronized ItemInventoryJournalApplyResult preflightCommitted(
            ItemInventoryJournalTransition transition
    ) {
        return repository.preflightCommitted(transition);
    }

    public synchronized ItemInventoryJournalApplyResult applyCommitted(
            ItemInventoryJournalTransition transition
    ) {
        requireEscrowMutationPermit();
        ItemInventoryJournalApplyResult result =
                repository.applyCommitted(transition);
        if (!result.replayed()) {
            setDirty();
        }
        return result;
    }

    public synchronized ItemInventoryQuarantineAdministrationResult
    preflightAdministration(
            ItemInventoryQuarantineAdministration administration
    ) {
        return repository.preflightAdministration(administration);
    }

    public synchronized ItemInventoryQuarantineAdministrationResult
    applyAdministration(
            ItemInventoryQuarantineAdministration administration
    ) {
        requireEscrowMutationPermit();
        ItemInventoryQuarantineAdministrationResult result =
                repository.applyAdministration(administration);
        if (!result.replayed()) {
            setDirty();
        }
        return result;
    }

    public synchronized void replaceFromValidated(
            ItemInventoryJournalSavedData source
    ) {
        requireEscrowMutationPermit();
        Objects.requireNonNull(source, "source");
        if (source == this) {
            return;
        }
        repository.rebuild(source.snapshot());
        setDirty();
    }

    public synchronized Optional<ItemInventoryJournalEntry> find(
            UUID requestId
    ) {
        return repository.find(requestId);
    }

    public synchronized Optional<ItemInventoryTerminalTombstone>
    findTombstone(UUID requestId) {
        return repository.findTombstone(requestId);
    }

    public synchronized ItemInventoryJournalCompactionResult
    preflightCompaction(ItemInventoryJournalCompaction compaction) {
        return repository.preflightCompaction(compaction);
    }

    public synchronized ItemInventoryJournalCompactionResult
    applyCompaction(ItemInventoryJournalCompaction compaction) {
        requireEscrowMutationPermit();
        ItemInventoryJournalCompactionResult result =
                repository.applyCompaction(compaction);
        if (!result.replayed()) {
            setDirty();
        }
        return result;
    }

    public synchronized List<ItemInventoryJournalEntry> preparedForPlayer(
            UUID playerId,
            int limit
    ) {
        return repository.preparedForPlayer(playerId, limit);
    }

    public synchronized List<ItemInventoryJournalEntry> entriesForPlayer(
            UUID playerId,
            int limit
    ) {
        return repository.entriesForPlayer(playerId, limit);
    }

    public synchronized boolean playerQuarantined(UUID playerId) {
        return repository.playerQuarantined(playerId);
    }

    public synchronized Optional<ItemInventoryQuarantineInspection>
    inspectQuarantine(UUID requestId) {
        return repository.inspectQuarantine(requestId);
    }

    public synchronized long revision() {
        return repository.revision();
    }

    public synchronized boolean hasLaterRequestForPlayer(
            UUID playerId,
            UUID requestId
    ) {
        return repository.hasLaterRequestForPlayer(playerId, requestId);
    }

    public synchronized ItemInventoryJournalSnapshot snapshot() {
        return repository.snapshot();
    }

    public synchronized boolean hasMaterializedState() {
        return repository.hasMaterializedState();
    }
}
