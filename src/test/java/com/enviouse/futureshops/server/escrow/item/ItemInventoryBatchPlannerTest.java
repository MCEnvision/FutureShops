package com.enviouse.futureshops.server.escrow.item;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemInventoryBatchPlannerTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void repeatedLargeExactPortionsStopAtTheReceiptBudget() {
        ItemStack large = new ItemStack(Items.EMERALD, 64);
        large.getOrCreateTag().putString("evidence one", "x".repeat(40000));
        large.getOrCreateTag().putString("evidence two", "y".repeat(40000));
        List<ItemStack> main = emptyMain();
        main.set(0, large);
        ItemInventoryState state = ItemInventoryState.of(
                main, ItemStack.EMPTY);
        List<ItemInventoryBatchEntry> entries = IntStream.range(0, 64)
                .mapToObj(index -> ItemInventoryBatchEntry.extract(
                        new UUID(2L, index + 1L),
                        ItemInputMatcher.itemOnly("minecraft:emerald"), 1))
                .toList();

        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryBatchPlanner.plan(state, entries));
    }

    @Test
    void repeatedLargeExactTemplatesStopBeforeCandidateReserialization() {
        ItemStack large = new ItemStack(Items.EMERALD, 64);
        large.getOrCreateTag().putString("evidence one", "x".repeat(40000));
        large.getOrCreateTag().putString("evidence two", "y".repeat(40000));
        ItemInputMatcher exact = ItemInputMatcher.exact(large);
        List<ItemInventoryBatchEntry> entries = IntStream.range(0, 64)
                .mapToObj(index -> ItemInventoryBatchEntry.extract(
                        new UUID(3L, index + 1L), exact, 1))
                .toList();
        List<ItemStack> main = emptyMain();
        main.set(0, large);

        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryBatchPlanner.plan(
                        ItemInventoryState.of(main, ItemStack.EMPTY),
                        entries));
    }

    @Test
    void oversizedRequestedCountReturnsInsufficientWithoutLargeDpArrays() {
        List<ItemStack> main = emptyMain();
        main.set(0, new ItemStack(Items.EMERALD, 64));

        ItemInventoryMutationPlan plan = ItemInventoryBatchPlanner.plan(
                ItemInventoryState.of(main, ItemStack.EMPTY),
                List.of(ItemInventoryBatchEntry.extract(UUID.randomUUID(),
                        ItemInputMatcher.itemOnly("minecraft:emerald"),
                        Integer.MAX_VALUE)));

        assertEquals(ItemInventoryPlanStatus.INSUFFICIENT_ITEMS,
                plan.status());
    }

    @Test
    void strictInputsAllocateBeforeOverlappingLenientInputs() {
        List<ItemStack> main = emptyMain();
        ItemStack tagged = new ItemStack(Items.EMERALD, 3);
        tagged.getOrCreateTag().putString("grade", "strict");
        main.set(0, tagged);
        main.set(1, new ItemStack(Items.EMERALD, 1));
        UUID lenientId = UUID.fromString(
                "00000000-0000-0000-0000-000000000001");
        UUID strictId = UUID.fromString(
                "ffffffff-ffff-ffff-ffff-ffffffffffff");

        ItemInventoryMutationPlan plan = ItemInventoryBatchPlanner.plan(
                ItemInventoryState.of(main, ItemStack.EMPTY),
                List.of(
                        ItemInventoryBatchEntry.extract(lenientId,
                                ItemInputMatcher.itemOnly("minecraft:emerald"),
                                2),
                        ItemInventoryBatchEntry.extract(strictId,
                                ItemInputMatcher.exact(tagged), 2)));

        assertTrue(plan.applicable());
        assertEquals(strictId, plan.entries().get(0).entryId());
        assertTrue(plan.after().stack(ItemInventorySlot.main(0)).isEmpty());
        assertTrue(plan.after().stack(ItemInventorySlot.main(1)).isEmpty());
        int fromFirstSlot = plan.allocations().stream()
                .filter(value -> value.slot().equals(
                        ItemInventorySlot.main(0)))
                .mapToInt(ItemInventoryAllocation::count).sum();
        assertEquals(3, fromFirstSlot);
        ItemInventoryAllocation strictPortion = plan.allocations().stream()
                .filter(value -> value.entryId().equals(strictId))
                .findFirst().orElseThrow();
        ItemStack captured = ItemStackSnapshotCodec.decode(
                strictPortion.actualStackSnapshot());
        assertEquals(2, captured.getCount());
        assertEquals("strict", captured.getTag().getString("grade"));
    }

    @Test
    void insertionUsesMainInventoryThenOffhand() {
        List<ItemStack> main = new ArrayList<>();
        for (int index = 0;
             index < ItemInventorySlot.MAIN_SLOT_COUNT;
             index++) {
            main.add(new ItemStack(Items.STONE, 64));
        }
        ItemStack existing = new ItemStack(Items.EMERALD, 60);
        existing.getOrCreateTag().putString("batch", "exact");
        main.set(0, existing);
        ItemStack incoming = new ItemStack(Items.EMERALD, 8);
        incoming.getOrCreateTag().putString("batch", "exact");

        ItemInventoryMutationPlan plan = ItemInventoryBatchPlanner.plan(
                ItemInventoryState.of(main, ItemStack.EMPTY),
                List.of(ItemInventoryBatchEntry.insert(
                        UUID.randomUUID(), incoming)));

        assertTrue(plan.applicable());
        assertEquals(64, plan.after().stack(
                ItemInventorySlot.main(0)).getCount());
        assertEquals(4, plan.after().stack(
                ItemInventorySlot.offhand()).getCount());
        assertEquals(List.of(ItemInventorySlot.main(0),
                        ItemInventorySlot.offhand()),
                plan.changes().stream().map(ItemInventorySlotChange::slot)
                        .toList());
    }

    @Test
    void armorIsExcludedFromExtractionAndInsufficientPlansAreAtomic() {
        List<ItemStack> main = new ArrayList<>();
        for (int index = 0;
             index < ItemInventorySlot.MAIN_SLOT_COUNT;
             index++) {
            main.add(new ItemStack(Items.STONE, 64));
        }
        List<ItemStack> armor = List.of(
                new ItemStack(Items.DIAMOND_HELMET), ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY);
        ItemInventoryState state = ItemInventoryState.fromCompartments(
                main, List.of(new ItemStack(Items.SHIELD)), armor);

        ItemInventoryMutationPlan plan = ItemInventoryBatchPlanner.plan(
                state, List.of(ItemInventoryBatchEntry.extract(
                        UUID.randomUUID(),
                        ItemInputMatcher.itemOnly("minecraft:diamond_helmet"),
                        1)));

        assertEquals(ItemInventoryPlanStatus.INSUFFICIENT_ITEMS,
                plan.status());
        assertFalse(plan.applicable());
        assertEquals(state, plan.after());
        assertTrue(plan.allocations().isEmpty());
        assertTrue(plan.changes().isEmpty());
        assertEquals(0, plan.fulfilledCounts().values().iterator().next());
    }

    @Test
    void canonicalEntryOrderingMakesEquivalentBatchesIdentical() {
        ItemInventoryState empty = ItemInventoryState.of(
                emptyMain(), ItemStack.EMPTY);
        UUID emeraldId = UUID.fromString(
                "00000000-0000-0000-0000-000000000001");
        UUID diamondId = UUID.fromString(
                "00000000-0000-0000-0000-000000000002");
        ItemInventoryBatchEntry emerald = ItemInventoryBatchEntry.insert(
                emeraldId, new ItemStack(Items.EMERALD, 2));
        ItemInventoryBatchEntry diamond = ItemInventoryBatchEntry.insert(
                diamondId, new ItemStack(Items.DIAMOND, 3));

        ItemInventoryMutationPlan first = ItemInventoryBatchPlanner.plan(
                empty, List.of(diamond, emerald));
        ItemInventoryMutationPlan second = ItemInventoryBatchPlanner.plan(
                empty, List.of(emerald, diamond));

        assertEquals(first, second);
        assertArrayEquals(first.batchFingerprint(),
                second.batchFingerprint());
    }

    private static List<ItemStack> emptyMain() {
        List<ItemStack> slots = new ArrayList<>();
        for (int index = 0;
             index < ItemInventorySlot.MAIN_SLOT_COUNT;
             index++) {
            slots.add(ItemStack.EMPTY);
        }
        return slots;
    }
}
