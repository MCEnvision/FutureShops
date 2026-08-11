package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.catalog.offer.OfferAction;
import com.enviouse.futureshops.money.PaymentSource;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class C2SPlayerShopOfferPacketTest {
    private static final UUID REQUEST_ID = UUID.fromString(
            "12345678-1234-5678-1234-567812345678");

    @Test
    void acquireRoundTripsStableOfferIdentityAndCorrelation() {
        C2SPlayerShopOfferPacket expected =
                new C2SPlayerShopOfferPacket(
                        new BlockPos(3, 70, 9), 4,
                        "player_listing_1", "money_and_barter",
                        OfferAction.ACQUIRE_FROM_SHOP, 12, 91L,
                        Optional.of(PaymentSource.PHYSICAL),
                        REQUEST_ID, 7);
        FriendlyByteBuf buffer =
                new FriendlyByteBuf(Unpooled.buffer());

        C2SPlayerShopOfferPacket.encode(expected, buffer);

        assertEquals(expected,
                C2SPlayerShopOfferPacket.decode(buffer));
    }

    @Test
    void sellRejectsPaymentSourceAndZeroRequestIdentity() {
        assertThrows(IllegalArgumentException.class, () ->
                new C2SPlayerShopOfferPacket(
                        BlockPos.ZERO, 0, "listing", "sell",
                        OfferAction.SELL_TO_SHOP, 1, 1L,
                        Optional.of(PaymentSource.WALLET),
                        REQUEST_ID, 0));
        assertThrows(IllegalArgumentException.class, () ->
                new C2SPlayerShopOfferPacket(
                        BlockPos.ZERO, 0, "listing", "sell",
                        OfferAction.SELL_TO_SHOP, 1, 1L,
                        Optional.empty(), new UUID(0L, 0L), 0));
    }

    @Test
    void decoderFailsClosedOnOversizeIdentifierAndQuantity() {
        FriendlyByteBuf oversized =
                new FriendlyByteBuf(Unpooled.buffer());
        oversized.writeBlockPos(BlockPos.ZERO);
        oversized.writeVarInt(0);
        oversized.writeUtf("x".repeat(
                C2SPlayerShopOfferPacket.MAX_IDENTIFIER_LENGTH + 1));
        assertThrows(DecoderException.class, () ->
                C2SPlayerShopOfferPacket.decode(oversized));

        FriendlyByteBuf quantity =
                new FriendlyByteBuf(Unpooled.buffer());
        quantity.writeBlockPos(BlockPos.ZERO);
        quantity.writeVarInt(0);
        quantity.writeUtf("listing");
        quantity.writeUtf("free");
        quantity.writeUtf(OfferAction.ACQUIRE_FROM_SHOP.name());
        quantity.writeVarInt(0);
        quantity.writeVarLong(1L);
        quantity.writeBoolean(false);
        quantity.writeUUID(REQUEST_ID);
        quantity.writeVarInt(0);
        assertThrows(DecoderException.class, () ->
                C2SPlayerShopOfferPacket.decode(quantity));
    }
}
