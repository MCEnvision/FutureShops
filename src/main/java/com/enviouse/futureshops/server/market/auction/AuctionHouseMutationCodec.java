package com.enviouse.futureshops.server.market.auction;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Objects;
import java.util.UUID;

public final class AuctionHouseMutationCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES = 1_200_000;

    private static final int MAGIC = 0x4653414D;

    private AuctionHouseMutationCodec() {
    }

    public static byte[] encode(AuctionHouseMutation mutation) {
        Objects.requireNonNull(mutation, "mutation");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            writeUuid(output, mutation.requestId());
            writeText(output, mutation.previousSnapshotFingerprint(), 64);
            output.writeLong(mutation.nextAcceptedSequence());
            for (AuctionTimeBasis basis : AuctionTimeBasis.values()) {
                output.writeLong(mutation.lastObservedTimeMillisByBasis()
                        .get(basis));
            }
            writeText(output, mutation.requestReceipt().fingerprint(), 64);
            byte[] result = AuctionHouseSnapshotCodec.encodeResult(
                    mutation.requestReceipt().result());
            output.writeInt(result.length);
            output.write(result);
            output.flush();
            byte[] encoded = bytes.toByteArray();
            requireSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode auction mutation", exception);
        }
    }

    public static AuctionHouseMutation decode(byte[] encoded) {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        requireSize(copy);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(copy))) {
            if (input.readInt() != MAGIC) {
                throw invalid("Auction mutation magic is invalid");
            }
            if (input.readInt() != CURRENT_SCHEMA) {
                throw invalid("Auction mutation schema is unsupported");
            }
            UUID requestId = readUuid(input);
            String previousFingerprint = readText(input, 64);
            long nextSequence = input.readLong();
            EnumMap<AuctionTimeBasis, Long> clocks =
                    new EnumMap<>(AuctionTimeBasis.class);
            for (AuctionTimeBasis basis : AuctionTimeBasis.values()) {
                clocks.put(basis, input.readLong());
            }
            String requestFingerprint = readText(input, 64);
            int resultSize = input.readInt();
            if (resultSize <= 0 || resultSize > 1_100_000
                    || resultSize > input.available()) {
                throw invalid("Auction mutation result size is invalid");
            }
            byte[] resultBytes = input.readNBytes(resultSize);
            if (resultBytes.length != resultSize) {
                throw new EOFException("Auction mutation result is truncated");
            }
            if (input.read() != -1) {
                throw invalid("Auction mutation has trailing data");
            }
            AuctionOperationResult result =
                    AuctionHouseSnapshotCodec.decodeResult(resultBytes);
            AuctionHouseMutation mutation = new AuctionHouseMutation(
                    requestId, previousFingerprint, nextSequence, clocks,
                    new AuctionRequestReceipt(requestFingerprint, result));
            if (!Arrays.equals(copy, encode(mutation))) {
                throw invalid("Auction mutation encoding is not canonical");
            }
            return mutation;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Auction mutation is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException("Auction mutation is invalid", exception);
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

    private static void writeText(DataOutputStream output, String value,
                                  int maximum) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > maximum) {
            throw invalid("Auction mutation text size is invalid");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readText(DataInputStream input, int maximum)
            throws IOException {
        int size = input.readInt();
        if (size <= 0 || size > maximum || size > input.available()) {
            throw invalid("Auction mutation text size is invalid");
        }
        byte[] bytes = input.readNBytes(size);
        if (bytes.length != size) {
            throw new EOFException("Auction mutation text is truncated");
        }
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (!Arrays.equals(bytes, value.getBytes(StandardCharsets.UTF_8))) {
            throw invalid("Auction mutation text is not valid UTF8");
        }
        return value;
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw invalid("Auction mutation size is invalid");
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
