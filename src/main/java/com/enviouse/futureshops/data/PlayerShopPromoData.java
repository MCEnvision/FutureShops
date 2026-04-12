package com.enviouse.futureshops.data;

import net.minecraft.network.FriendlyByteBuf;

public record PlayerShopPromoData(
        boolean active,
        String promoType,
        double promoValue,
        int buyX,
        int buyY,
        boolean flash) {

    public static final PlayerShopPromoData NONE = new PlayerShopPromoData(false, "", 0.0D, 0, 0, false);

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
    }

    public static PlayerShopPromoData decode(FriendlyByteBuf buffer) {
        return new PlayerShopPromoData(
                buffer.readBoolean(),
                buffer.readUtf(),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean());
    }
}

