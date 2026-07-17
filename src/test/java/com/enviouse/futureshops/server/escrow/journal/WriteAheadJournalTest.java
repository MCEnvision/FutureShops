package com.enviouse.futureshops.server.escrow.journal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WriteAheadJournalTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void appendReplayAndReopenContinueTheSequence() throws Exception {
        Path path = temporaryDirectory.resolve("escrow.wal");
        UUID firstTransaction = UUID.randomUUID();
        UUID firstStep = UUID.randomUUID();
        UUID secondTransaction = UUID.randomUUID();
        UUID secondStep = UUID.randomUUID();

        try (WriteAheadJournal journal = WriteAheadJournal.open(path)) {
            assertEquals(1L, journal.nextSequence());
            JournalRecord first = journal.append(firstTransaction, firstStep, bytes("first"));
            JournalRecord second = journal.append(secondTransaction, secondStep, bytes("second"));
            assertEquals(1L, first.sequence());
            assertEquals(2L, second.sequence());
            assertEquals(2L, journal.recordCount());
            assertEquals(Files.size(path), journal.sizeBytes());

            JournalReplayBatch replay = journal.replayBatch(0L, 1L, 100,
                    WriteAheadJournal.MAX_REPLAY_BATCH_BYTES);
            assertTrue(replay.endOfJournal());
            assertEquals(List.of(first, second), replay.records());
        }

        try (WriteAheadJournal reopened = WriteAheadJournal.open(path)) {
            assertFalse(reopened.recovery().truncatedTail());
            assertEquals(2L, reopened.recovery().recordCount());
            assertEquals(2L, reopened.recordCount());
            assertEquals(Files.size(path), reopened.sizeBytes());
            assertEquals(3L, reopened.nextSequence());
            assertEquals(3L, reopened.append(UUID.randomUUID(), UUID.randomUUID(), bytes("third")).sequence());
        }

        try (WriteAheadJournal reopenedAgain = WriteAheadJournal.open(path)) {
            assertEquals(3L, reopenedAgain.recovery().recordCount());
            assertEquals(4L, reopenedAgain.nextSequence());
        }
    }

    @Test
    void openingTrimsOnlyAnIncompleteFinalRecord() throws Exception {
        Path path = temporaryDirectory.resolve("truncated.wal");
        long firstRecordEnd;
        long completeLength;
        try (WriteAheadJournal journal = WriteAheadJournal.open(path)) {
            journal.append(UUID.randomUUID(), UUID.randomUUID(), bytes("kept"));
            firstRecordEnd = Files.size(path);
            journal.append(UUID.randomUUID(), UUID.randomUUID(), bytes("discarded tail"));
            completeLength = Files.size(path);
        }

        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.truncate(completeLength - 3L);
        }

        try (WriteAheadJournal recovered = WriteAheadJournal.open(path)) {
            assertTrue(recovered.recovery().truncatedTail());
            assertEquals(firstRecordEnd, recovered.recovery().validBytes());
            assertEquals(completeLength - 3L, recovered.recovery().originalBytes());
            assertEquals(1L, recovered.recovery().recordCount());
            assertEquals(firstRecordEnd, Files.size(path));
            assertEquals(2L, recovered.nextSequence());
            assertEquals(2L, recovered.append(UUID.randomUUID(), UUID.randomUUID(), bytes("replacement")).sequence());
        }

        try (WriteAheadJournal reopened = WriteAheadJournal.open(path)) {
            assertFalse(reopened.recovery().truncatedTail());
            assertEquals(2L, reopened.recovery().recordCount());
            JournalReplayBatch replay = reopened.replayBatch(0L, 1L, 2,
                    WriteAheadJournal.MAX_REPLAY_BATCH_BYTES);
            assertArrayEquals(bytes("replacement"), replay.records().get(1).payload());
        }
    }

    @Test
    void openingTrimsAnIncompleteFinalPrefix() throws Exception {
        Path path = temporaryDirectory.resolve("prefix.wal");
        long validBytes;
        try (WriteAheadJournal journal = WriteAheadJournal.open(path)) {
            journal.append(UUID.randomUUID(), UUID.randomUUID(), bytes("complete"));
            validBytes = Files.size(path);
        }
        Files.write(path, new byte[]{0x46, 0x53, 0x4A}, StandardOpenOption.APPEND);

        try (WriteAheadJournal recovered = WriteAheadJournal.open(path)) {
            assertTrue(recovered.recovery().truncatedTail());
            assertEquals(3L, recovered.recovery().discardedBytes());
            assertEquals(validBytes, Files.size(path));
        }
    }

    @Test
    void checksumCorruptionInAMiddleRecordFailsClosed() throws Exception {
        Path path = temporaryDirectory.resolve("corrupt.wal");
        try (WriteAheadJournal journal = WriteAheadJournal.open(path)) {
            journal.append(UUID.randomUUID(), UUID.randomUUID(), bytes("first payload"));
            journal.append(UUID.randomUUID(), UUID.randomUUID(), bytes("unique middle payload"));
            journal.append(UUID.randomUUID(), UUID.randomUUID(), bytes("third payload"));
        }

        byte[] file = Files.readAllBytes(path);
        int payloadOffset = indexOf(file, bytes("unique middle payload"));
        assertTrue(payloadOffset > 0);
        file[payloadOffset + 4] ^= 0x40;
        Files.write(path, file);

        JournalCorruptionException exception = assertThrows(
                JournalCorruptionException.class,
                () -> WriteAheadJournal.open(path)
        );
        assertTrue(exception.offset() > 0L);
        assertTrue(exception.getMessage().contains("checksum"));
    }

    @Test
    void explicitSequenceMustBeExactlyNext() throws Exception {
        Path path = temporaryDirectory.resolve("sequence.wal");
        try (WriteAheadJournal journal = WriteAheadJournal.open(path)) {
            journal.append(1L, UUID.randomUUID(), UUID.randomUUID(), bytes("one"));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> journal.append(1L, UUID.randomUUID(), UUID.randomUUID(), bytes("duplicate"))
            );
            assertThrows(
                    IllegalArgumentException.class,
                    () -> journal.append(3L, UUID.randomUUID(), UUID.randomUUID(), bytes("gap"))
            );
            assertEquals(2L, journal.append(2L, UUID.randomUUID(), UUID.randomUUID(), bytes("two")).sequence());
        }
    }

    @Test
    void sequenceExhaustionIsRejectedBeforeWriting() throws Exception {
        Path path = temporaryDirectory.resolve("sequence exhausted.wal");
        try (WriteAheadJournal journal = WriteAheadJournal.open(path)) {
            java.lang.reflect.Field nextSequence = WriteAheadJournal.class
                    .getDeclaredField("nextSequence");
            nextSequence.setAccessible(true);
            nextSequence.setLong(journal, Long.MAX_VALUE);
            assertThrows(IOException.class,
                    () -> journal.append(Long.MAX_VALUE, UUID.randomUUID(), UUID.randomUUID(),
                            bytes("must not be written")));
            assertEquals(0L, Files.size(path));
            assertFalse(journal.failed());
        }
    }

    @Test
    void duplicateSequenceInAValidEncodedRecordFailsReplay() throws Exception {
        Path path = temporaryDirectory.resolve("duplicate replay.wal");
        byte[] firstRecord;
        try (WriteAheadJournal journal = WriteAheadJournal.open(path)) {
            journal.append(UUID.randomUUID(), UUID.randomUUID(), bytes("one"));
        }
        firstRecord = Files.readAllBytes(path);
        Files.write(path, firstRecord, StandardOpenOption.APPEND);

        JournalCorruptionException exception = assertThrows(
                JournalCorruptionException.class,
                () -> WriteAheadJournal.open(path)
        );
        assertEquals(firstRecord.length, exception.offset());
        assertTrue(exception.getMessage().contains("sequence"));
    }

    @Test
    void hardPayloadLimitIsEnforcedBeforeWriting() throws Exception {
        Path path = temporaryDirectory.resolve("bounded.wal");
        try (WriteAheadJournal journal = WriteAheadJournal.open(path)) {
            byte[] oversized = new byte[WriteAheadJournal.MAX_PAYLOAD_BYTES + 1];
            assertThrows(
                    IllegalArgumentException.class,
                    () -> journal.append(UUID.randomUUID(), UUID.randomUUID(), oversized)
            );
            assertEquals(0L, Files.size(path));
            assertEquals(1L, journal.nextSequence());
        }
    }

    @Test
    void recordsDefensivelyCopyPayloads() {
        byte[] payload = bytes("stable");
        JournalRecord record = new JournalRecord(1L, UUID.randomUUID(), UUID.randomUUID(), payload);
        payload[0] = 0;
        assertArrayEquals(bytes("stable"), record.payload());
        byte[] returned = record.payload();
        returned[1] = 0;
        assertArrayEquals(bytes("stable"), record.payload());
    }

    @Test
    void concurrentOpenAndAppendAfterCloseAreRejected() throws Exception {
        Path path = temporaryDirectory.resolve("locked.wal");
        WriteAheadJournal journal = WriteAheadJournal.open(path);
        try {
            IOException locked = assertThrows(IOException.class, () -> WriteAheadJournal.open(path));
            assertTrue(locked.getMessage().contains("already"));
        } finally {
            journal.close();
        }

        assertThrows(
                IOException.class,
                () -> journal.append(UUID.randomUUID(), UUID.randomUUID(), bytes("closed"))
        );
        try (WriteAheadJournal reopened = WriteAheadJournal.open(path)) {
            assertEquals(1L, reopened.nextSequence());
        }
    }

    @Test
    void invalidCompleteHeaderIsCorruptionInsteadOfATruncatedTail() throws Exception {
        Path path = temporaryDirectory.resolve("header.wal");
        ByteBuffer invalid = ByteBuffer.allocate(16);
        invalid.putInt(0x01020304);
        invalid.putShort((short) WriteAheadJournal.FORMAT_VERSION);
        invalid.putShort((short) 0);
        invalid.putInt(48);
        invalid.putInt(0);
        Files.write(path, invalid.array());

        JournalCorruptionException exception = assertThrows(
                JournalCorruptionException.class,
                () -> WriteAheadJournal.open(path)
        );
        assertEquals(0L, exception.offset());
    }

    @Test
    void metadataScanTracksHistoryWithoutReturningPayloads() throws Exception {
        Path path = temporaryDirectory.resolve("metadata.wal");
        long validBytes;
        try (WriteAheadJournal journal = WriteAheadJournal.open(path)) {
            for (int index = 0; index < 100; index++) {
                journal.append(UUID.randomUUID(), UUID.randomUUID(), bytes("record " + index));
            }
            validBytes = Files.size(path);
        }
        try (WriteAheadJournal journal = WriteAheadJournal.open(path)) {
            JournalScanResult scan = journal.recovery();
            assertEquals(100L, scan.recordCount());
            assertEquals(1L, scan.firstSequence());
            assertEquals(100L, scan.lastSequence());
            assertEquals(validBytes, scan.validBytes());
            assertFalse(scan.truncatedTail());
        }
    }

    @Test
    void replayBatchHonorsRecordAndByteLimitsAndMakesProgress() throws Exception {
        Path path = temporaryDirectory.resolve("batches.wal");
        try (WriteAheadJournal journal = WriteAheadJournal.open(path)) {
            journal.append(UUID.randomUUID(), UUID.randomUUID(), new byte[4096]);
            journal.append(UUID.randomUUID(), UUID.randomUUID(), bytes("second"));
            journal.append(UUID.randomUUID(), UUID.randomUUID(), bytes("third"));

            JournalReplayBatch first = journal.replayBatch(0L, 1L, 10, 1L);
            assertEquals(1, first.recordCount());
            assertTrue(first.recordBytes() > 1L);
            assertFalse(first.endOfJournal());
            assertEquals(2L, first.nextExpectedSequence());

            JournalReplayBatch second = journal.replayBatch(first.nextOffset(),
                    first.nextExpectedSequence(), 1, WriteAheadJournal.MAX_REPLAY_BATCH_BYTES);
            assertEquals(1, second.recordCount());
            assertFalse(second.endOfJournal());

            JournalReplayBatch third = journal.replayBatch(second.nextOffset(),
                    second.nextExpectedSequence(), 10,
                    WriteAheadJournal.MAX_REPLAY_BATCH_BYTES);
            assertEquals(1, third.recordCount());
            assertTrue(third.endOfJournal());

            JournalReplayBatch empty = journal.replayBatch(third.nextOffset(),
                    third.nextExpectedSequence(), 10,
                    WriteAheadJournal.MAX_REPLAY_BATCH_BYTES);
            assertTrue(empty.records().isEmpty());
            assertTrue(empty.endOfJournal());
        }
    }

    @Test
    void replayAlwaysAllowsOneMaximumSizeRecord() throws Exception {
        Path path = temporaryDirectory.resolve("maximum record.wal");
        byte[] payload = new byte[WriteAheadJournal.MAX_PAYLOAD_BYTES];
        payload[0] = 1;
        payload[payload.length - 1] = 2;
        try (WriteAheadJournal journal = WriteAheadJournal.open(path)) {
            journal.append(UUID.randomUUID(), UUID.randomUUID(), payload);
            JournalReplayBatch batch = journal.replayBatch(0L, 1L, 10, 1L);
            assertEquals(1, batch.recordCount());
            assertEquals(WriteAheadJournal.MAX_RECORD_BYTES, batch.recordBytes());
            assertArrayEquals(payload, batch.records().get(0).payload());
            assertTrue(batch.endOfJournal());
        }
    }

    @Test
    void replayBatchRejectsWrongOffsetsSequencesAndUnboundedRequests() throws Exception {
        Path path = temporaryDirectory.resolve("replay bounds.wal");
        try (WriteAheadJournal journal = WriteAheadJournal.open(path)) {
            journal.append(UUID.randomUUID(), UUID.randomUUID(), bytes("one"));
            assertThrows(JournalCorruptionException.class,
                    () -> journal.replayBatch(0L, 2L, 1, 1024L));
            assertThrows(JournalCorruptionException.class,
                    () -> journal.replayBatch(1L, 1L, 1, 1024L));
            assertThrows(IllegalArgumentException.class,
                    () -> journal.replayBatch(Files.size(path) + 1L, 2L, 1, 1024L));
            assertThrows(IllegalArgumentException.class,
                    () -> journal.replayBatch(0L, 1L,
                            WriteAheadJournal.MAX_REPLAY_BATCH_RECORDS + 1, 1024L));
            assertThrows(IllegalArgumentException.class,
                    () -> journal.replayBatch(0L, 1L, 1,
                            WriteAheadJournal.MAX_REPLAY_BATCH_BYTES + 1L));
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static int indexOf(byte[] source, byte[] target) {
        for (int index = 0; index <= source.length - target.length; index++) {
            boolean matches = true;
            for (int targetIndex = 0; targetIndex < target.length; targetIndex++) {
                if (source[index + targetIndex] != target[targetIndex]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return index;
            }
        }
        return -1;
    }
}
