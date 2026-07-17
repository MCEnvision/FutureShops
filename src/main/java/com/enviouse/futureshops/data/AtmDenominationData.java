package com.enviouse.futureshops.data;

import net.minecraft.network.FriendlyByteBuf;

/** One physical-currency denomination advertised by the server ATM. */
public record AtmDenominationData(String itemId, long valueMinor, int maxStackSize) {
    public static void encode(FriendlyByteBuf buffer, AtmDenominationData value) {
        buffer.writeUtf(value.itemId());
        buffer.writeLong(value.valueMinor());
        buffer.writeVarInt(value.maxStackSize());
    }

    public static AtmDenominationData decode(FriendlyByteBuf buffer) {
        return new AtmDenominationData(buffer.readUtf(), buffer.readLong(), buffer.readVarInt());
    }
}
