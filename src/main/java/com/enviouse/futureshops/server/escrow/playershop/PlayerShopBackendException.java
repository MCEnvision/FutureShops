package com.enviouse.futureshops.server.escrow.playershop;

import java.util.Objects;

public final class PlayerShopBackendException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final Kind kind;

    public PlayerShopBackendException(Kind kind, String message) {
        super(PlayerShopBinarySupport.requireString(message,
                PlayerShopEscrowConstants.MAX_TEXT_LENGTH,
                "backend failure message"));
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public Kind kind() {
        return kind;
    }

    public enum Kind {
        CONFLICT,
        REJECTED,
        RECOVERY_REQUIRED,
        QUARANTINED
    }
}
