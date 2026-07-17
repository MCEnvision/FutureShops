package com.enviouse.futureshops.server.escrow.admin;

import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;

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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MaintenanceRepairCommandCodec {
    public static final int CURRENT_SCHEMA = 2;
    public static final int MAX_ENCODED_BYTES = 16_384;

    private static final int MAGIC = 0x46534D52;

    private MaintenanceRepairCommandCodec() {
    }

    public static byte[] encode(MaintenanceRepairCommand command) {
        Objects.requireNonNull(command, "command");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            writeUuid(output, command.commandId());
            writeText(output, command.actor(), MaintenanceRepairCommand.MAX_ACTOR_LENGTH);
            writeText(output, command.reason(), MaintenanceRepairCommand.MAX_REASON_LENGTH);
            writeBoolean(output, command.confirmed());
            writeInstant(output, command.createdAt());
            writeTarget(output, command.target());
            writeExpectedState(output, command.expectedState());
            writePayload(output, command.payload());
            writeAudit(output, command.auditRecord());
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException("Maintenance command exceeds its size limit");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode maintenance command", exception);
        }
    }

    public static MaintenanceRepairCommand decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("Maintenance command payload is invalid");
        }
        ByteArrayInputStream bytes = new ByteArrayInputStream(encoded);
        try (DataInputStream input = new DataInputStream(bytes)) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Maintenance command magic is invalid");
            }
            int schema = input.readInt();
            if (schema > CURRENT_SCHEMA) {
                throw new IllegalStateException(
                        "Maintenance command schema is newer than this build");
            }
            if (schema != CURRENT_SCHEMA) {
                throw new IllegalArgumentException("Maintenance command schema is unsupported");
            }
            UUID commandId = readUuid(input);
            String actor = readText(input, MaintenanceRepairCommand.MAX_ACTOR_LENGTH,
                    "actor");
            String reason = readText(input, MaintenanceRepairCommand.MAX_REASON_LENGTH,
                    "reason");
            boolean confirmed = readBoolean(input);
            Instant createdAt = readInstant(input);
            MaintenanceRepairTarget target = readTarget(input);
            MaintenanceExpectedState expectedState = readExpectedState(input);
            MaintenanceRepairPayload payload = readPayload(input);
            EscrowAdministrativeRecord audit = readAudit(input);
            if (bytes.available() != 0) {
                throw new IllegalArgumentException("Maintenance command has trailing data");
            }
            return new MaintenanceRepairCommand(commandId, actor, reason, confirmed,
                    createdAt, target, expectedState, payload, audit);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Maintenance command is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Maintenance command payload is malformed",
                    exception);
        }
    }

    private static void writeTarget(DataOutputStream output, MaintenanceRepairTarget target)
            throws IOException {
        output.writeByte(targetTypeId(target.type()));
        writeUuid(output, target.targetId());
    }

    private static MaintenanceRepairTarget readTarget(DataInputStream input) throws IOException {
        MaintenanceRepairTargetType type = targetType(input.readUnsignedByte());
        return new MaintenanceRepairTarget(type, readUuid(input));
    }

    private static void writeExpectedState(DataOutputStream output,
                                           MaintenanceExpectedState expectedState)
            throws IOException {
        if (expectedState.kind() == MaintenanceExpectedStateKind.REVISION) {
            output.writeByte(1);
            output.writeLong(expectedState.expectedRevision());
        } else {
            output.writeByte(2);
            output.write(expectedState.fingerprint().orElseThrow().bytes());
        }
    }

    private static MaintenanceExpectedState readExpectedState(DataInputStream input)
            throws IOException {
        return switch (input.readUnsignedByte()) {
            case 1 -> MaintenanceExpectedState.revision(input.readLong());
            case 2 -> new MaintenanceExpectedState(MaintenanceExpectedStateKind.FINGERPRINT,
                    -1L, Optional.of(MaintenanceStateFingerprint.of(
                    readExact(input, MaintenanceStateFingerprint.BYTE_LENGTH))));
            default -> throw new IllegalArgumentException(
                    "Maintenance expected state kind is invalid");
        };
    }

    private static void writePayload(DataOutputStream output, MaintenanceRepairPayload payload)
            throws IOException {
        output.writeByte(actionId(payload.action()));
        if (payload instanceof MaintenanceRepairPayload.EnterMaintenance value) {
            writeText(output, value.incidentReference(),
                    MaintenanceRepairPayload.MAX_INCIDENT_REFERENCE_LENGTH);
        } else if (payload instanceof MaintenanceRepairPayload.RetryReset
                || payload instanceof MaintenanceRepairPayload.ForceRefund
                || payload instanceof MaintenanceRepairPayload.ForceSettlement
                || payload instanceof MaintenanceRepairPayload.ClaimQuarantine
                || payload instanceof MaintenanceRepairPayload.CustodyQuarantine) {
            return;
        } else if (payload instanceof MaintenanceRepairPayload.ClaimRepair value) {
            output.writeByte(claimDispositionId(value.disposition()));
            output.writeLong(value.resultingRemainingUnits());
        } else if (payload instanceof MaintenanceRepairPayload.CustodyReconcile value) {
            output.write(value.observedFingerprint().bytes());
            output.writeByte(custodyDispositionId(value.disposition()));
        } else if (payload instanceof MaintenanceRepairPayload.VerifyAndResume value) {
            output.writeLong(value.verifiedJournalSequence());
            output.write(value.verificationFingerprint().bytes());
        } else {
            throw new IllegalArgumentException("Maintenance action payload is unsupported");
        }
    }

    private static MaintenanceRepairPayload readPayload(DataInputStream input)
            throws IOException {
        return switch (readAction(input.readUnsignedByte())) {
            case ENTER_MAINTENANCE -> new MaintenanceRepairPayload.EnterMaintenance(
                    readText(input, MaintenanceRepairPayload.MAX_INCIDENT_REFERENCE_LENGTH,
                            "incident reference"));
            case RESUME_WRITES -> new MaintenanceRepairPayload.VerifyAndResume(
                    input.readLong(), MaintenanceStateFingerprint.of(
                    readExact(input, MaintenanceStateFingerprint.BYTE_LENGTH)));
            case RETRY_TRANSACTION -> new MaintenanceRepairPayload.RetryReset();
            case FORCE_REFUND -> new MaintenanceRepairPayload.ForceRefund();
            case FORCE_SETTLEMENT -> new MaintenanceRepairPayload.ForceSettlement();
            case QUARANTINE_CLAIM -> new MaintenanceRepairPayload.ClaimQuarantine();
            case REPAIR_CLAIM -> new MaintenanceRepairPayload.ClaimRepair(
                    readClaimDisposition(input.readUnsignedByte()), input.readLong());
            case RECONCILE_CUSTODY -> new MaintenanceRepairPayload.CustodyReconcile(
                    MaintenanceStateFingerprint.of(
                            readExact(input, MaintenanceStateFingerprint.BYTE_LENGTH)),
                    readCustodyDisposition(input.readUnsignedByte()));
            case QUARANTINE_CUSTODY -> new MaintenanceRepairPayload.CustodyQuarantine();
        };
    }

    private static void writeAudit(DataOutputStream output, EscrowAdministrativeRecord audit)
            throws IOException {
        writeUuid(output, audit.requestId());
        writeText(output, audit.actor(), MaintenanceRepairCommand.MAX_ACTOR_LENGTH);
        output.writeByte(actionId(audit.action()));
        output.writeByte(audit.transactionId().isPresent() ? 1 : 0);
        if (audit.transactionId().isPresent()) {
            writeUuid(output, audit.transactionId().orElseThrow().value());
        }
        writeText(output, audit.reason(), MaintenanceRepairCommand.MAX_REASON_LENGTH);
        writeInstant(output, audit.createdAt());
        writeBoolean(output, audit.successful());
        writeText(output, audit.outcome(), MaintenanceRepairCommand.MAX_OUTCOME_LENGTH);
    }

    private static EscrowAdministrativeRecord readAudit(DataInputStream input)
            throws IOException {
        UUID requestId = readUuid(input);
        String actor = readText(input, MaintenanceRepairCommand.MAX_ACTOR_LENGTH,
                "audit actor");
        EscrowAdministrativeAction action = readAction(input.readUnsignedByte());
        int marker = input.readUnsignedByte();
        if (marker > 1) {
            throw new IllegalArgumentException(
                    "Maintenance audit transaction marker is invalid");
        }
        Optional<EscrowTransactionId> transactionId = marker == 1
                ? Optional.of(new EscrowTransactionId(readUuid(input))) : Optional.empty();
        String reason = readText(input, MaintenanceRepairCommand.MAX_REASON_LENGTH,
                "audit reason");
        Instant createdAt = readInstant(input);
        boolean successful = readBoolean(input);
        String outcome = readText(input, MaintenanceRepairCommand.MAX_OUTCOME_LENGTH,
                "audit outcome");
        return new EscrowAdministrativeRecord(requestId, actor, action, transactionId,
                reason, createdAt, successful, outcome);
    }

    private static int targetTypeId(MaintenanceRepairTargetType type) {
        return switch (type) {
            case RUNTIME -> 1;
            case TRANSACTION -> 2;
            case CLAIM -> 3;
            case CUSTODY_LOT -> 4;
            case CUSTODY_BATCH -> 5;
        };
    }

    private static MaintenanceRepairTargetType targetType(int id) {
        return switch (id) {
            case 1 -> MaintenanceRepairTargetType.RUNTIME;
            case 2 -> MaintenanceRepairTargetType.TRANSACTION;
            case 3 -> MaintenanceRepairTargetType.CLAIM;
            case 4 -> MaintenanceRepairTargetType.CUSTODY_LOT;
            case 5 -> MaintenanceRepairTargetType.CUSTODY_BATCH;
            default -> throw new IllegalArgumentException("Maintenance target type is invalid");
        };
    }

    private static int actionId(EscrowAdministrativeAction action) {
        return switch (action) {
            case ENTER_MAINTENANCE -> 1;
            case RESUME_WRITES -> 2;
            case RETRY_TRANSACTION -> 3;
            case FORCE_REFUND -> 4;
            case FORCE_SETTLEMENT -> 5;
            case QUARANTINE_CLAIM -> 6;
            case REPAIR_CLAIM -> 7;
            case RECONCILE_CUSTODY -> 8;
            case QUARANTINE_CUSTODY -> 9;
        };
    }

    private static EscrowAdministrativeAction readAction(int id) {
        return switch (id) {
            case 1 -> EscrowAdministrativeAction.ENTER_MAINTENANCE;
            case 2 -> EscrowAdministrativeAction.RESUME_WRITES;
            case 3 -> EscrowAdministrativeAction.RETRY_TRANSACTION;
            case 4 -> EscrowAdministrativeAction.FORCE_REFUND;
            case 5 -> EscrowAdministrativeAction.FORCE_SETTLEMENT;
            case 6 -> EscrowAdministrativeAction.QUARANTINE_CLAIM;
            case 7 -> EscrowAdministrativeAction.REPAIR_CLAIM;
            case 8 -> EscrowAdministrativeAction.RECONCILE_CUSTODY;
            case 9 -> EscrowAdministrativeAction.QUARANTINE_CUSTODY;
            default -> throw new IllegalArgumentException("Maintenance action is invalid");
        };
    }

    private static int claimDispositionId(MaintenanceClaimRepairDisposition disposition) {
        return switch (disposition) {
            case REOPEN_PENDING -> 1;
            case REOPEN_PARTIAL -> 2;
            case COMPLETE -> 3;
        };
    }

    private static MaintenanceClaimRepairDisposition readClaimDisposition(int id) {
        return switch (id) {
            case 1 -> MaintenanceClaimRepairDisposition.REOPEN_PENDING;
            case 2 -> MaintenanceClaimRepairDisposition.REOPEN_PARTIAL;
            case 3 -> MaintenanceClaimRepairDisposition.COMPLETE;
            default -> throw new IllegalArgumentException(
                    "Maintenance claim repair disposition is invalid");
        };
    }

    private static int custodyDispositionId(MaintenanceCustodyDisposition disposition) {
        return switch (disposition) {
            case CONFIRM_HELD -> 1;
            case MARK_RELEASED -> 2;
            case MARK_CONSUMED -> 3;
            case QUARANTINE -> 4;
        };
    }

    private static MaintenanceCustodyDisposition readCustodyDisposition(int id) {
        return switch (id) {
            case 1 -> MaintenanceCustodyDisposition.CONFIRM_HELD;
            case 2 -> MaintenanceCustodyDisposition.MARK_RELEASED;
            case 3 -> MaintenanceCustodyDisposition.MARK_CONSUMED;
            case 4 -> MaintenanceCustodyDisposition.QUARANTINE;
            default -> throw new IllegalArgumentException(
                    "Maintenance custody disposition is invalid");
        };
    }

    private static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static void writeBoolean(DataOutputStream output, boolean value) throws IOException {
        output.writeByte(value ? 1 : 0);
    }

    private static boolean readBoolean(DataInputStream input) throws IOException {
        int value = input.readUnsignedByte();
        if (value > 1) {
            throw new IllegalArgumentException("Maintenance boolean marker is invalid");
        }
        return value == 1;
    }

    private static void writeInstant(DataOutputStream output, Instant value) throws IOException {
        output.writeLong(value.getEpochSecond());
        output.writeInt(value.getNano());
    }

    private static Instant readInstant(DataInputStream input) throws IOException {
        long seconds = input.readLong();
        int nanos = input.readInt();
        if (nanos < 0 || nanos > 999_999_999) {
            throw new IllegalArgumentException("Maintenance timestamp nanoseconds are invalid");
        }
        try {
            return Instant.ofEpochSecond(seconds, nanos);
        } catch (DateTimeException | ArithmeticException exception) {
            throw new IllegalArgumentException("Maintenance timestamp is invalid", exception);
        }
    }

    private static void writeText(DataOutputStream output, String value,
                                  int maximumCharacters) throws IOException {
        if (value.length() > maximumCharacters) {
            throw new IllegalArgumentException("Maintenance text exceeds its limit");
        }
        byte[] encoded;
        try {
            ByteBuffer buffer = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            encoded = new byte[buffer.remaining()];
            buffer.get(encoded);
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Maintenance text is not valid UTF8", exception);
        }
        if (encoded.length == 0 || encoded.length > Math.multiplyExact(maximumCharacters, 4)) {
            throw new IllegalArgumentException("Maintenance text encoding is invalid");
        }
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static String readText(DataInputStream input, int maximumCharacters,
                                   String label) throws IOException {
        int maximumBytes = Math.multiplyExact(maximumCharacters, 4);
        int length = input.readInt();
        if (length <= 0 || length > maximumBytes) {
            throw new IllegalArgumentException("Maintenance text length is invalid");
        }
        byte[] encoded = readExact(input, length);
        try {
            String value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded)).toString();
            String normalized = MaintenanceRepairText.require(value, label,
                    maximumCharacters);
            if (!value.equals(normalized)) {
                throw new IllegalArgumentException("Maintenance text is not canonical");
            }
            return value;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Maintenance text is not valid UTF8", exception);
        }
    }

    private static byte[] readExact(DataInputStream input, int length) throws IOException {
        byte[] value = input.readNBytes(length);
        if (value.length != length) {
            throw new EOFException("Maintenance payload is truncated");
        }
        return value;
    }
}
