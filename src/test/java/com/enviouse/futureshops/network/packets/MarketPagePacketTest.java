package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.server.market.query.MarketPageCard;
import com.enviouse.futureshops.server.market.query.MarketPageCardKind;
import com.enviouse.futureshops.server.market.query.MarketCardInsights;
import com.enviouse.futureshops.server.market.query.MarketPageSnapshot;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketPagePacketTest {
    @Test
    void queryRoundTripPreservesCorrelationAndFingerprint() {
        C2SMarketPageQueryPacket packet = new C2SMarketPageQueryPacket(
                UUID.randomUUID(), UUID.randomUUID(), "bazaar",
                "products", "iron", "metals", "name", 2, 28,
                OptionalLong.of(10L), OptionalLong.of(100L));
        FriendlyByteBuf buffer = buffer();
        C2SMarketPageQueryPacket.encode(packet, buffer);

        C2SMarketPageQueryPacket decoded =
                C2SMarketPageQueryPacket.decode(buffer);

        assertEquals(packet, decoded);
        assertEquals(packet.fingerprint(), decoded.fingerprint());
        assertNotEquals(packet.fingerprint(),
                new C2SMarketPageQueryPacket(packet.requestId(),
                        packet.routeNonce(), "bazaar", "products",
                        "gold", "metals", "name", 2, 28,
                        OptionalLong.of(10L), OptionalLong.of(100L))
                        .fingerprint());
    }

    @Test
    void pageRoundTripPreservesBoundedCards() {
        UUID request = UUID.randomUUID();
        UUID route = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID participant = UUID.randomUUID();
        MarketPageCard card = new MarketPageCard(
                MarketPageCardKind.BAZAAR_PRODUCT, "iron@1",
                Optional.of(owner), "minecraft:iron_ingot", 1,
                "iron", "metals", "ACTIVE", 1L, 100L, 90L,
                12L, 0L, true, true, true, 0L,
                new MarketCardInsights("MarketMaker",
                        Optional.of(participant), "TopBuyer", "BUYER",
                        "{id:\"minecraft:iron_ingot\",Count:1b}",
                        true, 4L, 7L, 2L, 18L, 64L, 95L,
                        List.of(90L, 94L, 100L)));
        S2CMarketPagePacket packet = new S2CMarketPagePacket("OK",
                new MarketPageSnapshot(request, route,
                        MarketModule.BAZAAR, "products", 0, 28,
                        1, 1, 4L, 1000L, 2, 100L, 12L,
                        List.of("metals"), List.of(17), List.of(card)));
        FriendlyByteBuf buffer = buffer();
        S2CMarketPagePacket.encode(packet, buffer);

        assertEquals(packet, S2CMarketPagePacket.decode(buffer));
    }

    @Test
    void malformedQueryBoundsFailBeforeNetworkHandling() {
        assertThrows(IllegalArgumentException.class, () ->
                new C2SMarketPageQueryPacket(UUID.randomUUID(),
                        UUID.randomUUID(), "auction_house", "browse",
                        "", "", "ending_soon", 0, 101,
                        OptionalLong.empty(), OptionalLong.empty()));
    }

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }
}
