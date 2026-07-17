package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashInventoryState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class ForeignCashDepositEvidence {
    static final int MAX_ENCODED_BYTES = 20_000_000;

    private static final int MAGIC = 0x46434445;
    private static final int SCHEMA = 1;
    private static final int HASH_BYTES = 32;

    private final UUID playerId;
    private final UUID transactionId;
    private final Phase phase;
    private final byte[] eventBytes;
    private final ProtectedCashInventoryState inventoryState;
    private final Instant recordedAt;
    private final byte[] encoded;

    private ForeignCashDepositEvidence(
            UUID playerId,
            UUID transactionId,
            Phase phase,
            byte[] eventBytes,
            ProtectedCashInventoryState inventoryState,
            Instant recordedAt
    ) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.transactionId = Objects.requireNonNull(
                transactionId, "transactionId");
        this.phase = Objects.requireNonNull(phase, "phase");
        this.eventBytes = Objects.requireNonNull(
                eventBytes, "eventBytes").clone();
        this.inventoryState = Objects.requireNonNull(
                inventoryState, "inventoryState");
        this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
        requireEvent();
        this.encoded = encodeCanonical();
    }

    static ForeignCashDepositEvidence intent(
            ForeignCashDepositReservation reservation,
            ProtectedCashInventoryState beforeInventory
    ) {
        return new ForeignCashDepositEvidence(reservation.playerId(),
                reservation.transactionId(), Phase.INTENT,
                ForeignCashDepositCodec.encodeReservation(reservation),
                beforeInventory,
                reservation.heldTransaction().timestamps().updatedAt());
    }

    static ForeignCashDepositEvidence settlement(
            ForeignCashDepositSettlement settlement,
            ProtectedCashInventoryState afterInventory
    ) {
        return new ForeignCashDepositEvidence(
                settlement.reservation().playerId(),
                settlement.transactionId(), Phase.SETTLEMENT,
                ForeignCashDepositCodec.encodeSettlement(settlement),
                afterInventory,
                settlement.completedTransaction().timestamps().updatedAt());
    }

    static ForeignCashDepositEvidence cancellation(
            ForeignCashDepositCancellation cancellation,
            ProtectedCashInventoryState unchangedInventory
    ) {
        return new ForeignCashDepositEvidence(
                cancellation.reservation().playerId(),
                cancellation.transactionId(), Phase.CANCELLATION,
                ForeignCashDepositCodec.encodeCancellation(cancellation),
                unchangedInventory,
                cancellation.refundedTransaction().timestamps().updatedAt());
    }

    static ForeignCashDepositEvidence decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0
                || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Foreign cash evidence size is invalid");
        }
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(encoded))) {
            if (input.readInt() != MAGIC || input.readInt() != SCHEMA) {
                throw new IllegalArgumentException(
                        "Foreign cash evidence header is invalid");
            }
            UUID playerId = readUuid(input);
            UUID transactionId = readUuid(input);
            Phase phase = readEnum(input.readInt(), Phase.values());
            byte[] event = readBytes(input, maximumEventBytes(phase));
            byte[] inventory = readBytes(input,
                    ProtectedCashInventoryState.MAX_ENCODED_BYTES);
            Instant recordedAt = Instant.ofEpochSecond(input.readLong(),
                    input.readInt());
            byte[] digest = input.readNBytes(HASH_BYTES);
            if (digest.length != HASH_BYTES || input.read() != -1) {
                throw new IllegalArgumentException(
                        "Foreign cash evidence is truncated or has trailing data");
            }
            byte[] payload = Arrays.copyOf(encoded,
                    encoded.length - HASH_BYTES);
            if (!java.security.MessageDigest.isEqual(digest,
                    ForeignCashDepositReservation.sha256(payload))) {
                throw new IllegalArgumentException(
                        "Foreign cash evidence digest is invalid");
            }
            ForeignCashDepositEvidence result =
                    new ForeignCashDepositEvidence(playerId,
                            transactionId, phase, event,
                            ProtectedCashInventoryState.decode(inventory),
                            recordedAt);
            if (!Arrays.equals(encoded, result.encoded)) {
                throw new IllegalArgumentException(
                        "Foreign cash evidence is not canonical");
            }
            return result;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Foreign cash evidence is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException(
                    "Foreign cash evidence is invalid", exception);
        }
    }

    UUID playerId() {
        return playerId;
    }

    UUID transactionId() {
        return transactionId;
    }

    Phase phase() {
        return phase;
    }

    ProtectedCashInventoryState inventoryState() {
        return inventoryState;
    }

    byte[] encode() {
        return encoded.clone();
    }

    ForeignCashDepositReservation reservation() {
        return switch (phase) {
            case INTENT -> ForeignCashDepositCodec.decodeReservation(
                    eventBytes);
            case SETTLEMENT -> settlement().orElseThrow().reservation();
            case CANCELLATION -> cancellation().orElseThrow().reservation();
        };
    }

    Optional<ForeignCashDepositSettlement> settlement() {
        return phase == Phase.SETTLEMENT
                ? Optional.of(ForeignCashDepositCodec.decodeSettlement(
                eventBytes)) : Optional.empty();
    }

    Optional<ForeignCashDepositCancellation> cancellation() {
        return phase == Phase.CANCELLATION
                ? Optional.of(ForeignCashDepositCodec.decodeCancellation(
                eventBytes)) : Optional.empty();
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ForeignCashDepositEvidence other
                && Arrays.equals(encoded, other.encoded);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(encoded);
    }

    private void requireEvent() {
        ForeignCashDepositReservation reservation = reservation();
        if (!reservation.playerId().equals(playerId)
                || !reservation.transactionId().equals(transactionId)) {
            throw new IllegalArgumentException(
                    "Foreign cash evidence identity is invalid");
        }
        byte[] expectedHash = switch (phase) {
            case INTENT, CANCELLATION ->
                    reservation.inventoryBeforeHash();
            case SETTLEMENT -> settlement().orElseThrow()
                    .inventoryMutation().afterInventoryHash();
        };
        if (!java.security.MessageDigest.isEqual(expectedHash,
                inventoryState.hash())
                || recordedAt.isBefore(reservation.heldTransaction()
                .timestamps().updatedAt())) {
            throw new IllegalArgumentException(
                    "Foreign cash evidence inventory or time is invalid");
        }
    }

    private byte[] encodeCanonical() {
        try {
            ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(payloadBytes);
            output.writeInt(MAGIC);
            output.writeInt(SCHEMA);
            writeUuid(output, playerId);
            writeUuid(output, transactionId);
            output.writeInt(phase.ordinal());
            writeBytes(output, eventBytes, maximumEventBytes(phase));
            writeBytes(output, inventoryState.encode(),
                    ProtectedCashInventoryState.MAX_ENCODED_BYTES);
            output.writeLong(recordedAt.getEpochSecond());
            output.writeInt(recordedAt.getNano());
            output.flush();
            byte[] payload = payloadBytes.toByteArray();
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            result.write(payload);
            result.write(ForeignCashDepositReservation.sha256(payload));
            byte[] value = result.toByteArray();
            if (value.length > MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException(
                        "Foreign cash evidence exceeds its bound");
            }
            return value;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode foreign cash evidence", exception);
        }
    }

    private static int maximumEventBytes(Phase phase) {
        return phase == Phase.INTENT
                ? ForeignCashDepositCodec.MAX_RESERVATION_BYTES
                : ForeignCashDepositCodec.MAX_TERMINAL_BYTES;
    }

    private static void writeUuid(DataOutputStream output, UUID value)
            throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static void writeBytes(DataOutputStream output, byte[] value,
                                   int maximum) throws IOException {
        if (value.length == 0 || value.length > maximum) {
            throw new IllegalArgumentException(
                    "Foreign cash evidence field is invalid");
        }
        output.writeInt(value.length);
        output.write(value);
    }

    private static byte[] readBytes(DataInputStream input, int maximum)
            throws IOException {
        int count = input.readInt();
        if (count <= 0 || count > maximum) {
            throw new IllegalArgumentException(
                    "Foreign cash evidence field length is invalid");
        }
        byte[] value = input.readNBytes(count);
        if (value.length != count) {
            throw new EOFException();
        }
        return value;
    }

    private static <T> T readEnum(int ordinal, T[] values) {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException(
                    "Foreign cash evidence phase is invalid");
        }
        return values[ordinal];
    }

    enum Phase {
        INTENT,
        SETTLEMENT,
        CANCELLATION
    }
}
