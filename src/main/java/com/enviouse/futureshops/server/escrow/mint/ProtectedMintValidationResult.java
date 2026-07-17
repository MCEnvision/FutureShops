package com.enviouse.futureshops.server.escrow.mint;

import java.util.Objects;
import java.util.Optional;

public record ProtectedMintValidationResult(ProtectedMintValidationCode code,
                                            Optional<ProtectedMintBatch> batch,
                                            int validatedQuantity) {
    public ProtectedMintValidationResult {
        Objects.requireNonNull(code, "code");
        batch = Objects.requireNonNull(batch, "batch");
        if (validatedQuantity < 0
                || (code == ProtectedMintValidationCode.UNKNOWN_MINT) == batch.isPresent()
                || code == ProtectedMintValidationCode.VALID && validatedQuantity <= 0
                || code != ProtectedMintValidationCode.VALID && validatedQuantity != 0) {
            throw new IllegalArgumentException("Protected mint validation result is inconsistent");
        }
    }

    public boolean valid() {
        return code == ProtectedMintValidationCode.VALID;
    }
}
