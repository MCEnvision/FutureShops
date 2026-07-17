package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.ItemInventorySlot;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemInventoryOnlineRecoveryBatchTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void recoveryIsDeterministicAndGloballyBounded() {
        RepositoryGateway gateway = new RepositoryGateway();
        UUID firstId = UUID.fromString(
                "00000000-0000-0000-0000-000000000001");
        UUID secondId = UUID.fromString(
                "00000000-0000-0000-0000-000000000002");
        TestAccess first = new TestAccess(firstId);
        TestAccess second = new TestAccess(secondId);
        ItemInventoryMutationIntent firstIntent =
                ItemInventoryJournalTestFixtures.intent(firstId,
                        UUID.randomUUID(), UUID.randomUUID());
        ItemInventoryMutationIntent secondIntent =
                ItemInventoryJournalTestFixtures.intent(secondId,
                        UUID.randomUUID(), UUID.randomUUID());
        gateway.appendPreparedDurably(firstIntent);
        gateway.appendPreparedDurably(secondIntent);
        ExactItemInventoryRuntime runtime =
                new ExactItemInventoryRuntime(gateway);

        int firstBatch = ItemInventoryOnlineRecoveryBatch.recover(runtime,
                List.of(second, first), 1);

        assertEquals(1, firstBatch);
        assertEquals(ItemInventoryJournalStatus.COMMITTED,
                gateway.find(firstIntent.token().requestId())
                        .orElseThrow().status());
        assertEquals(ItemInventoryJournalStatus.PREPARED,
                gateway.find(secondIntent.token().requestId())
                        .orElseThrow().status());
        assertEquals(4, first.main.get(0).getCount());
        assertEquals(6, second.main.get(0).getCount());

        assertEquals(1, ItemInventoryOnlineRecoveryBatch.recover(runtime,
                List.of(first, second), 1));
        assertEquals(ItemInventoryJournalStatus.COMMITTED,
                gateway.find(secondIntent.token().requestId())
                        .orElseThrow().status());
        assertEquals(4, second.main.get(0).getCount());
    }

    @Test
    void invalidLimitsAndDuplicatePlayersFailClosed() {
        RepositoryGateway gateway = new RepositoryGateway();
        ExactItemInventoryRuntime runtime =
                new ExactItemInventoryRuntime(gateway);
        TestAccess access = new TestAccess(UUID.randomUUID());

        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryOnlineRecoveryBatch.recover(
                        runtime, List.of(access), 0));
        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryOnlineRecoveryBatch.recover(
                        runtime, List.of(access),
                        ItemInventoryOnlineRecoveryBatch.MAX_BATCH + 1));
        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryOnlineRecoveryBatch.recover(
                        runtime, List.of(access, access), 2));
    }

    private static final class RepositoryGateway
            implements DurableItemInventoryMutationGateway {
        private final PersistentItemInventoryJournal repository =
                new PersistentItemInventoryJournal();

        @Override
        public ItemInventoryGatewayResult appendPreparedDurably(
                ItemInventoryMutationIntent intent
        ) {
            return result(repository.applyCommitted(
                    ItemInventoryJournalTransition.prepare(intent)));
        }

        @Override
        public ItemInventoryGatewayResult appendCommittedDurably(
                ItemInventoryMutationReceipt receipt
        ) {
            return result(repository.applyCommitted(
                    ItemInventoryJournalTransition.commit(receipt)));
        }

        @Override
        public ItemInventoryGatewayResult appendAbortedDurably(
                ItemInventoryMutationAbort abort
        ) {
            return result(repository.applyCommitted(
                    ItemInventoryJournalTransition.abort(abort)));
        }

        @Override
        public ItemInventoryGatewayResult appendQuarantinedDurably(
                ItemInventoryMutationQuarantine quarantine
        ) {
            return result(repository.applyCommitted(
                    ItemInventoryJournalTransition.quarantine(quarantine)));
        }

        @Override
        public Optional<ItemInventoryJournalEntry> find(UUID requestId) {
            return repository.find(requestId);
        }

        @Override
        public List<ItemInventoryJournalEntry> preparedForPlayer(
                UUID playerId,
                int limit
        ) {
            return repository.preparedForPlayer(playerId, limit);
        }

        private static ItemInventoryGatewayResult result(
                ItemInventoryJournalApplyResult result
        ) {
            return new ItemInventoryGatewayResult(result.entry(),
                    result.replayed());
        }
    }

    private static final class TestAccess implements ItemInventoryAccess {
        private final UUID playerId;
        private final List<ItemStack> main = emptyMain();
        private ItemStack offhand = ItemStack.EMPTY;

        private TestAccess(UUID playerId) {
            this.playerId = playerId;
            main.set(0, new ItemStack(Items.EMERALD, 6));
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
}
