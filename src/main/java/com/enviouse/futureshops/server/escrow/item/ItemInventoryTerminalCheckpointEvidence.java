package com.enviouse.futureshops.server.escrow.item;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class ItemInventoryTerminalCheckpointEvidence {
    private final UUID requestId;
    private final UUID transactionId;
    private final ItemInventoryCheckpointTerminalState terminalState;
    private final boolean compactionEligible;
    private final byte[] terminalStateDigest;

    public ItemInventoryTerminalCheckpointEvidence(
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
        if (!terminalState.terminal() || !compactionEligible) {
            throw new IllegalArgumentException(
                    "Item checkpoint evidence is not terminal and eligible");
        }
    }

    public UUID requestId() {
        return requestId;
    }

    public UUID transactionId() {
        return transactionId;
    }

    public ItemInventoryCheckpointTerminalState terminalState() {
        return terminalState;
    }

    public boolean compactionEligible() {
        return compactionEligible;
    }

    public byte[] terminalStateDigest() {
        return terminalStateDigest.clone();
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ItemInventoryTerminalCheckpointEvidence other
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
