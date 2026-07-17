package com.enviouse.futureshops.server.escrow.item;

import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterInspectionStatus;

import java.util.Objects;
import java.util.Optional;

public record ItemInventoryReceiptInspection(
        CustodyAdapterInspectionStatus status,
        Optional<ItemInventoryReceiptEvidence> evidence,
        String detail
) {
    public ItemInventoryReceiptInspection {
        Objects.requireNonNull(status, "status");
        evidence = Objects.requireNonNull(evidence, "evidence");
        detail = Objects.requireNonNull(detail, "detail");
        if (detail.isBlank() || detail.length() > 256
                || status == CustodyAdapterInspectionStatus.APPLIED
                && evidence.isEmpty()
                || status == CustodyAdapterInspectionStatus.NOT_APPLIED
                && evidence.isPresent()) {
            throw new IllegalArgumentException(
                    "Item inventory receipt inspection is invalid");
        }
    }
}
