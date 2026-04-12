package com.enviouse.futureshops.data;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client-side representation of one shop item as delivered by
 * {@code S2CShopDataPacket}.
 *
 * <p>Prices are in minor units (e.g. {@code 1250} = $12.50 at 2 decimal places).
 * {@code stock = -1} is the sentinel for unlimited stock.
 * {@code promoPrice} is only meaningful when {@code hasPromo = true}.
 */
public record CatalogItem(
        String itemId,
        String displayName,
        long buyPrice,
        long sellPrice,
        int stock,
        boolean unlimited,
        boolean barterEnabled,
        String categoryId,
        boolean hasPromo,
        long promoPrice,
        boolean hasBarterRecipes) {

    public static void encode(FriendlyByteBuf buf, CatalogItem item) {
        buf.writeUtf(item.itemId);
        buf.writeUtf(item.displayName);
        buf.writeLong(item.buyPrice);
        buf.writeLong(item.sellPrice);
        buf.writeVarInt(item.stock);
        buf.writeBoolean(item.unlimited);
        buf.writeBoolean(item.barterEnabled);
        buf.writeUtf(item.categoryId);
        buf.writeBoolean(item.hasPromo);
        buf.writeLong(item.promoPrice);
        buf.writeBoolean(item.hasBarterRecipes);
    }

    public static CatalogItem decode(FriendlyByteBuf buf) {
        return new CatalogItem(
                buf.readUtf(),
                buf.readUtf(),
                buf.readLong(),
                buf.readLong(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readLong(),
                buf.readBoolean());
    }
}


