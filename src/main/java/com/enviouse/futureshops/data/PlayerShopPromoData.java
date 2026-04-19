package com.enviouse.futureshops.data;

import net.minecraft.network.FriendlyByteBuf;

public record PlayerShopPromoData(
        boolean active,
        String promoType,
        double promoValue,
        int buyX,
        int buyY,
        boolean flash,
        long startEpochSeconds,
        long endEpochSeconds) {

    public static final PlayerShopPromoData NONE =
            new PlayerShopPromoData(false, "", 0.0D, 0, 0, false, 0L, 0L);

    public boolean configured() {
        return promoType != null && !promoType.isBlank();
    }

    public static void encode(FriendlyByteBuf buffer, PlayerShopPromoData data) {
        buffer.writeBoolean(data.active());
        buffer.writeUtf(data.promoType());
        buffer.writeDouble(data.promoValue());
        buffer.writeVarInt(data.buyX());
        buffer.writeVarInt(data.buyY());
        buffer.writeBoolean(data.flash());
        buffer.writeVarLong(Math.max(0L, data.startEpochSeconds()));
        buffer.writeVarLong(Math.max(0L, data.endEpochSeconds()));
    }

    public static PlayerShopPromoData decode(FriendlyByteBuf buffer) {
        return new PlayerShopPromoData(
                buffer.readBoolean(),
                buffer.readUtf(),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readVarLong(),
                buffer.readVarLong());
    }
}
