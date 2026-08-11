package com.enviouse.futureshops.server.escrow.item;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class ItemInventoryCheckpointCompactionProof {
    private final long checkpointGeneration;
    private final byte[] checkpointDigest;
    private final Map<UUID, ItemInventoryCheckpointRequestEvidence> evidence;

    private ItemInventoryCheckpointCompactionProof(
            long checkpointGeneration,
            byte[] checkpointDigest,
            Map<UUID, ItemInventoryCheckpointRequestEvidence> evidence
    ) {
        this.checkpointGeneration = checkpointGeneration;
        this.checkpointDigest = Objects.requireNonNull(
                checkpointDigest, "checkpointDigest").clone();
        this.evidence = Map.copyOf(Objects.requireNonNull(
                evidence, "evidence"));
    }

    static ItemInventoryCheckpointCompactionProof verified(
            long checkpointGeneration,
            byte[] checkpointDigest,
            Map<UUID, ItemInventoryCheckpointRequestEvidence> evidence
    ) {
        if (checkpointGeneration <= 0L) {
            throw new IllegalArgumentException(
                    "Item checkpoint generation is invalid");
        }
        byte[] digest = Objects.requireNonNull(
                checkpointDigest, "checkpointDigest").clone();
        ItemInventoryHashes.requireHash(digest,
                "Item checkpoint digest");
        Map<UUID, ItemInventoryCheckpointRequestEvidence> values =
                Map.copyOf(Objects.requireNonNull(evidence, "evidence"));
        if (values.isEmpty()) {
            throw new IllegalArgumentException(
                    "Item checkpoint proof is empty");
        }
        for (Map.Entry<UUID, ItemInventoryCheckpointRequestEvidence> entry
                : values.entrySet()) {
            UUID requestId = ItemInventoryBatchEntry.requireUuid(
                    entry.getKey(), "requestId");
            ItemInventoryCheckpointRequestEvidence value =
                    Objects.requireNonNull(entry.getValue(), "evidence");
            if (!requestId.equals(value.requestId())
                    || !value.terminalState().terminal()
                    || !value.compactionEligible()) {
                throw new IllegalArgumentException(
                        "Item checkpoint request is not eligible");
            }
        }
        return new ItemInventoryCheckpointCompactionProof(
                checkpointGeneration, digest, values);
    }

    long checkpointGeneration() {
        return checkpointGeneration;
    }

    byte[] checkpointDigest() {
        return checkpointDigest.clone();
    }

    Map<UUID, ItemInventoryCheckpointRequestEvidence> evidence() {
        return evidence;
    }
}
