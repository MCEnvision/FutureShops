package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.data.BalanceTopEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public record S2CBalTopUiPacket(int page, int totalPages, List<BalanceTopEntry> entries, String currencyName, int currencyDecimals) {
    public static void encode(S2CBalTopUiPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.page());
        buffer.writeVarInt(packet.totalPages());
        buffer.writeCollection(packet.entries(), BalanceTopEntry::encode);
        buffer.writeUtf(packet.currencyName());
        buffer.writeVarInt(packet.currencyDecimals());
    }

    public static S2CBalTopUiPacket decode(FriendlyByteBuf buffer) {
        return new S2CBalTopUiPacket(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readList(BalanceTopEntry::decode),
                buffer.readUtf(),
                buffer.readVarInt());
    }

    public static void handle(S2CBalTopUiPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handleBalTopUi(packet)));
        context.setPacketHandled(true);
    }
}

