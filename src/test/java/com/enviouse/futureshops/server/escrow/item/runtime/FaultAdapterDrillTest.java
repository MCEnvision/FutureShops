package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.server.escrow.item.ItemInputMatcher;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchEntry;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchPlanner;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationPlan;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationToken;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryPlanStatus;
import com.enviouse.futureshops.server.escrow.item.ItemInventorySlot;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 6 fault adapter drills (plan §17: partial insertion, partial
 * extraction, slot reorder, unsupported reconciliation): an insufficient
 * batch fails as a whole with zero mutation at both the planner and runtime
 * layers and never touches the durable journal; a slot reorder between
 * prepare and recovery quarantines instead of guessing; a stack that stops
 * satisfying the strict policy after prepare quarantines during recovery.
 */
class FaultAdapterDrillTest {
    private static final Instant NOW = Instant.parse(
            "2026-07-17T12:00:00Z");

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void insufficientItemsFailTheWholeExtractionWithoutMutation() {
        List<ItemStack> main = emptyMain();
        main.set(0, new ItemStack(Items.EMERALD, 8));
        main.set(1, new ItemStack(Items.DIAMOND, 1));
        ItemInventoryState before = ItemInventoryState.of(
                main, ItemStack.EMPTY);
        UUID satisfiable = new UUID(1L, 1L);
        UUID starved = new UUID(1L, 2L);

        ItemInventoryMutationPlan plan = ItemInventoryBatchPlanner.plan(
                before, List.of(
                        ItemInventoryBatchEntry.extract(satisfiable,
                                ItemInputMatcher.itemOnly(
                                        "minecraft:emerald"), 8),
                        ItemInventoryBatchEntry.extract(starved,
                                ItemInputMatcher.itemOnly(
                                        "minecraft:diamond"), 3)));

        // Partial extraction is refused atomically: the satisfiable entry
        // does not proceed alone, and the pre-image is untouched.
        assertEquals(ItemInventoryPlanStatus.INSUFFICIENT_ITEMS,
                plan.status());
        assertEquals(before, plan.after());
        assertTrue(plan.allocations().isEmpty());
        assertTrue(plan.changes().isEmpty());
        assertEquals(8, plan.fulfilledCounts().get(satisfiable));
        assertEquals(0, plan.fulfilledCounts().get(starved));
    }

    @Test
    void insufficientCapacityFailsThePartialInsertWithoutMutation() {
        List<ItemStack> main = new ArrayList<>();
        for (int index = 0;
             index < ItemInventorySlot.MAIN_SLOT_COUNT;
             index++) {
            main.add(new ItemStack(Items.STONE, 64));
        }
        // Exactly three units of emerald space remain in the whole grid.
        main.set(0, new ItemStack(Items.EMERALD, 61));
        ItemInventoryState before = ItemInventoryState.of(main,
                new ItemStack(Items.STONE, 64));

        ItemInventoryMutationPlan plan = ItemInventoryBatchPlanner.plan(
                before, List.of(ItemInventoryBatchEntry.insert(
                        new UUID(2L, 1L),
                        new ItemStack(Items.EMERALD, 5))));

        // The adapter never inserts three of five: all or nothing.
        assertEquals(ItemInventoryPlanStatus.INSUFFICIENT_CAPACITY,
                plan.status());
        assertEquals(before, plan.after());
        assertTrue(plan.allocations().isEmpty());
        assertEquals(3, plan.fulfilledCounts().get(new UUID(2L, 1L)));
    }

    @Test
    void runtimeRejectsInsufficientPlansBeforeAnyDurableWrite() {
        DrillAccess access = new DrillAccess();
        access.main.set(0, new ItemStack(Items.EMERALD, 1));
        ExactItemInventoryRuntime runtime = new ExactItemInventoryRuntime(
                new RejectingGateway(), (stack, direction) -> true,
                fixedClock());

        ItemInventoryExecutionResult result = runtime.execute(access,
                UUID.randomUUID(), UUID.randomUUID(), List.of(
                        ItemInventoryBatchEntry.extract(UUID.randomUUID(),
                                ItemInputMatcher.itemOnly(
                                        "minecraft:emerald"), 3)));

        // The RejectingGateway throws on any append: reaching this point
        // proves the failed plan produced zero journal writes.
        assertEquals(ItemInventoryExecutionStatus.INSUFFICIENT_ITEMS,
                result.status());
        assertEquals(0, access.writes);
        assertEquals(1, access.main.get(0).getCount());
    }

    @Test
    void runtimeRejectsUnsupportedStacksBeforeAnyDurableWrite() {
        DrillAccess access = new DrillAccess();
        ExactItemInventoryRuntime runtime = new ExactItemInventoryRuntime(
                new RejectingGateway(),
                ItemInventoryStackPolicy.strictDefault(), fixedClock());

        ItemInventoryExecutionResult result = runtime.execute(access,
                UUID.randomUUID(), UUID.randomUUID(), List.of(
                        ItemInventoryBatchEntry.insert(UUID.randomUUID(),
                                nestedContainer())));

        assertEquals(ItemInventoryExecutionStatus.UNSUPPORTED_STACK,
                result.status());
        assertEquals(0, access.writes);
        assertTrue(access.main.get(0).isEmpty());
    }

    @Test
    void slotReorderAfterPrepareQuarantinesInsteadOfGuessing() {
        RecordingGateway gateway = new RecordingGateway();
        DrillAccess access = new DrillAccess();
        access.main.set(0, new ItemStack(Items.EMERALD, 5));
        access.main.set(1, new ItemStack(Items.DIAMOND, 7));
        ItemInventoryMutationIntent intent = intent(access, List.of(
                ItemInventoryBatchEntry.extract(UUID.randomUUID(),
                        ItemInputMatcher.itemOnly("minecraft:emerald"),
                        2)));
        gateway.appendPreparedDurably(intent);

        // An unload / reload reordered the slots: same items, new places.
        ItemStack first = access.main.get(0);
        access.main.set(0, access.main.get(1));
        access.main.set(1, first);

        ItemInventoryExecutionResult result = new ExactItemInventoryRuntime(
                gateway, (stack, direction) -> true, fixedClock())
                .recover(access, intent.token().requestId());

        // Neither the before- nor the after-image matches: the runtime must
        // quarantine for an operator, never improvise a mutation.
        assertEquals(ItemInventoryExecutionStatus.MANUAL_REVIEW,
                result.status());
        assertEquals(0, access.writes);
        ItemInventoryJournalEntry entry = gateway.find(
                intent.token().requestId()).orElseThrow();
        assertEquals(ItemInventoryJournalStatus.QUARANTINED,
                entry.status());
        assertEquals(ItemInventoryQuarantineReason.UNKNOWN_SLOT_IMAGE,
                entry.quarantine().orElseThrow().reason());
    }

    @Test
    void unsupportedStackDiscoveredDuringRecoveryQuarantines() {
        RecordingGateway gateway = new RecordingGateway();
        DrillAccess access = new DrillAccess();
        access.main.set(0, nestedContainer());
        // Prepared under a permissive policy (an older or buggy adapter).
        ItemInventoryMutationIntent intent = intent(access, List.of(
                ItemInventoryBatchEntry.extract(UUID.randomUUID(),
                        ItemInputMatcher.itemOnly(
                                "minecraft:shulker_box"), 1)));
        gateway.appendPreparedDurably(intent);

        // Recovery under the strict policy refuses to reconcile the stack.
        ItemInventoryExecutionResult result = new ExactItemInventoryRuntime(
                gateway, ItemInventoryStackPolicy.strictDefault(),
                fixedClock()).recover(access, intent.token().requestId());

        assertEquals(ItemInventoryExecutionStatus.MANUAL_REVIEW,
                result.status());
        assertEquals(0, access.writes);
        ItemInventoryJournalEntry entry = gateway.find(
                intent.token().requestId()).orElseThrow();
        assertEquals(ItemInventoryJournalStatus.QUARANTINED,
                entry.status());
        assertEquals(ItemInventoryQuarantineReason.UNSUPPORTED_STACK,
                entry.quarantine().orElseThrow().reason());
        // The container itself was never touched.
        assertEquals(1, access.main.get(0).getCount());
    }

    private static ItemStack nestedContainer() {
        ItemStack container = new ItemStack(Items.SHULKER_BOX);
        CompoundTag blockEntity = new CompoundTag();
        ListTag contents = new ListTag();
        CompoundTag nested = new CompoundTag();
        nested.putString("id", "minecraft:diamond");
        nested.putByte("Count", (byte) 1);
        contents.add(nested);
        blockEntity.put("Items", contents);
        container.getOrCreateTag().put("BlockEntityTag", blockEntity);
        return container;
    }

    private static ItemInventoryMutationIntent intent(
            DrillAccess access,
            List<ItemInventoryBatchEntry> entries
    ) {
        ItemInventoryMutationPlan plan = ItemInventoryBatchPlanner.plan(
                access.capture(), entries);
        ItemInventoryMutationToken token =
                ItemInventoryMutationToken.create(access.playerId(),
                        UUID.randomUUID(), UUID.randomUUID(), plan);
        ItemInventoryMutationReceipt receipt =
                ItemInventoryMutationReceipt.create(token, plan, NOW);
        return ItemInventoryMutationIntent.create(token, plan, receipt);
    }

    private static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
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

    /** Minimal live-inventory adapter that counts writes. */
    private static final class DrillAccess implements ItemInventoryAccess {
        private final UUID playerId = UUID.randomUUID();
        private final List<ItemStack> main = emptyMain();
        private ItemStack offhand = ItemStack.EMPTY;
        private int writes;

        @Override
        public UUID playerId() {
            return playerId;
        }

        @Override
        public ItemInventoryState capture() {
            return ItemInventoryState.of(main, offhand);
        }

        @Override
        public void write(ItemInventorySlot slot, ItemStack stack) {
            writes++;
            if (slot.isOffhand()) {
                offhand = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
            } else {
                main.set(slot.serializedSlot(), stack.isEmpty()
                        ? ItemStack.EMPTY : stack.copy());
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void savePlayerData() {
        }

        @Override
        public void forcePlayerData() {
        }
    }

    /** Gateway that fails the test on any durable append: proves the
     * rejection paths never reach the journal. */
    private static final class RejectingGateway
            implements DurableItemInventoryMutationGateway {
        @Override
        public ItemInventoryGatewayResult appendPreparedDurably(
                ItemInventoryMutationIntent intent
        ) {
            throw new AssertionError("The journal must not be prepared");
        }

        @Override
        public ItemInventoryGatewayResult appendCommittedDurably(
                ItemInventoryMutationReceipt receipt
        ) {
            throw new AssertionError("The journal must not be committed");
        }

        @Override
        public ItemInventoryGatewayResult appendAbortedDurably(
                ItemInventoryMutationAbort abort
        ) {
            throw new AssertionError("The journal must not be aborted");
        }

        @Override
        public ItemInventoryGatewayResult appendQuarantinedDurably(
                ItemInventoryMutationQuarantine quarantine
        ) {
            throw new AssertionError(
                    "The journal must not be quarantined");
        }

        @Override
        public Optional<ItemInventoryJournalEntry> find(UUID requestId) {
            return Optional.empty();
        }

        @Override
        public List<ItemInventoryJournalEntry> preparedForPlayer(
                UUID playerId,
                int limit
        ) {
            return List.of();
        }
    }

    /** Minimal in-memory journal for the recovery drills. */
    private static final class RecordingGateway
            implements DurableItemInventoryMutationGateway {
        private final Map<UUID, ItemInventoryJournalEntry> entries =
                new LinkedHashMap<>();

        @Override
        public ItemInventoryGatewayResult appendPreparedDurably(
                ItemInventoryMutationIntent intent
        ) {
            ItemInventoryJournalEntry existing = entries.get(
                    intent.token().requestId());
            if (existing != null) {
                return new ItemInventoryGatewayResult(existing, true);
            }
            ItemInventoryJournalEntry prepared =
                    ItemInventoryJournalEntry.prepared(intent);
            entries.put(intent.token().requestId(), prepared);
            return new ItemInventoryGatewayResult(prepared, false);
        }

        @Override
        public ItemInventoryGatewayResult appendCommittedDurably(
                ItemInventoryMutationReceipt receipt
        ) {
            ItemInventoryJournalEntry existing = entries.get(
                    receipt.token().requestId());
            if (existing.status() == ItemInventoryJournalStatus.COMMITTED) {
                return new ItemInventoryGatewayResult(existing, true);
            }
            ItemInventoryJournalEntry committed =
                    ItemInventoryJournalEntry.committed(existing.intent(),
                            receipt);
            entries.put(receipt.token().requestId(), committed);
            return new ItemInventoryGatewayResult(committed, false);
        }

        @Override
        public ItemInventoryGatewayResult appendAbortedDurably(
                ItemInventoryMutationAbort abort
        ) {
            ItemInventoryJournalEntry existing = entries.get(
                    abort.token().requestId());
            if (existing.status() == ItemInventoryJournalStatus.ABORTED) {
                return new ItemInventoryGatewayResult(existing, true);
            }
            ItemInventoryJournalEntry aborted =
                    ItemInventoryJournalEntry.aborted(existing.intent(),
                            abort);
            entries.put(abort.token().requestId(), aborted);
            return new ItemInventoryGatewayResult(aborted, false);
        }

        @Override
        public ItemInventoryGatewayResult appendQuarantinedDurably(
                ItemInventoryMutationQuarantine quarantine
        ) {
            ItemInventoryJournalEntry existing = entries.get(
                    quarantine.token().requestId());
            if (existing.status()
                    == ItemInventoryJournalStatus.QUARANTINED) {
                return new ItemInventoryGatewayResult(existing, true);
            }
            ItemInventoryJournalEntry quarantined =
                    ItemInventoryJournalEntry.quarantined(existing,
                            quarantine);
            entries.put(quarantine.token().requestId(), quarantined);
            return new ItemInventoryGatewayResult(quarantined, false);
        }

        @Override
        public Optional<ItemInventoryJournalEntry> find(UUID requestId) {
            return Optional.ofNullable(entries.get(requestId));
        }

        @Override
        public List<ItemInventoryJournalEntry> preparedForPlayer(
                UUID playerId,
                int limit
        ) {
            if (playerQuarantined(playerId)) {
                return List.of();
            }
            return entries.values().stream()
                    .filter(entry -> entry.status()
                            == ItemInventoryJournalStatus.PREPARED
                            || entry.status()
                            == ItemInventoryJournalStatus.QUARANTINED)
                    .filter(entry -> entry.intent().token().playerId()
                            .equals(playerId))
                    .limit(limit).toList();
        }

        @Override
        public boolean playerQuarantined(UUID playerId) {
            return entries.values().stream()
                    .anyMatch(entry -> entry.status()
                            == ItemInventoryJournalStatus.QUARANTINED
                            && entry.intent().token().playerId()
                            .equals(playerId));
        }
    }
}
