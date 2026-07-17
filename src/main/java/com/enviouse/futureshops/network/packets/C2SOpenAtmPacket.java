package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.economy.AtmService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server request to open or refresh the ATM. */
public record C2SOpenAtmPacket() {
    public static void encode(C2SOpenAtmPacket packet, FriendlyByteBuf buffer) {
    }

    public static C2SOpenAtmPacket decode(FriendlyByteBuf buffer) {
        return new C2SOpenAtmPacket();
    }

    public static void handle(C2SOpenAtmPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) AtmService.sendData(player, false);
        });
        context.setPacketHandled(true);
    }
}
