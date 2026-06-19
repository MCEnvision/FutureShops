package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.data.FranchiseMemberEntry;
import com.enviouse.futureshopsp.network.ShopPackets;
import com.enviouse.futureshopsp.server.shop.FranchiseSavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.enviouse.futureshopsp.Futureshops;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client → Server: Request franchise management UI data, or perform a franchise action.
 * action: "OPEN" = request data, "INVITE" = invite player, "KICK" = kick, "PROMOTE" = promote,
 *         "LEAVE" = leave, "DISBAND" = disband, "ACCEPT" = accept invite, "DECLINE" = decline
 */
public record C2SFranchiseActionPacket(String action, String targetName) implements CustomPacketPayload {
    public static final Type<C2SFranchiseActionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "c2sfranchiseactionpacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SFranchiseActionPacket> STREAM_CODEC = StreamCodec.ofMember(C2SFranchiseActionPacket::encode, C2SFranchiseActionPacket::decode);

    @Override
    public Type<C2SFranchiseActionPacket> type() {
        return TYPE;
    }

    public static void encode(C2SFranchiseActionPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.action, 32);
        buf.writeUtf(packet.targetName, 64);
    }

    public static C2SFranchiseActionPacket decode(FriendlyByteBuf buf) {
        return new C2SFranchiseActionPacket(buf.readUtf(32), buf.readUtf(64));
    }

    public static void handle(C2SFranchiseActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;
            FranchiseSavedData data = FranchiseSavedData.get(server);

            switch (packet.action) {
                case "OPEN" -> sendFranchiseData(player, server, data);
                case "INVITE" -> {
                    ServerPlayer target = server.getPlayerList().getPlayerByName(packet.targetName);
                    if (target != null) {
                        data.invite(player.getUUID(), target.getUUID());
                    }
                    sendFranchiseData(player, server, data);
                }
                case "KICK" -> {
                    UUID targetUuid = resolveUuid(server, packet.targetName);
                    if (targetUuid != null) data.kick(player.getUUID(), targetUuid);
                    sendFranchiseData(player, server, data);
                }
                case "PROMOTE" -> {
                    UUID targetUuid = resolveUuid(server, packet.targetName);
                    if (targetUuid != null) data.promote(player.getUUID(), targetUuid);
                    sendFranchiseData(player, server, data);
                }
                case "LEAVE" -> {
                    data.leave(player.getUUID());
                    sendFranchiseData(player, server, data);
                }
                case "DISBAND" -> {
                    data.disband(player.getUUID());
                    sendFranchiseData(player, server, data);
                }
                case "ACCEPT" -> {
                    data.acceptInvite(player.getUUID());
                    sendFranchiseData(player, server, data);
                }
                case "DECLINE" -> {
                    data.declineInvite(player.getUUID());
                    sendFranchiseData(player, server, data);
                }
            }
        });
    }

    public static void sendFranchiseDataToPlayer(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        FranchiseSavedData data = FranchiseSavedData.get(server);
        sendFranchiseData(player, server, data);
    }

    private static void sendFranchiseData(ServerPlayer player, MinecraftServer server, FranchiseSavedData data) {
        FranchiseSavedData.Franchise franchise = data.getFranchise(player.getUUID());
        boolean inFranchise = franchise != null;
        UUID franchiseId = inFranchise ? franchise.id : new UUID(0, 0);
        String franchiseName = inFranchise ? franchise.name : "";
        boolean isLeader = inFranchise && franchise.leader.equals(player.getUUID());

        List<FranchiseMemberEntry> members = new ArrayList<>();
        if (inFranchise) {
            for (UUID member : franchise.getMembers()) {
                String name = resolvePlayerName(server, member);
                boolean leader = member.equals(franchise.leader);
                boolean online = server.getPlayerList().getPlayer(member) != null;
                members.add(new FranchiseMemberEntry(member, name, leader, online));
            }
        }

        boolean hasPending = data.hasPendingInvite(player.getUUID());
        String pendingName = "";
        if (hasPending) {
            UUID pendingFranchiseId = data.getPendingInviteFranchise(player.getUUID());
            // Resolve franchise name from ID
            // We need to look up the franchise by iterating — add a helper
            for (var entry : data.getTopFranchises(100)) {
                if (entry.franchiseId().equals(pendingFranchiseId)) {
                    pendingName = entry.name();
                    break;
                }
            }
        }

        ShopPackets.sendToPlayer(player, new S2CFranchiseDataPacket(
                inFranchise, franchiseId, franchiseName, isLeader, members, hasPending, pendingName));
    }

    private static UUID resolveUuid(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return online.getUUID();
        try {
            return server.getProfileCache() != null
                    ? server.getProfileCache().get(name).map(p -> p.getId()).orElse(null)
                    : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String resolvePlayerName(MinecraftServer server, UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getGameProfile().getName();
        try {
            return server.getProfileCache() != null
                    ? server.getProfileCache().get(uuid).map(p -> p.getName()).orElse(uuid.toString().substring(0, 8))
                    : uuid.toString().substring(0, 8);
        } catch (Exception ignored) {
            return uuid.toString().substring(0, 8);
        }
    }
}


