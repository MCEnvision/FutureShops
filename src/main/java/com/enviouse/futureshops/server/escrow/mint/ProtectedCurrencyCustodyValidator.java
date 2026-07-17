package com.enviouse.futureshops.server.escrow.mint;

import com.enviouse.futureshops.server.escrow.custody.CustodyAssetType;
import com.enviouse.futureshops.server.escrow.custody.CustodyLot;
import com.enviouse.futureshops.server.escrow.custody.ProtectedCurrencyProvenance;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ProtectedCurrencyCustodyValidator {
    private ProtectedCurrencyCustodyValidator() {
    }

    public static ProtectedCustodyValidationResult validate(
            CustodyLot lot,
            ProtectedMintSavedData protectedMints,
            Optional<UUID> expectedReservationTransactionId
    ) {
        Objects.requireNonNull(lot, "lot");
        Objects.requireNonNull(protectedMints, "protectedMints");
        Objects.requireNonNull(expectedReservationTransactionId,
                "expectedReservationTransactionId");
        if (lot.assetType() != CustodyAssetType.PROTECTED_PHYSICAL_CURRENCY) {
            throw new IllegalArgumentException("Protected mint validation requires protected currency custody");
        }
        Map<UUID, ProtectedMintValidationCode> results = new LinkedHashMap<>();
        int billCount = 0;
        long minorUnits = 0L;
        for (ProtectedCurrencyProvenance provenance : lot.protectedProvenance()) {
            ProtectedMintValidationResult validation = protectedMints.validate(
                    provenance.mintId(), provenance.denominationMinorUnits(),
                    provenance.authorizedCount(), provenance.serverIdentityEvidence(),
                    provenance.checksumEvidence(), provenance.billCount(),
                    expectedReservationTransactionId);
            if (results.put(provenance.mintId(), validation.code()) != null) {
                throw new IllegalArgumentException("Protected custody repeats a mint batch");
            }
            if (validation.valid()) {
                billCount = Math.addExact(billCount, validation.validatedQuantity());
                minorUnits = Math.addExact(minorUnits, Math.multiplyExact(
                        provenance.denominationMinorUnits(),
                        (long) validation.validatedQuantity()));
            }
        }
        boolean valid = results.values().stream()
                .allMatch(code -> code == ProtectedMintValidationCode.VALID)
                && minorUnits == lot.units();
        if (!valid) {
            billCount = 0;
            minorUnits = 0L;
        }
        return new ProtectedCustodyValidationResult(valid, billCount, minorUnits, results);
    }
}
