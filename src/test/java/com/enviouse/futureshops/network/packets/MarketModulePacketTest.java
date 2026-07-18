package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.market.MarketModule;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketModulePacketTest {
    @Test
    void marketModuleRootsAreValidOpenPacketViews() {
        for (MarketModule module : new MarketModule[]{
                MarketModule.BAZAAR, MarketModule.AUCTION_HOUSE}) {
            C2SOpenMarketModulePacket packet =
                    new C2SOpenMarketModulePacket(UUID.randomUUID(),
                            module.id(), module.rootView());
            FriendlyByteBuf buffer = new FriendlyByteBuf(
                    Unpooled.buffer());
            C2SOpenMarketModulePacket.encode(packet, buffer);

            assertEquals(packet,
                    C2SOpenMarketModulePacket.decode(buffer));
        }
    }

    @Test
    void marketOpenPacketsValidateModuleIdentityAndBranding() {
        UUID requestId = UUID.fromString(
                "10000000-0000-0000-0000-000000000001");
        C2SOpenMarketModulePacket request =
                new C2SOpenMarketModulePacket(requestId, "bazaar",
                        "products");
        UUID routeNonce = UUID.fromString(
                "10000000-0000-0000-0000-000000000002");
        S2COpenMarketModulePacket response =
                new S2COpenMarketModulePacket(requestId, routeNonce,
                        "bazaar",
                        "products", "Green Market", "#48B978", true,
                        true, true, true, true);

        assertEquals(requestId, request.requestId());
        assertEquals("Green Market", response.displayName());
    }

    @Test
    void malformedMarketOpenPacketsAreRejected() {
        UUID zero = new UUID(0L, 0L);
        UUID requestId = UUID.fromString(
                "10000000-0000-0000-0000-000000000001");
        assertThrows(IllegalArgumentException.class,
                () -> new C2SOpenMarketModulePacket(zero, "bazaar",
                        "products"));
        assertThrows(IllegalArgumentException.class,
                () -> new S2COpenMarketModulePacket(requestId,
                        UUID.randomUUID(), "shop",
                        "browse", "Shop", "#9184D9", true, true,
                        true, true, true));
        assertThrows(IllegalArgumentException.class,
                () -> new S2COpenMarketModulePacket(requestId,
                        UUID.randomUUID(), "bazaar",
                        "products", "Bazaar", "not a color", true,
                        true, true, true, true));
    }

    @Test
    void existingOpenPacketsCarryDetailViewsWithoutANewWireShape() {
        UUID requestId = UUID.randomUUID();
        C2SOpenMarketModulePacket request =
                new C2SOpenMarketModulePacket(requestId,
                        "auction_house", "listing_detail");
        FriendlyByteBuf requestBuffer = new FriendlyByteBuf(
                Unpooled.buffer());
        C2SOpenMarketModulePacket.encode(request, requestBuffer);
        assertEquals(request,
                C2SOpenMarketModulePacket.decode(requestBuffer));

        S2COpenMarketModulePacket response =
                new S2COpenMarketModulePacket(requestId,
                        UUID.randomUUID(), "bazaar", "product_detail",
                        "Bazaar", "#48B978", true, true,
                        true, true, true);
        FriendlyByteBuf responseBuffer = new FriendlyByteBuf(
                Unpooled.buffer());
        S2COpenMarketModulePacket.encode(response, responseBuffer);
        assertEquals(response,
                S2COpenMarketModulePacket.decode(responseBuffer));
    }

    @Test
    void closePacketPreservesRouteScope() {
        C2SCloseMarketSessionPacket routeOnly =
                new C2SCloseMarketSessionPacket(UUID.randomUUID(), false);
        FriendlyByteBuf routeBuffer = new FriendlyByteBuf(
                Unpooled.buffer());
        C2SCloseMarketSessionPacket.encode(routeOnly, routeBuffer);
        assertEquals(routeOnly,
                C2SCloseMarketSessionPacket.decode(routeBuffer));

        C2SCloseMarketSessionPacket allRoutes =
                new C2SCloseMarketSessionPacket(UUID.randomUUID(), true);
        FriendlyByteBuf allBuffer = new FriendlyByteBuf(
                Unpooled.buffer());
        C2SCloseMarketSessionPacket.encode(allRoutes, allBuffer);
        assertEquals(allRoutes,
                C2SCloseMarketSessionPacket.decode(allBuffer));
    }

    @Test
    void closePacketRejectsZeroRoute() {
        assertThrows(IllegalArgumentException.class, () ->
                new C2SCloseMarketSessionPacket(new UUID(0L, 0L),
                        false));
    }
}
