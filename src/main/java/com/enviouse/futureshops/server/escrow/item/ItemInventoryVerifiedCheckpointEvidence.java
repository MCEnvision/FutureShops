package com.enviouse.futureshops.server.escrow.item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ItemInventoryVerifiedCheckpointEvidence {
    private final long checkpointGeneration;
    private final byte[] checkpointDigest;
    private final Map<UUID, ItemInventoryTerminalCheckpointEvidence> evidence;

    public ItemInventoryVerifiedCheckpointEvidence(
            long checkpointGeneration,
            byte[] checkpointDigest,
            List<ItemInventoryTerminalCheckpointEvidence> evidence
    ) {
        if (checkpointGeneration <= 0L) {
            throw new IllegalArgumentException(
                    "Item checkpoint generation is invalid");
        }
        this.checkpointGeneration = checkpointGeneration;
        this.checkpointDigest = Objects.requireNonNull(
                checkpointDigest, "checkpointDigest").clone();
        ItemInventoryHashes.requireHash(this.checkpointDigest,
                "Item checkpoint digest");
        List<ItemInventoryTerminalCheckpointEvidence> values = List.copyOf(
                Objects.requireNonNull(evidence, "evidence"));
        if (values.isEmpty()
                || values.size()
                > ItemInventoryReceiptRepository.MAX_RECEIPTS) {
            throw new IllegalArgumentException(
                    "Item checkpoint evidence count is invalid");
        }
        Map<UUID, ItemInventoryTerminalCheckpointEvidence> copied =
                new LinkedHashMap<>();
        for (ItemInventoryTerminalCheckpointEvidence value : values) {
            ItemInventoryTerminalCheckpointEvidence required =
                    Objects.requireNonNull(value, "evidence");
            if (copied.put(required.requestId(), required) != null) {
                throw new IllegalArgumentException(
                        "Item checkpoint evidence repeats a request");
            }
        }
        this.evidence = Map.copyOf(copied);
    }

    public long checkpointGeneration() {
        return checkpointGeneration;
    }

    public byte[] checkpointDigest() {
        return checkpointDigest.clone();
    }

    public Map<UUID, ItemInventoryTerminalCheckpointEvidence> evidence() {
        return evidence;
    }
}
