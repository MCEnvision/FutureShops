package com.enviouse.futureshops.server.escrow.custody;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class CustodyRepository {
    public static final int DEFAULT_MAXIMUM_LOTS = 1_000_000;
    public static final int DEFAULT_MAXIMUM_RECEIPTS = 1_000_000;
    public static final long DEFAULT_MAXIMUM_NBT_BYTES = 268_435_456L;

    private final int maximumLots;
    private final int maximumReceipts;
    private final long maximumNbtBytes;
    private final Map<UUID, CustodyLot> lots = new HashMap<>();
    private final Map<UUID, CustodyOperationReceipt> receipts = new HashMap<>();
    private final Map<String, UUID> requestIndex = new HashMap<>();
    private long storedNbtBytes;
    private long lastBatchProbeCount;

    public CustodyRepository() {
        this(DEFAULT_MAXIMUM_LOTS, DEFAULT_MAXIMUM_RECEIPTS, DEFAULT_MAXIMUM_NBT_BYTES);
    }

    CustodyRepository(int maximumLots, int maximumReceipts, long maximumNbtBytes) {
        if (maximumLots <= 0 || maximumReceipts <= 0 || maximumNbtBytes <= 0L) {
            throw new IllegalArgumentException("Custody repository limits must be positive");
        }
        this.maximumLots = maximumLots;
        this.maximumReceipts = maximumReceipts;
        this.maximumNbtBytes = maximumNbtBytes;
    }

    public synchronized CustodyOperationResult reserve(CustodyLot lot) {
        return reserve(lot, true);
    }

    public synchronized CustodyOperationResult preflightReserve(CustodyLot lot) {
        return reserve(lot, false);
    }

    private CustodyOperationResult reserve(CustodyLot lot, boolean commit) {
        Objects.requireNonNull(lot, "lot");
        if (lot.state() != CustodyLotState.HELD || lot.revision() != 0L) {
            throw new CustodyConflictException("Only a new held custody lot can be reserved");
        }
        CustodyOperationReceipt candidate = CustodyOperationReceipt.reserve(lot);
        CustodyOperationResult replay = replay(candidate.requestKey(), CustodyOperation.RESERVE,
                lot.lotId(), lot.assetFingerprint());
        if (replay != null) {
            if (!replay.receipt().equals(candidate)) {
                throw new CustodyConflictException("Custody reserve replay does not match the existing lot");
            }
            return replay;
        }
        CustodyLot existing = lots.get(lot.lotId());
        if (existing != null) {
            throw new CustodyConflictException("Custody lot ID already exists");
        }
        if (lots.size() >= maximumLots || receipts.size() >= maximumReceipts) {
            throw new CustodyConflictException("Custody repository entry limit is exceeded");
        }
        long addedNbtBytes = nbtBytes(lot);
        if (Math.addExact(storedNbtBytes, addedNbtBytes) > maximumNbtBytes) {
            throw new CustodyConflictException("Custody repository NBT limit is exceeded");
        }
        requireReceiptAvailable(candidate);
        if (commit) {
            putReceipt(candidate);
            lots.put(lot.lotId(), lot);
            storedNbtBytes = Math.addExact(storedNbtBytes, addedNbtBytes);
        }
        return new CustodyOperationResult(lot, candidate, false);
    }

    public synchronized CustodyOperationResult release(UUID lotId,
                                                       String requestKey,
                                                       CustodyTransferEvidence evidence,
                                                       Instant now) {
        return terminal(lotId, requestKey, CustodyOperation.RELEASE, evidence, now, true);
    }

    public synchronized CustodyOperationResult preflightRelease(UUID lotId,
                                                                String requestKey,
                                                                CustodyTransferEvidence evidence,
                                                                Instant now) {
        return terminal(lotId, requestKey, CustodyOperation.RELEASE, evidence, now, false);
    }

    public synchronized CustodyOperationResult consume(UUID lotId,
                                                       String requestKey,
                                                       CustodyTransferEvidence evidence,
                                                       Instant now) {
        return terminal(lotId, requestKey, CustodyOperation.CONSUME, evidence, now, true);
    }

    public synchronized CustodyOperationResult preflightConsume(UUID lotId,
                                                                String requestKey,
                                                                CustodyTransferEvidence evidence,
                                                                Instant now) {
        return terminal(lotId, requestKey, CustodyOperation.CONSUME, evidence, now, false);
    }

    public synchronized CustodyOperationResult quarantine(UUID lotId,
                                                          String requestKey,
                                                          CustodyTransferEvidence evidence,
                                                          Instant now) {
        return terminal(lotId, requestKey, CustodyOperation.QUARANTINE, evidence, now, true);
    }

    public synchronized CustodyOperationResult preflightQuarantine(
            UUID lotId,
            String requestKey,
            CustodyTransferEvidence evidence,
            Instant now
    ) {
        return terminal(lotId, requestKey, CustodyOperation.QUARANTINE, evidence, now, false);
    }

    synchronized List<CustodyOperationResult> preflightBatch(
            List<CustodyMutation> mutations
    ) {
        return evaluateBatch(mutations, false);
    }

    synchronized List<CustodyOperationResult> applyBatch(List<CustodyMutation> mutations) {
        return evaluateBatch(mutations, true);
    }

    synchronized long lastBatchProbeCount() {
        return lastBatchProbeCount;
    }

    public synchronized CustodyLot get(UUID lotId) {
        return lots.get(Objects.requireNonNull(lotId, "lotId"));
    }

    public synchronized CustodyOperationReceipt receiptForRequest(String requestKey) {
        UUID receiptId = requestIndex.get(CustodyLot.requireRequestKey(requestKey));
        return receiptId == null ? null : receipts.get(receiptId);
    }

    public synchronized CustodyLiabilityReport outstandingLiabilities() {
        Map<CustodyLiabilityKey, Long> outstanding = new HashMap<>();
        long wallet = 0L;
        long protectedCurrency = 0L;
        long foreignCurrency = 0L;
        long items = 0L;
        long heldLots = 0L;
        for (CustodyLot lot : lots.values()) {
            if (lot.state() != CustodyLotState.HELD) {
                continue;
            }
            CustodyLiabilityKey key = CustodyLiabilityKey.from(lot);
            outstanding.merge(key, lot.units(), Math::addExact);
            heldLots = Math.addExact(heldLots, 1L);
            switch (lot.assetType()) {
                case WALLET_RESERVE -> wallet = Math.addExact(wallet, lot.units());
                case ITEM_STACK -> items = Math.addExact(items, lot.units());
                case PROTECTED_PHYSICAL_CURRENCY ->
                        protectedCurrency = Math.addExact(protectedCurrency, lot.units());
                case FOREIGN_PHYSICAL_CURRENCY ->
                        foreignCurrency = Math.addExact(foreignCurrency, lot.units());
            }
        }
        return new CustodyLiabilityReport(outstanding, wallet, protectedCurrency,
                foreignCurrency, items, heldLots);
    }

    public synchronized CustodyConservationReport conservation() {
        Map<CustodyLiabilityKey, Long> reserved = new HashMap<>();
        Map<CustodyLiabilityKey, Long> held = new HashMap<>();
        Map<CustodyLiabilityKey, Long> released = new HashMap<>();
        Map<CustodyLiabilityKey, Long> consumed = new HashMap<>();
        Map<CustodyLiabilityKey, Long> quarantined = new HashMap<>();
        List<String> violations = new ArrayList<>();
        Map<UUID, Integer> reserveCounts = new HashMap<>();
        Map<UUID, Integer> terminalCounts = new HashMap<>();
        Map<UUID, CustodyLotState> terminalStates = new HashMap<>();

        for (CustodyOperationReceipt receipt : receipts.values()) {
            CustodyLot lot = lots.get(receipt.lotId());
            if (lot == null) {
                violations.add("Custody receipt references a missing lot " + receipt.lotId());
                continue;
            }
            if (!receipt.transactionId().equals(lot.transactionId())
                    || receipt.units() != lot.units()
                    || !CustodyHashes.equal(receipt.assetFingerprint(), lot.assetFingerprint())) {
                violations.add("Custody receipt identity does not match lot " + lot.lotId());
            }
            if (receipt.operation() == CustodyOperation.RESERVE) {
                reserveCounts.merge(lot.lotId(), 1, Math::addExact);
            } else {
                terminalCounts.merge(lot.lotId(), 1, Math::addExact);
                terminalStates.put(lot.lotId(), receipt.resultingState());
            }
        }

        for (CustodyLot lot : lots.values()) {
            CustodyLiabilityKey key = CustodyLiabilityKey.from(lot);
            merge(reserved, key, lot.units());
            switch (lot.state()) {
                case HELD -> merge(held, key, lot.units());
                case RELEASED -> merge(released, key, lot.units());
                case CONSUMED -> merge(consumed, key, lot.units());
                case QUARANTINED -> merge(quarantined, key, lot.units());
            }
            int reserveCount = reserveCounts.getOrDefault(lot.lotId(), 0);
            int terminalCount = terminalCounts.getOrDefault(lot.lotId(), 0);
            if (reserveCount != 1) {
                violations.add("Custody lot must have one reserve receipt " + lot.lotId());
            }
            int expectedTerminalCount = lot.state() == CustodyLotState.HELD ? 0 : 1;
            if (terminalCount != expectedTerminalCount
                    || terminalCount == 1
                    && terminalStates.get(lot.lotId()) != lot.state()) {
                violations.add("Custody terminal receipt does not match lot " + lot.lotId());
            }
        }

        for (Map.Entry<CustodyLiabilityKey, Long> entry : reserved.entrySet()) {
            CustodyLiabilityKey key = entry.getKey();
            long accounted = 0L;
            accounted = Math.addExact(accounted, held.getOrDefault(key, 0L));
            accounted = Math.addExact(accounted, released.getOrDefault(key, 0L));
            accounted = Math.addExact(accounted, consumed.getOrDefault(key, 0L));
            accounted = Math.addExact(accounted, quarantined.getOrDefault(key, 0L));
            if (accounted != entry.getValue()) {
                violations.add("Custody units do not conserve for " + key);
            }
        }
        return new CustodyConservationReport(reserved, held, released, consumed,
                quarantined, violations.isEmpty(), violations);
    }

    synchronized Map<UUID, CustodyLot> snapshotLots() {
        return Map.copyOf(lots);
    }

    synchronized Map<UUID, CustodyOperationReceipt> snapshotReceipts() {
        return Map.copyOf(receipts);
    }

    synchronized void restore(Map<UUID, CustodyLot> restoredLots,
                              Map<UUID, CustodyOperationReceipt> restoredReceipts) {
        Objects.requireNonNull(restoredLots, "restoredLots");
        Objects.requireNonNull(restoredReceipts, "restoredReceipts");
        lots.clear();
        receipts.clear();
        requestIndex.clear();
        storedNbtBytes = 0L;
        if (restoredLots.size() > maximumLots
                || restoredReceipts.size() > maximumReceipts) {
            throw new CustodyConflictException("Persisted custody data exceeds repository limits");
        }
        for (CustodyLot lot : restoredLots.values()) {
            storedNbtBytes = Math.addExact(storedNbtBytes, nbtBytes(lot));
            if (storedNbtBytes > maximumNbtBytes) {
                throw new CustodyConflictException("Persisted custody NBT exceeds repository limits");
            }
        }
        lots.putAll(restoredLots);
        for (CustodyOperationReceipt receipt : restoredReceipts.values()) {
            putReceipt(receipt);
        }
        for (CustodyOperationReceipt receipt : receipts.values()) {
            if (!lots.containsKey(receipt.lotId())) {
                lots.clear();
                receipts.clear();
                requestIndex.clear();
                throw new CustodyConflictException("Persisted custody receipt references a missing lot");
            }
        }
        CustodyConservationReport report = conservation();
        if (!report.conserved()) {
            lots.clear();
            receipts.clear();
            requestIndex.clear();
            throw new CustodyConflictException("Persisted custody data does not conserve");
        }
    }

    private List<CustodyOperationResult> evaluateBatch(List<CustodyMutation> mutations,
                                                       boolean commit) {
        List<CustodyMutation> candidates = List.copyOf(
                Objects.requireNonNull(mutations, "mutations"));
        if (candidates.isEmpty() || candidates.size() > CustodyBatchPlan.MAX_BATCH_LOTS) {
            throw new CustodyConflictException("Invalid custody mutation batch size");
        }
        lastBatchProbeCount = 0L;
        List<CustodyOperationResult> results = new ArrayList<>(candidates.size());
        List<CustodyOperationReceipt> newReceipts = new ArrayList<>(candidates.size());
        Map<UUID, CustodyLot> changedLots = new HashMap<>();
        Set<UUID> batchLotIds = new HashSet<>();
        Set<UUID> batchReceiptIds = new HashSet<>();
        Set<String> batchRequestKeys = new HashSet<>();
        int addedLots = 0;
        int addedReceipts = 0;
        long addedNbtBytes = 0L;
        Boolean replayed = null;
        for (CustodyMutation mutation : candidates) {
            Objects.requireNonNull(mutation, "mutation");
            CustodyOperationReceipt receipt = mutation.receipt();
            if (!batchLotIds.add(receipt.lotId())
                    || !batchReceiptIds.add(receipt.receiptId())
                    || !batchRequestKeys.add(receipt.requestKey())) {
                throw new CustodyConflictException("Custody batch mutation identity is duplicated");
            }
            UUID existingReceiptId = requestIndex.get(receipt.requestKey());
            lastBatchProbeCount++;
            CustodyOperationResult result;
            if (existingReceiptId != null) {
                CustodyOperationReceipt existingReceipt = receipts.get(existingReceiptId);
                CustodyLot existingLot = lots.get(receipt.lotId());
                lastBatchProbeCount += 2L;
                if (!receipt.equals(existingReceipt) || existingLot == null
                        || !CustodyHashes.equal(existingLot.assetFingerprint(),
                        receipt.assetFingerprint())) {
                    throw new CustodyConflictException(
                            "Custody batch replay does not match materialized state");
                }
                result = new CustodyOperationResult(existingLot, existingReceipt, true);
            } else {
                CustodyOperationReceipt conflictingReceipt = receipts.get(receipt.receiptId());
                CustodyLot existingLot = lots.get(receipt.lotId());
                lastBatchProbeCount += 2L;
                if (conflictingReceipt != null) {
                    throw new CustodyConflictException("Custody batch receipt identity conflicts");
                }
                CustodyMutation expected;
                if (receipt.operation() == CustodyOperation.RESERVE) {
                    if (existingLot != null) {
                        throw new CustodyConflictException("Custody batch lot already exists");
                    }
                    expected = CustodyMutation.reserve(mutation.resultingLot());
                    addedLots = Math.addExact(addedLots, 1);
                    addedNbtBytes = Math.addExact(
                            addedNbtBytes, nbtBytes(mutation.resultingLot()));
                } else {
                    if (existingLot == null || existingLot.state() != CustodyLotState.HELD) {
                        throw new CustodyConflictException(
                                "Custody batch terminal lot is not held");
                    }
                    expected = CustodyMutation.terminal(existingLot, receipt.operation(),
                            receipt.requestKey(), receipt.evidence(), receipt.createdAt());
                }
                if (!expected.equals(mutation)) {
                    throw new CustodyConflictException(
                            "Custody batch mutation does not match repository state");
                }
                addedReceipts = Math.addExact(addedReceipts, 1);
                newReceipts.add(receipt);
                changedLots.put(receipt.lotId(), mutation.resultingLot());
                result = new CustodyOperationResult(
                        mutation.resultingLot(), receipt, false);
            }
            if (replayed != null && replayed != result.replayed()) {
                throw new CustodyConflictException(
                        "Custody batch is only partially materialized");
            }
            replayed = result.replayed();
            results.add(result);
        }
        if (Math.addExact(lots.size(), addedLots) > maximumLots
                || Math.addExact(receipts.size(), addedReceipts) > maximumReceipts) {
            throw new CustodyConflictException("Custody repository entry limit is exceeded");
        }
        if (Math.addExact(storedNbtBytes, addedNbtBytes) > maximumNbtBytes) {
            throw new CustodyConflictException("Custody repository NBT limit is exceeded");
        }
        if (commit && !Boolean.TRUE.equals(replayed)) {
            for (CustodyOperationReceipt receipt : newReceipts) {
                putReceipt(receipt);
            }
            lots.putAll(changedLots);
            storedNbtBytes = Math.addExact(storedNbtBytes, addedNbtBytes);
        }
        return List.copyOf(results);
    }

    private CustodyOperationResult terminal(UUID lotId,
                                            String requestKey,
                                            CustodyOperation operation,
                                            CustodyTransferEvidence evidence,
                                            Instant now,
                                            boolean commit) {
        Objects.requireNonNull(lotId, "lotId");
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(now, "now");
        requestKey = CustodyLot.requireRequestKey(requestKey);
        CustodyLot lot = lots.get(lotId);
        if (lot == null) {
            throw new CustodyConflictException("Custody lot does not exist");
        }
        CustodyOperationResult replay = replay(requestKey, operation, lotId, lot.assetFingerprint());
        if (replay != null) {
            if (!replay.receipt().evidence().equals(evidence)) {
                throw new CustodyConflictException("Custody operation replay evidence does not match");
            }
            return replay;
        }
        if (lot.state() != CustodyLotState.HELD) {
            throw new CustodyConflictException("Custody lot has already reached a terminal state");
        }
        CustodyOperationReceipt receipt = CustodyOperationReceipt.terminal(lot, operation,
                requestKey, evidence, now);
        CustodyLot transitioned = lot.transition(receipt.resultingState(), now);
        if (receipts.size() >= maximumReceipts) {
            throw new CustodyConflictException("Custody receipt limit is exceeded");
        }
        requireReceiptAvailable(receipt);
        if (commit) {
            putReceipt(receipt);
            lots.put(lotId, transitioned);
        }
        return new CustodyOperationResult(transitioned, receipt, false);
    }

    private CustodyOperationResult replay(String requestKey,
                                          CustodyOperation operation,
                                          UUID lotId,
                                          byte[] fingerprint) {
        UUID receiptId = requestIndex.get(requestKey);
        if (receiptId == null) {
            return null;
        }
        CustodyOperationReceipt receipt = receipts.get(receiptId);
        if (receipt == null
                || receipt.operation() != operation
                || !receipt.lotId().equals(lotId)
                || !CustodyHashes.equal(receipt.assetFingerprint(), fingerprint)) {
            throw new CustodyConflictException("Custody request key was reused with different content");
        }
        CustodyLot lot = lots.get(lotId);
        if (lot == null) {
            throw new CustodyConflictException("Custody replay state is inconsistent");
        }
        return new CustodyOperationResult(lot, receipt, true);
    }

    private void putReceipt(CustodyOperationReceipt receipt) {
        CustodyOperationReceipt oldReceipt = receipts.putIfAbsent(receipt.receiptId(), receipt);
        if (oldReceipt != null && !oldReceipt.equals(receipt)) {
            throw new CustodyConflictException("Duplicate custody receipt ID");
        }
        UUID oldRequest = requestIndex.putIfAbsent(receipt.requestKey(), receipt.receiptId());
        if (oldRequest != null && !oldRequest.equals(receipt.receiptId())) {
            throw new CustodyConflictException("Duplicate custody request key");
        }
    }

    private void requireReceiptAvailable(CustodyOperationReceipt receipt) {
        CustodyOperationReceipt oldReceipt = receipts.get(receipt.receiptId());
        UUID oldRequest = requestIndex.get(receipt.requestKey());
        if (oldReceipt != null && !oldReceipt.equals(receipt)
                || oldRequest != null && !oldRequest.equals(receipt.receiptId())) {
            throw new CustodyConflictException("Custody receipt identity conflicts");
        }
    }

    private static void merge(Map<CustodyLiabilityKey, Long> values,
                              CustodyLiabilityKey key,
                              long units) {
        values.merge(key, units, Math::addExact);
    }

    private static long nbtBytes(CustodyLot lot) {
        long bytes = 0L;
        for (CustodyItemSnapshot snapshot : lot.itemSnapshots()) {
            bytes = Math.addExact(bytes, snapshot.serializedNbt().length);
        }
        return bytes;
    }
}
