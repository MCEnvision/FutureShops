package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record S2CBalanceUiPacket(long balanceMinorUnits, String currencyName, int currencyDecimals) {
    public static void encode(S2CBalanceUiPacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.balanceMinorUnits());
        buffer.writeUtf(packet.currencyName());
        buffer.writeVarInt(packet.currencyDecimals());
    }

    public static S2CBalanceUiPacket decode(FriendlyByteBuf buffer) {
        return new S2CBalanceUiPacket(buffer.readLong(), buffer.readUtf(), buffer.readVarInt());
    }

    public static void handle(S2CBalanceUiPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handleBalanceUi(packet)));
        context.setPacketHandled(true);
    }
}

