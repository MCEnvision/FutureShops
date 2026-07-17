package com.enviouse.futureshops.server.escrow.redemption;

import com.enviouse.futureshops.server.escrow.custody.CustodyLot;
import com.enviouse.futureshops.server.escrow.custody.CustodyLotState;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutation;
import com.enviouse.futureshops.server.escrow.custody.ProtectedCurrencyProvenance;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintBatch;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintJournalEvent;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ProtectedCashRedemptionConservationValidator {
    private ProtectedCashRedemptionConservationValidator() {
    }

    public static void validateReservation(
            ProtectedCashRedemptionReservation reservation,
            ProtectedMintSavedData protectedMints
    ) {
        Objects.requireNonNull(reservation, "reservation");
        Objects.requireNonNull(protectedMints, "protectedMints");
        Map<UUID, BatchEvidence> evidenceByBatch = new HashMap<>();
        long custodyValue = 0L;
        for (CustodyMutation mutation : reservation.custodyReservations()) {
            CustodyLot lot = mutation.resultingLot();
            if (lot.state() != CustodyLotState.HELD
                    || lot.protectedProvenance().size() != 1) {
                throw new IllegalArgumentException(
                        "Protected cash reservation custody does not conserve");
            }
            custodyValue = Math.addExact(custodyValue, lot.units());
            ProtectedCurrencyProvenance provenance =
                    lot.protectedProvenance().get(0);
            BatchEvidence supplied = new BatchEvidence(
                    provenance.denominationMinorUnits(),
                    provenance.authorizedCount(), provenance.billCount(),
                    provenance.serverIdentityEvidence(),
                    provenance.checksumEvidence());
            evidenceByBatch.merge(provenance.mintId(), supplied,
                    BatchEvidence::merge);
        }
        if (custodyValue != reservation.amountMinorUnits()
                || evidenceByBatch.size()
                != reservation.mintReservations().size()) {
            throw new IllegalArgumentException(
                    "Protected cash reservation value does not conserve");
        }
        long mintValue = 0L;
        for (ProtectedMintJournalEvent event :
                reservation.mintReservations()) {
            UUID batchId = event.targetBatchId().orElseThrow();
            BatchEvidence evidence = evidenceByBatch.remove(batchId);
            ProtectedMintBatch batch = protectedMints.getBatch(batchId);
            if (evidence == null || batch == null
                    || event.quantity() != evidence.selectedCount()
                    || batch.denominationMinorUnits()
                    != evidence.denominationMinorUnits()
                    || batch.authorizedCount() != evidence.authorizedCount()
                    || !batch.serverIdentityEvidence().equals(
                    evidence.serverIdentityEvidence())
                    || !batch.checksumEvidence().equals(
                    evidence.checksumEvidence())) {
                throw new IllegalArgumentException(
                        "Protected cash reservation mint evidence is invalid");
            }
            mintValue = Math.addExact(mintValue, Math.multiplyExact(
                    evidence.denominationMinorUnits(),
                    (long) evidence.selectedCount()));
        }
        if (!evidenceByBatch.isEmpty()
                || mintValue != reservation.amountMinorUnits()) {
            throw new IllegalArgumentException(
                    "Protected cash reservation mint value does not conserve");
        }
    }

    public static void validateSettlement(
            ProtectedCashRedemptionSettlement settlement,
            ProtectedMintSavedData protectedMints
    ) {
        Objects.requireNonNull(settlement, "settlement");
        validateReservation(settlement.reservation(), protectedMints);
        long consumedValue = 0L;
        for (CustodyMutation mutation : settlement.custodyConsumptions()) {
            if (mutation.resultingLot().state()
                    != CustodyLotState.CONSUMED) {
                throw new IllegalArgumentException(
                        "Protected cash settlement custody is not consumed");
            }
            consumedValue = Math.addExact(consumedValue,
                    mutation.resultingLot().units());
        }
        Map<UUID, Integer> reservedByBatch = new HashMap<>();
        for (ProtectedMintJournalEvent event :
                settlement.reservation().mintReservations()) {
            reservedByBatch.put(event.targetBatchId().orElseThrow(),
                    event.quantity());
        }
        long committedValue = 0L;
        for (ProtectedMintJournalEvent event : settlement.mintCommits()) {
            UUID batchId = event.targetBatchId().orElseThrow();
            Integer reserved = reservedByBatch.remove(batchId);
            ProtectedMintBatch batch = protectedMints.getBatch(batchId);
            if (reserved == null || reserved != event.quantity()
                    || batch == null) {
                throw new IllegalArgumentException(
                        "Protected cash settlement mint commit is invalid");
            }
            committedValue = Math.addExact(committedValue,
                    Math.multiplyExact(batch.denominationMinorUnits(),
                            (long) event.quantity()));
        }
        long amount = settlement.amountMinorUnits();
        if (!reservedByBatch.isEmpty()
                || consumedValue != amount
                || committedValue != amount) {
            throw new IllegalArgumentException(
                    "Protected cash settlement value does not conserve");
        }
    }

    public static void validateCancellation(
            ProtectedCashRedemptionCancellation cancellation,
            ProtectedMintSavedData protectedMints
    ) {
        Objects.requireNonNull(cancellation, "cancellation");
        validateReservation(cancellation.reservation(), protectedMints);
        long releasedValue = 0L;
        for (CustodyMutation mutation : cancellation.custodyReleases()) {
            if (mutation.resultingLot().state()
                    != CustodyLotState.RELEASED) {
                throw new IllegalArgumentException(
                        "Protected cash cancellation custody is not released");
            }
            releasedValue = Math.addExact(releasedValue,
                    mutation.resultingLot().units());
        }
        Map<UUID, Integer> reservedByBatch = new HashMap<>();
        for (ProtectedMintJournalEvent event :
                cancellation.reservation().mintReservations()) {
            reservedByBatch.put(event.targetBatchId().orElseThrow(),
                    event.quantity());
        }
        long mintValue = 0L;
        for (ProtectedMintJournalEvent event :
                cancellation.mintReleases()) {
            UUID batchId = event.targetBatchId().orElseThrow();
            Integer reserved = reservedByBatch.remove(batchId);
            ProtectedMintBatch batch = protectedMints.getBatch(batchId);
            if (reserved == null || reserved != event.quantity()
                    || batch == null) {
                throw new IllegalArgumentException(
                        "Protected cash cancellation mint release is invalid");
            }
            mintValue = Math.addExact(mintValue, Math.multiplyExact(
                    batch.denominationMinorUnits(),
                    (long) event.quantity()));
        }
        long amount = cancellation.amountMinorUnits();
        if (!reservedByBatch.isEmpty()
                || releasedValue != amount
                || mintValue != amount) {
            throw new IllegalArgumentException(
                    "Protected cash cancellation value does not conserve");
        }
    }

    private record BatchEvidence(
            long denominationMinorUnits,
            int authorizedCount,
            int selectedCount,
            String serverIdentityEvidence,
            String checksumEvidence
    ) {
        private BatchEvidence {
            if (denominationMinorUnits <= 0L || authorizedCount <= 0
                    || selectedCount <= 0) {
                throw new IllegalArgumentException(
                        "Protected cash batch evidence is invalid");
            }
            Objects.requireNonNull(serverIdentityEvidence,
                    "serverIdentityEvidence");
            Objects.requireNonNull(checksumEvidence, "checksumEvidence");
        }

        private BatchEvidence merge(BatchEvidence other) {
            if (denominationMinorUnits != other.denominationMinorUnits
                    || authorizedCount != other.authorizedCount
                    || !serverIdentityEvidence.equals(
                    other.serverIdentityEvidence)
                    || !checksumEvidence.equals(other.checksumEvidence)) {
                throw new IllegalArgumentException(
                        "Protected cash batch evidence conflicts");
            }
            return new BatchEvidence(denominationMinorUnits, authorizedCount,
                    Math.addExact(selectedCount, other.selectedCount),
                    serverIdentityEvidence, checksumEvidence);
        }
    }
}
