package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerShopBarterCommitCodecTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void canonicalCommitAndIntentRoundTripExactly() {
        ServerShopBarterCommit commit =
                ServerShopBarterTestFixtures.commit();
        ServerShopBarterIntent intent =
                ServerShopBarterIntent.prepared(
                        ServerShopBarterTestFixtures.request());
        byte[] commitBytes = ServerShopBarterCommitCodec.encode(commit);
        byte[] intentBytes = ServerShopBarterIntentCodec.encode(intent);

        ServerShopBarterCommit decodedCommit =
                ServerShopBarterCommitCodec.decode(commitBytes);
        ServerShopBarterIntent decodedIntent =
                ServerShopBarterIntentCodec.decode(intentBytes);

        assertEquals(commit, decodedCommit);
        assertEquals(intent, decodedIntent);
        assertArrayEquals(commitBytes,
                ServerShopBarterCommitCodec.encode(decodedCommit));
        assertArrayEquals(intentBytes,
                ServerShopBarterIntentCodec.encode(decodedIntent));
        ItemStack ingredient = ItemStackSnapshotCodec.decode(
                decodedCommit.ingredients().get(0).exactItemTemplate());
        assertEquals("forest", ingredient.getTag().getString(
                "barter_variant"));
    }

    @Test
    void trailingTruncatedAndCorruptCommitFramesFailClosed() {
        byte[] encoded = ServerShopBarterCommitCodec.encode(
                ServerShopBarterTestFixtures.commit());
        byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);
        byte[] truncated = Arrays.copyOf(encoded, encoded.length - 1);
        byte[] corruptMagic = encoded.clone();
        corruptMagic[0] ^= 1;

        assertThrows(IllegalArgumentException.class,
                () -> ServerShopBarterCommitCodec.decode(trailing));
        assertThrows(IllegalArgumentException.class,
                () -> ServerShopBarterCommitCodec.decode(truncated));
        assertThrows(IllegalArgumentException.class,
                () -> ServerShopBarterCommitCodec.decode(corruptMagic));
    }

    @Test
    void trailingTruncatedAndCorruptIntentFramesFailClosed() {
        byte[] encoded = ServerShopBarterIntentCodec.encode(
                ServerShopBarterIntent.prepared(
                        ServerShopBarterTestFixtures.request()));
        byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);
        byte[] truncated = Arrays.copyOf(encoded, encoded.length - 1);
        byte[] corruptMagic = encoded.clone();
        corruptMagic[0] ^= 1;

        assertThrows(IllegalArgumentException.class,
                () -> ServerShopBarterIntentCodec.decode(trailing));
        assertThrows(IllegalArgumentException.class,
                () -> ServerShopBarterIntentCodec.decode(truncated));
        assertThrows(IllegalArgumentException.class,
                () -> ServerShopBarterIntentCodec.decode(corruptMagic));
    }

    @Test
    void invalidNanosecondsAndUnboundedCountsFailClosed() {
        byte[] invalidInstant = ServerShopBarterCommitCodec.encode(
                ServerShopBarterTestFixtures.commit());
        ByteBuffer instant = ByteBuffer.wrap(invalidInstant);
        positionAfterIdentityStrings(instant);
        instant.position(instant.position() + Integer.BYTES
                + Long.BYTES * 3);
        instant.putInt(1_000_000_000);

        byte[] invalidCount = ServerShopBarterIntentCodec.encode(
                ServerShopBarterIntent.prepared(
                        ServerShopBarterTestFixtures.request()));
        ByteBuffer count = ByteBuffer.wrap(invalidCount);
        positionAfterIdentityStrings(count);
        count.position(count.position() + Integer.BYTES
                + Long.BYTES * 3 + Integer.BYTES);
        count.putInt(ServerShopBarterCommit.MAX_INGREDIENTS + 1);

        assertThrows(IllegalArgumentException.class,
                () -> ServerShopBarterCommitCodec.decode(
                        invalidInstant));
        assertThrows(IllegalArgumentException.class,
                () -> ServerShopBarterIntentCodec.decode(invalidCount));
    }

    private static void positionAfterIdentityStrings(ByteBuffer buffer) {
        buffer.position(Integer.BYTES * 2 + Long.BYTES * 4);
        skipString(buffer);
        skipString(buffer);
    }

    private static void skipString(ByteBuffer buffer) {
        int length = buffer.getInt();
        buffer.position(buffer.position() + length);
    }
}
