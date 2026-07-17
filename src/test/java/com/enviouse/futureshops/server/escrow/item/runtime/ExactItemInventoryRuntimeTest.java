package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.server.escrow.item.ItemInputMatcher;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchEntry;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchPlanner;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationPlan;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationToken;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExactItemInventoryRuntimeTest {
    private static final Instant NOW = Instant.parse(
            "2026-07-17T12:34:56.789Z");

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void durablePreparePrecedesEveryLiveWrite() {
        List<String> events = new ArrayList<>();
        TestGateway gateway = new TestGateway(events);
        TestAccess access = new TestAccess(UUID.randomUUID(), events);
        access.main.set(0, new ItemStack(Items.EMERALD, 5));
        ExactItemInventoryRuntime runtime = runtime(gateway);

        ItemInventoryExecutionResult result = runtime.execute(access,
                UUID.randomUUID(), UUID.randomUUID(), List.of(
                        ItemInventoryBatchEntry.extract(UUID.randomUUID(),
                                ItemInputMatcher.itemOnly(
                                        "minecraft:emerald"), 3)));

        assertEquals(ItemInventoryExecutionStatus.APPLIED, result.status());
        assertEquals(2, access.main.get(0).getCount());
        assertEquals("prepare", events.get(0));
        assertTrue(events.indexOf("prepare") < events.indexOf("write"));
        assertTrue(events.indexOf("write") < events.indexOf("save"));
        assertTrue(events.indexOf("save") < events.indexOf("force"));
        assertTrue(events.indexOf("force") < events.indexOf("commit"));
        assertEquals(0, runtime.trackedPlayerLocks());
    }

    @Test
    void replayDoesNotApplyTheSameMutationTwice() {
        TestGateway gateway = new TestGateway(new ArrayList<>());
        TestAccess access = new TestAccess(UUID.randomUUID(),
                new ArrayList<>());
        access.main.set(0, new ItemStack(Items.DIAMOND, 4));
        ExactItemInventoryRuntime runtime = runtime(gateway);
        UUID transactionId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        List<ItemInventoryBatchEntry> entries = List.of(
                ItemInventoryBatchEntry.extract(UUID.randomUUID(),
                        ItemInputMatcher.itemOnly("minecraft:diamond"), 2));

        ItemInventoryExecutionResult first = runtime.execute(access,
                transactionId, requestId, entries);
        int writes = access.writes;
        ItemInventoryExecutionResult second = runtime.execute(access,
                transactionId, requestId, entries);

        assertEquals(ItemInventoryExecutionStatus.APPLIED, first.status());
        assertEquals(ItemInventoryExecutionStatus.REPLAYED, second.status());
        assertEquals(writes, access.writes);
        assertEquals(2, access.main.get(0).getCount());
        assertEquals(1, access.saves);
        assertEquals(1, access.forces);
    }

    @Test
    void replayOfOlderCommitNeverRepairsAcrossANewerRequest() {
        TestGateway gateway = new TestGateway(new ArrayList<>());
        TestAccess access = new TestAccess(UUID.randomUUID(),
                new ArrayList<>());
        access.main.set(0, new ItemStack(Items.EMERALD, 6));
        ExactItemInventoryRuntime runtime = runtime(gateway);
        UUID firstTransaction = UUID.randomUUID();
        UUID firstRequest = UUID.randomUUID();
        List<ItemInventoryBatchEntry> firstEntries = List.of(
                ItemInventoryBatchEntry.extract(UUID.randomUUID(),
                        ItemInputMatcher.itemOnly(
                                "minecraft:emerald"), 2));
        runtime.execute(access, firstTransaction, firstRequest,
                firstEntries);
        runtime.execute(access, UUID.randomUUID(), UUID.randomUUID(),
                List.of(ItemInventoryBatchEntry.extract(
                        UUID.randomUUID(), ItemInputMatcher.itemOnly(
                                "minecraft:emerald"), 1)));
        int writes = access.writes;

        ItemInventoryExecutionResult replay = runtime.execute(access,
                firstTransaction, firstRequest, firstEntries);

        assertEquals(ItemInventoryExecutionStatus.REPLAYED,
                replay.status());
        assertEquals(3, access.main.get(0).getCount());
        assertEquals(writes, access.writes);
        assertEquals(ItemInventoryJournalStatus.COMMITTED,
                gateway.find(firstRequest).orElseThrow().status());
    }

    @Test
    void crashAfterLiveApplyRecoversByCommittingThePostimage() {
        TestGateway gateway = new TestGateway(new ArrayList<>());
        gateway.commitFailures = 1;
        TestAccess access = new TestAccess(UUID.randomUUID(),
                new ArrayList<>());
        ExactItemInventoryRuntime runtime = runtime(gateway);
        UUID transactionId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        List<ItemInventoryBatchEntry> entries = List.of(
                ItemInventoryBatchEntry.insert(UUID.randomUUID(),
                        new ItemStack(Items.GOLD_INGOT, 6)));

        ItemInventoryExecutionResult first = runtime.execute(access,
                transactionId, requestId, entries);
        int writes = access.writes;
        ItemInventoryExecutionResult recovered = runtime.recover(access,
                requestId);

        assertEquals(ItemInventoryExecutionStatus.RECOVERY_REQUIRED,
                first.status());
        assertEquals(ItemInventoryExecutionStatus.REPLAYED,
                recovered.status());
        assertEquals(writes, access.writes);
        assertEquals(6, access.main.get(0).getCount());
        assertEquals(ItemInventoryJournalStatus.COMMITTED,
                gateway.find(requestId).orElseThrow().status());
    }

    @Test
    void partialWriteFailureRollsBackTheWholeBatchAndAborts() {
        TestGateway gateway = new TestGateway(new ArrayList<>());
        TestAccess access = new TestAccess(UUID.randomUUID(),
                new ArrayList<>());
        access.main.set(0, new ItemStack(Items.EMERALD, 63));
        access.failWriteNumber = 2;
        ExactItemInventoryRuntime runtime = runtime(gateway);
        UUID requestId = UUID.randomUUID();

        ItemInventoryExecutionResult result = runtime.execute(access,
                UUID.randomUUID(), requestId, List.of(
                        ItemInventoryBatchEntry.insert(UUID.randomUUID(),
                                new ItemStack(Items.EMERALD, 3))));

        assertEquals(ItemInventoryExecutionStatus.ABORTED, result.status());
        assertEquals(63, access.main.get(0).getCount());
        assertTrue(access.main.get(1).isEmpty());
        assertEquals(ItemInventoryAbortReason.APPLY_FAILED_ROLLED_BACK,
                gateway.find(requestId).orElseThrow().abort()
                        .orElseThrow().reason());
    }

    @Test
    void unknownPreparedSlotImageFailsClosedWithoutWrites() {
        TestGateway gateway = new TestGateway(new ArrayList<>());
        TestAccess access = new TestAccess(UUID.randomUUID(),
                new ArrayList<>());
        access.main.set(0, new ItemStack(Items.EMERALD, 5));
        ItemInventoryMutationIntent intent = intent(access,
                UUID.randomUUID(), UUID.randomUUID(), List.of(
                        ItemInventoryBatchEntry.extract(UUID.randomUUID(),
                                ItemInputMatcher.itemOnly(
                                        "minecraft:emerald"), 2)));
        gateway.appendPreparedDurably(intent);
        access.main.set(0, new ItemStack(Items.EMERALD, 4));
        int writes = access.writes;

        ItemInventoryExecutionResult result = runtime(gateway).recover(
                access, intent.token().requestId());

        assertEquals(ItemInventoryExecutionStatus.MANUAL_REVIEW,
                result.status());
        assertEquals(writes, access.writes);
        assertEquals(ItemInventoryJournalStatus.QUARANTINED,
                gateway.find(intent.token().requestId()).orElseThrow()
                        .status());
        assertEquals(ItemInventoryQuarantineReason.UNKNOWN_SLOT_IMAGE,
                gateway.find(intent.token().requestId()).orElseThrow()
                        .quarantine().orElseThrow().reason());
    }

    @Test
    void mixedKnownPreparedImageCompletesForwardAtomically() {
        TestGateway gateway = new TestGateway(new ArrayList<>());
        TestAccess access = new TestAccess(UUID.randomUUID(),
                new ArrayList<>());
        access.main.set(0, new ItemStack(Items.EMERALD, 63));
        ItemInventoryMutationIntent intent = intent(access,
                UUID.randomUUID(), UUID.randomUUID(), List.of(
                        ItemInventoryBatchEntry.insert(UUID.randomUUID(),
                                new ItemStack(Items.EMERALD, 3))));
        gateway.appendPreparedDurably(intent);
        ItemInventorySlotMutationEvidence first =
                intent.slotEvidence().get(0);
        access.write(first.slot(), first.afterStack());

        ItemInventoryExecutionResult result = runtime(gateway).recover(
                access, intent.token().requestId());

        assertEquals(ItemInventoryExecutionStatus.REPLAYED, result.status());
        assertEquals(64, access.main.get(0).getCount());
        assertEquals(2, access.main.get(1).getCount());
    }

    @Test
    void strictPolicyRejectsNestedContainerBeforePrepare() {
        TestGateway gateway = new TestGateway(new ArrayList<>());
        TestAccess access = new TestAccess(UUID.randomUUID(),
                new ArrayList<>());
        ItemStack container = new ItemStack(Items.SHULKER_BOX);
        CompoundTag blockEntity = new CompoundTag();
        ListTag contents = new ListTag();
        CompoundTag nested = new CompoundTag();
        nested.putString("id", "minecraft:diamond");
        nested.putByte("Count", (byte) 1);
        contents.add(nested);
        blockEntity.put("Items", contents);
        container.getOrCreateTag().put("BlockEntityTag", blockEntity);

        ItemInventoryExecutionResult result = new ExactItemInventoryRuntime(
                gateway, ItemInventoryStackPolicy.strictDefault(),
                fixedClock()).execute(access, UUID.randomUUID(),
                UUID.randomUUID(), List.of(ItemInventoryBatchEntry.insert(
                        UUID.randomUUID(), container)));

        assertEquals(ItemInventoryExecutionStatus.UNSUPPORTED_STACK,
                result.status());
        assertTrue(gateway.entries.isEmpty());
        assertTrue(access.main.get(0).isEmpty());
    }

    @Test
    void strictPolicyRejectsPersistentCapabilityStackBeforePrepare() {
        TestGateway gateway = new TestGateway(new ArrayList<>());
        TestAccess access = new TestAccess(UUID.randomUUID(),
                new ArrayList<>());
        ItemStack capable = new ItemStack(
                MinecraftTestBootstrap.capabilityItem());

        ItemInventoryExecutionResult result = new ExactItemInventoryRuntime(
                gateway, ItemInventoryStackPolicy.strictDefault(),
                fixedClock()).execute(access, UUID.randomUUID(),
                UUID.randomUUID(), List.of(ItemInventoryBatchEntry.insert(
                        UUID.randomUUID(), capable)));

        assertEquals(ItemInventoryExecutionStatus.UNSUPPORTED_STACK,
                result.status());
        assertTrue(gateway.entries.isEmpty());
    }

    @Test
    void reusedRequestWithDifferentBatchFailsClosed() {
        TestGateway gateway = new TestGateway(new ArrayList<>());
        TestAccess access = new TestAccess(UUID.randomUUID(),
                new ArrayList<>());
        ExactItemInventoryRuntime runtime = runtime(gateway);
        UUID transactionId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        runtime.execute(access, transactionId, requestId, List.of(
                ItemInventoryBatchEntry.insert(UUID.randomUUID(),
                        new ItemStack(Items.EMERALD, 1))));

        assertThrows(IllegalStateException.class,
                () -> runtime.execute(access, transactionId, requestId,
                        List.of(ItemInventoryBatchEntry.insert(
                                UUID.randomUUID(),
                                new ItemStack(Items.DIAMOND, 1)))));
    }

    @Test
    void boundedPreparedRecoveryOnlyReturnsThePlayersWork() {
        TestGateway gateway = new TestGateway(new ArrayList<>());
        TestAccess first = new TestAccess(UUID.randomUUID(),
                new ArrayList<>());
        TestAccess second = new TestAccess(UUID.randomUUID(),
                new ArrayList<>());
        gateway.appendPreparedDurably(intent(first, UUID.randomUUID(),
                UUID.randomUUID(), List.of(ItemInventoryBatchEntry.insert(
                        UUID.randomUUID(),
                        new ItemStack(Items.EMERALD, 1)))));
        gateway.appendPreparedDurably(intent(second, UUID.randomUUID(),
                UUID.randomUUID(), List.of(ItemInventoryBatchEntry.insert(
                        UUID.randomUUID(),
                        new ItemStack(Items.DIAMOND, 1)))));

        List<ItemInventoryExecutionResult> results = runtime(gateway)
                .recoverPrepared(first, 1);

        assertEquals(1, results.size());
        assertEquals(1, first.main.get(0).getCount());
        assertTrue(second.main.get(0).isEmpty());
    }

    @Test
    void newRequestCannotPassAnUnresolvedMutationForThePlayer() {
        TestGateway gateway = new TestGateway(new ArrayList<>());
        TestAccess access = new TestAccess(UUID.randomUUID(),
                new ArrayList<>());
        gateway.appendPreparedDurably(intent(access, UUID.randomUUID(),
                UUID.randomUUID(), List.of(ItemInventoryBatchEntry.insert(
                        UUID.randomUUID(),
                        new ItemStack(Items.EMERALD, 1)))));

        assertThrows(IllegalStateException.class,
                () -> runtime(gateway).execute(access, UUID.randomUUID(),
                        UUID.randomUUID(), List.of(
                                ItemInventoryBatchEntry.insert(
                                        UUID.randomUUID(),
                                        new ItemStack(Items.DIAMOND, 1)))));
        assertEquals(1, gateway.entries.size());
    }

    @Test
    void saveFailureLeavesPreparedWorkAndRecoveryDoesNotRewriteSlots() {
        TestGateway gateway = new TestGateway(new ArrayList<>());
        TestAccess access = new TestAccess(UUID.randomUUID(),
                new ArrayList<>());
        access.saveFailures = 1;
        ExactItemInventoryRuntime runtime = runtime(gateway);
        UUID requestId = UUID.randomUUID();

        ItemInventoryExecutionResult first = runtime.execute(access,
                UUID.randomUUID(), requestId, List.of(
                        ItemInventoryBatchEntry.insert(UUID.randomUUID(),
                                new ItemStack(Items.EMERALD, 2))));
        int writes = access.writes;
        ItemInventoryExecutionResult recovered = runtime.recover(access,
                requestId);

        assertEquals(ItemInventoryExecutionStatus.RECOVERY_REQUIRED,
                first.status());
        assertEquals(ItemInventoryExecutionStatus.REPLAYED,
                recovered.status());
        assertEquals(writes, access.writes);
        assertEquals(ItemInventoryJournalStatus.COMMITTED,
                gateway.find(requestId).orElseThrow().status());
    }

    @Test
    void forceFailureLeavesPreparedWorkUntilTheBarrierSucceeds() {
        TestGateway gateway = new TestGateway(new ArrayList<>());
        TestAccess access = new TestAccess(UUID.randomUUID(),
                new ArrayList<>());
        access.forceFailures = 1;
        ExactItemInventoryRuntime runtime = runtime(gateway);
        UUID requestId = UUID.randomUUID();

        ItemInventoryExecutionResult first = runtime.execute(access,
                UUID.randomUUID(), requestId, List.of(
                        ItemInventoryBatchEntry.insert(UUID.randomUUID(),
                                new ItemStack(Items.DIAMOND, 2))));
        ItemInventoryExecutionResult recovered = runtime.recover(access,
                requestId);

        assertEquals(ItemInventoryExecutionStatus.RECOVERY_REQUIRED,
                first.status());
        assertEquals(ItemInventoryExecutionStatus.REPLAYED,
                recovered.status());
        assertEquals(2, access.saves);
        assertEquals(2, access.forces);
    }

    @Test
    void committedRecoveryRepairsACompletePreimageAndForcesIt() {
        TestGateway gateway = new TestGateway(new ArrayList<>());
        TestAccess access = new TestAccess(UUID.randomUUID(),
                new ArrayList<>());
        ExactItemInventoryRuntime runtime = runtime(gateway);
        UUID requestId = UUID.randomUUID();
        runtime.execute(access, UUID.randomUUID(), requestId, List.of(
                ItemInventoryBatchEntry.insert(UUID.randomUUID(),
                        new ItemStack(Items.GOLD_INGOT, 3))));
        access.main.set(0, ItemStack.EMPTY);
        int saves = access.saves;

        ItemInventoryExecutionResult repaired = runtime.recover(access,
                requestId);

        assertEquals(ItemInventoryExecutionStatus.REPLAYED,
                repaired.status());
        assertEquals(3, access.main.get(0).getCount());
        assertEquals(saves + 1, access.saves);
        assertEquals(access.saves, access.forces);
    }

    private static ExactItemInventoryRuntime runtime(TestGateway gateway) {
        return new ExactItemInventoryRuntime(gateway,
                (stack, direction) -> true, fixedClock());
    }

    private static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private static ItemInventoryMutationIntent intent(
            TestAccess access,
            UUID transactionId,
            UUID requestId,
            List<ItemInventoryBatchEntry> entries
    ) {
        ItemInventoryMutationPlan plan = ItemInventoryBatchPlanner.plan(
                access.capture(), entries);
        ItemInventoryMutationToken token = ItemInventoryMutationToken.create(
                access.playerId(), transactionId, requestId, plan);
        ItemInventoryMutationReceipt receipt =
                ItemInventoryMutationReceipt.create(token, plan, NOW);
        return ItemInventoryMutationIntent.create(token, plan, receipt);
    }

    private static final class TestAccess implements ItemInventoryAccess {
        private final UUID playerId;
        private final List<String> events;
        private final List<ItemStack> main = emptyMain();
        private ItemStack offhand = ItemStack.EMPTY;
        private int writes;
        private int failWriteNumber;
        private int saves;
        private int forces;
        private int saveFailures;
        private int forceFailures;

        private TestAccess(UUID playerId, List<String> events) {
            this.playerId = playerId;
            this.events = events;
        }

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
            events.add("write");
            if (slot.isOffhand()) {
                offhand = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
            } else {
                main.set(slot.serializedSlot(), stack.isEmpty()
                        ? ItemStack.EMPTY : stack.copy());
            }
            if (failWriteNumber == writes) {
                failWriteNumber = 0;
                throw new IllegalStateException("Injected write failure");
            }
        }

        @Override
        public void flush() {
            events.add("flush");
        }

        @Override
        public void savePlayerData() {
            saves++;
            events.add("save");
            if (saveFailures > 0) {
                saveFailures--;
                throw new ItemInventoryDurabilityException(
                        "Injected save failure",
                        new IllegalStateException("save"));
            }
        }

        @Override
        public void forcePlayerData() {
            forces++;
            events.add("force");
            if (forceFailures > 0) {
                forceFailures--;
                throw new ItemInventoryDurabilityException(
                        "Injected force failure",
                        new IllegalStateException("force"));
            }
        }
    }

    private static final class TestGateway
            implements DurableItemInventoryMutationGateway {
        private final Map<UUID, ItemInventoryJournalEntry> entries =
                new LinkedHashMap<>();
        private final List<String> events;
        private int commitFailures;

        private TestGateway(List<String> events) {
            this.events = events;
        }

        @Override
        public synchronized ItemInventoryGatewayResult
        appendPreparedDurably(ItemInventoryMutationIntent intent) {
            events.add("prepare");
            ItemInventoryJournalEntry existing = entries.get(
                    intent.token().requestId());
            if (existing != null) {
                if (!existing.intent().token().equals(intent.token())) {
                    throw new IllegalStateException("Prepare conflict");
                }
                return new ItemInventoryGatewayResult(existing, true);
            }
            ItemInventoryJournalEntry prepared =
                    ItemInventoryJournalEntry.prepared(intent);
            entries.put(intent.token().requestId(), prepared);
            return new ItemInventoryGatewayResult(prepared, false);
        }

        @Override
        public synchronized ItemInventoryGatewayResult
        appendCommittedDurably(ItemInventoryMutationReceipt receipt) {
            events.add("commit");
            if (commitFailures > 0) {
                commitFailures--;
                throw new IllegalStateException("Injected commit failure");
            }
            ItemInventoryJournalEntry existing = require(
                    receipt.token().requestId());
            if (!existing.intent().token().equals(receipt.token())) {
                throw new IllegalStateException("Commit conflict");
            }
            if (existing.status()
                    == ItemInventoryJournalStatus.COMMITTED) {
                return new ItemInventoryGatewayResult(existing, true);
            }
            if (existing.status() != ItemInventoryJournalStatus.PREPARED) {
                throw new IllegalStateException("Commit after abort");
            }
            ItemInventoryJournalEntry committed =
                    ItemInventoryJournalEntry.committed(existing.intent(),
                            receipt);
            entries.put(receipt.token().requestId(), committed);
            return new ItemInventoryGatewayResult(committed, false);
        }

        @Override
        public synchronized ItemInventoryGatewayResult
        appendAbortedDurably(ItemInventoryMutationAbort abort) {
            events.add("abort");
            ItemInventoryJournalEntry existing = require(
                    abort.token().requestId());
            if (!existing.intent().token().equals(abort.token())) {
                throw new IllegalStateException("Abort conflict");
            }
            if (existing.status() == ItemInventoryJournalStatus.ABORTED) {
                return new ItemInventoryGatewayResult(existing, true);
            }
            if (existing.status() != ItemInventoryJournalStatus.PREPARED) {
                throw new IllegalStateException("Abort after commit");
            }
            ItemInventoryJournalEntry aborted =
                    ItemInventoryJournalEntry.aborted(existing.intent(),
                            abort);
            entries.put(abort.token().requestId(), aborted);
            return new ItemInventoryGatewayResult(aborted, false);
        }

        @Override
        public synchronized ItemInventoryGatewayResult
        appendQuarantinedDurably(
                ItemInventoryMutationQuarantine quarantine
        ) {
            events.add("quarantine");
            ItemInventoryJournalEntry existing = require(
                    quarantine.token().requestId());
            if (!existing.intent().token().equals(quarantine.token())) {
                throw new IllegalStateException("Quarantine conflict");
            }
            if (existing.status()
                    == ItemInventoryJournalStatus.QUARANTINED) {
                return new ItemInventoryGatewayResult(existing, true);
            }
            if (existing.status() == ItemInventoryJournalStatus.ABORTED) {
                throw new IllegalStateException("Quarantine after abort");
            }
            ItemInventoryJournalEntry quarantined =
                    ItemInventoryJournalEntry.quarantined(existing,
                            quarantine);
            entries.put(quarantine.token().requestId(), quarantined);
            return new ItemInventoryGatewayResult(quarantined, false);
        }

        @Override
        public synchronized Optional<ItemInventoryJournalEntry> find(
                UUID requestId
        ) {
            return Optional.ofNullable(entries.get(requestId));
        }

        @Override
        public synchronized List<ItemInventoryJournalEntry>
        preparedForPlayer(UUID playerId, int limit) {
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
                    .sorted(Comparator.comparing(entry -> entry.intent()
                            .token().requestId().toString()))
                    .limit(limit).toList();
        }

        @Override
        public synchronized boolean playerQuarantined(UUID playerId) {
            return entries.values().stream()
                    .anyMatch(entry -> entry.status()
                            == ItemInventoryJournalStatus.QUARANTINED
                            && entry.intent().token().playerId()
                            .equals(playerId));
        }

        @Override
        public synchronized boolean hasLaterRequestForPlayer(
                UUID playerId,
                UUID requestId
        ) {
            boolean found = false;
            for (ItemInventoryJournalEntry entry : entries.values()) {
                if (!entry.intent().token().playerId().equals(playerId)) {
                    continue;
                }
                if (found) {
                    return true;
                }
                found = entry.intent().token().requestId()
                        .equals(requestId);
            }
            return false;
        }

        private ItemInventoryJournalEntry require(UUID requestId) {
            ItemInventoryJournalEntry entry = entries.get(requestId);
            if (entry == null) {
                throw new IllegalStateException("Request is not prepared");
            }
            return entry;
        }
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
