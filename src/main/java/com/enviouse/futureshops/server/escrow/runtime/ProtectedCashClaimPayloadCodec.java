package com.enviouse.futureshops.server.escrow.runtime;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Objects;

public final class ProtectedCashClaimPayloadCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES = 4096;

    private static final int MAGIC = 0x46534350;

    private ProtectedCashClaimPayloadCodec() {
    }

    public static byte[] encode(ProtectedCashClaimPayload payload) {
        Objects.requireNonNull(payload, "payload");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            BinaryCodecSupport.writeUuid(output, payload.batchId());
            output.writeLong(payload.denominationMinorUnits());
            output.writeInt(payload.authorizedCount());
            output.writeInt(payload.portionIndex());
            output.writeInt(payload.portionCount());
            output.writeInt(payload.billCount());
            BinaryCodecSupport.writeString(output, payload.serverIdentityEvidence(),
                    ProtectedCashClaimPayload.MAX_SERVER_EVIDENCE_LENGTH * 4);
            BinaryCodecSupport.writeString(output, payload.checksumEvidence(),
                    ProtectedCashClaimPayload.MAX_CHECKSUM_EVIDENCE_LENGTH * 4);
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException(
                        "Protected cash claim payload exceeds its limit");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode protected cash claim payload", exception);
        }
    }

    public static ProtectedCashClaimPayload decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("Protected cash claim payload size is invalid");
        }
        ByteArrayInputStream bytes = new ByteArrayInputStream(encoded);
        try (DataInputStream input = new DataInputStream(bytes)) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Protected cash claim payload magic is invalid");
            }
            int schema = input.readInt();
            if (schema > CURRENT_SCHEMA) {
                throw new IllegalStateException(
                        "Protected cash claim payload schema is newer than this build");
            }
            if (schema != CURRENT_SCHEMA) {
                throw new IllegalArgumentException(
                        "Protected cash claim payload schema is unsupported");
            }
            ProtectedCashClaimPayload payload = new ProtectedCashClaimPayload(
                    BinaryCodecSupport.readUuid(input),
                    input.readLong(), input.readInt(), input.readInt(), input.readInt(),
                    input.readInt(),
                    BinaryCodecSupport.readString(input,
                            ProtectedCashClaimPayload.MAX_SERVER_EVIDENCE_LENGTH * 4),
                    BinaryCodecSupport.readString(input,
                            ProtectedCashClaimPayload.MAX_CHECKSUM_EVIDENCE_LENGTH * 4));
            if (bytes.available() != 0) {
                throw new IllegalArgumentException(
                        "Protected cash claim payload has trailing data");
            }
            return payload;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Protected cash claim payload is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Protected cash claim payload is invalid", exception);
        }
    }
}
