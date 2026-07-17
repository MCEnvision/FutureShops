package com.enviouse.futureshops.server.escrow.item;

import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ItemInventoryReceiptCompactionBridge {
    private ItemInventoryReceiptCompactionBridge() {
    }

    public static int compact(
            ItemInventoryReceiptRepository repository,
            ItemInventoryVerifiedCheckpointEvidence checkpoint
    ) {
        Objects.requireNonNull(repository, "repository");
        ItemInventoryVerifiedCheckpointEvidence verified =
                Objects.requireNonNull(checkpoint, "checkpoint");
        Map<UUID, ItemInventoryCheckpointRequestEvidence> internal =
                new LinkedHashMap<>();
        verified.evidence().forEach((requestId, evidence) -> {
            if (evidence.terminalState()
                    == ItemInventoryCheckpointTerminalState.COMMITTED) {
                ItemInventoryMutationReceipt receipt = repository
                        .findFullReceipt(requestId).orElseThrow(() ->
                                new IllegalStateException(
                                        "Committed checkpoint evidence has no full receipt"));
                if (!MessageDigest.isEqual(receipt.digest(),
                        evidence.terminalStateDigest())) {
                    throw new IllegalStateException(
                            "Committed checkpoint evidence digest conflicts");
                }
            }
            internal.put(requestId,
                    new ItemInventoryCheckpointRequestEvidence(
                            requestId, evidence.transactionId(),
                            evidence.terminalState(),
                            evidence.compactionEligible(),
                            evidence.terminalStateDigest()));
        });
        ItemInventoryCheckpointCompactionProof proof =
                ItemInventoryCheckpointCompactionProof.verified(
                        verified.checkpointGeneration(),
                        verified.checkpointDigest(), internal);
        return repository.compactFullReceiptsWithVerifiedCheckpoint(proof);
    }
}
