package com.enviouse.futureshops.server.escrow.store;

import com.enviouse.futureshops.server.escrow.model.DimensionAwareShopReference;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLot;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLotType;
import com.enviouse.futureshops.server.escrow.model.EscrowError;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipant;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipantRole;
import com.enviouse.futureshops.server.escrow.model.EscrowParty;
import com.enviouse.futureshops.server.escrow.model.EscrowPartyType;
import com.enviouse.futureshops.server.escrow.model.EscrowProtectionLevel;
import com.enviouse.futureshops.server.escrow.model.EscrowRequestKey;
import com.enviouse.futureshops.server.escrow.model.EscrowRetryMetadata;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTimestamps;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import com.enviouse.futureshops.server.escrow.model.MoneyAmount;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public final class EscrowTransactionByteCodec {
    private static final int MAGIC = 0x46533345;
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES = EscrowCodecLimits.MAX_BINARY_BYTES;

    private EscrowTransactionByteCodec() {
    }

    public static byte[] encode(EscrowTransaction transaction) {
        EscrowTransactionNbtCodec.validateBounds(transaction);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(
                new LimitedOutputStream(bytes, MAX_ENCODED_BYTES))) {
            Writer writer = new Writer(output);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            writer.writeUuid(transaction.transactionId().value());
            writer.writeOptional(transaction.parentTransactionId(), value -> writer.writeUuid(value.value()));
            writer.writeString(transaction.requestKey().value(), EscrowRequestKey.MAX_LENGTH);
            writer.writeEnum(transaction.operation());
            writer.writeEnum(transaction.state());
            writer.writeParticipants(transaction.participants());
            writer.writeAssetLots(transaction.assetLots());
            writer.writeTimestamps(transaction.timestamps());
            output.writeLong(transaction.revision());
            output.writeLong(transaction.configRevision());
            writer.writeOptional(transaction.lastError(), writer::writeError);
            writer.writeRetry(transaction.retryMetadata());
            writer.writeOptional(transaction.shopReference(), writer::writeShopReference);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Escrow transaction exceeds its binary limit", exception);
        }
    }

    public static EscrowTransaction decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalStateException("Escrow transaction binary payload is invalid");
        }
        ByteArrayInputStream bytes = new ByteArrayInputStream(encoded);
        try (DataInputStream input = new DataInputStream(bytes)) {
            Reader reader = new Reader(input, bytes);
            if (input.readInt() != MAGIC) {
                throw new IllegalStateException("Escrow transaction binary magic is invalid");
            }
            int schema = input.readInt();
            if (schema > CURRENT_SCHEMA) {
                throw new IllegalStateException("Escrow transaction binary schema is newer than this build");
            }
            if (schema != CURRENT_SCHEMA) {
                throw new IllegalStateException("Escrow transaction binary schema is unsupported");
            }
            EscrowTransaction transaction = new EscrowTransaction(
                    new EscrowTransactionId(reader.readUuid()),
                    reader.readOptional(() -> new EscrowTransactionId(reader.readUuid())),
                    new EscrowRequestKey(reader.readString(EscrowRequestKey.MAX_LENGTH)),
                    reader.readEnum(EscrowOperation.class, "operation"),
                    reader.readEnum(EscrowState.class, "state"),
                    reader.readParticipants(),
                    reader.readAssetLots(),
                    reader.readTimestamps(),
                    input.readLong(),
                    input.readLong(),
                    reader.readOptional(reader::readError),
                    reader.readRetry(),
                    reader.readOptional(reader::readShopReference)
            );
            if (bytes.available() != 0) {
                throw new IllegalStateException("Escrow transaction binary payload has trailing data");
            }
            EscrowTransactionNbtCodec.validateBounds(transaction);
            return transaction;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (EOFException exception) {
            throw new IllegalStateException("Escrow transaction binary payload is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Escrow transaction binary payload is invalid", exception);
        }
    }

    private static final class Writer {
        private final DataOutputStream output;

        private Writer(DataOutputStream output) {
            this.output = output;
        }

        private void writeUuid(UUID value) throws IOException {
            output.writeLong(value.getMostSignificantBits());
            output.writeLong(value.getLeastSignificantBits());
        }

        private <T> void writeOptional(Optional<T> value, IoConsumer<T> consumer) throws IOException {
            output.writeBoolean(value.isPresent());
            if (value.isPresent()) {
                consumer.accept(value.orElseThrow());
            }
        }

        private void writeString(String value, int maximumCharacters) throws IOException {
            EscrowCodecLimits.requireString("Escrow binary string", value, maximumCharacters);
            byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
            int maximumBytes = Math.multiplyExact(maximumCharacters, 4);
            if (utf8.length > maximumBytes) {
                throw new IllegalStateException("Escrow binary string exceeds its limit");
            }
            output.writeInt(utf8.length);
            output.write(utf8);
        }

        private void writeEnum(Enum<?> value) throws IOException {
            writeString(value.name(), 128);
        }

        private void writeParticipants(Set<EscrowParticipant> participants) throws IOException {
            List<EscrowParticipant> ordered = new ArrayList<>(participants);
            ordered.sort(Comparator.comparing((EscrowParticipant value) -> value.party().type().name())
                    .thenComparing(value -> value.party().id()));
            output.writeInt(ordered.size());
            for (EscrowParticipant participant : ordered) {
                writeParty(participant.party());
                List<EscrowParticipantRole> roles = participant.roles().stream()
                        .sorted(Comparator.comparing(Enum::name)).toList();
                output.writeInt(roles.size());
                for (EscrowParticipantRole role : roles) {
                    writeEnum(role);
                }
            }
        }

        private void writeAssetLots(List<EscrowAssetLot> lots) throws IOException {
            output.writeInt(lots.size());
            for (EscrowAssetLot lot : lots) {
                writeUuid(lot.lotId());
                writeEnum(lot.type());
                writeEnum(lot.protectionLevel());
                writeParty(lot.source());
                writeParty(lot.destination());
                output.writeLong(lot.quantity());
                writeOptional(lot.money(), this::writeMoney);
                byte[] payload = lot.serializedPayload();
                output.writeInt(payload.length);
                output.write(payload);
                writeStringMap(lot.attributes(), EscrowCodecLimits.MAX_ATTRIBUTES);
            }
        }

        private void writeParty(EscrowParty party) throws IOException {
            writeEnum(party.type());
            writeString(party.id(), EscrowParty.MAX_ID_LENGTH);
        }

        private void writeMoney(MoneyAmount money) throws IOException {
            writeString(money.currencyId(), MoneyAmount.MAX_CURRENCY_ID_LENGTH);
            output.writeLong(money.minorUnits());
        }

        private void writeTimestamps(EscrowTimestamps timestamps) throws IOException {
            writeInstant(timestamps.createdAt());
            writeInstant(timestamps.updatedAt());
            writeOptional(timestamps.commitDecidedAt(), this::writeInstant);
            writeOptional(timestamps.terminalAt(), this::writeInstant);
        }

        private void writeInstant(Instant instant) throws IOException {
            output.writeLong(instant.getEpochSecond());
            output.writeInt(instant.getNano());
        }

        private void writeError(EscrowError error) throws IOException {
            writeString(error.code(), EscrowError.MAX_CODE_LENGTH);
            writeString(error.message(), EscrowError.MAX_MESSAGE_LENGTH);
            output.writeBoolean(error.retryable());
            writeInstant(error.occurredAt());
            writeStringMap(error.details(), EscrowCodecLimits.MAX_ERROR_DETAILS);
        }

        private void writeRetry(EscrowRetryMetadata retry) throws IOException {
            output.writeInt(retry.attemptCount());
            output.writeInt(retry.maxAttempts());
            writeOptional(retry.nextAttemptAt(), this::writeInstant);
            writeOptional(retry.resumeState(), this::writeEnum);
        }

        private void writeShopReference(DimensionAwareShopReference reference) throws IOException {
            writeString(reference.shopId(), DimensionAwareShopReference.MAX_SHOP_ID_LENGTH);
            writeString(reference.dimensionId(), DimensionAwareShopReference.MAX_DIMENSION_ID_LENGTH);
            output.writeInt(reference.blockX());
            output.writeInt(reference.blockY());
            output.writeInt(reference.blockZ());
        }

        private void writeStringMap(Map<String, String> values, int maximumSize) throws IOException {
            EscrowCodecLimits.requireCount("Escrow binary map", values.size(), maximumSize);
            output.writeInt(values.size());
            for (Map.Entry<String, String> entry : new TreeMap<>(values).entrySet()) {
                writeString(entry.getKey(), EscrowCodecLimits.MAX_MAP_KEY_LENGTH);
                writeString(entry.getValue(), EscrowCodecLimits.MAX_MAP_VALUE_LENGTH);
            }
        }
    }

    private static final class Reader {
        private final DataInputStream input;
        private final ByteArrayInputStream bytes;
        private int totalPayloadBytes;

        private Reader(DataInputStream input, ByteArrayInputStream bytes) {
            this.input = input;
            this.bytes = bytes;
        }

        private UUID readUuid() throws IOException {
            return new UUID(input.readLong(), input.readLong());
        }

        private <T> Optional<T> readOptional(IoSupplier<T> supplier) throws IOException {
            int marker = input.readUnsignedByte();
            if (marker == 0) {
                return Optional.empty();
            }
            if (marker != 1) {
                throw new IllegalStateException("Escrow binary optional marker is invalid");
            }
            return Optional.of(supplier.get());
        }

        private String readString(int maximumCharacters) throws IOException {
            int length = input.readInt();
            int maximumBytes = Math.multiplyExact(maximumCharacters, 4);
            if (length <= 0 || length > maximumBytes || length > bytes.available()) {
                throw new IllegalStateException("Escrow binary string length is invalid");
            }
            byte[] utf8 = input.readNBytes(length);
            if (utf8.length != length) {
                throw new EOFException("Escrow binary string is truncated");
            }
            String value;
            try {
                value = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(utf8)).toString();
            } catch (CharacterCodingException exception) {
                throw new IllegalStateException("Escrow binary string is not valid UTF8", exception);
            }
            return EscrowCodecLimits.requireString("Escrow binary string", value, maximumCharacters);
        }

        private <E extends Enum<E>> E readEnum(Class<E> enumType, String field) throws IOException {
            String value = readString(128);
            try {
                return Enum.valueOf(enumType, value);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Escrow binary " + field + " is unknown", exception);
            }
        }

        private Set<EscrowParticipant> readParticipants() throws IOException {
            int count = readCount(EscrowCodecLimits.MAX_PARTICIPANTS, "participants");
            Set<EscrowParticipant> participants = new LinkedHashSet<>();
            for (int index = 0; index < count; index++) {
                EscrowParty party = readParty();
                int roleCount = readCount(EscrowParticipantRole.values().length, "participant roles");
                Set<EscrowParticipantRole> roles = new LinkedHashSet<>();
                for (int roleIndex = 0; roleIndex < roleCount; roleIndex++) {
                    if (!roles.add(readEnum(EscrowParticipantRole.class, "participant role"))) {
                        throw new IllegalStateException("Duplicate escrow binary participant role");
                    }
                }
                if (!participants.add(new EscrowParticipant(party, roles))) {
                    throw new IllegalStateException("Duplicate escrow binary participant");
                }
            }
            return participants;
        }

        private List<EscrowAssetLot> readAssetLots() throws IOException {
            int count = readCount(EscrowCodecLimits.MAX_ASSET_LOTS, "asset lots");
            List<EscrowAssetLot> lots = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                UUID lotId = readUuid();
                EscrowAssetLotType type = readEnum(EscrowAssetLotType.class, "asset lot type");
                EscrowProtectionLevel protection = readEnum(EscrowProtectionLevel.class, "protection level");
                EscrowParty source = readParty();
                EscrowParty destination = readParty();
                long quantity = input.readLong();
                Optional<MoneyAmount> money = readOptional(this::readMoney);
                int payloadLength = input.readInt();
                if (payloadLength < 0 || payloadLength > EscrowCodecLimits.MAX_PAYLOAD_BYTES
                        || payloadLength > bytes.available()) {
                    throw new IllegalStateException("Escrow binary asset payload length is invalid");
                }
                totalPayloadBytes = Math.addExact(totalPayloadBytes, payloadLength);
                if (totalPayloadBytes > EscrowCodecLimits.MAX_TOTAL_PAYLOAD_BYTES) {
                    throw new IllegalStateException("Escrow binary transaction payload exceeds its limit");
                }
                byte[] payload = input.readNBytes(payloadLength);
                if (payload.length != payloadLength) {
                    throw new EOFException("Escrow binary asset payload is truncated");
                }
                Map<String, String> attributes = readStringMap(
                        EscrowCodecLimits.MAX_ATTRIBUTES, "asset attributes");
                lots.add(new EscrowAssetLot(lotId, type, protection, source, destination,
                        quantity, money, payload, attributes));
            }
            return List.copyOf(lots);
        }

        private EscrowParty readParty() throws IOException {
            return new EscrowParty(
                    readEnum(EscrowPartyType.class, "party type"),
                    readString(EscrowParty.MAX_ID_LENGTH));
        }

        private MoneyAmount readMoney() throws IOException {
            return new MoneyAmount(readString(MoneyAmount.MAX_CURRENCY_ID_LENGTH), input.readLong());
        }

        private EscrowTimestamps readTimestamps() throws IOException {
            return new EscrowTimestamps(
                    readInstant(),
                    readInstant(),
                    readOptional(this::readInstant),
                    readOptional(this::readInstant));
        }

        private Instant readInstant() throws IOException {
            long seconds = input.readLong();
            int nanos = input.readInt();
            if (nanos < 0 || nanos > 999_999_999) {
                throw new IllegalStateException("Escrow binary timestamp nanoseconds are invalid");
            }
            try {
                return Instant.ofEpochSecond(seconds, nanos);
            } catch (DateTimeException exception) {
                throw new IllegalStateException("Escrow binary timestamp is invalid", exception);
            }
        }

        private EscrowError readError() throws IOException {
            return new EscrowError(
                    readString(EscrowError.MAX_CODE_LENGTH),
                    readString(EscrowError.MAX_MESSAGE_LENGTH),
                    readBoolean(),
                    readInstant(),
                    readStringMap(EscrowCodecLimits.MAX_ERROR_DETAILS, "error details"));
        }

        private EscrowRetryMetadata readRetry() throws IOException {
            int attemptCount = input.readInt();
            int maximumAttempts = input.readInt();
            Optional<Instant> nextAttempt = readOptional(this::readInstant);
            Optional<EscrowState> resumeState = readOptional(() -> readEnum(EscrowState.class, "resume state"));
            return new EscrowRetryMetadata(attemptCount, maximumAttempts, nextAttempt, resumeState);
        }

        private DimensionAwareShopReference readShopReference() throws IOException {
            return new DimensionAwareShopReference(
                    readString(DimensionAwareShopReference.MAX_SHOP_ID_LENGTH),
                    readString(DimensionAwareShopReference.MAX_DIMENSION_ID_LENGTH),
                    input.readInt(), input.readInt(), input.readInt());
        }

        private Map<String, String> readStringMap(int maximumSize, String field) throws IOException {
            int count = readCount(maximumSize, field);
            Map<String, String> values = new LinkedHashMap<>();
            for (int index = 0; index < count; index++) {
                String key = readString(EscrowCodecLimits.MAX_MAP_KEY_LENGTH);
                String value = readString(EscrowCodecLimits.MAX_MAP_VALUE_LENGTH);
                if (values.put(key, value) != null) {
                    throw new IllegalStateException("Duplicate escrow binary " + field + " key");
                }
            }
            return Map.copyOf(values);
        }

        private int readCount(int maximum, String field) throws IOException {
            int count = input.readInt();
            EscrowCodecLimits.requireCount("Escrow binary " + field, count, maximum);
            return count;
        }

        private boolean readBoolean() throws IOException {
            int value = input.readUnsignedByte();
            if (value != 0 && value != 1) {
                throw new IllegalStateException("Escrow binary boolean is invalid");
            }
            return value == 1;
        }
    }

    private static final class LimitedOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final int maximumBytes;
        private int written;

        private LimitedOutputStream(OutputStream delegate, int maximumBytes) {
            this.delegate = delegate;
            this.maximumBytes = maximumBytes;
        }

        @Override
        public void write(int value) throws IOException {
            requireCapacity(1);
            delegate.write(value);
            written++;
        }

        @Override
        public void write(byte[] values, int offset, int length) throws IOException {
            if (values == null || offset < 0 || length < 0 || offset > values.length - length) {
                throw new IndexOutOfBoundsException();
            }
            requireCapacity(length);
            delegate.write(values, offset, length);
            written += length;
        }

        private void requireCapacity(int additional) throws IOException {
            if (additional > maximumBytes - written) {
                throw new IOException("Escrow binary payload exceeds its limit");
            }
        }
    }

    @FunctionalInterface
    private interface IoConsumer<T> {
        void accept(T value) throws IOException;
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }
}
