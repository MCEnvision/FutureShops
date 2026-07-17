package com.enviouse.futureshops.server.escrow.checkpoint;

import java.io.IOException;

public final class EscrowCheckpointException extends IOException {
    public EscrowCheckpointException(String message) {
        super(message);
    }

    public EscrowCheckpointException(String message, Throwable cause) {
        super(message, cause);
    }
}
