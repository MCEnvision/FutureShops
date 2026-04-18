package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record S2CPlayerShopResultPacket(boolean success, String code, String chatMessage) {
    public static void encode(S2CPlayerShopResultPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.success());
        buffer.writeUtf(packet.code());
        buffer.writeUtf(packet.chatMessage());
    }

    public static S2CPlayerShopResultPacket decode(FriendlyByteBuf buffer) {
        return new S2CPlayerShopResultPacket(buffer.readBoolean(), buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(S2CPlayerShopResultPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handlePlayerShopResult(packet)));
        context.setPacketHandled(true);
    }
}
