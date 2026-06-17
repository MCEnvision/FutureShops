package com.enviouse.futureshopsp.data;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client-side representation of a shop category as delivered by
 * {@code S2CShopDataPacket}.  All authority stays on the server.
 */
public record CatalogCategory(String id, String displayName, int sortOrder) {

    public static void encode(FriendlyByteBuf buf, CatalogCategory cat) {
        buf.writeUtf(cat.id);
        buf.writeUtf(cat.displayName);
        buf.writeVarInt(cat.sortOrder);
    }

    public static CatalogCategory decode(FriendlyByteBuf buf) {
        return new CatalogCategory(buf.readUtf(), buf.readUtf(), buf.readVarInt());
    }
}


