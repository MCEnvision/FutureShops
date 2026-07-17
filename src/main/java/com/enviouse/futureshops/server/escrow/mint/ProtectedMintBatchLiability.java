package com.enviouse.futureshops.server.escrow.mint;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ProtectedMintBatchLiability(UUID batchId,
                                          long denominationMinorUnits,
                                          int authorizedCount,
                                          int authorizedQuantity,
                                          int availableQuantity,
                                          Map<UUID, Integer> reservedQuantities,
                                          String serverIdentityEvidence,
                                          String checksumEvidence) {
    public ProtectedMintBatchLiability {
        Objects.requireNonNull(batchId, "batchId");
        reservedQuantities = Map.copyOf(Objects.requireNonNull(
                reservedQuantities, "reservedQuantities"));
        serverIdentityEvidence = Objects.requireNonNull(
                serverIdentityEvidence, "serverIdentityEvidence");
        checksumEvidence = Objects.requireNonNull(
                checksumEvidence, "checksumEvidence");
        if (denominationMinorUnits <= 0L || authorizedCount <= 0
                || authorizedQuantity < 0 || availableQuantity < 0) {
            throw new IllegalArgumentException("Protected mint liability is invalid");
        }
        long reserved = 0L;
        for (Map.Entry<UUID, Integer> entry : reservedQuantities.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "reserved transaction");
            if (entry.getValue() == null || entry.getValue() <= 0) {
                throw new IllegalArgumentException(
                        "Protected mint reserved liability is invalid");
            }
            reserved = Math.addExact(reserved, entry.getValue().longValue());
        }
        long outstanding = Math.addExact((long) authorizedQuantity,
                Math.addExact((long) availableQuantity, reserved));
        if (outstanding > authorizedCount) {
            throw new IllegalArgumentException(
                    "Protected mint outstanding liability exceeds authorization");
        }
    }
}
