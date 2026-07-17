package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerShopSellCommitCodecTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void canonicalCommitRoundTripsExactly() {
        ServerShopSellCommit commit = ServerShopSellTestFixtures.commit();
        byte[] encoded = ServerShopSellCommitCodec.encode(commit);
        ServerShopSellCommit decoded =
                ServerShopSellCommitCodec.decode(encoded);

        assertEquals(commit, decoded);
        assertArrayEquals(encoded,
                ServerShopSellCommitCodec.encode(decoded));
    }

    @Test
    void trailingTruncatedAndCorruptFramesFailClosed() {
        byte[] encoded = ServerShopSellCommitCodec.encode(
                ServerShopSellTestFixtures.commit());
        byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);
        byte[] truncated = Arrays.copyOf(encoded, encoded.length - 1);
        byte[] corruptMagic = encoded.clone();
        corruptMagic[0] ^= 1;

        assertThrows(IllegalArgumentException.class,
                () -> ServerShopSellCommitCodec.decode(trailing));
        assertThrows(IllegalArgumentException.class,
                () -> ServerShopSellCommitCodec.decode(truncated));
        assertThrows(IllegalArgumentException.class,
                () -> ServerShopSellCommitCodec.decode(corruptMagic));
    }

    @Test
    void normalizedOutOfRangeInstantEncodingIsRejected() {
        byte[] encoded = ServerShopSellCommitCodec.encode(
                ServerShopSellTestFixtures.commit());
        byte[] noncanonical = encoded.clone();
        ByteBuffer buffer = ByteBuffer.wrap(noncanonical);
        buffer.position(40);
        skipString(buffer);
        skipString(buffer);
        skipString(buffer);
        buffer.position(buffer.position() + Integer.BYTES
                + Long.BYTES * 4);
        buffer.putInt(1_000_000_000);

        assertThrows(IllegalArgumentException.class,
                () -> ServerShopSellCommitCodec.decode(noncanonical));
    }

    private static void skipString(ByteBuffer buffer) {
        int length = buffer.getInt();
        buffer.position(buffer.position() + length);
    }
}
