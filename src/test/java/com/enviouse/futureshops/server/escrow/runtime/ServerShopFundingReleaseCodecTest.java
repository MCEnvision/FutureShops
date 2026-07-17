package com.enviouse.futureshops.server.escrow.runtime;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerShopFundingReleaseCodecTest {
    @Test
    void exactReleaseRoundTrips() {
        ServerShopFundingRelease release = release();

        assertEquals(release, ServerShopFundingReleaseCodec.decode(
                ServerShopFundingReleaseCodec.encode(release)));
    }

    @Test
    void trailingAndConflictingEvidenceAreRejected() {
        byte[] encoded = ServerShopFundingReleaseCodec.encode(release());
        byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);
        byte[] conflicting = encoded.clone();
        conflicting[8] ^= 1;

        assertThrows(IllegalArgumentException.class,
                () -> ServerShopFundingReleaseCodec.decode(trailing));
        assertThrows(IllegalArgumentException.class,
                () -> ServerShopFundingReleaseCodec.decode(conflicting));
        assertThrows(IllegalArgumentException.class,
                () -> ServerShopFundingReleaseCodec.decode(
                        Arrays.copyOf(encoded, encoded.length - 1)));
    }

    private static ServerShopFundingRelease release() {
        UUID purchase = UUID.randomUUID();
        return ServerShopFundingRelease.create(purchase,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                125L, Instant.parse("2026-07-17T20:00:00.123456789Z"));
    }
}
