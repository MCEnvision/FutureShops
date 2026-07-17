package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.server.market.MarketProfileMutationService;
import com.enviouse.futureshops.server.market.profile.MarketProfileMutation;
import com.enviouse.futureshops.server.market.profile.MarketProfileMutationCommand;
import com.enviouse.futureshops.server.market.profile.MarketProfileMutationType;
import com.enviouse.futureshops.server.market.profile.MarketProfileSavedData;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public record C2SMarketProfileMutationPacket(
        MarketProfileMutationCommand command
) {
    public C2SMarketProfileMutationPacket {
        command = Objects.requireNonNull(command, "command");
    }

    public static void encode(
            C2SMarketProfileMutationPacket packet,
            FriendlyByteBuf buffer
    ) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(buffer, "buffer");
        MarketProfileMutationCommand command = packet.command();
        buffer.writeUUID(command.requestId());
        buffer.writeUUID(command.routeNonce());
        buffer.writeUtf(command.module().id(), 32);
        buffer.writeUtf(command.view(), 32);
        buffer.writeLong(command.expectedProfileRevision());
        buffer.writeEnum(command.mutation().type());
        writeMutation(buffer, command.mutation());
    }

    public static C2SMarketProfileMutationPacket decode(
            FriendlyByteBuf buffer
    ) {
        try {
            UUID requestId = buffer.readUUID();
            UUID routeNonce = buffer.readUUID();
            MarketModule module = MarketModule.fromId(
                    buffer.readUtf(32));
            String view = buffer.readUtf(32);
            long expectedRevision = buffer.readLong();
            MarketProfileMutationType type = buffer.readEnum(
                    MarketProfileMutationType.class);
            MarketProfileMutation mutation = readMutation(buffer, type);
            C2SMarketProfileMutationPacket result =
                    new C2SMarketProfileMutationPacket(
                            new MarketProfileMutationCommand(requestId,
                                    routeNonce, module, view,
                                    expectedRevision, mutation));
            requireFullyRead(buffer);
            return result;
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Market profile mutation request is invalid",
                    exception);
        }
    }

    public static void handle(
            C2SMarketProfileMutationPacket packet,
            Supplier<NetworkEvent.Context> supplier
    ) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                MarketProfileMutationService.mutate(player, packet);
            }
        });
        context.setPacketHandled(true);
    }

    private static void writeMutation(
            FriendlyByteBuf buffer,
            MarketProfileMutation mutation
    ) {
        if (mutation instanceof MarketProfileMutation.AuctionWatch value) {
            buffer.writeUUID(value.listingId());
            buffer.writeBoolean(value.watched());
            return;
        }
        if (mutation instanceof
                MarketProfileMutation.BazaarFavorite value) {
            writeProduct(buffer, value.product());
            buffer.writeBoolean(value.favorite());
            return;
        }
        if (mutation instanceof
                MarketProfileMutation.PriceAlertAdd value) {
            buffer.writeUUID(value.alertId());
            writeProduct(buffer, value.product());
            buffer.writeEnum(value.direction());
            buffer.writeLong(value.thresholdMinor());
            return;
        }
        if (mutation instanceof
                MarketProfileMutation.PriceAlertRemove value) {
            buffer.writeUUID(value.alertId());
            return;
        }
        if (mutation instanceof
                MarketProfileMutation.NotificationsRead value) {
            buffer.writeVarInt(value.notificationIds().size());
            for (UUID notificationId : value.notificationIds()) {
                buffer.writeUUID(notificationId);
            }
            return;
        }
        throw new IllegalArgumentException(
                "Market profile mutation request type is invalid");
    }

    private static MarketProfileMutation readMutation(
            FriendlyByteBuf buffer,
            MarketProfileMutationType type
    ) {
        return switch (type) {
            case AUCTION_WATCH -> new MarketProfileMutation.AuctionWatch(
                    buffer.readUUID(), buffer.readBoolean());
            case BAZAAR_FAVORITE ->
                    new MarketProfileMutation.BazaarFavorite(
                            readProduct(buffer), buffer.readBoolean());
            case PRICE_ALERT_ADD ->
                    new MarketProfileMutation.PriceAlertAdd(
                            buffer.readUUID(), readProduct(buffer),
                            buffer.readEnum(MarketProfileSavedData
                                    .AlertDirection.class),
                            buffer.readLong());
            case PRICE_ALERT_REMOVE ->
                    new MarketProfileMutation.PriceAlertRemove(
                            buffer.readUUID());
            case NOTIFICATIONS_READ ->
                    new MarketProfileMutation.NotificationsRead(
                            readNotificationIds(buffer));
        };
    }

    private static void writeProduct(
            FriendlyByteBuf buffer,
            MarketProfileSavedData.ProductKey product
    ) {
        buffer.writeUtf(product.productId(), 160);
        buffer.writeLong(product.version());
    }

    private static MarketProfileSavedData.ProductKey readProduct(
            FriendlyByteBuf buffer
    ) {
        return new MarketProfileSavedData.ProductKey(
                buffer.readUtf(160), buffer.readLong());
    }

    private static List<UUID> readNotificationIds(
            FriendlyByteBuf buffer
    ) {
        int count = buffer.readVarInt();
        if (count <= 0
                || count > MarketProfileSavedData.MAX_NOTIFICATIONS) {
            throw new IllegalArgumentException(
                    "Market profile notification count is invalid");
        }
        List<UUID> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(buffer.readUUID());
        }
        return List.copyOf(values);
    }

    private static void requireFullyRead(FriendlyByteBuf buffer) {
        if (buffer.isReadable()) {
            throw new IllegalArgumentException(
                    "Market profile mutation request has trailing data");
        }
    }
}
