package com.enviouse.futureshops.server.escrow.redemption;

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

public final class ProtectedCashRedemptionEvidence {
    public static final int MAX_ENCODED_BYTES = 25_165_824;

    private static final int MAGIC = 0x46534345;
    private static final int SCHEMA = 1;

    private final UUID playerId;
    private final UUID transactionId;
    private final Phase phase;
    private final byte[] eventBytes;
    private final ProtectedCashInventoryState inventoryState;
    private final Instant recordedAt;
    private final byte[] encoded;

    private ProtectedCashRedemptionEvidence(
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

    public static ProtectedCashRedemptionEvidence intent(
            ProtectedCashRedemptionReservation reservation,
            ProtectedCashInventoryState beforeInventory
    ) {
        return new ProtectedCashRedemptionEvidence(reservation.playerId(),
                reservation.transactionId(), Phase.INTENT,
                ProtectedCashRedemptionReservationCodec.encode(reservation),
                beforeInventory,
                reservation.heldTransaction().timestamps().updatedAt());
    }

    public static ProtectedCashRedemptionEvidence settlement(
            ProtectedCashRedemptionSettlement settlement,
            ProtectedCashInventoryState afterInventory
    ) {
        return new ProtectedCashRedemptionEvidence(
                settlement.reservation().playerId(),
                settlement.transactionId(), Phase.SETTLEMENT,
                ProtectedCashRedemptionSettlementCodec.encode(settlement),
                afterInventory,
                settlement.completedTransaction().timestamps().updatedAt());
    }

    public static ProtectedCashRedemptionEvidence cancellation(
            ProtectedCashRedemptionCancellation cancellation,
            ProtectedCashInventoryState unchangedInventory
    ) {
        return new ProtectedCashRedemptionEvidence(
                cancellation.reservation().playerId(),
                cancellation.transactionId(), Phase.CANCELLATION,
                ProtectedCashRedemptionCancellationCodec.encode(cancellation),
                unchangedInventory,
                cancellation.refundedTransaction().timestamps().updatedAt());
    }

    public static ProtectedCashRedemptionEvidence decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0
                || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Protected cash evidence size is invalid");
        }
        ByteArrayInputStream bytes = new ByteArrayInputStream(encoded);
        try (DataInputStream input = new DataInputStream(bytes)) {
            if (input.readInt() != MAGIC || input.readInt() != SCHEMA) {
                throw new IllegalArgumentException(
                        "Protected cash evidence header is invalid");
            }
            UUID playerId = ProtectedCashRedemptionSupport.readUuid(input);
            UUID transactionId = ProtectedCashRedemptionSupport.readUuid(input);
            Phase phase = ProtectedCashRedemptionSupport.readEnum(
                    input.readInt(), Phase.values(),
                    "Protected cash evidence phase");
            byte[] event = ProtectedCashRedemptionSupport.readBytes(input,
                    bytes, maximumEventBytes(phase),
                    "Protected cash evidence event");
            byte[] inventory = ProtectedCashRedemptionSupport.readBytes(input,
                    bytes, ProtectedCashInventoryState.MAX_ENCODED_BYTES,
                    "Protected cash evidence inventory");
            Instant recordedAt = ProtectedCashRedemptionSupport.readInstant(
                    input);
            byte[] digest = input.readNBytes(
                    ProtectedCashRedemptionSupport.HASH_BYTES);
            if (digest.length != ProtectedCashRedemptionSupport.HASH_BYTES
                    || bytes.available() != 0) {
                throw new IllegalArgumentException(
                        "Protected cash evidence is truncated or has trailing data");
            }
            int payloadLength = encoded.length
                    - ProtectedCashRedemptionSupport.HASH_BYTES;
            byte[] payload = Arrays.copyOf(encoded, payloadLength);
            if (!ProtectedCashRedemptionSupport.equal(digest,
                    ProtectedCashRedemptionSupport.sha256(payload))) {
                throw new IllegalArgumentException(
                        "Protected cash evidence digest is invalid");
            }
            ProtectedCashRedemptionEvidence result =
                    new ProtectedCashRedemptionEvidence(playerId,
                            transactionId, phase, event,
                            ProtectedCashInventoryState.decode(inventory),
                            recordedAt);
            if (!Arrays.equals(encoded, result.encoded)) {
                throw new IllegalArgumentException(
                        "Protected cash evidence is not canonical");
            }
            return result;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Protected cash evidence is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Protected cash evidence is invalid", exception);
        }
    }

    public UUID playerId() {
        return playerId;
    }

    public UUID transactionId() {
        return transactionId;
    }

    public Phase phase() {
        return phase;
    }

    public ProtectedCashInventoryState inventoryState() {
        return inventoryState;
    }

    public Instant recordedAt() {
        return recordedAt;
    }

    public byte[] encode() {
        return encoded.clone();
    }

    public ProtectedCashRedemptionReservation reservation() {
        return switch (phase) {
            case INTENT -> ProtectedCashRedemptionReservationCodec.decode(
                    eventBytes);
            case SETTLEMENT -> settlement().orElseThrow().reservation();
            case CANCELLATION -> cancellation().orElseThrow().reservation();
        };
    }

    public Optional<ProtectedCashRedemptionSettlement> settlement() {
        return phase == Phase.SETTLEMENT
                ? Optional.of(ProtectedCashRedemptionSettlementCodec.decode(
                eventBytes)) : Optional.empty();
    }

    public Optional<ProtectedCashRedemptionCancellation> cancellation() {
        return phase == Phase.CANCELLATION
                ? Optional.of(ProtectedCashRedemptionCancellationCodec.decode(
                eventBytes)) : Optional.empty();
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ProtectedCashRedemptionEvidence other
                && Arrays.equals(encoded, other.encoded);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(encoded);
    }

    private void requireEvent() {
        ProtectedCashRedemptionReservation reservation = reservation();
        if (!reservation.playerId().equals(playerId)
                || !reservation.transactionId().equals(transactionId)) {
            throw new IllegalArgumentException(
                    "Protected cash evidence identity is invalid");
        }
        byte[] expectedHash = switch (phase) {
            case INTENT, CANCELLATION ->
                    reservation.inventoryBeforeHash();
            case SETTLEMENT -> settlement().orElseThrow()
                    .inventoryMutation().afterInventoryHash();
        };
        if (!ProtectedCashRedemptionSupport.equal(expectedHash,
                inventoryState.hash())) {
            throw new IllegalArgumentException(
                    "Protected cash evidence inventory is invalid");
        }
        if (recordedAt.isBefore(
                reservation.heldTransaction().timestamps().updatedAt())) {
            throw new IllegalArgumentException(
                    "Protected cash evidence time is invalid");
        }
    }

    private byte[] encodeCanonical() {
        try {
            ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(payloadBytes);
            output.writeInt(MAGIC);
            output.writeInt(SCHEMA);
            ProtectedCashRedemptionSupport.writeUuid(output, playerId);
            ProtectedCashRedemptionSupport.writeUuid(output, transactionId);
            output.writeInt(phase.ordinal());
            ProtectedCashRedemptionSupport.writeBytes(output, eventBytes,
                    maximumEventBytes(phase),
                    "Protected cash evidence event");
            ProtectedCashRedemptionSupport.writeBytes(output,
                    inventoryState.encode(),
                    ProtectedCashInventoryState.MAX_ENCODED_BYTES,
                    "Protected cash evidence inventory");
            ProtectedCashRedemptionSupport.writeInstant(output, recordedAt);
            output.flush();
            byte[] payload = payloadBytes.toByteArray();
            ByteArrayOutputStream resultBytes = new ByteArrayOutputStream();
            resultBytes.write(payload);
            resultBytes.write(ProtectedCashRedemptionSupport.sha256(payload));
            byte[] result = resultBytes.toByteArray();
            if (result.length > MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException(
                        "Protected cash evidence exceeds its limit");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode protected cash evidence", exception);
        }
    }

    private static int maximumEventBytes(Phase phase) {
        return switch (phase) {
            case INTENT ->
                    ProtectedCashRedemptionReservationCodec.MAX_ENCODED_BYTES;
            case SETTLEMENT ->
                    ProtectedCashRedemptionSettlementCodec.MAX_ENCODED_BYTES;
            case CANCELLATION ->
                    ProtectedCashRedemptionCancellationCodec.MAX_ENCODED_BYTES;
        };
    }

    public enum Phase {
        INTENT,
        SETTLEMENT,
        CANCELLATION
    }
}
