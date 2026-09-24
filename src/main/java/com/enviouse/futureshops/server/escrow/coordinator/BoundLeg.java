package com.enviouse.futureshops.server.escrow.coordinator;

import com.enviouse.futureshops.api.economy.BindingV1;
import com.enviouse.futureshops.api.economy.LegId;
import com.enviouse.futureshops.api.economy.RootId;

import java.util.Objects;

/** Immutable monetary leg bound to the provider identity captured at admission. */
public record BoundLeg(
        RootId rootId,
        LegId legId,
        BindingV1 binding,
        String operation,
        long minorUnits,
        String payloadFingerprint,
        boolean debit,
        String intendedPhase,
        long createdAtEpochMillis,
        RootId parentRootId) {
    public BoundLeg(RootId rootId, LegId legId, BindingV1 binding, String operation,
                    long minorUnits, String payloadFingerprint, boolean debit) {
        this(rootId, legId, binding, operation, minorUnits, payloadFingerprint, debit,
                "phase-003", System.currentTimeMillis(), null);
    }

    public BoundLeg {
        rootId = Objects.requireNonNull(rootId, "rootId");
        legId = Objects.requireNonNull(legId, "legId");
        binding = Objects.requireNonNull(binding, "binding");
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
        if (intendedPhase == null || intendedPhase.isBlank() || intendedPhase.length() > 64
                || intendedPhase.indexOf('\n') >= 0 || intendedPhase.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("intendedPhase must be a bounded single line");
        }
        if (createdAtEpochMillis <= 0L) {
            throw new IllegalArgumentException("createdAtEpochMillis must be positive");
        }
        if (parentRootId != null && parentRootId.equals(rootId)) {
            throw new IllegalArgumentException("parent root must differ from child root");
        }
    }
}
