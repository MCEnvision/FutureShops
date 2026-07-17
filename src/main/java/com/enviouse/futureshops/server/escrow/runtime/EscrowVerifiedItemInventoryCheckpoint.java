package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.checkpoint.EscrowCheckpointReference;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalSnapshot;

import java.util.Objects;

public record EscrowVerifiedItemInventoryCheckpoint(
        EscrowCheckpointReference reference,
        ItemInventoryJournalSnapshot snapshot
) {
    public EscrowVerifiedItemInventoryCheckpoint {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(snapshot, "snapshot");
    }
}
