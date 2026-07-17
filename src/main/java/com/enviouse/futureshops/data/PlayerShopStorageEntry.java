package com.enviouse.futureshops.data;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * One linked stock-storage container, sent to the owner for the Storage sub-tab's
 * "Linked storage" list. {@code blockId} is the block's registry id (e.g. "minecraft:chest")
 * so the CLIENT localizes the display name; {@code itemCount} is the total item count
 * currently held in that container.
 */
public record PlayerShopStorageEntry(BlockPos pos, String blockId, int itemCount) {
    public static void encode(FriendlyByteBuf buffer, PlayerShopStorageEntry entry) {
        buffer.writeBlockPos(entry.pos());
        buffer.writeUtf(entry.blockId());
        buffer.writeVarInt(entry.itemCount());
    }

    public static PlayerShopStorageEntry decode(FriendlyByteBuf buffer) {
        return new PlayerShopStorageEntry(buffer.readBlockPos(), buffer.readUtf(), buffer.readVarInt());
    }
}
