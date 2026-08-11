package com.enviouse.futureshops.server.escrow.redemption;

import com.enviouse.futureshops.money.InternalBillInventoryPlanner;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterCapability;
import com.enviouse.futureshops.server.escrow.custody.CustodyAssetType;
import com.enviouse.futureshops.server.escrow.custody.CustodyEndpointEvidence;
import com.enviouse.futureshops.server.escrow.custody.CustodyItemSnapshot;
import com.enviouse.futureshops.server.escrow.custody.CustodyLot;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutation;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutationCodec;
import com.enviouse.futureshops.server.escrow.custody.CustodyOperation;
import com.enviouse.futureshops.server.escrow.custody.CustodyProtectionTier;
import com.enviouse.futureshops.server.escrow.custody.CustodyTransferEvidence;
import com.enviouse.futureshops.server.escrow.custody.ProtectedCurrencyProvenance;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintJournalEvent;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLot;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLotType;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipant;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipantRole;
import com.enviouse.futureshops.server.escrow.model.EscrowParty;
import com.enviouse.futureshops.server.escrow.model.EscrowProtectionLevel;
import com.enviouse.futureshops.server.escrow.model.EscrowRequestKey;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import com.enviouse.futureshops.server.escrow.model.MoneyAmount;
import com.enviouse.futureshops.server.escrow.model.CashDepositMode;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ProtectedCashRedemptionFactory {
    private ProtectedCashRedemptionFactory() {
    }

    public static ProtectedCashRedemptionReservation walletReservation(
            UUID playerId,
            UUID transactionId,
            String requestKey,
            long configRevision,
            long walletBalanceLimitMinorUnits,
            InternalBillInventoryPlanner.ExactPlan plan,
            ProtectedCashInventoryState beforeInventory,
            CashDepositMode depositMode,
            Instant now
    ) {
        LedgerAccountId destination = new LedgerAccountId(
                LedgerAccountType.PLAYER_WALLET, playerId.toString());
        java.util.Objects.requireNonNull(depositMode, "depositMode");
        ProtectedCashRedemptionSupport.PlanFacts facts =
                ProtectedCashRedemptionSupport.analyze(plan);
        List<CustodyMutation> custody = new ArrayList<>();
        for (InternalBillInventoryPlanner.Portion portion : plan.portions()) {
            UUID lotId = ProtectedCashRedemptionReservation.custodyLotId(
                    transactionId, portion);
            String reserveKey = ProtectedCashRedemptionReservation
                    .custodyReserveRequestKey(transactionId, destination, lotId);
            byte[] snapshot = portion.exactStackSnapshot();
            CustodyEndpointEvidence source = CustodyEndpointEvidence.captured(
                    "player_inventory",
                    CustodyAdapterCapability.RECONCILABLE,
                    playerId.toString(), location(portion), snapshot, snapshot,
                    "protected cash reserve " + lotId);
            CustodyEndpointEvidence vault = CustodyEndpointEvidence.captured(
                    "escrow_vault",
                    CustodyAdapterCapability.TRANSACTIONAL_PROTECTED,
                    transactionId.toString(), "vault." + lotId,
                    new byte[0], snapshot,
                    "protected cash reserve " + lotId);
            ProtectedCashRedemptionSupport.BatchFacts batch = facts.batches()
                    .get(UUID.fromString(portion.mintId()));
            CustodyLot lot = CustodyLot.held(lotId, transactionId, reserveKey,
                    CustodyAssetType.PROTECTED_PHYSICAL_CURRENCY,
                    CustodyProtectionTier.PROTECTED,
                    portion.valueMinorUnits(),
                    CustodyLot.BUILT_IN_CURRENCY_PROVIDER,
                    List.of(CustodyItemSnapshot.capture("futureshops:money",
                            portion.selectedCount(), snapshot)),
                    List.of(new ProtectedCurrencyProvenance(batch.batchId(),
                            batch.denominationMinorUnits(),
                            batch.authorizedCount(), portion.selectedCount(),
                            batch.serverIdentityEvidence(),
                            batch.checksumEvidence())),
                    new CustodyTransferEvidence(source, vault), now);
            custody.add(CustodyMutation.reserve(lot));
        }
        EscrowParty player = EscrowParty.player(playerId);
        EscrowParty authority = EscrowParty.system("protected_currency");
        Set<EscrowParticipant> participants = Set.of(
                new EscrowParticipant(player, Set.of(
                        EscrowParticipantRole.INITIATOR,
                        EscrowParticipantRole.PAYER,
                        EscrowParticipantRole.BUYER,
                        EscrowParticipantRole.BENEFICIARY)),
                new EscrowParticipant(authority, Set.of(
                        EscrowParticipantRole.BENEFICIARY,
                        EscrowParticipantRole.CUSTODIAN)));
        List<EscrowAssetLot> assets = new ArrayList<>();
        for (int index = 0; index < plan.portions().size(); index++) {
            InternalBillInventoryPlanner.Portion portion =
                    plan.portions().get(index);
            CustodyMutation mutation = custody.get(index);
            assets.add(new EscrowAssetLot(mutation.resultingLot().lotId(),
                    EscrowAssetLotType.PROTECTED_PHYSICAL_CURRENCY,
                    EscrowProtectionLevel.PROTECTED, player, authority,
                    portion.selectedCount(), Optional.of(new MoneyAmount(
                    ProtectedCashRedemptionReservation.CURRENCY_ID,
                    portion.valueMinorUnits())),
                    CustodyMutationCodec.encode(mutation),
                    ProtectedCashRedemptionReservation.assetAttributes(
                            portion, destination,
                            walletBalanceLimitMinorUnits,
                            depositMode,
                            beforeInventory.hash())));
        }
        EscrowTransaction held = EscrowTransaction.create(
                        new EscrowTransactionId(transactionId), Optional.empty(),
                        new EscrowRequestKey(requestKey),
                        EscrowOperation.CURRENCY_DEPOSIT, participants, assets,
                        now, configRevision, Optional.empty())
                .transitionTo(EscrowState.VALIDATED, now)
                .transitionTo(EscrowState.HOLDING, now)
                .transitionTo(EscrowState.HELD, now);
        List<UUID> batchIds = new ArrayList<>(facts.batches().keySet());
        batchIds.sort(Comparator.comparing(UUID::toString));
        List<ProtectedMintJournalEvent> mints = batchIds.stream()
                .map(batchId -> ProtectedMintJournalEvent.reserve(
                        transactionId, batchId,
                        ProtectedCashRedemptionReservation
                                .mintReserveRequestKey(transactionId,
                                        destination, batchId),
                        facts.batches().get(batchId).selectedCount(), now))
                .toList();
        UUID reservationId = ProtectedCashRedemptionReservation.reservationId(
                playerId, destination, walletBalanceLimitMinorUnits,
                depositMode, beforeInventory.hash(), held, plan);
        return new ProtectedCashRedemptionReservation(reservationId, playerId,
                destination, walletBalanceLimitMinorUnits,
                depositMode, beforeInventory.hash(), plan, held, custody,
                mints);
    }

    public static ProtectedCashRedemptionReservation walletReservation(
            UUID playerId,
            UUID transactionId,
            String requestKey,
            long configRevision,
            long walletBalanceLimitMinorUnits,
            InternalBillInventoryPlanner.ExactPlan plan,
            ProtectedCashInventoryState beforeInventory,
            Instant now
    ) {
        return walletReservation(playerId, transactionId, requestKey,
                configRevision, walletBalanceLimitMinorUnits, plan,
                beforeInventory, CashDepositMode.PUBLIC_WALLET, now);
    }

    public static ProtectedCashRedemptionSettlement settlement(
            ProtectedCashRedemptionReservation reservation,
            ProtectedCashRedemptionSettlement.InventoryMutationReceipt receipt,
            long walletBalanceBeforeMinorUnits,
            long walletReservedBeforeMinorUnits,
            Instant now
    ) {
        EscrowTransaction completed = reservation.heldTransaction()
                .transitionTo(EscrowState.COMMIT_DECIDED, now)
                .transitionTo(EscrowState.COMMITTED, now)
                .transitionTo(EscrowState.CLAIMS_CREATED, now)
                .transitionTo(EscrowState.COMPLETED, now);
        List<CustodyMutation> consumptions = reservation
                .custodyReservations().stream().map(reserve -> {
                    CustodyLot held = reserve.resultingLot();
                    CustodyEndpointEvidence sink =
                            CustodyEndpointEvidence.captured(
                                    "protected_currency_sink",
                                    CustodyAdapterCapability
                                            .TRANSACTIONAL_PROTECTED,
                                    ProtectedCashRedemptionSettlement
                                            .CURRENCY_SINK_OWNER,
                                    "spent." + held.lotId(), new byte[0],
                                    held.assetFingerprint(),
                                    ProtectedCashRedemptionSupport.hex(
                                            receipt.mutationTokenDigest()));
                    return CustodyMutation.terminal(held,
                            CustodyOperation.CONSUME,
                            ProtectedCashRedemptionSettlement
                                    .custodyConsumeRequestKey(
                                            reservation.transactionId(),
                                            reservation.destinationAccount(),
                                            held.lotId()),
                            new CustodyTransferEvidence(
                                    held.holdEvidence().destination(), sink),
                            now);
                }).toList();
        List<ProtectedMintJournalEvent> commits = reservation
                .mintReservations().stream().map(reserved -> {
                    UUID batchId = reserved.targetBatchId().orElseThrow();
                    return ProtectedMintJournalEvent.commit(
                            reservation.transactionId(), batchId,
                            ProtectedCashRedemptionSettlement
                                    .mintCommitRequestKey(
                                            reservation.transactionId(),
                                            reservation.destinationAccount(),
                                            batchId), reserved.quantity(), now);
                }).toList();
        long amount = reservation.amountMinorUnits();
        long walletCredit = walletCredit(reservation,
                walletBalanceBeforeMinorUnits,
                walletReservedBeforeMinorUnits);
        long claimCredit = Math.subtractExact(amount, walletCredit);
        List<LedgerLeg> legs = new ArrayList<>();
        legs.add(new LedgerLeg(LedgerAccountId.system(
                LedgerAccountType.PROTECTED_CURRENCY_OUTSTANDING),
                Math.negateExact(amount)));
        if (walletCredit > 0L) {
            legs.add(new LedgerLeg(reservation.destinationAccount(),
                    walletCredit));
        }
        Optional<EscrowClaim> overflow = Optional.empty();
        if (claimCredit > 0L) {
            UUID claimId = ProtectedCashRedemptionSettlement.overflowClaimId(
                    reservation);
            overflow = Optional.of(new EscrowClaim(claimId,
                    reservation.transactionId(), reservation.playerId(),
                    ProtectedCashRedemptionSettlement
                            .overflowClaimSourceKey(reservation),
                    overflowClaimKind(reservation), claimCredit,
                    claimCredit, new byte[0],
                    ClaimStatus.PENDING,
                    ProtectedCashRedemptionSettlement.OVERFLOW_CLAIM_LABEL,
                    now, now));
            legs.add(new LedgerLeg(new LedgerAccountId(
                    LedgerAccountType.PLAYER_CLAIM, claimId.toString()),
                    claimCredit));
        }
        LedgerTransaction ledger = new LedgerTransaction(
                reservation.transactionId(),
                ProtectedCashRedemptionSettlement.ledgerIdempotencyKey(
                        reservation.transactionId(),
                        reservation.destinationAccount()),
                ProtectedCashRedemptionSettlement.LEDGER_REASON, legs);
        return new ProtectedCashRedemptionSettlement(reservation, completed,
                receipt, consumptions, commits,
                reservation.destinationAccount(),
                walletBalanceBeforeMinorUnits,
                walletReservedBeforeMinorUnits, overflow, ledger);
    }

    private static ClaimKind overflowClaimKind(
            ProtectedCashRedemptionReservation reservation
    ) {
        return reservation.depositMode() == CashDepositMode.INTERNAL_ESCROW
                ? ClaimKind.INTERNAL_ESCROW_MONEY : ClaimKind.MONEY;
    }

    public static ProtectedCashRedemptionCancellation cancellation(
            ProtectedCashRedemptionReservation reservation,
            ProtectedCashInventoryState unchangedInventory,
            Instant now
    ) {
        List<ProtectedCashRedemptionCancellation.SlotObservation> observations =
                reservation.plan().portions().stream().map(portion ->
                        new ProtectedCashRedemptionCancellation.SlotObservation(
                                portion.slot(),
                                portion.exactStackSnapshot())).toList();
        ProtectedCashRedemptionCancellation.InventoryNoMutationProof proof =
                ProtectedCashRedemptionCancellation.InventoryNoMutationProof
                        .create(reservation.playerId(),
                                reservation.transactionId(),
                                reservation.reservationId(),
                                ProtectedCashRedemptionCancellation
                                        .inventoryProofRequestKey(
                                                reservation.transactionId(),
                                                reservation
                                                        .destinationAccount()),
                                observations, unchangedInventory.hash(), now);
        EscrowTransaction refunded = reservation.heldTransaction()
                .transitionTo(EscrowState.ABORTING, now)
                .transitionTo(EscrowState.REFUND_PENDING, now)
                .transitionTo(EscrowState.REFUNDED, now);
        List<CustodyMutation> releases = new ArrayList<>();
        for (int index = 0;
             index < reservation.custodyReservations().size(); index++) {
            CustodyLot held = reservation.custodyReservations().get(index)
                    .resultingLot();
            InternalBillInventoryPlanner.Portion portion =
                    reservation.plan().portions().get(index);
            CustodyEndpointEvidence original = held.holdEvidence().source();
            byte[] stackHash = ProtectedCashRedemptionSupport.sha256(
                    portion.exactStackSnapshot());
            CustodyEndpointEvidence destination =
                    new CustodyEndpointEvidence(original.adapterId(),
                            original.capability(), original.ownerKey(),
                            original.locationKey(), stackHash, stackHash,
                            ProtectedCashRedemptionSupport.hex(
                                    proof.proofDigest()));
            releases.add(CustodyMutation.terminal(held,
                    CustodyOperation.RELEASE,
                    ProtectedCashRedemptionCancellation
                            .custodyReleaseRequestKey(
                                    reservation.transactionId(),
                                    reservation.destinationAccount(),
                                    held.lotId()),
                    new CustodyTransferEvidence(
                            held.holdEvidence().destination(), destination),
                    now));
        }
        List<ProtectedMintJournalEvent> mintReleases = reservation
                .mintReservations().stream().map(reserved -> {
                    UUID batchId = reserved.targetBatchId().orElseThrow();
                    return ProtectedMintJournalEvent.release(
                            reservation.transactionId(), batchId,
                            ProtectedCashRedemptionCancellation
                                    .mintReleaseRequestKey(
                                            reservation.transactionId(),
                                            reservation.destinationAccount(),
                                            batchId), reserved.quantity(), now);
                }).toList();
        return new ProtectedCashRedemptionCancellation(reservation, refunded,
                proof, releases, mintReleases);
    }

    private static long walletCredit(
            ProtectedCashRedemptionReservation reservation,
            long walletBalanceBefore,
            long walletReservedBefore
    ) {
        if (walletReservedBefore < 0L) {
            throw new IllegalArgumentException(
                    "Protected cash reserved balance is invalid");
        }
        BigInteger capacity = BigInteger.valueOf(
                        reservation.walletBalanceLimitMinorUnits())
                .subtract(BigInteger.valueOf(walletBalanceBefore))
                .subtract(BigInteger.valueOf(walletReservedBefore));
        if (capacity.signum() <= 0) {
            return 0L;
        }
        BigInteger amount = BigInteger.valueOf(
                reservation.amountMinorUnits());
        return capacity.compareTo(amount) >= 0
                ? reservation.amountMinorUnits() : capacity.longValueExact();
    }

    private static String location(
            InternalBillInventoryPlanner.Portion portion
    ) {
        return "inventory." + portion.slot().container().name().toLowerCase(
                java.util.Locale.ROOT) + "." + portion.slot().index();
    }
}
