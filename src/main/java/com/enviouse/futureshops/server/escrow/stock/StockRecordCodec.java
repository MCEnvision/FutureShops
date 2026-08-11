package com.enviouse.futureshops.server.escrow.stock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class StockRecordCodec {
    private static final int VERSION = 2;
    private static final int DEFINITION_MAGIC = 0x53544446;
    private static final int LISTING_MAGIC = 0x53544C53;
    private static final int RESERVATION_MAGIC = 0x53545253;
    private static final int RECEIPT_MAGIC = 0x53545243;

    private StockRecordCodec() {
    }

    public static byte[] encodeDefinition(StockDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return encode(DEFINITION_MAGIC, output -> writeDefinition(output, definition));
    }

    public static StockDefinition decodeDefinition(byte[] encoded) {
        return decode(encoded, DEFINITION_MAGIC, "Stock definition",
                StockRecordCodec::readDefinition);
    }

    public static byte[] encodeListing(CatalogStockState listing) {
        Objects.requireNonNull(listing, "listing");
        return encode(LISTING_MAGIC, output -> writeListing(output, listing));
    }

    public static CatalogStockState decodeListing(byte[] encoded) {
        return decode(encoded, LISTING_MAGIC, "Stock listing", StockRecordCodec::readListing);
    }

    public static byte[] encodeReservation(StockReservation reservation) {
        Objects.requireNonNull(reservation, "reservation");
        return encode(RESERVATION_MAGIC, output -> writeReservation(output, reservation));
    }

    public static StockReservation decodeReservation(byte[] encoded) {
        return decode(encoded, RESERVATION_MAGIC, "Stock reservation",
                StockRecordCodec::readReservation);
    }

    public static byte[] encodeReceipt(StockMutationReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt");
        return encode(RECEIPT_MAGIC, output -> writeReceipt(output, receipt));
    }

    public static StockMutationReceipt decodeReceipt(byte[] encoded) {
        return decode(encoded, RECEIPT_MAGIC, "Stock receipt", StockRecordCodec::readReceipt);
    }

    static void writeDefinition(DataOutputStream output, StockDefinition definition)
            throws IOException {
        writeKey(output, definition.key());
        writePolicy(output, definition.policy());
        StockBinaryIo.writeString(output, definition.configFingerprint());
    }

    static StockDefinition readDefinition(DataInputStream input) throws IOException {
        return new StockDefinition(readKey(input), readPolicy(input),
                StockBinaryIo.readString(input, StockLimits.FINGERPRINT_LENGTH));
    }

    static void writeListing(DataOutputStream output, CatalogStockState listing)
            throws IOException {
        writeKey(output, listing.key());
        writePolicy(output, listing.policy());
        StockBinaryIo.writeEnum(output, listing.status());
        output.writeLong(listing.availableQuantity());
        StockBinaryIo.writeString(output, listing.configFingerprint());
        output.writeLong(listing.revision());
        StockBinaryIo.writeInstant(output, listing.updatedAt());
    }

    static CatalogStockState readListing(DataInputStream input) throws IOException {
        return new CatalogStockState(readKey(input), readPolicy(input),
                StockBinaryIo.readEnum(input, CatalogStockStatus.class, "stock status"),
                input.readLong(),
                StockBinaryIo.readString(input, StockLimits.FINGERPRINT_LENGTH),
                input.readLong(), StockBinaryIo.readInstant(input));
    }

    static void writeReservation(DataOutputStream output, StockReservation reservation)
            throws IOException {
        StockBinaryIo.writeUuid(output, reservation.reservationId().value());
        StockBinaryIo.writeUuid(output, reservation.transactionId());
        writeKey(output, reservation.stockKey());
        output.writeInt(reservation.direction().wireId());
        output.writeLong(reservation.quantity());
        StockBinaryIo.writeBoolean(output, reservation.inventoryBacked());
        StockBinaryIo.writeEnum(output, reservation.state());
        output.writeLong(reservation.revision());
        StockBinaryIo.writeInstant(output, reservation.createdAt());
        StockBinaryIo.writeInstant(output, reservation.updatedAt());
    }

    static StockReservation readReservation(DataInputStream input) throws IOException {
        StockReservationId reservationId = new StockReservationId(
                StockBinaryIo.readUuid(input));
        UUID transactionId = StockBinaryIo.readUuid(input);
        StockKey key = readKey(input);
        StockReservationDirection direction = StockReservationDirection
                .fromWireId(input.readInt());
        return new StockReservation(reservationId,
                transactionId, key, direction, input.readLong(),
                StockBinaryIo.readBoolean(input),
                StockBinaryIo.readEnum(input, StockReservationState.class,
                        "stock reservation state"),
                input.readLong(), StockBinaryIo.readInstant(input),
                StockBinaryIo.readInstant(input));
    }

    static void writeReceipt(DataOutputStream output, StockMutationReceipt receipt)
            throws IOException {
        StockBinaryIo.writeUuid(output, receipt.requestId());
        output.writeInt(receipt.operation().wireId());
        StockBinaryIo.writeString(output, receipt.requestFingerprint());
        output.writeLong(receipt.storeRevision());
        StockBinaryIo.writeBoolean(output, receipt.transactionId().isPresent());
        if (receipt.transactionId().isPresent()) {
            StockBinaryIo.writeUuid(output, receipt.transactionId().orElseThrow());
        }
        StockBinaryIo.writeBoolean(output, receipt.stockKey().isPresent());
        if (receipt.stockKey().isPresent()) {
            writeKey(output, receipt.stockKey().orElseThrow());
        }
        StockBinaryIo.writeBoolean(output, receipt.reservationId().isPresent());
        if (receipt.reservationId().isPresent()) {
            StockBinaryIo.writeUuid(output, receipt.reservationId().orElseThrow().value());
        }
        output.writeInt(receipt.reservationIds().size());
        for (StockReservationId reservationId : receipt.reservationIds()) {
            StockBinaryIo.writeUuid(output, reservationId.value());
        }
        StockBinaryIo.writeEnum(output, receipt.outcome());
        output.writeLong(receipt.listingRevision());
        output.writeLong(receipt.reservationRevision());
        StockBinaryIo.writeInstant(output, receipt.appliedAt());
    }

    static StockMutationReceipt readReceipt(DataInputStream input) throws IOException {
        UUID requestId = StockBinaryIo.readUuid(input);
        StockMutationType operation = StockMutationType.fromWireId(
                input.readInt());
        String fingerprint = StockBinaryIo.readString(input, StockLimits.FINGERPRINT_LENGTH);
        long storeRevision = input.readLong();
        Optional<UUID> transactionId = StockBinaryIo.readBoolean(input)
                ? Optional.of(StockBinaryIo.readUuid(input)) : Optional.empty();
        Optional<StockKey> key = StockBinaryIo.readBoolean(input)
                ? Optional.of(readKey(input)) : Optional.empty();
        Optional<StockReservationId> reservationId = StockBinaryIo.readBoolean(input)
                ? Optional.of(new StockReservationId(StockBinaryIo.readUuid(input)))
                : Optional.empty();
        int reservationCount = input.readInt();
        if (reservationCount < 0
                || reservationCount > StockLimits.MAX_BATCH_LINES) {
            throw new IllegalArgumentException(
                    "Invalid stock receipt reservation count");
        }
        java.util.List<StockReservationId> reservationIds =
                new java.util.ArrayList<>(reservationCount);
        for (int index = 0; index < reservationCount; index++) {
            reservationIds.add(new StockReservationId(
                    StockBinaryIo.readUuid(input)));
        }
        StockMutationOutcome outcome = StockBinaryIo.readEnum(input,
                StockMutationOutcome.class, "stock mutation outcome");
        return new StockMutationReceipt(requestId, operation, fingerprint, storeRevision,
                transactionId, key, reservationId, reservationIds, outcome,
                input.readLong(), input.readLong(), StockBinaryIo.readInstant(input));
    }

    static void writeKey(DataOutputStream output, StockKey key) throws IOException {
        StockBinaryIo.writeString(output, key.shopId());
        StockBinaryIo.writeString(output, key.listingId());
    }

    static StockKey readKey(DataInputStream input) throws IOException {
        return new StockKey(
                StockBinaryIo.readString(input, StockLimits.MAX_IDENTIFIER_LENGTH),
                StockBinaryIo.readString(input, StockLimits.MAX_IDENTIFIER_LENGTH));
    }

    static void writePolicy(DataOutputStream output, StockPolicy policy) throws IOException {
        StockBinaryIo.writeBoolean(output, policy.unlimited());
        output.writeLong(policy.configuredQuantity());
    }

    static StockPolicy readPolicy(DataInputStream input) throws IOException {
        return new StockPolicy(StockBinaryIo.readBoolean(input), input.readLong());
    }

    private static byte[] encode(int magic, Encoder encoder) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(magic);
            output.writeShort(VERSION);
            encoder.encode(output);
            output.flush();
            byte[] result = bytes.toByteArray();
            requireSize(result);
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode stock record", exception);
        }
    }

    private static <T> T decode(byte[] encoded, int magic, String name, Decoder<T> decoder) {
        Objects.requireNonNull(encoded, "encoded");
        requireSize(encoded);
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
            if (input.readInt() != magic) {
                throw new IllegalArgumentException(name + " magic does not match");
            }
            if (input.readUnsignedShort() != VERSION) {
                throw new IllegalArgumentException("Unsupported " + name + " version");
            }
            T result = decoder.decode(input);
            StockBinaryIo.requireFinished(input, name);
            return result;
        } catch (IOException | ArithmeticException exception) {
            throw new IllegalArgumentException("Unable to decode " + name, exception);
        }
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length == 0 || encoded.length > StockLimits.MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("Invalid stock record size");
        }
    }

    @FunctionalInterface
    private interface Encoder {
        void encode(DataOutputStream output) throws IOException;
    }

    @FunctionalInterface
    private interface Decoder<T> {
        T decode(DataInputStream input) throws IOException;
    }
}
