package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.market.MarketClaimCollectionClientState;
import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.server.market.claim.MarketClaimCollectionCode;
import com.enviouse.futureshops.server.market.claim.MarketClaimCollectionResult;
import com.enviouse.futureshops.server.market.claim.MarketClaimPresentationKind;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Supplier;

public record S2CMarketClaimCollectionPacket(
        MarketClaimCollectionResult result
) {
    public S2CMarketClaimCollectionPacket {
        result = Objects.requireNonNull(result, "result");
    }

    public static void encode(
            S2CMarketClaimCollectionPacket packet,
            FriendlyByteBuf buffer
    ) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(buffer, "buffer");
        MarketClaimCollectionResult result = packet.result();
        buffer.writeUUID(result.requestId());
        buffer.writeUUID(result.routeNonce());
        buffer.writeUtf(result.module().id(), 32);
        buffer.writeUtf(result.view(), 32);
        buffer.writeUUID(result.claimId());
        buffer.writeEnum(result.kind());
        buffer.writeEnum(result.code());
        buffer.writeVarLong(result.deliveredUnits());
        buffer.writeVarLong(result.remainingUnits());
        buffer.writeBoolean(result.resultingBalanceMinor().isPresent());
        if (result.resultingBalanceMinor().isPresent()) {
            buffer.writeLong(
                    result.resultingBalanceMinor().getAsLong());
        }
        buffer.writeBoolean(result.replayed());
        buffer.writeBoolean(result.refreshClaims());
    }

    public static S2CMarketClaimCollectionPacket decode(
            FriendlyByteBuf buffer
    ) {
        try {
            UUID requestId = buffer.readUUID();
            UUID routeNonce = buffer.readUUID();
            MarketModule module = MarketModule.fromId(
                    buffer.readUtf(32));
            String view = buffer.readUtf(32);
            UUID claimId = buffer.readUUID();
            MarketClaimPresentationKind kind = buffer.readEnum(
                    MarketClaimPresentationKind.class);
            MarketClaimCollectionCode code = buffer.readEnum(
                    MarketClaimCollectionCode.class);
            long delivered = buffer.readVarLong();
            long remaining = buffer.readVarLong();
            OptionalLong balance = buffer.readBoolean()
                    ? OptionalLong.of(buffer.readLong())
                    : OptionalLong.empty();
            boolean replayed = buffer.readBoolean();
            boolean refreshClaims = buffer.readBoolean();
            S2CMarketClaimCollectionPacket packet =
                    new S2CMarketClaimCollectionPacket(
                            new MarketClaimCollectionResult(requestId,
                                    routeNonce, module, view, claimId,
                                    kind, code, delivered, remaining,
                                    balance, replayed,
                                    refreshClaims));
            requireFullyRead(buffer);
            return packet;
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Market claim collection response is invalid",
                    exception);
        }
    }

    public static void handle(
            S2CMarketClaimCollectionPacket packet,
            Supplier<NetworkEvent.Context> supplier
    ) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT, () -> () ->
                        MarketClaimCollectionClientState.accept(
                                packet.result())));
        context.setPacketHandled(true);
    }

    private static void requireFullyRead(FriendlyByteBuf buffer) {
        if (buffer.isReadable()) {
            throw new IllegalArgumentException(
                    "Market claim collection response has trailing data");
        }
    }
}
