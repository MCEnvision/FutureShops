package com.enviouse.futureshops.server.escrow.inventory;

import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterInspectionStatus;

import java.util.Objects;
import java.util.Optional;

record PlayerInventoryReceiptInspection(
        CustodyAdapterInspectionStatus status,
        Optional<PlayerInventoryDeliveryReceipt> receipt,
        String detail
) {
    PlayerInventoryReceiptInspection {
        Objects.requireNonNull(status, "status");
        receipt = Objects.requireNonNull(receipt, "receipt");
        detail = Objects.requireNonNull(detail, "detail").strip();
        if (detail.isEmpty()
                || (status == CustodyAdapterInspectionStatus.APPLIED)
                != receipt.isPresent()) {
            throw new IllegalArgumentException(
                    "Player inventory receipt inspection is invalid");
        }
    }
}
