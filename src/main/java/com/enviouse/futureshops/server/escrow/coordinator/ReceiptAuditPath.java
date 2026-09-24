package com.enviouse.futureshops.server.escrow.coordinator;

import java.nio.file.Path;
import java.util.Objects;

/** Canonical local path for receipt facts owned by the existing escrow WAL boundary. */
public final class ReceiptAuditPath {
    private ReceiptAuditPath() {
    }

    public static Path forWorld(Path worldRoot) {
        Objects.requireNonNull(worldRoot, "worldRoot");
        return worldRoot.toAbsolutePath().normalize()
                .resolve("data")
                .resolve("futureshops")
                .resolve("receipts")
                .resolve("economy-coordinator.wal");
    }
}
