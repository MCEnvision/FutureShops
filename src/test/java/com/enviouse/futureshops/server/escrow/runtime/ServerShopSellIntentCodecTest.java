package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerShopSellIntentCodecTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void preparedAndTerminalIntentsRoundTripCanonically() {
        ServerShopSellIntent prepared = ServerShopSellIntent.prepared(
                ServerShopSellTestFixtures.request(),
                ServerShopSellTestFixtures.wallet());
        ServerShopSellIntent terminal = prepared.abort(
                ServerShopSellIntent.Status.ABORTED_MISSING_ITEMS);

        for (ServerShopSellIntent intent : new ServerShopSellIntent[]{
                prepared, terminal}) {
            byte[] encoded = ServerShopSellIntentCodec.encode(intent);
            ServerShopSellIntent decoded =
                    ServerShopSellIntentCodec.decode(encoded);
            assertEquals(intent, decoded);
            assertEquals(intent.intentFingerprint(),
                    decoded.intentFingerprint());
            assertArrayEquals(encoded,
                    ServerShopSellIntentCodec.encode(decoded));
        }
    }

    @Test
    void truncatedAndTamperedIntentsAreRejected() {
        byte[] encoded = ServerShopSellIntentCodec.encode(
                ServerShopSellIntent.prepared(
                        ServerShopSellTestFixtures.request(),
                        ServerShopSellTestFixtures.wallet()));
        assertThrows(IllegalArgumentException.class,
                () -> ServerShopSellIntentCodec.decode(
                        Arrays.copyOf(encoded, encoded.length - 1)));

        byte[] tampered = encoded.clone();
        tampered[tampered.length - 1] ^= 1;
        assertThrows(IllegalArgumentException.class,
                () -> ServerShopSellIntentCodec.decode(tampered));
    }
}
