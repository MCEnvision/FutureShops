package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.catalog.offer.OfferAction;
import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopOfferCommit;
import com.enviouse.futureshops.server.shop.PlayerShopBlockService;
import com.enviouse.futureshops.server.transaction.ShopTransactionUtil;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public record C2SPlayerShopOfferPacket(
        BlockPos shopPos,
        int listingIndex,
        String listingId,
        String optionId,
        OfferAction action,
        int quantity,
        long expectedOfferRevision,
        Optional<PaymentSource> paymentSource,
        UUID requestId,
        int responseToken
) {
    public static final int MAX_IDENTIFIER_LENGTH = 160;
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public C2SPlayerShopOfferPacket {
        shopPos = Objects.requireNonNull(shopPos, "shopPos");
        listingId = identifier(listingId, "listingId");
        optionId = identifier(optionId, "optionId");
        action = Objects.requireNonNull(action, "action");
        paymentSource = Objects.requireNonNull(
                paymentSource, "paymentSource");
        requestId = Objects.requireNonNull(requestId, "requestId");
        if (!ShopTransactionUtil.isValidPlayerShopListingIndex(
                listingIndex)
                || !ShopTransactionUtil.isValidBuyQuantity(quantity)
                || expectedOfferRevision < 0L
                || expectedOfferRevision
                > ServerShopOfferCommit.MAX_REVISION
                || ZERO_UUID.equals(requestId)
                || action == OfferAction.SELL_TO_SHOP
                && paymentSource.isPresent()
                || !ShopTransactionUtil.isValidPlayerShopResponseToken(
                responseToken)) {
            throw new IllegalArgumentException(
                    "Player shop offer packet is invalid");
        }
    }

    public static void encode(
            C2SPlayerShopOfferPacket packet,
            FriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(packet.shopPos());
        buffer.writeVarInt(packet.listingIndex());
        buffer.writeUtf(packet.listingId(), MAX_IDENTIFIER_LENGTH);
        buffer.writeUtf(packet.optionId(), MAX_IDENTIFIER_LENGTH);
        buffer.writeUtf(packet.action().name(), 32);
        buffer.writeVarInt(packet.quantity());
        buffer.writeVarLong(packet.expectedOfferRevision());
        buffer.writeBoolean(packet.paymentSource().isPresent());
        packet.paymentSource().ifPresent(source ->
                buffer.writeUtf(source.wire(), 32));
        buffer.writeUUID(packet.requestId());
        buffer.writeVarInt(packet.responseToken());
    }

    public static C2SPlayerShopOfferPacket decode(
            FriendlyByteBuf buffer
    ) {
        try {
            BlockPos shopPos = buffer.readBlockPos();
            int listingIndex = buffer.readVarInt();
            String listingId = buffer.readUtf(MAX_IDENTIFIER_LENGTH);
            String optionId = buffer.readUtf(MAX_IDENTIFIER_LENGTH);
            OfferAction action = OfferAction.valueOf(
                    buffer.readUtf(32).toUpperCase(Locale.ROOT));
            int quantity = buffer.readVarInt();
            long revision = buffer.readVarLong();
            Optional<PaymentSource> source = buffer.readBoolean()
                    ? Optional.of(PaymentSource.fromWire(
                    buffer.readUtf(32)).orElseThrow())
                    : Optional.empty();
            UUID requestId = buffer.readUUID();
            int responseToken = buffer.readVarInt();
            return new C2SPlayerShopOfferPacket(
                    shopPos, listingIndex, listingId, optionId, action,
                    quantity, revision, source, requestId, responseToken);
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Player shop offer packet is malformed", exception);
        }
    }

    public static void handle(
            C2SPlayerShopOfferPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                PlayerShopBlockService.handleOffer(player, packet);
            }
        });
        context.setPacketHandled(true);
    }

    private static String identifier(String value, String field) {
        String candidate = Objects.requireNonNull(value, field).strip();
        if (candidate.isEmpty()
                || candidate.length() > MAX_IDENTIFIER_LENGTH
                || !candidate.matches("[a-z0-9_.:/-]+")) {
            throw new IllegalArgumentException(
                    "Player shop offer identifier is invalid");
        }
        return candidate;
    }
}
