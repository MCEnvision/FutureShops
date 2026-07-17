package com.enviouse.futureshops.server.escrow.redemption;

import com.enviouse.futureshops.init.ModItems;
import com.enviouse.futureshops.money.InternalBillAuthorityRouter;
import com.enviouse.futureshops.money.InternalBillInventoryPlanner;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.money.MoneyNbtKeys;
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
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class ProtectedCashRedemptionTestFixtures {
    static final UUID PLAYER_ID = UUID.fromString(
            "6b6e68b1-8402-4a45-b047-9f90903fbb25");
    static final UUID TRANSACTION_ID = UUID.fromString(
            "f1ea435f-2e82-488d-897c-335fe87c186b");
    static final UUID MINT_A = UUID.fromString(
            "9f99602e-7158-4c50-820a-578099c6cc96");
    static final UUID MINT_B = UUID.fromString(
            "bad79819-0c85-4864-b95e-b1ad1795b37b");
    static final UUID MINT_PLAYER = UUID.fromString(
            "a121105f-d543-4900-af98-f1317513f977");
    static final Instant CREATED_AT = Instant.parse("2026-07-17T12:00:00Z");
    static final Instant RESERVED_AT = CREATED_AT.plusSeconds(2);
    static final Instant HELD_AT = CREATED_AT.plusSeconds(3);
    static final Instant INVENTORY_AT = CREATED_AT.plusSeconds(4);
    static final Instant CONSUMED_AT = CREATED_AT.plusSeconds(5);
    static final Instant COMPLETED_AT = CREATED_AT.plusSeconds(7);
    static final Instant CANCEL_PROOF_AT = CREATED_AT.plusSeconds(4);
    static final Instant RELEASED_AT = CREATED_AT.plusSeconds(5);
    static final Instant REFUNDED_AT = CREATED_AT.plusSeconds(6);
    static final long WALLET_BALANCE_LIMIT = 10_000L;

    private ProtectedCashRedemptionTestFixtures() {
    }

    static ProtectedCashRedemptionReservation reservation() {
        return reservation(WALLET_BALANCE_LIMIT);
    }

    static ProtectedCashRedemptionReservation reservation(long walletBalanceLimit) {
        LedgerAccountId destination = new LedgerAccountId(
                LedgerAccountType.PLAYER_WALLET, PLAYER_ID.toString());
        byte[] inventoryBeforeHash = hash("inventory before");
        InternalBillInventoryPlanner.ExactPlan plan = plan();
        List<CustodyMutation> custody = custodyReservations(plan, destination);
        EscrowTransaction held = heldTransaction(plan, custody, destination,
                walletBalanceLimit, inventoryBeforeHash);
        List<ProtectedMintJournalEvent> mints = List.of(
                ProtectedMintJournalEvent.reserve(TRANSACTION_ID, MINT_A,
                        ProtectedCashRedemptionReservation.mintReserveRequestKey(
                                TRANSACTION_ID, destination, MINT_A),
                        3, RESERVED_AT),
                ProtectedMintJournalEvent.reserve(TRANSACTION_ID, MINT_B,
                        ProtectedCashRedemptionReservation.mintReserveRequestKey(
                                TRANSACTION_ID, destination, MINT_B),
                        1, RESERVED_AT));
        UUID reservationId = ProtectedCashRedemptionReservation.reservationId(
                PLAYER_ID, destination, walletBalanceLimit,
                inventoryBeforeHash, held, plan);
        return new ProtectedCashRedemptionReservation(reservationId,
                PLAYER_ID, destination, walletBalanceLimit,
                inventoryBeforeHash, plan, held, custody, mints);
    }

    static ProtectedCashRedemptionSettlement settlement() {
        return settlement(0L, 0L);
    }

    static ProtectedCashRedemptionSettlement settlement(
            long walletBalanceBefore,
            long walletReservedBefore
    ) {
        return settlement(WALLET_BALANCE_LIMIT, walletBalanceBefore,
                walletReservedBefore);
    }

    static ProtectedCashRedemptionSettlement settlement(
            long walletBalanceLimit,
            long walletBalanceBefore,
            long walletReservedBefore
    ) {
        ProtectedCashRedemptionReservation reservation = reservation(
                walletBalanceLimit);
        LedgerAccountId destination = reservation.destinationAccount();
        EscrowTransaction completed = completedTransaction(
                reservation.heldTransaction());
        List<ProtectedCashRedemptionSettlement.SlotMutation> slotMutations =
                reservation.plan().portions().stream()
                        .map(portion -> new ProtectedCashRedemptionSettlement
                                .SlotMutation(portion.slot(),
                                portion.selectedCount(),
                                portion.exactStackSnapshot(),
                                ProtectedCashRedemptionSupport
                                        .expectedAfterSnapshot(portion)))
                        .toList();
        ProtectedCashRedemptionSettlement.InventoryMutationReceipt inventory =
                ProtectedCashRedemptionSettlement.InventoryMutationReceipt.create(
                        PLAYER_ID, TRANSACTION_ID,
                        reservation.reservationId(),
                        ProtectedCashRedemptionSettlement
                                .inventoryMutationRequestKey(TRANSACTION_ID,
                                        reservation.destinationAccount()),
                        slotMutations, reservation.inventoryBeforeHash(),
                        hash("inventory after"), INVENTORY_AT);
        List<CustodyMutation> consumes = new ArrayList<>();
        for (CustodyMutation reserve : reservation.custodyReservations()) {
            CustodyEndpointEvidence sink = CustodyEndpointEvidence.captured(
                    "protected_currency_sink",
                    CustodyAdapterCapability.TRANSACTIONAL_PROTECTED,
                    ProtectedCashRedemptionSettlement.CURRENCY_SINK_OWNER,
                    "spent." + reserve.resultingLot().lotId(),
                    bytes("sink before"), bytes("sink after"),
                    ProtectedCashRedemptionSupport.hex(
                            inventory.mutationTokenDigest()));
            CustodyTransferEvidence evidence = new CustodyTransferEvidence(
                    reserve.resultingLot().holdEvidence().destination(), sink);
            consumes.add(CustodyMutation.terminal(reserve.resultingLot(),
                    CustodyOperation.CONSUME,
                    ProtectedCashRedemptionSettlement.custodyConsumeRequestKey(
                            TRANSACTION_ID,
                            reservation.destinationAccount(),
                            reserve.resultingLot().lotId()),
                    evidence, CONSUMED_AT));
        }
        List<ProtectedMintJournalEvent> commits = reservation
                .mintReservations().stream()
                .map(event -> ProtectedMintJournalEvent.commit(
                        TRANSACTION_ID, event.targetBatchId().orElseThrow(),
                        ProtectedCashRedemptionSettlement.mintCommitRequestKey(
                                TRANSACTION_ID,
                                reservation.destinationAccount(),
                                event.targetBatchId().orElseThrow()),
                        event.quantity(), CONSUMED_AT))
                .toList();
        long amount = reservation.amountMinorUnits();
        BigInteger capacity = BigInteger.valueOf(walletBalanceLimit)
                .subtract(BigInteger.valueOf(walletBalanceBefore))
                .subtract(BigInteger.valueOf(walletReservedBefore));
        long walletCredit = capacity.signum() <= 0 ? 0L
                : capacity.compareTo(BigInteger.valueOf(amount)) >= 0
                ? amount : capacity.longValueExact();
        long claimCredit = Math.subtractExact(amount, walletCredit);
        List<LedgerLeg> legs = new ArrayList<>();
        legs.add(new LedgerLeg(LedgerAccountId.system(
                LedgerAccountType.PROTECTED_CURRENCY_OUTSTANDING),
                Math.negateExact(amount)));
        if (walletCredit > 0L) {
            legs.add(new LedgerLeg(destination, walletCredit));
        }
        Optional<EscrowClaim> overflowClaim = Optional.empty();
        if (claimCredit > 0L) {
            UUID claimId = ProtectedCashRedemptionSettlement.overflowClaimId(
                    reservation);
            overflowClaim = Optional.of(new EscrowClaim(claimId,
                    TRANSACTION_ID, PLAYER_ID,
                    ProtectedCashRedemptionSettlement
                            .overflowClaimSourceKey(reservation),
                    ClaimKind.MONEY, claimCredit, claimCredit, new byte[0],
                    ClaimStatus.PENDING,
                    ProtectedCashRedemptionSettlement.OVERFLOW_CLAIM_LABEL,
                    CONSUMED_AT, CONSUMED_AT));
            legs.add(new LedgerLeg(new LedgerAccountId(
                    LedgerAccountType.PLAYER_CLAIM, claimId.toString()),
                    claimCredit));
        }
        LedgerTransaction ledger = new LedgerTransaction(TRANSACTION_ID,
                ProtectedCashRedemptionSettlement.ledgerIdempotencyKey(
                        TRANSACTION_ID,
                        reservation.destinationAccount()),
                ProtectedCashRedemptionSettlement.LEDGER_REASON,
                legs);
        return new ProtectedCashRedemptionSettlement(reservation, completed,
                inventory, consumes, commits, destination,
                walletBalanceBefore, walletReservedBefore, overflowClaim,
                ledger);
    }

    static ProtectedCashRedemptionCancellation cancellation() {
        ProtectedCashRedemptionReservation reservation = reservation();
        EscrowTransaction refunded = reservation.heldTransaction()
                .transitionTo(EscrowState.ABORTING, REFUNDED_AT)
                .transitionTo(EscrowState.REFUND_PENDING, REFUNDED_AT)
                .transitionTo(EscrowState.REFUNDED, REFUNDED_AT);
        List<ProtectedCashRedemptionCancellation.SlotObservation>
                observations = reservation.plan().portions().stream()
                .map(portion -> new ProtectedCashRedemptionCancellation
                        .SlotObservation(portion.slot(),
                        portion.exactStackSnapshot()))
                .toList();
        ProtectedCashRedemptionCancellation.InventoryNoMutationProof proof =
                ProtectedCashRedemptionCancellation.InventoryNoMutationProof
                        .create(PLAYER_ID, TRANSACTION_ID,
                                reservation.reservationId(),
                                ProtectedCashRedemptionCancellation
                                        .inventoryProofRequestKey(
                                                TRANSACTION_ID,
                                                reservation
                                                        .destinationAccount()),
                                observations,
                                reservation.inventoryBeforeHash(),
                                CANCEL_PROOF_AT);
        List<CustodyMutation> releases = new ArrayList<>();
        for (int index = 0;
             index < reservation.custodyReservations().size(); index++) {
            CustodyLot held = reservation.custodyReservations().get(index)
                    .resultingLot();
            InternalBillInventoryPlanner.Portion portion =
                    reservation.plan().portions().get(index);
            CustodyEndpointEvidence original = held.holdEvidence().source();
            byte[] snapshotHash = ProtectedCashRedemptionSupport.sha256(
                    portion.exactStackSnapshot());
            CustodyEndpointEvidence destination =
                    new CustodyEndpointEvidence(original.adapterId(),
                            original.capability(), original.ownerKey(),
                            original.locationKey(), snapshotHash, snapshotHash,
                            ProtectedCashRedemptionSupport.hex(
                                    proof.proofDigest()));
            releases.add(CustodyMutation.terminal(held,
                    CustodyOperation.RELEASE,
                    ProtectedCashRedemptionCancellation
                            .custodyReleaseRequestKey(TRANSACTION_ID,
                                    reservation.destinationAccount(),
                                    held.lotId()),
                    new CustodyTransferEvidence(
                            held.holdEvidence().destination(), destination),
                    RELEASED_AT));
        }
        List<ProtectedMintJournalEvent> mintReleases = reservation
                .mintReservations().stream()
                .map(event -> ProtectedMintJournalEvent.release(
                        TRANSACTION_ID,
                        event.targetBatchId().orElseThrow(),
                        ProtectedCashRedemptionCancellation
                                .mintReleaseRequestKey(TRANSACTION_ID,
                                        reservation.destinationAccount(),
                                        event.targetBatchId().orElseThrow()),
                        event.quantity(), RELEASED_AT))
                .toList();
        return new ProtectedCashRedemptionCancellation(reservation, refunded,
                proof, releases, mintReleases);
    }

    static InternalBillInventoryPlanner.ExactPlan plan() {
        List<InternalBillInventoryPlanner.Portion> portions = List.of(
                portion(InternalBillInventoryPlanner.Container.MAIN, 0,
                        MINT_A, 100L, 8, 3, 2),
                portion(InternalBillInventoryPlanner.Container.MAIN, 1,
                        MINT_A, 100L, 8, 2, 1),
                portion(InternalBillInventoryPlanner.Container.OFFHAND, 0,
                        MINT_B, 500L, 4, 2, 1));
        return new InternalBillInventoryPlanner.ExactPlan(
                InternalBillInventoryPlanner.PlanStatus.SUCCESS,
                800L, 800L,
                InternalBillAuthorityRouter.Authority.PROTECTED,
                portions);
    }

    static ProductionScenario productionScenario() {
        InternalBillInventoryPlanner.ExactPlan plan = plan();
        ProtectedCashInventoryState before = inventoryState(plan, false);
        ProtectedCashInventoryState after = inventoryState(plan, true);
        ProtectedCashRedemptionReservation reservation =
                ProtectedCashRedemptionFactory.walletReservation(
                        PLAYER_ID, TRANSACTION_ID,
                        "protected.cash.production." + TRANSACTION_ID,
                        4L, WALLET_BALANCE_LIMIT, plan, before,
                        HELD_AT);
        List<ProtectedCashRedemptionSettlement.SlotMutation> mutations =
                plan.portions().stream().map(portion ->
                        new ProtectedCashRedemptionSettlement.SlotMutation(
                                portion.slot(), portion.selectedCount(),
                                portion.exactStackSnapshot(),
                                ProtectedCashRedemptionSupport
                                        .expectedAfterSnapshot(portion)))
                        .toList();
        ProtectedCashRedemptionSettlement.InventoryMutationReceipt receipt =
                ProtectedCashRedemptionSettlement.InventoryMutationReceipt
                        .create(PLAYER_ID, TRANSACTION_ID,
                                reservation.reservationId(),
                                ProtectedCashRedemptionSettlement
                                        .inventoryMutationRequestKey(
                                                TRANSACTION_ID,
                                                reservation
                                                        .destinationAccount()),
                                mutations, before.hash(), after.hash(),
                                INVENTORY_AT);
        ProtectedCashRedemptionSettlement settlement =
                ProtectedCashRedemptionFactory.settlement(reservation,
                        receipt, 0L, 0L, COMPLETED_AT);
        ProtectedCashRedemptionCancellation cancellation =
                ProtectedCashRedemptionFactory.cancellation(reservation,
                        before, REFUNDED_AT);
        return new ProductionScenario(before, after, reservation,
                settlement, cancellation);
    }

    private static ProtectedCashInventoryState inventoryState(
            InternalBillInventoryPlanner.ExactPlan plan,
            boolean afterRemoval
    ) {
        return ProtectedCashInventoryState.fromPlayerInventoryTag(
                playerInventoryTag(plan, afterRemoval));
    }

    static ListTag playerInventoryTag(
            InternalBillInventoryPlanner.ExactPlan plan,
            boolean afterRemoval
    ) {
        ListTag inventory = new ListTag();
        for (InternalBillInventoryPlanner.Portion portion :
                plan.portions()) {
            ItemStack stack = ItemStackSnapshotCodec.decode(
                    portion.exactStackSnapshot());
            if (afterRemoval) {
                stack.shrink(portion.selectedCount());
            }
            if (!stack.isEmpty()) {
                CompoundTag entry = stack.save(new CompoundTag());
                int slot = portion.slot().container()
                        == InternalBillInventoryPlanner.Container.MAIN
                        ? portion.slot().index() : 150;
                entry.putByte("Slot", (byte) slot);
                inventory.add(entry);
            }
        }
        return inventory;
    }

    static InternalBillInventoryPlanner.Portion portion(
            InternalBillInventoryPlanner.Container container,
            int slot,
            UUID mintId,
            long denomination,
            int authorizedCount,
            int originalCount,
            int selectedCount
    ) {
        return new InternalBillInventoryPlanner.Portion(
                new InternalBillInventoryPlanner.SlotIdentity(container, slot),
                InternalBillAuthorityRouter.Authority.PROTECTED,
                mintId.toString(), denomination, authorizedCount,
                originalCount, selectedCount,
                billSnapshot(mintId, denomination, authorizedCount,
                        originalCount));
    }

    static byte[] billSnapshot(UUID mintId,
                               long denomination,
                               int authorizedCount,
                               int count) {
        ItemStack stack = new ItemStack(ModItems.MONEY_ITEM.get(), count);
        CompoundTag mint = new CompoundTag();
        mint.putLong(MoneyNbtKeys.DENOMINATION, denomination);
        mint.putString(MoneyNbtKeys.MINT_ID, mintId.toString());
        mint.putLong(MoneyNbtKeys.MINT_TIMESTAMP, 1_752_750_000L);
        mint.putString(MoneyNbtKeys.MINT_PLAYER, MINT_PLAYER.toString());
        mint.putString(MoneyNbtKeys.MINT_SERVER, serverEvidence(mintId));
        mint.putInt(MoneyNbtKeys.AUTHORIZED_COUNT, authorizedCount);
        mint.putString(MoneyNbtKeys.CHECKSUM, checksumEvidence(mintId));
        stack.getOrCreateTag().put(MoneyNbtKeys.ROOT, mint);
        return ItemStackSnapshotCodec.encode(stack);
    }

    static EscrowTransaction completedTransaction(EscrowTransaction held) {
        return held.transitionTo(EscrowState.COMMIT_DECIDED,
                        CREATED_AT.plusSeconds(4))
                .transitionTo(EscrowState.COMMITTED,
                        CREATED_AT.plusSeconds(5))
                .transitionTo(EscrowState.CLAIMS_CREATED,
                        CREATED_AT.plusSeconds(6))
                .transitionTo(EscrowState.COMPLETED, COMPLETED_AT);
    }

    static byte[] hash(String value) {
        return ProtectedCashRedemptionSupport.sha256(bytes(value));
    }

    private static List<CustodyMutation> custodyReservations(
            InternalBillInventoryPlanner.ExactPlan plan,
            LedgerAccountId destinationAccount
    ) {
        List<CustodyMutation> mutations = new ArrayList<>();
        for (InternalBillInventoryPlanner.Portion portion : plan.portions()) {
            UUID lotId = ProtectedCashRedemptionReservation.custodyLotId(
                    TRANSACTION_ID, portion);
            String requestKey = ProtectedCashRedemptionReservation
                    .custodyReserveRequestKey(TRANSACTION_ID,
                            destinationAccount, lotId);
            CustodyEndpointEvidence source = CustodyEndpointEvidence.captured(
                    "player_inventory", CustodyAdapterCapability.RECONCILABLE,
                    PLAYER_ID.toString(),
                    "inventory." + portion.slot().container().name() + "."
                            + portion.slot().index(),
                    bytes("player before " + lotId),
                    bytes("player after " + lotId),
                    "reserve source " + lotId);
            CustodyEndpointEvidence destination =
                    CustodyEndpointEvidence.captured(
                            "escrow_vault",
                            CustodyAdapterCapability.TRANSACTIONAL_PROTECTED,
                            TRANSACTION_ID.toString(), "vault." + lotId,
                            bytes("vault before " + lotId),
                            bytes("vault after " + lotId),
                            "reserve destination " + lotId);
            CustodyLot lot = CustodyLot.held(lotId, TRANSACTION_ID,
                    requestKey,
                    CustodyAssetType.PROTECTED_PHYSICAL_CURRENCY,
                    CustodyProtectionTier.PROTECTED,
                    portion.valueMinorUnits(),
                    CustodyLot.BUILT_IN_CURRENCY_PROVIDER,
                    List.of(CustodyItemSnapshot.capture("futureshops:money",
                            portion.selectedCount(),
                            portion.exactStackSnapshot())),
                    List.of(new ProtectedCurrencyProvenance(
                            UUID.fromString(portion.mintId()),
                            portion.denominationMinorUnits(),
                            portion.authorizedCount(), portion.selectedCount(),
                            serverEvidence(UUID.fromString(portion.mintId())),
                            checksumEvidence(UUID.fromString(portion.mintId())))),
                    new CustodyTransferEvidence(source, destination),
                    RESERVED_AT);
            mutations.add(CustodyMutation.reserve(lot));
        }
        return List.copyOf(mutations);
    }

    private static EscrowTransaction heldTransaction(
            InternalBillInventoryPlanner.ExactPlan plan,
            List<CustodyMutation> custody,
            LedgerAccountId destinationAccount,
            long walletBalanceLimitMinorUnits,
            byte[] inventoryBeforeHash
    ) {
        EscrowParty player = EscrowParty.player(PLAYER_ID);
        EscrowParty system = EscrowParty.system("protected_currency");
        Set<EscrowParticipant> participants = Set.of(
                new EscrowParticipant(player, Set.of(
                        EscrowParticipantRole.INITIATOR,
                        EscrowParticipantRole.PAYER,
                        EscrowParticipantRole.BUYER,
                        EscrowParticipantRole.BENEFICIARY)),
                new EscrowParticipant(system, Set.of(
                        EscrowParticipantRole.BENEFICIARY,
                        EscrowParticipantRole.CUSTODIAN)));
        List<EscrowAssetLot> assets = new ArrayList<>();
        for (int index = 0; index < plan.portions().size(); index++) {
            InternalBillInventoryPlanner.Portion portion =
                    plan.portions().get(index);
            CustodyMutation mutation = custody.get(index);
            assets.add(new EscrowAssetLot(
                    mutation.resultingLot().lotId(),
                    EscrowAssetLotType.PROTECTED_PHYSICAL_CURRENCY,
                    EscrowProtectionLevel.PROTECTED,
                    player, system, portion.selectedCount(),
                    Optional.of(new MoneyAmount(
                            ProtectedCashRedemptionReservation.CURRENCY_ID,
                            portion.valueMinorUnits())),
                    CustodyMutationCodec.encode(mutation),
                    ProtectedCashRedemptionReservation.assetAttributes(
                            portion, destinationAccount,
                            walletBalanceLimitMinorUnits,
                            inventoryBeforeHash)));
        }
        return EscrowTransaction.create(
                        new EscrowTransactionId(TRANSACTION_ID),
                        Optional.empty(),
                        new EscrowRequestKey(
                                "protected.cash.test." + TRANSACTION_ID),
                        EscrowOperation.CURRENCY_DEPOSIT,
                        participants, assets, CREATED_AT, 4L,
                        Optional.empty())
                .transitionTo(EscrowState.VALIDATED,
                        CREATED_AT.plusSeconds(1))
                .transitionTo(EscrowState.HOLDING, RESERVED_AT)
                .transitionTo(EscrowState.HELD, HELD_AT);
    }

    private static String serverEvidence(UUID mintId) {
        return "server evidence " + mintId;
    }

    private static String checksumEvidence(UUID mintId) {
        return "checksum evidence " + mintId;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    record ProductionScenario(
            ProtectedCashInventoryState before,
            ProtectedCashInventoryState after,
            ProtectedCashRedemptionReservation reservation,
            ProtectedCashRedemptionSettlement settlement,
            ProtectedCashRedemptionCancellation cancellation
    ) {
    }
}
