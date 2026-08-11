package com.enviouse.futureshops.server.escrow.item;

import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterInspectionStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ItemInventoryReceiptRepository {
    public static final int MAX_RECEIPTS = 4096;
    public static final int MAX_TOTAL_RECEIPT_BYTES = 15 * 1024 * 1024;
    public static final int MAX_TOMBSTONES = 65536;

    private final Map<UUID, ItemInventoryMutationReceipt> receipts;
    private final Map<UUID, ItemInventoryReceiptTombstone> tombstones;
    private final Map<UUID, UUID> receiptIdOwners;
    private final Map<UUID, UUID> mutationIdOwners;
    private final int maximumTombstones;
    private final int maximumSnapshotBytes;
    private long revision;
    private long totalReceiptBytes;

    public ItemInventoryReceiptRepository() {
        this(new ItemInventoryReceiptSnapshot(0L, Map.of(), Map.of()));
    }

    public ItemInventoryReceiptRepository(
            ItemInventoryReceiptSnapshot snapshot
    ) {
        this(snapshot, MAX_TOMBSTONES,
                ItemInventoryReceiptSnapshotCodec.MAX_ENCODED_BYTES);
    }

    ItemInventoryReceiptRepository(
            ItemInventoryReceiptSnapshot snapshot,
            int maximumTombstones,
            int maximumSnapshotBytes
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (maximumTombstones < 0 || maximumTombstones > MAX_TOMBSTONES
                || snapshot.tombstones().size() > maximumTombstones) {
            throw new IllegalArgumentException(
                    "Item inventory receipt tombstone limit is invalid");
        }
        this.receipts = new HashMap<>(snapshot.receipts());
        this.tombstones = new HashMap<>(snapshot.tombstones());
        this.receiptIdOwners = new HashMap<>();
        this.mutationIdOwners = new HashMap<>();
        snapshot.receipts().forEach((requestId, receipt) -> {
            receiptIdOwners.put(receipt.token().receiptId(), requestId);
            mutationIdOwners.put(receipt.token().mutationId(), requestId);
        });
        snapshot.tombstones().forEach((requestId, tombstone) -> {
            receiptIdOwners.put(tombstone.receiptId(), requestId);
            mutationIdOwners.put(tombstone.mutationId(), requestId);
        });
        this.revision = snapshot.revision();
        this.totalReceiptBytes = encodedSize(this.receipts);
        this.maximumTombstones = maximumTombstones;
        this.maximumSnapshotBytes = maximumSnapshotBytes;
        ItemInventoryReceiptSnapshotCodec.requireProjectedSize(
                receipts.size(), totalReceiptBytes, tombstones.size(),
                maximumSnapshotBytes);
    }

    public synchronized ItemInventoryReceiptAppendResult append(
            ItemInventoryMutationReceipt receipt
    ) {
        Objects.requireNonNull(receipt, "receipt");
        ItemInventoryMutationToken token = receipt.token();
        UUID requestId = token.requestId();
        ItemInventoryMutationReceipt existing = receipts.get(requestId);
        if (existing != null) {
            if (existing.token().equals(token)) {
                return ItemInventoryReceiptAppendResult.REPLAYED;
            }
            throw new IllegalStateException(
                    "Item inventory receipt request conflicts");
        }
        ItemInventoryReceiptTombstone tombstone = tombstones.get(requestId);
        if (tombstone != null) {
            if (tombstone.matchesToken(token)) {
                return ItemInventoryReceiptAppendResult.REPLAYED;
            }
            throw new IllegalStateException(
                    "Item inventory receipt request conflicts");
        }
        if (receipts.size() >= MAX_RECEIPTS) {
            throw new IllegalStateException(
                    "Item inventory receipt repository is full");
        }
        requireUniqueIdentity(token);
        int receiptBytes = ItemInventoryMutationReceiptCodec.encode(
                receipt).length;
        long nextBytes = Math.addExact(totalReceiptBytes, receiptBytes);
        long nextRevision = Math.addExact(revision, 1L);
        if (nextBytes > MAX_TOTAL_RECEIPT_BYTES) {
            throw new IllegalStateException(
                    "Item inventory receipt repository byte limit is exceeded");
        }
        ItemInventoryReceiptSnapshotCodec.requireProjectedSize(
                Math.addExact(receipts.size(), 1), nextBytes,
                tombstones.size(), maximumSnapshotBytes);
        receipts.put(requestId, receipt);
        receiptIdOwners.put(token.receiptId(), requestId);
        mutationIdOwners.put(token.mutationId(), requestId);
        revision = nextRevision;
        totalReceiptBytes = nextBytes;
        return ItemInventoryReceiptAppendResult.APPLIED;
    }

    synchronized int compactFullReceiptsWithVerifiedCheckpoint(
            ItemInventoryCheckpointCompactionProof proof
    ) {
        ItemInventoryCheckpointCompactionProof verified =
                Objects.requireNonNull(proof, "proof");
        if (verified.checkpointGeneration() <= 0L) {
            throw new IllegalArgumentException(
                    "Item checkpoint generation is invalid");
        }
        ItemInventoryHashes.requireHash(verified.checkpointDigest(),
                "Item checkpoint digest");
        Map<UUID, ItemInventoryMutationReceipt> nextReceipts =
                new HashMap<>(receipts);
        Map<UUID, ItemInventoryReceiptTombstone> nextTombstones =
                new HashMap<>(tombstones);
        int compacted = 0;
        for (Map.Entry<UUID, ItemInventoryCheckpointRequestEvidence> entry
                : verified.evidence().entrySet()) {
            UUID requestId = entry.getKey();
            UUID transactionId = entry.getValue().transactionId();
            ItemInventoryReceiptTombstone existingTombstone =
                    nextTombstones.get(requestId);
            if (existingTombstone != null) {
                if (!existingTombstone.transactionId().equals(
                        transactionId)) {
                    throw new IllegalStateException(
                            "Item checkpoint transaction conflicts");
                }
                continue;
            }
            ItemInventoryMutationReceipt receipt = nextReceipts.remove(
                    requestId);
            if (receipt == null) {
                throw new IllegalStateException(
                        "Item checkpoint request has no receipt evidence");
            }
            if (!receipt.token().transactionId().equals(transactionId)) {
                throw new IllegalStateException(
                        "Item checkpoint transaction conflicts");
            }
            ItemInventoryReceiptTombstone tombstone =
                    ItemInventoryReceiptTombstone.fromReceipt(receipt);
            if (nextTombstones.putIfAbsent(requestId, tombstone) != null) {
                throw new IllegalStateException(
                        "Item inventory receipt compaction conflicts");
            }
            compacted = Math.addExact(compacted, 1);
        }
        if (compacted == 0) {
            return 0;
        }
        if (nextTombstones.size() > maximumTombstones) {
            throw new IllegalStateException(
                    "Item inventory receipt tombstone limit is exceeded");
        }
        long nextRevision = Math.addExact(revision, 1L);
        long nextReceiptBytes = encodedSize(nextReceipts);
        ItemInventoryReceiptSnapshot nextSnapshot =
                new ItemInventoryReceiptSnapshot(nextRevision,
                        nextReceipts, nextTombstones);
        ItemInventoryReceiptSnapshotCodec.requireProjectedSize(
                nextSnapshot.receipts().size(), nextReceiptBytes,
                nextSnapshot.tombstones().size(), maximumSnapshotBytes);
        receipts.clear();
        receipts.putAll(nextReceipts);
        tombstones.clear();
        tombstones.putAll(nextTombstones);
        revision = nextRevision;
        totalReceiptBytes = nextReceiptBytes;
        return compacted;
    }

    public synchronized ItemInventoryReceiptInspection inspect(
            ItemInventoryMutationToken token,
            ItemInventoryState persistedInventory
    ) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(persistedInventory, "persistedInventory");
        ItemInventoryReceiptEvidence evidence = evidence(token.requestId());
        if (evidence != null) {
            if (!matchesEvidence(evidence, token)) {
                return inspection(CustodyAdapterInspectionStatus.UNKNOWN,
                        Optional.of(evidence),
                        "Receipt request identity conflicts with the token");
            }
            if (matchesChangedSlots(token, persistedInventory, true)) {
                return inspection(CustodyAdapterInspectionStatus.APPLIED,
                        Optional.of(evidence),
                        "Receipt and changed slot postimages prove the mutation was applied");
            }
            return inspection(CustodyAdapterInspectionStatus.UNKNOWN,
                    Optional.of(evidence),
                    "Receipt exists but a changed slot postimage is absent");
        }
        if (matchesChangedSlots(token, persistedInventory, false)) {
            return inspection(CustodyAdapterInspectionStatus.NOT_APPLIED,
                    Optional.empty(),
                    "Changed slot preimages and missing receipt prove no mutation");
        }
        return inspection(CustodyAdapterInspectionStatus.UNKNOWN,
                Optional.empty(),
                "Changed slots do not match a complete mutation image");
    }

    public synchronized ItemInventoryReceiptInspection inspect(
            ItemInventoryMutationToken token,
            byte[] persistedInventoryHash
    ) {
        Objects.requireNonNull(token, "token");
        byte[] currentHash = Objects.requireNonNull(
                persistedInventoryHash, "persistedInventoryHash").clone();
        ItemInventoryHashes.requireHash(currentHash,
                "Persisted item inventory hash");
        ItemInventoryReceiptEvidence evidence = evidence(token.requestId());
        if (evidence != null) {
            if (!matchesEvidence(evidence, token)) {
                return inspection(CustodyAdapterInspectionStatus.UNKNOWN,
                        Optional.of(evidence),
                        "Receipt request identity conflicts with the token");
            }
            if (ItemInventoryHashes.equal(currentHash,
                    token.afterInventoryHash())) {
                return inspection(CustodyAdapterInspectionStatus.APPLIED,
                        Optional.of(evidence),
                        "Receipt and inventory prove the mutation was applied");
            }
            return inspection(CustodyAdapterInspectionStatus.UNKNOWN,
                    Optional.of(evidence),
                    "Receipt exists but the inventory postimage is absent");
        }
        if (ItemInventoryHashes.equal(currentHash,
                token.beforeInventoryHash())) {
            return inspection(CustodyAdapterInspectionStatus.NOT_APPLIED,
                    Optional.empty(),
                    "Inventory preimage and missing receipt prove no mutation");
        }
        return inspection(CustodyAdapterInspectionStatus.UNKNOWN,
                Optional.empty(),
                "Inventory changed without a matching receipt");
    }

    public synchronized Optional<ItemInventoryReceiptEvidence> findEvidence(
            UUID requestId
    ) {
        return Optional.ofNullable(evidence(
                Objects.requireNonNull(requestId, "requestId")));
    }

    public synchronized Optional<ItemInventoryMutationReceipt> findFullReceipt(
            UUID requestId
    ) {
        return Optional.ofNullable(receipts.get(
                Objects.requireNonNull(requestId, "requestId")));
    }

    public synchronized ItemInventoryReceiptSnapshot snapshot() {
        return new ItemInventoryReceiptSnapshot(revision, receipts,
                tombstones);
    }

    private ItemInventoryReceiptEvidence evidence(UUID requestId) {
        ItemInventoryMutationReceipt receipt = receipts.get(requestId);
        return receipt == null ? tombstones.get(requestId)
                : new ItemInventoryFullReceiptEvidence(receipt);
    }

    private static boolean matchesEvidence(
            ItemInventoryReceiptEvidence evidence,
            ItemInventoryMutationToken token
    ) {
        if (evidence instanceof ItemInventoryFullReceiptEvidence full) {
            return full.receipt().token().equals(token);
        }
        return ((ItemInventoryReceiptTombstone) evidence).matchesToken(token);
    }

    private void requireUniqueIdentity(ItemInventoryMutationToken token) {
        if (receiptIdOwners.containsKey(token.receiptId())
                || mutationIdOwners.containsKey(token.mutationId())) {
            throw new IllegalStateException(
                    "Item inventory receipt identity conflicts");
        }
    }

    private static ItemInventoryReceiptInspection inspection(
            CustodyAdapterInspectionStatus status,
            Optional<ItemInventoryReceiptEvidence> evidence,
            String detail
    ) {
        return new ItemInventoryReceiptInspection(status, evidence, detail);
    }

    private static boolean matchesChangedSlots(
            ItemInventoryMutationToken token,
            ItemInventoryState inventory,
            boolean after
    ) {
        for (ItemInventorySlotChange change : token.changes()) {
            byte[] expected = after ? change.afterHash() : change.beforeHash();
            if (!ItemInventoryHashes.equal(
                    inventory.slotHash(change.slot()), expected)) {
                return false;
            }
        }
        return true;
    }

    static long encodedSize(
            Map<UUID, ItemInventoryMutationReceipt> receipts
    ) {
        long bytes = 0L;
        for (ItemInventoryMutationReceipt receipt : receipts.values()) {
            bytes = Math.addExact(bytes,
                    ItemInventoryMutationReceiptCodec.encode(receipt).length);
            if (bytes > MAX_TOTAL_RECEIPT_BYTES) {
                throw new IllegalArgumentException(
                        "Item inventory receipt bytes exceed their limit");
            }
        }
        return bytes;
    }
}
