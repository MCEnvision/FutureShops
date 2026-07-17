package com.enviouse.futureshops.server.escrow.journal;

import java.util.List;
import java.util.Objects;

public record JournalReplayBatch(List<JournalRecord> records,
                                 long startOffset,
                                 long nextOffset,
                                 long firstExpectedSequence,
                                 long nextExpectedSequence,
                                 long recordBytes,
                                 boolean endOfJournal) {
    public JournalReplayBatch {
        records = List.copyOf(Objects.requireNonNull(records, "records"));
        if (startOffset < 0L || nextOffset < startOffset
                || firstExpectedSequence <= 0L || nextExpectedSequence <= 0L
                || recordBytes < 0L || recordBytes != nextOffset - startOffset) {
            throw new IllegalArgumentException("Journal replay batch values are invalid");
        }
        long expected = firstExpectedSequence;
        for (JournalRecord record : records) {
            if (record.sequence() != expected || expected == Long.MAX_VALUE) {
                throw new IllegalArgumentException("Journal replay batch sequence is invalid");
            }
            expected++;
        }
        if (expected != nextExpectedSequence
                || records.isEmpty() != (recordBytes == 0L)) {
            throw new IllegalArgumentException("Journal replay batch progress is invalid");
        }
    }

    public int recordCount() {
        return records.size();
    }
}
