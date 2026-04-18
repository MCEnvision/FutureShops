package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.data.FranchiseMemberEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server → Client: Full franchise data for the management screen.
 */
public record S2CFranchiseDataPacket(
        boolean inFranchise,
        UUID franchiseId,
        String franchiseName,
        boolean isLeader,
        List<FranchiseMemberEntry> members,
        boolean hasPendingInvite,
        String pendingFranchiseName
) {
    public static void encode(S2CFranchiseDataPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.inFranchise);
        if (packet.inFranchise) {
            buf.writeUUID(packet.franchiseId);
            buf.writeUtf(packet.franchiseName, 64);
            buf.writeBoolean(packet.isLeader);
            buf.writeVarInt(packet.members.size());
            for (FranchiseMemberEntry m : packet.members) {
                m.encode(buf);
            }
        }
        buf.writeBoolean(packet.hasPendingInvite);
        if (packet.hasPendingInvite) {
            buf.writeUtf(packet.pendingFranchiseName, 64);
        }
    }

    public static S2CFranchiseDataPacket decode(FriendlyByteBuf buf) {
        boolean inFranchise = buf.readBoolean();
        UUID franchiseId = null;
        String franchiseName = "";
        boolean isLeader = false;
        List<FranchiseMemberEntry> members = List.of();
        if (inFranchise) {
            franchiseId = buf.readUUID();
            franchiseName = buf.readUtf(64);
            isLeader = buf.readBoolean();
            int count = buf.readVarInt();
            List<FranchiseMemberEntry> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                list.add(FranchiseMemberEntry.decode(buf));
            }
            members = List.copyOf(list);
        }
        boolean hasPendingInvite = buf.readBoolean();
        String pendingFranchiseName = hasPendingInvite ? buf.readUtf(64) : "";
        return new S2CFranchiseDataPacket(inFranchise, franchiseId, franchiseName, isLeader, members, hasPendingInvite, pendingFranchiseName);
    }

    public static void handle(S2CFranchiseDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ShopClientPacketHandler.handleFranchiseData(packet));
        ctx.get().setPacketHandled(true);
    }
}

