package com.enviouse.futureshops.server.escrow.checkpoint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class EscrowCheckpointReferenceCodec {
    public static final int FORMAT_VERSION = 1;
    public static final int ENCODED_BYTES = 116;

    private static final int MAGIC = 0x46534352;

    private EscrowCheckpointReferenceCodec() {
    }

    public static byte[] encode(EscrowCheckpointReference reference) {
        Objects.requireNonNull(reference, "reference");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(ENCODED_BYTES);
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeShort(FORMAT_VERSION);
            output.writeShort(0);
            writeUuid(output, reference.checkpointId());
            writeUuid(output, reference.sourceJournalLineageId());
            writeUuid(output, reference.replacementJournalLineageId());
            output.writeLong(reference.baseJournalSequence());
            output.writeLong(reference.createdAt().getEpochSecond());
            output.writeInt(reference.createdAt().getNano());
            output.writeLong(reference.checkpointFileBytes());
            output.write(reference.checkpointSha256());
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length != ENCODED_BYTES) {
                throw new IllegalStateException("Escrow checkpoint reference length does not match");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode escrow checkpoint reference", exception);
        }
    }

    public static EscrowCheckpointReference decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length != ENCODED_BYTES) {
            throw new IllegalArgumentException("Escrow checkpoint reference size is invalid");
        }
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Escrow checkpoint reference magic does not match");
            }
            int version = input.readUnsignedShort();
            if (version != FORMAT_VERSION) {
                throw new IllegalArgumentException(
                        version > FORMAT_VERSION
                                ? "Escrow checkpoint reference schema is newer than this build"
                                : "Escrow checkpoint reference schema is unsupported");
            }
            if (input.readUnsignedShort() != 0) {
                throw new IllegalArgumentException("Escrow checkpoint reference flags are unsupported");
            }
            UUID checkpointId = readUuid(input);
            UUID sourceLineage = readUuid(input);
            UUID replacementLineage = readUuid(input);
            long baseSequence = input.readLong();
            long seconds = input.readLong();
            int nanos = input.readInt();
            Instant createdAt;
            try {
                if (nanos < 0 || nanos > 999_999_999) {
                    throw new IllegalArgumentException("Escrow checkpoint reference time is invalid");
                }
                createdAt = Instant.ofEpochSecond(seconds, nanos);
            } catch (DateTimeException exception) {
                throw new IllegalArgumentException("Escrow checkpoint reference time is invalid", exception);
            }
            long fileBytes = input.readLong();
            byte[] sha256 = input.readNBytes(EscrowCheckpointManifest.SHA256_BYTES);
            if (sha256.length != EscrowCheckpointManifest.SHA256_BYTES || input.read() != -1) {
                throw new IllegalArgumentException("Escrow checkpoint reference is truncated");
            }
            EscrowCheckpointManifest manifest = new EscrowCheckpointManifest(
                    checkpointId, sourceLineage, replacementLineage, baseSequence,
                    createdAt, fileBytes, sha256);
            return new EscrowCheckpointReference(manifest);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to decode escrow checkpoint reference", exception);
        }
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }
}
