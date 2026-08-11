package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.server.escrow.item.ItemInputMatcher;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchEntry;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchPlanner;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationPlan;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationToken;
import com.enviouse.futureshops.server.escrow.item.ItemInventorySlot;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ItemInventoryJournalTestFixtures {
    public static final Instant NOW = Instant.parse(
            "2026-07-17T12:34:56.789Z");

    private ItemInventoryJournalTestFixtures() {
    }

    public static ItemInventoryMutationIntent intent(
            UUID playerId,
            UUID transactionId,
            UUID requestId
    ) {
        List<ItemStack> main = new ArrayList<>();
        for (int index = 0;
             index < ItemInventorySlot.MAIN_SLOT_COUNT;
             index++) {
            main.add(ItemStack.EMPTY);
        }
        main.set(0, new ItemStack(Items.EMERALD, 6));
        ItemInventoryMutationPlan plan = ItemInventoryBatchPlanner.plan(
                ItemInventoryState.of(main, ItemStack.EMPTY),
                List.of(ItemInventoryBatchEntry.extract(
                        UUID.nameUUIDFromBytes(("entry." + requestId)
                                .getBytes(StandardCharsets.UTF_8)),
                        ItemInputMatcher.itemOnly("minecraft:emerald"), 2)));
        ItemInventoryMutationToken token = ItemInventoryMutationToken.create(
                playerId, transactionId, requestId, plan);
        ItemInventoryMutationReceipt receipt =
                ItemInventoryMutationReceipt.create(token, plan, NOW);
        return ItemInventoryMutationIntent.create(token, plan, receipt);
    }
}
