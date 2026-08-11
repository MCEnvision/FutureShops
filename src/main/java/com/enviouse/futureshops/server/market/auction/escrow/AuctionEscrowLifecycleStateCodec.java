package com.enviouse.futureshops.server.market.auction.escrow;

import com.enviouse.futureshops.server.market.auction.AuctionHouseSnapshotCodec;

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

public final class AuctionEscrowLifecycleStateCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES =
            AuctionHouseSnapshotCodec.MAX_ENCODED_BYTES;

    private static final int MAGIC = 0x41454C53;

    private AuctionEscrowLifecycleStateCodec() {
    }

    public static byte[] encode(AuctionEscrowLifecycleState state) {
        Objects.requireNonNull(state, "state");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            output.writeInt(state.createIntents().size());
            for (Map.Entry<UUID, AuctionCreateEscrowIntent> entry
                    : state.createIntents().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(
                            Comparator.comparing(UUID::toString)))
                    .toList()) {
                writeUuid(output, entry.getKey());
                writeComponent(output,
                        AuctionCreateEscrowIntentCodec.encode(
                                entry.getValue()));
            }
            output.writeInt(state.commits().size());
            for (Map.Entry<UUID, AuctionEscrowCommit> entry
                    : state.commits().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(
                            Comparator.comparing(UUID::toString)))
                    .toList()) {
                writeUuid(output, entry.getKey());
                writeComponent(output, AuctionEscrowCommitCodec.encode(
                        entry.getValue()));
            }
            output.flush();
            byte[] encoded = bytes.toByteArray();
            requireSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode auction escrow lifecycle state",
                    exception);
        }
    }

    public static AuctionEscrowLifecycleState decode(byte[] encoded) {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        requireSize(copy);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(copy))) {
            if (input.readInt() != MAGIC) {
                throw invalid(
                        "Auction escrow lifecycle state magic is invalid");
            }
            if (input.readInt() != CURRENT_SCHEMA) {
                throw invalid(
                        "Auction escrow lifecycle state schema is unsupported");
            }
            int intentCount = input.readInt();
            if (intentCount < 0 || intentCount
                    > AuctionEscrowLifecycleState.MAX_CREATE_INTENTS) {
                throw invalid(
                        "Auction creation intent count is invalid");
            }
            Map<UUID, AuctionCreateEscrowIntent> intents = new HashMap<>();
            for (int index = 0; index < intentCount; index++) {
                UUID requestId = readUuid(input);
                AuctionCreateEscrowIntent intent =
                        AuctionCreateEscrowIntentCodec.decode(
                                readComponent(input));
                if (!requestId.equals(intent.requestId())
                        || intents.put(requestId, intent) != null) {
                    throw invalid(
                            "Auction creation intent index is invalid");
                }
            }
            int commitCount = input.readInt();
            if (commitCount < 0 || commitCount
                    > AuctionEscrowLifecycleState.MAX_COMMITS) {
                throw invalid("Auction escrow commit count is invalid");
            }
            Map<UUID, AuctionEscrowCommit> commits = new HashMap<>();
            for (int index = 0; index < commitCount; index++) {
                UUID requestId = readUuid(input);
                AuctionEscrowCommit commit = AuctionEscrowCommitCodec
                        .decode(readComponent(input));
                if (!requestId.equals(commit.requestId())
                        || commits.put(requestId, commit) != null) {
                    throw invalid(
                            "Auction escrow commit index is invalid");
                }
            }
            if (input.read() != -1) {
                throw invalid(
                        "Auction escrow lifecycle state has trailing data");
            }
            AuctionEscrowLifecycleState result =
                    new AuctionEscrowLifecycleState(intents, commits);
            if (!Arrays.equals(copy, encode(result))) {
                throw invalid(
                        "Auction escrow lifecycle state is not canonical");
            }
            return result;
        } catch (EOFException exception) {
            throw invalid(
                    "Auction escrow lifecycle state is truncated",
                    exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw invalid(
                    "Auction escrow lifecycle state is invalid",
                    exception);
        }
    }

    private static void writeUuid(DataOutputStream output, UUID value)
            throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static void writeComponent(
            DataOutputStream output,
            byte[] value
    ) throws IOException {
        if (value.length == 0 || value.length > MAX_ENCODED_BYTES) {
            throw invalid(
                    "Auction escrow lifecycle state component is invalid");
        }
        output.writeInt(value.length);
        output.write(value);
        if (output.size() > MAX_ENCODED_BYTES) {
            throw invalid(
                    "Auction escrow lifecycle state exceeds its limit");
        }
    }

    private static byte[] readComponent(DataInputStream input)
            throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > MAX_ENCODED_BYTES
                || length > input.available()) {
            throw invalid(
                    "Auction escrow lifecycle state component size is invalid");
        }
        byte[] value = input.readNBytes(length);
        if (value.length != length) {
            throw new EOFException(
                    "Auction escrow lifecycle state component is truncated");
        }
        return value;
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw invalid(
                    "Auction escrow lifecycle state size is invalid");
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
