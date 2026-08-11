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

public record S2CServerShopOfferResultPacket(
        UUID requestId,
        String shopId,
        String listingId,
        String optionId,
        ServerShopOfferService.Status status,
        long resultingBalanceMinorUnits,
        boolean replayed
) {
    private static final int MAX_IDENTIFIER = 160;
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public S2CServerShopOfferResultPacket {
        requestId = java.util.Objects.requireNonNull(requestId, "requestId");
        shopId = validateIdentifier(shopId);
        listingId = validateIdentifier(listingId);
        optionId = validateIdentifier(optionId);
        status = java.util.Objects.requireNonNull(status, "status");
        if (requestId.equals(ZERO_UUID)) {
            throw new IllegalArgumentException(
                    "Server shop offer result request is invalid");
        }
    }

    public static S2CServerShopOfferResultPacket from(
            C2SServerShopOfferPacket request,
            ServerShopOfferService.Result result,
            long resultingBalanceMinorUnits
    ) {
        return new S2CServerShopOfferResultPacket(
                result.requestId(), request.shopId(),
                request.listingId(), request.optionId(),
                result.status(), resultingBalanceMinorUnits,
                result.replayed());
    }

    public static void encode(
            S2CServerShopOfferResultPacket packet,
            FriendlyByteBuf buffer
    ) {
        buffer.writeUUID(packet.requestId);
        buffer.writeUtf(packet.shopId, MAX_IDENTIFIER);
        buffer.writeUtf(packet.listingId, MAX_IDENTIFIER);
        buffer.writeUtf(packet.optionId, MAX_IDENTIFIER);
        buffer.writeUtf(packet.status.name(), 48);
        buffer.writeLong(packet.resultingBalanceMinorUnits);
        buffer.writeBoolean(packet.replayed);
    }

    public static S2CServerShopOfferResultPacket decode(
            FriendlyByteBuf buffer
    ) {
        try {
            return new S2CServerShopOfferResultPacket(
                    buffer.readUUID(),
                    buffer.readUtf(MAX_IDENTIFIER),
                    buffer.readUtf(MAX_IDENTIFIER),
                    buffer.readUtf(MAX_IDENTIFIER),
                    ServerShopOfferService.Status.valueOf(
                            buffer.readUtf(48)
                                    .toUpperCase(Locale.ROOT)),
                    buffer.readLong(),
                    buffer.readBoolean());
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Server shop offer result is malformed", exception);
        }
    }

    public static void handle(
            S2CServerShopOfferResultPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> ShopClientPacketHandler
                                .handleServerShopOfferResult(packet)));
        context.setPacketHandled(true);
    }

    private static String validateIdentifier(String value) {
        String candidate = java.util.Objects.requireNonNull(
                value, "identifier").strip();
        if (candidate.isEmpty() || candidate.length() > MAX_IDENTIFIER
                || !candidate.matches("[a-z0-9_.:/-]+")) {
            throw new IllegalArgumentException(
                    "Server shop offer result identifier is invalid");
        }
        return candidate;
    }
}
