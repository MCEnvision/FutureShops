package com.enviouse.futureshopsp.data;

import net.minecraft.network.FriendlyByteBuf;

public record OwnedShopSummary(
        String dimensionKey,
        long shopPosLong,
        String featuredItemId,
        int listingCount,
        int totalStock,
        int lowStockListings,
        boolean linked,
        long pendingMinor,
        long lifetimeMinor) {

    public static void encode(FriendlyByteBuf buffer, OwnedShopSummary summary) {
        buffer.writeUtf(summary.dimensionKey());
        buffer.writeLong(summary.shopPosLong());
        buffer.writeUtf(summary.featuredItemId());
        buffer.writeVarInt(summary.listingCount());
        buffer.writeVarInt(summary.totalStock());
        buffer.writeVarInt(summary.lowStockListings());
        buffer.writeBoolean(summary.linked());
        buffer.writeLong(summary.pendingMinor());
        buffer.writeLong(summary.lifetimeMinor());
    }

    public static OwnedShopSummary decode(FriendlyByteBuf buffer) {
        return new OwnedShopSummary(
                buffer.readUtf(),
                buffer.readLong(),
                buffer.readUtf(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readLong(),
                buffer.readLong());
    }
}

