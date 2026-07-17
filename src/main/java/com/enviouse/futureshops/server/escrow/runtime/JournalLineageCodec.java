package com.enviouse.futureshops.server.escrow.runtime;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Objects;

public final class JournalLineageCodec {
    private static final int VERSION = 1;

    private JournalLineageCodec() {
    }

    public static byte[] encode(JournalLineage lineage) {
        Objects.requireNonNull(lineage, "lineage");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(VERSION);
            BinaryCodecSupport.writeUuid(output, lineage.lineageId());
            output.writeLong(lineage.createdAt().getEpochSecond());
            output.writeInt(lineage.createdAt().getNano());
            output.flush();
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to encode escrow journal lineage", ex);
        }
    }

    public static JournalLineage decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
            if (input.readInt() != VERSION) {
                throw new IllegalArgumentException("Unsupported escrow journal lineage version");
            }
            java.util.UUID lineageId = BinaryCodecSupport.readUuid(input);
            Instant createdAt;
            try {
                long epochSecond = input.readLong();
                int nano = input.readInt();
                if (nano < 0 || nano > 999_999_999) {
                    throw new IllegalArgumentException("Invalid escrow journal lineage nanoseconds");
                }
                createdAt = Instant.ofEpochSecond(epochSecond, nano);
            } catch (DateTimeException exception) {
                throw new IllegalArgumentException("Invalid escrow journal lineage time", exception);
            }
            JournalLineage lineage = new JournalLineage(lineageId, createdAt);
            if (input.read() != -1) {
                throw new IllegalArgumentException("Escrow journal lineage has trailing data");
            }
            return lineage;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to decode escrow journal lineage", ex);
        }
    }
}
