package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client result of an ATM withdrawal. */
public record S2CAtmResultPacket(boolean success, String code, long balanceMinor, long amountMinor) {
    public static void encode(S2CAtmResultPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.success());
        buffer.writeUtf(packet.code());
        buffer.writeLong(packet.balanceMinor());
        buffer.writeLong(packet.amountMinor());
    }

    public static S2CAtmResultPacket decode(FriendlyByteBuf buffer) {
        return new S2CAtmResultPacket(buffer.readBoolean(), buffer.readUtf(),
                buffer.readLong(), buffer.readLong());
    }

    public static void handle(S2CAtmResultPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handleAtmResult(packet)));
        context.setPacketHandled(true);
    }
}
