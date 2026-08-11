package com.enviouse.futureshops.server.market.control;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

final class MarketControlBinarySupport {
    private MarketControlBinarySupport() {
    }

    static void writeModule(DataOutputStream output,
                            MarketModuleControl value)
            throws IOException {
        output.writeByte(value.module().wireTag());
        output.writeByte(value.status().wireTag());
        output.writeLong(value.revision());
        writeActor(output, value.actor());
        writeText(output, value.reason(),
                MarketModuleControl.MAX_REASON_BYTES);
        output.writeLong(value.changedAtMillis());
        writeOptionalLong(output, value.pauseStartedAtMillis());
        output.writeLong(value.accumulatedPausedMillis());
        writeOptional(output, value.lastPauseTimingEvidence(),
                MarketControlBinarySupport::writePauseEvidence);
        writeOptional(output, value.cancellationBatchId(),
                MarketControlBinarySupport::writeUuid);
    }

    static MarketModuleControl readModule(DataInputStream input)
            throws IOException {
        return new MarketModuleControl(
                MarketControlModule.fromWireTag(
                        input.readUnsignedByte()),
                MarketModuleStatus.fromWireTag(
                        input.readUnsignedByte()),
                input.readLong(), readActor(input),
                readText(input, MarketModuleControl.MAX_REASON_BYTES),
                input.readLong(), readOptionalLong(input),
                input.readLong(),
                readOptional(input,
                        MarketControlBinarySupport::readPauseEvidence),
                readOptional(input,
                        MarketControlBinarySupport::readUuid));
    }

    static void writeAudit(DataOutputStream output,
                           MarketControlAuditEntry value)
            throws IOException {
        writeUuid(output, value.requestId());
        writeText(output, value.requestFingerprint(), 64);
        output.writeByte(value.module().wireTag());
        output.writeByte(value.previousStatus().wireTag());
        output.writeByte(value.nextStatus().wireTag());
        output.writeLong(value.moduleRevision());
        output.writeLong(value.globalRevision());
        writeActor(output, value.actor());
        writeText(output, value.reason(),
                MarketModuleControl.MAX_REASON_BYTES);
        output.writeLong(value.requestedAtMillis());
        output.writeLong(value.appliedAtMillis());
        writeOptional(output, value.cancellationBatchId(),
                MarketControlBinarySupport::writeUuid);
        writeOptional(output, value.safetyEvidence(),
                MarketControlBinarySupport::writeSafetyEvidence);
        writeOptional(output, value.pauseTimingEvidence(),
                MarketControlBinarySupport::writePauseEvidence);
    }

    static MarketControlAuditEntry readAudit(DataInputStream input)
            throws IOException {
        return new MarketControlAuditEntry(readUuid(input),
                readText(input, 64),
                MarketControlModule.fromWireTag(
                        input.readUnsignedByte()),
                MarketModuleStatus.fromWireTag(
                        input.readUnsignedByte()),
                MarketModuleStatus.fromWireTag(
                        input.readUnsignedByte()),
                input.readLong(), input.readLong(), readActor(input),
                readText(input, MarketModuleControl.MAX_REASON_BYTES),
                input.readLong(), input.readLong(),
                readOptional(input,
                        MarketControlBinarySupport::readUuid),
                readOptional(input,
                        MarketControlBinarySupport::readSafetyEvidence),
                readOptional(input,
                        MarketControlBinarySupport::readPauseEvidence));
    }

    static void writeActor(DataOutputStream output,
                           MarketControlActor actor) throws IOException {
        writeUuid(output, actor.actorId());
        writeText(output, actor.label(),
                MarketControlActor.MAX_LABEL_BYTES);
    }

    static MarketControlActor readActor(DataInputStream input)
            throws IOException {
        return new MarketControlActor(readUuid(input),
                readText(input, MarketControlActor.MAX_LABEL_BYTES));
    }

    static void writeSafetyEvidence(
            DataOutputStream output,
            MarketControlSafetyEvidence evidence
    ) throws IOException {
        writeUuid(output, evidence.evidenceId());
        writeUuid(output, evidence.cancellationBatchId());
        output.writeLong(evidence.observedAtMillis());
        output.writeLong(evidence.activeValueOperations());
        output.writeLong(evidence.uncommittedRefundActions());
        output.writeBoolean(evidence.reconciliationComplete());
    }

    static MarketControlSafetyEvidence readSafetyEvidence(
            DataInputStream input
    ) throws IOException {
        return new MarketControlSafetyEvidence(readUuid(input),
                readUuid(input), input.readLong(), input.readLong(),
                input.readLong(), input.readBoolean());
    }

    static void writePauseEvidence(
            DataOutputStream output,
            MarketPauseTimingEvidence evidence
    ) throws IOException {
        output.writeLong(evidence.pausedAtMillis());
        writeOptionalLong(output, evidence.resumedAtMillis());
        output.writeLong(evidence.accumulatedPausedMillisBefore());
        output.writeLong(evidence.accumulatedPausedMillisAfter());
    }

    static MarketPauseTimingEvidence readPauseEvidence(
            DataInputStream input
    ) throws IOException {
        return new MarketPauseTimingEvidence(input.readLong(),
                readOptionalLong(input), input.readLong(),
                input.readLong());
    }

    static void writeUuid(DataOutputStream output, UUID value)
            throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    static void writeText(DataOutputStream output, String value,
                          int maximumBytes) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > maximumBytes) {
            throw invalid("Market control text size is invalid");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    static String readText(DataInputStream input, int maximumBytes)
            throws IOException {
        int size = input.readInt();
        if (size <= 0 || size > maximumBytes
                || size > input.available()) {
            throw invalid("Market control text size is invalid");
        }
        byte[] bytes = input.readNBytes(size);
        if (bytes.length != size) {
            throw new EOFException("Market control text is truncated");
        }
        try {
            String value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            if (!Arrays.equals(bytes,
                    value.getBytes(StandardCharsets.UTF_8))) {
                throw invalid(
                        "Market control text encoding is not canonical");
            }
            return value;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException(
                    "Market control text is not valid UTF8", exception);
        }
    }

    static void writeOptionalLong(DataOutputStream output,
                                  OptionalLong value)
            throws IOException {
        output.writeBoolean(value.isPresent());
        if (value.isPresent()) {
            output.writeLong(value.getAsLong());
        }
    }

    static OptionalLong readOptionalLong(DataInputStream input)
            throws IOException {
        return input.readBoolean()
                ? OptionalLong.of(input.readLong())
                : OptionalLong.empty();
    }

    static <T> void writeOptional(
            DataOutputStream output,
            Optional<T> value,
            Writer<T> writer
    ) throws IOException {
        output.writeBoolean(value.isPresent());
        if (value.isPresent()) {
            writer.write(output, value.orElseThrow());
        }
    }

    static <T> Optional<T> readOptional(
            DataInputStream input,
            Reader<T> reader
    ) throws IOException {
        return input.readBoolean()
                ? Optional.of(reader.read(input)) : Optional.empty();
    }

    static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    @FunctionalInterface
    interface Writer<T> {
        void write(DataOutputStream output, T value) throws IOException;
    }

    @FunctionalInterface
    interface Reader<T> {
        T read(DataInputStream input) throws IOException;
    }
}
