package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopOfferService;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

public record S2CServerShopOfferCartResultPacket(
        UUID requestId,
        String shopId,
        ServerShopOfferService.Status status,
        long resultingBalanceMinorUnits,
        boolean replayed
) {
    private static final int MAX_IDENTIFIER = 160;
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public S2CServerShopOfferCartResultPacket {
        requestId = java.util.Objects.requireNonNull(
                requestId, "requestId");
        shopId = java.util.Objects.requireNonNull(
                shopId, "shopId").strip();
        status = java.util.Objects.requireNonNull(status, "status");
        if (requestId.equals(ZERO_UUID)
                || shopId.isEmpty()
                || shopId.length() > MAX_IDENTIFIER) {
            throw new IllegalArgumentException(
                    "Server shop offer cart result is invalid");
        }
    }

    public static void encode(
            S2CServerShopOfferCartResultPacket packet,
            FriendlyByteBuf buffer
    ) {
        buffer.writeUUID(packet.requestId);
        buffer.writeUtf(packet.shopId, MAX_IDENTIFIER);
        buffer.writeUtf(packet.status.name(), 48);
        buffer.writeLong(packet.resultingBalanceMinorUnits);
        buffer.writeBoolean(packet.replayed);
    }

    public static S2CServerShopOfferCartResultPacket decode(
            FriendlyByteBuf buffer
    ) {
        try {
            return new S2CServerShopOfferCartResultPacket(
                    buffer.readUUID(),
                    buffer.readUtf(MAX_IDENTIFIER),
                    ServerShopOfferService.Status.valueOf(
                            buffer.readUtf(48)
                                    .toUpperCase(Locale.ROOT)),
                    buffer.readLong(), buffer.readBoolean());
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Server shop offer cart result is malformed",
                    exception);
        }
    }

    public static void handle(
            S2CServerShopOfferCartResultPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> ShopClientPacketHandler
                                .handleServerShopOfferCartResult(packet)));
        context.setPacketHandled(true);
    }
}
