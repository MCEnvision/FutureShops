package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.escrow.runtime.AuctionActionService;
import com.enviouse.futureshops.money.PaymentSource;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Create an auction listing from an exact player-inventory slot (plan §8 listing creation: the
 * server re-checks slot content, restrictions, limits, and fee before escrow holds the item; the
 * listing becomes visible only after custody is durable). {@code buyoutMinor == 0} means no
 * A zero buyout means no buyout. A zero duration is only valid for fixed price listings.
 */
public record C2SAuctionCreatePacket(
        UUID requestId,
        UUID routeNonce,
        int inventorySlot,
        String listingType,
        long startingBidMinor,
        long buyoutMinor,
        long durationSeconds,
        int quantity,
        String itemFingerprint,
        String paymentSource
) {
    private static final UUID ZERO = new UUID(0L, 0L);
    /** Survival inventory slots only (0-40 covers main + hotbar + offhand + armor is excluded server-side). */
    public static final int MAX_SLOT = 63;
    public static final long MAX_MINOR = 1_000_000_000_000L;
    public static final long MAX_DURATION_SECONDS = 60L * 60L * 24L * 60L; // 60 days
    public static final int MAX_QUANTITY = 6400;
    public static final int FINGERPRINT_LENGTH = 64;

    public C2SAuctionCreatePacket {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(routeNonce, "routeNonce");
        listingType = Objects.requireNonNull(listingType, "listingType").strip();
        itemFingerprint = Objects.requireNonNull(itemFingerprint, "itemFingerprint")
                .strip().toLowerCase(java.util.Locale.ROOT);
        paymentSource = Objects.requireNonNull(paymentSource, "paymentSource").strip();
        if (requestId.equals(ZERO)) {
            throw new IllegalArgumentException("Auction create requestId is required");
        }
        if (itemFingerprint.length() != FINGERPRINT_LENGTH
                || !itemFingerprint.chars().allMatch(
                        character -> Character.digit(character, 16) >= 0)) {
            throw new IllegalArgumentException("Auction create fingerprint is invalid");
        }
        if (inventorySlot < 0 || inventorySlot > MAX_SLOT) {
            throw new IllegalArgumentException("Auction create slot is out of bounds");
        }
        if (listingType.isEmpty() || listingType.length() > 32) {
            throw new IllegalArgumentException("Auction listing type is invalid");
        }
        if (startingBidMinor < 0L || startingBidMinor > MAX_MINOR
                || buyoutMinor < 0L || buyoutMinor > MAX_MINOR) {
            throw new IllegalArgumentException("Auction create prices are out of bounds");
        }
        if (durationSeconds < 0L || durationSeconds > MAX_DURATION_SECONDS) {
            throw new IllegalArgumentException("Auction create duration is out of bounds");
        }
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("Auction create quantity is out of bounds");
        }
        if (PaymentSource.fromWire(paymentSource).isEmpty()) {
            throw new IllegalArgumentException("Auction payment source is invalid");
        }
    }

    public static void encode(C2SAuctionCreatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.requestId());
        buffer.writeUUID(packet.routeNonce());
        buffer.writeVarInt(packet.inventorySlot());
        buffer.writeUtf(packet.listingType(), 32);
        buffer.writeVarLong(packet.startingBidMinor());
        buffer.writeVarLong(packet.buyoutMinor());
        buffer.writeVarLong(packet.durationSeconds());
        buffer.writeVarInt(packet.quantity());
        buffer.writeUtf(packet.itemFingerprint(), FINGERPRINT_LENGTH);
        buffer.writeUtf(packet.paymentSource(), 16);
    }

    public static C2SAuctionCreatePacket decode(FriendlyByteBuf buffer) {
        try {
            return new C2SAuctionCreatePacket(buffer.readUUID(), buffer.readUUID(),
                    buffer.readVarInt(), buffer.readUtf(32), buffer.readVarLong(),
                    buffer.readVarLong(), buffer.readVarLong(), buffer.readVarInt(),
                    buffer.readUtf(FINGERPRINT_LENGTH), buffer.readUtf(16));
        } catch (RuntimeException exception) {
            throw new DecoderException("Auction create request is invalid", exception);
        }
    }

    /**
     * Click-time identity of the stack the player intends to list (plan §8 step 7: the server
     * re-checks slot, fingerprint, …). SHA-256 over registry id + raw NBT text — the SAME method
     * runs on both sides, so the server comparison rejects a slot whose content changed between
     * the client's selection and the server's processing instead of listing the wrong item.
     */
    public static String fingerprintOf(net.minecraft.world.item.ItemStack stack) {
        var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        String registryId = key == null ? "minecraft:air" : key.toString();
        String tag = stack.getTag() == null ? "" : stack.getTag().toString();
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(
                            (registryId + "|" + tag).getBytes(
                                    java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static void handle(C2SAuctionCreatePacket packet,
                              Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                AuctionActionService.create(player, packet);
            }
        });
        context.setPacketHandled(true);
    }
}
