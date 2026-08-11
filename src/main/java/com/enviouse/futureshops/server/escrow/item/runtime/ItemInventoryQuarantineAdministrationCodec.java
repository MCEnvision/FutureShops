package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.runtime.ClaimJournalCodec;
import com.enviouse.futureshops.server.escrow.runtime.EscrowJournalEventCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ItemInventoryQuarantineAdministrationCodec {
    private static final int MAGIC = 0x49514144;
    private static final int VERSION = 1;

    private ItemInventoryQuarantineAdministrationCodec() {
    }

    public static byte[] encode(
            ItemInventoryQuarantineAdministration administration
    ) {
        Objects.requireNonNull(administration, "administration");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeShort(VERSION);
            writeUuid(output, administration.commandId());
            writeUuid(output, administration.requestId());
            writeUuid(output, administration.playerId());
            writeUuid(output, administration.actorId());
            output.writeByte(administration.action().wireCode());
            output.writeLong(administration.expectedJournalRevision());
            output.write(administration.expectedQuarantineDigest());
            writeString(output, administration.reason());
            writeInstant(output, administration.reviewedAt());
            output.writeBoolean(administration.refundClaim().isPresent());
            if (administration.refundClaim().isPresent()) {
                byte[] claim = ClaimJournalCodec.encodeClaim(
                        administration.refundClaim().orElseThrow());
                output.writeInt(claim.length);
                output.write(claim);
            }
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > EscrowJournalEventCodec.MAX_BODY_BYTES) {
                throw new IllegalArgumentException(
                        "Item inventory quarantine administration is too large");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode item inventory quarantine administration",
                    exception);
        }
    }

    public static ItemInventoryQuarantineAdministration decode(
            byte[] encoded
    ) {
        byte[] value = Objects.requireNonNull(encoded, "encoded").clone();
        if (value.length == 0
                || value.length > EscrowJournalEventCodec.MAX_BODY_BYTES) {
            throw new IllegalArgumentException(
                    "Item inventory quarantine administration size is invalid");
        }
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(value))) {
            if (input.readInt() != MAGIC
                    || input.readUnsignedShort() != VERSION) {
                throw new IllegalArgumentException(
                        "Item inventory quarantine administration header is invalid");
            }
            UUID commandId = readUuid(input);
            UUID requestId = readUuid(input);
            UUID playerId = readUuid(input);
            UUID actorId = readUuid(input);
            ItemInventoryQuarantineAdministrativeAction action =
                    ItemInventoryQuarantineAdministrativeAction
                            .fromWireCode(input.readUnsignedByte());
            long revision = input.readLong();
            byte[] digest = input.readNBytes(32);
            if (digest.length != 32) {
                throw new EOFException();
            }
            String reason = readString(input,
                    ItemInventoryQuarantineAdministration.MAX_REASON_LENGTH);
            Instant reviewedAt = readInstant(input);
            int refundFlag = input.readUnsignedByte();
            if (refundFlag > 1) {
                throw new IllegalArgumentException(
                        "Item inventory quarantine refund flag is invalid");
            }
            Optional<EscrowClaim> refund = Optional.empty();
            if (refundFlag == 1) {
                int length = input.readInt();
                if (length <= 0 || length > input.available()) {
                    throw new IllegalArgumentException(
                            "Item inventory quarantine refund is invalid");
                }
                refund = Optional.of(ClaimJournalCodec.decodeClaim(
                        input.readNBytes(length)));
            }
            if (input.available() != 0) {
                throw new IllegalArgumentException(
                        "Item inventory quarantine administration has trailing data");
            }
            return new ItemInventoryQuarantineAdministration(commandId,
                    requestId, playerId, actorId, action, revision, digest,
                    refund, reason, reviewedAt);
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Item inventory quarantine administration is truncated",
                    exception);
        } catch (IOException | DateTimeException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Item inventory quarantine administration is invalid",
                    exception);
        }
    }

    private static void writeUuid(DataOutputStream output, UUID value)
            throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static void writeString(DataOutputStream output, String value)
            throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static String readString(DataInputStream input, int maximum)
            throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > maximum * 4
                || length > input.available()) {
            throw new IllegalArgumentException(
                    "Item inventory quarantine text is invalid");
        }
        byte[] encoded = input.readNBytes(length);
        String value = new String(encoded, StandardCharsets.UTF_8);
        if (value.length() > maximum
                || !java.util.Arrays.equals(encoded, value.getBytes(
                StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException(
                    "Item inventory quarantine text is invalid");
        }
        return value;
    }

    private static void writeInstant(DataOutputStream output, Instant value)
            throws IOException {
        output.writeLong(value.getEpochSecond());
        output.writeInt(value.getNano());
    }

    private static Instant readInstant(DataInputStream input)
            throws IOException {
        return Instant.ofEpochSecond(input.readLong(), input.readInt());
    }
}
