package com.enviouse.futureshops.network;

import com.enviouse.futureshops.network.packets.S2CMarketActionResponsePacket;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketActionRefundDetailTest {
    @Test
    void bazaarCancelDetailCarriesOrderAndExactRefund() {
        UUID orderId = UUID.fromString(
                "52000000-0000-0000-0000-000000000001");
        String detail = S2CMarketActionResponsePacket
                .bazaarCancelDetail(orderId, 3_007L);

        assertEquals(Optional.of(new S2CMarketActionResponsePacket
                        .BazaarCancelDetail(orderId, 3_007L)),
                S2CMarketActionResponsePacket.parseBazaarCancelDetail(
                        detail));
    }

    @Test
    void unrelatedDetailCannotInventARefund() {
        assertEquals(Optional.empty(),
                S2CMarketActionResponsePacket.parseBazaarCancelDetail(
                        "not-a-refund"));
    }
}
