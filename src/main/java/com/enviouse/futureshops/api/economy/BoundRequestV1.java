package com.enviouse.futureshops.api.economy;

import java.util.Objects;

/** Immutable request bound to one account identity and durable operation leg. */
public record BoundRequestV1(
        BindingV1 binding,
        RequestId rootId,
        RequestId legId,
        String operation,
        long minorUnits,
        String payloadFingerprint) {
    public BoundRequestV1 {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(rootId, "rootId");
        Objects.requireNonNull(legId, "legId");
        if (operation == null || operation.isBlank() || operation.length() > 64
                || operation.indexOf('\n') >= 0 || operation.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("operation must be a bounded single line");
        }
        if (minorUnits <= 0L) {
            throw new IllegalArgumentException("minorUnits must be positive");
        }
        if (payloadFingerprint == null || payloadFingerprint.length() != 64
                || !payloadFingerprint.matches("[0-9a-fA-F]+")) {
            throw new IllegalArgumentException("payloadFingerprint must be a sha256 fingerprint");
        }
    }
}
