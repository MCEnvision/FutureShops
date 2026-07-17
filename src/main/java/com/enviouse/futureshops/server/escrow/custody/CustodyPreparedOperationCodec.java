package com.enviouse.futureshops.server.escrow.custody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class CustodyPreparedOperationCodec {
    public static final int MAX_ENCODED_BYTES = CustodyMutationCodec.MAX_ENCODED_BYTES;

    private static final int MAGIC = 0x43505250;
    private static final int VERSION = 2;

    private CustodyPreparedOperationCodec() {
    }

    public static byte[] encode(CustodyPreparedOperation intent) {
        Objects.requireNonNull(intent, "intent");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeShort(VERSION);
            CustodyMutationCodec.writeUuid(output, intent.intentId());
            CustodyMutationCodec.writeString(output, intent.operation().name());
            CustodyMutationCodec.writeString(output, intent.requestKey());
            CustodyMutationCodec.writeLot(output, intent.lotSnapshot());
            CustodyMutationCodec.writeString(output, intent.adapterId());
            CustodyMutationCodec.writeString(output, intent.adapterCapability().name());
            CustodyMutationCodec.writeString(output, intent.simulationToken());
            CustodyMutationCodec.writeEvidence(output, intent.plannedEvidence());
            CustodyMutationCodec.writeInstant(output, intent.preparedAt());
            CustodyMutationCodec.writeString(output, intent.status().name());
            CustodyMutationCodec.writeStrictBoolean(output, intent.resolvedReceiptId().isPresent());
            if (intent.resolvedReceiptId().isPresent()) {
                CustodyMutationCodec.writeUuid(output, intent.resolvedReceiptId().orElseThrow());
            }
            CustodyMutationCodec.writeStrictBoolean(output, intent.resolvedAt().isPresent());
            if (intent.resolvedAt().isPresent()) {
                CustodyMutationCodec.writeInstant(output, intent.resolvedAt().orElseThrow());
            }
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException("Prepared custody operation exceeds journal bounds");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode prepared custody operation", exception);
        }
    }

    public static CustodyPreparedOperation decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("Invalid prepared custody operation size");
        }
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Prepared custody operation magic does not match");
            }
            int version = input.readUnsignedShort();
            if (version != VERSION) {
                throw new IllegalArgumentException("Unsupported prepared custody operation version");
            }
            UUID intentId = CustodyMutationCodec.readUuid(input);
            CustodyOperation operation = CustodyMutationCodec.readEnum(input,
                    CustodyOperation.class, "prepared custody operation");
            String requestKey = CustodyMutationCodec.readString(input,
                    CustodyLot.MAX_REQUEST_KEY_LENGTH * 4);
            CustodyLot lot = CustodyMutationCodec.readLot(input);
            String adapterId = CustodyMutationCodec.readString(input,
                    CustodyEndpointEvidence.MAX_TEXT_LENGTH * 4);
            CustodyAdapterCapability capability = CustodyMutationCodec.readEnum(input,
                    CustodyAdapterCapability.class, "prepared custody adapter capability");
            String simulationToken = CustodyMutationCodec.readString(input,
                    CustodyPreparedOperation.MAX_SIMULATION_TOKEN_LENGTH * 4);
            CustodyTransferEvidence plannedEvidence = CustodyMutationCodec.readEvidence(input);
            Instant preparedAt = CustodyMutationCodec.readInstant(input);
            CustodyPreparedStatus status = CustodyMutationCodec.readEnum(input,
                    CustodyPreparedStatus.class, "prepared custody status");
            Optional<UUID> receiptId = CustodyMutationCodec.readStrictBoolean(input)
                    ? Optional.of(CustodyMutationCodec.readUuid(input))
                    : Optional.empty();
            Optional<Instant> resolvedAt = CustodyMutationCodec.readStrictBoolean(input)
                    ? Optional.of(CustodyMutationCodec.readInstant(input))
                    : Optional.empty();
            if (input.read() != -1) {
                throw new IllegalArgumentException("Prepared custody operation has trailing bytes");
            }
            return new CustodyPreparedOperation(intentId, operation, requestKey, lot,
                    adapterId, capability, simulationToken, plannedEvidence, preparedAt,
                    status, receiptId, resolvedAt);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to decode prepared custody operation", exception);
        }
    }
}
