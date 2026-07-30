package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.escrow.runtime.BazaarActionService;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Place a Bazaar order (plan §9). {@code side} = BUY / SELL, {@code orderType} = LIMIT / INSTANT,
 * {@code timeInForce} = GOOD_UNTIL_CANCELLED / GOOD_UNTIL_TIME / IMMEDIATE_OR_CANCEL /
 * FILL_OR_KILL (wire names of the backend enums; the server re-validates against config).
 * {@code limitPriceMinor} is the limit for resting orders and the WORST allowED execution price
 * for instant orders — the plan's slippage bound: instant buys never spend more than
 * {@code limit × quantity + fees}, instant sells never proceed below it. {@code paymentSource}
 * (wallet / physical) funds buy orders; physical money is consumed into escrow at placement,
 * never merely checked (plan §6).
 */
public record C2SBazaarOrderPacket(
        UUID requestId,
        UUID routeNonce,
        String productId,
        long productVersion,
        String side,
        String orderType,
        String timeInForce,
        long limitPriceMinor,
        int quantity,
        long expirationHours,
        String paymentSource
) {
    private static final UUID ZERO = new UUID(0L, 0L);
    public static final int MAX_TOKEN = 32;
    public static final int MAX_PRODUCT_ID = 128;
    public static final long MAX_MINOR = 1_000_000_000_000L;
    public static final int MAX_QUANTITY = 1_000_000;
    public static final long MAX_EXPIRATION_HOURS = 24L * 365L;

    public C2SBazaarOrderPacket {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(routeNonce, "routeNonce");
        productId = requireToken(productId, MAX_PRODUCT_ID, "productId");
        side = requireToken(side, MAX_TOKEN, "side");
        orderType = requireToken(orderType, MAX_TOKEN, "orderType");
        timeInForce = requireToken(timeInForce, MAX_TOKEN, "timeInForce");
        paymentSource = requireToken(paymentSource, MAX_TOKEN, "paymentSource");
        if (requestId.equals(ZERO)) {
            throw new IllegalArgumentException("Bazaar order requestId is required");
        }
        if (productVersion < 0L) {
            throw new IllegalArgumentException("Bazaar product version must not be negative");
        }
        if (limitPriceMinor <= 0L || limitPriceMinor > MAX_MINOR) {
            throw new IllegalArgumentException("Bazaar order price is out of bounds");
        }
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("Bazaar order quantity is out of bounds");
        }
        if (expirationHours < 0L || expirationHours > MAX_EXPIRATION_HOURS) {
            throw new IllegalArgumentException("Bazaar order expiration is out of bounds");
        }
    }

    private static String requireToken(String value, int maximum, String name) {
        String normalized = Objects.requireNonNull(value, name).strip();
        if (normalized.isEmpty() || normalized.length() > maximum) {
            throw new IllegalArgumentException("Bazaar order " + name + " is invalid");
        }
        return normalized;
    }

    public static void encode(C2SBazaarOrderPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.requestId());
        buffer.writeUUID(packet.routeNonce());
        buffer.writeUtf(packet.productId(), MAX_PRODUCT_ID);
        buffer.writeVarLong(packet.productVersion());
        buffer.writeUtf(packet.side(), MAX_TOKEN);
        buffer.writeUtf(packet.orderType(), MAX_TOKEN);
        buffer.writeUtf(packet.timeInForce(), MAX_TOKEN);
        buffer.writeVarLong(packet.limitPriceMinor());
        buffer.writeVarInt(packet.quantity());
        buffer.writeVarLong(packet.expirationHours());
        buffer.writeUtf(packet.paymentSource(), MAX_TOKEN);
    }

    public static C2SBazaarOrderPacket decode(FriendlyByteBuf buffer) {
        try {
            return new C2SBazaarOrderPacket(buffer.readUUID(), buffer.readUUID(),
                    buffer.readUtf(MAX_PRODUCT_ID), buffer.readVarLong(), buffer.readUtf(MAX_TOKEN),
                    buffer.readUtf(MAX_TOKEN), buffer.readUtf(MAX_TOKEN), buffer.readVarLong(),
                    buffer.readVarInt(), buffer.readVarLong(), buffer.readUtf(MAX_TOKEN));
        } catch (RuntimeException exception) {
            throw new DecoderException("Bazaar order request is invalid", exception);
        }
    }

    public static void handle(C2SBazaarOrderPacket packet,
                              Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                BazaarActionService.order(player, packet);
            }
        });
        context.setPacketHandled(true);
    }
}
