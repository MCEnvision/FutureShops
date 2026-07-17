package com.enviouse.futureshops.server.escrow.journal;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class JournalRecord {
    private final long sequence;
    private final UUID transactionId;
    private final UUID stepId;
    private final byte[] payload;

    public JournalRecord(long sequence, UUID transactionId, UUID stepId, byte[] payload) {
        if (sequence <= 0L) {
            throw new IllegalArgumentException("Sequence must be positive");
        }
        this.sequence = sequence;
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.stepId = Objects.requireNonNull(stepId, "stepId");
        this.payload = Objects.requireNonNull(payload, "payload").clone();
    }

    public long sequence() {
        return sequence;
    }

    public UUID transactionId() {
        return transactionId;
    }

    public UUID stepId() {
        return stepId;
    }

    public byte[] payload() {
        return payload.clone();
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) {
            return true;
        }
        if (!(value instanceof JournalRecord other)) {
            return false;
        }
        return sequence == other.sequence
                && transactionId.equals(other.transactionId)
                && stepId.equals(other.stepId)
                && Arrays.equals(payload, other.payload);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(sequence, transactionId, stepId);
        return 31 * result + Arrays.hashCode(payload);
    }

    @Override
    public String toString() {
        return "JournalRecord{" + sequence + ", " + transactionId + ", " + stepId + ", " + payload.length + " bytes}";
    }
}
