package com.enviouse.futureshops.server.escrow.item;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

final class ItemInventoryCheckpointRequestEvidence {
    private final UUID requestId;
    private final UUID transactionId;
    private final ItemInventoryCheckpointTerminalState terminalState;
    private final boolean compactionEligible;
    private final byte[] terminalStateDigest;

    ItemInventoryCheckpointRequestEvidence(
            UUID requestId,
            UUID transactionId,
            ItemInventoryCheckpointTerminalState terminalState,
            boolean compactionEligible,
            byte[] terminalStateDigest
    ) {
        this.requestId = ItemInventoryBatchEntry.requireUuid(
                requestId, "requestId");
        this.transactionId = ItemInventoryBatchEntry.requireUuid(
                transactionId, "transactionId");
        this.terminalState = Objects.requireNonNull(
                terminalState, "terminalState");
        this.compactionEligible = compactionEligible;
        this.terminalStateDigest = Objects.requireNonNull(
                terminalStateDigest, "terminalStateDigest").clone();
        ItemInventoryHashes.requireHash(this.terminalStateDigest,
                "Item checkpoint terminal state digest");
    }

    UUID requestId() {
        return requestId;
    }

    UUID transactionId() {
        return transactionId;
    }

    ItemInventoryCheckpointTerminalState terminalState() {
        return terminalState;
    }

    boolean compactionEligible() {
        return compactionEligible;
    }

    byte[] terminalStateDigest() {
        return terminalStateDigest.clone();
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ItemInventoryCheckpointRequestEvidence other
                && requestId.equals(other.requestId)
                && transactionId.equals(other.transactionId)
                && terminalState == other.terminalState
                && compactionEligible == other.compactionEligible
                && Arrays.equals(terminalStateDigest,
                other.terminalStateDigest);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(requestId, transactionId, terminalState,
                compactionEligible);
        return 31 * result + Arrays.hashCode(terminalStateDigest);
    }
}
