package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.data.BulkSellQuote;
import com.enviouse.futureshops.data.BulkSellTarget;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.server.shop.BulkSellService;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

public record C2SBulkSellQuotePacket(
        BulkSellTarget target,
        String shopId,
        boolean selectEligibleByDefault
) {
    public C2SBulkSellQuotePacket {
        target = Objects.requireNonNull(target, "target");
        shopId = Objects.requireNonNull(shopId, "shopId").strip();
        if (shopId.isEmpty()
                || shopId.length() > BulkSellQuote.MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "Bulk sell quote request is invalid");
        }
    }

    public static void encode(
            C2SBulkSellQuotePacket packet,
            FriendlyByteBuf buffer
    ) {
        buffer.writeUtf(packet.target.name(), 32);
        buffer.writeUtf(packet.shopId,
                BulkSellQuote.MAX_TEXT_LENGTH);
        buffer.writeBoolean(packet.selectEligibleByDefault);
    }

    public static C2SBulkSellQuotePacket decode(
        FriendlyByteBuf buffer
    ) {
        try {
            C2SBulkSellQuotePacket packet =
                    new C2SBulkSellQuotePacket(
                    BulkSellTarget.valueOf(
                            buffer.readUtf(32)
                                    .toUpperCase(Locale.ROOT)),
                    buffer.readUtf(BulkSellQuote.MAX_TEXT_LENGTH),
                    buffer.readBoolean());
            BulkSellPacketCodec.requireComplete(
                    buffer, "Bulk sell quote request");
            return packet;
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Bulk sell quote request is malformed", exception);
        }
    }

    public static void handle(
            C2SBulkSellQuotePacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            BulkSellService.QuoteResult result =
                    BulkSellService.quote(
                            player, packet.target, packet.shopId,
                            packet.selectEligibleByDefault);
            ShopPackets.sendToPlayer(player,
                    S2CBulkSellQuotePacket.from(result));
        });
        context.setPacketHandled(true);
    }
}
