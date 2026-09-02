package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.server.market.MarketClaimCollectionService;
import com.enviouse.futureshops.server.market.claim.MarketClaimCollectionCommand;
import com.enviouse.futureshops.server.market.claim.MarketClaimCollectionCode;
import com.enviouse.futureshops.server.market.claim.MarketClaimCollectionResult;
import io.netty.handler.codec.DecoderException;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;

public record C2SMarketClaimCollectionPacket(
        MarketClaimCollectionCommand command
) {
    private static final Logger LOGGER = LogUtils.getLogger();
    public C2SMarketClaimCollectionPacket {
        command = Objects.requireNonNull(command, "command");
    }

    public static void encode(
            C2SMarketClaimCollectionPacket packet,
            FriendlyByteBuf buffer
    ) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(buffer, "buffer");
        MarketClaimCollectionCommand command = packet.command();
        buffer.writeUUID(command.requestId());
        buffer.writeUUID(command.routeNonce());
        buffer.writeUtf(command.module().id(), 32);
        buffer.writeUtf(command.view(), 32);
        buffer.writeUUID(command.claimId());
    }

    public static C2SMarketClaimCollectionPacket decode(
            FriendlyByteBuf buffer
    ) {
        try {
            UUID requestId = buffer.readUUID();
            UUID routeNonce = buffer.readUUID();
            MarketModule module = MarketModule.fromId(
                    buffer.readUtf(32));
            String view = buffer.readUtf(32);
            UUID claimId = buffer.readUUID();
            C2SMarketClaimCollectionPacket packet =
                    new C2SMarketClaimCollectionPacket(
                            new MarketClaimCollectionCommand(requestId,
                                    routeNonce, module, view, claimId));
            requireFullyRead(buffer);
            return packet;
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Market claim collection request is invalid",
                    exception);
        }
    }

    public static void handle(
            C2SMarketClaimCollectionPacket packet,
            Supplier<NetworkEvent.Context> supplier
    ) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                try {
                    MarketClaimCollectionService.collect(player, packet);
                } catch (RuntimeException exception) {
                    LOGGER.error("FutureShops claim collection failed for player {} and request {}",
                            player.getUUID(), packet.command().requestId(), exception);
                    try {
                        ShopPackets.sendToPlayer(player,
                                new S2CMarketClaimCollectionPacket(
                                        MarketClaimCollectionResult.failure(
                                                packet.command(),
                                                MarketClaimCollectionCode.SERVER_ERROR)));
                    } catch (RuntimeException responseException) {
                        LOGGER.error("FutureShops claim collection error response failed for player {} and request {}",
                                player.getUUID(), packet.command().requestId(), responseException);
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }

    private static void requireFullyRead(FriendlyByteBuf buffer) {
        if (buffer.isReadable()) {
            throw new IllegalArgumentException(
                    "Market claim collection request has trailing data");
        }
    }
}
