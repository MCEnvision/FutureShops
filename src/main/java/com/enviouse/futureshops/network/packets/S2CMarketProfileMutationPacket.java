package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.server.market.profile.MarketProfileMutationResult;
import com.enviouse.futureshops.server.market.profile.MarketProfileMutationResultCode;
import com.enviouse.futureshops.server.market.profile.MarketProfileMutationType;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public record S2CMarketProfileMutationPacket(
        MarketProfileMutationResult result
) {
    public S2CMarketProfileMutationPacket {
        result = Objects.requireNonNull(result, "result");
    }

    public static void encode(
            S2CMarketProfileMutationPacket packet,
            FriendlyByteBuf buffer
    ) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(buffer, "buffer");
        MarketProfileMutationResult result = packet.result();
        buffer.writeUUID(result.requestId());
        buffer.writeUUID(result.routeNonce());
        buffer.writeUtf(result.module().id(), 32);
        buffer.writeEnum(result.type());
        buffer.writeEnum(result.resultCode());
        buffer.writeLong(result.profileRevision());
        buffer.writeVarInt(result.watchedAuctionCount());
        buffer.writeVarInt(result.favoriteProductCount());
        buffer.writeVarInt(result.priceAlertCount());
        buffer.writeVarInt(result.notificationCount());
        buffer.writeVarInt(result.unreadNotificationCount());
        buffer.writeVarInt(result.affectedCount());
        buffer.writeBoolean(result.changed());
        buffer.writeBoolean(result.replayed());
    }

    public static S2CMarketProfileMutationPacket decode(
            FriendlyByteBuf buffer
    ) {
        try {
            UUID requestId = buffer.readUUID();
            UUID routeNonce = buffer.readUUID();
            MarketModule module = MarketModule.fromId(
                    buffer.readUtf(32));
            MarketProfileMutationType type = buffer.readEnum(
                    MarketProfileMutationType.class);
            MarketProfileMutationResultCode resultCode =
                    buffer.readEnum(
                            MarketProfileMutationResultCode.class);
            MarketProfileMutationResult result =
                    new MarketProfileMutationResult(requestId,
                            routeNonce, module, type, resultCode,
                            buffer.readLong(), buffer.readVarInt(),
                            buffer.readVarInt(), buffer.readVarInt(),
                            buffer.readVarInt(), buffer.readVarInt(),
                            buffer.readVarInt(), buffer.readBoolean(),
                            buffer.readBoolean());
            S2CMarketProfileMutationPacket packet =
                    new S2CMarketProfileMutationPacket(result);
            requireFullyRead(buffer);
            return packet;
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Market profile mutation response is invalid",
                    exception);
        }
    }

    public static void handle(
            S2CMarketProfileMutationPacket packet,
            Supplier<NetworkEvent.Context> supplier
    ) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT, () -> () -> ShopClientPacketHandler
                        .handleMarketProfileMutation(packet)));
        context.setPacketHandled(true);
    }

    private static void requireFullyRead(FriendlyByteBuf buffer) {
        if (buffer.isReadable()) {
            throw new IllegalArgumentException(
                    "Market profile mutation response has trailing data");
        }
    }
}
