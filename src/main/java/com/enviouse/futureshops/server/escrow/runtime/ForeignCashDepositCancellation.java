package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.custody.CustodyLotState;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutation;
import com.enviouse.futureshops.server.escrow.custody.CustodyOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionCancellation.InventoryNoMutationProof;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ForeignCashDepositCancellation(
        ForeignCashDepositReservation reservation,
        EscrowTransaction refundedTransaction,
        InventoryNoMutationProof inventoryProof,
        List<CustodyMutation> custodyReleases
) {
    public ForeignCashDepositCancellation {
        Objects.requireNonNull(reservation, "reservation");
        Objects.requireNonNull(refundedTransaction,
                "refundedTransaction");
        Objects.requireNonNull(inventoryProof, "inventoryProof");
        Objects.requireNonNull(custodyReleases, "custodyReleases");
        if (refundedTransaction.state() != EscrowState.REFUNDED
                || !refundedTransaction.transactionId().equals(
                reservation.heldTransaction().transactionId())
                || !inventoryProof.playerId().equals(
                reservation.playerId())
                || !inventoryProof.transactionId().equals(
                reservation.transactionId())
                || !inventoryProof.reservationId().equals(
                reservation.reservationId())
                || !java.security.MessageDigest.isEqual(
                inventoryProof.inventoryHash(),
                reservation.inventoryBeforeHash())) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit cancellation identity is invalid");
        }
        Map<UUID, CustodyMutation> byLot = new HashMap<>();
        for (CustodyMutation mutation : custodyReleases) {
            if (byLot.put(mutation.resultingLot().lotId(), mutation)
                    != null) {
                throw new IllegalArgumentException(
                        "Foreign cash deposit release is duplicated");
            }
        }
        List<CustodyMutation> ordered = new ArrayList<>();
        for (CustodyMutation held : reservation.custodyReservations()) {
            CustodyMutation released = byLot.remove(
                    held.resultingLot().lotId());
            if (released == null
                    || released.resultingLot().state()
                    != CustodyLotState.RELEASED
                    || released.receipt().operation()
                    != CustodyOperation.RELEASE
                    || !java.security.MessageDigest.isEqual(
                    released.resultingLot().assetFingerprint(),
                    held.resultingLot().assetFingerprint())) {
                throw new IllegalArgumentException(
                        "Foreign cash deposit release is invalid");
            }
            ordered.add(released);
        }
        if (!byLot.isEmpty()) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit release has an extra lot");
        }
        custodyReleases = List.copyOf(ordered);
    }

    public UUID transactionId() {
        return reservation.transactionId();
    }

    public static String inventoryProofRequestKey(UUID transactionId) {
        return "foreign.cash." + transactionId + ".inventory.unchanged";
    }

    public static String custodyReleaseRequestKey(UUID transactionId,
                                                   UUID lotId) {
        return "foreign.cash." + transactionId + ".lot." + lotId
                + ".release";
    }
}
