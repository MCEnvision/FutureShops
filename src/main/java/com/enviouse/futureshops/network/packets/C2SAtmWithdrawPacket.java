package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.money.CurrencyWithdrawalService;
import com.enviouse.futureshops.server.economy.AtmService;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Client → server exact denomination counts chosen in the ATM. */
public record C2SAtmWithdrawPacket(String currencySignature, List<Integer> denominationCounts) {
    public static void encode(C2SAtmWithdrawPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.currencySignature(), 128);
        buffer.writeVarInt(packet.denominationCounts().size());
        for (int count : packet.denominationCounts()) buffer.writeVarInt(count);
    }

    public static C2SAtmWithdrawPacket decode(FriendlyByteBuf buffer) {
        String signature = buffer.readUtf(128);
        int size = buffer.readVarInt();
        if (size < 0 || size > CurrencyWithdrawalService.MAX_DENOMINATIONS) {
            throw new DecoderException("ATM denomination count out of range: " + size);
        }
        List<Integer> counts = new ArrayList<>(size);
        for (int i = 0; i < size; i++) counts.add(buffer.readVarInt());
        return new C2SAtmWithdrawPacket(signature, counts);
    }

    public static void handle(C2SAtmWithdrawPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) AtmService.withdraw(player, packet.currencySignature(), packet.denominationCounts());
        });
        context.setPacketHandled(true);
    }
}
