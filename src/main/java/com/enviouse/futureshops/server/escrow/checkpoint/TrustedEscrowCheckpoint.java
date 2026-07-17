package com.enviouse.futureshops.server.escrow.checkpoint;

import java.nio.file.Path;
import java.util.Objects;

public record TrustedEscrowCheckpoint(EscrowCheckpoint checkpoint,
                                      EscrowCheckpointReference reference,
                                      Path generationPath) {
    public TrustedEscrowCheckpoint {
        Objects.requireNonNull(checkpoint, "checkpoint");
        Objects.requireNonNull(reference, "reference");
        generationPath = Objects.requireNonNull(generationPath, "generationPath")
                .toAbsolutePath().normalize();
        if (!reference.manifest().describes(checkpoint)) {
            throw new IllegalArgumentException("Checkpoint reference metadata does not match");
        }
    }
}
