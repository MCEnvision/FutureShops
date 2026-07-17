package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.checkpoint.EscrowCheckpointStore;
import com.enviouse.futureshops.server.escrow.checkpoint.TrustedEscrowCheckpoint;

import java.util.Map;

public interface EscrowCheckpointSnapshotBundle {
    Map<EscrowCheckpointStore, byte[]> captureSnapshots();

    EscrowPreparedCheckpointRestore prepareTrustedRestore(
            TrustedEscrowCheckpoint trustedCheckpoint);
}
