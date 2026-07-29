package com.enviouse.futureshops.network.packets;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BazaarRegisterProductPacketTest {
    @Test
    void roundTripPreservesRequestAndRoute() {
        C2SBazaarRegisterProductPacket packet =
                new C2SBazaarRegisterProductPacket(
                        UUID.randomUUID(), UUID.randomUUID(),
                        "minecraft:iron_ingot");
        FriendlyByteBuf buffer = new FriendlyByteBuf(
                Unpooled.buffer());

        C2SBazaarRegisterProductPacket.encode(packet, buffer);

        assertEquals(packet,
                C2SBazaarRegisterProductPacket.decode(buffer));
    }

    @Test
    void zeroIdentifiersAreRejected() {
        UUID zero = new UUID(0L, 0L);
        assertThrows(IllegalArgumentException.class, () ->
                new C2SBazaarRegisterProductPacket(
                        zero, UUID.randomUUID(),
                        "minecraft:iron_ingot"));
        FriendlyByteBuf buffer = new FriendlyByteBuf(
                Unpooled.buffer());
        buffer.writeUUID(UUID.randomUUID());
        buffer.writeUUID(zero);
        buffer.writeUtf("minecraft:iron_ingot");
        assertThrows(DecoderException.class, () ->
                C2SBazaarRegisterProductPacket.decode(buffer));
    }

    @Test
    void registryItemIdentifierIsRequiredAndValidated() {
        UUID requestId = UUID.randomUUID();
        UUID routeNonce = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () ->
                new C2SBazaarRegisterProductPacket(
                        requestId, routeNonce, ""));
        assertThrows(IllegalArgumentException.class, () ->
                new C2SBazaarRegisterProductPacket(
                        requestId, routeNonce, "not an item id"));
    }
}
