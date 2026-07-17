package com.enviouse.futureshops.server.escrow.custody;

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
import java.util.UUID;

public final class CustodySavedData extends EscrowManagedSavedData {
    public static final String DATA_NAME = "futureshops_escrow_custody";
    private static final int CURRENT_VERSION = 3;
    private static final int MAX_ENTRIES = 1_000_000;

    private final CustodyRepository repository = new CustodyRepository();
    private final CustodyPreparedRepository preparedRepository = new CustodyPreparedRepository();
    private final CustodyPreparedBatchRepository batchRepository =
            new CustodyPreparedBatchRepository();

    public static CustodySavedData load(CompoundTag tag) {
        if (tag.contains("schemaVersion")
                && !tag.contains("schemaVersion", Tag.TAG_INT)) {
            throw new IllegalStateException("Escrow custody schema has the wrong type");
        }
        int version = SavedDataMigrations.readVersion(tag);
        if (version < 0) {
            throw new IllegalStateException("Escrow custody schema cannot be negative");
        }
        if (version > CURRENT_VERSION) {
            throw new IllegalStateException("Escrow custody schema is newer than this build");
        }
        SavedDataMigrations.needsMigration(DATA_NAME, version, CURRENT_VERSION);
        Map<UUID, CustodyLot> lots = new HashMap<>();
        Map<UUID, CustodyOperationReceipt> receipts = new HashMap<>();
        Map<UUID, CustodyPreparedOperation> prepared = new HashMap<>();
        Map<UUID, CustodyPreparedBatch> batches = new HashMap<>();

        ListTag lotTags = requireCompoundList(tag, "lots", version);
        requireBound(lotTags.size(), "lots");
        for (Tag value : lotTags) {
            CustodyLot lot;
            try {
                lot = CustodyNbtCodec.readLot((CompoundTag) value);
            } catch (IllegalArgumentException | ArithmeticException exception) {
                throw new IllegalStateException("Invalid escrow custody lot", exception);
            }
            if (lots.put(lot.lotId(), lot) != null) {
                throw new IllegalStateException("Duplicate escrow custody lot ID");
            }
        }

        ListTag receiptTags = requireCompoundList(tag, "receipts", version);
        requireBound(receiptTags.size(), "receipts");
        for (Tag value : receiptTags) {
            CustodyOperationReceipt receipt;
            try {
                receipt = CustodyNbtCodec.readReceipt((CompoundTag) value);
            } catch (IllegalArgumentException | ArithmeticException exception) {
                throw new IllegalStateException("Invalid escrow custody receipt", exception);
            }
            if (receipts.put(receipt.receiptId(), receipt) != null) {
                throw new IllegalStateException("Duplicate escrow custody receipt ID");
            }
        }

        ListTag preparedTags = requireCompoundList(tag, "prepared", version);
        requireBound(preparedTags.size(), "prepared intents");
        for (Tag value : preparedTags) {
            CompoundTag entry = (CompoundTag) value;
            if (!entry.contains("payload", Tag.TAG_BYTE_ARRAY)) {
                throw new IllegalStateException("Prepared custody intent payload is missing");
            }
            CustodyPreparedOperation intent;
            try {
                intent = CustodyPreparedOperationCodec.decode(entry.getByteArray("payload"));
            } catch (IllegalArgumentException | ArithmeticException exception) {
                throw new IllegalStateException("Invalid prepared custody intent", exception);
            }
            if (prepared.put(intent.intentId(), intent) != null) {
                throw new IllegalStateException("Duplicate prepared custody intent ID");
            }
        }

        ListTag batchTags = requireCompoundList(tag, "batches", version);
        requireBound(batchTags.size(), "prepared batches");
        for (Tag value : batchTags) {
            CompoundTag entry = (CompoundTag) value;
            if (!entry.contains("payload", Tag.TAG_BYTE_ARRAY)) {
                throw new IllegalStateException("Prepared custody batch payload is missing");
            }
            CustodyPreparedBatch batch;
            try {
                batch = CustodyPreparedBatchCodec.decode(entry.getByteArray("payload"));
            } catch (IllegalArgumentException | ArithmeticException exception) {
                throw new IllegalStateException("Invalid prepared custody batch", exception);
            }
            if (batches.put(batch.batchId(), batch) != null) {
                throw new IllegalStateException("Duplicate prepared custody batch ID");
            }
        }
        if (version < 3) {
            migrateLegacyBatches(prepared, batches);
        }

        CustodySavedData data = new CustodySavedData();
        try {
            data.repository.restore(lots, receipts);
            data.preparedRepository.restore(prepared);
            data.batchRepository.restore(batches);
            validatePreparedLinks(prepared, lots, receipts);
            validateBatchLinks(batches, prepared);
        } catch (CustodyConflictException exception) {
            throw new IllegalStateException("Escrow custody data failed conservation checks", exception);
        }
        if (version < CURRENT_VERSION) {
            data.setDirty();
        }
        return data;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        Map<UUID, CustodyLot> lotSnapshot = repository.snapshotLots();
        Map<UUID, CustodyOperationReceipt> receiptSnapshot = repository.snapshotReceipts();
        Map<UUID, CustodyPreparedOperation> preparedSnapshot = preparedRepository.snapshot();
        Map<UUID, CustodyPreparedBatch> batchSnapshot = batchRepository.snapshot();
        requireBound(lotSnapshot.size(), "lots");
        requireBound(receiptSnapshot.size(), "receipts");
        requireBound(preparedSnapshot.size(), "prepared intents");
        requireBound(batchSnapshot.size(), "prepared batches");
        ListTag lots = new ListTag();
        for (CustodyLot lot : lotSnapshot.values()) {
            lots.add(CustodyNbtCodec.writeLot(lot));
        }
        tag.put("lots", lots);

        ListTag receipts = new ListTag();
        for (CustodyOperationReceipt receipt : receiptSnapshot.values()) {
            receipts.add(CustodyNbtCodec.writeReceipt(receipt));
        }
        tag.put("receipts", receipts);

        ListTag prepared = new ListTag();
        for (CustodyPreparedOperation intent : preparedSnapshot.values()) {
            CompoundTag value = new CompoundTag();
            value.putByteArray("payload", CustodyPreparedOperationCodec.encode(intent));
            prepared.add(value);
        }
        tag.put("prepared", prepared);

        ListTag batches = new ListTag();
        for (CustodyPreparedBatch batch : batchSnapshot.values()) {
            CompoundTag value = new CompoundTag();
            value.putByteArray("payload", CustodyPreparedBatchCodec.encode(batch));
            batches.add(value);
        }
        tag.put("batches", batches);
        return tag;
    }

    public static CustodySavedData get(MinecraftServer server) {
        return EscrowRuntimeStoreBinding.bind(server,
                server.overworld().getDataStorage().computeIfAbsent(
                        CustodySavedData::load, CustodySavedData::new, DATA_NAME));
    }

    public synchronized void replaceFromValidated(CustodySavedData source) {
        requireEscrowMutationPermit();
        Objects.requireNonNull(source, "source");
        if (source == this) {
            return;
        }
        CustodyStateSnapshot snapshot = source.snapshotForRestore();
        repository.restore(snapshot.lots(), snapshot.receipts());
        preparedRepository.restore(snapshot.prepared());
        batchRepository.restore(snapshot.batches());
        validatePreparedLinks(snapshot.prepared(), snapshot.lots(), snapshot.receipts());
        validateBatchLinks(snapshot.batches(), snapshot.prepared());
        setDirty();
    }

    public synchronized CustodyOperationResult reserveCommitted(CustodyLot lot) {
        requireEscrowMutationPermit();
        CustodyOperationResult result = repository.reserve(lot);
        if (!result.replayed()) {
            setDirty();
        }
        return result;
    }

    public synchronized CustodyOperationResult releaseCommitted(UUID lotId,
                                                                String requestKey,
                                                                CustodyTransferEvidence evidence,
                                                                Instant now) {
        requireEscrowMutationPermit();
        CustodyOperationResult result = repository.release(lotId, requestKey, evidence, now);
        if (!result.replayed()) {
            setDirty();
        }
        return result;
    }

    public synchronized CustodyOperationResult consumeCommitted(UUID lotId,
                                                                String requestKey,
                                                                CustodyTransferEvidence evidence,
                                                                Instant now) {
        requireEscrowMutationPermit();
        CustodyOperationResult result = repository.consume(lotId, requestKey, evidence, now);
        if (!result.replayed()) {
            setDirty();
        }
        return result;
    }

    public synchronized CustodyOperationResult quarantineCommitted(UUID lotId,
                                                                   String requestKey,
                                                                   CustodyTransferEvidence evidence,
                                                                   Instant now) {
        requireEscrowMutationPermit();
        CustodyOperationResult result = repository.quarantine(lotId, requestKey, evidence, now);
        if (!result.replayed()) {
            setDirty();
        }
        return result;
    }

    public synchronized CustodyOperationResult applyCommitted(CustodyMutation mutation) {
        requireEscrowMutationPermit();
        CustodyOperationReceipt receipt = Objects.requireNonNull(mutation, "mutation").receipt();
        CustodyPreparedBatch preparedBatch = batchRepository.forOperationRequest(
                receipt.requestKey());
        boolean legacySingleBatch = preparedBatch != null
                && preparedBatch.operations().size() == 1
                && preparedBatch.status() == CustodyBatchStatus.PREPARED;
        if (legacySingleBatch) {
            preparedBatch = preparedBatch.markApplying(preparedBatch.revision(),
                    receipt.createdAt().isBefore(preparedBatch.updatedAt())
                            ? preparedBatch.updatedAt() : receipt.createdAt());
            batchRepository.apply(preparedBatch);
        }
        CustodyOperationReceipt priorReceipt = repository.receiptForRequest(receipt.requestKey());
        CustodyPreparedOperation prepared = preparedRepository.forRequest(receipt.requestKey());
        Instant resolvedAt = prepared == null ? receipt.createdAt()
                : receipt.createdAt().isBefore(prepared.preparedAt())
                ? prepared.preparedAt() : receipt.createdAt();
        if (priorReceipt == null) {
            CustodyMutation expected = expectedMutation(mutation);
            if (!expected.equals(mutation)) {
                throw new CustodyConflictException("Committed custody mutation does not match its prior state");
            }
        }
        if (prepared != null) {
            prepared.resolve(receipt, resolvedAt);
        }
        CustodyOperationResult result = switch (receipt.operation()) {
            case RESERVE -> reserveCommitted(mutation.resultingLot());
            case RELEASE -> releaseCommitted(receipt.lotId(), receipt.requestKey(),
                    receipt.evidence(), receipt.createdAt());
            case CONSUME -> consumeCommitted(receipt.lotId(), receipt.requestKey(),
                    receipt.evidence(), receipt.createdAt());
            case QUARANTINE -> quarantineCommitted(receipt.lotId(), receipt.requestKey(),
                    receipt.evidence(), receipt.createdAt());
        };
        if (!result.receipt().equals(receipt)) {
            throw new CustodyConflictException("Committed custody mutation receipt does not match");
        }
        if (!result.replayed() && !result.lot().equals(mutation.resultingLot())) {
            throw new CustodyConflictException("Committed custody mutation lot does not match");
        }
        if (prepared != null) {
            CustodyPreparedResult resolved = preparedRepository.resolve(receipt, resolvedAt);
            if (!resolved.replayed()) {
                setDirty();
            }
        }
        if (legacySingleBatch) {
            CustodyPreparedBatch applied = preparedBatch.markApplied(preparedBatch.revision(),
                    preparedBatch.plannedEvidenceByLot(), resolvedAt);
            batchRepository.apply(applied);
            setDirty();
        }
        return result;
    }

    public synchronized CustodyOperationResult preflightCommitted(CustodyMutation mutation) {
        CustodyOperationReceipt receipt = Objects.requireNonNull(mutation, "mutation").receipt();
        CustodyOperationReceipt priorReceipt = repository.receiptForRequest(receipt.requestKey());
        CustodyPreparedOperation prepared = preparedRepository.forRequest(receipt.requestKey());
        Instant resolvedAt = prepared == null ? receipt.createdAt()
                : receipt.createdAt().isBefore(prepared.preparedAt())
                ? prepared.preparedAt() : receipt.createdAt();
        if (priorReceipt == null && !expectedMutation(mutation).equals(mutation)) {
            throw new CustodyConflictException("Committed custody mutation does not match its prior state");
        }
        if (prepared != null) {
            prepared.resolve(receipt, resolvedAt);
        }
        CustodyOperationResult result = switch (receipt.operation()) {
            case RESERVE -> repository.preflightReserve(mutation.resultingLot());
            case RELEASE -> repository.preflightRelease(receipt.lotId(), receipt.requestKey(),
                    receipt.evidence(), receipt.createdAt());
            case CONSUME -> repository.preflightConsume(receipt.lotId(), receipt.requestKey(),
                    receipt.evidence(), receipt.createdAt());
            case QUARANTINE -> repository.preflightQuarantine(receipt.lotId(), receipt.requestKey(),
                    receipt.evidence(), receipt.createdAt());
        };
        if (!result.receipt().equals(receipt)) {
            throw new CustodyConflictException("Committed custody mutation receipt does not match");
        }
        if (!result.replayed() && !result.lot().equals(mutation.resultingLot())) {
            throw new CustodyConflictException("Committed custody mutation lot does not match");
        }
        return result;
    }

    private CustodyMutation expectedMutation(CustodyMutation incoming) {
        CustodyOperationReceipt receipt = incoming.receipt();
        if (receipt.operation() == CustodyOperation.RESERVE) {
            return CustodyMutation.reserve(incoming.resultingLot());
        }
        CustodyLot held = repository.get(receipt.lotId());
        if (held == null) {
            throw new CustodyConflictException("Custody lot does not exist");
        }
        return CustodyMutation.terminal(held, receipt.operation(), receipt.requestKey(),
                receipt.evidence(), receipt.createdAt());
    }

    public synchronized CustodyPreparedResult prepareCommitted(CustodyPreparedOperation intent) {
        requireEscrowMutationPermit();
        CustodyPreparedBatch batch = singleBatch(intent);
        batchRepository.apply(batch);
        CustodyPreparedResult result = preparedRepository.prepare(intent);
        if (!result.replayed()) {
            setDirty();
        }
        return result;
    }

    public synchronized CustodyPreparedResult preflightPrepareCommitted(
            CustodyPreparedOperation intent
    ) {
        batchRepository.preflight(singleBatch(intent));
        return preparedRepository.preflight(intent);
    }

    public synchronized CustodyPreparedBatchResult preflightBatchCommitted(
            CustodyPreparedBatch batch
    ) {
        Objects.requireNonNull(batch, "batch");
        CustodyPreparedBatch current = batchRepository.get(batch.batchId());
        if (current == null) {
            preparedRepository.preflightAdditional(batch.operations().size());
            for (CustodyPreparedOperation operation : batch.operations()) {
                preparedRepository.preflight(operation);
            }
        }
        return batchRepository.preflight(batch);
    }

    public synchronized CustodyPreparedBatchResult applyBatchCommitted(
            CustodyPreparedBatch batch
    ) {
        requireEscrowMutationPermit();
        Objects.requireNonNull(batch, "batch");
        CustodyPreparedBatch current = batchRepository.get(batch.batchId());
        if (current == null) {
            preparedRepository.preflightAdditional(batch.operations().size());
            for (CustodyPreparedOperation operation : batch.operations()) {
                preparedRepository.preflight(operation);
            }
        }
        CustodyPreparedBatchResult result = batchRepository.apply(batch);
        if (current == null) {
            for (CustodyPreparedOperation operation : batch.operations()) {
                preparedRepository.prepare(operation);
            }
        }
        if (!result.replayed()) {
            setDirty();
        }
        return result;
    }

    public synchronized CustodyBatchApplyResult preflightBatchCommit(
            CustodyBatchCommit commit
    ) {
        Objects.requireNonNull(commit, "commit");
        boolean mutationsReplayed = true;
        if (commit.batch().status() == CustodyBatchStatus.APPLIED) {
            java.util.List<CustodyOperationResult> results =
                    repository.preflightBatch(commit.mutations());
            for (int index = 0; index < commit.mutations().size(); index++) {
                CustodyMutation mutation = commit.mutations().get(index);
                CustodyOperationResult result = results.get(index);
                validatePreparedMutation(mutation);
                if (!result.receipt().equals(mutation.receipt())
                        || (!result.replayed()
                        && !result.lot().equals(mutation.resultingLot()))) {
                    throw new CustodyConflictException(
                            "Committed custody batch mutation does not match");
                }
                mutationsReplayed &= result.replayed();
            }
        }
        CustodyPreparedBatchResult batchResult = preflightBatchCommitted(commit.batch());
        if (batchResult.replayed() && !mutationsReplayed
                && commit.batch().status() == CustodyBatchStatus.APPLIED) {
            throw new CustodyConflictException("Applied custody batch is only partially materialized");
        }
        return new CustodyBatchApplyResult(commit,
                batchResult.replayed() && mutationsReplayed);
    }

    public synchronized CustodyBatchApplyResult applyBatchCommit(CustodyBatchCommit commit) {
        requireEscrowMutationPermit();
        CustodyBatchApplyResult preflight = preflightBatchCommit(commit);
        if (preflight.replayed()) {
            return preflight;
        }
        if (commit.batch().status() == CustodyBatchStatus.APPLIED) {
            repository.applyBatch(commit.mutations());
            for (CustodyMutation mutation : commit.mutations()) {
                CustodyPreparedOperation prepared = preparedRepository.forRequest(
                        mutation.receipt().requestKey());
                Instant resolvedAt = mutation.receipt().createdAt().isBefore(prepared.preparedAt())
                        ? prepared.preparedAt() : mutation.receipt().createdAt();
                preparedRepository.resolve(mutation.receipt(), resolvedAt);
            }
        }
        applyBatchCommitted(commit.batch());
        setDirty();
        return new CustodyBatchApplyResult(commit, false);
    }

    public synchronized java.util.List<CustodyPreparedOperation> unresolvedPreparedOperations(int limit) {
        return batchRepository.unresolvedOperations(limit);
    }

    public synchronized boolean hasUnresolvedPreparedOperations() {
        return batchRepository.hasUnresolved();
    }

    public synchronized java.util.List<CustodyPreparedBatch> unresolvedPreparedBatches(int limit) {
        return batchRepository.unresolved(limit);
    }

    public synchronized void validatePreparedProof(CustodyMutation mutation) {
        CustodyOperationReceipt receipt = Objects.requireNonNull(mutation, "mutation").receipt();
        CustodyPreparedOperation prepared = preparedRepository.forRequest(receipt.requestKey());
        CustodyPreparedBatch batch = batchRepository.forOperationRequest(receipt.requestKey());
        if (prepared == null || batch == null) {
            throw new CustodyConflictException("Custody mutation has no prepared intent");
        }
        if (batch.status() == CustodyBatchStatus.APPLIED) {
            CustodyOperationReceipt existing = repository.receiptForRequest(receipt.requestKey());
            if (existing == null || !existing.equals(receipt)) {
                throw new CustodyConflictException("Applied custody batch mutation is missing");
            }
        } else if ((batch.status() != CustodyBatchStatus.APPLYING
                && batch.status() != CustodyBatchStatus.PREPARED)
                || batch.operations().size() != 1) {
            throw new CustodyConflictException("Custody mutation batch is not applying");
        }
        Instant resolvedAt = receipt.createdAt().isBefore(prepared.preparedAt())
                ? prepared.preparedAt() : receipt.createdAt();
        prepared.resolve(receipt, resolvedAt);
    }

    private void validatePreparedMutation(CustodyMutation mutation) {
        CustodyOperationReceipt receipt = mutation.receipt();
        CustodyPreparedOperation prepared = preparedRepository.forRequest(receipt.requestKey());
        CustodyPreparedBatch batch = batchRepository.forOperationRequest(receipt.requestKey());
        if (prepared == null || batch == null
                || !batch.transactionId().equals(receipt.transactionId())) {
            throw new CustodyConflictException("Custody batch mutation has no prepared proof");
        }
        Instant resolvedAt = receipt.createdAt().isBefore(prepared.preparedAt())
                ? prepared.preparedAt() : receipt.createdAt();
        prepared.resolve(receipt, resolvedAt);
    }

    public synchronized CustodyLot getLot(UUID lotId) {
        return repository.get(lotId);
    }

    public synchronized CustodyPreparedBatch getPreparedBatch(UUID batchId) {
        return batchRepository.get(batchId);
    }

    public synchronized CustodyLiabilityReport outstandingLiabilities() {
        return repository.outstandingLiabilities();
    }

    public synchronized CustodyConservationReport conservation() {
        return repository.conservation();
    }

    public synchronized CustodyLiabilitySnapshot liabilitySnapshot() {
        List<CustodyHeldLiability> liabilities = repository.snapshotLots().values().stream()
                .filter(lot -> lot.state() == CustodyLotState.HELD)
                .map(lot -> new CustodyHeldLiability(
                        lot.lotId(), lot.transactionId(), lot.assetType(), lot.units(),
                        lot.currencyProvider(), lot.protectedProvenance()))
                .sorted(Comparator.comparing(value -> value.lotId().toString()))
                .toList();
        try {
            CustodyConservationReport report = repository.conservation();
            return new CustodyLiabilitySnapshot(
                    liabilities, report.conserved(), report.violations());
        } catch (ArithmeticException exception) {
            return new CustodyLiabilitySnapshot(liabilities, false,
                    List.of("Custody local conservation arithmetic overflow"));
        }
    }

    public synchronized boolean hasMaterializedState() {
        return !repository.snapshotLots().isEmpty()
                || !repository.snapshotReceipts().isEmpty()
                || !preparedRepository.snapshot().isEmpty()
                || !batchRepository.snapshot().isEmpty();
    }

    private static void requireBound(int size, String label) {
        if (size < 0 || size > MAX_ENTRIES) {
            throw new IllegalStateException("Escrow custody " + label + " exceed entry limit");
        }
    }

    private static ListTag requireCompoundList(CompoundTag tag, String key, int version) {
        Tag raw = tag.get(key);
        if (raw == null) {
            if (version == CURRENT_VERSION) {
                throw new IllegalStateException("Escrow custody schema is missing " + key);
            }
            return new ListTag();
        }
        if (!(raw instanceof ListTag list)) {
            throw new IllegalStateException("Escrow custody " + key + " is not a list");
        }
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalStateException("Escrow custody " + key + " has the wrong element type");
        }
        return list;
    }

    private static void validatePreparedLinks(Map<UUID, CustodyPreparedOperation> prepared,
                                              Map<UUID, CustodyLot> lots,
                                              Map<UUID, CustodyOperationReceipt> receipts) {
        for (CustodyPreparedOperation intent : prepared.values()) {
            if (intent.status() == CustodyPreparedStatus.RESOLVED) {
                CustodyOperationReceipt receipt = receipts.get(intent.resolvedReceiptId().orElseThrow());
                if (receipt == null) {
                    throw new CustodyConflictException("Resolved custody intent references a missing receipt");
                }
                intent.resolve(receipt, intent.resolvedAt().orElseThrow());
            } else if (intent.operation() != CustodyOperation.RESERVE) {
                CustodyLot lot = lots.get(intent.lotSnapshot().lotId());
                if (lot == null || lot.state() != CustodyLotState.HELD
                        || !CustodyHashes.equal(lot.assetFingerprint(),
                        intent.lotSnapshot().assetFingerprint())) {
                    throw new CustodyConflictException("Unresolved custody intent references invalid held assets");
                }
            }
        }
    }

    private static void migrateLegacyBatches(
            Map<UUID, CustodyPreparedOperation> prepared,
            Map<UUID, CustodyPreparedBatch> batches
    ) {
        for (CustodyPreparedOperation operation : prepared.values()) {
            CustodyPreparedOperation initial = initialPreparation(operation);
            CustodyBatchStatus status = operation.status() == CustodyPreparedStatus.RESOLVED
                    ? CustodyBatchStatus.APPLIED : CustodyBatchStatus.PREPARED;
            long revision = status == CustodyBatchStatus.APPLIED ? 2L : 0L;
            Instant updated = operation.resolvedAt().orElse(operation.preparedAt());
            CustodyPreparedBatch batch = new CustodyPreparedBatch(
                    CustodyPreparedBatch.deterministicId(
                            operation.lotSnapshot().transactionId(), operation.requestKey()),
                    operation.lotSnapshot().transactionId(), operation.requestKey(),
                    java.util.List.of(initial), status, operation.preparedAt(), updated,
                    revision, "Migrated");
            if (batches.put(batch.batchId(), batch) != null) {
                throw new CustodyConflictException("Legacy custody batches collide");
            }
        }
    }

    private static CustodyPreparedOperation initialPreparation(CustodyPreparedOperation operation) {
        return new CustodyPreparedOperation(operation.intentId(), operation.operation(),
                operation.requestKey(), operation.lotSnapshot(), operation.adapterId(),
                operation.adapterCapability(), operation.simulationToken(),
                operation.plannedEvidence(), operation.preparedAt(),
                CustodyPreparedStatus.PREPARED, java.util.Optional.empty(),
                java.util.Optional.empty());
    }

    private static CustodyPreparedBatch singleBatch(CustodyPreparedOperation operation) {
        CustodyPreparedOperation initial = initialPreparation(
                Objects.requireNonNull(operation, "operation"));
        return new CustodyPreparedBatch(CustodyPreparedBatch.deterministicId(
                initial.lotSnapshot().transactionId(), initial.requestKey()),
                initial.lotSnapshot().transactionId(), initial.requestKey(),
                java.util.List.of(initial), CustodyBatchStatus.PREPARED,
                initial.preparedAt(), initial.preparedAt(), 0L, "Prepared");
    }

    private static void validateBatchLinks(
            Map<UUID, CustodyPreparedBatch> batches,
            Map<UUID, CustodyPreparedOperation> prepared
    ) {
        Map<String, UUID> batchByOperation = new HashMap<>();
        for (CustodyPreparedBatch batch : batches.values()) {
            for (CustodyPreparedOperation initial : batch.operations()) {
                CustodyPreparedOperation operation = prepared.get(initial.intentId());
                if (operation == null || !samePreparation(initial, operation)
                        || batchByOperation.put(initial.requestKey(), batch.batchId()) != null) {
                    throw new CustodyConflictException("Prepared custody batch membership is invalid");
                }
                boolean resolved = operation.status() == CustodyPreparedStatus.RESOLVED;
                if ((batch.status() == CustodyBatchStatus.APPLIED) != resolved) {
                    throw new CustodyConflictException("Prepared custody batch outcome is inconsistent");
                }
            }
        }
        if (batchByOperation.size() != prepared.size()) {
            throw new CustodyConflictException("Prepared custody operation has no batch");
        }
    }

    private static boolean samePreparation(CustodyPreparedOperation first,
                                           CustodyPreparedOperation second) {
        return first.intentId().equals(second.intentId())
                && first.operation() == second.operation()
                && first.requestKey().equals(second.requestKey())
                && first.lotSnapshot().equals(second.lotSnapshot())
                && first.adapterId().equals(second.adapterId())
                && first.adapterCapability() == second.adapterCapability()
                && first.simulationToken().equals(second.simulationToken())
                && first.plannedEvidence().equals(second.plannedEvidence())
                && first.preparedAt().equals(second.preparedAt());
    }

    private synchronized CustodyStateSnapshot snapshotForRestore() {
        return new CustodyStateSnapshot(repository.snapshotLots(), repository.snapshotReceipts(),
                preparedRepository.snapshot(), batchRepository.snapshot());
    }

    private record CustodyStateSnapshot(
            Map<UUID, CustodyLot> lots,
            Map<UUID, CustodyOperationReceipt> receipts,
            Map<UUID, CustodyPreparedOperation> prepared,
            Map<UUID, CustodyPreparedBatch> batches
    ) {
    }
}
