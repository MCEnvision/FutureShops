package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.admin.MaintenanceStateFingerprint;

import java.util.Objects;

public record MaintenanceRuntimeSnapshot(long revision,
                                         MaintenanceStateFingerprint fingerprint) {
    public MaintenanceRuntimeSnapshot {
        if (revision < 0L) {
            throw new IllegalArgumentException("Invalid maintenance runtime revision");
        }
        Objects.requireNonNull(fingerprint, "fingerprint");
    }
}
