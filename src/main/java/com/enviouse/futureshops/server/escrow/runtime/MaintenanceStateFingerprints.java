package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.admin.MaintenanceExpectedState;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceExpectedStateKind;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceStateFingerprint;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

public final class MaintenanceStateFingerprints {
    private MaintenanceStateFingerprints() {
    }

    public static MaintenanceStateFingerprint sha256(byte[] canonicalState) {
        Objects.requireNonNull(canonicalState, "canonicalState");
        try {
            return MaintenanceStateFingerprint.of(
                    MessageDigest.getInstance("SHA-256").digest(canonicalState));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static void requireExpected(MaintenanceExpectedState expected,
                                       long currentRevision,
                                       MaintenanceStateFingerprint currentFingerprint) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(currentFingerprint, "currentFingerprint");
        if (expected.kind() == MaintenanceExpectedStateKind.REVISION) {
            if (currentRevision < 0L) {
                throw new EscrowRuntimeException(
                        "Maintenance target does not expose a revision");
            }
            if (expected.expectedRevision() != currentRevision) {
                throw new EscrowRuntimeException(
                        "Maintenance target revision does not match");
            }
            return;
        }
        byte[] expectedBytes = expected.fingerprint().orElseThrow().bytes();
        if (!MessageDigest.isEqual(expectedBytes, currentFingerprint.bytes())) {
            throw new EscrowRuntimeException(
                    "Maintenance target fingerprint does not match");
        }
    }
}
