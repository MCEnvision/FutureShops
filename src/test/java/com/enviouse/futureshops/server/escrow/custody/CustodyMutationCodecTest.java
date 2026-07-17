package com.enviouse.futureshops.server.escrow.custody;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustodyMutationCodecTest {
    @Test
    void reserveMutationRoundTripsWithStableBytes() {
        CustodyMutation mutation = CustodyMutation.reserve(
                CustodyTestFixtures.protectedCurrencyLot("codec reserve", 25L, 3));

        byte[] first = CustodyMutationCodec.encode(mutation);
        CustodyMutation decoded = CustodyMutationCodec.decode(first);
        byte[] second = CustodyMutationCodec.encode(decoded);

        assertEquals(mutation, decoded);
        org.junit.jupiter.api.Assertions.assertArrayEquals(first, second);
    }

    @Test
    void terminalMutationRoundTripsAndAppliesIdempotently() {
        CustodyLot held = CustodyTestFixtures.foreignCurrencyLot("codec foreign reserve", 30L, 3);
        CustodyMutation reserve = CustodyMutation.reserve(held);
        CustodyMutation release = CustodyMutation.terminal(held, CustodyOperation.RELEASE,
                "codec foreign release", CustodyTestFixtures.terminalEvidence("codec release"),
                CustodyTestFixtures.NOW.plusSeconds(1));
        CustodySavedData data = new CustodySavedData();

        data.applyCommitted(CustodyMutationCodec.decode(CustodyMutationCodec.encode(reserve)));
        assertEquals(CustodyLotState.RELEASED,
                data.applyCommitted(CustodyMutationCodec.decode(
                        CustodyMutationCodec.encode(release))).lot().state());
        assertEquals(true, data.applyCommitted(release).replayed());
    }

    @Test
    void codecRejectsNewerVersionTrailingDataAndOversizedPayloads() {
        byte[] encoded = CustodyMutationCodec.encode(CustodyMutation.reserve(
                CustodyTestFixtures.itemLot("codec bounds", 1)));
        byte[] newer = encoded.clone();
        newer[5] = 3;
        byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);

        assertThrows(IllegalArgumentException.class, () -> CustodyMutationCodec.decode(newer));
        assertThrows(IllegalArgumentException.class, () -> CustodyMutationCodec.decode(trailing));
        assertThrows(IllegalArgumentException.class, () -> CustodyMutationCodec.decode(
                new byte[CustodyMutationCodec.MAX_ENCODED_BYTES + 1]));
    }

    @Test
    void codecPreservesNanosecondInstantsAndAcceptsBoundedOneMegabyteNbt() {
        byte[] nbt = new byte[CustodyItemSnapshot.MAX_NBT_BYTES];
        Arrays.fill(nbt, (byte) 7);
        CustodyItemSnapshot snapshot = CustodyItemSnapshot.capture("minecraft:shulker_box", 1, nbt);
        Instant precise = Instant.parse("2026-07-16T12:00:00.123456789Z");
        CustodyLot lot = CustodyLot.held(UUID.randomUUID(), UUID.randomUUID(), "large precise",
                CustodyAssetType.ITEM_STACK, CustodyProtectionTier.RECONCILED, 1L, "",
                List.of(snapshot), List.of(), CustodyTestFixtures.evidence("player_inventory",
                        CustodyAdapterCapability.RECONCILABLE, "large precise"), precise);
        CustodyMutation mutation = CustodyMutation.reserve(lot);

        byte[] encoded = CustodyMutationCodec.encode(mutation);

        org.junit.jupiter.api.Assertions.assertTrue(encoded.length > 1_048_576);
        assertEquals(mutation, CustodyMutationCodec.decode(encoded));
    }

    @Test
    void decoderRejectsMalformedUtf8AndNonCanonicalBoolean() {
        CustodyMutation reserve = CustodyMutation.reserve(
                CustodyTestFixtures.itemLot("utf marker custody", 1));
        byte[] malformedUtf = CustodyMutationCodec.encode(reserve);
        int utfOffset = indexOf(malformedUtf, "utf marker custody".getBytes(StandardCharsets.UTF_8));
        malformedUtf[utfOffset] = (byte) 0xC3;
        malformedUtf[utfOffset + 1] = 0x28;

        CustodyLot held = CustodyTestFixtures.itemLot("boolean reserve", 1);
        CustodyMutation terminal = CustodyMutation.terminal(held, CustodyOperation.RELEASE,
                "strict boolean terminal", CustodyTestFixtures.terminalEvidence("strict boolean"),
                CustodyTestFixtures.NOW.plusNanos(7));
        byte[] nonCanonicalBoolean = CustodyMutationCodec.encode(terminal);
        byte[] marker = "strict boolean terminal".getBytes(StandardCharsets.UTF_8);
        int booleanOffset = indexOf(nonCanonicalBoolean, marker) + marker.length;
        nonCanonicalBoolean[booleanOffset] = 2;

        assertThrows(IllegalArgumentException.class,
                () -> CustodyMutationCodec.decode(malformedUtf));
        assertThrows(IllegalArgumentException.class,
                () -> CustodyMutationCodec.decode(nonCanonicalBoolean));
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        for (int offset = 0; offset <= haystack.length - needle.length; offset++) {
            boolean matched = true;
            for (int index = 0; index < needle.length; index++) {
                if (haystack[offset + index] != needle[index]) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return offset;
            }
        }
        throw new AssertionError("Marker was not found in encoded custody data");
    }
}
