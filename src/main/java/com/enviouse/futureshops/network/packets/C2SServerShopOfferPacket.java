package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.catalog.offer.OfferAction;
import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopOfferService;
import com.enviouse.futureshops.server.shop.InventorySyncService;
import com.enviouse.futureshops.server.shop.ShopDataService;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public record C2SServerShopOfferPacket(
        String shopId,
        String listingId,
        String optionId,
        OfferAction action,
        int quantity,
        long expectedOfferRevision,
        UUID requestId,
        Optional<PaymentSource> paymentSource
) {
    private static final int MAX_IDENTIFIER = 160;
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public C2SServerShopOfferPacket {
        shopId = validateIdentifier(shopId, "shopId");
        listingId = validateIdentifier(listingId, "listingId");
        optionId = validateIdentifier(optionId, "optionId");
        action = java.util.Objects.requireNonNull(action, "action");
        requestId = java.util.Objects.requireNonNull(requestId, "requestId");
        paymentSource = java.util.Objects.requireNonNull(
                paymentSource, "paymentSource");
        if (quantity < 1 || quantity > 2304
                || expectedOfferRevision < 0L
                || expectedOfferRevision
                > com.enviouse.futureshops.server.escrow.runtime
                .ServerShopOfferCommit.MAX_REVISION
                || requestId.equals(ZERO_UUID)
                || action == OfferAction.SELL_TO_SHOP
                && paymentSource.isPresent()) {
            throw new IllegalArgumentException(
                    "Server shop offer packet is invalid");
        }
    }

    public static void encode(
            C2SServerShopOfferPacket packet,
            FriendlyByteBuf buffer
    ) {
        buffer.writeUtf(packet.shopId, MAX_IDENTIFIER);
        buffer.writeUtf(packet.listingId, MAX_IDENTIFIER);
        buffer.writeUtf(packet.optionId, MAX_IDENTIFIER);
        buffer.writeUtf(packet.action.name(), 32);
        buffer.writeVarInt(packet.quantity);
        buffer.writeVarLong(packet.expectedOfferRevision);
        buffer.writeUUID(packet.requestId);
        buffer.writeBoolean(packet.paymentSource.isPresent());
        packet.paymentSource.ifPresent(source ->
                buffer.writeUtf(source.wire(), 32));
    }

    public static C2SServerShopOfferPacket decode(FriendlyByteBuf buffer) {
        try {
            String shopId = buffer.readUtf(MAX_IDENTIFIER);
            String listingId = buffer.readUtf(MAX_IDENTIFIER);
            String optionId = buffer.readUtf(MAX_IDENTIFIER);
            OfferAction action = OfferAction.valueOf(
                    buffer.readUtf(32).toUpperCase(Locale.ROOT));
            int quantity = buffer.readVarInt();
            long revision = buffer.readVarLong();
            UUID requestId = buffer.readUUID();
            Optional<PaymentSource> source = buffer.readBoolean()
                    ? Optional.of(PaymentSource.fromWire(
                    buffer.readUtf(32)).orElseThrow())
                    : Optional.empty();
            return new C2SServerShopOfferPacket(shopId, listingId,
                    optionId, action, quantity, revision, requestId,
                    source);
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Server shop offer packet is malformed", exception);
        }
    }

    public static void handle(
            C2SServerShopOfferPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            ServerShopOfferService.Result result =
                    ServerShopOfferService.execute(player,
                            new ServerShopOfferService.Request(
                                    packet.requestId,
                                    player.getUUID(),
                                    packet.shopId,
                                    packet.listingId,
                                    packet.optionId,
                                    packet.action,
                                    packet.quantity,
                                    packet.expectedOfferRevision,
                                    packet.paymentSource,
                                    0));
            ShopPackets.sendToPlayer(player,
                    S2CServerShopOfferResultPacket.from(
                            packet, result,
                            BalanceManager.getDisplayBalance(
                                    player.getUUID())));
            if (result.status().success()
                    && player.getServer() != null
                    && !result.replayed()) {
                InventorySyncService.sendOwnedCounts(
                        player, packet.shopId);
                ShopDataService.resendSessionsViewingShop(
                        player.getServer(), packet.shopId);
            }
        });
        context.setPacketHandled(true);
    }

    private static String validateIdentifier(
            String value,
            String field
    ) {
        String candidate = java.util.Objects.requireNonNull(
                value, field).strip();
        if (candidate.isEmpty() || candidate.length() > MAX_IDENTIFIER
                || !candidate.matches("[a-z0-9_.:/-]+")) {
            throw new IllegalArgumentException(
                    "Server shop offer packet identifier is invalid");
        }
        return candidate;
    }
}
