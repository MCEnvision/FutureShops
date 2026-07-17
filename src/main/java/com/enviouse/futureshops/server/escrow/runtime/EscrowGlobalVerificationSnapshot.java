package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.admin.MaintenanceStateFingerprint;

import java.util.Objects;

public record EscrowGlobalVerificationSnapshot(long journalSequence,
                                               MaintenanceStateFingerprint fingerprint) {
    public EscrowGlobalVerificationSnapshot {
        if (journalSequence < 0L) {
            throw new IllegalArgumentException(
                    "Invalid escrow global verification sequence");
        }
        Objects.requireNonNull(fingerprint, "fingerprint");
    }
}
