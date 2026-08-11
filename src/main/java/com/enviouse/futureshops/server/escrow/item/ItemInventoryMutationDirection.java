package com.enviouse.futureshops.server.escrow.item;

public enum ItemInventoryMutationDirection {
    INSERT(1),
    EXTRACT(2);

    private final int wireCode;

    ItemInventoryMutationDirection(int wireCode) {
        this.wireCode = wireCode;
    }

    public int wireCode() {
        return wireCode;
    }

    public static ItemInventoryMutationDirection fromWireCode(int code) {
        for (ItemInventoryMutationDirection value : values()) {
            if (value.wireCode == code) {
                return value;
            }
        }
        throw new IllegalArgumentException(
                "Item inventory mutation direction code is invalid");
    }
}
