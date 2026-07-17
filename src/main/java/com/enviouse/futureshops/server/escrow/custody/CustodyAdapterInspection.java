package com.enviouse.futureshops.server.escrow.custody;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record CustodyAdapterInspection(
        CustodyAdapterInspectionStatus status,
        Map<UUID, CustodyTransferEvidence> evidenceByLot,
        String detail
) {
    public CustodyAdapterInspection {
        Objects.requireNonNull(status, "status");
        evidenceByLot = Map.copyOf(Objects.requireNonNull(evidenceByLot, "evidenceByLot"));
        detail = Objects.requireNonNull(detail, "detail").strip();
        if (detail.isEmpty() || detail.length() > 1024) {
            throw new IllegalArgumentException("Invalid custody adapter inspection detail");
        }
        if ((status == CustodyAdapterInspectionStatus.APPLIED) != !evidenceByLot.isEmpty()) {
            throw new IllegalArgumentException("Custody adapter inspection evidence is invalid");
        }
    }

    public static CustodyAdapterInspection applied(
            Map<UUID, CustodyTransferEvidence> evidenceByLot,
            String detail
    ) {
        return new CustodyAdapterInspection(
                CustodyAdapterInspectionStatus.APPLIED, evidenceByLot, detail);
    }

    public static CustodyAdapterInspection notApplied(String detail) {
        return new CustodyAdapterInspection(
                CustodyAdapterInspectionStatus.NOT_APPLIED, Map.of(), detail);
    }

    public static CustodyAdapterInspection unknown(String detail) {
        return new CustodyAdapterInspection(
                CustodyAdapterInspectionStatus.UNKNOWN, Map.of(), detail);
    }
}
