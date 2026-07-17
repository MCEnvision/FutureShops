package com.enviouse.futureshops.server.escrow.item.runtime;

public enum ItemInventoryQuarantineReason {
    UNKNOWN_SLOT_IMAGE(1),
    UNSUPPORTED_STACK(2),
    ROLLBACK_FAILED(3),
    COMMITTED_REPAIR_FAILED(4);

    private final int wireCode;

    ItemInventoryQuarantineReason(int wireCode) {
        this.wireCode = wireCode;
    }

    public int wireCode() {
        return wireCode;
    }

    public static ItemInventoryQuarantineReason fromWireCode(int code) {
        for (ItemInventoryQuarantineReason value : values()) {
            if (value.wireCode == code) {
                return value;
            }
        }
        throw new IllegalArgumentException(
                "Item inventory quarantine reason is invalid");
    }
}
