package com.enviouse.futureshops.server.escrow.admin;

import java.util.Objects;
import java.util.Optional;

public record MaintenanceExpectedState(MaintenanceExpectedStateKind kind,
                                       long expectedRevision,
                                       Optional<MaintenanceStateFingerprint> fingerprint) {
    public MaintenanceExpectedState {
        Objects.requireNonNull(kind, "kind");
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        if (kind == MaintenanceExpectedStateKind.REVISION
                && (expectedRevision < 0L || fingerprint.isPresent())) {
            throw new IllegalArgumentException("Invalid expected maintenance revision");
        }
        if (kind == MaintenanceExpectedStateKind.FINGERPRINT
                && (expectedRevision != -1L || fingerprint.isEmpty())) {
            throw new IllegalArgumentException("Invalid expected maintenance fingerprint");
        }
    }

    public static MaintenanceExpectedState revision(long revision) {
        return new MaintenanceExpectedState(MaintenanceExpectedStateKind.REVISION,
                revision, Optional.empty());
    }

    public static MaintenanceExpectedState fingerprint(byte[] fingerprint) {
        return new MaintenanceExpectedState(MaintenanceExpectedStateKind.FINGERPRINT,
                -1L, Optional.of(MaintenanceStateFingerprint.of(fingerprint)));
    }
}
