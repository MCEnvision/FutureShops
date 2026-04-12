package com.enviouse.futureshops.data;

import net.minecraft.network.FriendlyByteBuf;

public record BalanceTopEntry(String playerName, long balanceMinorUnits) {
    public static void encode(FriendlyByteBuf buffer, BalanceTopEntry entry) {
        buffer.writeUtf(entry.playerName());
        buffer.writeLong(entry.balanceMinorUnits());
    }

    public static BalanceTopEntry decode(FriendlyByteBuf buffer) {
        return new BalanceTopEntry(buffer.readUtf(), buffer.readLong());
    }
}

