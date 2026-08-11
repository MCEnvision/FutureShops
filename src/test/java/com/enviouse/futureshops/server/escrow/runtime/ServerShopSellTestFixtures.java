package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchPlanner;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationPlan;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationToken;
import com.enviouse.futureshops.server.escrow.item.ItemInventorySlot;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryState;
import com.enviouse.futureshops.server.escrow.model.DimensionAwareShopReference;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class ServerShopSellTestFixtures {
    static final UUID REQUEST_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    static final UUID PLAYER_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000002");
    static final Instant QUOTED_AT = Instant.parse(
            "2026-07-20T12:00:00Z");
    static final Instant APPLIED_AT = Instant.parse(
            "2026-07-20T12:00:01.123456789Z");

    private ServerShopSellTestFixtures() {
    }

    static ServerShopSellCommit commit() {
        byte[] template = template();
        ItemInventoryMutationReceipt receipt = receipt(REQUEST_ID,
                PLAYER_ID, 3, template, APPLIED_AT);
        return ServerShopSellCommit.create(REQUEST_ID, PLAYER_ID,
                "default", "emerald.offer", "minecraft:emerald", 3,
                100L, 7L, 11L, QUOTED_AT, 0L, -150L, 0L,
                100L, 4L, "Credits", 2, template, receipt,
                new DimensionAwareShopReference("default",
                        "minecraft:overworld", 4, 64, 8));
    }

    static ServerShopSellService.PreparedRequest request() {
        return new ServerShopSellService.PreparedRequest(
                new ServerShopSellService.Identity(REQUEST_ID, PLAYER_ID,
                        "default", "emerald.offer", 3),
                "minecraft:emerald", 100L, 7L, 11L, QUOTED_AT,
                template(), new DimensionAwareShopReference("default",
                "minecraft:overworld", 4, 64, 8));
    }

    static ServerShopSellService.WalletSnapshot wallet() {
        return new ServerShopSellService.WalletSnapshot(
                0L, -150L, 0L, 100L, 4L, "Credits", 2);
    }

    static byte[] template() {
        ItemStack template = taggedStack(1);
        return ItemStackSnapshotCodec.encode(template);
    }

    static ItemInventoryMutationReceipt receipt(
            UUID requestId,
            UUID playerId,
            int quantity,
            byte[] template,
            Instant appliedAt
    ) {
        ItemStack held = ItemStackSnapshotCodec.decode(template);
        held.setCount(quantity + 2);
        List<ItemStack> main = emptyMain();
        main.set(0, held);
        ItemInventoryState before = ItemInventoryState.of(
                main, ItemStack.EMPTY);
        ItemInventoryMutationPlan plan = ItemInventoryBatchPlanner.plan(
                before, ServerShopSellCommit.custodyEntries(requestId,
                        quantity, template));
        ItemInventoryMutationToken token =
                ItemInventoryMutationToken.create(playerId, requestId,
                        ServerShopSellCommit.itemCustodyRequestId(requestId),
                        plan);
        return ItemInventoryMutationReceipt.create(token, plan, appliedAt);
    }

    static ItemStack taggedStack(int count) {
        ItemStack stack = new ItemStack(Items.EMERALD, count);
        CompoundTag tag = new CompoundTag();
        tag.putString("sell_variant", "exact");
        tag.putInt("quality", 9);
        stack.setTag(tag);
        return stack;
    }

    private static List<ItemStack> emptyMain() {
        return new ArrayList<>(java.util.Collections.nCopies(
                ItemInventorySlot.MAIN_SLOT_COUNT, ItemStack.EMPTY));
    }
}
