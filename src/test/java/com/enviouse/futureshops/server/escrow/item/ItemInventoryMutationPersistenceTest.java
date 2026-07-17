package com.enviouse.futureshops.server.escrow.item;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterInspectionStatus;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemInventoryMutationPersistenceTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void tokenReceiptAndSnapshotRoundTripWithInspectionSemantics() {
        ItemInventoryState before = stateWith(
                new ItemStack(Items.EMERALD, 7));
        ItemInventoryMutationPlan plan = ItemInventoryBatchPlanner.plan(
                before, List.of(ItemInventoryBatchEntry.extract(
                        UUID.randomUUID(),
                        ItemInputMatcher.itemOnly("minecraft:emerald"), 4)));
        UUID playerId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        ItemInventoryMutationToken token =
                ItemInventoryMutationToken.create(playerId, transactionId,
                        requestId, plan);
        ItemInventoryMutationToken duplicate =
                ItemInventoryMutationToken.create(playerId, transactionId,
                        requestId, plan);
        assertEquals(token, duplicate);
        assertEquals(token, ItemInventoryMutationTokenCodec.decode(
                ItemInventoryMutationTokenCodec.encode(token)));

        ItemInventoryMutationReceipt receipt =
                ItemInventoryMutationReceipt.create(token, plan,
                        Instant.parse("2026-07-19T12:00:00Z"));
        assertEquals(ItemInventoryMutationReceiptCodec.projectedEncodedSize(
                        plan.allocations(), plan.changes().size()),
                ItemInventoryMutationReceiptCodec.encode(receipt).length);
        assertEquals(receipt,
                ItemInventoryMutationReceiptCodec.decode(
                        ItemInventoryMutationReceiptCodec.encode(receipt)));

        ItemInventoryReceiptRepository repository =
                new ItemInventoryReceiptRepository();
        assertEquals(CustodyAdapterInspectionStatus.NOT_APPLIED,
                repository.inspect(token, before).status());
        assertEquals(ItemInventoryReceiptAppendResult.APPLIED,
                repository.append(receipt));
        assertEquals(ItemInventoryReceiptAppendResult.REPLAYED,
                repository.append(receipt));
        assertEquals(CustodyAdapterInspectionStatus.APPLIED,
                repository.inspect(token, plan.after()).status());
        assertEquals(CustodyAdapterInspectionStatus.UNKNOWN,
                repository.inspect(token, before).status());

        byte[] snapshotBytes = ItemInventoryReceiptSnapshotCodec.encode(
                repository.snapshot());
        ItemInventoryReceiptSnapshot restored =
                ItemInventoryReceiptSnapshotCodec.decode(snapshotBytes);
        ItemInventoryReceiptRepository restoredRepository =
                new ItemInventoryReceiptRepository(restored);
        assertEquals(receipt,
                restoredRepository.findFullReceipt(requestId).orElseThrow());
        assertEquals(repository.snapshot(), restoredRepository.snapshot());

        byte[] corrupted = snapshotBytes.clone();
        corrupted[20] ^= 1;
        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryReceiptSnapshotCodec.decode(corrupted));
    }

    @Test
    void conflictingRequestEvidenceFailsClosed() {
        UUID playerId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        ItemInventoryMutationPlan firstPlan = ItemInventoryBatchPlanner.plan(
                stateWith(new ItemStack(Items.EMERALD, 8)),
                List.of(ItemInventoryBatchEntry.extract(UUID.randomUUID(),
                        ItemInputMatcher.itemOnly("minecraft:emerald"), 2)));
        ItemInventoryMutationPlan secondPlan = ItemInventoryBatchPlanner.plan(
                stateWith(new ItemStack(Items.EMERALD, 9)),
                List.of(ItemInventoryBatchEntry.extract(UUID.randomUUID(),
                        ItemInputMatcher.itemOnly("minecraft:emerald"), 3)));
        ItemInventoryMutationReceipt first = receipt(playerId,
                transactionId, requestId, firstPlan,
                Instant.parse("2026-07-19T13:00:00Z"));
        ItemInventoryMutationReceipt second = receipt(playerId,
                transactionId, requestId, secondPlan,
                Instant.parse("2026-07-19T13:00:01Z"));
        ItemInventoryReceiptRepository repository =
                new ItemInventoryReceiptRepository();
        repository.append(first);

        assertThrows(IllegalStateException.class,
                () -> repository.append(second));
        assertEquals(CustodyAdapterInspectionStatus.UNKNOWN,
                repository.inspect(second.token(),
                        secondPlan.after()).status());
    }

    @Test
    void revisionOverflowDoesNotPartiallyAppendAReceipt() {
        ItemInventoryMutationPlan firstPlan = ItemInventoryBatchPlanner.plan(
                stateWith(new ItemStack(Items.EMERALD, 8)),
                List.of(ItemInventoryBatchEntry.extract(UUID.randomUUID(),
                        ItemInputMatcher.itemOnly("minecraft:emerald"), 1)));
        ItemInventoryMutationReceipt first = receipt(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), firstPlan,
                Instant.parse("2026-07-19T13:30:00Z"));
        ItemInventoryReceiptRepository repository =
                new ItemInventoryReceiptRepository(
                        new ItemInventoryReceiptSnapshot(Long.MAX_VALUE,
                                Map.of(first.token().requestId(), first)));
        ItemInventoryMutationPlan secondPlan = ItemInventoryBatchPlanner.plan(
                stateWith(new ItemStack(Items.DIAMOND, 8)),
                List.of(ItemInventoryBatchEntry.extract(UUID.randomUUID(),
                        ItemInputMatcher.itemOnly("minecraft:diamond"), 1)));
        ItemInventoryMutationReceipt second = receipt(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), secondPlan,
                Instant.parse("2026-07-19T13:30:01Z"));

        assertThrows(ArithmeticException.class,
                () -> repository.append(second));
        assertEquals(new ItemInventoryReceiptSnapshot(Long.MAX_VALUE,
                        Map.of(first.token().requestId(), first)),
                repository.snapshot());
    }

    @Test
    void unrelatedSlotChangesDoNotEraseMutationEvidence() {
        ItemInventoryState before = stateWith(new ItemStack(Items.EMERALD, 7));
        ItemInventoryMutationPlan plan = ItemInventoryBatchPlanner.plan(
                before, List.of(ItemInventoryBatchEntry.extract(
                        UUID.randomUUID(), ItemInputMatcher.itemOnly(
                                "minecraft:emerald"), 4)));
        ItemInventoryMutationToken token = ItemInventoryMutationToken.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), plan);
        ItemInventoryMutationReceipt receipt = ItemInventoryMutationReceipt.create(
                token, plan, Instant.parse("2026-07-19T12:30:00Z"));
        ItemInventoryState beforeWithUnrelatedChange = stateWith(
                new ItemStack(Items.EMERALD, 7), new ItemStack(Items.DIAMOND, 1));
        ItemInventoryState afterWithUnrelatedChange = stateWith(
                new ItemStack(Items.EMERALD, 3), new ItemStack(Items.DIAMOND, 1));
        ItemInventoryReceiptRepository empty = new ItemInventoryReceiptRepository();
        ItemInventoryReceiptRepository applied = new ItemInventoryReceiptRepository();
        applied.append(receipt);

        assertEquals(CustodyAdapterInspectionStatus.NOT_APPLIED,
                empty.inspect(token, beforeWithUnrelatedChange).status());
        assertEquals(CustodyAdapterInspectionStatus.APPLIED,
                applied.inspect(token, afterWithUnrelatedChange).status());
    }

    @Test
    void tokenAndReceiptCodecsRejectTrailingTruncatedAndOversizedData() {
        ItemInventoryMutationPlan plan = ItemInventoryBatchPlanner.plan(
                stateWith(new ItemStack(Items.DIAMOND, 5)),
                List.of(ItemInventoryBatchEntry.extract(UUID.randomUUID(),
                        ItemInputMatcher.itemOnly("minecraft:diamond"), 1)));
        ItemInventoryMutationToken token =
                ItemInventoryMutationToken.create(UUID.randomUUID(),
                        UUID.randomUUID(), UUID.randomUUID(), plan);
        ItemInventoryMutationReceipt receipt =
                ItemInventoryMutationReceipt.create(token, plan,
                        Instant.parse("2026-07-19T14:00:00Z"));
        byte[] tokenBytes = ItemInventoryMutationTokenCodec.encode(token);
        byte[] receiptBytes = ItemInventoryMutationReceiptCodec.encode(receipt);
        byte[] corruptToken = tokenBytes.clone();
        corruptToken[corruptToken.length / 2] ^= 1;
        byte[] corruptReceipt = receiptBytes.clone();
        corruptReceipt[corruptReceipt.length / 2] ^= 1;

        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryMutationTokenCodec.decode(corruptToken));
        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryMutationTokenCodec.decode(
                        Arrays.copyOf(tokenBytes, tokenBytes.length - 1)));
        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryMutationTokenCodec.decode(
                        Arrays.copyOf(tokenBytes, tokenBytes.length + 1)));
        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryMutationTokenCodec.decode(
                        new byte[ItemInventoryMutationTokenCodec
                                .MAX_ENCODED_BYTES + 1]));
        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryMutationReceiptCodec.decode(
                        corruptReceipt));
        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryMutationReceiptCodec.decode(
                        Arrays.copyOf(receiptBytes,
                                receiptBytes.length - 1)));
        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryMutationReceiptCodec.decode(
                        Arrays.copyOf(receiptBytes,
                                receiptBytes.length + 1)));

        byte[] tokenRoundTrip = ItemInventoryMutationTokenCodec.encode(
                ItemInventoryMutationTokenCodec.decode(tokenBytes));
        byte[] receiptRoundTrip = ItemInventoryMutationReceiptCodec.encode(
                ItemInventoryMutationReceiptCodec.decode(receiptBytes));
        assertArrayEquals(tokenBytes, tokenRoundTrip);
        assertArrayEquals(receiptBytes, receiptRoundTrip);
    }

    private static ItemInventoryMutationReceipt receipt(
            UUID playerId,
            UUID transactionId,
            UUID requestId,
            ItemInventoryMutationPlan plan,
            Instant appliedAt
    ) {
        ItemInventoryMutationToken token =
                ItemInventoryMutationToken.create(playerId, transactionId,
                        requestId, plan);
        return ItemInventoryMutationReceipt.create(token, plan, appliedAt);
    }

    private static ItemInventoryState stateWith(ItemStack first) {
        return stateWith(first, ItemStack.EMPTY);
    }

    private static ItemInventoryState stateWith(
            ItemStack first,
            ItemStack second
    ) {
        List<ItemStack> main = new ArrayList<>();
        main.add(first);
        main.add(second);
        for (int index = 2;
             index < ItemInventorySlot.MAIN_SLOT_COUNT;
             index++) {
            main.add(ItemStack.EMPTY);
        }
        return ItemInventoryState.of(main, ItemStack.EMPTY);
    }
}
