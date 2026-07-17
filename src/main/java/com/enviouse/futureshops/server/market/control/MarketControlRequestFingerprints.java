package com.enviouse.futureshops.server.market.control;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class MarketControlRequestFingerprints {
    private static final int SCHEMA = 1;

    private MarketControlRequestFingerprints() {
    }

    public static String fingerprint(
            MarketControlTransitionCommand command
    ) {
        Objects.requireNonNull(command, "command");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(SCHEMA);
            MarketControlBinarySupport.writeUuid(output,
                    command.requestId());
            output.writeByte(command.module().wireTag());
            output.writeLong(command.expectedModuleRevision());
            output.writeByte(command.targetStatus().wireTag());
            MarketControlBinarySupport.writeActor(output,
                    command.actor());
            MarketControlBinarySupport.writeText(output,
                    command.reason(),
                    MarketModuleControl.MAX_REASON_BYTES);
            output.writeLong(command.requestedAtMillis());
            output.writeLong(command.appliedAtMillis());
            MarketControlBinarySupport.writeOptional(output,
                    command.cancellationBatchId(),
                    MarketControlBinarySupport::writeUuid);
            MarketControlBinarySupport.writeOptional(output,
                    command.safetyEvidence(),
                    MarketControlBinarySupport::writeSafetyEvidence);
            output.flush();
            return HexFormat.of().formatHex(digest(
                    bytes.toByteArray()));
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to fingerprint market control request",
                    exception);
        }
    }

    private static byte[] digest(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Market control hashing is unavailable", exception);
        }
    }
}
