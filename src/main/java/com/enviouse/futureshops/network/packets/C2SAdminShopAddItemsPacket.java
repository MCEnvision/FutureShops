package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.shop.AdminShopEditService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Client → Server: batch-add admin shop listings from the item picker. All selected registry
 * ids share one category / price / stock; the server validates each id against the item
 * registry and writes the whole batch as a single admin.json mutation + reload.
 *
 * <p>Prices are minor units; {@code stock == -1} means unlimited.
 */
public record C2SAdminShopAddItemsPacket(
        List<String> itemIds,
        String categoryId,
        long buyMinor,
        long sellMinor,
        long stock,
        // Protocol 28: barter add. When barter=true the items become safe empty-recipe barter
        // targets, outputCount each; the client then opens their ingredient editors.
        boolean barter,
        int outputCount,
        int ingredientCount) {

    /** Convenience for the normal (money) add path — barter off. */
    public C2SAdminShopAddItemsPacket(List<String> itemIds, String categoryId, long buyMinor, long sellMinor, long stock) {
        this(itemIds, categoryId, buyMinor, sellMinor, stock, false, 1, 1);
    }

    private static final int MAX_ITEMS = 256;
    private static final int MAX_ITEM_ID_LENGTH = 256;
    private static final int MAX_CATEGORY_ID_LENGTH = 128;

    public static void encode(C2SAdminShopAddItemsPacket packet, FriendlyByteBuf buffer) {
        buffer.writeCollection(packet.itemIds,
                (target, value) -> target.writeUtf(value, MAX_ITEM_ID_LENGTH));
        buffer.writeUtf(packet.categoryId, MAX_CATEGORY_ID_LENGTH);
        buffer.writeLong(packet.buyMinor);
        buffer.writeLong(packet.sellMinor);
        buffer.writeLong(packet.stock);
        buffer.writeBoolean(packet.barter);
        buffer.writeVarInt(packet.outputCount);
        buffer.writeVarInt(packet.ingredientCount);
    }

    public static C2SAdminShopAddItemsPacket decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ITEMS) {
            throw new io.netty.handler.codec.DecoderException(
                    "C2SAdminShopAddItemsPacket itemIds out of range: " + count);
        }
        java.util.List<String> itemIds = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            itemIds.add(buffer.readUtf(MAX_ITEM_ID_LENGTH));
        }
        return new C2SAdminShopAddItemsPacket(
                itemIds,
                buffer.readUtf(MAX_CATEGORY_ID_LENGTH),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readVarInt());
    }

    public static void handle(C2SAdminShopAddItemsPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            AdminShopEditService.handleAddItems(player, packet);
        });
        context.setPacketHandled(true);
    }
}
