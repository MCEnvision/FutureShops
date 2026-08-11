package com.enviouse.futureshops.network.packets;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerShopCorrelationPacketTest {
    private static final BlockPos SHOP_POS = new BlockPos(8, 70, -12);
    private static final UUID REQUEST_ID = UUID.fromString(
            "91000000-0000-0000-0000-000000000001");

    @Test
    void purchaseRequestAndResultRoundTripCorrelationIdentity() {
        C2SPlayerShopBuyPacket request = new C2SPlayerShopBuyPacket(
                SHOP_POS, 3, 4, "MONEY", "PHYSICAL",
                REQUEST_ID, 27);
        FriendlyByteBuf requestBuffer = buffer();
        C2SPlayerShopBuyPacket.encode(request, requestBuffer);
        assertEquals(request,
                C2SPlayerShopBuyPacket.decode(requestBuffer));

        S2CPlayerShopResultPacket result =
                new S2CPlayerShopResultPacket(true, "BOUGHT", "",
                        REQUEST_ID, 27);
        FriendlyByteBuf resultBuffer = buffer();
        S2CPlayerShopResultPacket.encode(result, resultBuffer);
        assertEquals(result,
                S2CPlayerShopResultPacket.decode(resultBuffer));
    }

    @Test
    void buybackAndSettlementRequestsRoundTripCorrelationIdentity() {
        C2SPlayerShopSellPacket sell = new C2SPlayerShopSellPacket(
                SHOP_POS, 6, 9, REQUEST_ID, 41);
        FriendlyByteBuf sellBuffer = buffer();
        C2SPlayerShopSellPacket.encode(sell, sellBuffer);
        assertEquals(sell,
                C2SPlayerShopSellPacket.decode(sellBuffer));

        C2SPlayerShopSettlementClaimPacket settlement =
                new C2SPlayerShopSettlementClaimPacket(
                        SHOP_POS, REQUEST_ID, 42);
        FriendlyByteBuf settlementBuffer = buffer();
        C2SPlayerShopSettlementClaimPacket.encode(settlement,
                settlementBuffer);
        assertEquals(settlement,
                C2SPlayerShopSettlementClaimPacket.decode(
                        settlementBuffer));
    }

    @Test
    void requestPacketsRejectUncorrelatedIdentities() {
        UUID zero = new UUID(0L, 0L);

        assertThrows(IllegalArgumentException.class,
                () -> new C2SPlayerShopBuyPacket(SHOP_POS, 0, 1,
                        "MONEY", "WALLET", zero, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new C2SPlayerShopSellPacket(SHOP_POS, 0, 1,
                        zero, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new C2SPlayerShopSettlementClaimPacket(
                        SHOP_POS, zero, 0));
    }

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }
}
