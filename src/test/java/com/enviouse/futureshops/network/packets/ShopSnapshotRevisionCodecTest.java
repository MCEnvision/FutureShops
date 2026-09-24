package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.shop.ShopResultCode;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShopSnapshotRevisionCodecTest {
    @Test
    void buyRequestAndResponsePreserveRevisionAndReason() {
        UUID requestId = UUID.randomUUID();
        C2SBuyRequestPacket request = new C2SBuyRequestPacket(
                "default", false,
                List.of(new C2SBuyRequestPacket.LineItem("diamond", 2)),
                "WALLET", requestId, 19L);
        FriendlyByteBuf requestBuffer = new FriendlyByteBuf(
                Unpooled.buffer());
        C2SBuyRequestPacket.encode(request, requestBuffer);
        assertEquals(request, C2SBuyRequestPacket.decode(requestBuffer));

        S2CBuyResponsePacket response = new S2CBuyResponsePacket(
                false, false, "default", ShopResultCode.STALE_REQUEST,
                100L, 0, 0L, requestId, 20L, "stale_snapshot");
        FriendlyByteBuf responseBuffer = new FriendlyByteBuf(
                Unpooled.buffer());
        S2CBuyResponsePacket.encode(response, responseBuffer);
        assertEquals(response, S2CBuyResponsePacket.decode(responseBuffer));
    }

    @Test
    void sellRequestAndResponsePreserveRevisionAndReason() {
        UUID requestId = UUID.randomUUID();
        C2SSellRequestPacket request = new C2SSellRequestPacket(
                "default", "diamond", 3, requestId, 7L);
        FriendlyByteBuf requestBuffer = new FriendlyByteBuf(
                Unpooled.buffer());
        C2SSellRequestPacket.encode(request, requestBuffer);
        assertEquals(request, C2SSellRequestPacket.decode(requestBuffer));

        S2CSellResponsePacket response = new S2CSellResponsePacket(
                false, "default", "diamond", ShopResultCode.STALE_REQUEST,
                100L, 3, 0L, requestId, 8L, "stale_snapshot");
        FriendlyByteBuf responseBuffer = new FriendlyByteBuf(
                Unpooled.buffer());
        S2CSellResponsePacket.encode(response, responseBuffer);
        assertEquals(response, S2CSellResponsePacket.decode(responseBuffer));
    }

    @Test
    void responseReasonIsBoundedAndMachineReadable() {
        UUID requestId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new S2CBuyResponsePacket(
                false, false, "default", ShopResultCode.SERVER_ERROR,
                0L, 0, 0L, requestId, 1L, "not machine readable"));
        assertThrows(IllegalArgumentException.class, () -> new S2CSellResponsePacket(
                false, "default", "diamond", ShopResultCode.SERVER_ERROR,
                0L, 0, 0L, requestId, 1L, "x".repeat(33)));
    }
}
