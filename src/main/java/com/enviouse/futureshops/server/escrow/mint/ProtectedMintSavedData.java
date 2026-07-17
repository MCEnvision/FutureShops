package com.enviouse.futureshops.server.escrow.mint;

import com.enviouse.futureshops.server.SavedDataMigrations;
import com.enviouse.futureshops.server.escrow.runtime.EscrowManagedSavedData;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeStoreBinding;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ProtectedMintSavedData extends EscrowManagedSavedData {
    public static final String DATA_NAME = "futureshops_escrow_protected_mints";
    public static final int CURRENT_VERSION = 1;

    private final ProtectedMintRepository repository = new ProtectedMintRepository();

    public static ProtectedMintSavedData load(CompoundTag tag) {
        if (tag.contains("schemaVersion")
                && !tag.contains("schemaVersion", Tag.TAG_INT)) {
            throw new IllegalStateException("Protected mint schema has the wrong type");
        }
        int version = SavedDataMigrations.readVersion(tag);
        if (version > CURRENT_VERSION) {
            throw new IllegalStateException("Protected mint schema is newer than this build");
        }
        if (version < 0) {
            throw new IllegalStateException("Protected mint schema is invalid");
        }
        SavedDataMigrations.needsMigration(DATA_NAME, version, CURRENT_VERSION);
        ListTag batchTags = requireList(tag, "batches", version);
        ListTag receiptTags = requireList(tag, "receipts", version);
        if (batchTags.size() > ProtectedMintRepository.MAX_BATCHES
                || receiptTags.size() > ProtectedMintRepository.MAX_RECEIPTS) {
            throw new IllegalStateException("Protected mint data exceeds entry limits");
        }
        Map<UUID, ProtectedMintBatch> batches = new HashMap<>();
        Map<UUID, ProtectedMintReceipt> receipts = new HashMap<>();
        long receiptReferences = 0L;
        try {
            for (Tag raw : batchTags) {
                ProtectedMintBatch batch = ProtectedMintNbtCodec.readBatch((CompoundTag) raw);
                if (batches.put(batch.batchId(), batch) != null) {
                    throw new IllegalStateException("Duplicate protected mint batch ID");
                }
            }
            for (Tag raw : receiptTags) {
                ProtectedMintReceipt receipt = ProtectedMintNbtCodec.readReceipt(
                        (CompoundTag) raw);
                receiptReferences = Math.addExact(receiptReferences,
                        receipt.sourceBatchId().isPresent() ? 1L : 0L);
                receiptReferences = Math.addExact(receiptReferences,
                        receipt.resultingBatchId().isPresent() ? 1L : 0L);
                if (receiptReferences > ProtectedMintRepository.MAX_RECEIPT_REFERENCES) {
                    throw new IllegalStateException(
                            "Protected mint receipt reference limit is exceeded");
                }
                if (receipts.put(receipt.receiptId(), receipt) != null) {
                    throw new IllegalStateException("Duplicate protected mint receipt ID");
                }
            }
            ProtectedMintSavedData data = new ProtectedMintSavedData();
            data.repository.restore(batches, receipts);
            if (version < CURRENT_VERSION) {
                data.setDirty();
            }
            return data;
        } catch (ProtectedMintConflictException | IllegalArgumentException
                 | ArithmeticException exception) {
            throw new IllegalStateException("Protected mint data failed validation", exception);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        ListTag batches = new ListTag();
        for (ProtectedMintBatch batch : repository.snapshotBatches().values().stream()
                .sorted(Comparator.comparing(value -> value.batchId().toString())).toList()) {
            batches.add(ProtectedMintNbtCodec.writeBatch(batch));
        }
        tag.put("batches", batches);
        ListTag receipts = new ListTag();
        for (ProtectedMintReceipt receipt : repository.snapshotReceipts().values().stream()
                .sorted(Comparator.comparing(value -> value.receiptId().toString())).toList()) {
            receipts.add(ProtectedMintNbtCodec.writeReceipt(receipt));
        }
        tag.put("receipts", receipts);
        return tag;
    }

    public static ProtectedMintSavedData get(MinecraftServer server) {
        return EscrowRuntimeStoreBinding.bind(server,
                server.overworld().getDataStorage().computeIfAbsent(
                        ProtectedMintSavedData::load, ProtectedMintSavedData::new,
                        DATA_NAME));
    }

    public synchronized void replaceFromValidated(ProtectedMintSavedData source) {
        requireEscrowMutationPermit();
        Objects.requireNonNull(source, "source");
        if (source == this) {
            return;
        }
        ProtectedMintStateSnapshot snapshot = source.snapshotForRestore();
        repository.restore(snapshot.batches(), snapshot.receipts());
        setDirty();
    }

    public synchronized ProtectedMintApplyResult applyCommitted(ProtectedMintJournalEvent event) {
        requireEscrowMutationPermit();
        ProtectedMintApplyResult result = repository.applyCommitted(event);
        if (!result.replayed()) {
            setDirty();
        }
        return result;
    }

    public synchronized ProtectedMintApplyResult preflightCommitted(
            ProtectedMintJournalEvent event) {
        return repository.preflightCommitted(event);
    }

    public synchronized ProtectedMintApplyResult authorizeCommitted(ProtectedMintBatch batch) {
        return applyCommitted(ProtectedMintJournalEvent.authorize(batch));
    }

    public synchronized void preflightIssueBatch(List<ProtectedMintJournalEvent> events) {
        repository.preflightIssueBatch(events);
    }

    public synchronized ProtectedMintApplyResult materializeCommitted(UUID transactionId,
                                                                      UUID batchId,
                                                                      String requestKey,
                                                                      int quantity,
                                                                      Instant now) {
        return applyCommitted(ProtectedMintJournalEvent.materialize(transactionId,
                batchId, requestKey, quantity, now));
    }

    public synchronized ProtectedMintApplyResult reserveCommitted(UUID transactionId,
                                                                  UUID batchId,
                                                                  String requestKey,
                                                                  int quantity,
                                                                  Instant now) {
        return applyCommitted(ProtectedMintJournalEvent.reserve(transactionId,
                batchId, requestKey, quantity, now));
    }

    public synchronized ProtectedMintApplyResult commitCommitted(UUID transactionId,
                                                                 UUID batchId,
                                                                 String requestKey,
                                                                 int quantity,
                                                                 Instant now) {
        return applyCommitted(ProtectedMintJournalEvent.commit(transactionId,
                batchId, requestKey, quantity, now));
    }

    public synchronized ProtectedMintApplyResult refundCommitted(UUID transactionId,
                                                                 UUID sourceBatchId,
                                                                 String requestKey,
                                                                 ProtectedMintState sourceState,
                                                                 int quantity,
                                                                 ProtectedMintBatch replacementBatch,
                                                                 Instant now) {
        return applyCommitted(ProtectedMintJournalEvent.refund(transactionId,
                sourceBatchId, requestKey, sourceState, quantity,
                replacementBatch, now));
    }

    public synchronized ProtectedMintApplyResult quarantineCommitted(UUID transactionId,
                                                                     UUID batchId,
                                                                     String requestKey,
                                                                     ProtectedMintState sourceState,
                                                                     int quantity,
                                                                     Instant now) {
        return applyCommitted(ProtectedMintJournalEvent.quarantine(transactionId,
                batchId, requestKey, sourceState, quantity, now));
    }

    public synchronized ProtectedMintBatch getBatch(UUID batchId) {
        return repository.getBatch(batchId);
    }

    public synchronized ProtectedMintReceipt receiptForRequest(String requestKey) {
        return repository.receiptForRequest(requestKey);
    }

    public synchronized ProtectedMintValidationResult validate(UUID batchId,
                                                               long denominationMinorUnits,
                                                               int authorizedCount,
                                                               String serverIdentityEvidence,
                                                               String checksumEvidence,
                                                               int requestedQuantity,
                                                               Optional<UUID>
                                                                       expectedReservationTransactionId) {
        return repository.validate(batchId, denominationMinorUnits, authorizedCount,
                serverIdentityEvidence, checksumEvidence, requestedQuantity,
                expectedReservationTransactionId);
    }

    public synchronized ProtectedMintLiabilityReport outstandingLiability() {
        return repository.outstandingLiability();
    }

    public synchronized ProtectedMintConservationReport conservation() {
        return repository.conservation();
    }

    public synchronized ProtectedMintLiabilitySnapshot liabilitySnapshot() {
        List<ProtectedMintBatchLiability> batches = repository.snapshotBatches().values().stream()
                .map(batch -> new ProtectedMintBatchLiability(
                        batch.batchId(), batch.denominationMinorUnits(),
                        batch.authorizedCount(), batch.authorizedQuantity(),
                        batch.availableQuantity(), batch.reservedQuantities(),
                        batch.serverIdentityEvidence(), batch.checksumEvidence()))
                .sorted(Comparator.comparing(value -> value.batchId().toString()))
                .toList();
        try {
            ProtectedMintConservationReport report = repository.conservation();
            return new ProtectedMintLiabilitySnapshot(
                    batches, report.conserved(), report.violations());
        } catch (ArithmeticException exception) {
            return new ProtectedMintLiabilitySnapshot(batches, false,
                    List.of("Protected mint local conservation arithmetic overflow"));
        }
    }

    public synchronized boolean hasMaterializedState() {
        return repository.hasMaterializedState();
    }

    private static ListTag requireList(CompoundTag tag, String key, int version) {
        if (!tag.contains(key)) {
            if (version == CURRENT_VERSION) {
                throw new IllegalStateException("Protected mint data is missing");
            }
            return new ListTag();
        }
        Tag raw = tag.get(key);
        if (!(raw instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalStateException("Protected mint data has the wrong type");
        }
        return list;
    }

    private synchronized ProtectedMintStateSnapshot snapshotForRestore() {
        return new ProtectedMintStateSnapshot(repository.snapshotBatches(),
                repository.snapshotReceipts());
    }

    private record ProtectedMintStateSnapshot(Map<UUID, ProtectedMintBatch> batches,
                                              Map<UUID, ProtectedMintReceipt> receipts) {
    }
}
