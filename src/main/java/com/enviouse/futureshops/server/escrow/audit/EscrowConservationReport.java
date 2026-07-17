package com.enviouse.futureshops.server.escrow.audit;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

public record EscrowConservationReport(boolean conserved,
                                       OptionalLong ledgerNetMinorUnits,
                                       List<EscrowLiabilityComparison> liabilityComparisons,
                                       Map<ProtectedReservationKey,
                                               ProtectedReservationComparison>
                                               protectedReservations,
                                       boolean custodyLocallyConserved,
                                       boolean protectedMintLocallyConserved,
                                       List<EscrowUnverifiedCategory> unverifiedCategories,
                                       List<EscrowConservationViolation> violations,
                                       String deterministicFingerprint) {
    public EscrowConservationReport {
        ledgerNetMinorUnits = Objects.requireNonNull(
                ledgerNetMinorUnits, "ledgerNetMinorUnits");
        liabilityComparisons = List.copyOf(Objects.requireNonNull(
                liabilityComparisons, "liabilityComparisons"));
        protectedReservations = Map.copyOf(Objects.requireNonNull(
                protectedReservations, "protectedReservations"));
        unverifiedCategories = List.copyOf(Objects.requireNonNull(
                unverifiedCategories, "unverifiedCategories"));
        violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
        deterministicFingerprint = Objects.requireNonNull(
                deterministicFingerprint, "deterministicFingerprint");
        if (conserved != violations.isEmpty()
                || !deterministicFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Escrow conservation report is invalid");
        }
    }
}
