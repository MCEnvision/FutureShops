package com.enviouse.futureshops.server.escrow.admin;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public record MaintenanceRepairTarget(MaintenanceRepairTargetType type, UUID targetId) {
    public static final UUID RUNTIME_TARGET_ID = UUID.nameUUIDFromBytes(
            "futureshops escrow runtime".getBytes(StandardCharsets.UTF_8));

    public MaintenanceRepairTarget {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(targetId, "targetId");
        if (targetId.getMostSignificantBits() == 0L
                && targetId.getLeastSignificantBits() == 0L) {
            throw new IllegalArgumentException("Invalid maintenance target ID");
        }
        if (type == MaintenanceRepairTargetType.RUNTIME
                && !RUNTIME_TARGET_ID.equals(targetId)) {
            throw new IllegalArgumentException("Invalid maintenance runtime target ID");
        }
    }

    public static MaintenanceRepairTarget runtime() {
        return new MaintenanceRepairTarget(MaintenanceRepairTargetType.RUNTIME,
                RUNTIME_TARGET_ID);
    }

    public static MaintenanceRepairTarget transaction(UUID transactionId) {
        return new MaintenanceRepairTarget(MaintenanceRepairTargetType.TRANSACTION,
                transactionId);
    }

    public static MaintenanceRepairTarget claim(UUID claimId) {
        return new MaintenanceRepairTarget(MaintenanceRepairTargetType.CLAIM, claimId);
    }

    public static MaintenanceRepairTarget custodyLot(UUID lotId) {
        return new MaintenanceRepairTarget(MaintenanceRepairTargetType.CUSTODY_LOT, lotId);
    }

    public static MaintenanceRepairTarget custodyBatch(UUID batchId) {
        return new MaintenanceRepairTarget(MaintenanceRepairTargetType.CUSTODY_BATCH, batchId);
    }
}
