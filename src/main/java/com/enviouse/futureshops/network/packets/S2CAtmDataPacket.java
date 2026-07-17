package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.data.AtmDenominationData;
import com.enviouse.futureshops.money.CurrencyWithdrawalService;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server → client ATM balance, security mode, and live denomination catalog. */
public record S2CAtmDataPacket(
        long balanceMinor,
        String currencyName,
        int currencyDecimals,
        String providerId,
        boolean protectedMinting,
        String currencySignature,
        List<AtmDenominationData> denominations,
        boolean openScreen) {

    public static void encode(S2CAtmDataPacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.balanceMinor());
        buffer.writeUtf(packet.currencyName());
        buffer.writeVarInt(packet.currencyDecimals());
        buffer.writeUtf(packet.providerId());
        buffer.writeBoolean(packet.protectedMinting());
        buffer.writeUtf(packet.currencySignature(), 128);
        buffer.writeVarInt(packet.denominations().size());
        for (AtmDenominationData denomination : packet.denominations()) {
            AtmDenominationData.encode(buffer, denomination);
        }
        buffer.writeBoolean(packet.openScreen());
    }

    public static S2CAtmDataPacket decode(FriendlyByteBuf buffer) {
        long balance = buffer.readLong();
        String currencyName = buffer.readUtf();
        int decimals = buffer.readVarInt();
        String providerId = buffer.readUtf();
        boolean protectedMinting = buffer.readBoolean();
        String signature = buffer.readUtf(128);
        int size = buffer.readVarInt();
        if (size < 0 || size > CurrencyWithdrawalService.MAX_DENOMINATIONS) {
            throw new DecoderException("ATM denomination data out of range: " + size);
        }
        List<AtmDenominationData> denominations = new ArrayList<>(size);
        for (int i = 0; i < size; i++) denominations.add(AtmDenominationData.decode(buffer));
        boolean openScreen = buffer.readBoolean();
        return new S2CAtmDataPacket(balance, currencyName, decimals, providerId,
                protectedMinting, signature, denominations, openScreen);
    }

    public static void handle(S2CAtmDataPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handleAtmData(packet)));
        context.setPacketHandled(true);
    }
}
