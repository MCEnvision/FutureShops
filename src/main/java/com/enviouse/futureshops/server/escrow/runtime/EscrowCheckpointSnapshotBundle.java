package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.checkpoint.EscrowCheckpointStore;
import com.enviouse.futureshops.server.escrow.checkpoint.TrustedEscrowCheckpoint;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalSnapshot;

import java.util.Map;
import java.util.Optional;

public interface EscrowCheckpointSnapshotBundle {
    Map<EscrowCheckpointStore, byte[]> captureSnapshots();

    EscrowPreparedCheckpointRestore prepareTrustedRestore(
            TrustedEscrowCheckpoint trustedCheckpoint);

    default Optional<ItemInventoryJournalSnapshot>
    trustedItemInventoryJournalSnapshot(
            TrustedEscrowCheckpoint trustedCheckpoint
    ) {
        return Optional.empty();
    }
}
