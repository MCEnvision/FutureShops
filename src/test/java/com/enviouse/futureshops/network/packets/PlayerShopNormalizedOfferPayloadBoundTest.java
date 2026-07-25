package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.network.ServerShopOfferNetworkCodec;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerShopNormalizedOfferPayloadBoundTest {
    @Test
    void aggregateNormalizedOfferPayloadIsRejectedBeforeParsing()
            throws Exception {
        FriendlyByteBuf buffer = new FriendlyByteBuf(
                Unpooled.buffer());
        try {
            buffer.writeVarInt(
                    ServerShopOfferNetworkCodec
                            .MAX_ENCODED_CATALOG_BYTES + 1);
            buffer.writeZero(
                    ServerShopOfferNetworkCodec
                            .MAX_ENCODED_CATALOG_BYTES + 1);
            Method decoder = S2CPlayerShopDataPacket.class
                    .getDeclaredMethod(
                            "readNormalizedOffers",
                            FriendlyByteBuf.class);
            decoder.setAccessible(true);
            InvocationTargetException failure = assertThrows(
                    InvocationTargetException.class,
                    () -> decoder.invoke(null, buffer));
            assertInstanceOf(DecoderException.class,
                    failure.getCause());
        } finally {
            buffer.release();
        }
    }
}
