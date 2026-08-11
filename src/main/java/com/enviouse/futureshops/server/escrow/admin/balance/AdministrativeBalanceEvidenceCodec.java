package com.enviouse.futureshops.server.escrow.admin.balance;

import com.enviouse.futureshops.server.shop.ShopResultCode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public final class AdministrativeBalanceEvidenceCodec {
    private static final int VERSION = 1;
    private static final int MAXIMUM_TEXT_LENGTH = 1024;

    private AdministrativeBalanceEvidenceCodec() {
    }

    public static String encode(AdministrativeBalanceEvidence evidence) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(VERSION);
            writeUuid(output, evidence.evidenceId());
            writeUuid(output, evidence.mutationRequestId());
            output.writeUTF(evidence.mutationFingerprint());
            output.writeByte(evidence.phase().ordinal());
            output.writeByte(evidence.operation().ordinal());
            writeUuid(output, evidence.targetPlayerId());
            writeOptionalUuid(output, evidence.counterpartyPlayerId());
            output.writeLong(evidence.amountMinor());
            output.writeBoolean(evidence.allowNegative());
            output.writeByte(evidence.confirmation().ordinal());
            output.writeLong(evidence.balanceBefore());
            output.writeLong(evidence.resultingBalance());
            writeOptionalLong(output,
                    evidence.counterpartyBalanceBefore());
            writeOptionalLong(output,
                    evidence.counterpartyResultingBalance());
            output.writeBoolean(evidence.successful());
            output.writeUTF(evidence.resultCode().name());
            output.writeLong(evidence.recordedAt().getEpochSecond());
            output.writeInt(evidence.recordedAt().getNano());
            output.flush();
            String encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(bytes.toByteArray());
            if (encoded.length() > MAXIMUM_TEXT_LENGTH) {
                throw new IllegalArgumentException(
                        "Balance evidence is too large");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode balance evidence", exception);
        }
    }

    public static AdministrativeBalanceEvidence decode(String encoded) {
        if (encoded == null || encoded.isEmpty()
                || encoded.length() > MAXIMUM_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "Invalid balance evidence size");
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            DataInputStream input = new DataInputStream(
                    new ByteArrayInputStream(bytes));
            if (input.readInt() != VERSION) {
                throw new IllegalArgumentException(
                        "Unsupported balance evidence version");
            }
            UUID evidenceId = readUuid(input);
            UUID mutationRequestId = readUuid(input);
            String fingerprint = input.readUTF();
            int phaseId = input.readUnsignedByte();
            AdministrativeBalanceEvidencePhase[] phases =
                    AdministrativeBalanceEvidencePhase.values();
            if (phaseId >= phases.length) {
                throw new IllegalArgumentException(
                        "Invalid balance evidence phase");
            }
            int operationId = input.readUnsignedByte();
            AdministrativeBalanceOperation[] operations =
                    AdministrativeBalanceOperation.values();
            if (operationId >= operations.length) {
                throw new IllegalArgumentException(
                        "Invalid balance evidence operation");
            }
            UUID targetPlayerId = readUuid(input);
            Optional<UUID> counterpartyPlayerId =
                    readOptionalUuid(input);
            long amountMinor = input.readLong();
            boolean allowNegative = input.readBoolean();
            int confirmationId = input.readUnsignedByte();
            AdministrativeBalanceConfirmation[] confirmations =
                    AdministrativeBalanceConfirmation.values();
            if (confirmationId >= confirmations.length) {
                throw new IllegalArgumentException(
                        "Invalid balance evidence confirmation");
            }
            long balanceBefore = input.readLong();
            long resultingBalance = input.readLong();
            OptionalLong counterpartyBefore = readOptionalLong(input);
            OptionalLong counterpartyAfter = readOptionalLong(input);
            boolean successful = input.readBoolean();
            ShopResultCode resultCode;
            try {
                resultCode = ShopResultCode.valueOf(input.readUTF());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Invalid balance evidence result", exception);
            }
            Instant recordedAt;
            try {
                recordedAt = Instant.ofEpochSecond(
                        input.readLong(), input.readInt());
            } catch (DateTimeException exception) {
                throw new IllegalArgumentException(
                        "Invalid balance evidence time", exception);
            }
            if (input.read() != -1) {
                throw new IllegalArgumentException(
                        "Balance evidence has trailing data");
            }
            return new AdministrativeBalanceEvidence(evidenceId,
                    mutationRequestId, fingerprint, phases[phaseId],
                    operations[operationId], targetPlayerId,
                    counterpartyPlayerId, amountMinor, allowNegative,
                    confirmations[confirmationId], counterpartyBefore,
                    balanceBefore, resultingBalance, counterpartyAfter,
                    successful, resultCode, recordedAt);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unable to decode balance evidence", exception);
        }
    }

    private static void writeUuid(DataOutputStream output, UUID value)
            throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input)
            throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static void writeOptionalUuid(
            DataOutputStream output,
            Optional<UUID> value
    ) throws IOException {
        output.writeBoolean(value.isPresent());
        if (value.isPresent()) {
            writeUuid(output, value.orElseThrow());
        }
    }

    private static Optional<UUID> readOptionalUuid(
            DataInputStream input
    ) throws IOException {
        return input.readBoolean()
                ? Optional.of(readUuid(input)) : Optional.empty();
    }

    private static void writeOptionalLong(
            DataOutputStream output,
            OptionalLong value
    ) throws IOException {
        output.writeBoolean(value.isPresent());
        if (value.isPresent()) {
            output.writeLong(value.getAsLong());
        }
    }

    private static OptionalLong readOptionalLong(DataInputStream input)
            throws IOException {
        return input.readBoolean()
                ? OptionalLong.of(input.readLong()) : OptionalLong.empty();
    }
}
