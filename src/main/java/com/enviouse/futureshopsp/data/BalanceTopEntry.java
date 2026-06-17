package com.enviouse.futureshopsp.data;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record BalanceTopEntry(UUID playerUuid, String playerName, long balanceMinorUnits) {
    public static void encode(FriendlyByteBuf buffer, BalanceTopEntry entry) {
        buffer.writeUUID(entry.playerUuid());
        buffer.writeUtf(entry.playerName());
        buffer.writeLong(entry.balanceMinorUnits());
    }

    public static BalanceTopEntry decode(FriendlyByteBuf buffer) {
        return new BalanceTopEntry(buffer.readUUID(), buffer.readUtf(), buffer.readLong());
    }
}

