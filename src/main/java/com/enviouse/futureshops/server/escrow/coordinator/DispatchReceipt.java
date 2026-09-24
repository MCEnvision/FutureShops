package com.enviouse.futureshops.server.escrow.coordinator;

import java.util.Objects;

/** Provider evidence required before a leg can be confirmed. */
public record DispatchReceipt(String externalOperationId, long minorUnits, String payloadFingerprint) {
    public DispatchReceipt {
        if (externalOperationId == null || externalOperationId.isBlank()
                || externalOperationId.length() > 256
                || externalOperationId.indexOf('\n') >= 0
                || externalOperationId.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("externalOperationId must be bounded");
        }
        if (minorUnits <= 0L) {
            throw new IllegalArgumentException("receipt minorUnits must be positive");
        }
        if (payloadFingerprint == null || payloadFingerprint.length() != 64
                || !payloadFingerprint.matches("[0-9a-fA-F]+")) {
            throw new IllegalArgumentException("receipt payload fingerprint must be sha256");
        }
        Objects.requireNonNull(payloadFingerprint, "payloadFingerprint");
    }
}
