package com.enviouse.futureshops.server.escrow.custody;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CustodyAdapterApplyResult(
        boolean applied,
        Set<UUID> appliedLotIds,
        Map<UUID, CustodyTransferEvidence> evidenceByLot,
        String reason
) {
    public CustodyAdapterApplyResult {
        Objects.requireNonNull(appliedLotIds, "appliedLotIds");
        Objects.requireNonNull(evidenceByLot, "evidenceByLot");
        Objects.requireNonNull(reason, "reason");
        appliedLotIds = Set.copyOf(appliedLotIds);
        evidenceByLot = Map.copyOf(evidenceByLot);
        reason = reason.strip();
        if (!evidenceByLot.keySet().equals(appliedLotIds)) {
            throw new IllegalArgumentException("Custody adapter evidence must match applied lots");
        }
        if (!applied && reason.isEmpty()) {
            throw new IllegalArgumentException("Rejected custody apply requires a reason");
        }
    }

    public static CustodyAdapterApplyResult rejected(String reason) {
        return new CustodyAdapterApplyResult(false, Set.of(), Map.of(), reason);
    }
}
