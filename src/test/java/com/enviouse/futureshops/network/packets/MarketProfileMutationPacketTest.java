package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.server.market.profile.MarketProfileMutation;
import com.enviouse.futureshops.server.market.profile.MarketProfileMutationCommand;
import com.enviouse.futureshops.server.market.profile.MarketProfileMutationResult;
import com.enviouse.futureshops.server.market.profile.MarketProfileMutationResultCode;
import com.enviouse.futureshops.server.market.profile.MarketProfileMutationType;
import com.enviouse.futureshops.server.market.profile.MarketProfileSavedData;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketProfileMutationPacketTest {
    private static final MarketProfileSavedData.ProductKey PRODUCT =
            new MarketProfileSavedData.ProductKey(
                    "minecraft:diamond", 7L);

    @Test
    void everyRequestMutationRoundTripsExactly() {
        UUID route = UUID.randomUUID();
        List<MarketProfileMutationCommand> commands = List.of(
                command(route, MarketModule.AUCTION_HOUSE, "browse",
                        new MarketProfileMutation.AuctionWatch(
                                UUID.randomUUID(), true)),
                command(route, MarketModule.BAZAAR, "products",
                        new MarketProfileMutation.BazaarFavorite(
                                PRODUCT, true)),
                command(route, MarketModule.BAZAAR, "products",
                        new MarketProfileMutation.PriceAlertAdd(
                                UUID.randomUUID(), PRODUCT,
                                MarketProfileSavedData.AlertDirection
                                        .AT_OR_BELOW, 400L)),
                command(route, MarketModule.BAZAAR, "products",
                        new MarketProfileMutation.PriceAlertRemove(
                                UUID.randomUUID())),
                command(route, MarketModule.AUCTION_HOUSE, "claims",
                        new MarketProfileMutation.NotificationsRead(
                                List.of(UUID.randomUUID(),
                                        UUID.randomUUID()))));

        for (MarketProfileMutationCommand command : commands) {
            C2SMarketProfileMutationPacket packet =
                    new C2SMarketProfileMutationPacket(command);
            FriendlyByteBuf buffer = buffer();
            C2SMarketProfileMutationPacket.encode(packet, buffer);

            C2SMarketProfileMutationPacket restored =
                    C2SMarketProfileMutationPacket.decode(buffer);
            assertEquals(packet, restored);
            assertEquals(command.fingerprint(),
                    restored.command().fingerprint());
        }
    }

    @Test
    void responseRoundTripsExactly() {
        MarketProfileMutationResult result =
                new MarketProfileMutationResult(UUID.randomUUID(),
                        UUID.randomUUID(), MarketModule.BAZAAR,
                        MarketProfileMutationType.PRICE_ALERT_ADD,
                        MarketProfileMutationResultCode.SUCCESS, 9L,
                        2, 3, 4, 5, 1, 1, true, false);
        S2CMarketProfileMutationPacket packet =
                new S2CMarketProfileMutationPacket(result);
        FriendlyByteBuf buffer = buffer();
        S2CMarketProfileMutationPacket.encode(packet, buffer);

        assertEquals(packet,
                S2CMarketProfileMutationPacket.decode(buffer));
    }

    @Test
    void trailingDataIsRejectedForBothDirections() {
        C2SMarketProfileMutationPacket request =
                new C2SMarketProfileMutationPacket(command(
                        UUID.randomUUID(), MarketModule.BAZAAR,
                        "products",
                        new MarketProfileMutation.BazaarFavorite(
                                PRODUCT, false)));
        FriendlyByteBuf requestBuffer = buffer();
        C2SMarketProfileMutationPacket.encode(request, requestBuffer);
        requestBuffer.writeByte(1);
        assertThrows(DecoderException.class, () ->
                C2SMarketProfileMutationPacket.decode(requestBuffer));

        S2CMarketProfileMutationPacket response =
                new S2CMarketProfileMutationPacket(
                        new MarketProfileMutationResult(
                                UUID.randomUUID(), UUID.randomUUID(),
                                MarketModule.BAZAAR,
                                MarketProfileMutationType
                                        .BAZAAR_FAVORITE,
                                MarketProfileMutationResultCode.NO_CHANGE,
                                0L, 0, 0, 0, 0, 0, 0,
                                false, false));
        FriendlyByteBuf responseBuffer = buffer();
        S2CMarketProfileMutationPacket.encode(response,
                responseBuffer);
        responseBuffer.writeByte(1);
        assertThrows(DecoderException.class, () ->
                S2CMarketProfileMutationPacket.decode(responseBuffer));
    }

    @Test
    void identitiesVersionsThresholdsAndCollectionsAreBounded() {
        UUID zero = new UUID(0L, 0L);
        assertThrows(IllegalArgumentException.class, () ->
                new MarketProfileMutation.AuctionWatch(zero, true));
        assertThrows(IllegalArgumentException.class, () ->
                new MarketProfileSavedData.ProductKey("item", 0L));
        assertThrows(IllegalArgumentException.class, () ->
                new MarketProfileSavedData.ProductKey(
                        "x".repeat(161), 1L));
        assertThrows(IllegalArgumentException.class, () ->
                new MarketProfileMutation.PriceAlertAdd(
                        UUID.randomUUID(), PRODUCT,
                        MarketProfileSavedData.AlertDirection.AT_OR_ABOVE,
                        0L));
        assertThrows(IllegalArgumentException.class, () ->
                new MarketProfileMutation.NotificationsRead(List.of()));
        UUID duplicate = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () ->
                new MarketProfileMutation.NotificationsRead(
                        List.of(duplicate, duplicate)));
        assertThrows(IllegalArgumentException.class, () ->
                new MarketProfileMutationCommand(UUID.randomUUID(),
                        UUID.randomUUID(), MarketModule.BAZAAR,
                        "products", -1L,
                        new MarketProfileMutation.BazaarFavorite(
                                PRODUCT, true)));

        FriendlyByteBuf emptyNotifications = buffer();
        writeHeader(emptyNotifications,
                MarketProfileMutationType.NOTIFICATIONS_READ,
                MarketModule.BAZAAR, "claims");
        emptyNotifications.writeVarInt(0);
        assertThrows(DecoderException.class, () ->
                C2SMarketProfileMutationPacket.decode(
                        emptyNotifications));

        FriendlyByteBuf oversizedNotifications = buffer();
        writeHeader(oversizedNotifications,
                MarketProfileMutationType.NOTIFICATIONS_READ,
                MarketModule.BAZAAR, "claims");
        oversizedNotifications.writeVarInt(
                MarketProfileSavedData.MAX_NOTIFICATIONS + 1);
        assertThrows(DecoderException.class, () ->
                C2SMarketProfileMutationPacket.decode(
                        oversizedNotifications));
    }

    @Test
    void invalidResponseCountsAreRejectedDuringDecode() {
        FriendlyByteBuf buffer = buffer();
        buffer.writeUUID(UUID.randomUUID());
        buffer.writeUUID(UUID.randomUUID());
        buffer.writeUtf(MarketModule.BAZAAR.id(), 32);
        buffer.writeEnum(MarketProfileMutationType.BAZAAR_FAVORITE);
        buffer.writeEnum(MarketProfileMutationResultCode.NO_CHANGE);
        buffer.writeLong(0L);
        buffer.writeVarInt(-1);
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeBoolean(false);
        buffer.writeBoolean(false);

        assertThrows(DecoderException.class, () ->
                S2CMarketProfileMutationPacket.decode(buffer));
    }

    private static MarketProfileMutationCommand command(
            UUID route,
            MarketModule module,
            String view,
            MarketProfileMutation mutation
    ) {
        return new MarketProfileMutationCommand(UUID.randomUUID(),
                route, module, view, 4L, mutation);
    }

    private static void writeHeader(
            FriendlyByteBuf buffer,
            MarketProfileMutationType type,
            MarketModule module,
            String view
    ) {
        buffer.writeUUID(UUID.randomUUID());
        buffer.writeUUID(UUID.randomUUID());
        buffer.writeUtf(module.id(), 32);
        buffer.writeUtf(view, 32);
        buffer.writeLong(0L);
        buffer.writeEnum(type);
    }

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }
}
