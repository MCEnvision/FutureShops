package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.custody.CustodyLotState;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutation;
import com.enviouse.futureshops.server.escrow.custody.CustodyOperation;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionSettlement.InventoryMutationReceipt;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ForeignCashDepositSettlement(
        ForeignCashDepositReservation reservation,
        EscrowTransaction completedTransaction,
        InventoryMutationReceipt inventoryMutation,
        List<CustodyMutation> custodyConsumptions,
        long walletBalanceBeforeMinorUnits,
        long walletReservedBeforeMinorUnits,
        Optional<EscrowClaim> overflowClaim,
        LedgerTransaction ledgerTransaction
) {
    public static final String LEDGER_REASON = "FOREIGN_CASH_DEPOSIT";
    public static final String CURRENCY_SINK_OWNER =
            "foreign_currency_sink";

    public ForeignCashDepositSettlement {
        Objects.requireNonNull(reservation, "reservation");
        Objects.requireNonNull(completedTransaction,
                "completedTransaction");
        Objects.requireNonNull(inventoryMutation, "inventoryMutation");
        Objects.requireNonNull(custodyConsumptions,
                "custodyConsumptions");
        overflowClaim = Objects.requireNonNull(
                overflowClaim, "overflowClaim");
        Objects.requireNonNull(ledgerTransaction, "ledgerTransaction");
        if (walletReservedBeforeMinorUnits < 0L
                || completedTransaction.state() != EscrowState.COMPLETED
                || !completedTransaction.transactionId().equals(
                reservation.heldTransaction().transactionId())
                || !inventoryMutation.playerId().equals(
                reservation.playerId())
                || !inventoryMutation.transactionId().equals(
                reservation.transactionId())
                || !inventoryMutation.reservationId().equals(
                reservation.reservationId())
                || !java.security.MessageDigest.isEqual(
                inventoryMutation.beforeInventoryHash(),
                reservation.inventoryBeforeHash())) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit settlement identity is invalid");
        }
        Map<UUID, CustodyMutation> byLot = new HashMap<>();
        for (CustodyMutation mutation : custodyConsumptions) {
            if (byLot.put(mutation.resultingLot().lotId(), mutation)
                    != null) {
                throw new IllegalArgumentException(
                        "Foreign cash deposit consumption is duplicated");
            }
        }
        List<CustodyMutation> ordered = new ArrayList<>();
        for (CustodyMutation held : reservation.custodyReservations()) {
            CustodyMutation consumed = byLot.remove(
                    held.resultingLot().lotId());
            if (consumed == null
                    || consumed.resultingLot().state()
                    != CustodyLotState.CONSUMED
                    || consumed.receipt().operation()
                    != CustodyOperation.CONSUME
                    || !java.security.MessageDigest.isEqual(
                    consumed.resultingLot().assetFingerprint(),
                    held.resultingLot().assetFingerprint())) {
                throw new IllegalArgumentException(
                        "Foreign cash deposit consumption is invalid");
            }
            ordered.add(consumed);
        }
        if (!byLot.isEmpty()) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit consumption has an extra lot");
        }
        custodyConsumptions = List.copyOf(ordered);
        requireClaimAndLedger(reservation, walletBalanceBeforeMinorUnits,
                walletReservedBeforeMinorUnits, overflowClaim,
                ledgerTransaction);
    }

    public UUID transactionId() {
        return reservation.transactionId();
    }

    public long amountMinorUnits() {
        return reservation.amountMinorUnits();
    }

    public long walletCreditMinorUnits() {
        return ledgerTransaction.legs().stream()
                .filter(leg -> leg.account().equals(
                        reservation.destinationAccount()))
                .mapToLong(LedgerLeg::deltaMinor).sum();
    }

    public long overflowClaimMinorUnits() {
        return overflowClaim.map(EscrowClaim::originalUnits).orElse(0L);
    }

    public static String inventoryMutationRequestKey(UUID transactionId) {
        return "foreign.cash." + transactionId + ".inventory.remove";
    }

    public static String custodyConsumeRequestKey(UUID transactionId,
                                                   UUID lotId) {
        return "foreign.cash." + transactionId + ".lot." + lotId
                + ".consume";
    }

    public static UUID overflowClaimId(
            ForeignCashDepositReservation reservation
    ) {
        String material = "futureshops foreign cash overflow claim v1 "
                + reservation.transactionId() + " "
                + reservation.reservationId();
        return UUID.nameUUIDFromBytes(material.getBytes(
                StandardCharsets.UTF_8));
    }

    public static String overflowClaimSourceKey(
            ForeignCashDepositReservation reservation
    ) {
        return "foreign.cash." + reservation.transactionId()
                + ".overflow";
    }

    public static String ledgerIdempotencyKey(
            ForeignCashDepositReservation reservation
    ) {
        return "foreign.cash." + reservation.transactionId()
                + ".ledger";
    }

    private static void requireClaimAndLedger(
            ForeignCashDepositReservation reservation,
            long walletBefore,
            long reservedBefore,
            Optional<EscrowClaim> overflow,
            LedgerTransaction ledger
    ) {
        long amount = reservation.amountMinorUnits();
        long walletCredit = expectedWalletCredit(reservation,
                walletBefore, reservedBefore);
        long overflowCredit = Math.subtractExact(amount, walletCredit);
        if (!ledger.transactionId().equals(reservation.transactionId())
                || !ledger.idempotencyKey().equals(
                ledgerIdempotencyKey(reservation))
                || !ledger.reason().equals(LEDGER_REASON)) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit ledger identity is invalid");
        }
        long source = ledger.legs().stream()
                .filter(leg -> leg.account().type()
                        == LedgerAccountType.FOREIGN_CURRENCY_SOURCE)
                .mapToLong(LedgerLeg::deltaMinor).sum();
        long wallet = ledger.legs().stream()
                .filter(leg -> leg.account().equals(
                        reservation.destinationAccount()))
                .mapToLong(LedgerLeg::deltaMinor).sum();
        long claims = ledger.legs().stream()
                .filter(leg -> leg.account().type()
                        == LedgerAccountType.PLAYER_CLAIM)
                .mapToLong(LedgerLeg::deltaMinor).sum();
        if (source != Math.negateExact(amount)
                || wallet != walletCredit || claims != overflowCredit) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit ledger does not balance");
        }
        if (overflowCredit == 0L && overflow.isPresent()) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit overflow claim is unexpected");
        }
        if (overflowCredit > 0L) {
            EscrowClaim claim = overflow.orElseThrow(() ->
                    new IllegalArgumentException(
                            "Foreign cash deposit overflow claim is missing"));
            if (!claim.claimId().equals(overflowClaimId(reservation))
                    || !claim.transactionId().equals(
                    reservation.transactionId())
                    || !claim.ownerId().equals(reservation.playerId())
                    || claim.kind() != ClaimKind.MONEY
                    || claim.status() != ClaimStatus.PENDING
                    || claim.originalUnits() != overflowCredit
                    || claim.remainingUnits() != overflowCredit
                    || claim.payload().length != 0) {
                throw new IllegalArgumentException(
                        "Foreign cash deposit overflow claim is invalid");
            }
        }
    }

    static long expectedWalletCredit(
            ForeignCashDepositReservation reservation,
            long walletBefore,
            long reservedBefore
    ) {
        if (reservedBefore < 0L) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit reserved balance is invalid");
        }
        java.math.BigInteger capacity = java.math.BigInteger.valueOf(
                        reservation.walletBalanceLimitMinorUnits())
                .subtract(java.math.BigInteger.valueOf(walletBefore))
                .subtract(java.math.BigInteger.valueOf(reservedBefore));
        if (capacity.signum() <= 0) {
            return 0L;
        }
        java.math.BigInteger amount = java.math.BigInteger.valueOf(
                reservation.amountMinorUnits());
        return capacity.compareTo(amount) >= 0
                ? reservation.amountMinorUnits()
                : capacity.longValueExact();
    }
}
