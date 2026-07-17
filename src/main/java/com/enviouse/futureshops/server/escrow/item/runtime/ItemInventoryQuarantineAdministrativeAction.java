package com.enviouse.futureshops.server.escrow.item.runtime;

public enum ItemInventoryQuarantineAdministrativeAction {
    RELEASE(1),
    REFUND(2),
    KEEP_QUARANTINED(3);

    private final int wireCode;

    ItemInventoryQuarantineAdministrativeAction(int wireCode) {
        this.wireCode = wireCode;
    }

    int wireCode() {
        return wireCode;
    }

    static ItemInventoryQuarantineAdministrativeAction fromWireCode(
            int wireCode
    ) {
        for (ItemInventoryQuarantineAdministrativeAction value : values()) {
            if (value.wireCode == wireCode) {
                return value;
            }
        }
        throw new IllegalArgumentException(
                "Item inventory quarantine action is invalid");
    }
}
