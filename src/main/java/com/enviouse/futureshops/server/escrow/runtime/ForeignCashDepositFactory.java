package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.Config;
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
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
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
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashInventoryState;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionCancellation.InventoryNoMutationProof;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionCancellation.SlotObservation;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionSettlement.InventoryMutationReceipt;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class ForeignCashDepositFactory {
    private static final String OVERFLOW_CLAIM_LABEL =
            "Foreign currency deposit overflow";

    private ForeignCashDepositFactory() {
    }

    static ForeignCashDepositReservation reservation(
            UUID requestId,
            UUID playerId,
            UUID transactionId,
            String requestKey,
            long walletBalanceLimitMinorUnits,
            ForeignCashDepositPlan plan,
            ProtectedCashInventoryState beforeInventory,
            Instant now
    ) {
        LedgerAccountId destination = new LedgerAccountId(
                LedgerAccountType.PLAYER_WALLET, playerId.toString());
        List<CustodyMutation> custody = new ArrayList<>();
        for (ForeignCashDepositPlan.Portion portion : plan.portions()) {
            UUID lotId = ForeignCashDepositReservation.custodyLotId(
                    transactionId, portion);
            String reserveKey = ForeignCashDepositReservation
                    .custodyReserveRequestKey(transactionId, lotId);
            byte[] snapshot = portion.exactStackSnapshot();
            CustodyEndpointEvidence source =
                    CustodyEndpointEvidence.captured(
                            "foreign_player_inventory",
                            CustodyAdapterCapability.UNPROTECTED_EXTERNAL,
                            playerId.toString(), location(portion.slot()),
                            snapshot, snapshot,
                            "foreign cash reserve " + lotId);
            CustodyEndpointEvidence vault =
                    CustodyEndpointEvidence.captured(
                            "foreign_currency_escrow",
                            CustodyAdapterCapability.UNPROTECTED_EXTERNAL,
                            transactionId.toString(), "vault." + lotId,
                            new byte[0], snapshot,
                            "foreign cash reserve " + lotId);
            CustodyLot lot = CustodyLot.held(lotId, transactionId,
                    reserveKey,
                    CustodyAssetType.FOREIGN_PHYSICAL_CURRENCY,
                    CustodyProtectionTier.UNPROTECTED_FOREIGN,
                    portion.valueMinorUnits(), plan.providerId(),
                    List.of(CustodyItemSnapshot.capture(
                            portion.registryId(), portion.selectedCount(),
                            snapshot)), List.of(),
                    new CustodyTransferEvidence(source, vault), now);
            custody.add(CustodyMutation.reserve(lot));
        }
        EscrowParty player = EscrowParty.player(playerId);
        EscrowParty authority = EscrowParty.system("foreign_currency");
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
            ForeignCashDepositPlan.Portion portion =
                    plan.portions().get(index);
            CustodyMutation mutation = custody.get(index);
            assets.add(new EscrowAssetLot(
                    mutation.resultingLot().lotId(),
                    EscrowAssetLotType.FOREIGN_PHYSICAL_CURRENCY,
                    EscrowProtectionLevel.EXTERNAL, player, authority,
                    portion.selectedCount(), Optional.of(new MoneyAmount(
                    ForeignCashDepositReservation.CURRENCY_ID,
                    portion.valueMinorUnits())),
                    CustodyMutationCodec.encode(mutation), Map.of(
                    "authority", "foreign_unprotected",
                    "provider", plan.providerId(),
                    "provider_signature", plan.providerSignature(),
                    "registry_id", portion.registryId(),
                    "unit_value", Long.toString(
                            portion.unitValueMinorUnits()),
                    "selected_count", Integer.toString(
                            portion.selectedCount()),
                    "slot", portion.slot().container().name() + "."
                            + portion.slot().index(),
                    "inventory_hash", HexFormat.of().formatHex(
                            beforeInventory.hash()),
                    "dupe_protection_warning",
                            Config.FOREIGN_CURRENCY_WARNING)));
        }
        EscrowTransaction held = EscrowTransaction.create(
                        new EscrowTransactionId(transactionId),
                        Optional.empty(), new EscrowRequestKey(requestKey),
                        EscrowOperation.CURRENCY_DEPOSIT, participants,
                        assets, now, configRevision(
                                plan.providerSignature()), Optional.empty())
                .transitionTo(EscrowState.VALIDATED, now)
                .transitionTo(EscrowState.HOLDING, now)
                .transitionTo(EscrowState.HELD, now);
        UUID reservationId = ForeignCashDepositReservation.reservationId(
                requestId, playerId, destination,
                walletBalanceLimitMinorUnits, beforeInventory.hash(), plan,
                held);
        return new ForeignCashDepositReservation(reservationId, requestId,
                playerId, destination, walletBalanceLimitMinorUnits,
                beforeInventory.hash(), plan, held, custody);
    }

    static ForeignCashDepositSettlement settlement(
            ForeignCashDepositReservation reservation,
            InventoryMutationReceipt receipt,
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
                                    "foreign_currency_sink",
                                    CustodyAdapterCapability
                                            .UNPROTECTED_EXTERNAL,
                                    ForeignCashDepositSettlement
                                            .CURRENCY_SINK_OWNER,
                                    "spent." + held.lotId(), new byte[0],
                                    held.assetFingerprint(),
                                    HexFormat.of().formatHex(
                                            receipt.mutationTokenDigest()));
                    return CustodyMutation.terminal(held,
                            CustodyOperation.CONSUME,
                            ForeignCashDepositSettlement
                                    .custodyConsumeRequestKey(
                                            reservation.transactionId(),
                                            held.lotId()),
                            new CustodyTransferEvidence(
                                    held.holdEvidence().destination(), sink),
                            now);
                }).toList();
        long amount = reservation.amountMinorUnits();
        long walletCredit = ForeignCashDepositSettlement
                .expectedWalletCredit(reservation,
                        walletBalanceBeforeMinorUnits,
                        walletReservedBeforeMinorUnits);
        long claimCredit = Math.subtractExact(amount, walletCredit);
        List<LedgerLeg> legs = new ArrayList<>();
        legs.add(new LedgerLeg(foreignSourceAccount(reservation),
                Math.negateExact(amount)));
        if (walletCredit > 0L) {
            legs.add(new LedgerLeg(reservation.destinationAccount(),
                    walletCredit));
        }
        Optional<EscrowClaim> overflow = Optional.empty();
        if (claimCredit > 0L) {
            UUID claimId = ForeignCashDepositSettlement.overflowClaimId(
                    reservation);
            overflow = Optional.of(new EscrowClaim(claimId,
                    reservation.transactionId(), reservation.playerId(),
                    ForeignCashDepositSettlement
                            .overflowClaimSourceKey(reservation),
                    ClaimKind.MONEY, claimCredit, claimCredit, new byte[0],
                    ClaimStatus.PENDING, OVERFLOW_CLAIM_LABEL, now, now));
            legs.add(new LedgerLeg(new LedgerAccountId(
                    LedgerAccountType.PLAYER_CLAIM, claimId.toString()),
                    claimCredit));
        }
        LedgerTransaction ledger = new LedgerTransaction(
                reservation.transactionId(),
                ForeignCashDepositSettlement
                        .ledgerIdempotencyKey(reservation),
                ForeignCashDepositSettlement.LEDGER_REASON, legs);
        return new ForeignCashDepositSettlement(reservation, completed,
                receipt, consumptions, walletBalanceBeforeMinorUnits,
                walletReservedBeforeMinorUnits, overflow, ledger);
    }

    static ForeignCashDepositCancellation cancellation(
            ForeignCashDepositReservation reservation,
            ProtectedCashInventoryState unchangedInventory,
            Instant now
    ) {
        List<SlotObservation> observations = reservation.plan().portions()
                .stream().map(portion -> new SlotObservation(
                        portion.slot(), portion.exactStackSnapshot()))
                .toList();
        InventoryNoMutationProof proof = InventoryNoMutationProof.create(
                reservation.playerId(), reservation.transactionId(),
                reservation.reservationId(),
                ForeignCashDepositCancellation.inventoryProofRequestKey(
                        reservation.transactionId()), observations,
                unchangedInventory.hash(), now);
        EscrowTransaction refunded = reservation.heldTransaction()
                .transitionTo(EscrowState.ABORTING, now)
                .transitionTo(EscrowState.REFUND_PENDING, now)
                .transitionTo(EscrowState.REFUNDED, now);
        List<CustodyMutation> releases = reservation
                .custodyReservations().stream().map(reserve -> {
                    CustodyLot held = reserve.resultingLot();
                    CustodyEndpointEvidence original =
                            held.holdEvidence().source();
                    return CustodyMutation.terminal(held,
                            CustodyOperation.RELEASE,
                            ForeignCashDepositCancellation
                                    .custodyReleaseRequestKey(
                                            reservation.transactionId(),
                                            held.lotId()),
                            new CustodyTransferEvidence(
                                    held.holdEvidence().destination(),
                                    new CustodyEndpointEvidence(
                                            original.adapterId(),
                                            original.capability(),
                                            original.ownerKey(),
                                            original.locationKey(),
                                            original.beforeStateHash(),
                                            original.afterStateHash(),
                                            HexFormat.of().formatHex(
                                                    proof.proofDigest()))),
                            now);
                }).toList();
        return new ForeignCashDepositCancellation(reservation, refunded,
                proof, releases);
    }

    static LedgerAccountId foreignSourceAccount(
            ForeignCashDepositReservation reservation
    ) {
        return new LedgerAccountId(LedgerAccountType.FOREIGN_CURRENCY_SOURCE,
                reservation.plan().providerSignature());
    }

    private static String location(
            InternalBillInventoryPlanner.SlotIdentity slot
    ) {
        return "inventory." + slot.container().name().toLowerCase(
                java.util.Locale.ROOT) + "." + slot.index();
    }

    private static long configRevision(String signature) {
        byte[] bytes = HexFormat.of().parseHex(signature);
        long value = 0L;
        for (int index = 0; index < Long.BYTES; index++) {
            value = value << 8 | bytes[index] & 255L;
        }
        return value & Long.MAX_VALUE;
    }
}
