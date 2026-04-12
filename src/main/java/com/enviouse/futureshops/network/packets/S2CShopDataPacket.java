package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record S2CShopDataPacket(String shopId, long balanceMinorUnits, String currencyName, int currencyDecimals) {
    public static void encode(S2CShopDataPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shopId);
        buffer.writeLong(packet.balanceMinorUnits);
        buffer.writeUtf(packet.currencyName);
        buffer.writeVarInt(packet.currencyDecimals);
    }

    public static S2CShopDataPacket decode(FriendlyByteBuf buffer) {
        return new S2CShopDataPacket(buffer.readUtf(), buffer.readLong(), buffer.readUtf(), buffer.readVarInt());
    }

    public static void handle(S2CShopDataPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ShopClientPacketHandler.handleShopData(packet)));
        context.setPacketHandled(true);
    }
}
