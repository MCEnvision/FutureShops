package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.money.InternalBillInventoryPlanner;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.model.CashDepositMode;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLot;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashInventoryState;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionSettlement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ForeignCashDepositModeCodecTest {
    private static final UUID PLAYER_ID = UUID.fromString(
            "a2000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse(
            "2026-07-17T22:00:00Z");

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void foreignDepositModeRoundTripsAndSelectsTheExactClaimKind() {
        ForeignCashDepositSettlement internal = settlement(
                CashDepositMode.INTERNAL_ESCROW,
                UUID.fromString(
                        "a2000000-0000-0000-0000-000000000002"),
                UUID.fromString(
                        "a2000000-0000-0000-0000-000000000003"));
        ForeignCashDepositSettlement publicDeposit = settlement(
                CashDepositMode.PUBLIC_WALLET,
                UUID.fromString(
                        "a2000000-0000-0000-0000-000000000004"),
                UUID.fromString(
                        "a2000000-0000-0000-0000-000000000005"));

        assertEquals(CashDepositMode.INTERNAL_ESCROW,
                ForeignCashDepositCodec.decodeReservation(
                        ForeignCashDepositCodec.encodeReservation(
                                internal.reservation())).depositMode());
        assertEquals(internal, ForeignCashDepositCodec.decodeSettlement(
                ForeignCashDepositCodec.encodeSettlement(internal)));
        assertEquals(ClaimKind.INTERNAL_ESCROW_MONEY,
                internal.overflowClaim().orElseThrow().kind());
        assertEquals(ClaimKind.MONEY,
                publicDeposit.overflowClaim().orElseThrow().kind());
    }

    @Test
    void schemaOneWalPayloadsDecodeAsPublicWalletDeposits() {
        LegacyTerminals legacy = legacyPublicTerminals();
        byte[] reservationBytes = legacyReservationBytes(
                legacy.reservation());
        byte[] settlementBytes = legacyTerminalBytes(
                ForeignCashDepositCodec.encodeSettlement(
                        legacy.settlement()), reservationBytes);
        byte[] cancellationBytes = legacyTerminalBytes(
                ForeignCashDepositCodec.encodeCancellation(
                        legacy.cancellation()), reservationBytes);

        ForeignCashDepositReservation reservation =
                ForeignCashDepositCodec.decodeReservation(
                        reservationBytes);
        ForeignCashDepositSettlement settlement =
                ForeignCashDepositCodec.decodeSettlement(settlementBytes);
        ForeignCashDepositCancellation cancellation =
                ForeignCashDepositCodec.decodeCancellation(
                        cancellationBytes);

        assertEquals(legacy.reservation(), reservation);
        assertEquals(legacy.settlement(), settlement);
        assertEquals(legacy.cancellation(), cancellation);
        assertEquals(CashDepositMode.PUBLIC_WALLET,
                reservation.depositMode());
        assertEquals(ClaimKind.MONEY,
                settlement.overflowClaim().orElseThrow().kind());
        assertEquals(legacy.reservation().plan(),
                ForeignCashDepositCodec.decodePlan(
                        ForeignCashDepositCodec.encodeLegacyPlanIdentity(
                                legacy.reservation().plan())));
    }

    @Test
    void legacyAndFutureSchemasFailClosedForInvalidModeOrShape() {
        ForeignCashDepositSettlement internal = settlement(
                CashDepositMode.INTERNAL_ESCROW,
                UUID.fromString(
                        "a2000000-0000-0000-0000-000000000006"),
                UUID.fromString(
                        "a2000000-0000-0000-0000-000000000007"));
        byte[] current = ForeignCashDepositCodec.encodeReservation(
                internal.reservation());
        byte[] future = current.clone();
        ByteBuffer.wrap(future).putInt(Integer.BYTES, 3);
        byte[] unsupported = current.clone();
        ByteBuffer.wrap(unsupported).putInt(Integer.BYTES, 0);
        byte[] legacyInternal = legacyReservationBytes(
                internal.reservation());
        byte[] trailingLegacy = Arrays.copyOf(legacyInternal,
                legacyInternal.length + 1);

        assertThrows(IllegalStateException.class,
                () -> ForeignCashDepositCodec.decodeReservation(future));
        assertThrows(IllegalArgumentException.class,
                () -> ForeignCashDepositCodec.decodeReservation(
                        unsupported));
        assertThrows(IllegalArgumentException.class,
                () -> ForeignCashDepositCodec.decodeReservation(
                        legacyInternal));
        assertThrows(IllegalArgumentException.class,
                () -> ForeignCashDepositCodec.decodeReservation(
                        trailingLegacy));
    }

    private static ForeignCashDepositSettlement settlement(
            CashDepositMode mode,
            UUID requestId,
            UUID transactionId
    ) {
        ItemStack beforeStack = new ItemStack(Items.EMERALD, 2);
        ItemStack afterStack = new ItemStack(Items.EMERALD, 1);
        var slot = new InternalBillInventoryPlanner.SlotIdentity(
                InternalBillInventoryPlanner.Container.MAIN, 0);
        var portion = new ForeignCashDepositPlan.Portion(slot,
                String.valueOf(ForgeRegistries.ITEMS.getKey(Items.EMERALD)),
                100L, 2, 1,
                ItemStackSnapshotCodec.encode(beforeStack));
        ForeignCashDepositPlan plan = new ForeignCashDepositPlan(
                "coinmod", "a".repeat(64), 100L, List.of(portion));
        ProtectedCashInventoryState before = inventory(beforeStack);
        ProtectedCashInventoryState after = inventory(afterStack);
        ForeignCashDepositReservation reservation =
                ForeignCashDepositFactory.reservation(requestId, PLAYER_ID,
                        transactionId,
                        "cash.deposit.mode." + mode + "." + requestId,
                        500L, mode, plan, before, NOW);
        var mutation = new ProtectedCashRedemptionSettlement.SlotMutation(
                slot, 1, ItemStackSnapshotCodec.encode(beforeStack),
                ItemStackSnapshotCodec.encode(afterStack));
        var receipt = ProtectedCashRedemptionSettlement
                .InventoryMutationReceipt.create(
                        PLAYER_ID, transactionId,
                        reservation.reservationId(),
                        ForeignCashDepositSettlement
                                .inventoryMutationRequestKey(transactionId),
                        List.of(mutation), before.hash(), after.hash(),
                        NOW.plusSeconds(1));
        return ForeignCashDepositFactory.settlement(reservation, receipt,
                500L, 0L, NOW.plusSeconds(2));
    }

    private static ProtectedCashInventoryState inventory(ItemStack stack) {
        CompoundTag entry = new CompoundTag();
        stack.save(entry);
        entry.putByte("Slot", (byte) 0);
        ListTag inventory = new ListTag();
        inventory.add(entry);
        return ProtectedCashInventoryState.fromPlayerInventoryTag(inventory);
    }

    private static LegacyTerminals legacyPublicTerminals() {
        ForeignCashDepositSettlement current = settlement(
                CashDepositMode.PUBLIC_WALLET,
                UUID.fromString(
                        "a2000000-0000-0000-0000-000000000008"),
                UUID.fromString(
                        "a2000000-0000-0000-0000-000000000009"));
        ForeignCashDepositReservation legacy = legacyReservation(
                current.reservation());
        var baselineReceipt = current.inventoryMutation();
        var receipt = ProtectedCashRedemptionSettlement
                .InventoryMutationReceipt.create(
                        legacy.playerId(), legacy.transactionId(),
                        legacy.reservationId(), baselineReceipt.requestKey(),
                        baselineReceipt.mutations(),
                        baselineReceipt.beforeInventoryHash(),
                        baselineReceipt.afterInventoryHash(),
                        baselineReceipt.occurredAt());
        ForeignCashDepositSettlement settlement =
                ForeignCashDepositFactory.settlement(legacy, receipt,
                        500L, 0L, NOW.plusSeconds(2));
        ForeignCashDepositCancellation cancellation =
                ForeignCashDepositFactory.cancellation(legacy,
                        inventory(new ItemStack(Items.EMERALD, 2)),
                        NOW.plusSeconds(2));
        return new LegacyTerminals(legacy, settlement, cancellation);
    }

    private static ForeignCashDepositReservation legacyReservation(
            ForeignCashDepositReservation current
    ) {
        EscrowTransaction held = withoutDepositMode(
                current.heldTransaction());
        UUID reservationId = ForeignCashDepositReservation
                .legacyReservationId(current.requestId(),
                        current.playerId(), current.destinationAccount(),
                        current.walletBalanceLimitMinorUnits(),
                        current.inventoryBeforeHash(), current.plan(), held);
        return new ForeignCashDepositReservation(reservationId,
                current.requestId(), current.playerId(),
                current.destinationAccount(),
                current.walletBalanceLimitMinorUnits(),
                CashDepositMode.PUBLIC_WALLET,
                current.inventoryBeforeHash(), current.plan(), held,
                current.custodyReservations());
    }

    private static EscrowTransaction withoutDepositMode(
            EscrowTransaction transaction
    ) {
        List<EscrowAssetLot> assets = transaction.assetLots().stream()
                .map(ForeignCashDepositModeCodecTest::withoutDepositMode)
                .toList();
        return new EscrowTransaction(transaction.transactionId(),
                transaction.parentTransactionId(), transaction.requestKey(),
                transaction.operation(), transaction.state(),
                transaction.participants(), assets, transaction.timestamps(),
                transaction.revision(), transaction.configRevision(),
                transaction.lastError(), transaction.retryMetadata(),
                transaction.shopReference());
    }

    private static EscrowAssetLot withoutDepositMode(EscrowAssetLot asset) {
        Map<String, String> attributes = new HashMap<>(asset.attributes());
        attributes.remove("deposit_mode");
        return new EscrowAssetLot(asset.lotId(), asset.type(),
                asset.protectionLevel(), asset.source(), asset.destination(),
                asset.quantity(), asset.money(), asset.serializedPayload(),
                attributes);
    }

    private static byte[] legacyReservationBytes(
            ForeignCashDepositReservation reservation
    ) {
        byte[] current = ForeignCashDepositCodec.encodeReservation(
                reservation);
        int ownerLength = ByteBuffer.wrap(current).getInt(60);
        int modeOffset = 72 + ownerLength;
        byte[] legacy = removeInt(current, modeOffset);
        ByteBuffer bytes = ByteBuffer.wrap(legacy);
        bytes.putInt(Integer.BYTES, 1);
        int planLengthOffset = modeOffset + Integer.BYTES + 32;
        int planOffset = planLengthOffset + Integer.BYTES;
        bytes.putInt(planOffset + Integer.BYTES, 1);
        return legacy;
    }

    private static byte[] legacyTerminalBytes(
            byte[] current,
            byte[] legacyReservation
    ) {
        int currentReservationLength = ByteBuffer.wrap(current).getInt(8);
        int currentTailOffset = 12 + currentReservationLength;
        byte[] legacy = new byte[current.length
                - currentReservationLength + legacyReservation.length];
        System.arraycopy(current, 0, legacy, 0, 8);
        ByteBuffer.wrap(legacy).putInt(Integer.BYTES, 1)
                .putInt(8, legacyReservation.length);
        System.arraycopy(legacyReservation, 0, legacy, 12,
                legacyReservation.length);
        System.arraycopy(current, currentTailOffset, legacy,
                12 + legacyReservation.length,
                current.length - currentTailOffset);
        return legacy;
    }

    private static byte[] removeInt(byte[] value, int offset) {
        byte[] result = new byte[value.length - Integer.BYTES];
        System.arraycopy(value, 0, result, 0, offset);
        System.arraycopy(value, offset + Integer.BYTES, result, offset,
                result.length - offset);
        return result;
    }

    private record LegacyTerminals(
            ForeignCashDepositReservation reservation,
            ForeignCashDepositSettlement settlement,
            ForeignCashDepositCancellation cancellation
    ) {
    }
}
