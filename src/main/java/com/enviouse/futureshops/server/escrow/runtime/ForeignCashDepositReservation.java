package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.custody.CustodyAssetType;
import com.enviouse.futureshops.server.escrow.custody.CustodyLotState;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutation;
import com.enviouse.futureshops.server.escrow.custody.CustodyOperation;
import com.enviouse.futureshops.server.escrow.custody.CustodyProtectionTier;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLotType;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.CashDepositMode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ForeignCashDepositReservation(
        UUID reservationId,
        UUID requestId,
        UUID playerId,
        LedgerAccountId destinationAccount,
        long walletBalanceLimitMinorUnits,
        CashDepositMode depositMode,
        byte[] inventoryBeforeHash,
        ForeignCashDepositPlan plan,
        EscrowTransaction heldTransaction,
        List<CustodyMutation> custodyReservations
) {
    public static final String CURRENCY_ID = "futureshops:wallet";

    public ForeignCashDepositReservation(
            UUID reservationId,
            UUID requestId,
            UUID playerId,
            LedgerAccountId destinationAccount,
            long walletBalanceLimitMinorUnits,
            byte[] inventoryBeforeHash,
            ForeignCashDepositPlan plan,
            EscrowTransaction heldTransaction,
            List<CustodyMutation> custodyReservations
    ) {
        this(reservationId, requestId, playerId, destinationAccount,
                walletBalanceLimitMinorUnits,
                CashDepositMode.PUBLIC_WALLET, inventoryBeforeHash, plan,
                heldTransaction, custodyReservations);
    }

    public ForeignCashDepositReservation {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(destinationAccount, "destinationAccount");
        Objects.requireNonNull(depositMode, "depositMode");
        inventoryBeforeHash = requireHash(inventoryBeforeHash,
                "inventoryBeforeHash");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(heldTransaction, "heldTransaction");
        Objects.requireNonNull(custodyReservations,
                "custodyReservations");
        if (destinationAccount.type() != LedgerAccountType.PLAYER_WALLET
                || !destinationAccount.ownerKey().equals(
                playerId.toString())
                || walletBalanceLimitMinorUnits < 0L
                || heldTransaction.operation()
                != EscrowOperation.CURRENCY_DEPOSIT
                || heldTransaction.state() != EscrowState.HELD) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit reservation identity is invalid");
        }
        UUID expected = reservationId(requestId, playerId,
                destinationAccount, walletBalanceLimitMinorUnits,
                depositMode, inventoryBeforeHash, plan, heldTransaction);
        boolean legacyPublicIdentity = depositMode
                == CashDepositMode.PUBLIC_WALLET
                && reservationId.equals(legacyReservationId(requestId,
                playerId, destinationAccount,
                walletBalanceLimitMinorUnits, inventoryBeforeHash, plan,
                heldTransaction));
        if (!reservationId.equals(expected) && !legacyPublicIdentity) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit reservation id is invalid");
        }
        Map<UUID, CustodyMutation> byLot = new HashMap<>();
        for (CustodyMutation mutation : custodyReservations) {
            Objects.requireNonNull(mutation, "custodyReservation");
            if (byLot.put(mutation.resultingLot().lotId(), mutation)
                    != null) {
                throw new IllegalArgumentException(
                        "Foreign cash deposit custody is duplicated");
            }
        }
        List<CustodyMutation> ordered = new ArrayList<>();
        for (ForeignCashDepositPlan.Portion portion : plan.portions()) {
            UUID lotId = custodyLotId(transactionId(heldTransaction),
                    portion);
            CustodyMutation mutation = byLot.remove(lotId);
            if (mutation == null) {
                throw new IllegalArgumentException(
                        "Foreign cash deposit custody is incomplete");
            }
            requireCustody(mutation, playerId, plan, portion,
                    transactionId(heldTransaction));
            ordered.add(mutation);
        }
        if (!byLot.isEmpty()
                || heldTransaction.assetLots().size() != ordered.size()
                || heldTransaction.assetLots().stream().anyMatch(asset ->
                asset.type() != EscrowAssetLotType
                        .FOREIGN_PHYSICAL_CURRENCY
                        || !depositMode.name().equals(
                        asset.attributes().get("deposit_mode"))
                        && (!legacyPublicIdentity
                        || asset.attributes().containsKey(
                        "deposit_mode")))) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit escrow assets are invalid");
        }
        custodyReservations = List.copyOf(ordered);
    }

    public UUID transactionId() {
        return heldTransaction.transactionId().value();
    }

    public long amountMinorUnits() {
        return plan.amountMinorUnits();
    }

    public static UUID reservationId(
            UUID requestId,
            UUID playerId,
            LedgerAccountId destination,
            long walletLimit,
            CashDepositMode depositMode,
            byte[] inventoryHash,
            ForeignCashDepositPlan plan,
            EscrowTransaction held
    ) {
        Objects.requireNonNull(depositMode, "depositMode");
        String material = "futureshops foreign cash deposit reservation v2 "
                + requestId + " " + playerId + " "
                + destination.ownerKey() + " " + walletLimit + " "
                + depositMode + " "
                + java.util.HexFormat.of().formatHex(
                requireHash(inventoryHash, "inventoryHash")) + " "
                + java.util.HexFormat.of().formatHex(
                sha256(ForeignCashDepositCodec
                        .encodeLegacyPlanIdentity(plan))) + " "
                + held.transactionId().value();
        return UUID.nameUUIDFromBytes(material.getBytes(
                StandardCharsets.UTF_8));
    }

    static UUID legacyReservationId(
            UUID requestId,
            UUID playerId,
            LedgerAccountId destination,
            long walletLimit,
            byte[] inventoryHash,
            ForeignCashDepositPlan plan,
            EscrowTransaction held
    ) {
        String material = "futureshops foreign cash deposit reservation v1 "
                + requestId + " " + playerId + " "
                + destination.ownerKey() + " " + walletLimit + " "
                + java.util.HexFormat.of().formatHex(
                requireHash(inventoryHash, "inventoryHash")) + " "
                + java.util.HexFormat.of().formatHex(
                sha256(ForeignCashDepositCodec.encodePlan(plan))) + " "
                + held.transactionId().value();
        return UUID.nameUUIDFromBytes(material.getBytes(
                StandardCharsets.UTF_8));
    }

    public static UUID reservationId(
            UUID requestId,
            UUID playerId,
            LedgerAccountId destination,
            long walletLimit,
            byte[] inventoryHash,
            ForeignCashDepositPlan plan,
            EscrowTransaction held
    ) {
        return reservationId(requestId, playerId, destination, walletLimit,
                CashDepositMode.PUBLIC_WALLET, inventoryHash, plan, held);
    }

    public static UUID custodyLotId(
            UUID transactionId,
            ForeignCashDepositPlan.Portion portion
    ) {
        String material = "futureshops foreign cash deposit lot v1 "
                + transactionId + " " + portion.slot().container() + " "
                + portion.slot().index() + " " + portion.registryId();
        return UUID.nameUUIDFromBytes(material.getBytes(
                StandardCharsets.UTF_8));
    }

    public static String custodyReserveRequestKey(
            UUID transactionId,
            UUID lotId
    ) {
        return "foreign.cash." + transactionId + ".lot." + lotId
                + ".reserve";
    }

    private static void requireCustody(
            CustodyMutation mutation,
            UUID playerId,
            ForeignCashDepositPlan plan,
            ForeignCashDepositPlan.Portion portion,
            UUID transactionId
    ) {
        var lot = mutation.resultingLot();
        if (!lot.transactionId().equals(transactionId)
                || lot.state() != CustodyLotState.HELD
                || lot.assetType()
                != CustodyAssetType.FOREIGN_PHYSICAL_CURRENCY
                || lot.protectionTier()
                != CustodyProtectionTier.UNPROTECTED_FOREIGN
                || !lot.currencyProvider().equals(plan.providerId())
                || lot.units() != portion.valueMinorUnits()
                || mutation.receipt().operation()
                != CustodyOperation.RESERVE
                || lot.itemSnapshots().size() != 1
                || !lot.protectedProvenance().isEmpty()
                || !lot.holdEvidence().source().ownerKey().equals(
                playerId.toString())) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit custody is invalid");
        }
        var snapshot = lot.itemSnapshots().get(0);
        if (!snapshot.registryId().equals(portion.registryId())
                || snapshot.count() != portion.selectedCount()
                || !Arrays.equals(snapshot.serializedNbt(),
                portion.exactStackSnapshot())) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit custody snapshot is invalid");
        }
    }

    static byte[] requireHash(byte[] value, String label) {
        byte[] copy = Objects.requireNonNull(value, label).clone();
        if (copy.length != 32) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit hash is invalid");
        }
        return copy;
    }

    static byte[] sha256(byte[] value) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Foreign cash deposit digest is unavailable", exception);
        }
    }

    private static UUID transactionId(EscrowTransaction transaction) {
        return transaction.transactionId().value();
    }

    @Override
    public byte[] inventoryBeforeHash() {
        return inventoryBeforeHash.clone();
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ForeignCashDepositReservation other
                && reservationId.equals(other.reservationId)
                && requestId.equals(other.requestId)
                && playerId.equals(other.playerId)
                && destinationAccount.equals(other.destinationAccount)
                && walletBalanceLimitMinorUnits
                == other.walletBalanceLimitMinorUnits
                && depositMode == other.depositMode
                && Arrays.equals(inventoryBeforeHash,
                other.inventoryBeforeHash)
                && plan.equals(other.plan)
                && heldTransaction.equals(other.heldTransaction)
                && custodyReservations.equals(other.custodyReservations);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(reservationId, requestId, playerId,
                destinationAccount, walletBalanceLimitMinorUnits,
                depositMode, plan,
                heldTransaction, custodyReservations)
                + Arrays.hashCode(inventoryBeforeHash);
    }
}
