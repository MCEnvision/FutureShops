package com.enviouse.futureshops.data;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * Network-safe DTO for franchise leaderboard display.
 */
public record FranchiseLeaderboardEntry(
        UUID franchiseId,
        String name,
        String leaderName,
        int memberCount) {

    public static void encode(FriendlyByteBuf buffer, FranchiseLeaderboardEntry entry) {
        buffer.writeUUID(entry.franchiseId());
        buffer.writeUtf(entry.name());
        buffer.writeUtf(entry.leaderName());
        buffer.writeVarInt(entry.memberCount());
    }

    public static FranchiseLeaderboardEntry decode(FriendlyByteBuf buffer) {
        return new FranchiseLeaderboardEntry(
                buffer.readUUID(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readVarInt());
    }
}

