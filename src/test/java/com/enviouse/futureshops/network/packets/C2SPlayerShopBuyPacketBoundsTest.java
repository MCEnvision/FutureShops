package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.transaction.ShopTransactionUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class C2SPlayerShopBuyPacketBoundsTest {
    private static final BlockPos SHOP_POS = new BlockPos(1, 64, 2);
    private static final UUID REQUEST_ID = UUID.fromString("12345678-1234-5678-1234-567812345678");

    @Test
    void constructorAcceptsOnlyTheInclusiveQuantityRange() {
        assertEquals(1, packet(1).quantity());
        assertEquals(ShopTransactionUtil.MAX_BUY_QUANTITY,
                packet(ShopTransactionUtil.MAX_BUY_QUANTITY).quantity());
        assertThrows(IllegalArgumentException.class, () -> packet(0));
        assertThrows(IllegalArgumentException.class, () -> packet(-1));
        assertThrows(IllegalArgumentException.class,
                () -> packet(ShopTransactionUtil.MAX_BUY_QUANTITY + 1));
        assertThrows(IllegalArgumentException.class, () -> packet(Integer.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> packet(Integer.MAX_VALUE));
    }

    @Test
    void decoderRejectsEveryMalformedQuantityExtreme() {
        assertThrows(DecoderException.class, () -> C2SPlayerShopBuyPacket.decode(wire(0, 0, "MONEY", "WALLET")));
        assertThrows(DecoderException.class, () -> C2SPlayerShopBuyPacket.decode(wire(-1, 0, "MONEY", "WALLET")));
        assertThrows(DecoderException.class, () -> C2SPlayerShopBuyPacket.decode(wire(
                ShopTransactionUtil.MAX_BUY_QUANTITY + 1, 0, "MONEY", "WALLET")));
        assertThrows(DecoderException.class, () -> C2SPlayerShopBuyPacket.decode(wire(
                Integer.MIN_VALUE, 0, "MONEY", "WALLET")));
        assertThrows(DecoderException.class, () -> C2SPlayerShopBuyPacket.decode(wire(
                Integer.MAX_VALUE, 0, "MONEY", "WALLET")));
    }

    @Test
    void maximumQuantityRoundTrips() {
        C2SPlayerShopBuyPacket packet = C2SPlayerShopBuyPacket.decode(wire(
                ShopTransactionUtil.MAX_BUY_QUANTITY, C2SPlayerShopBuyPacket.MAX_RESPONSE_TOKEN,
                "MONEY_AND_BARTER", "INVENTORY"));
        assertEquals(ShopTransactionUtil.MAX_BUY_QUANTITY, packet.quantity());
        assertEquals(C2SPlayerShopBuyPacket.MAX_RESPONSE_TOKEN, packet.responseToken());
        assertEquals("MONEY_AND_BARTER", packet.paymentMethod());
        assertEquals("INVENTORY", packet.paymentSource());
    }

    @Test
    void stringsAndCorrelationTokenAreBoundedWithoutLayoutChanges() {
        assertThrows(IllegalArgumentException.class, () -> new C2SPlayerShopBuyPacket(
                SHOP_POS, 0, 1, "x".repeat(C2SPlayerShopBuyPacket.MAX_PAYMENT_METHOD_LENGTH + 1),
                "WALLET", REQUEST_ID, 0));
        assertThrows(IllegalArgumentException.class, () -> new C2SPlayerShopBuyPacket(
                SHOP_POS, 0, 1, "MONEY",
                "x".repeat(C2SPlayerShopBuyPacket.MAX_PAYMENT_SOURCE_LENGTH + 1), REQUEST_ID, 0));
        assertThrows(IllegalArgumentException.class, () -> new C2SPlayerShopBuyPacket(
                SHOP_POS, 0, 1, "MONEY", "WALLET", REQUEST_ID, -1));
        assertEquals(C2SPlayerShopBuyPacket.MAX_RESPONSE_TOKEN,
                new C2SPlayerShopBuyPacket(
                        SHOP_POS, 0, 1, "MONEY", "WALLET", REQUEST_ID,
                        C2SPlayerShopBuyPacket.MAX_RESPONSE_TOKEN).responseToken());
        assertThrows(IllegalArgumentException.class, () -> new C2SPlayerShopBuyPacket(
                SHOP_POS, 0, 1, "MONEY", "WALLET", REQUEST_ID,
                C2SPlayerShopBuyPacket.MAX_RESPONSE_TOKEN + 1));
        assertThrows(IllegalArgumentException.class, () -> new C2SPlayerShopBuyPacket(
                SHOP_POS, 0, 1, "MONEY", "WALLET", REQUEST_ID, Integer.MAX_VALUE));
        assertThrows(DecoderException.class, () -> C2SPlayerShopBuyPacket.decode(wire(
                1, 0, "x".repeat(C2SPlayerShopBuyPacket.MAX_PAYMENT_METHOD_LENGTH + 1), "WALLET")));
        assertThrows(DecoderException.class, () -> C2SPlayerShopBuyPacket.decode(wire(
                1, -1, "MONEY", "WALLET")));
        assertThrows(DecoderException.class, () -> C2SPlayerShopBuyPacket.decode(wire(
                1, C2SPlayerShopBuyPacket.MAX_RESPONSE_TOKEN + 1, "MONEY", "WALLET")));
        assertThrows(DecoderException.class, () -> C2SPlayerShopBuyPacket.decode(wire(
                1, Integer.MAX_VALUE, "MONEY", "WALLET")));
    }

    @Test
    void negativeListingIndexIsMalformed() {
        assertThrows(IllegalArgumentException.class, () -> new C2SPlayerShopBuyPacket(
                SHOP_POS, -1, 1, "MONEY", "WALLET", REQUEST_ID, 0));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeBlockPos(SHOP_POS);
        buffer.writeVarInt(-1);
        buffer.writeVarInt(1);
        buffer.writeUtf("MONEY");
        buffer.writeUtf("WALLET");
        buffer.writeUUID(REQUEST_ID);
        buffer.writeVarInt(0);
        assertThrows(DecoderException.class, () -> C2SPlayerShopBuyPacket.decode(buffer));
    }

    @Test
    void listingIndexHasAnInclusiveWireBound() {
        assertEquals(C2SPlayerShopBuyPacket.MAX_LISTING_INDEX,
                new C2SPlayerShopBuyPacket(
                        SHOP_POS, C2SPlayerShopBuyPacket.MAX_LISTING_INDEX, 1,
                        "MONEY", "WALLET", REQUEST_ID, 0).listingIndex());
        assertThrows(IllegalArgumentException.class, () -> new C2SPlayerShopBuyPacket(
                SHOP_POS, C2SPlayerShopBuyPacket.MAX_LISTING_INDEX + 1, 1,
                "MONEY", "WALLET", REQUEST_ID, 0));
        assertThrows(IllegalArgumentException.class, () -> new C2SPlayerShopBuyPacket(
                SHOP_POS, Integer.MAX_VALUE, 1, "MONEY", "WALLET", REQUEST_ID, 0));
        assertThrows(DecoderException.class, () -> C2SPlayerShopBuyPacket.decode(wireWithListingIndex(
                C2SPlayerShopBuyPacket.MAX_LISTING_INDEX + 1)));
        assertThrows(DecoderException.class, () -> C2SPlayerShopBuyPacket.decode(wireWithListingIndex(
                Integer.MAX_VALUE)));
    }

    private static C2SPlayerShopBuyPacket packet(int quantity) {
        return new C2SPlayerShopBuyPacket(
                SHOP_POS, 0, quantity, "MONEY", "WALLET", REQUEST_ID, 0);
    }

    private static FriendlyByteBuf wire(
            int quantity,
            int responseToken,
            String paymentMethod,
            String paymentSource
    ) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeBlockPos(SHOP_POS);
        buffer.writeVarInt(0);
        buffer.writeVarInt(quantity);
        buffer.writeUtf(paymentMethod);
        buffer.writeUtf(paymentSource);
        buffer.writeUUID(REQUEST_ID);
        buffer.writeVarInt(responseToken);
        return buffer;
    }

    private static FriendlyByteBuf wireWithListingIndex(int listingIndex) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeBlockPos(SHOP_POS);
        buffer.writeVarInt(listingIndex);
        buffer.writeVarInt(1);
        buffer.writeUtf("MONEY");
        buffer.writeUtf("WALLET");
        buffer.writeUUID(REQUEST_ID);
        buffer.writeVarInt(0);
        return buffer;
    }
}
