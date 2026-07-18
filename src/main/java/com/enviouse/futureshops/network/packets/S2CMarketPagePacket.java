package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.server.market.query.MarketPageCard;
import com.enviouse.futureshops.server.market.query.MarketPageCardKind;
import com.enviouse.futureshops.server.market.query.MarketCardInsights;
import com.enviouse.futureshops.server.market.query.MarketPageSnapshot;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public record S2CMarketPagePacket(
        String resultCode,
        MarketPageSnapshot page
) {
    public S2CMarketPagePacket {
        resultCode = text(resultCode, 64, false);
        page = Objects.requireNonNull(page, "page");
    }

    public static void encode(
            S2CMarketPagePacket packet,
            FriendlyByteBuf buffer
    ) {
        MarketPageSnapshot page = packet.page();
        buffer.writeUtf(packet.resultCode(), 64);
        buffer.writeUUID(page.requestId());
        buffer.writeUUID(page.routeNonce());
        buffer.writeUtf(page.module().id(), 32);
        buffer.writeUtf(page.view(), 32);
        buffer.writeVarInt(page.pageIndex());
        buffer.writeVarInt(page.pageSize());
        buffer.writeVarInt(page.totalResults());
        buffer.writeVarInt(page.pageCount());
        buffer.writeLong(page.publicRevision());
        buffer.writeLong(page.profileRevision());
        buffer.writeLong(page.profileReplayEpoch());
        buffer.writeLong(page.serverTimeMillis());
        buffer.writeVarInt(page.unreadNotifications());
        buffer.writeLong(page.aggregatePrimaryMinor());
        buffer.writeLong(page.aggregateQuantity());
        buffer.writeVarInt(page.categories().size());
        for (String category : page.categories()) {
            buffer.writeUtf(category, 128);
        }
        buffer.writeVarInt(page.cards().size());
        for (MarketPageCard card : page.cards()) {
            writeCard(buffer, card);
        }
    }

    public static S2CMarketPagePacket decode(FriendlyByteBuf buffer) {
        try {
            String resultCode = buffer.readUtf(64);
            UUID requestId = buffer.readUUID();
            UUID routeNonce = buffer.readUUID();
            MarketModule module = MarketModule.fromId(
                    buffer.readUtf(32));
            String view = buffer.readUtf(32);
            int pageIndex = buffer.readVarInt();
            int pageSize = buffer.readVarInt();
            int totalResults = buffer.readVarInt();
            int pageCount = buffer.readVarInt();
            long publicRevision = buffer.readLong();
            long profileRevision = buffer.readLong();
            long profileReplayEpoch = buffer.readLong();
            long serverTime = buffer.readLong();
            int unread = buffer.readVarInt();
            long aggregatePrimary = buffer.readLong();
            long aggregateQuantity = buffer.readLong();
            int categoryCount = boundedCount(buffer.readVarInt(), 256);
            List<String> categories = new ArrayList<>(categoryCount);
            for (int index = 0; index < categoryCount; index++) {
                categories.add(buffer.readUtf(128));
            }
            int cardCount = boundedCount(buffer.readVarInt(), pageSize);
            List<MarketPageCard> cards = new ArrayList<>(cardCount);
            for (int index = 0; index < cardCount; index++) {
                cards.add(readCard(buffer));
            }
            return new S2CMarketPagePacket(resultCode,
                    new MarketPageSnapshot(requestId, routeNonce, module,
                            view, pageIndex, pageSize, totalResults,
                            pageCount, publicRevision, profileRevision,
                            profileReplayEpoch, serverTime, unread,
                            aggregatePrimary, aggregateQuantity,
                            categories, cards));
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Market page response is invalid", exception);
        }
    }

    public static void handle(
            S2CMarketPagePacket packet,
            Supplier<NetworkEvent.Context> supplier
    ) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT, () -> () ->
                        ShopClientPacketHandler.handleMarketPage(packet)));
        context.setPacketHandled(true);
    }

    private static void writeCard(
            FriendlyByteBuf buffer,
            MarketPageCard card
    ) {
        buffer.writeEnum(card.kind());
        buffer.writeUtf(card.identity(), 192);
        buffer.writeBoolean(card.ownerId().isPresent());
        card.ownerId().ifPresent(buffer::writeUUID);
        buffer.writeUtf(card.registryId(), 256);
        buffer.writeVarInt(card.itemCount());
        buffer.writeUtf(card.title(), 256);
        buffer.writeUtf(card.category(), 128);
        buffer.writeUtf(card.state(), 64);
        buffer.writeLong(card.revision());
        buffer.writeLong(card.primaryMinor());
        buffer.writeLong(card.secondaryMinor());
        buffer.writeLong(card.quantity());
        buffer.writeLong(card.remainingMillis());
        buffer.writeBoolean(card.watched());
        buffer.writeBoolean(card.primaryAction());
        buffer.writeBoolean(card.secondaryAction());
        buffer.writeLong(card.tertiaryMinor());
        MarketCardInsights insights = card.insights();
        buffer.writeUtf(insights.ownerName(), 64);
        buffer.writeBoolean(insights.participantId().isPresent());
        insights.participantId().ifPresent(buffer::writeUUID);
        buffer.writeUtf(insights.participantName(), 64);
        buffer.writeUtf(insights.participantRole(), 32);
        buffer.writeUtf(insights.itemStackSnbt(),
                MarketCardInsights.MAXIMUM_STACK_SNBT);
        buffer.writeBoolean(insights.itemHasData());
        buffer.writeLong(insights.activeListings());
        buffer.writeLong(insights.activeBuyOrders());
        buffer.writeLong(insights.tradesLastHour());
        buffer.writeLong(insights.tradesLastDay());
        buffer.writeLong(insights.unitsLastDay());
        buffer.writeLong(insights.averagePriceMinor());
        buffer.writeVarInt(insights.priceHistoryMinor().size());
        for (long point : insights.priceHistoryMinor()) {
            buffer.writeLong(point);
        }
    }

    private static MarketPageCard readCard(FriendlyByteBuf buffer) {
        MarketPageCardKind kind = buffer.readEnum(
                MarketPageCardKind.class);
        String identity = buffer.readUtf(192);
        Optional<UUID> owner = buffer.readBoolean()
                ? Optional.of(buffer.readUUID()) : Optional.empty();
        String registryId = buffer.readUtf(256);
        int itemCount = buffer.readVarInt();
        String title = buffer.readUtf(256);
        String category = buffer.readUtf(128);
        String state = buffer.readUtf(64);
        long revision = buffer.readLong();
        long primary = buffer.readLong();
        long secondary = buffer.readLong();
        long quantity = buffer.readLong();
        long remaining = buffer.readLong();
        boolean watched = buffer.readBoolean();
        boolean primaryAction = buffer.readBoolean();
        boolean secondaryAction = buffer.readBoolean();
        long tertiary = buffer.readLong();
        String ownerName = buffer.readUtf(64);
        Optional<UUID> participant = buffer.readBoolean()
                ? Optional.of(buffer.readUUID()) : Optional.empty();
        String participantName = buffer.readUtf(64);
        String participantRole = buffer.readUtf(32);
        String itemStackSnbt = buffer.readUtf(
                MarketCardInsights.MAXIMUM_STACK_SNBT);
        boolean itemHasData = buffer.readBoolean();
        long activeListings = buffer.readLong();
        long activeBuyOrders = buffer.readLong();
        long tradesLastHour = buffer.readLong();
        long tradesLastDay = buffer.readLong();
        long unitsLastDay = buffer.readLong();
        long averagePrice = buffer.readLong();
        int pointCount = boundedCount(buffer.readVarInt(),
                MarketCardInsights.MAXIMUM_PRICE_POINTS);
        List<Long> history = new ArrayList<>(pointCount);
        for (int index = 0; index < pointCount; index++) {
            history.add(buffer.readLong());
        }
        return new MarketPageCard(kind, identity, owner,
                registryId, itemCount, title, category, state, revision,
                primary, secondary, quantity, remaining, watched,
                primaryAction, secondaryAction, tertiary,
                new MarketCardInsights(ownerName, participant,
                        participantName, participantRole, itemStackSnbt,
                        itemHasData, activeListings, activeBuyOrders,
                        tradesLastHour, tradesLastDay, unitsLastDay,
                        averagePrice, history));
    }

    private static int boundedCount(int value, int maximum) {
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException(
                    "Market page collection size is invalid");
        }
        return value;
    }

    private static String text(
            String value,
            int maximum,
            boolean allowEmpty
    ) {
        String result = Objects.requireNonNull(value, "value").strip();
        if (!result.equals(value) || !allowEmpty && result.isEmpty()
                || result.length() > maximum) {
            throw new IllegalArgumentException(
                    "Market page packet text is invalid");
        }
        return result;
    }
}
