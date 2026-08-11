package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.shop.ShopResultCode;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SellBarterCorrelationPacketTest {
    @Test
    void sellRequestAndResponsePreserveCorrelationIdentity() {
        UUID requestId = UUID.fromString(
                "10000000-0000-0000-0000-000000000001");
        C2SSellRequestPacket request = new C2SSellRequestPacket(
                "default", "diamond.offer", 3, requestId);
        FriendlyByteBuf requestBuffer = buffer();
        C2SSellRequestPacket.encode(request, requestBuffer);
        assertEquals(request,
                C2SSellRequestPacket.decode(requestBuffer));

        S2CSellResponsePacket response = new S2CSellResponsePacket(
                true, "default", "minecraft:diamond",
                ShopResultCode.OK, 300L, 3, 300L, requestId);
        FriendlyByteBuf responseBuffer = buffer();
        S2CSellResponsePacket.encode(response, responseBuffer);
        assertEquals(response,
                S2CSellResponsePacket.decode(responseBuffer));
    }

    @Test
    void barterRequestAndResponsePreserveCorrelationIdentity() {
        UUID requestId = UUID.fromString(
                "20000000-0000-0000-0000-000000000002");
        C2SBarterRequestPacket request = new C2SBarterRequestPacket(
                "default", "rare.exchange", 2, requestId);
        FriendlyByteBuf requestBuffer = buffer();
        C2SBarterRequestPacket.encode(request, requestBuffer);
        assertEquals(request,
                C2SBarterRequestPacket.decode(requestBuffer));

        S2CBarterResponsePacket response = new S2CBarterResponsePacket(
                true, "default", "rare.exchange", ShopResultCode.OK,
                2, 4, requestId);
        FriendlyByteBuf responseBuffer = buffer();
        S2CBarterResponsePacket.encode(response, responseBuffer);
        assertEquals(response,
                S2CBarterResponsePacket.decode(responseBuffer));
    }

    @Test
    void zeroCorrelationIdentitiesAreRejected() {
        UUID zero = new UUID(0L, 0L);
        assertThrows(IllegalArgumentException.class,
                () -> new C2SSellRequestPacket(
                        "default", "diamond", 1, zero));
        assertThrows(IllegalArgumentException.class,
                () -> new C2SBarterRequestPacket(
                        "default", "trade", 1, zero));
        assertThrows(IllegalArgumentException.class,
                () -> new S2CSellResponsePacket(true, "default",
                        "minecraft:diamond", ShopResultCode.OK,
                        1L, 1, 1L, zero));
        assertThrows(IllegalArgumentException.class,
                () -> new S2CBarterResponsePacket(true, "default",
                        "trade", ShopResultCode.OK, 1, 1, zero));
    }

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }
}
