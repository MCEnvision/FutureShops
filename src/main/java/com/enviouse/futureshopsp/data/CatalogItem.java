package com.enviouse.futureshopsp.data;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client-side representation of one shop item as delivered by
 * {@code S2CShopDataPacket}.
 *
 * <p>{@code listingId} is the stable catalog <em>resolution key</em> the client echoes back in
 * buy/sell/cart requests to select exactly this listing (several listings may share an {@code itemId}
 * but each has a unique {@code listingId}). For legacy single-variant entries {@code listingId == itemId}.
 *
 * <p>{@code itemId} is the real registry id and is the ONLY field used to render the icon — it is a
 * valid {@link net.minecraft.resources.ResourceLocation}. The client MUST NOT call
 * {@code ResourceLocation.parse(listingId)}: a multi-variant listingId is not a resource location.
 *
 * <p>Prices are in minor units (e.g. {@code 1250} = $12.50 at 2 decimal places).
 * {@code stock = -1} is the sentinel for unlimited stock.
 * {@code promoPrice} is only meaningful when {@code hasPromo = true}.
 */
public record CatalogItem(
        String listingId,
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
        boolean hasBarterRecipes,
        String nbtJson) {

    public static void encode(FriendlyByteBuf buf, CatalogItem item) {
        buf.writeUtf(item.listingId != null ? item.listingId : item.itemId);
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
        buf.writeUtf(item.nbtJson != null ? item.nbtJson : "");
    }

    public static CatalogItem decode(FriendlyByteBuf buf) {
        return new CatalogItem(
                buf.readUtf(),
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
                buf.readBoolean(),
                buf.readUtf());
    }
}
