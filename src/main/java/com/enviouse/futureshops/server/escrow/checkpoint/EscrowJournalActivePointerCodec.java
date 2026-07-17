package com.enviouse.futureshops.server.escrow.checkpoint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class EscrowJournalActivePointerCodec {
    public static final int FORMAT_VERSION = 1;
    public static final int ENCODED_BYTES = 176;

    private static final int MAGIC = 0x46534150;
    private static final int BODY_BYTES = ENCODED_BYTES - EscrowCheckpointManifest.SHA256_BYTES;

    private EscrowJournalActivePointerCodec() {
    }

    public static byte[] encode(EscrowJournalActivePointer pointer) {
        Objects.requireNonNull(pointer, "pointer");
        try {
            ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream(BODY_BYTES);
            DataOutputStream output = new DataOutputStream(bodyBytes);
            output.writeInt(MAGIC);
            output.writeShort(FORMAT_VERSION);
            output.writeShort(0);
            writeUuid(output, pointer.journalLineageId());
            byte[] reference = EscrowCheckpointReferenceCodec.encode(pointer.checkpointReference());
            output.writeInt(reference.length);
            output.write(reference);
            output.flush();
            byte[] body = bodyBytes.toByteArray();
            if (body.length != BODY_BYTES) {
                throw new IllegalStateException("Escrow journal pointer body length does not match");
            }
            ByteArrayOutputStream encoded = new ByteArrayOutputStream(ENCODED_BYTES);
            encoded.write(body);
            encoded.write(sha256().digest(body));
            return encoded.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode escrow journal pointer", exception);
        }
    }

    public static EscrowJournalActivePointer decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length != ENCODED_BYTES) {
            throw new IllegalArgumentException("Escrow journal pointer size is invalid");
        }
        byte[] body = Arrays.copyOf(encoded, BODY_BYTES);
        byte[] expectedChecksum = sha256().digest(body);
        byte[] storedChecksum = Arrays.copyOfRange(encoded, BODY_BYTES, encoded.length);
        if (!MessageDigest.isEqual(expectedChecksum, storedChecksum)) {
            throw new IllegalArgumentException("Escrow journal pointer checksum does not match");
        }
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(body));
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Escrow journal pointer magic does not match");
            }
            int version = input.readUnsignedShort();
            if (version != FORMAT_VERSION) {
                throw new IllegalArgumentException(
                        version > FORMAT_VERSION
                                ? "Escrow journal pointer schema is newer than this build"
                                : "Escrow journal pointer schema is unsupported");
            }
            if (input.readUnsignedShort() != 0) {
                throw new IllegalArgumentException("Escrow journal pointer flags are unsupported");
            }
            UUID lineageId = readUuid(input);
            int referenceBytes = input.readInt();
            if (referenceBytes != EscrowCheckpointReferenceCodec.ENCODED_BYTES) {
                throw new IllegalArgumentException("Escrow journal pointer reference size is invalid");
            }
            byte[] reference = input.readNBytes(referenceBytes);
            if (reference.length != referenceBytes || input.read() != -1) {
                throw new IllegalArgumentException("Escrow journal pointer is truncated");
            }
            return new EscrowJournalActivePointer(
                    lineageId, EscrowCheckpointReferenceCodec.decode(reference));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to decode escrow journal pointer", exception);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA256 is unavailable", exception);
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
