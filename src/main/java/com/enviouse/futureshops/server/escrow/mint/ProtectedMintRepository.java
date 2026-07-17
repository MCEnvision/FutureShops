package com.enviouse.futureshops.server.escrow.mint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ProtectedMintRepository {
    public static final int MAX_BATCHES = 1_000_000;
    public static final int MAX_RECEIPTS = 1_000_000;
    public static final long MAX_RECEIPT_REFERENCES = 2_000_000L;

    private final Map<UUID, ProtectedMintBatch> batches = new HashMap<>();
    private final Map<UUID, ProtectedMintReceipt> receipts = new HashMap<>();
    private final Map<String, UUID> requestIndex = new HashMap<>();
    private long receiptReferenceCount;

    public synchronized ProtectedMintApplyResult preflightCommitted(
            ProtectedMintJournalEvent event) {
        return evaluate(Objects.requireNonNull(event, "event"));
    }

    public synchronized ProtectedMintApplyResult applyCommitted(ProtectedMintJournalEvent event) {
        ProtectedMintApplyResult result = evaluate(Objects.requireNonNull(event, "event"));
        if (result.replayed()) {
            return result;
        }
        if (event.operation() == ProtectedMintOperation.AUTHORIZE
                || event.operation() == ProtectedMintOperation.ISSUE) {
            ProtectedMintBatch created = result.affectedBatches().get(0);
            batches.put(created.batchId(), created);
        } else {
            ProtectedMintBatch changed = result.affectedBatches().get(0);
            batches.put(changed.batchId(), changed);
        }
        for (ProtectedMintBatch replacement : result.replacementBatches()) {
            batches.put(replacement.batchId(), replacement);
        }
        putReceipt(result.receipt());
        return result;
    }

    public synchronized ProtectedMintApplyResult authorizeCommitted(ProtectedMintBatch batch) {
        return applyCommitted(ProtectedMintJournalEvent.authorize(batch));
    }

    synchronized ProtectedMintApplyResult issueCommitted(ProtectedMintBatch batch) {
        return applyCommitted(ProtectedMintJournalEvent.issue(batch));
    }

    public synchronized void preflightIssueBatch(List<ProtectedMintJournalEvent> events) {
        Objects.requireNonNull(events, "events");
        Set<UUID> newBatchIds = new HashSet<>();
        Set<String> newRequestKeys = new HashSet<>();
        int additional = 0;
        for (ProtectedMintJournalEvent event : events) {
            if (event.operation() != ProtectedMintOperation.ISSUE) {
                throw new ProtectedMintConflictException(
                        "Protected mint issue batch contains another operation");
            }
            ProtectedMintApplyResult result = preflightCommitted(event);
            if (result.replayed()) {
                continue;
            }
            ProtectedMintBatch batch = event.batch().orElseThrow();
            if (!newBatchIds.add(batch.batchId())
                    || !newRequestKeys.add(event.requestKey())) {
                throw new ProtectedMintConflictException(
                        "Protected mint issue batch contains duplicate identity");
            }
            additional = Math.addExact(additional, 1);
        }
        if (Math.addExact(batches.size(), additional) > MAX_BATCHES
                || Math.addExact(receipts.size(), additional) > MAX_RECEIPTS
                || Math.addExact(receiptReferenceCount, (long) additional)
                > MAX_RECEIPT_REFERENCES) {
            throw new ProtectedMintConflictException(
                    "Protected mint issue batch exceeds repository limits");
        }
    }

    public synchronized ProtectedMintApplyResult materializeCommitted(UUID transactionId,
                                                                      UUID batchId,
                                                                      String requestKey,
                                                                      int quantity,
                                                                      java.time.Instant now) {
        return applyCommitted(ProtectedMintJournalEvent.materialize(transactionId,
                batchId, requestKey, quantity, now));
    }

    public synchronized ProtectedMintApplyResult reserveCommitted(UUID transactionId,
                                                                  UUID batchId,
                                                                  String requestKey,
                                                                  int quantity,
                                                                  java.time.Instant now) {
        return applyCommitted(ProtectedMintJournalEvent.reserve(transactionId,
                batchId, requestKey, quantity, now));
    }

    public synchronized ProtectedMintApplyResult commitCommitted(UUID transactionId,
                                                                 UUID batchId,
                                                                 String requestKey,
                                                                 int quantity,
                                                                 java.time.Instant now) {
        return applyCommitted(ProtectedMintJournalEvent.commit(transactionId,
                batchId, requestKey, quantity, now));
    }

    public synchronized ProtectedMintApplyResult refundCommitted(UUID transactionId,
                                                                 UUID sourceBatchId,
                                                                 String requestKey,
                                                                 ProtectedMintState sourceState,
                                                                 int quantity,
                                                                 ProtectedMintBatch replacementBatch,
                                                                 java.time.Instant now) {
        return applyCommitted(ProtectedMintJournalEvent.refund(transactionId,
                sourceBatchId, requestKey, sourceState, quantity, replacementBatch, now));
    }

    public synchronized ProtectedMintApplyResult quarantineCommitted(UUID transactionId,
                                                                     UUID batchId,
                                                                     String requestKey,
                                                                     ProtectedMintState sourceState,
                                                                     int quantity,
                                                                     java.time.Instant now) {
        return applyCommitted(ProtectedMintJournalEvent.quarantine(transactionId,
                batchId, requestKey, sourceState, quantity, now));
    }

    public synchronized ProtectedMintBatch getBatch(UUID batchId) {
        return batches.get(Objects.requireNonNull(batchId, "batchId"));
    }

    public synchronized ProtectedMintReceipt receiptForRequest(String requestKey) {
        UUID receiptId = requestIndex.get(ProtectedMintText.requestKey(requestKey));
        return receiptId == null ? null : receipts.get(receiptId);
    }

    public synchronized ProtectedMintValidationResult validate(
            UUID batchId, long denominationMinorUnits, int authorizedCount,
            String serverIdentityEvidence, String checksumEvidence,
            int requestedQuantity, Optional<UUID> expectedReservationTransactionId) {
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(expectedReservationTransactionId,
                "expectedReservationTransactionId");
        ProtectedMintBatch batch = batches.get(batchId);
        if (batch == null) {
            return new ProtectedMintValidationResult(ProtectedMintValidationCode.UNKNOWN_MINT,
                    Optional.empty(), 0);
        }
        if (batch.denominationMinorUnits() != denominationMinorUnits
                || batch.authorizedCount() != authorizedCount) {
            return invalid(ProtectedMintValidationCode.DENOMINATION_MISMATCH, batch);
        }
        if (!evidenceEqual(batch.serverIdentityEvidence(), serverIdentityEvidence)) {
            return invalid(ProtectedMintValidationCode.SERVER_IDENTITY_MISMATCH, batch);
        }
        if (!evidenceEqual(batch.checksumEvidence(), checksumEvidence)) {
            return invalid(ProtectedMintValidationCode.CHECKSUM_MISMATCH, batch);
        }
        if (requestedQuantity <= 0 || requestedQuantity > authorizedCount) {
            return invalid(ProtectedMintValidationCode.NOT_AVAILABLE, batch);
        }
        int available = expectedReservationTransactionId.isPresent()
                ? batch.reservedFor(expectedReservationTransactionId.orElseThrow())
                : batch.availableQuantity();
        if (available >= requestedQuantity) {
            return new ProtectedMintValidationResult(ProtectedMintValidationCode.VALID,
                    Optional.of(batch), requestedQuantity);
        }
        if (batch.spentQuantity() == batch.authorizedCount()) {
            return invalid(ProtectedMintValidationCode.ALREADY_SPENT, batch);
        }
        if (batch.refundedQuantity() == batch.authorizedCount()) {
            return invalid(ProtectedMintValidationCode.REFUNDED, batch);
        }
        if (batch.quarantinedQuantity() == batch.authorizedCount()) {
            return invalid(ProtectedMintValidationCode.QUARANTINED, batch);
        }
        return invalid(ProtectedMintValidationCode.NOT_AVAILABLE, batch);
    }

    public synchronized ProtectedMintLiabilityReport outstandingLiability() {
        long outstandingUnits = 0L;
        long outstandingValue = 0L;
        Map<Long, Long> byDenomination = new HashMap<>();
        Map<ProtectedMintState, Long> byState = new EnumMap<>(ProtectedMintState.class);
        for (ProtectedMintBatch batch : batches.values()) {
            for (ProtectedMintState state : ProtectedMintState.values()) {
                int quantity = batch.quantity(state);
                if (quantity > 0) {
                    byState.merge(state, (long) quantity, Math::addExact);
                }
            }
            long liability = Math.addExact(batch.authorizedQuantity(),
                    Math.addExact(batch.availableQuantity(), batch.reservedQuantity()));
            if (liability == 0L) {
                continue;
            }
            outstandingUnits = Math.addExact(outstandingUnits, liability);
            outstandingValue = Math.addExact(outstandingValue,
                    Math.multiplyExact(batch.denominationMinorUnits(), liability));
            byDenomination.merge(batch.denominationMinorUnits(), liability, Math::addExact);
        }
        return new ProtectedMintLiabilityReport(outstandingUnits, outstandingValue,
                byDenomination, byState);
    }

    public synchronized ProtectedMintConservationReport conservation() {
        Map<ProtectedMintState, Long> quantitiesByState =
                new EnumMap<>(ProtectedMintState.class);
        Map<ProtectedMintState, Long> valueByState =
                new EnumMap<>(ProtectedMintState.class);
        List<String> violations = new ArrayList<>();
        Map<UUID, Integer> replacementsByOrigin = new HashMap<>();
        long issuedUnits = 0L;
        long issuedValue = 0L;

        for (Map.Entry<UUID, ProtectedMintBatch> entry : batches.entrySet()) {
            ProtectedMintBatch batch = entry.getValue();
            if (!entry.getKey().equals(batch.batchId())) {
                violations.add("Protected mint batch index is invalid");
            }
            issuedUnits = Math.addExact(issuedUnits, batch.authorizedCount());
            issuedValue = Math.addExact(issuedValue, Math.multiplyExact(
                    batch.denominationMinorUnits(), (long) batch.authorizedCount()));
            for (ProtectedMintState state : ProtectedMintState.values()) {
                int quantity = batch.quantity(state);
                if (quantity > 0) {
                    quantitiesByState.merge(state, (long) quantity, Math::addExact);
                    valueByState.merge(state, Math.multiplyExact(
                            batch.denominationMinorUnits(), (long) quantity), Math::addExact);
                }
            }
            if (batch.replacementForBatchId().isPresent()) {
                UUID originId = batch.replacementForBatchId().orElseThrow();
                ProtectedMintBatch origin = batches.get(originId);
                if (origin == null
                        || origin.denominationMinorUnits() != batch.denominationMinorUnits()) {
                    violations.add("Protected mint replacement lineage is invalid");
                } else {
                    replacementsByOrigin.merge(originId, batch.authorizedCount(), Math::addExact);
                }
            }
        }

        Map<UUID, BatchReceiptLineage> lineages = validateReceiptIndexes(violations);
        for (ProtectedMintBatch batch : batches.values()) {
            if (batch.refundedQuantity()
                    != replacementsByOrigin.getOrDefault(batch.batchId(), 0)) {
                violations.add("Protected mint refunded quantity has no exact replacement");
            }
            validateBatchLineage(batch, lineages.get(batch.batchId()),
                    receiptForRequest(batch.authorizeRequestKey()), violations);
        }
        return new ProtectedMintConservationReport(quantitiesByState, valueByState,
                issuedUnits, issuedValue, violations.isEmpty(), violations);
    }

    public synchronized boolean hasMaterializedState() {
        return !batches.isEmpty() || !receipts.isEmpty();
    }

    synchronized Map<UUID, ProtectedMintBatch> snapshotBatches() {
        return Map.copyOf(batches);
    }

    synchronized Map<UUID, ProtectedMintReceipt> snapshotReceipts() {
        return Map.copyOf(receipts);
    }

    synchronized void restore(Map<UUID, ProtectedMintBatch> restoredBatches,
                              Map<UUID, ProtectedMintReceipt> restoredReceipts) {
        Objects.requireNonNull(restoredBatches, "restoredBatches");
        Objects.requireNonNull(restoredReceipts, "restoredReceipts");
        if (restoredBatches.size() > MAX_BATCHES
                || restoredReceipts.size() > MAX_RECEIPTS) {
            throw new ProtectedMintConflictException("Protected mint restored data exceeds limits");
        }
        batches.clear();
        receipts.clear();
        requestIndex.clear();
        receiptReferenceCount = 0L;
        for (Map.Entry<UUID, ProtectedMintBatch> entry : restoredBatches.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().batchId())) {
                clear();
                throw new ProtectedMintConflictException("Protected mint batch index is invalid");
            }
        }
        batches.putAll(restoredBatches);
        try {
            for (ProtectedMintReceipt receipt : restoredReceipts.values()) {
                putReceipt(receipt);
            }
            if (!conservation().conserved()) {
                throw new ProtectedMintConflictException(
                        "Protected mint restored data does not conserve");
            }
        } catch (RuntimeException exception) {
            clear();
            throw exception;
        }
    }

    private ProtectedMintApplyResult evaluate(ProtectedMintJournalEvent event) {
        byte[] mutationHash = ProtectedMintIds.hash(ProtectedMintEventCodec.encode(event));
        ProtectedMintApplyResult replay = replay(event, mutationHash);
        if (replay != null) {
            return replay;
        }
        requireReceiptCapacity();
        ProtectedMintReceipt receipt = receipt(event, mutationHash);
        requireReceiptIdentityAvailable(receipt);
        requireReceiptReferenceCapacity(receipt);
        return switch (event.operation()) {
            case ISSUE -> evaluateIssue(event, receipt);
            case AUTHORIZE -> evaluateAuthorize(event, receipt);
            case MATERIALIZE -> evaluateMaterialize(event, receipt);
            case RESERVE -> evaluateReserve(event, receipt);
            case COMMIT -> evaluateCommit(event, receipt);
            case REFUND -> evaluateRefund(event, receipt);
            case QUARANTINE -> evaluateQuarantine(event, receipt);
        };
    }

    private ProtectedMintApplyResult evaluateAuthorize(ProtectedMintJournalEvent event,
                                                       ProtectedMintReceipt receipt) {
        ProtectedMintBatch batch = event.batch().orElseThrow();
        requireBatchCapacity(1);
        if (batches.containsKey(batch.batchId())) {
            throw new ProtectedMintConflictException("Protected mint batch ID already exists");
        }
        return new ProtectedMintApplyResult(receipt, List.of(batch), List.of(), false);
    }

    private ProtectedMintApplyResult evaluateIssue(ProtectedMintJournalEvent event,
                                                   ProtectedMintReceipt receipt) {
        ProtectedMintBatch batch = event.batch().orElseThrow();
        requireBatchCapacity(1);
        if (batches.containsKey(batch.batchId())) {
            throw new ProtectedMintConflictException("Protected mint batch ID already exists");
        }
        return new ProtectedMintApplyResult(receipt, List.of(batch), List.of(), false);
    }

    private ProtectedMintApplyResult evaluateMaterialize(ProtectedMintJournalEvent event,
                                                         ProtectedMintReceipt receipt) {
        ProtectedMintBatch batch = requireBatch(event.targetBatchId().orElseThrow());
        if (!batch.transactionId().equals(event.transactionId())) {
            throw new ProtectedMintConflictException(
                    "Protected mint materialization transaction does not match");
        }
        ProtectedMintBatch changed;
        try {
            changed = batch.materialize(event.quantity(), event.occurredAt());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw conflict("Protected mint batch cannot be materialized", exception);
        }
        return new ProtectedMintApplyResult(receipt, List.of(changed), List.of(), false);
    }

    private ProtectedMintApplyResult evaluateReserve(ProtectedMintJournalEvent event,
                                                     ProtectedMintReceipt receipt) {
        ProtectedMintBatch batch = requireBatch(event.targetBatchId().orElseThrow());
        ProtectedMintBatch changed;
        try {
            changed = batch.reserve(event.transactionId(), event.quantity(), event.occurredAt());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw conflict("Protected mint quantity cannot be reserved", exception);
        }
        return new ProtectedMintApplyResult(receipt, List.of(changed), List.of(), false);
    }

    private ProtectedMintApplyResult evaluateCommit(ProtectedMintJournalEvent event,
                                                    ProtectedMintReceipt receipt) {
        ProtectedMintBatch batch = requireBatch(event.targetBatchId().orElseThrow());
        ProtectedMintBatch changed;
        try {
            changed = batch.commit(event.transactionId(), event.quantity(), event.occurredAt());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw conflict("Protected mint reservation cannot be committed", exception);
        }
        return new ProtectedMintApplyResult(receipt, List.of(changed), List.of(), false);
    }

    private ProtectedMintApplyResult evaluateRefund(ProtectedMintJournalEvent event,
                                                    ProtectedMintReceipt receipt) {
        ProtectedMintBatch source = requireBatch(event.targetBatchId().orElseThrow());
        ProtectedMintBatch replacement = event.batch().orElseThrow();
        requireBatchCapacity(1);
        if (batches.containsKey(replacement.batchId())) {
            throw new ProtectedMintConflictException("Protected mint replacement batch exists");
        }
        if (source.denominationMinorUnits() != replacement.denominationMinorUnits()
                || !source.serverIdentityEvidence().equals(
                replacement.serverIdentityEvidence())) {
            throw new ProtectedMintConflictException(
                    "Protected mint refund replacement evidence does not match");
        }
        ProtectedMintBatch changed;
        try {
            changed = source.refund(event.transactionId(), event.sourceState().orElseThrow(),
                    event.quantity(), event.occurredAt());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw conflict("Protected mint quantity cannot be refunded", exception);
        }
        return new ProtectedMintApplyResult(receipt, List.of(changed),
                List.of(replacement), false);
    }

    private ProtectedMintApplyResult evaluateQuarantine(ProtectedMintJournalEvent event,
                                                        ProtectedMintReceipt receipt) {
        ProtectedMintBatch batch = requireBatch(event.targetBatchId().orElseThrow());
        Optional<UUID> reservation = event.sourceState().orElseThrow()
                == ProtectedMintState.RESERVED
                ? Optional.of(event.transactionId()) : Optional.empty();
        ProtectedMintBatch changed;
        try {
            changed = batch.quarantine(reservation, event.sourceState().orElseThrow(),
                    event.quantity(), event.occurredAt());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw conflict("Protected mint quantity cannot be quarantined", exception);
        }
        return new ProtectedMintApplyResult(receipt, List.of(changed), List.of(), false);
    }

    private ProtectedMintApplyResult replay(ProtectedMintJournalEvent event,
                                            byte[] mutationHash) {
        UUID receiptId = requestIndex.get(event.requestKey());
        if (receiptId == null) {
            return null;
        }
        ProtectedMintReceipt receipt = receipts.get(receiptId);
        if (receipt == null || !Arrays.equals(receipt.mutationHash(), mutationHash)) {
            throw new ProtectedMintConflictException("Protected mint request key was reused");
        }
        ProtectedMintBatch affected = receipt.sourceBatchId().isPresent()
                ? batches.get(receipt.sourceBatchId().orElseThrow())
                : batches.get(receipt.resultingBatchId().orElseThrow());
        if (affected == null) {
            throw new ProtectedMintConflictException("Protected mint replay state is incomplete");
        }
        List<ProtectedMintBatch> replacements = event.operation() == ProtectedMintOperation.REFUND
                ? List.of(requireBatch(receipt.resultingBatchId().orElseThrow())) : List.of();
        return new ProtectedMintApplyResult(receipt, List.of(affected), replacements, true);
    }

    private ProtectedMintReceipt receipt(ProtectedMintJournalEvent event, byte[] mutationHash) {
        Optional<UUID> source = event.targetBatchId();
        Optional<UUID> result = event.targetBatchId();
        if (event.operation() == ProtectedMintOperation.AUTHORIZE
                || event.operation() == ProtectedMintOperation.ISSUE) {
            source = Optional.empty();
            result = Optional.of(event.batch().orElseThrow().batchId());
        } else if (event.operation() == ProtectedMintOperation.REFUND) {
            result = Optional.of(event.batch().orElseThrow().batchId());
        }
        return new ProtectedMintReceipt(ProtectedMintIds.receiptId(event.requestKey()),
                event.requestKey(), event.operation(), event.transactionId(), source, result,
                event.quantity(), event.sourceState(), mutationHash, event.occurredAt());
    }

    private Map<UUID, BatchReceiptLineage> validateReceiptIndexes(List<String> violations) {
        Map<UUID, BatchReceiptLineage> lineages = new HashMap<>();
        if (requestIndex.size() != receipts.size()) {
            violations.add("Protected mint request and receipt counts disagree");
        }
        long countedReferences = 0L;
        for (ProtectedMintReceipt receipt : receipts.values()) {
            countedReferences = Math.addExact(countedReferences, receiptReferences(receipt));
            if (!receipt.receiptId().equals(requestIndex.get(receipt.requestKey()))) {
                violations.add("Protected mint request index is invalid");
            }
            if (receipt.sourceBatchId().isPresent()) {
                UUID sourceId = receipt.sourceBatchId().orElseThrow();
                ProtectedMintBatch source = batches.get(sourceId);
                if (source == null) {
                    violations.add("Protected mint receipt source is missing");
                } else {
                    lineages.computeIfAbsent(sourceId, ignored -> new BatchReceiptLineage())
                            .accept(receipt, source, violations);
                }
            }
            if (receipt.resultingBatchId().isEmpty()
                    || !batches.containsKey(receipt.resultingBatchId().orElseThrow())) {
                violations.add("Protected mint receipt result is missing");
            }
        }
        if (countedReferences != receiptReferenceCount
                || countedReferences > MAX_RECEIPT_REFERENCES) {
            violations.add("Protected mint receipt reference count is invalid");
        }
        for (ProtectedMintBatch batch : batches.values()) {
            ProtectedMintReceipt creation = receiptForRequest(batch.authorizeRequestKey());
            boolean replacement = batch.replacementForBatchId().isPresent();
            if (creation == null || creation.resultingBatchId().isEmpty()
                    || !creation.resultingBatchId().orElseThrow().equals(batch.batchId())
                    || creation.transactionId() != null
                    && !creation.transactionId().equals(batch.transactionId())
                    || !creation.occurredAt().equals(batch.authorizedAt())
                    || creation.quantity() != batch.authorizedCount()
                    || replacement && creation.operation() != ProtectedMintOperation.REFUND
                    || !replacement && creation.operation() != ProtectedMintOperation.AUTHORIZE
                    && creation.operation() != ProtectedMintOperation.ISSUE
                    || replacement && !creation.sourceBatchId().equals(
                    batch.replacementForBatchId())) {
                violations.add("Protected mint batch has no valid creation receipt");
            }
        }
        return lineages;
    }

    private void validateBatchLineage(ProtectedMintBatch batch,
                                      BatchReceiptLineage lineage,
                                      ProtectedMintReceipt creation,
                                      List<String> violations) {
        BatchReceiptLineage flows = lineage == null ? new BatchReceiptLineage() : lineage;
        boolean issued = creation != null
                && creation.operation() == ProtectedMintOperation.ISSUE;
        int initiallyAvailable = issued ? batch.authorizedCount() : 0;
        int expectedAuthorized = batch.authorizedCount() - initiallyAvailable
                - flows.materialized - flows.quarantinedAuthorized;
        int expectedAvailable = initiallyAvailable + flows.materialized - flows.reserved
                - flows.quarantinedAvailable;
        Map<UUID, Integer> expectedReserved = difference(flows.reserveByTransaction,
                flows.commitByTransaction, flows.refundReservedByTransaction,
                flows.quarantineReservedByTransaction);
        Map<UUID, Integer> expectedSpent = difference(flows.commitByTransaction,
                flows.refundSpentByTransaction);
        int expectedRefunded = flows.refundedReserved + flows.refundedSpent;
        int expectedQuarantined = flows.quarantinedAuthorized
                + flows.quarantinedAvailable + flows.quarantinedReserved;
        if (expectedAuthorized != batch.authorizedQuantity()
                || expectedAvailable != batch.availableQuantity()
                || !expectedReserved.equals(batch.reservedQuantities())
                || !expectedSpent.equals(batch.spentQuantities())
                || expectedRefunded != batch.refundedQuantity()
                || expectedQuarantined != batch.quarantinedQuantity()
                || Math.addExact(flows.transitions, issued ? 1 : 0) != batch.revision()) {
            violations.add("Protected mint batch quantities do not match receipt lineage");
        }
    }

    @SafeVarargs
    private static Map<UUID, Integer> difference(Map<UUID, Integer> initial,
                                                 Map<UUID, Integer>... deductions) {
        Map<UUID, Integer> result = new HashMap<>(initial);
        for (Map<UUID, Integer> deduction : deductions) {
            for (Map.Entry<UUID, Integer> entry : deduction.entrySet()) {
                result.merge(entry.getKey(), -entry.getValue(), Math::addExact);
            }
        }
        result.entrySet().removeIf(entry -> entry.getValue() == 0);
        return Map.copyOf(result);
    }

    private void putReceipt(ProtectedMintReceipt receipt) {
        ProtectedMintReceipt oldReceipt = receipts.putIfAbsent(receipt.receiptId(), receipt);
        if (oldReceipt != null) {
            if (!oldReceipt.equals(receipt)) {
                throw new ProtectedMintConflictException("Protected mint receipt ID is duplicated");
            }
            return;
        }
        long nextReferences = Math.addExact(receiptReferenceCount, receiptReferences(receipt));
        if (nextReferences > MAX_RECEIPT_REFERENCES) {
            receipts.remove(receipt.receiptId());
            throw new ProtectedMintConflictException(
                    "Protected mint receipt reference limit is exceeded");
        }
        UUID oldRequest = requestIndex.putIfAbsent(receipt.requestKey(), receipt.receiptId());
        if (oldRequest != null && !oldRequest.equals(receipt.receiptId())) {
            receipts.remove(receipt.receiptId());
            throw new ProtectedMintConflictException("Protected mint request key is duplicated");
        }
        receiptReferenceCount = nextReferences;
    }

    private void requireReceiptIdentityAvailable(ProtectedMintReceipt receipt) {
        if (receipts.containsKey(receipt.receiptId())
                || requestIndex.containsKey(receipt.requestKey())) {
            throw new ProtectedMintConflictException("Protected mint receipt identity conflicts");
        }
    }

    private void requireReceiptCapacity() {
        if (receipts.size() >= MAX_RECEIPTS) {
            throw new ProtectedMintConflictException("Protected mint receipt limit is exceeded");
        }
    }

    private void requireReceiptReferenceCapacity(ProtectedMintReceipt receipt) {
        if (Math.addExact(receiptReferenceCount, receiptReferences(receipt))
                > MAX_RECEIPT_REFERENCES) {
            throw new ProtectedMintConflictException(
                    "Protected mint receipt reference limit is exceeded");
        }
    }

    private void requireBatchCapacity(int additional) {
        if (Math.addExact(batches.size(), additional) > MAX_BATCHES) {
            throw new ProtectedMintConflictException("Protected mint batch limit is exceeded");
        }
    }

    private ProtectedMintBatch requireBatch(UUID batchId) {
        ProtectedMintBatch batch = batches.get(batchId);
        if (batch == null) {
            throw new ProtectedMintConflictException("Protected mint batch does not exist");
        }
        return batch;
    }

    private void clear() {
        batches.clear();
        receipts.clear();
        requestIndex.clear();
        receiptReferenceCount = 0L;
    }

    private static long receiptReferences(ProtectedMintReceipt receipt) {
        return (receipt.sourceBatchId().isPresent() ? 1L : 0L)
                + (receipt.resultingBatchId().isPresent() ? 1L : 0L);
    }

    private static ProtectedMintValidationResult invalid(ProtectedMintValidationCode code,
                                                         ProtectedMintBatch batch) {
        return new ProtectedMintValidationResult(code, Optional.of(batch), 0);
    }

    private static ProtectedMintConflictException conflict(String message,
                                                           RuntimeException cause) {
        ProtectedMintConflictException conflict = new ProtectedMintConflictException(message);
        conflict.initCause(cause);
        return conflict;
    }

    private static boolean evidenceEqual(String expected, String supplied) {
        if (supplied == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    private static final class BatchReceiptLineage {
        private int transitions;
        private int materialized;
        private int reserved;
        private int refundedReserved;
        private int refundedSpent;
        private int quarantinedAuthorized;
        private int quarantinedAvailable;
        private int quarantinedReserved;
        private final Map<UUID, Integer> reserveByTransaction = new HashMap<>();
        private final Map<UUID, Integer> commitByTransaction = new HashMap<>();
        private final Map<UUID, Integer> refundReservedByTransaction = new HashMap<>();
        private final Map<UUID, Integer> refundSpentByTransaction = new HashMap<>();
        private final Map<UUID, Integer> quarantineReservedByTransaction = new HashMap<>();

        private void accept(ProtectedMintReceipt receipt, ProtectedMintBatch batch,
                            List<String> violations) {
            transitions = Math.addExact(transitions, 1);
            if (receipt.occurredAt().isBefore(batch.authorizedAt())
                    || receipt.occurredAt().isAfter(batch.updatedAt())) {
                violations.add("Protected mint receipt time is outside batch lineage");
            }
            int quantity = receipt.quantity();
            switch (receipt.operation()) {
                case AUTHORIZE, ISSUE ->
                        violations.add("Protected mint creation has a source batch");
                case MATERIALIZE -> {
                    materialized = Math.addExact(materialized, quantity);
                    if (!receipt.transactionId().equals(batch.transactionId())) {
                        violations.add("Protected mint materialization transaction is invalid");
                    }
                }
                case RESERVE -> {
                    reserved = Math.addExact(reserved, quantity);
                    merge(reserveByTransaction, receipt.transactionId(), quantity);
                }
                case COMMIT -> merge(commitByTransaction, receipt.transactionId(), quantity);
                case REFUND -> {
                    if (receipt.sourceState().orElseThrow() == ProtectedMintState.RESERVED) {
                        refundedReserved = Math.addExact(refundedReserved, quantity);
                        merge(refundReservedByTransaction, receipt.transactionId(), quantity);
                    } else {
                        refundedSpent = Math.addExact(refundedSpent, quantity);
                        merge(refundSpentByTransaction, receipt.transactionId(), quantity);
                    }
                }
                case QUARANTINE -> {
                    switch (receipt.sourceState().orElseThrow()) {
                        case AUTHORIZED -> quarantinedAuthorized =
                                Math.addExact(quarantinedAuthorized, quantity);
                        case AVAILABLE -> quarantinedAvailable =
                                Math.addExact(quarantinedAvailable, quantity);
                        case RESERVED -> {
                            quarantinedReserved = Math.addExact(quarantinedReserved, quantity);
                            merge(quarantineReservedByTransaction,
                                    receipt.transactionId(), quantity);
                        }
                        default -> violations.add("Protected mint quarantine source is invalid");
                    }
                }
            }
        }

        private static void merge(Map<UUID, Integer> values, UUID transactionId,
                                  int quantity) {
            values.merge(transactionId, quantity, Math::addExact);
        }
    }
}
