package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairCommand;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairCommandCodec;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceStateFingerprint;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchCommit;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchCommitCodec;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionByteCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

public final class MaintenanceRepairJournalCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES = EscrowJournalEventCodec.MAX_BODY_BYTES;

    private static final int MAGIC = 0x46534D4A;

    private MaintenanceRepairJournalCodec() {
    }

    public static byte[] encode(MaintenanceRepairJournalEntry entry) {
        Objects.requireNonNull(entry, "entry");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            byte[] command = MaintenanceRepairCommandCodec.encode(entry.command());
            output.writeInt(command.length);
            output.write(command);
            writeEffect(output, entry.effect());
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException(
                        "Maintenance repair journal entry exceeds its size limit");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode maintenance repair journal entry", exception);
        }
    }

    public static MaintenanceRepairJournalEntry decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Maintenance repair journal entry size is invalid");
        }
        ByteArrayInputStream bytes = new ByteArrayInputStream(encoded);
        try (DataInputStream input = new DataInputStream(bytes)) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Maintenance repair journal magic is invalid");
            }
            int schema = input.readInt();
            if (schema > CURRENT_SCHEMA) {
                throw new IllegalStateException(
                        "Maintenance repair journal schema is newer than this build");
            }
            if (schema != CURRENT_SCHEMA) {
                throw new IllegalArgumentException(
                        "Maintenance repair journal schema is unsupported");
            }
            int commandBytes = input.readInt();
            if (commandBytes <= 0
                    || commandBytes > MaintenanceRepairCommandCodec.MAX_ENCODED_BYTES
                    || commandBytes > bytes.available()) {
                throw new IllegalArgumentException(
                        "Maintenance repair command size is invalid");
            }
            MaintenanceRepairCommand command = MaintenanceRepairCommandCodec.decode(
                    input.readNBytes(commandBytes));
            MaintenanceRepairJournalEntry.Effect effect = readEffect(input, bytes);
            if (bytes.available() != 0) {
                throw new IllegalArgumentException(
                        "Maintenance repair journal entry has trailing data");
            }
            return new MaintenanceRepairJournalEntry(command, effect);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Maintenance repair journal entry is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Maintenance repair journal entry is malformed", exception);
        }
    }

    private static void writeEffect(DataOutputStream output,
                                    MaintenanceRepairJournalEntry.Effect effect)
            throws IOException {
        byte[] payload;
        if (effect instanceof MaintenanceRepairJournalEntry.AuditOnly) {
            output.writeByte(0);
            output.writeInt(0);
            return;
        }
        if (effect instanceof MaintenanceRepairJournalEntry.RuntimeState value) {
            output.writeByte(1);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream nested = new DataOutputStream(bytes);
            nested.writeLong(value.result().revision());
            nested.write(value.result().fingerprint().bytes());
            nested.flush();
            payload = bytes.toByteArray();
        } else if (effect instanceof MaintenanceRepairJournalEntry.TransactionState value) {
            output.writeByte(2);
            payload = EscrowTransactionByteCodec.encode(value.transaction());
        } else if (effect instanceof MaintenanceRepairJournalEntry.ClaimState value) {
            output.writeByte(3);
            payload = ClaimJournalCodec.encodeClaim(value.claim());
        } else if (effect instanceof MaintenanceRepairJournalEntry.CustodyLotVerification value) {
            output.writeByte(4);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream nested = new DataOutputStream(bytes);
            writeUuid(nested, value.lotId());
            nested.writeLong(value.revision());
            nested.write(value.stateFingerprint().bytes());
            nested.flush();
            payload = bytes.toByteArray();
        } else if (effect instanceof MaintenanceRepairJournalEntry.CustodyBatchState value) {
            output.writeByte(5);
            payload = CustodyBatchCommitCodec.encode(value.commit());
        } else {
            throw new IllegalArgumentException("Unknown maintenance repair effect");
        }
        if (payload.length == 0 || payload.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Maintenance repair effect size is invalid");
        }
        output.writeInt(payload.length);
        output.write(payload);
    }

    private static MaintenanceRepairJournalEntry.Effect readEffect(
            DataInputStream input,
            ByteArrayInputStream bytes
    ) throws IOException {
        int kind = input.readUnsignedByte();
        int payloadBytes = input.readInt();
        if (payloadBytes < 0 || payloadBytes > MAX_ENCODED_BYTES
                || payloadBytes > bytes.available()) {
            throw new IllegalArgumentException(
                    "Maintenance repair effect size is invalid");
        }
        byte[] payload = input.readNBytes(payloadBytes);
        return switch (kind) {
            case 0 -> {
                requireEmpty(payload);
                yield new MaintenanceRepairJournalEntry.AuditOnly();
            }
            case 1 -> readRuntimeState(payload);
            case 2 -> new MaintenanceRepairJournalEntry.TransactionState(
                    EscrowTransactionByteCodec.decode(payload));
            case 3 -> new MaintenanceRepairJournalEntry.ClaimState(
                    ClaimJournalCodec.decodeClaim(payload));
            case 4 -> readCustodyVerification(payload);
            case 5 -> new MaintenanceRepairJournalEntry.CustodyBatchState(
                    CustodyBatchCommitCodec.decode(payload));
            default -> throw new IllegalArgumentException(
                    "Maintenance repair effect kind is invalid");
        };
    }

    private static MaintenanceRepairJournalEntry.CustodyLotVerification
    readCustodyVerification(byte[] payload) throws IOException {
        if (payload.length != Long.BYTES * 3
                + MaintenanceStateFingerprint.BYTE_LENGTH) {
            throw new IllegalArgumentException(
                    "Maintenance custody verification size is invalid");
        }
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
        UUID lotId = readUuid(input);
        long revision = input.readLong();
        byte[] fingerprint = input.readNBytes(MaintenanceStateFingerprint.BYTE_LENGTH);
        return new MaintenanceRepairJournalEntry.CustodyLotVerification(
                lotId, revision, MaintenanceStateFingerprint.of(fingerprint));
    }

    private static MaintenanceRepairJournalEntry.RuntimeState readRuntimeState(
            byte[] payload
    ) throws IOException {
        if (payload.length != Long.BYTES + MaintenanceStateFingerprint.BYTE_LENGTH) {
            throw new IllegalArgumentException(
                    "Maintenance runtime state size is invalid");
        }
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
        return new MaintenanceRepairJournalEntry.RuntimeState(
                new MaintenanceRuntimeSnapshot(input.readLong(),
                        MaintenanceStateFingerprint.of(input.readNBytes(
                                MaintenanceStateFingerprint.BYTE_LENGTH))));
    }

    private static void requireEmpty(byte[] payload) {
        if (payload.length != 0) {
            throw new IllegalArgumentException(
                    "Maintenance repair effect must not contain a payload");
        }
    }

    private static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }
}
