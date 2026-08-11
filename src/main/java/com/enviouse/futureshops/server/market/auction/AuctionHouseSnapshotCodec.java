package com.enviouse.futureshops.server.market.auction;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.HexFormat;

public final class AuctionHouseSnapshotCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES = 67_107_840;

    private static final int MAGIC = 0x46534148;

    private AuctionHouseSnapshotCodec() {
    }

    public static byte[] encode(AuctionHouseSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            output.writeLong(snapshot.nextAcceptedSequence());
            for (AuctionTimeBasis basis : AuctionTimeBasis.values()) {
                output.writeLong(snapshot.lastObservedTimeMillis(basis));
            }
            output.writeInt(snapshot.listings().size());
            for (Map.Entry<UUID, AuctionListing> entry
                    : snapshot.listings().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {
                writeUuid(output, entry.getKey());
                writeListing(output, entry.getValue());
            }
            output.writeInt(snapshot.requestReceipts().size());
            for (Map.Entry<UUID, AuctionRequestReceipt> entry
                    : snapshot.requestReceipts().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {
                writeUuid(output, entry.getKey());
                writeString(output, entry.getValue().fingerprint(), 64);
                writeResult(output, entry.getValue().result());
            }
            output.flush();
            byte[] encoded = bytes.toByteArray();
            requireSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode auction snapshot", exception);
        }
    }

    public static AuctionHouseSnapshot decode(byte[] encoded) {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        requireSize(copy);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(copy))) {
            if (input.readInt() != MAGIC) {
                throw invalid("Auction snapshot magic is invalid");
            }
            if (input.readInt() != CURRENT_SCHEMA) {
                throw invalid("Auction snapshot schema is unsupported");
            }
            long nextSequence = input.readLong();
            EnumMap<AuctionTimeBasis, Long> clocks =
                    new EnumMap<>(AuctionTimeBasis.class);
            for (AuctionTimeBasis basis : AuctionTimeBasis.values()) {
                clocks.put(basis, input.readLong());
            }
            int listingCount = readCount(input,
                    AuctionHouseSnapshot.MAX_LISTINGS, "listing");
            Map<UUID, AuctionListing> listings = new LinkedHashMap<>();
            for (int index = 0; index < listingCount; index++) {
                UUID key = readUuid(input);
                AuctionListing listing = readListing(input);
                if (!key.equals(listing.listingId())
                        || listings.putIfAbsent(key, listing) != null) {
                    throw invalid("Auction snapshot listing identity is invalid");
                }
            }
            int receiptCount = readCount(input,
                    AuctionHouseSnapshot.MAX_REQUEST_RECEIPTS, "receipt");
            Map<UUID, AuctionRequestReceipt> receipts =
                    new LinkedHashMap<>();
            for (int index = 0; index < receiptCount; index++) {
                UUID key = readUuid(input);
                String fingerprint = readString(input, 64);
                AuctionOperationResult result = readResult(input);
                if (!key.equals(result.requestId())
                        || receipts.putIfAbsent(key,
                        new AuctionRequestReceipt(fingerprint, result))
                        != null) {
                    throw invalid("Auction snapshot receipt identity is invalid");
                }
            }
            if (input.read() != -1) {
                throw invalid("Auction snapshot has trailing data");
            }
            AuctionHouseSnapshot snapshot = new AuctionHouseSnapshot(
                    nextSequence, clocks, listings, receipts);
            if (!Arrays.equals(copy, encode(snapshot))) {
                throw invalid("Auction snapshot encoding is not canonical");
            }
            return snapshot;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Auction snapshot is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException("Auction snapshot is invalid", exception);
        }
    }

    public static String fingerprint(AuctionHouseSnapshot snapshot) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(
                    "SHA-256").digest(encode(snapshot)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Auction snapshot hashing is unavailable", exception);
        }
    }

    static byte[] encodeResult(AuctionOperationResult result) {
        Objects.requireNonNull(result, "result");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            writeResult(output, result);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode auction result", exception);
        }
    }

    static AuctionOperationResult decodeResult(byte[] encoded) {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        if (copy.length == 0 || copy.length > 1_100_000) {
            throw invalid("Auction result size is invalid");
        }
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(copy))) {
            AuctionOperationResult result = readResult(input);
            if (input.read() != -1
                    || !Arrays.equals(copy, encodeResult(result))) {
                throw invalid("Auction result encoding is not canonical");
            }
            return result;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Auction result is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException("Auction result is invalid", exception);
        }
    }

    private static void writeListing(DataOutputStream output,
                                     AuctionListing listing)
            throws IOException {
        writeUuid(output, listing.listingId());
        writeUuid(output, listing.sellerId());
        writeUuid(output, listing.activationTransactionId());
        writeLot(output, listing.itemLot());
        writeEnum(output, listing.type());
        writeEnum(output, listing.state());
        output.writeLong(listing.revision());
        output.writeLong(listing.startingBidMinor());
        output.writeLong(listing.buyoutMinor());
        writeRules(output, listing.rules());
        output.writeLong(listing.createdAtMillis());
        output.writeLong(listing.deadlineMillis());
        output.writeLong(listing.originalDurationMillis());
        output.writeLong(listing.lastObservedTimeMillis());
        output.writeLong(listing.frozenRemainingMillis());
        output.writeInt(listing.antiSnipeExtensionCount());
        output.writeLong(listing.antiSnipeCumulativeMillis());
        output.writeLong(listing.acceptedBidCount());
        writeOptional(output, listing.highestBid(),
                AuctionHouseSnapshotCodec::writeBid);
        writeOptional(output, listing.sale(),
                AuctionHouseSnapshotCodec::writeSale);
        writeOptional(output, listing.terminalTransactionId(),
                AuctionHouseSnapshotCodec::writeUuid);
    }

    private static AuctionListing readListing(DataInputStream input)
            throws IOException {
        return new AuctionListing(readUuid(input), readUuid(input),
                readUuid(input), readLot(input),
                readEnum(input, AuctionListingType.values(),
                        "listing type"),
                readEnum(input, AuctionListingState.values(),
                        "listing state"),
                input.readLong(), input.readLong(), input.readLong(),
                readRules(input), input.readLong(), input.readLong(),
                input.readLong(), input.readLong(), input.readLong(),
                input.readInt(), input.readLong(), input.readLong(),
                readOptional(input, AuctionHouseSnapshotCodec::readBid),
                readOptional(input, AuctionHouseSnapshotCodec::readSale),
                readOptional(input, AuctionHouseSnapshotCodec::readUuid));
    }

    private static void writeLot(DataOutputStream output,
                                 AuctionItemLot lot) throws IOException {
        writeUuid(output, lot.custodyLotId());
        writeString(output, lot.registryId(), 1024);
        writeString(output, lot.fingerprint(), 64);
        output.writeInt(lot.count());
        output.writeInt(lot.serializedBytes());
        writeStringAllowEmpty(output, lot.categoryId(), 512);
        writeString(output, lot.searchDocument(), 16384);
    }

    private static AuctionItemLot readLot(DataInputStream input)
            throws IOException {
        return new AuctionItemLot(readUuid(input), readString(input, 1024),
                readString(input, 64), input.readInt(), input.readInt(),
                readStringAllowEmpty(input, 512),
                readString(input, 16384));
    }

    private static void writeRules(DataOutputStream output,
                                   AuctionRuleSnapshot rules)
            throws IOException {
        output.writeLong(rules.listingFeeMinor());
        output.writeInt(rules.saleTaxBasisPoints());
        output.writeLong(rules.minimumIncrementMinor());
        output.writeInt(rules.minimumIncrementBasisPoints());
        output.writeBoolean(rules.antiSnipeEnabled());
        output.writeLong(rules.antiSnipeTriggerMillis());
        output.writeLong(rules.antiSnipeExtensionMillis());
        output.writeLong(rules.maximumAntiSnipeCumulativeMillis());
        output.writeInt(rules.maximumAntiSnipeExtensionCount());
        output.writeBoolean(rules.allowSellerCancelBeforeBid());
        writeEnum(output, rules.timeBasis());
        output.writeBoolean(rules.pauseWhileFrozen());
        output.writeLong(rules.configRevision());
    }

    private static AuctionRuleSnapshot readRules(DataInputStream input)
            throws IOException {
        return new AuctionRuleSnapshot(input.readLong(), input.readInt(),
                input.readLong(), input.readInt(), readBoolean(input),
                input.readLong(), input.readLong(), input.readLong(),
                input.readInt(), readBoolean(input),
                readEnum(input, AuctionTimeBasis.values(), "time basis"),
                readBoolean(input), input.readLong());
    }

    private static void writeBid(DataOutputStream output,
                                 AuctionBidStanding bid)
            throws IOException {
        writeUuid(output, bid.bidId());
        writeUuid(output, bid.bidderId());
        writeUuid(output, bid.holdAccountId());
        writeUuid(output, bid.holdTransactionId());
        output.writeLong(bid.amountMinor());
        output.writeLong(bid.acceptedSequence());
        output.writeLong(bid.acceptedAtMillis());
    }

    private static AuctionBidStanding readBid(DataInputStream input)
            throws IOException {
        return new AuctionBidStanding(readUuid(input), readUuid(input),
                readUuid(input), readUuid(input), input.readLong(),
                input.readLong(), input.readLong());
    }

    private static void writeSale(DataOutputStream output,
                                  AuctionSale sale) throws IOException {
        writeUuid(output, sale.buyerId());
        writeUuid(output, sale.holdAccountId());
        writeUuid(output, sale.holdTransactionId());
        writeUuid(output, sale.settlementTransactionId());
        output.writeLong(sale.priceMinor());
        output.writeLong(sale.soldAtMillis());
        output.writeBoolean(sale.buyout());
    }

    private static AuctionSale readSale(DataInputStream input)
            throws IOException {
        return new AuctionSale(readUuid(input), readUuid(input),
                readUuid(input), readUuid(input), input.readLong(),
                input.readLong(), readBoolean(input));
    }

    private static void writeResult(DataOutputStream output,
                                    AuctionOperationResult result)
            throws IOException {
        writeUuid(output, result.requestId());
        writeUuid(output, result.listingId());
        writeEnum(output, result.operation());
        writeEnum(output, result.status());
        output.writeBoolean(result.replayed());
        output.writeLong(result.observedRevision());
        output.writeLong(result.requiredHoldDeltaMinor());
        output.writeLong(result.refundMinor());
        output.writeLong(result.antiSnipeAddedMillis());
        writeOptional(output, result.refundPlayerId(),
                AuctionHouseSnapshotCodec::writeUuid);
        writeOptional(output, result.displacedBid(),
                AuctionHouseSnapshotCodec::writeBid);
        writeOptional(output, result.listing(),
                AuctionHouseSnapshotCodec::writeListing);
    }

    private static AuctionOperationResult readResult(DataInputStream input)
            throws IOException {
        return new AuctionOperationResult(readUuid(input), readUuid(input),
                readEnum(input, AuctionOperationType.values(),
                        "operation type"),
                readEnum(input, AuctionOperationStatus.values(),
                        "operation status"),
                readBoolean(input), input.readLong(), input.readLong(),
                input.readLong(), input.readLong(),
                readOptional(input, AuctionHouseSnapshotCodec::readUuid),
                readOptional(input, AuctionHouseSnapshotCodec::readBid),
                readOptional(input, AuctionHouseSnapshotCodec::readListing));
    }

    private static int readCount(DataInputStream input, int maximum,
                                 String label) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > maximum) {
            throw invalid("Auction snapshot " + label + " count is invalid");
        }
        return count;
    }

    private static void writeUuid(DataOutputStream output, UUID value)
            throws IOException {
        Objects.requireNonNull(value, "value");
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static void writeString(DataOutputStream output, String value,
                                    int maximum) throws IOException {
        writeString(output, value, maximum, false);
    }

    private static void writeStringAllowEmpty(DataOutputStream output,
                                              String value, int maximum)
            throws IOException {
        writeString(output, value, maximum, true);
    }

    private static void writeString(DataOutputStream output, String value,
                                    int maximum, boolean allowEmpty)
            throws IOException {
        byte[] bytes = encodeUtf8(value);
        if ((!allowEmpty && bytes.length == 0) || bytes.length > maximum) {
            throw invalid("Auction snapshot string size is invalid");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input, int maximum)
            throws IOException {
        return readString(input, maximum, false);
    }

    private static String readStringAllowEmpty(DataInputStream input,
                                               int maximum)
            throws IOException {
        return readString(input, maximum, true);
    }

    private static String readString(DataInputStream input, int maximum,
                                     boolean allowEmpty)
            throws IOException {
        int size = input.readInt();
        if (size < 0 || !allowEmpty && size == 0 || size > maximum
                || size > input.available()) {
            throw invalid("Auction snapshot string size is invalid");
        }
        byte[] bytes = input.readNBytes(size);
        if (bytes.length != size) {
            throw new EOFException("Auction snapshot string is truncated");
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw invalid("Auction snapshot string is not valid UTF8");
        }
    }

    private static byte[] encodeUtf8(String value) {
        Objects.requireNonNull(value, "value");
        try {
            ByteBuffer buffer = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(java.nio.CharBuffer.wrap(value));
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        } catch (CharacterCodingException exception) {
            throw invalid("Auction snapshot string is not valid Unicode");
        }
    }

    private static <E extends Enum<E>> void writeEnum(
            DataOutputStream output, E value) throws IOException {
        output.writeInt(Objects.requireNonNull(value, "value").ordinal());
    }

    private static <E extends Enum<E>> E readEnum(DataInputStream input,
                                                  E[] values,
                                                  String label)
            throws IOException {
        int ordinal = input.readInt();
        if (ordinal < 0 || ordinal >= values.length) {
            throw invalid("Auction snapshot " + label + " is invalid");
        }
        return values[ordinal];
    }

    private static boolean readBoolean(DataInputStream input)
            throws IOException {
        int value = input.readUnsignedByte();
        if (value != 0 && value != 1) {
            throw invalid("Auction snapshot boolean is invalid");
        }
        return value == 1;
    }

    private static <T> void writeOptional(DataOutputStream output,
                                          Optional<T> optional,
                                          Writer<T> writer)
            throws IOException {
        Optional<T> value = Objects.requireNonNull(optional, "optional");
        output.writeBoolean(value.isPresent());
        if (value.isPresent()) {
            writer.write(output, value.orElseThrow());
        }
    }

    private static <T> Optional<T> readOptional(DataInputStream input,
                                                Reader<T> reader)
            throws IOException {
        return readBoolean(input) ? Optional.of(reader.read(input))
                : Optional.empty();
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw invalid("Auction snapshot size is invalid");
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    @FunctionalInterface
    private interface Writer<T> {
        void write(DataOutputStream output, T value) throws IOException;
    }

    @FunctionalInterface
    private interface Reader<T> {
        T read(DataInputStream input) throws IOException;
    }

}
