package com.enviouse.futureshops.data;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client-side representation of an active promo/discount entry as delivered by
 * {@code S2CShopDataPacket}.
 *
 * <p>Type strings (mirror spec §23 / §25.7):
 * {@code PERCENTAGE}, {@code BUY_X_GET_Y}, {@code FLAT}, {@code FLASH},
 * {@code BARTER_DISCOUNT}.
 *
 * <p>{@code targetItemId} is empty when the promo applies to the whole shop.
 * {@code endTimeEpoch} is {@code 0} for permanent promos.
 */
public record CatalogPromo(
        String promoId,
        String promoType,
        String targetItemId,
        double discountValue,
        long endTimeEpoch) {

    public static void encode(FriendlyByteBuf buf, CatalogPromo promo) {
        buf.writeUtf(promo.promoId);
        buf.writeUtf(promo.promoType);
        buf.writeUtf(promo.targetItemId);
        buf.writeDouble(promo.discountValue);
        buf.writeLong(promo.endTimeEpoch);
    }

    public static CatalogPromo decode(FriendlyByteBuf buf) {
        return new CatalogPromo(
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readDouble(),
                buf.readLong());
    }
}


