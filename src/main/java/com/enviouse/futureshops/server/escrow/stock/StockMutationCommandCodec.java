package com.enviouse.futureshops.server.escrow.stock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class StockMutationCommandCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES = 16_000_000;

    private static final int MAGIC = 0x53544D55;

    private StockMutationCommandCodec() {
    }

    public static byte[] encode(StockMutationCommand command) {
        Objects.requireNonNull(command, "command");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeShort(CURRENT_SCHEMA);
            output.writeInt(command.operation().wireId());
            StockBinaryIo.writeUuid(output, command.requestId());
            StockBinaryIo.writeInstant(output, command.appliedAt());
            writeBody(output, command);
            output.flush();
            byte[] encoded = bytes.toByteArray();
            requireSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode stock mutation command", exception);
        }
    }

    public static StockMutationCommand decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        requireSize(encoded);
        ByteArrayInputStream bytes = new ByteArrayInputStream(encoded);
        try (DataInputStream input = new DataInputStream(bytes)) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Stock mutation command magic is invalid");
            }
            int schema = input.readUnsignedShort();
            if (schema > CURRENT_SCHEMA) {
                throw new IllegalStateException(
                        "Stock mutation command schema is newer than this build");
            }
            if (schema != CURRENT_SCHEMA) {
                throw new IllegalArgumentException(
                        "Stock mutation command schema is unsupported");
            }
            StockMutationType operation = StockMutationType.fromWireId(
                    input.readInt());
            UUID requestId = StockBinaryIo.readUuid(input);
            java.time.Instant appliedAt = StockBinaryIo.readInstant(input);
            StockMutationCommand command = readBody(input, operation,
                    requestId, appliedAt);
            StockBinaryIo.requireFinished(input, "Stock mutation command");
            return command;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Stock mutation command is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Stock mutation command is invalid", exception);
        }
    }

    private static void writeBody(DataOutputStream output,
                                  StockMutationCommand command)
            throws IOException {
        if (command instanceof StockMutationCommand.Seed value) {
            StockRecordCodec.writeDefinition(output, value.definition());
        } else if (command instanceof StockMutationCommand.Reserve value) {
            StockBinaryIo.writeUuid(output, value.transactionId());
            StockRecordCodec.writeKey(output, value.stockKey());
            output.writeLong(value.quantity());
            output.writeLong(value.expectedListingRevision());
        } else if (command instanceof StockMutationCommand.Resolve value) {
            StockBinaryIo.writeUuid(output, value.transactionId());
            StockBinaryIo.writeUuid(output, value.reservationId().value());
            output.writeLong(value.expectedReservationRevision());
        } else if (command instanceof StockMutationCommand.DefinitionChange value) {
            StockRecordCodec.writeDefinition(output, value.definition());
            output.writeLong(value.expectedListingRevision());
        } else if (command instanceof StockMutationCommand.Reconcile value) {
            output.writeInt(value.definitions().size());
            for (StockDefinition definition : value.definitions()) {
                StockRecordCodec.writeDefinition(output, definition);
            }
            StockBinaryIo.writeString(output, value.catalogFingerprint());
        } else if (command instanceof StockMutationCommand.ReserveBatch value) {
            StockBinaryIo.writeUuid(output, value.transactionId());
            output.writeInt(value.reservations().size());
            for (StockReservationRequest reservation : value.reservations()) {
                StockRecordCodec.writeKey(output, reservation.stockKey());
                output.writeInt(reservation.direction().wireId());
                output.writeLong(reservation.quantity());
                output.writeLong(reservation.expectedListingRevision());
            }
        } else if (command instanceof StockMutationCommand.ResolveBatch value) {
            StockBinaryIo.writeUuid(output, value.transactionId());
            output.writeInt(value.reservations().size());
            for (StockReservationResolution reservation : value.reservations()) {
                StockBinaryIo.writeUuid(output,
                        reservation.reservationId().value());
                output.writeLong(reservation.expectedReservationRevision());
            }
        } else {
            throw new IllegalArgumentException(
                    "Unknown stock mutation command");
        }
    }

    private static StockMutationCommand readBody(
            DataInputStream input,
            StockMutationType operation,
            UUID requestId,
            java.time.Instant appliedAt
    ) throws IOException {
        return switch (operation) {
            case SEED -> new StockMutationCommand.Seed(requestId,
                    StockRecordCodec.readDefinition(input), appliedAt);
            case RESERVE -> new StockMutationCommand.Reserve(requestId,
                    StockBinaryIo.readUuid(input),
                    StockRecordCodec.readKey(input), input.readLong(),
                    input.readLong(), appliedAt);
            case COMMIT, RELEASE -> new StockMutationCommand.Resolve(
                    requestId, operation, StockBinaryIo.readUuid(input),
                    new StockReservationId(StockBinaryIo.readUuid(input)),
                    input.readLong(), appliedAt);
            case REFRESH, ADMIN_RESET ->
                    new StockMutationCommand.DefinitionChange(requestId,
                            operation, StockRecordCodec.readDefinition(input),
                            input.readLong(), appliedAt);
            case RELOAD_RECONCILE -> new StockMutationCommand.Reconcile(
                    requestId, readDefinitions(input),
                    StockBinaryIo.readString(input,
                            StockLimits.FINGERPRINT_LENGTH), appliedAt);
            case RESERVE_BATCH -> new StockMutationCommand.ReserveBatch(
                    requestId, StockBinaryIo.readUuid(input),
                    readReservationRequests(input), appliedAt);
            case COMMIT_BATCH, RELEASE_BATCH ->
                    new StockMutationCommand.ResolveBatch(requestId, operation,
                            StockBinaryIo.readUuid(input),
                            readReservationResolutions(input), appliedAt);
        };
    }

    private static List<StockDefinition> readDefinitions(
            DataInputStream input
    ) throws IOException {
        int count = readCount(input, true);
        List<StockDefinition> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(StockRecordCodec.readDefinition(input));
        }
        return values;
    }

    private static List<StockReservationRequest> readReservationRequests(
            DataInputStream input
    ) throws IOException {
        int count = readCount(input, false);
        List<StockReservationRequest> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(new StockReservationRequest(
                    StockRecordCodec.readKey(input),
                    StockReservationDirection.fromWireId(input.readInt()),
                    input.readLong(), input.readLong()));
        }
        return values;
    }

    private static List<StockReservationResolution>
    readReservationResolutions(DataInputStream input) throws IOException {
        int count = readCount(input, false);
        List<StockReservationResolution> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(new StockReservationResolution(
                    new StockReservationId(StockBinaryIo.readUuid(input)),
                    input.readLong()));
        }
        return values;
    }

    private static int readCount(DataInputStream input, boolean allowEmpty)
            throws IOException {
        int count = input.readInt();
        if (count < (allowEmpty ? 0 : 1)
                || count > StockLimits.MAX_BATCH_LINES) {
            throw new IllegalArgumentException(
                    "Stock mutation command line count is invalid");
        }
        return count;
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Stock mutation command size is invalid");
        }
    }
}
