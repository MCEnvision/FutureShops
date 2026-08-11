package com.enviouse.futureshops.server.escrow.item.runtime;

public enum ItemInventoryAbortReason {
    APPLY_FAILED_ROLLED_BACK(1),
    RECOVERY_FAILED_ROLLED_BACK(2),
    PREIMAGE_CHANGED(3),
    CALLER_CANCELLED(4);

    private final int wireCode;

    ItemInventoryAbortReason(int wireCode) {
        this.wireCode = wireCode;
    }

    public int wireCode() {
        return wireCode;
    }

    public static ItemInventoryAbortReason fromWireCode(int code) {
        for (ItemInventoryAbortReason value : values()) {
            if (value.wireCode == code) {
                return value;
            }
        }
        throw new IllegalArgumentException(
                "Item inventory abort reason is invalid");
    }
}
