package com.enviouse.futureshops.server.escrow.mint;

import com.enviouse.futureshops.server.escrow.runtime.EscrowJournalEventCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ProtectedMintEventCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES = EscrowJournalEventCodec.MAX_BODY_BYTES;

    private static final int MAGIC = 0x46534D54;

    private ProtectedMintEventCodec() {
    }

    public static byte[] encode(ProtectedMintJournalEvent event) {
        ObjectsSupport.require(event, "event");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            output.writeByte(event.operation().wireId());
            writeString(output, event.requestKey(), ProtectedMintText.MAX_REQUEST_KEY_LENGTH);
            writeUuid(output, event.transactionId());
            writeOptionalUuid(output, event.targetBatchId());
            output.writeInt(event.quantity());
            writeOptionalState(output, event.sourceState());
            output.writeByte(event.batch().isPresent() ? 1 : 0);
            if (event.batch().isPresent()) {
                writeBatch(output, event.batch().orElseThrow());
            }
            writeInstant(output, event.occurredAt());
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException("Protected mint event exceeds its binary limit");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode protected mint event", exception);
        }
    }

    public static ProtectedMintJournalEvent decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("Protected mint event payload is invalid");
        }
        ByteArrayInputStream bytes = new ByteArrayInputStream(encoded);
        try (DataInputStream input = new DataInputStream(bytes)) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Protected mint event magic is invalid");
            }
            int schema = input.readInt();
            if (schema > CURRENT_SCHEMA) {
                throw new IllegalStateException("Protected mint event schema is newer than this build");
            }
            if (schema != CURRENT_SCHEMA) {
                throw new IllegalArgumentException("Protected mint event schema is unsupported");
            }
            ProtectedMintOperation operation = ProtectedMintOperation.fromWireId(
                    input.readUnsignedByte());
            String requestKey = readString(input, ProtectedMintText.MAX_REQUEST_KEY_LENGTH);
            UUID transactionId = readUuid(input);
            Optional<UUID> targetBatchId = readOptionalUuid(input);
            int quantity = input.readInt();
            Optional<ProtectedMintState> sourceState = readOptionalState(input);
            int batchMarker = input.readUnsignedByte();
            if (batchMarker > 1) {
                throw new IllegalArgumentException("Protected mint event batch marker is invalid");
            }
            Optional<ProtectedMintBatch> batch = batchMarker == 1
                    ? Optional.of(readBatch(input)) : Optional.empty();
            Instant occurredAt = readInstant(input);
            if (bytes.available() != 0) {
                throw new IllegalArgumentException("Protected mint event has trailing data");
            }
            return new ProtectedMintJournalEvent(operation, requestKey, transactionId,
                    targetBatchId, quantity, sourceState, batch, occurredAt);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Protected mint event is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Protected mint event payload is invalid", exception);
        }
    }

    private static void writeBatch(DataOutputStream output, ProtectedMintBatch batch)
            throws IOException {
        writeUuid(output, batch.batchId());
        writeUuid(output, batch.transactionId());
        writeString(output, batch.authorizeRequestKey(), ProtectedMintText.MAX_REQUEST_KEY_LENGTH);
        output.writeLong(batch.denominationMinorUnits());
        output.writeInt(batch.authorizedCount());
        output.writeInt(batch.authorizedQuantity());
        output.writeInt(batch.availableQuantity());
        writeQuantityMap(output, batch.reservedQuantities());
        writeQuantityMap(output, batch.spentQuantities());
        output.writeInt(batch.refundedQuantity());
        output.writeInt(batch.quarantinedQuantity());
        writeOptionalUuid(output, batch.replacementForBatchId());
        writeString(output, batch.serverIdentityEvidence(),
                ProtectedMintText.MAX_SERVER_EVIDENCE_LENGTH);
        writeString(output, batch.checksumEvidence(),
                ProtectedMintText.MAX_CHECKSUM_EVIDENCE_LENGTH);
        writeInstant(output, batch.authorizedAt());
        writeInstant(output, batch.updatedAt());
        output.writeLong(batch.revision());
    }

    private static ProtectedMintBatch readBatch(DataInputStream input) throws IOException {
        return new ProtectedMintBatch(readUuid(input), readUuid(input),
                readString(input, ProtectedMintText.MAX_REQUEST_KEY_LENGTH),
                input.readLong(), input.readInt(), input.readInt(), input.readInt(),
                readQuantityMap(input), readQuantityMap(input), input.readInt(), input.readInt(),
                readOptionalUuid(input),
                readString(input, ProtectedMintText.MAX_SERVER_EVIDENCE_LENGTH),
                readString(input, ProtectedMintText.MAX_CHECKSUM_EVIDENCE_LENGTH),
                readInstant(input), readInstant(input), input.readLong());
    }

    private static void writeQuantityMap(DataOutputStream output, Map<UUID, Integer> values)
            throws IOException {
        output.writeInt(values.size());
        for (Map.Entry<UUID, Integer> entry : values.entrySet().stream()
                .sorted(Comparator.comparing(value -> value.getKey().toString())).toList()) {
            writeUuid(output, entry.getKey());
            output.writeInt(entry.getValue());
        }
    }

    private static Map<UUID, Integer> readQuantityMap(DataInputStream input) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > ProtectedMintBatch.MAX_RESERVATION_ENTRIES) {
            throw new IllegalArgumentException("Protected mint quantity map size is invalid");
        }
        Map<UUID, Integer> values = new HashMap<>();
        for (int index = 0; index < count; index++) {
            UUID key = readUuid(input);
            int quantity = input.readInt();
            if (quantity <= 0 || values.put(key, quantity) != null) {
                throw new IllegalArgumentException("Protected mint quantity map is invalid");
            }
        }
        return Map.copyOf(values);
    }

    private static void writeOptionalState(DataOutputStream output,
                                           Optional<ProtectedMintState> value)
            throws IOException {
        output.writeByte(value.isPresent() ? 1 : 0);
        if (value.isPresent()) {
            output.writeByte(value.orElseThrow().wireId());
        }
    }

    private static Optional<ProtectedMintState> readOptionalState(DataInputStream input)
            throws IOException {
        int marker = input.readUnsignedByte();
        if (marker > 1) {
            throw new IllegalArgumentException("Protected mint state marker is invalid");
        }
        return marker == 1
                ? Optional.of(ProtectedMintState.fromWireId(input.readUnsignedByte()))
                : Optional.empty();
    }

    private static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static void writeOptionalUuid(DataOutputStream output, Optional<UUID> value)
            throws IOException {
        output.writeByte(value.isPresent() ? 1 : 0);
        if (value.isPresent()) {
            writeUuid(output, value.orElseThrow());
        }
    }

    private static Optional<UUID> readOptionalUuid(DataInputStream input) throws IOException {
        int marker = input.readUnsignedByte();
        if (marker > 1) {
            throw new IllegalArgumentException("Protected mint optional marker is invalid");
        }
        return marker == 1 ? Optional.of(readUuid(input)) : Optional.empty();
    }

    private static void writeInstant(DataOutputStream output, Instant value) throws IOException {
        output.writeLong(value.getEpochSecond());
        output.writeInt(value.getNano());
    }

    private static Instant readInstant(DataInputStream input) throws IOException {
        long seconds = input.readLong();
        int nanos = input.readInt();
        if (nanos < 0 || nanos > 999_999_999) {
            throw new IllegalArgumentException("Protected mint timestamp nanoseconds are invalid");
        }
        try {
            return Instant.ofEpochSecond(seconds, nanos);
        } catch (DateTimeException | ArithmeticException exception) {
            throw new IllegalArgumentException("Protected mint timestamp is invalid", exception);
        }
    }

    private static void writeString(DataOutputStream output, String value, int maximumCharacters)
            throws IOException {
        if (value.length() > maximumCharacters) {
            throw new IllegalArgumentException("Protected mint string exceeds its limit");
        }
        byte[] utf8;
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            utf8 = new byte[encoded.remaining()];
            encoded.get(utf8);
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Protected mint string is not valid UTF-8",
                    exception);
        }
        if (utf8.length == 0 || utf8.length > Math.multiplyExact(maximumCharacters, 4)) {
            throw new IllegalArgumentException("Protected mint string encoding is invalid");
        }
        output.writeInt(utf8.length);
        output.write(utf8);
    }

    private static String readString(DataInputStream input, int maximumCharacters)
            throws IOException {
        int maximumBytes = Math.multiplyExact(maximumCharacters, 4);
        int size = input.readInt();
        if (size <= 0 || size > maximumBytes) {
            throw new IllegalArgumentException("Protected mint string length is invalid");
        }
        byte[] utf8 = input.readNBytes(size);
        if (utf8.length != size) {
            throw new EOFException("Protected mint string is truncated");
        }
        try {
            String value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(utf8)).toString();
            if (value.isEmpty() || value.length() > maximumCharacters) {
                throw new IllegalArgumentException("Protected mint string is invalid");
            }
            return value;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Protected mint string is not valid UTF-8", exception);
        }
    }

    private static final class ObjectsSupport {
        private static <T> T require(T value, String label) {
            if (value == null) {
                throw new NullPointerException(label);
            }
            return value;
        }
    }
}
