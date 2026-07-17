package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;
import java.util.UUID;

public record S2CPlayerShopResultPacket(
        boolean success,
        String code,
        String chatMessage,
        UUID requestId,
        int responseToken
) {
    private static final UUID UNCORRELATED_REQUEST_ID = new UUID(0L, 0L);

    public S2CPlayerShopResultPacket {
        requestId = requestId == null ? UNCORRELATED_REQUEST_ID : requestId;
    }

    public S2CPlayerShopResultPacket(boolean success, String code, String chatMessage) {
        this(success, code, chatMessage, UNCORRELATED_REQUEST_ID, 0);
    }

    public static void encode(S2CPlayerShopResultPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.success());
        buffer.writeUtf(packet.code());
        buffer.writeUtf(packet.chatMessage());
        buffer.writeUUID(packet.requestId());
        buffer.writeVarInt(packet.responseToken());
    }

    public static S2CPlayerShopResultPacket decode(FriendlyByteBuf buffer) {
        return new S2CPlayerShopResultPacket(
                buffer.readBoolean(), buffer.readUtf(), buffer.readUtf(),
                buffer.readUUID(), buffer.readVarInt());
    }

    public static void handle(S2CPlayerShopResultPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handlePlayerShopResult(packet)));
        context.setPacketHandled(true);
    }
}
