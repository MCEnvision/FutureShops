package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAction;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeRecord;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class AdministrativeAuditJournalCodec {
    private static final int VERSION = 1;
    private static final int MAX_ACTOR_BYTES = 640;
    private static final int MAX_REASON_BYTES = 4096;
    private static final int MAX_OUTCOME_BYTES = 4096;

    private AdministrativeAuditJournalCodec() {
    }

    public static byte[] encode(EscrowAdministrativeRecord record) {
        Objects.requireNonNull(record, "record");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(VERSION);
            BinaryCodecSupport.writeUuid(output, record.requestId());
            BinaryCodecSupport.writeString(output, record.actor(), MAX_ACTOR_BYTES);
            BinaryCodecSupport.writeString(output, record.action().name(), 128);
            output.writeBoolean(record.transactionId().isPresent());
            if (record.transactionId().isPresent()) {
                BinaryCodecSupport.writeUuid(output, record.transactionId().orElseThrow().value());
            }
            BinaryCodecSupport.writeString(output, record.reason(), MAX_REASON_BYTES);
            output.writeLong(record.createdAt().getEpochSecond());
            output.writeInt(record.createdAt().getNano());
            output.writeBoolean(record.successful());
            BinaryCodecSupport.writeString(output, record.outcome(), MAX_OUTCOME_BYTES);
            output.flush();
            byte[] result = bytes.toByteArray();
            if (result.length > EscrowJournalEventCodec.MAX_BODY_BYTES) {
                throw new IllegalArgumentException("Administrative audit journal body is too large");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode administrative audit", exception);
        }
    }

    public static EscrowAdministrativeRecord decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length == 0 || encoded.length > EscrowJournalEventCodec.MAX_BODY_BYTES) {
            throw new IllegalArgumentException("Invalid administrative audit journal size");
        }
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
            if (input.readInt() != VERSION) {
                throw new IllegalArgumentException("Unsupported administrative audit journal version");
            }
            java.util.UUID requestId = BinaryCodecSupport.readUuid(input);
            String actor = BinaryCodecSupport.readString(input, MAX_ACTOR_BYTES);
            EscrowAdministrativeAction action;
            try {
                action = EscrowAdministrativeAction.valueOf(
                        BinaryCodecSupport.readString(input, 128));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown administrative audit action", exception);
            }
            Optional<EscrowTransactionId> transactionId = BinaryCodecSupport.readBoolean(input)
                    ? Optional.of(new EscrowTransactionId(BinaryCodecSupport.readUuid(input)))
                    : Optional.empty();
            String reason = BinaryCodecSupport.readString(input, MAX_REASON_BYTES);
            Instant createdAt;
            try {
                long epochSecond = input.readLong();
                int nano = input.readInt();
                if (nano < 0 || nano > 999_999_999) {
                    throw new IllegalArgumentException("Invalid administrative audit nanoseconds");
                }
                createdAt = Instant.ofEpochSecond(epochSecond, nano);
            } catch (DateTimeException exception) {
                throw new IllegalArgumentException("Invalid administrative audit time", exception);
            }
            boolean successful = BinaryCodecSupport.readBoolean(input);
            String outcome = BinaryCodecSupport.readString(input, MAX_OUTCOME_BYTES);
            if (input.read() != -1) {
                throw new IllegalArgumentException("Administrative audit journal body has trailing data");
            }
            return new EscrowAdministrativeRecord(
                    requestId, actor, action, transactionId, reason, createdAt, successful, outcome);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to decode administrative audit", exception);
        }
    }
}
