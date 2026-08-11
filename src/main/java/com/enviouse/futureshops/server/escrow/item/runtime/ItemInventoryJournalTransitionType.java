package com.enviouse.futureshops.server.escrow.item.runtime;

public enum ItemInventoryJournalTransitionType {
    PREPARE(1),
    COMMIT(2),
    ABORT(3),
    QUARANTINE(4);

    private final int wireCode;

    ItemInventoryJournalTransitionType(int wireCode) {
        this.wireCode = wireCode;
    }

    public int wireCode() {
        return wireCode;
    }

    public static ItemInventoryJournalTransitionType fromWireCode(
            int wireCode
    ) {
        for (ItemInventoryJournalTransitionType type : values()) {
            if (type.wireCode == wireCode) {
                return type;
            }
        }
        throw new IllegalArgumentException(
                "Unknown item inventory journal transition type");
    }
}
