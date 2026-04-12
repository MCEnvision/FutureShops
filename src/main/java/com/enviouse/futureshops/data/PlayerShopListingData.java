package com.enviouse.futureshops.data;

import net.minecraft.network.FriendlyByteBuf;

public record PlayerShopListingData(
        String itemId,
        String tradeMode,
        long moneyPriceMinor,
        long effectiveUnitPriceMinor,
        String barterItemId,
        int barterItemCount,
        int stock,
        PlayerShopPromoData promo) {

    public static void encode(FriendlyByteBuf buffer, PlayerShopListingData data) {
        buffer.writeUtf(data.itemId());
        buffer.writeUtf(data.tradeMode());
        buffer.writeLong(data.moneyPriceMinor());
        buffer.writeLong(data.effectiveUnitPriceMinor());
        buffer.writeUtf(data.barterItemId());
        buffer.writeVarInt(data.barterItemCount());
        buffer.writeVarInt(data.stock());
        PlayerShopPromoData.encode(buffer, data.promo());
    }

    public static PlayerShopListingData decode(FriendlyByteBuf buffer) {
        return new PlayerShopListingData(
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readUtf(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                PlayerShopPromoData.decode(buffer));
    }
}

