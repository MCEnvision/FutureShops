package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayload;
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

final class ServerShopBarterTestFixtures {
    static final UUID REQUEST_ID = UUID.fromString(
            "31000000-0000-0000-0000-000000000001");
    static final UUID PLAYER_ID = UUID.fromString(
            "32000000-0000-0000-0000-000000000002");
    static final Instant QUOTED_AT = Instant.parse(
            "2026-07-21T12:00:00.123456789Z");
    static final Instant APPLIED_AT = Instant.parse(
            "2026-07-21T12:00:02.987654321Z");

    private ServerShopBarterTestFixtures() {
    }

    static ServerShopBarterCommit commit() {
        ServerShopBarterService.PreparedRequest request = request();
        return ServerShopBarterCommit.create(REQUEST_ID, PLAYER_ID,
                "default", "rare.exchange", 2, 17L, 29L,
                QUOTED_AT, request.ingredients(), request.outputs(),
                receipt(request, APPLIED_AT), shopReference());
    }

    static ServerShopBarterService.PreparedRequest request() {
        return request(REQUEST_ID, "rare.exchange", 2, 17L, 29L);
    }

    static ServerShopBarterService.PreparedRequest request(
            UUID requestId,
            String recipeId,
            int multiplier,
            long quoteRevision,
            long recipeRevision
    ) {
        return new ServerShopBarterService.PreparedRequest(
                new ServerShopBarterService.Identity(requestId,
                        PLAYER_ID, "default", recipeId, multiplier),
                quoteRevision, recipeRevision, QUOTED_AT,
                ingredients(), outputs(requestId, multiplier),
                shopReference());
    }

    static List<ServerShopBarterCommit.Ingredient> ingredients() {
        return List.of(new ServerShopBarterCommit.Ingredient(0,
                        "emerald.exact", "minecraft:emerald", 2,
                        emeraldTemplate()),
                new ServerShopBarterCommit.Ingredient(1,
                        "diamond.exact", "minecraft:diamond", 1,
                        diamondTemplate()));
    }

    static List<ServerShopBarterCommit.OutputLine> outputs(
            UUID requestId,
            int multiplier
    ) {
        int appleTotal = Math.multiplyExact(3, multiplier);
        int first = appleTotal / 2 + appleTotal % 2;
        int second = appleTotal - first;
        String appleSource = ServerShopBarterCommit.outputSourceKey(
                requestId, 0);
        List<ExactItemClaimPayload> apples = List.of(
                ExactItemClaimPayload.capture(requestId, appleSource,
                        0, 2, rewardApple(first)),
                ExactItemClaimPayload.capture(requestId, appleSource,
                        1, 2, rewardApple(second)));
        String goldSource = ServerShopBarterCommit.outputSourceKey(
                requestId, 1);
        List<ExactItemClaimPayload> gold = List.of(
                ExactItemClaimPayload.capture(requestId, goldSource,
                        0, 1, rewardGold(multiplier)));
        return List.of(new ServerShopBarterCommit.OutputLine(0,
                        "reward.apple", "minecraft:golden_apple", 3,
                        41L, apples),
                new ServerShopBarterCommit.OutputLine(1,
                        "reward.gold", "minecraft:gold_ingot", 1,
                        43L, gold));
    }

    static ItemInventoryMutationReceipt receipt(
            ServerShopBarterService.PreparedRequest request,
            Instant appliedAt
    ) {
        List<ItemStack> main = emptyMain();
        ItemStack emeraldFirst = taggedEmerald(3);
        ItemStack emeraldSecond = taggedEmerald(4);
        ItemStack diamonds = taggedDiamond(5);
        main.set(0, emeraldFirst);
        main.set(1, diamonds);
        main.set(2, emeraldSecond);
        ItemInventoryState before = ItemInventoryState.of(
                main, ItemStack.EMPTY);
        ItemInventoryMutationPlan plan = ItemInventoryBatchPlanner.plan(
                before, ServerShopBarterCommit.custodyEntries(
                        request.identity().requestId(),
                        request.identity().multiplier(),
                        request.ingredients()));
        ItemInventoryMutationToken token =
                ItemInventoryMutationToken.create(
                        request.identity().playerId(),
                        request.identity().requestId(),
                        ServerShopBarterCommit.ingredientCustodyRequestId(
                                request.identity().requestId()), plan);
        return ItemInventoryMutationReceipt.create(token, plan,
                appliedAt);
    }

    static byte[] emeraldTemplate() {
        return ItemStackSnapshotCodec.encode(taggedEmerald(1));
    }

    static byte[] diamondTemplate() {
        return ItemStackSnapshotCodec.encode(taggedDiamond(1));
    }

    static ItemStack taggedEmerald(int count) {
        ItemStack stack = new ItemStack(Items.EMERALD, count);
        CompoundTag tag = new CompoundTag();
        tag.putString("barter_variant", "forest");
        tag.putInt("quality", 7);
        stack.setTag(tag);
        return stack;
    }

    static ItemStack taggedDiamond(int count) {
        ItemStack stack = new ItemStack(Items.DIAMOND, count);
        CompoundTag tag = new CompoundTag();
        tag.putString("barter_variant", "deep");
        tag.putInt("quality", 11);
        stack.setTag(tag);
        return stack;
    }

    static ItemStack rewardApple(int count) {
        ItemStack stack = new ItemStack(Items.GOLDEN_APPLE, count);
        stack.getOrCreateTag().putString("reward", "bundle");
        return stack;
    }

    static ItemStack rewardGold(int count) {
        ItemStack stack = new ItemStack(Items.GOLD_INGOT, count);
        stack.getOrCreateTag().putString("reward", "single");
        return stack;
    }

    static DimensionAwareShopReference shopReference() {
        return new DimensionAwareShopReference("default",
                "minecraft:overworld", 12, 70, -8);
    }

    private static List<ItemStack> emptyMain() {
        return new ArrayList<>(Collections.nCopies(
                ItemInventorySlot.MAIN_SLOT_COUNT, ItemStack.EMPTY));
    }
}
