package com.enviouse.futureshopsp.data;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * Network-safe DTO for a franchise member entry displayed in the franchise management UI.
 */
public record FranchiseMemberEntry(UUID uuid, String name, boolean leader, boolean online) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(uuid);
        buf.writeUtf(name, 64);
        buf.writeBoolean(leader);
        buf.writeBoolean(online);
    }

    public static FranchiseMemberEntry decode(FriendlyByteBuf buf) {
        return new FranchiseMemberEntry(buf.readUUID(), buf.readUtf(64), buf.readBoolean(), buf.readBoolean());
    }
}

