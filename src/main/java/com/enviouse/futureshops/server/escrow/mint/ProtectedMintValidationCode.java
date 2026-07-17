package com.enviouse.futureshops.server.escrow.mint;

public enum ProtectedMintValidationCode {
    VALID,
    UNKNOWN_MINT,
    DENOMINATION_MISMATCH,
    SERVER_IDENTITY_MISMATCH,
    CHECKSUM_MISMATCH,
    NOT_AVAILABLE,
    ALREADY_SPENT,
    REFUNDED,
    QUARANTINED
}
