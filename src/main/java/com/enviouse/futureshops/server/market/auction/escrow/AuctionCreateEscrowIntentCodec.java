package com.enviouse.futureshops.server.market.auction.escrow;

import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayload;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayloadCodec;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceiptCodec;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryMutationIntent;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryMutationIntentCodec;
import com.enviouse.futureshops.server.escrow.runtime.EscrowJournalEventCodec;
import com.enviouse.futureshops.server.market.auction.AuctionItemLot;
import com.enviouse.futureshops.server.market.auction.AuctionListingType;
import com.enviouse.futureshops.server.market.auction.AuctionRuleSnapshot;
import com.enviouse.futureshops.server.market.auction.AuctionTimeBasis;
import com.enviouse.futureshops.server.market.auction.CreateAuctionCommand;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class AuctionCreateEscrowIntentCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES =
            EscrowJournalEventCodec.MAX_BODY_BYTES - 64;

    private static final int MAGIC = 0x4143494E;
    private static final int MAX_TEXT_BYTES = 16_384;

    private AuctionCreateEscrowIntentCodec() {
    }

    public static byte[] encode(AuctionCreateEscrowIntent intent) {
        Objects.requireNonNull(intent, "intent");
        byte[] core = coreBytes(intent);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            writeBytes(output, core, MAX_ENCODED_BYTES);
            output.writeInt(intent.status().ordinal());
            output.writeLong(intent.revision());
            writeText(output, fingerprint(intent));
            output.flush();
            byte[] encoded = bytes.toByteArray();
            requireSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode auction creation intent", exception);
        }
    }

    public static AuctionCreateEscrowIntent decode(byte[] encoded) {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        requireSize(copy);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(copy))) {
            if (input.readInt() != MAGIC) {
                throw invalid("Auction creation intent magic is invalid");
            }
            if (input.readInt() != CURRENT_SCHEMA) {
                throw invalid("Auction creation intent schema is unsupported");
            }
            byte[] core = readBytes(input, MAX_ENCODED_BYTES);
            int statusIndex = input.readInt();
            if (statusIndex < 0 || statusIndex
                    >= AuctionCreateEscrowIntent.Status.values().length) {
                throw invalid("Auction creation intent status is invalid");
            }
            long revision = input.readLong();
            String fingerprint = readText(input, 64);
            if (input.read() != -1) {
                throw invalid("Auction creation intent has trailing data");
            }
            Core decoded = decodeCore(core);
            AuctionCreateEscrowIntent result = new AuctionCreateEscrowIntent(
                    decoded.command(), decoded.itemIntent(),
                    decoded.custody(), decoded.wallet(),
                    decoded.currencyId(), decoded.preparedAt(),
                    AuctionCreateEscrowIntent.Status.values()[statusIndex],
                    revision);
            if (!fingerprint.equals(fingerprint(result))
                    || !Arrays.equals(copy, encode(result))) {
                throw invalid(
                        "Auction creation intent encoding is not canonical");
            }
            return result;
        } catch (EOFException exception) {
            throw invalid("Auction creation intent is truncated",
                    exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw invalid("Auction creation intent is invalid", exception);
        }
    }

    static String fingerprint(AuctionCreateEscrowIntent intent) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(coreBytes(intent)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable",
                    exception);
        }
    }

    private static byte[] coreBytes(AuctionCreateEscrowIntent intent) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            writeCommand(output, intent.command());
            writeBytes(output, ItemInventoryMutationIntentCodec.encode(
                    intent.itemMutationIntent()),
                    ItemInventoryMutationIntentCodec.MAX_ENCODED_BYTES);
            writeCustody(output, intent.plannedCustody());
            writeWallet(output, intent.sellerWallet());
            writeText(output, intent.currencyId());
            writeInstant(output, intent.preparedAt());
            output.flush();
            byte[] result = bytes.toByteArray();
            if (result.length == 0 || result.length > MAX_ENCODED_BYTES) {
                throw invalid("Auction creation intent core is too large");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode auction creation intent core",
                    exception);
        }
    }

    private static Core decodeCore(byte[] encoded) throws IOException {
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(encoded))) {
            CreateAuctionCommand command = readCommand(input);
            ItemInventoryMutationIntent itemIntent =
                    ItemInventoryMutationIntentCodec.decode(
                            readBytes(input,
                                    ItemInventoryMutationIntentCodec
                                            .MAX_ENCODED_BYTES));
            AuctionEscrowItemCustody custody = readCustody(input);
            AuctionEscrowWalletSnapshot wallet = readWallet(input);
            String currencyId = readText(input,
                    AuctionCreateEscrowIntent.MAX_CURRENCY_ID_LENGTH);
            Instant preparedAt = readInstant(input);
            if (input.read() != -1) {
                throw invalid(
                        "Auction creation intent core has trailing data");
            }
            return new Core(command, itemIntent, custody, wallet,
                    currencyId, preparedAt);
        }
    }

    private static void writeCommand(
            DataOutputStream output,
            CreateAuctionCommand command
    ) throws IOException {
        writeUuid(output, command.requestId());
        writeUuid(output, command.listingId());
        writeUuid(output, command.sellerId());
        writeUuid(output, command.activationTransactionId());
        AuctionItemLot lot = command.itemLot();
        writeUuid(output, lot.custodyLotId());
        writeText(output, lot.registryId());
        writeText(output, lot.fingerprint());
        output.writeInt(lot.count());
        output.writeInt(lot.serializedBytes());
        writeText(output, lot.categoryId());
        writeText(output, lot.searchDocument());
        output.writeInt(command.type().ordinal());
        output.writeLong(command.startingBidMinor());
        output.writeLong(command.buyoutMinor());
        writeRules(output, command.rules());
        output.writeLong(command.createdAtMillis());
        output.writeLong(command.deadlineMillis());
    }

    private static CreateAuctionCommand readCommand(DataInputStream input)
            throws IOException {
        UUID requestId = readUuid(input);
        UUID listingId = readUuid(input);
        UUID sellerId = readUuid(input);
        UUID activationId = readUuid(input);
        AuctionItemLot lot = new AuctionItemLot(readUuid(input),
                readText(input, 256), readText(input, 64),
                input.readInt(), input.readInt(),
                readText(input, 128), readText(input, 4096));
        int typeIndex = input.readInt();
        if (typeIndex < 0 || typeIndex
                >= AuctionListingType.values().length) {
            throw invalid("Auction creation listing type is invalid");
        }
        long startingBid = input.readLong();
        long buyout = input.readLong();
        AuctionRuleSnapshot rules = readRules(input);
        return new CreateAuctionCommand(requestId, listingId, sellerId,
                activationId, lot, AuctionListingType.values()[typeIndex],
                startingBid, buyout, rules, input.readLong(),
                input.readLong());
    }

    private static void writeRules(
            DataOutputStream output,
            AuctionRuleSnapshot rules
    ) throws IOException {
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
        output.writeInt(rules.timeBasis().ordinal());
        output.writeBoolean(rules.pauseWhileFrozen());
        output.writeLong(rules.configRevision());
    }

    private static AuctionRuleSnapshot readRules(DataInputStream input)
            throws IOException {
        long listingFee = input.readLong();
        int tax = input.readInt();
        long increment = input.readLong();
        int incrementBps = input.readInt();
        boolean antiSnipe = input.readBoolean();
        long trigger = input.readLong();
        long extension = input.readLong();
        long maximumExtension = input.readLong();
        int maximumCount = input.readInt();
        boolean cancel = input.readBoolean();
        int timeIndex = input.readInt();
        if (timeIndex < 0 || timeIndex
                >= AuctionTimeBasis.values().length) {
            throw invalid("Auction creation time basis is invalid");
        }
        return new AuctionRuleSnapshot(listingFee, tax, increment,
                incrementBps, antiSnipe, trigger, extension,
                maximumExtension, maximumCount, cancel,
                AuctionTimeBasis.values()[timeIndex],
                input.readBoolean(), input.readLong());
    }

    static void writeCustody(
            DataOutputStream output,
            AuctionEscrowItemCustody custody
    ) throws IOException {
        writeUuid(output, custody.listingId());
        writeUuid(output, custody.activationTransactionId());
        writeBytes(output, ItemInventoryMutationReceiptCodec.encode(
                custody.receipt()),
                ItemInventoryMutationReceiptCodec.MAX_ENCODED_BYTES);
        output.writeInt(custody.exactItems().size());
        for (ExactItemClaimPayload item : custody.exactItems()) {
            writeBytes(output, ExactItemClaimPayloadCodec.encode(item),
                    ExactItemClaimPayloadCodec.MAX_ENCODED_BYTES);
        }
    }

    static AuctionEscrowItemCustody readCustody(
            DataInputStream input
    ) throws IOException {
        UUID listingId = readUuid(input);
        UUID activationId = readUuid(input);
        ItemInventoryMutationReceipt receipt =
                ItemInventoryMutationReceiptCodec.decode(readBytes(input,
                        ItemInventoryMutationReceiptCodec
                                .MAX_ENCODED_BYTES));
        int count = input.readInt();
        if (count <= 0 || count
                > ItemInventoryMutationReceipt.MAX_ALLOCATIONS) {
            throw invalid("Auction custody item count is invalid");
        }
        List<ExactItemClaimPayload> items = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            items.add(ExactItemClaimPayloadCodec.decode(readBytes(input,
                    ExactItemClaimPayloadCodec.MAX_ENCODED_BYTES)));
        }
        return new AuctionEscrowItemCustody(listingId, activationId,
                receipt, items);
    }

    private static void writeWallet(
            DataOutputStream output,
            AuctionEscrowWalletSnapshot wallet
    ) throws IOException {
        writeUuid(output, wallet.playerId());
        output.writeLong(wallet.walletMinor());
        output.writeLong(wallet.debtMinor());
        output.writeLong(wallet.reservedMinor());
        output.writeLong(wallet.walletLimitMinor());
        output.writeLong(wallet.configurationGeneration());
    }

    private static AuctionEscrowWalletSnapshot readWallet(
            DataInputStream input
    ) throws IOException {
        return new AuctionEscrowWalletSnapshot(readUuid(input),
                input.readLong(), input.readLong(), input.readLong(),
                input.readLong(), input.readLong());
    }

    private static void writeUuid(DataOutputStream output, UUID value)
            throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static void writeInstant(DataOutputStream output, Instant value)
            throws IOException {
        output.writeLong(value.getEpochSecond());
        output.writeInt(value.getNano());
    }

    private static Instant readInstant(DataInputStream input)
            throws IOException {
        long seconds = input.readLong();
        int nanos = input.readInt();
        if (nanos < 0 || nanos > 999_999_999) {
            throw invalid("Auction creation instant is invalid");
        }
        try {
            return Instant.ofEpochSecond(seconds, nanos);
        } catch (DateTimeException exception) {
            throw invalid("Auction creation instant is invalid",
                    exception);
        }
    }

    private static void writeText(DataOutputStream output, String value)
            throws IOException {
        byte[] bytes = Objects.requireNonNull(value, "value")
                .getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES) {
            throw invalid("Auction creation text is too large");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readText(DataInputStream input, int maximum)
            throws IOException {
        int length = input.readInt();
        int maximumBytes = Math.multiplyExact(maximum, 4);
        if (length < 0 || length > maximumBytes
                || length > input.available()) {
            throw invalid("Auction creation text size is invalid");
        }
        byte[] bytes = input.readNBytes(length);
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (!Arrays.equals(bytes, value.getBytes(StandardCharsets.UTF_8))) {
            throw invalid("Auction creation text is not valid UTF8");
        }
        return value;
    }

    private static void writeBytes(
            DataOutputStream output,
            byte[] value,
            int maximum
    ) throws IOException {
        if (value.length == 0 || value.length > maximum) {
            throw invalid("Auction creation component size is invalid");
        }
        output.writeInt(value.length);
        output.write(value);
    }

    private static byte[] readBytes(DataInputStream input, int maximum)
            throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > maximum
                || length > input.available()) {
            throw invalid("Auction creation component size is invalid");
        }
        byte[] result = input.readNBytes(length);
        if (result.length != length) {
            throw new EOFException(
                    "Auction creation component is truncated");
        }
        return result;
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw invalid("Auction creation intent size is invalid");
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

    private record Core(
            CreateAuctionCommand command,
            ItemInventoryMutationIntent itemIntent,
            AuctionEscrowItemCustody custody,
            AuctionEscrowWalletSnapshot wallet,
            String currencyId,
            Instant preparedAt
    ) {
    }
}
