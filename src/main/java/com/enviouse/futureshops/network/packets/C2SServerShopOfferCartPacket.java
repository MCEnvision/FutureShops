package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopOfferCartService;
import com.enviouse.futureshops.server.shop.InventorySyncService;
import com.enviouse.futureshops.server.shop.ShopDataService;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public record C2SServerShopOfferCartPacket(
        String shopId,
        List<Line> lines,
        UUID requestId,
        Optional<PaymentSource> paymentSource
) {
    private static final int MAX_IDENTIFIER = 160;
    private static final int MAXIMUM_LINES = 256;
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public C2SServerShopOfferCartPacket {
        shopId = identifier(shopId);
        lines = List.copyOf(lines);
        requestId = java.util.Objects.requireNonNull(
                requestId, "requestId");
        paymentSource = java.util.Objects.requireNonNull(
                paymentSource, "paymentSource");
        if (lines.isEmpty() || lines.size() > MAXIMUM_LINES
                || requestId.equals(ZERO_UUID)) {
            throw new IllegalArgumentException(
                    "Server shop offer cart packet is invalid");
        }
        new ServerShopOfferCartService.Request(
                requestId, UUID.randomUUID(), shopId,
                lines.stream().map(Line::request).toList(),
                paymentSource, 0);
    }

    public static void encode(
            C2SServerShopOfferCartPacket packet,
            FriendlyByteBuf buffer
    ) {
        buffer.writeUtf(packet.shopId, MAX_IDENTIFIER);
        buffer.writeVarInt(packet.lines.size());
        for (Line line : packet.lines) {
            buffer.writeUtf(line.listingId, MAX_IDENTIFIER);
            buffer.writeUtf(line.optionId, MAX_IDENTIFIER);
            buffer.writeVarInt(line.quantity);
            buffer.writeVarLong(line.expectedOfferRevision);
        }
        buffer.writeUUID(packet.requestId);
        buffer.writeBoolean(packet.paymentSource.isPresent());
        packet.paymentSource.ifPresent(source ->
                buffer.writeUtf(source.wire(), 32));
    }

    public static C2SServerShopOfferCartPacket decode(
            FriendlyByteBuf buffer
    ) {
        try {
            String shopId = buffer.readUtf(MAX_IDENTIFIER);
            int count = buffer.readVarInt();
            if (count <= 0 || count > MAXIMUM_LINES) {
                throw new DecoderException(
                        "Server shop offer cart line count is invalid");
            }
            List<Line> lines = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                lines.add(new Line(
                        buffer.readUtf(MAX_IDENTIFIER),
                        buffer.readUtf(MAX_IDENTIFIER),
                        buffer.readVarInt(),
                        buffer.readVarLong()));
            }
            UUID requestId = buffer.readUUID();
            Optional<PaymentSource> source =
                    buffer.readBoolean()
                            ? Optional.of(PaymentSource.fromWire(
                            buffer.readUtf(32)).orElseThrow())
                            : Optional.empty();
            return new C2SServerShopOfferCartPacket(
                    shopId, lines, requestId, source);
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Server shop offer cart packet is malformed",
                    exception);
        }
    }

    public static void handle(
            C2SServerShopOfferCartPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            ServerShopOfferCartService.Result result =
                    ServerShopOfferCartService.execute(
                            player,
                            new ServerShopOfferCartService.Request(
                                    packet.requestId,
                                    player.getUUID(),
                                    packet.shopId,
                                    packet.lines.stream()
                                            .map(Line::request)
                                            .toList(),
                                    packet.paymentSource, 0));
            ShopPackets.sendToPlayer(player,
                    new S2CServerShopOfferCartResultPacket(
                            result.requestId(), packet.shopId,
                            result.status(),
                            BalanceManager.getDisplayBalance(
                                    player.getUUID()),
                            result.replayed()));
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

    private static String identifier(String value) {
        String normalized = java.util.Objects.requireNonNull(
                value, "identifier").strip();
        if (normalized.isEmpty()
                || normalized.length() > MAX_IDENTIFIER
                || !normalized.matches("[a-z0-9_.:/-]+")) {
            throw new IllegalArgumentException(
                    "Server shop offer cart identifier is invalid");
        }
        return normalized;
    }

    public record Line(
            String listingId,
            String optionId,
            int quantity,
            long expectedOfferRevision
    ) {
        public Line {
            listingId = identifier(listingId);
            optionId = identifier(optionId);
            if (quantity <= 0 || quantity > 2304
                    || expectedOfferRevision < 0L
                    || expectedOfferRevision
                    > com.enviouse.futureshops.server.escrow.runtime
                    .ServerShopOfferCommit.MAX_REVISION) {
                throw new IllegalArgumentException(
                        "Server shop offer cart line is invalid");
            }
        }

        private ServerShopOfferCartService.LineRequest request() {
            return new ServerShopOfferCartService.LineRequest(
                    listingId, optionId, quantity,
                    expectedOfferRevision);
        }
    }
}
