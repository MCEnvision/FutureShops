package com.enviouse.futureshops.server.escrow.item;

public enum ItemMatchMode {
    EXACT(1),
    ITEM_ONLY(2);

    private final int fingerprintCode;

    ItemMatchMode(int fingerprintCode) {
        this.fingerprintCode = fingerprintCode;
    }

    public int fingerprintCode() {
        return fingerprintCode;
    }

    public static ItemMatchMode fromFingerprintCode(int code) {
        for (ItemMatchMode value : values()) {
            if (value.fingerprintCode == code) {
                return value;
            }
        }
        throw new IllegalArgumentException(
                "Item match mode code is invalid");
    }
}
