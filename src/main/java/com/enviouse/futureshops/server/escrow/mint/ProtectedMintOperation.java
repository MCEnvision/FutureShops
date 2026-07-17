package com.enviouse.futureshops.server.escrow.mint;

public enum ProtectedMintOperation {
    AUTHORIZE(1),
    MATERIALIZE(2),
    RESERVE(3),
    COMMIT(4),
    REFUND(5),
    QUARANTINE(6),
    ISSUE(7),
    RELEASE(8);

    private final int wireId;

    ProtectedMintOperation(int wireId) {
        this.wireId = wireId;
    }

    public int wireId() {
        return wireId;
    }

    public static ProtectedMintOperation fromWireId(int wireId) {
        for (ProtectedMintOperation value : values()) {
            if (value.wireId == wireId) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown protected mint operation");
    }
}
