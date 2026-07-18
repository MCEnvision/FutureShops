package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Shared response for every Auction House / Bazaar mutating action (plan §12: every response
 * carries the request UUID, the created transaction UUID when one exists, a result status, the
 * new entity revision, the public config revision, and the server timestamp). {@code status} is
 * the stable wire name of the module's operation status enum (e.g. {@code APPLIED},
 * {@code STALE_REVISION}); {@code detail} is an optional machine argument (listing/order id,
 * required minimum bid, …) the client localizes — never pre-rendered English.
 */
public record S2CMarketActionResponsePacket(
        UUID requestId,
        UUID transactionId,
        String moduleId,
        String action,
        String status,
        long newRevision,
        long configRevision,
        long serverTimestamp,
        String detail
) {
    public static final UUID NO_TRANSACTION = new UUID(0L, 0L);
    private static final int MAX_TOKEN = 48;
    private static final int MAX_DETAIL = 96;

    public S2CMarketActionResponsePacket {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(transactionId, "transactionId");
        moduleId = requireToken(moduleId, "moduleId");
        action = requireToken(action, "action");
        status = requireToken(status, "status");
        detail = Objects.requireNonNull(detail, "detail").strip();
        if (detail.length() > MAX_DETAIL) {
            throw new IllegalArgumentException("Market action detail is too long");
        }
        if (requestId.equals(NO_TRANSACTION)) {
            throw new IllegalArgumentException("Market action requestId is required");
        }
        if (newRevision < 0L || configRevision < 0L) {
            throw new IllegalArgumentException("Market action revisions must not be negative");
        }
    }

    private static String requireToken(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).strip();
        if (normalized.isEmpty() || normalized.length() > MAX_TOKEN) {
            throw new IllegalArgumentException("Market action " + name + " is invalid");
        }
        return normalized;
    }

    public boolean applied() {
        return "APPLIED".equals(status);
    }

    public static void encode(S2CMarketActionResponsePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.requestId());
        buffer.writeUUID(packet.transactionId());
        buffer.writeUtf(packet.moduleId(), MAX_TOKEN);
        buffer.writeUtf(packet.action(), MAX_TOKEN);
        buffer.writeUtf(packet.status(), MAX_TOKEN);
        buffer.writeVarLong(packet.newRevision());
        buffer.writeVarLong(packet.configRevision());
        buffer.writeLong(packet.serverTimestamp());
        buffer.writeUtf(packet.detail(), MAX_DETAIL);
    }

    public static S2CMarketActionResponsePacket decode(FriendlyByteBuf buffer) {
        try {
            return new S2CMarketActionResponsePacket(buffer.readUUID(), buffer.readUUID(),
                    buffer.readUtf(MAX_TOKEN), buffer.readUtf(MAX_TOKEN), buffer.readUtf(MAX_TOKEN),
                    buffer.readVarLong(), buffer.readVarLong(), buffer.readLong(),
                    buffer.readUtf(MAX_DETAIL));
        } catch (RuntimeException exception) {
            throw new DecoderException("Market action response is invalid", exception);
        }
    }

    public static void handle(S2CMarketActionResponsePacket packet,
                              Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handleMarketActionResponse(packet)));
        context.setPacketHandled(true);
    }
}
