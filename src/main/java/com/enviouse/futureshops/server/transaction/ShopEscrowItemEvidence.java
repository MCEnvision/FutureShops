package com.enviouse.futureshops.server.transaction;

import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayload;
import com.enviouse.futureshops.server.escrow.model.DimensionAwareShopReference;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class ShopEscrowItemEvidence {
    private ShopEscrowItemEvidence() {
    }

    static ItemStack exactStack(
            String itemId,
            String nbtJson,
            int count
    ) {
        if (count <= 0) {
            throw new IllegalArgumentException(
                    "Exact item evidence count is invalid");
        }
        Item item = ShopTransactionUtil.resolveItem(itemId);
        if (item == null || "minecraft:air".equals(itemId)) {
            throw new IllegalArgumentException(
                    "Exact item evidence registry identity is invalid");
        }
        ItemStack stack = new ItemStack(item, count);
        if (nbtJson != null && !nbtJson.isBlank()) {
            try {
                stack.setTag(TagParser.parseTag(nbtJson));
            } catch (Exception exception) {
                throw new IllegalArgumentException(
                        "Exact item evidence NBT is invalid", exception);
            }
        }
        return stack;
    }

    static byte[] exactTemplate(String itemId, String nbtJson) {
        return ItemStackSnapshotCodec.encode(exactStack(itemId, nbtJson, 1));
    }

    static List<ExactItemClaimPayload> captureOutput(
            UUID requestId,
            String sourceKey,
            ItemStack prototype,
            int totalCount
    ) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(sourceKey, "sourceKey");
        Objects.requireNonNull(prototype, "prototype");
        if (prototype.isEmpty() || totalCount <= 0) {
            throw new IllegalArgumentException(
                    "Exact item output is invalid");
        }
        int maximum = Math.max(1, Math.min(Byte.MAX_VALUE,
                prototype.getMaxStackSize()));
        int portionCount = Math.floorDiv(
                Math.addExact(totalCount, maximum - 1), maximum);
        if (portionCount <= 0
                || portionCount > ExactItemClaimPayload.MAX_PORTIONS) {
            throw new IllegalArgumentException(
                    "Exact item output has too many portions");
        }
        List<ExactItemClaimPayload> output = new ArrayList<>(
                portionCount);
        int remaining = totalCount;
        for (int index = 0; index < portionCount; index++) {
            int count = Math.min(remaining, maximum);
            ItemStack stack = prototype.copy();
            stack.setCount(count);
            output.add(ExactItemClaimPayload.capture(requestId, sourceKey,
                    index, portionCount, stack));
            remaining -= count;
        }
        if (remaining != 0) {
            throw new IllegalStateException(
                    "Exact item output partition is incomplete");
        }
        return List.copyOf(output);
    }

    static DimensionAwareShopReference shopReference(
            ServerPlayer player,
            String shopId
    ) {
        Objects.requireNonNull(player, "player");
        return new DimensionAwareShopReference(shopId,
                player.serverLevel().dimension().location().toString(),
                player.blockPosition().getX(),
                player.blockPosition().getY(),
                player.blockPosition().getZ());
    }
}
