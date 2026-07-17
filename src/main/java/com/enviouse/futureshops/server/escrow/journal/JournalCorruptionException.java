package com.enviouse.futureshops.server.escrow.journal;

import java.io.IOException;

public final class JournalCorruptionException extends IOException {
    private final long offset;

    public JournalCorruptionException(long offset, String message) {
        super(message);
        this.offset = offset;
    }

    public JournalCorruptionException(long offset, String message, Throwable cause) {
        super(message, cause);
        this.offset = offset;
    }

    public long offset() {
        return offset;
    }
}
