package com.enviouse.futureshops.server.escrow.stock;

import com.enviouse.futureshops.server.SavedDataMigrations;
import com.enviouse.futureshops.server.escrow.runtime.EscrowManagedSavedData;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeStoreBinding;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class StockSavedData extends EscrowManagedSavedData {
    public static final String DATA_NAME = "futureshops_escrow_stock";
    public static final int CURRENT_VERSION = 1;

    private static final String SNAPSHOT_KEY = "snapshot";

    private final PersistentStockRepository repository =
            new PersistentStockRepository();

    public static StockSavedData load(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        if (tag.contains("schemaVersion")
                && !tag.contains("schemaVersion", Tag.TAG_INT)) {
            throw new IllegalStateException(
                    "Stock store schema has the wrong type");
        }
        int version = SavedDataMigrations.readVersion(tag);
        if (version > CURRENT_VERSION) {
            throw new IllegalStateException(
                    "Stock store schema is newer than this build");
        }
        if (version < 0) {
            throw new IllegalStateException("Stock store schema is invalid");
        }
        SavedDataMigrations.needsMigration(DATA_NAME, version,
                CURRENT_VERSION);
        StockSavedData data = new StockSavedData();
        if (version == 0 && !tag.contains(SNAPSHOT_KEY)) {
            data.setDirty();
            return data;
        }
        if (!tag.contains(SNAPSHOT_KEY, Tag.TAG_BYTE_ARRAY)) {
            throw new IllegalStateException("Stock store snapshot is missing");
        }
        byte[] encoded = tag.getByteArray(SNAPSHOT_KEY);
        try {
            data.repository.rebuild(StockStoreSnapshotCodec.decode(encoded));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Stock store snapshot failed validation", exception);
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
                StockStoreSnapshotCodec.encode(repository.snapshot()));
        return tag;
    }

    public static StockSavedData get(MinecraftServer server) {
        return EscrowRuntimeStoreBinding.bind(server,
                server.overworld().getDataStorage().computeIfAbsent(
                        StockSavedData::load, StockSavedData::new,
                        DATA_NAME));
    }

    public synchronized StockCommandResult preflightCommitted(
            StockMutationCommand command
    ) {
        return repository.preflightCommitted(command);
    }

    public synchronized StockCommandResult applyCommitted(
            StockMutationCommand command
    ) {
        requireEscrowMutationPermit();
        StockCommandResult result = repository.applyCommitted(command);
        if (!result.replayed()) {
            setDirty();
        }
        return result;
    }

    public synchronized void replaceFromValidated(StockSavedData source) {
        requireEscrowMutationPermit();
        Objects.requireNonNull(source, "source");
        if (source == this) {
            return;
        }
        repository.rebuild(source.snapshot());
        setDirty();
    }

    public synchronized CatalogStockState listing(StockKey key) {
        return repository.listing(key);
    }

    public synchronized StockReservation reservation(
            StockReservationId reservationId
    ) {
        return repository.reservation(reservationId);
    }

    public synchronized List<StockReservation> reservationsForTransaction(
            UUID transactionId
    ) {
        return repository.reservationsForTransaction(transactionId);
    }

    public synchronized StockMutationReceipt receipt(UUID requestId) {
        return repository.receipt(requestId);
    }

    public synchronized StockCommandResult resultForRequest(
            UUID requestId,
            boolean replayed
    ) {
        return repository.resultForRequest(requestId, replayed);
    }

    public synchronized StockStoreSnapshot snapshot() {
        return repository.snapshot();
    }

    public synchronized StockConservationReport conservation() {
        return repository.conservation();
    }

    public synchronized boolean hasMaterializedState() {
        StockStoreSnapshot snapshot = repository.snapshot();
        return snapshot.storeRevision() != 0L
                || !snapshot.listings().isEmpty()
                || !snapshot.reservations().isEmpty()
                || !snapshot.receipts().isEmpty()
                || !snapshot.catalogFingerprint().equals(
                PersistentStockRepository.EMPTY_CATALOG_FINGERPRINT);
    }
}
