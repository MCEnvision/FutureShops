package com.enviouse.futureshops.server.market.bazaar.escrow;

import com.enviouse.futureshops.server.market.bazaar.BazaarMutationCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class BazaarEscrowLifecycleStateCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES =
            BazaarMutationCodec.MAX_ENCODED_BYTES;

    private static final int MAGIC = 0x425A4C53;

    private BazaarEscrowLifecycleStateCodec() {
    }

    public static byte[] encode(BazaarEscrowLifecycleState state) {
        Objects.requireNonNull(state, "state");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            output.writeInt(state.createIntents().size());
            for (Map.Entry<UUID, BazaarCreateEscrowIntent> entry
                    : state.createIntents().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(
                            Comparator.comparing(UUID::toString)))
                    .toList()) {
                BazaarEscrowBinarySupport.writeUuid(output,
                        entry.getKey());
                BazaarEscrowBinarySupport.writeBytes(output,
                        BazaarCreateEscrowIntentCodec.encode(
                                entry.getValue()),
                        BazaarCreateEscrowIntentCodec.MAX_ENCODED_BYTES);
            }
            output.writeInt(state.commitFingerprints().size());
            for (Map.Entry<UUID, String> entry
                    : state.commitFingerprints().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(
                            Comparator.comparing(UUID::toString)))
                    .toList()) {
                BazaarEscrowBinarySupport.writeUuid(output,
                        entry.getKey());
                BazaarEscrowBinarySupport.writeText(output,
                        entry.getValue());
            }
            output.writeInt(state.activeBackings().size());
            for (Map.Entry<UUID, BazaarEscrowOrderBacking> entry
                    : state.activeBackings().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(
                            Comparator.comparing(UUID::toString)))
                    .toList()) {
                BazaarEscrowBinarySupport.writeUuid(output,
                        entry.getKey());
                BazaarEscrowBinarySupport.writeBacking(output,
                        entry.getValue());
            }
            output.flush();
            byte[] result = bytes.toByteArray();
            requireSize(result);
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode Bazaar lifecycle state", exception);
        }
    }

    public static BazaarEscrowLifecycleState decode(byte[] encoded) {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        requireSize(copy);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(copy))) {
            if (input.readInt() != MAGIC) {
                throw invalid("Bazaar lifecycle state magic is invalid");
            }
            if (input.readInt() != CURRENT_SCHEMA) {
                throw invalid(
                        "Bazaar lifecycle state schema is unsupported");
            }
            int intentCount = readCount(input,
                    BazaarEscrowLifecycleState.MAX_CREATE_INTENTS,
                    "creation intent");
            Map<UUID, BazaarCreateEscrowIntent> intents = new HashMap<>();
            for (int index = 0; index < intentCount; index++) {
                UUID requestId = BazaarEscrowBinarySupport.readUuid(input);
                BazaarCreateEscrowIntent intent =
                        BazaarCreateEscrowIntentCodec.decode(
                                BazaarEscrowBinarySupport.readBytes(input,
                                        BazaarCreateEscrowIntentCodec
                                                .MAX_ENCODED_BYTES));
                if (!requestId.equals(intent.requestId())
                        || intents.put(requestId, intent) != null) {
                    throw invalid(
                            "Bazaar creation intent index is invalid");
                }
            }
            int commitCount = readCount(input,
                    BazaarEscrowLifecycleState.MAX_COMMITS,
                    "commit fingerprint");
            Map<UUID, String> commits = new HashMap<>();
            for (int index = 0; index < commitCount; index++) {
                UUID requestId = BazaarEscrowBinarySupport.readUuid(input);
                String fingerprint = BazaarEscrowBinarySupport.readText(
                        input, 64, false);
                if (!fingerprint.matches("[0-9a-f]{64}")
                        || commits.put(requestId, fingerprint) != null) {
                    throw invalid(
                            "Bazaar commit fingerprint index is invalid");
                }
            }
            int backingCount = readCount(input,
                    BazaarEscrowLifecycleState.MAX_ACTIVE_BACKINGS,
                    "active backing");
            Map<UUID, BazaarEscrowOrderBacking> backings = new HashMap<>();
            for (int index = 0; index < backingCount; index++) {
                UUID orderId = BazaarEscrowBinarySupport.readUuid(input);
                BazaarEscrowOrderBacking backing =
                        BazaarEscrowBinarySupport.readBacking(input);
                if (!orderId.equals(backing.orderId())
                        || backings.put(orderId, backing) != null) {
                    throw invalid(
                            "Bazaar active backing index is invalid");
                }
            }
            if (input.read() != -1) {
                throw invalid(
                        "Bazaar lifecycle state has trailing data");
            }
            BazaarEscrowLifecycleState result =
                    new BazaarEscrowLifecycleState(intents, commits,
                            backings);
            if (!Arrays.equals(copy, encode(result))) {
                throw invalid(
                        "Bazaar lifecycle state encoding is not canonical");
            }
            return result;
        } catch (EOFException exception) {
            throw invalid("Bazaar lifecycle state is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw invalid("Bazaar lifecycle state is invalid", exception);
        }
    }

    private static int readCount(
            DataInputStream input,
            int maximum,
            String label
    ) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > maximum) {
            throw invalid("Bazaar " + label + " count is invalid");
        }
        return count;
    }

    private static void requireSize(byte[] value) {
        if (value.length == 0 || value.length > MAX_ENCODED_BYTES) {
            throw invalid("Bazaar lifecycle state size is invalid");
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static IllegalArgumentException invalid(
            String message,
            Throwable cause
    ) {
        return new IllegalArgumentException(message, cause);
    }
}
