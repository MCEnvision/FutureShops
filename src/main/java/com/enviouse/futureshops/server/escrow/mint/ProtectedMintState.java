package com.enviouse.futureshops.server.escrow.mint;

public enum ProtectedMintState {
    AUTHORIZED(1),
    AVAILABLE(2),
    RESERVED(3),
    SPENT(4),
    REFUNDED(5),
    QUARANTINED(6);

    private final int wireId;

    ProtectedMintState(int wireId) {
        this.wireId = wireId;
    }

    public int wireId() {
        return wireId;
    }

    public boolean canTransitionTo(ProtectedMintState target) {
        return switch (this) {
            case AUTHORIZED -> target == AVAILABLE || target == QUARANTINED;
            case AVAILABLE -> target == RESERVED || target == QUARANTINED;
            case RESERVED -> target == AVAILABLE || target == SPENT
                    || target == REFUNDED || target == QUARANTINED;
            case SPENT -> target == REFUNDED;
            case REFUNDED, QUARANTINED -> false;
        };
    }

    public boolean carriesOutstandingLiability() {
        return this == AUTHORIZED || this == AVAILABLE || this == RESERVED;
    }

    public static ProtectedMintState fromWireId(int wireId) {
        for (ProtectedMintState value : values()) {
            if (value.wireId == wireId) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown protected mint state");
    }
}
