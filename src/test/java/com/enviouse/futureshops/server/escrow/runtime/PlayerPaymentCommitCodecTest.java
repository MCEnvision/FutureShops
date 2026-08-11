package com.enviouse.futureshops.server.escrow.runtime;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerPaymentCommitCodecTest {
    @Test
    void overflowCommitRoundTripsWithStableFingerprint() {
        PlayerPaymentCommit commit = PlayerPaymentTestFixtures.overflow();

        byte[] encoded = PlayerPaymentCommitCodec.encode(commit);
        PlayerPaymentCommit decoded = PlayerPaymentCommitCodec.decode(
                encoded);

        assertEquals(commit, decoded);
        assertEquals(commit.fingerprint(), decoded.fingerprint());
        assertArrayEquals(encoded, PlayerPaymentCommitCodec.encode(decoded));
        assertEquals(150L, decoded.acceptedMinorUnits());
        assertEquals(50L, decoded.recipientDebtCreditMinorUnits());
        assertEquals(100L, decoded.recipientWalletCreditMinorUnits());
        assertEquals(850L, decoded.overflowClaimMinorUnits());
    }

    @Test
    void reservedFundsReduceRecipientCapacity() {
        PlayerPaymentCommit commit =
                PlayerPaymentTestFixtures.reservedOverflow();

        assertEquals(50L, commit.acceptedMinorUnits());
        assertEquals(450L, commit.overflowClaimMinorUnits());
    }

    @Test
    void factoryNormalizesCurrencyNameBeforeSealingEvidence() {
        PlayerPaymentCommit commit = PlayerPaymentCommit.create(
                PlayerPaymentTestFixtures.REQUEST_ID,
                PlayerPaymentTestFixtures.PAYER_ID,
                PlayerPaymentTestFixtures.RECIPIENT_ID,
                100L, 200L, 0L, 0L, 0L, 0L,
                1_000L, "  Coins  ", 2,
                PlayerPaymentTestFixtures.NOW);

        assertEquals("Coins", commit.currencyName());
        assertEquals("Coins", commit.completedTransaction().assetLots()
                .get(0).attributes().get(
                        PlayerPaymentCommit.ATTRIBUTE_CURRENCY_NAME));
        assertEquals(commit, PlayerPaymentCommitCodec.decode(
                PlayerPaymentCommitCodec.encode(commit)));
    }

    @Test
    void codecRejectsCorruptionNewerSchemaTrailingDataAndBounds() {
        byte[] encoded = PlayerPaymentCommitCodec.encode(
                PlayerPaymentTestFixtures.overflow());
        byte[] badMagic = encoded.clone();
        badMagic[0] ^= 1;
        assertThrows(IllegalArgumentException.class,
                () -> PlayerPaymentCommitCodec.decode(badMagic));
        byte[] newer = encoded.clone();
        newer[7] = 2;
        assertThrows(IllegalStateException.class,
                () -> PlayerPaymentCommitCodec.decode(newer));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerPaymentCommitCodec.decode(Arrays.copyOf(
                        encoded, encoded.length - 1)));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerPaymentCommitCodec.decode(Arrays.copyOf(
                        encoded, encoded.length + 1)));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerPaymentCommitCodec.decode(new byte[
                        PlayerPaymentCommitCodec.MAX_ENCODED_BYTES + 1]));
    }
}
