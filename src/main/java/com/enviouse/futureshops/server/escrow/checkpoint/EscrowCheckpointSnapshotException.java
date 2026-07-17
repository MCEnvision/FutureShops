package com.enviouse.futureshops.server.escrow.checkpoint;

public final class EscrowCheckpointSnapshotException extends IllegalStateException {
    public EscrowCheckpointSnapshotException(String message) {
        super(message);
    }

    public EscrowCheckpointSnapshotException(String message, Throwable cause) {
        super(message, cause);
    }
}
