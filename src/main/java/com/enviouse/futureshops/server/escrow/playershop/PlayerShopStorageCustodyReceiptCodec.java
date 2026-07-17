package com.enviouse.futureshops.server.escrow.playershop;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class PlayerShopStorageCustodyReceiptCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES =
            3 * PlayerShopEscrowConstants.MAX_COMPONENT_BYTES;

    private static final int MAGIC = 0x46535043;

    private PlayerShopStorageCustodyReceiptCodec() {
    }

    public static byte[] encode(PlayerShopStorageCustodyReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            writeBody(output, receipt);
            output.flush();
            byte[] encoded = bytes.toByteArray();
            requireSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode player shop custody receipt", exception);
        }
    }

    public static PlayerShopStorageCustodyReceipt decode(byte[] encoded) {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        requireSize(copy);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(copy))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Player shop custody magic is invalid");
            }
            if (input.readInt() != CURRENT_SCHEMA) {
                throw new IllegalArgumentException("Player shop custody schema is unsupported");
            }
            PlayerShopStorageCustodyReceipt receipt = readBody(input);
            PlayerShopBinarySupport.requireFinished(input, "custody receipt");
            if (!Arrays.equals(copy, encode(receipt))) {
                throw new IllegalArgumentException("Player shop custody receipt is not canonical");
            }
            return receipt;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Player shop custody receipt is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException("Player shop custody receipt is invalid", exception);
        }
    }

    static void writeBody(DataOutputStream output,
                          PlayerShopStorageCustodyReceipt receipt) throws IOException {
        PlayerShopBinarySupport.writeUuid(output, receipt.requestId());
        PlayerShopIntentCodec.writeStorageMutation(output, receipt.plan());
        output.writeByte(receipt.state().ordinal());
        output.writeInt(receipt.appliedQuantity());
        PlayerShopBinarySupport.writeOptionalString(output,
                receipt.observedBeforeFingerprint(), 128);
        PlayerShopBinarySupport.writeOptionalString(output,
                receipt.observedAfterFingerprint(), 128);
        PlayerShopBinarySupport.writeOptionalBytes(output,
                receipt.adapterReceipt(),
                PlayerShopEscrowConstants.MAX_COMPONENT_BYTES);
        output.writeLong(receipt.updatedAt().getEpochSecond());
        output.writeInt(receipt.updatedAt().getNano());
        output.writeLong(receipt.revision());
        PlayerShopBinarySupport.writeOptionalString(output, receipt.reason(),
                PlayerShopEscrowConstants.MAX_TEXT_LENGTH);
        PlayerShopBinarySupport.writeString(output,
                receipt.receiptFingerprint(), 64);
    }

    static PlayerShopStorageCustodyReceipt readBody(DataInputStream input)
            throws IOException {
        UUID requestId = PlayerShopBinarySupport.readUuid(input,
                "custody request id");
        PlayerShopStorageMutationPlan plan =
                PlayerShopIntentCodec.readStorageMutation(input);
        PlayerShopStorageCustodyReceipt.RecoveryState state =
                PlayerShopBinarySupport.readEnum(input,
                        PlayerShopStorageCustodyReceipt.RecoveryState.values(),
                        "custody state");
        int quantity = input.readInt();
        String before = PlayerShopBinarySupport.readOptionalString(input, 128,
                "custody before fingerprint");
        String after = PlayerShopBinarySupport.readOptionalString(input, 128,
                "custody after fingerprint");
        byte[] evidence = PlayerShopBinarySupport.readOptionalBytes(input,
                PlayerShopEscrowConstants.MAX_COMPONENT_BYTES,
                "adapter receipt");
        long seconds = input.readLong();
        int nanos = input.readInt();
        if (nanos < 0 || nanos > 999_999_999) {
            throw new IllegalArgumentException("Player shop custody instant is invalid");
        }
        Instant updatedAt = Instant.ofEpochSecond(seconds, nanos);
        long revision = input.readLong();
        String reason = PlayerShopBinarySupport.readOptionalString(input,
                PlayerShopEscrowConstants.MAX_TEXT_LENGTH, "custody reason");
        String fingerprint = PlayerShopBinarySupport.readString(input, 64,
                "custody receipt fingerprint");
        return new PlayerShopStorageCustodyReceipt(requestId, plan, state,
                quantity, before, after, evidence, updatedAt, revision,
                reason, fingerprint);
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("Player shop custody receipt size is invalid");
        }
    }
}
