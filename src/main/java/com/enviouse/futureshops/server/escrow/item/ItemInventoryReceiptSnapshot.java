package com.enviouse.futureshops.server.escrow.item;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public record ItemInventoryReceiptSnapshot(
        long revision,
        Map<UUID, ItemInventoryMutationReceipt> receipts,
        Map<UUID, ItemInventoryReceiptTombstone> tombstones
) {
    public ItemInventoryReceiptSnapshot(
            long revision,
            Map<UUID, ItemInventoryMutationReceipt> receipts
    ) {
        this(revision, receipts, Map.of());
    }

    public ItemInventoryReceiptSnapshot {
        receipts = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(
                receipts, "receipts")));
        tombstones = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(
                tombstones, "tombstones")));
        if (revision < 0L
                || receipts.size()
                > ItemInventoryReceiptRepository.MAX_RECEIPTS
                || tombstones.size()
                > ItemInventoryReceiptRepository.MAX_TOMBSTONES) {
            throw new IllegalArgumentException(
                    "Item inventory receipt snapshot is invalid");
        }
        if (revision < Math.addExact((long) receipts.size(),
                tombstones.size())) {
            throw new IllegalArgumentException(
                    "Item inventory receipt snapshot revision is invalid");
        }
        Set<UUID> requestIds = new HashSet<>();
        Set<UUID> receiptIds = new HashSet<>();
        Set<UUID> mutationIds = new HashSet<>();
        for (Map.Entry<UUID, ItemInventoryMutationReceipt> entry
                : receipts.entrySet()) {
            ItemInventoryMutationToken token = Objects.requireNonNull(
                    entry.getValue(), "receipt").token();
            UUID requestId = ItemInventoryBatchEntry.requireUuid(
                    entry.getKey(), "requestId");
            if (!requestId
                    .equals(token.requestId())
                    || !requestIds.add(requestId)
                    || !receiptIds.add(token.receiptId())
                    || !mutationIds.add(token.mutationId())) {
                throw new IllegalArgumentException(
                        "Item inventory receipt snapshot key is invalid");
            }
        }
        for (Map.Entry<UUID, ItemInventoryReceiptTombstone> entry
                : tombstones.entrySet()) {
            ItemInventoryReceiptTombstone tombstone = Objects.requireNonNull(
                    entry.getValue(), "tombstone");
            UUID requestId = ItemInventoryBatchEntry.requireUuid(
                    entry.getKey(), "requestId");
            if (!requestId.equals(tombstone.requestId())
                    || !requestIds.add(requestId)
                    || !receiptIds.add(tombstone.receiptId())
                    || !mutationIds.add(tombstone.mutationId())) {
                throw new IllegalArgumentException(
                        "Item inventory receipt snapshot identity conflicts");
            }
        }
        long receiptBytes = ItemInventoryReceiptRepository.encodedSize(
                receipts);
        ItemInventoryReceiptSnapshotCodec.requireProjectedSize(
                receipts.size(), receiptBytes, tombstones.size());
    }
}
