package com.enviouse.futureshops.server.escrow.redemption;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.money.InternalBillAuthorityRouter;
import com.enviouse.futureshops.money.InternalBillInventoryPlanner;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutation;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintJournalEvent;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.model.CashDepositMode;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLot;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtectedCashRedemptionCodecTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void reservationAndSettlementRoundTripWithReplayEquality() {
        ProtectedCashRedemptionReservation reservation =
                ProtectedCashRedemptionTestFixtures.reservation();
        byte[] reservationBytes =
                ProtectedCashRedemptionReservationCodec.encode(reservation);
        ProtectedCashRedemptionReservation decodedReservation =
                ProtectedCashRedemptionReservationCodec.decode(reservationBytes);

        assertEquals(reservation, decodedReservation);
        assertEquals(reservation.fingerprint(),
                decodedReservation.fingerprint());
        assertArrayEquals(reservationBytes,
                ProtectedCashRedemptionReservationCodec.encode(
                        decodedReservation));
        assertEquals(800L, reservation.amountMinorUnits());
        assertEquals(3, reservation.custodyReservations().size());
        assertEquals(2, reservation.mintReservations().size());

        ProtectedCashRedemptionSettlement settlement =
                ProtectedCashRedemptionTestFixtures.settlement();
        byte[] settlementBytes =
                ProtectedCashRedemptionSettlementCodec.encode(settlement);
        ProtectedCashRedemptionSettlement decodedSettlement =
                ProtectedCashRedemptionSettlementCodec.decode(settlementBytes);

        assertEquals(settlement, decodedSettlement);
        assertEquals(settlement.fingerprint(),
                decodedSettlement.fingerprint());
        assertArrayEquals(settlementBytes,
                ProtectedCashRedemptionSettlementCodec.encode(
                        decodedSettlement));

        ProtectedCashRedemptionCancellation cancellation =
                ProtectedCashRedemptionTestFixtures.cancellation();
        byte[] cancellationBytes =
                ProtectedCashRedemptionCancellationCodec.encode(cancellation);
        ProtectedCashRedemptionCancellation decodedCancellation =
                ProtectedCashRedemptionCancellationCodec.decode(
                        cancellationBytes);
        assertEquals(cancellation, decodedCancellation);
        assertEquals(cancellation.fingerprint(),
                decodedCancellation.fingerprint());
        assertArrayEquals(cancellationBytes,
                ProtectedCashRedemptionCancellationCodec.encode(
                        decodedCancellation));
    }

    @Test
    void internalEscrowModeRoundTripsAndCreatesOnlyInternalMoney() {
        var baseline = ProtectedCashRedemptionTestFixtures
                .productionScenario();
        ProtectedCashRedemptionReservation reservation =
                ProtectedCashRedemptionFactory.walletReservation(
                        ProtectedCashRedemptionTestFixtures.PLAYER_ID,
                        UUID.fromString(
                                "a1000000-0000-0000-0000-000000000001"),
                        "protected.cash.internal.mode",
                        4L,
                        ProtectedCashRedemptionTestFixtures
                                .WALLET_BALANCE_LIMIT,
                        ProtectedCashRedemptionTestFixtures.plan(),
                        baseline.before(),
                        CashDepositMode.INTERNAL_ESCROW,
                        ProtectedCashRedemptionTestFixtures.HELD_AT);
        var baselineReceipt = baseline.settlement()
                .inventoryMutation();
        var receipt = ProtectedCashRedemptionSettlement
                .InventoryMutationReceipt.create(
                        reservation.playerId(), reservation.transactionId(),
                        reservation.reservationId(),
                        ProtectedCashRedemptionSettlement
                                .inventoryMutationRequestKey(
                                reservation.transactionId(),
                                reservation.destinationAccount()),
                        baselineReceipt.mutations(),
                        baselineReceipt.beforeInventoryHash(),
                        baselineReceipt.afterInventoryHash(),
                        baselineReceipt.occurredAt());
        ProtectedCashRedemptionSettlement settlement =
                ProtectedCashRedemptionFactory.settlement(reservation,
                        receipt, reservation.walletBalanceLimitMinorUnits(),
                        0L,
                        ProtectedCashRedemptionTestFixtures.COMPLETED_AT);

        assertEquals(CashDepositMode.INTERNAL_ESCROW,
                ProtectedCashRedemptionReservationCodec.decode(
                        ProtectedCashRedemptionReservationCodec.encode(
                                reservation)).depositMode());
        assertEquals(ClaimKind.INTERNAL_ESCROW_MONEY,
                settlement.overflowClaim().orElseThrow().kind());
        assertEquals(settlement,
                ProtectedCashRedemptionSettlementCodec.decode(
                ProtectedCashRedemptionSettlementCodec.encode(
                        settlement)));
    }

    @Test
    void schemaThreeReservationAndNestedWalTerminalsDecodeAsPublicWallet() {
        ProtectedCashRedemptionTestFixtures.ProductionScenario baseline =
                ProtectedCashRedemptionTestFixtures.productionScenario();
        ProtectedCashRedemptionReservation legacy = legacyReservation(
                baseline.reservation());
        var baselineReceipt = baseline.settlement().inventoryMutation();
        var receipt = ProtectedCashRedemptionSettlement
                .InventoryMutationReceipt.create(
                        legacy.playerId(), legacy.transactionId(),
                        legacy.reservationId(), baselineReceipt.requestKey(),
                        baselineReceipt.mutations(),
                        baselineReceipt.beforeInventoryHash(),
                        baselineReceipt.afterInventoryHash(),
                        baselineReceipt.occurredAt());
        ProtectedCashRedemptionSettlement settlement =
                ProtectedCashRedemptionFactory.settlement(legacy, receipt,
                        legacy.walletBalanceLimitMinorUnits(), 0L,
                        ProtectedCashRedemptionTestFixtures.COMPLETED_AT);
        ProtectedCashRedemptionCancellation cancellation =
                ProtectedCashRedemptionFactory.cancellation(legacy,
                        baseline.before(),
                        ProtectedCashRedemptionTestFixtures.REFUNDED_AT);
        byte[] reservationBytes = legacyReservationBytes(legacy);
        byte[] settlementBytes = replaceNestedReservation(
                ProtectedCashRedemptionSettlementCodec.encode(settlement),
                reservationBytes);
        byte[] cancellationBytes = replaceNestedReservation(
                ProtectedCashRedemptionCancellationCodec.encode(
                        cancellation), reservationBytes);

        ProtectedCashRedemptionReservation decodedReservation =
                ProtectedCashRedemptionReservationCodec.decode(
                        reservationBytes);
        ProtectedCashRedemptionSettlement decodedSettlement =
                ProtectedCashRedemptionSettlementCodec.decode(
                        settlementBytes);
        ProtectedCashRedemptionCancellation decodedCancellation =
                ProtectedCashRedemptionCancellationCodec.decode(
                        cancellationBytes);

        assertEquals(legacy, decodedReservation);
        assertEquals(settlement, decodedSettlement);
        assertEquals(cancellation, decodedCancellation);
        assertEquals(CashDepositMode.PUBLIC_WALLET,
                decodedReservation.depositMode());
        assertEquals(ClaimKind.MONEY,
                decodedSettlement.overflowClaim().orElseThrow().kind());
    }

    @Test
    void schemaThreeCannotEraseInternalEscrowModeEvidence() {
        ProtectedCashRedemptionTestFixtures.ProductionScenario baseline =
                ProtectedCashRedemptionTestFixtures.productionScenario();
        ProtectedCashRedemptionReservation internal =
                ProtectedCashRedemptionFactory.walletReservation(
                        ProtectedCashRedemptionTestFixtures.PLAYER_ID,
                        UUID.fromString(
                                "a1000000-0000-0000-0000-000000000002"),
                        "protected.cash.internal.legacy.rejected",
                        4L,
                        ProtectedCashRedemptionTestFixtures
                                .WALLET_BALANCE_LIMIT,
                        ProtectedCashRedemptionTestFixtures.plan(),
                        baseline.before(), CashDepositMode.INTERNAL_ESCROW,
                        ProtectedCashRedemptionTestFixtures.HELD_AT);

        assertThrows(IllegalArgumentException.class,
                () -> ProtectedCashRedemptionReservationCodec.decode(
                        legacyReservationBytes(internal)));
    }

    @Test
    void constructorCanonicalizesAllBatchAndLotOrdering() {
        ProtectedCashRedemptionReservation valid =
                ProtectedCashRedemptionTestFixtures.reservation();
        List<CustodyMutation> custody = new ArrayList<>(
                valid.custodyReservations());
        List<ProtectedMintJournalEvent> mints = new ArrayList<>(
                valid.mintReservations());
        Collections.reverse(custody);
        Collections.reverse(mints);
        ProtectedCashRedemptionReservation reordered =
                new ProtectedCashRedemptionReservation(valid.reservationId(),
                        valid.playerId(), valid.destinationAccount(),
                        valid.walletBalanceLimitMinorUnits(),
                        valid.inventoryBeforeHash(),
                        valid.plan(), valid.heldTransaction(), custody, mints);

        assertEquals(valid, reordered);
        assertArrayEquals(
                ProtectedCashRedemptionReservationCodec.encode(valid),
                ProtectedCashRedemptionReservationCodec.encode(reordered));

        ProtectedCashRedemptionSettlement settlement =
                ProtectedCashRedemptionTestFixtures.settlement();
        List<CustodyMutation> consumes = new ArrayList<>(
                settlement.custodyConsumptions());
        List<ProtectedMintJournalEvent> commits = new ArrayList<>(
                settlement.mintCommits());
        Collections.reverse(consumes);
        Collections.reverse(commits);
        ProtectedCashRedemptionSettlement reorderedSettlement =
                new ProtectedCashRedemptionSettlement(
                        settlement.reservation(),
                        settlement.completedTransaction(),
                        settlement.inventoryMutation(), consumes, commits,
                        settlement.destinationAccount(),
                        settlement.walletBalanceBeforeMinorUnits(),
                        settlement.walletReservedBeforeMinorUnits(),
                        settlement.overflowClaim(),
                        settlement.ledgerTransaction());

        assertEquals(settlement, reorderedSettlement);
        assertArrayEquals(
                ProtectedCashRedemptionSettlementCodec.encode(settlement),
                ProtectedCashRedemptionSettlementCodec.encode(
                        reorderedSettlement));
    }

    @Test
    void codecsRejectTamperingTruncationTrailingDataAndBounds() {
        byte[] reservation = ProtectedCashRedemptionReservationCodec.encode(
                ProtectedCashRedemptionTestFixtures.reservation());
        byte[] badReservationMagic = reservation.clone();
        badReservationMagic[0] ^= 1;
        assertThrows(IllegalArgumentException.class,
                () -> ProtectedCashRedemptionReservationCodec.decode(
                        badReservationMagic));
        byte[] newerReservation = reservation.clone();
        ByteBuffer.wrap(newerReservation).putInt(Integer.BYTES,
                ProtectedCashRedemptionReservationCodec.CURRENT_SCHEMA + 1);
        assertThrows(IllegalStateException.class,
                () -> ProtectedCashRedemptionReservationCodec.decode(
                        newerReservation));
        byte[] changedDestination = reservation.clone();
        ByteBuffer.wrap(changedDestination).putInt(
                Integer.BYTES * 2 + Long.BYTES * 4,
                LedgerAccountType.ADMIN_SINK.ordinal());
        assertThrows(IllegalArgumentException.class,
                () -> ProtectedCashRedemptionReservationCodec.decode(
                        changedDestination));
        assertThrows(IllegalArgumentException.class,
                () -> ProtectedCashRedemptionReservationCodec.decode(
                        Arrays.copyOf(reservation, reservation.length - 1)));
        assertThrows(IllegalArgumentException.class,
                () -> ProtectedCashRedemptionReservationCodec.decode(
                        Arrays.copyOf(reservation, reservation.length + 1)));
        assertThrows(IllegalArgumentException.class,
                () -> ProtectedCashRedemptionReservationCodec.decode(
                        new byte[ProtectedCashRedemptionReservationCodec
                                .MAX_ENCODED_BYTES + 1]));

        byte[] settlement = ProtectedCashRedemptionSettlementCodec.encode(
                ProtectedCashRedemptionTestFixtures.settlement());
        byte[] badSettlementMagic = settlement.clone();
        badSettlementMagic[0] ^= 1;
        assertThrows(IllegalArgumentException.class,
                () -> ProtectedCashRedemptionSettlementCodec.decode(
                        badSettlementMagic));
        byte[] newerSettlement = settlement.clone();
        newerSettlement[7] = 3;
        assertThrows(IllegalStateException.class,
                () -> ProtectedCashRedemptionSettlementCodec.decode(
                        newerSettlement));
        assertThrows(IllegalArgumentException.class,
                () -> ProtectedCashRedemptionSettlementCodec.decode(
                        Arrays.copyOf(settlement, settlement.length - 1)));
        assertThrows(IllegalArgumentException.class,
                () -> ProtectedCashRedemptionSettlementCodec.decode(
                        Arrays.copyOf(settlement, settlement.length + 1)));
        assertThrows(IllegalArgumentException.class,
                () -> ProtectedCashRedemptionSettlementCodec.decode(
                        new byte[ProtectedCashRedemptionSettlementCodec
                                .MAX_ENCODED_BYTES + 1]));

        byte[] cancellation = ProtectedCashRedemptionCancellationCodec.encode(
                ProtectedCashRedemptionTestFixtures.cancellation());
        byte[] badCancellationMagic = cancellation.clone();
        badCancellationMagic[0] ^= 1;
        assertThrows(IllegalArgumentException.class,
                () -> ProtectedCashRedemptionCancellationCodec.decode(
                        badCancellationMagic));
        byte[] newerCancellation = cancellation.clone();
        newerCancellation[7] = 2;
        assertThrows(IllegalStateException.class,
                () -> ProtectedCashRedemptionCancellationCodec.decode(
                        newerCancellation));
        assertThrows(IllegalArgumentException.class,
                () -> ProtectedCashRedemptionCancellationCodec.decode(
                        Arrays.copyOf(cancellation,
                                cancellation.length - 1)));
        assertThrows(IllegalArgumentException.class,
                () -> ProtectedCashRedemptionCancellationCodec.decode(
                        Arrays.copyOf(cancellation,
                                cancellation.length + 1)));
    }

    @Test
    void duplicateMintEventsAndPartialPlansFailClosed() {
        ProtectedCashRedemptionReservation valid =
                ProtectedCashRedemptionTestFixtures.reservation();
        ProtectedMintJournalEvent first = valid.mintReservations().get(0);
        assertThrows(IllegalArgumentException.class,
                () -> new ProtectedCashRedemptionReservation(
                        valid.reservationId(), valid.playerId(),
                        valid.destinationAccount(),
                        valid.walletBalanceLimitMinorUnits(),
                        valid.inventoryBeforeHash(), valid.plan(),
                        valid.heldTransaction(), valid.custodyReservations(),
                        List.of(first, first)));

        InternalBillInventoryPlanner.Portion portion =
                valid.plan().portions().get(0);
        InternalBillInventoryPlanner.ExactPlan partial =
                new InternalBillInventoryPlanner.ExactPlan(
                        InternalBillInventoryPlanner.PlanStatus.SUCCESS,
                        portion.valueMinorUnits(), portion.valueMinorUnits(),
                        InternalBillAuthorityRouter.Authority.PROTECTED,
                        List.of(portion));
        assertThrows(IllegalArgumentException.class, () ->
                new ProtectedCashRedemptionReservation(
                        ProtectedCashRedemptionReservation.reservationId(
                                valid.playerId(), valid.destinationAccount(),
                                valid.walletBalanceLimitMinorUnits(),
                                valid.inventoryBeforeHash(),
                                valid.heldTransaction(), partial),
                        valid.playerId(), valid.destinationAccount(),
                        valid.walletBalanceLimitMinorUnits(),
                        valid.inventoryBeforeHash(), partial,
                        valid.heldTransaction(),
                        valid.custodyReservations(), valid.mintReservations()));

        InternalBillInventoryPlanner.ExactPlan failed =
                new InternalBillInventoryPlanner.ExactPlan(
                        InternalBillInventoryPlanner.PlanStatus
                                .NO_EXACT_SELECTION,
                        valid.amountMinorUnits(), 0L,
                        InternalBillAuthorityRouter.Authority.NONE, List.of());
        assertThrows(IllegalArgumentException.class, () ->
                new ProtectedCashRedemptionReservation(
                        valid.reservationId(), valid.playerId(),
                        valid.destinationAccount(),
                        valid.walletBalanceLimitMinorUnits(),
                        valid.inventoryBeforeHash(), failed,
                        valid.heldTransaction(),
                        valid.custodyReservations(), valid.mintReservations()));
    }

    @Test
    void wrongSlotsSnapshotsCountsAndAuthoritiesFailClosed() {
        ProtectedCashRedemptionReservation valid =
                ProtectedCashRedemptionTestFixtures.reservation();
        List<InternalBillInventoryPlanner.Portion> movedPortions =
                new ArrayList<>(valid.plan().portions());
        InternalBillInventoryPlanner.Portion original = movedPortions.get(0);
        movedPortions.set(0, new InternalBillInventoryPlanner.Portion(
                new InternalBillInventoryPlanner.SlotIdentity(
                        InternalBillInventoryPlanner.Container.MAIN, 2),
                original.authority(), original.mintId(),
                original.denominationMinorUnits(), original.authorizedCount(),
                original.originalStackCount(), original.selectedCount(),
                original.exactStackSnapshot()));
        InternalBillInventoryPlanner.ExactPlan moved = exactPlan(movedPortions);
        assertThrows(IllegalArgumentException.class, () ->
                new ProtectedCashRedemptionReservation(
                        ProtectedCashRedemptionReservation.reservationId(
                                valid.playerId(), valid.destinationAccount(),
                                valid.walletBalanceLimitMinorUnits(),
                                valid.inventoryBeforeHash(),
                                valid.heldTransaction(), moved),
                        valid.playerId(), valid.destinationAccount(),
                        valid.walletBalanceLimitMinorUnits(),
                        valid.inventoryBeforeHash(), moved,
                        valid.heldTransaction(),
                        valid.custodyReservations(), valid.mintReservations()));

        InternalBillInventoryPlanner.Portion wrongSnapshotCount =
                new InternalBillInventoryPlanner.Portion(original.slot(),
                        original.authority(), original.mintId(),
                        original.denominationMinorUnits(),
                        original.authorizedCount(),
                        original.originalStackCount() + 1,
                        original.selectedCount(),
                        original.exactStackSnapshot());
        List<InternalBillInventoryPlanner.Portion> wrongSnapshots =
                new ArrayList<>(valid.plan().portions());
        wrongSnapshots.set(0, wrongSnapshotCount);
        assertThrows(IllegalArgumentException.class, () ->
                ProtectedCashRedemptionReservation.reservationId(
                        valid.playerId(), valid.destinationAccount(),
                        valid.walletBalanceLimitMinorUnits(),
                        valid.inventoryBeforeHash(),
                        valid.heldTransaction(),
                        exactPlan(wrongSnapshots)));

        InternalBillInventoryPlanner.Portion legacy =
                new InternalBillInventoryPlanner.Portion(original.slot(),
                        InternalBillAuthorityRouter.Authority.LEGACY,
                        original.mintId(), original.denominationMinorUnits(),
                        original.authorizedCount(),
                        original.originalStackCount(), original.selectedCount(),
                        original.exactStackSnapshot());
        InternalBillInventoryPlanner.ExactPlan legacyPlan =
                new InternalBillInventoryPlanner.ExactPlan(
                        InternalBillInventoryPlanner.PlanStatus.SUCCESS,
                        legacy.valueMinorUnits(), legacy.valueMinorUnits(),
                        InternalBillAuthorityRouter.Authority.LEGACY,
                        List.of(legacy));
        assertThrows(IllegalArgumentException.class, () ->
                ProtectedCashRedemptionReservation.reservationId(
                        valid.playerId(), valid.destinationAccount(),
                        valid.walletBalanceLimitMinorUnits(),
                        valid.inventoryBeforeHash(),
                        valid.heldTransaction(), legacyPlan));
    }

    @Test
    void copiedMintNbtOnAnotherRegistryItemFailsClosed() {
        ProtectedCashRedemptionReservation valid =
                ProtectedCashRedemptionTestFixtures.reservation();
        InternalBillInventoryPlanner.Portion original =
                valid.plan().portions().get(0);
        ItemStack protectedStack = ItemStackSnapshotCodec.decode(
                original.exactStackSnapshot());
        ItemStack forged = new ItemStack(Items.PAPER,
                protectedStack.getCount());
        forged.setTag(protectedStack.getTag().copy());
        InternalBillInventoryPlanner.Portion forgedPortion =
                new InternalBillInventoryPlanner.Portion(original.slot(),
                        original.authority(), original.mintId(),
                        original.denominationMinorUnits(),
                        original.authorizedCount(),
                        original.originalStackCount(),
                        original.selectedCount(),
                        ItemStackSnapshotCodec.encode(forged));
        List<InternalBillInventoryPlanner.Portion> portions =
                new ArrayList<>(valid.plan().portions());
        portions.set(0, forgedPortion);

        assertThrows(IllegalArgumentException.class, () ->
                ProtectedCashRedemptionReservation.reservationId(
                        valid.playerId(), valid.destinationAccount(),
                        valid.walletBalanceLimitMinorUnits(),
                        valid.inventoryBeforeHash(),
                        valid.heldTransaction(), exactPlan(portions)));
    }

    @Test
    void durableInventoryReceiptRejectsWrongRemovalAndAfterSnapshot() {
        ProtectedCashRedemptionSettlement valid =
                ProtectedCashRedemptionTestFixtures.settlement();
        List<ProtectedCashRedemptionSettlement.SlotMutation> mutations =
                new ArrayList<>(valid.inventoryMutation().mutations());
        ProtectedCashRedemptionSettlement.SlotMutation first = mutations.get(0);
        mutations.set(0, new ProtectedCashRedemptionSettlement.SlotMutation(
                first.slot(), first.removedCount() + 1,
                first.beforeSnapshot(), first.afterSnapshot()));
        ProtectedCashRedemptionSettlement.InventoryMutationReceipt wrongCount =
                receipt(valid, mutations, ProtectedCashRedemptionTestFixtures
                        .INVENTORY_AT);
        assertThrows(IllegalArgumentException.class, () ->
                replaceInventory(valid, wrongCount));

        mutations = new ArrayList<>(valid.inventoryMutation().mutations());
        first = mutations.get(0);
        mutations.set(0, new ProtectedCashRedemptionSettlement.SlotMutation(
                first.slot(), first.removedCount(), first.beforeSnapshot(),
                first.beforeSnapshot()));
        ProtectedCashRedemptionSettlement.InventoryMutationReceipt wrongAfter =
                receipt(valid, mutations, ProtectedCashRedemptionTestFixtures
                        .INVENTORY_AT);
        assertThrows(IllegalArgumentException.class, () ->
                replaceInventory(valid, wrongAfter));

        byte[] badToken = valid.inventoryMutation()
                .mutationTokenDigest();
        badToken[0] ^= 1;
        ProtectedCashRedemptionSettlement.InventoryMutationReceipt source =
                valid.inventoryMutation();
        assertThrows(IllegalArgumentException.class, () ->
                new ProtectedCashRedemptionSettlement.InventoryMutationReceipt(
                        source.receiptId(), source.playerId(),
                        source.transactionId(), source.reservationId(),
                        source.requestKey(), source.mutations(),
                        source.beforeInventoryHash(), source.afterInventoryHash(),
                        badToken, source.occurredAt()));
    }

    @Test
    void ledgerMustBalanceExactAmountIntoTheReservedDestination() {
        ProtectedCashRedemptionSettlement valid =
                ProtectedCashRedemptionTestFixtures.settlement();
        long wrongAmount = valid.amountMinorUnits() - 1L;
        LedgerTransaction wrongAmountLedger = new LedgerTransaction(
                valid.transactionId(),
                ProtectedCashRedemptionSettlement.ledgerIdempotencyKey(
                        valid.transactionId(),
                        valid.reservation().destinationAccount()),
                ProtectedCashRedemptionSettlement.LEDGER_REASON,
                List.of(
                        new LedgerLeg(LedgerAccountId.system(
                                LedgerAccountType
                                        .PROTECTED_CURRENCY_OUTSTANDING),
                                -wrongAmount),
                        new LedgerLeg(valid.destinationAccount(), wrongAmount)));
        assertThrows(IllegalArgumentException.class, () ->
                new ProtectedCashRedemptionSettlement(valid.reservation(),
                        valid.completedTransaction(), valid.inventoryMutation(),
                        valid.custodyConsumptions(), valid.mintCommits(),
                        valid.destinationAccount(),
                        valid.walletBalanceBeforeMinorUnits(),
                        valid.walletReservedBeforeMinorUnits(),
                        valid.overflowClaim(), wrongAmountLedger));

        LedgerAccountId adminSink = LedgerAccountId.system(
                LedgerAccountType.ADMIN_SINK);
        LedgerTransaction disallowedLedger = new LedgerTransaction(
                valid.transactionId(),
                ProtectedCashRedemptionSettlement.ledgerIdempotencyKey(
                        valid.transactionId(),
                        valid.reservation().destinationAccount()),
                ProtectedCashRedemptionSettlement.LEDGER_REASON,
                List.of(
                        new LedgerLeg(LedgerAccountId.system(
                                LedgerAccountType
                                        .PROTECTED_CURRENCY_OUTSTANDING),
                                -valid.amountMinorUnits()),
                        new LedgerLeg(adminSink, valid.amountMinorUnits())));
        assertThrows(IllegalArgumentException.class, () ->
                new ProtectedCashRedemptionSettlement(valid.reservation(),
                        valid.completedTransaction(), valid.inventoryMutation(),
                        valid.custodyConsumptions(), valid.mintCommits(),
                        adminSink, valid.walletBalanceBeforeMinorUnits(),
                        valid.walletReservedBeforeMinorUnits(),
                        valid.overflowClaim(), disallowedLedger));

        assertEquals(LedgerAccountType.PLAYER_WALLET,
                valid.destinationAccount().type());
    }

    @Test
    void walletHeadroomHandlesNegativeAndExtremeBalancesExactly() {
        ProtectedCashRedemptionSettlement negative =
                ProtectedCashRedemptionTestFixtures.settlement(
                        10_000L, -500L, 100L);
        assertEquals(800L, negative.walletCreditMinorUnits());
        assertEquals(0L, negative.overflowClaimMinorUnits());

        ProtectedCashRedemptionSettlement minimum =
                ProtectedCashRedemptionTestFixtures.settlement(
                        Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE);
        assertEquals(800L, minimum.walletCreditMinorUnits());
        assertEquals(0L, minimum.overflowClaimMinorUnits());

        ProtectedCashRedemptionSettlement split =
                ProtectedCashRedemptionTestFixtures.settlement(
                        Long.MAX_VALUE, Long.MAX_VALUE - 100L, 50L);
        assertEquals(50L, split.walletCreditMinorUnits());
        assertEquals(750L, split.overflowClaimMinorUnits());
        assertEquals(750L, split.overflowClaim().orElseThrow()
                .remainingUnits());

        assertThrows(IllegalArgumentException.class, () ->
                ProtectedCashRedemptionTestFixtures.settlement(
                        Long.MAX_VALUE, Long.MIN_VALUE, -1L));
    }

    @Test
    void identityBoundsAndArithmeticOverflowFailClosed() {
        ProtectedCashRedemptionSettlement valid =
                ProtectedCashRedemptionTestFixtures.settlement();
        ProtectedCashRedemptionSettlement.InventoryMutationReceipt wrongIdentity =
                ProtectedCashRedemptionSettlement.InventoryMutationReceipt.create(
                        valid.reservation().playerId(), UUID.randomUUID(),
                        valid.reservation().reservationId(),
                        valid.inventoryMutation().requestKey(),
                        valid.inventoryMutation().mutations(),
                        valid.inventoryMutation().beforeInventoryHash(),
                        valid.inventoryMutation().afterInventoryHash(),
                        valid.inventoryMutation().occurredAt());
        assertThrows(IllegalArgumentException.class, () ->
                replaceInventory(valid, wrongIdentity));

        InternalBillInventoryPlanner.Portion portion =
                valid.reservation().plan().portions().get(0);
        List<InternalBillInventoryPlanner.Portion> tooMany =
                Collections.nCopies(
                        ProtectedCashRedemptionReservation.MAX_PORTIONS + 1,
                        portion);
        long repeatedValue = Math.multiplyExact(portion.valueMinorUnits(),
                (long) tooMany.size());
        InternalBillInventoryPlanner.ExactPlan oversizedPlan =
                new InternalBillInventoryPlanner.ExactPlan(
                        InternalBillInventoryPlanner.PlanStatus.SUCCESS,
                        repeatedValue, repeatedValue,
                        InternalBillAuthorityRouter.Authority.PROTECTED,
                        tooMany);
        assertThrows(IllegalArgumentException.class, () ->
                ProtectedCashRedemptionReservation.reservationId(
                        valid.reservation().playerId(),
                        valid.reservation().destinationAccount(),
                        valid.reservation().walletBalanceLimitMinorUnits(),
                        valid.reservation().inventoryBeforeHash(),
                        valid.reservation().heldTransaction(), oversizedPlan));

        assertThrows(ArithmeticException.class, () -> {
            InternalBillInventoryPlanner.Portion overflowing =
                    new InternalBillInventoryPlanner.Portion(portion.slot(),
                            portion.authority(), portion.mintId(),
                            Long.MAX_VALUE, portion.authorizedCount(),
                            portion.originalStackCount(), 2,
                            ProtectedCashRedemptionTestFixtures.billSnapshot(
                                    UUID.fromString(portion.mintId()),
                                    Long.MAX_VALUE, portion.authorizedCount(),
                                    portion.originalStackCount()));
            new InternalBillInventoryPlanner.ExactPlan(
                    InternalBillInventoryPlanner.PlanStatus.SUCCESS,
                    Long.MAX_VALUE, Long.MAX_VALUE,
                    InternalBillAuthorityRouter.Authority.PROTECTED,
                    List.of(overflowing));
        });
    }

    private static InternalBillInventoryPlanner.ExactPlan exactPlan(
            List<InternalBillInventoryPlanner.Portion> portions
    ) {
        long amount = portions.stream()
                .mapToLong(InternalBillInventoryPlanner.Portion::valueMinorUnits)
                .reduce(0L, Math::addExact);
        return new InternalBillInventoryPlanner.ExactPlan(
                InternalBillInventoryPlanner.PlanStatus.SUCCESS,
                amount, amount,
                InternalBillAuthorityRouter.Authority.PROTECTED, portions);
    }

    private static ProtectedCashRedemptionSettlement.InventoryMutationReceipt
    receipt(ProtectedCashRedemptionSettlement settlement,
            List<ProtectedCashRedemptionSettlement.SlotMutation> mutations,
            java.time.Instant occurredAt) {
        return ProtectedCashRedemptionSettlement.InventoryMutationReceipt.create(
                settlement.reservation().playerId(), settlement.transactionId(),
                settlement.reservation().reservationId(),
                settlement.inventoryMutation().requestKey(), mutations,
                settlement.inventoryMutation().beforeInventoryHash(),
                settlement.inventoryMutation().afterInventoryHash(), occurredAt);
    }

    private static ProtectedCashRedemptionSettlement replaceInventory(
            ProtectedCashRedemptionSettlement settlement,
            ProtectedCashRedemptionSettlement.InventoryMutationReceipt receipt
    ) {
        return new ProtectedCashRedemptionSettlement(settlement.reservation(),
                settlement.completedTransaction(), receipt,
                settlement.custodyConsumptions(), settlement.mintCommits(),
                settlement.destinationAccount(),
                settlement.walletBalanceBeforeMinorUnits(),
                settlement.walletReservedBeforeMinorUnits(),
                settlement.overflowClaim(), settlement.ledgerTransaction());
    }

    private static ProtectedCashRedemptionReservation legacyReservation(
            ProtectedCashRedemptionReservation current
    ) {
        List<EscrowAssetLot> assets = new ArrayList<>();
        for (int index = 0; index < current.plan().portions().size();
             index++) {
            EscrowAssetLot asset = current.heldTransaction().assetLots()
                    .get(index);
            assets.add(new EscrowAssetLot(asset.lotId(), asset.type(),
                    asset.protectionLevel(), asset.source(),
                    asset.destination(), asset.quantity(), asset.money(),
                    asset.serializedPayload(),
                    ProtectedCashRedemptionReservation
                            .legacyAssetAttributes(
                                    current.plan().portions().get(index),
                                    current.destinationAccount(),
                                    current.walletBalanceLimitMinorUnits(),
                                    current.inventoryBeforeHash())));
        }
        EscrowTransaction transaction = current.heldTransaction();
        EscrowTransaction held = new EscrowTransaction(
                transaction.transactionId(),
                transaction.parentTransactionId(), transaction.requestKey(),
                transaction.operation(), transaction.state(),
                transaction.participants(), assets, transaction.timestamps(),
                transaction.revision(), transaction.configRevision(),
                transaction.lastError(), transaction.retryMetadata(),
                transaction.shopReference());
        UUID reservationId = ProtectedCashRedemptionSupport
                .legacyReservationId(current.playerId(),
                        current.destinationAccount(),
                        current.walletBalanceLimitMinorUnits(),
                        current.inventoryBeforeHash(), held, current.plan());
        return new ProtectedCashRedemptionReservation(reservationId,
                current.playerId(), current.destinationAccount(),
                current.walletBalanceLimitMinorUnits(),
                CashDepositMode.PUBLIC_WALLET,
                current.inventoryBeforeHash(), current.plan(), held,
                current.custodyReservations(), current.mintReservations());
    }

    private static byte[] legacyReservationBytes(
            ProtectedCashRedemptionReservation reservation
    ) {
        byte[] current = ProtectedCashRedemptionReservationCodec.encode(
                reservation);
        int ownerLength = ByteBuffer.wrap(current).getInt(44);
        int modeOffset = 56 + ownerLength;
        byte[] legacy = new byte[current.length - Integer.BYTES];
        System.arraycopy(current, 0, legacy, 0, modeOffset);
        System.arraycopy(current, modeOffset + Integer.BYTES, legacy,
                modeOffset, legacy.length - modeOffset);
        ByteBuffer.wrap(legacy).putInt(Integer.BYTES, 3);
        return legacy;
    }

    private static byte[] replaceNestedReservation(
            byte[] current,
            byte[] legacyReservation
    ) {
        int currentReservationLength = ByteBuffer.wrap(current).getInt(8);
        int currentTailOffset = 12 + currentReservationLength;
        byte[] legacy = new byte[current.length
                - currentReservationLength + legacyReservation.length];
        System.arraycopy(current, 0, legacy, 0, 8);
        ByteBuffer.wrap(legacy).putInt(8, legacyReservation.length);
        System.arraycopy(legacyReservation, 0, legacy, 12,
                legacyReservation.length);
        System.arraycopy(current, currentTailOffset, legacy,
                12 + legacyReservation.length,
                current.length - currentTailOffset);
        return legacy;
    }
}
