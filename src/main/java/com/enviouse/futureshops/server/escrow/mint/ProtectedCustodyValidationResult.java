package com.enviouse.futureshops.server.escrow.mint;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ProtectedCustodyValidationResult(
        boolean valid,
        int validatedBillCount,
        long validatedMinorUnits,
        Map<UUID, ProtectedMintValidationCode> resultsByBatch
) {
    public ProtectedCustodyValidationResult {
        resultsByBatch = Map.copyOf(Objects.requireNonNull(resultsByBatch, "resultsByBatch"));
        if (validatedBillCount < 0 || validatedMinorUnits < 0L
                || valid != resultsByBatch.values().stream()
                .allMatch(code -> code == ProtectedMintValidationCode.VALID)) {
            throw new IllegalArgumentException("Protected custody validation result is invalid");
        }
    }
}
