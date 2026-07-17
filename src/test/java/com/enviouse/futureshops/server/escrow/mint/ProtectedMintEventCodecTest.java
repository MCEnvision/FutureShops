package com.enviouse.futureshops.server.escrow.mint;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtectedMintEventCodecTest {
    @Test
    void allEventShapesRoundTripWithStableBytes() {
        ProtectedMintBatch batch = ProtectedMintTestFixtures.batch();
        ProtectedMintBatch replacement = ProtectedMintBatch.replacement(
                UUID.fromString("10000000-0000-0000-0000-000000000099"),
                ProtectedMintTestFixtures.HOLD_TRANSACTION, "mint.refund.codec",
                batch, 2, ProtectedMintTestFixtures.SERVER,
                ProtectedMintTestFixtures.CREATED.plusSeconds(4),
                ProtectedMintTestFixtures.EVIDENCE);
        ProtectedMintBatch issued = ProtectedMintBatch.issue(
                ProtectedMintTestFixtures.MINT_TRANSACTION, "mint.issue.codec",
                25L, 8, ProtectedMintTestFixtures.SERVER,
                ProtectedMintTestFixtures.CREATED,
                ProtectedMintTestFixtures.EVIDENCE);
        List<ProtectedMintJournalEvent> events = List.of(
                ProtectedMintJournalEvent.issue(issued),
                ProtectedMintJournalEvent.authorize(batch),
                ProtectedMintJournalEvent.materialize(batch.transactionId(), batch.batchId(),
                        "mint.materialize.codec", 10, batch.authorizedAt().plusSeconds(1)),
                ProtectedMintJournalEvent.reserve(ProtectedMintTestFixtures.HOLD_TRANSACTION,
                        batch.batchId(), "mint.reserve.codec", 4,
                        batch.authorizedAt().plusSeconds(2)),
                ProtectedMintJournalEvent.commit(ProtectedMintTestFixtures.HOLD_TRANSACTION,
                        batch.batchId(), "mint.commit.codec", 3,
                        batch.authorizedAt().plusSeconds(3)),
                ProtectedMintJournalEvent.refund(ProtectedMintTestFixtures.HOLD_TRANSACTION,
                        batch.batchId(), "mint.refund.codec", ProtectedMintState.SPENT,
                        2, replacement, batch.authorizedAt().plusSeconds(4)),
                ProtectedMintJournalEvent.quarantine(
                        ProtectedMintTestFixtures.HOLD_TRANSACTION, batch.batchId(),
                        "mint.quarantine.codec", ProtectedMintState.RESERVED, 1,
                        batch.authorizedAt().plusSeconds(5)));
        for (ProtectedMintJournalEvent event : events) {
            byte[] encoded = ProtectedMintEventCodec.encode(event);
            ProtectedMintJournalEvent decoded = ProtectedMintEventCodec.decode(encoded);
            assertEquals(event, decoded);
            assertArrayEquals(encoded, ProtectedMintEventCodec.encode(decoded));
        }
    }

    @Test
    void truncationNewerSchemaTrailingDataAndInvalidNanosFailClosed() {
        byte[] encoded = ProtectedMintEventCodec.encode(
                ProtectedMintJournalEvent.authorize(ProtectedMintTestFixtures.batch()));
        assertThrows(IllegalArgumentException.class,
                () -> ProtectedMintEventCodec.decode(Arrays.copyOf(encoded,
                        encoded.length - 1)));

        byte[] newer = encoded.clone();
        newer[7] = 2;
        assertThrows(IllegalStateException.class,
                () -> ProtectedMintEventCodec.decode(newer));

        byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);
        assertThrows(IllegalArgumentException.class,
                () -> ProtectedMintEventCodec.decode(trailing));

        byte[] invalidNanos = encoded.clone();
        Arrays.fill(invalidNanos, invalidNanos.length - 4, invalidNanos.length, (byte) 0xff);
        assertThrows(IllegalArgumentException.class,
                () -> ProtectedMintEventCodec.decode(invalidNanos));
    }

    @Test
    void malformedUnicodeCannotBeSilentlyReplaced() {
        assertThrows(IllegalArgumentException.class,
                () -> ProtectedMintJournalEvent.materialize(
                        ProtectedMintTestFixtures.MINT_TRANSACTION,
                        ProtectedMintTestFixtures.BATCH_ID, "mint.bad.\ud800", 1,
                        ProtectedMintTestFixtures.CREATED));
    }
}
