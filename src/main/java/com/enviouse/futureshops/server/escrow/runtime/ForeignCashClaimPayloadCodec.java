package com.enviouse.futureshops.server.escrow.runtime;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class ForeignCashClaimPayloadCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES =
            ForeignCashClaimPayload.MAX_ITEM_STACK_NBT_BYTES + 4096;

    private static final int MAGIC = 0x46534643;
    private static final int FINGERPRINT_MAGIC = 0x46534646;

    private ForeignCashClaimPayloadCodec() {
    }

    public static byte[] encode(ForeignCashClaimPayload payload) {
        Objects.requireNonNull(payload, "payload");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            writeFields(output, payload.providerId(), payload.configSignature(),
                    payload.registryItemId(), payload.denominationMinorUnits(),
                    payload.stackCount(), payload.denominationIndex(),
                    payload.portionIndex(), payload.portionCount(),
                    payload.serializedItemStackNbt());
            BinaryCodecSupport.writeString(output, payload.fingerprint(),
                    ForeignCashClaimPayload.CONFIG_SIGNATURE_LENGTH);
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException(
                        "Foreign cash claim payload exceeds its limit");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode foreign cash claim payload", exception);
        }
    }

    public static ForeignCashClaimPayload decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0
                || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Foreign cash claim payload size is invalid");
        }
        ByteArrayInputStream bytes = new ByteArrayInputStream(encoded);
        try (DataInputStream input = new DataInputStream(bytes)) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Foreign cash claim payload magic is invalid");
            }
            int schema = input.readInt();
            if (schema > CURRENT_SCHEMA) {
                throw new IllegalStateException(
                        "Foreign cash claim payload schema is newer than this build");
            }
            if (schema != CURRENT_SCHEMA) {
                throw new IllegalArgumentException(
                        "Foreign cash claim payload schema is unsupported");
            }
            String providerId = BinaryCodecSupport.readString(input,
                    ForeignCashClaimPayload.MAX_PROVIDER_ID_LENGTH * 4);
            String configSignature = BinaryCodecSupport.readString(input,
                    ForeignCashClaimPayload.CONFIG_SIGNATURE_LENGTH);
            String registryItemId = BinaryCodecSupport.readString(input,
                    ForeignCashClaimPayload.MAX_REGISTRY_ITEM_ID_LENGTH * 4);
            long denomination = input.readLong();
            int stackCount = input.readInt();
            int denominationIndex = input.readInt();
            int portionIndex = input.readInt();
            int portionCount = input.readInt();
            byte[] itemStackNbt = readBytes(input, bytes,
                    ForeignCashClaimPayload.MAX_ITEM_STACK_NBT_BYTES);
            String fingerprint = BinaryCodecSupport.readString(input,
                    ForeignCashClaimPayload.CONFIG_SIGNATURE_LENGTH);
            if (bytes.available() != 0) {
                throw new IllegalArgumentException(
                        "Foreign cash claim payload has trailing data");
            }
            return new ForeignCashClaimPayload(
                    providerId, configSignature, registryItemId,
                    denomination, stackCount, denominationIndex,
                    portionIndex, portionCount, itemStackNbt, fingerprint);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Foreign cash claim payload is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Foreign cash claim payload is invalid", exception);
        }
    }

    static String fingerprintOf(
            String providerId,
            String configSignature,
            String registryItemId,
            long denominationMinorUnits,
            int stackCount,
            int denominationIndex,
            int portionIndex,
            int portionCount,
            byte[] serializedItemStackNbt
    ) {
        Objects.requireNonNull(serializedItemStackNbt,
                "serializedItemStackNbt");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(FINGERPRINT_MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            writeFields(output, providerId, configSignature, registryItemId,
                    denominationMinorUnits, stackCount, denominationIndex,
                    portionIndex, portionCount, serializedItemStackNbt);
            output.flush();
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            bytes.toByteArray()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to fingerprint foreign cash claim payload", exception);
        }
    }

    private static void writeFields(
            DataOutputStream output,
            String providerId,
            String configSignature,
            String registryItemId,
            long denominationMinorUnits,
            int stackCount,
            int denominationIndex,
            int portionIndex,
            int portionCount,
            byte[] serializedItemStackNbt
    ) throws IOException {
        BinaryCodecSupport.writeString(output, providerId,
                ForeignCashClaimPayload.MAX_PROVIDER_ID_LENGTH * 4);
        BinaryCodecSupport.writeString(output, configSignature,
                ForeignCashClaimPayload.CONFIG_SIGNATURE_LENGTH);
        BinaryCodecSupport.writeString(output, registryItemId,
                ForeignCashClaimPayload.MAX_REGISTRY_ITEM_ID_LENGTH * 4);
        output.writeLong(denominationMinorUnits);
        output.writeInt(stackCount);
        output.writeInt(denominationIndex);
        output.writeInt(portionIndex);
        output.writeInt(portionCount);
        output.writeInt(serializedItemStackNbt.length);
        output.write(serializedItemStackNbt);
    }

    private static byte[] readBytes(DataInputStream input,
                                    ByteArrayInputStream bytes,
                                    int maximumBytes) throws IOException {
        int size = input.readInt();
        if (size <= 0 || size > maximumBytes || size > bytes.available()) {
            throw new IllegalArgumentException(
                    "Foreign cash item stack NBT size is invalid");
        }
        byte[] value = input.readNBytes(size);
        if (value.length != size) {
            throw new EOFException("Foreign cash item stack NBT is truncated");
        }
        return value;
    }
}
