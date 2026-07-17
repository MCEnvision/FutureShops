package com.enviouse.futureshops.server.escrow.journal;

public record JournalScanResult(long recordCount, long firstSequence, long lastSequence,
                                boolean truncatedTail, long validBytes, long originalBytes) {
    public JournalScanResult {
        if (recordCount < 0L || validBytes < 0L || originalBytes < validBytes) {
            throw new IllegalArgumentException("Journal scan values are invalid");
        }
        if (recordCount == 0L) {
            if (firstSequence != 0L || lastSequence != 0L || validBytes != 0L) {
                throw new IllegalArgumentException("Empty journal scan values are invalid");
            }
        } else {
            if (firstSequence <= 0L || lastSequence < firstSequence
                    || lastSequence - firstSequence + 1L != recordCount
                    || validBytes == 0L) {
                throw new IllegalArgumentException("Journal scan sequence values are invalid");
            }
        }
        if (truncatedTail != (originalBytes > validBytes)) {
            throw new IllegalArgumentException("Journal scan truncation values are invalid");
        }
    }

    public long discardedBytes() {
        return originalBytes - validBytes;
    }

    public boolean empty() {
        return recordCount == 0L;
    }
}
