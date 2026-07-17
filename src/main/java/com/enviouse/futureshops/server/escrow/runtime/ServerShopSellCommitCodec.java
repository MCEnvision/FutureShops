package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceiptCodec;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommand;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommandCodec;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionByteCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ServerShopSellCommitCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES =
            EscrowJournalEventCodec.MAX_BODY_BYTES;

    private static final int MAGIC = 0x46535353;
    private static final int MAX_TRANSACTION_BYTES = 8_388_608;
    private static final int MAX_LEDGER_BYTES = 65_536;
    private static final int MAX_STOCK_BYTES = 8_388_608;
    private static final int MAX_CLAIM_BYTES = 4_500_000;

    private ServerShopSellCommitCodec() {
    }

    public static byte[] encode(ServerShopSellCommit commit) {
        Objects.requireNonNull(commit, "commit");
        ServerShopSellConservationValidator.validate(commit);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            BinaryCodecSupport.writeUuid(output, commit.requestId());
            BinaryCodecSupport.writeUuid(output, commit.playerId());
            BinaryCodecSupport.writeString(output, commit.shopId(), 640);
            BinaryCodecSupport.writeString(output,
                    commit.listingId(), 640);
            BinaryCodecSupport.writeString(output, commit.itemId(), 640);
            output.writeInt(commit.quantity());
            output.writeLong(commit.unitPriceMinorUnits());
            output.writeLong(commit.quoteRevision());
            output.writeLong(commit.expectedStockRevision());
            writeInstant(output, commit.quoteCreatedAt());
            output.writeLong(commit.walletBeforeMinorUnits());
            output.writeLong(commit.debtBeforeMinorUnits());
            output.writeLong(commit.reservedBeforeMinorUnits());
            output.writeLong(commit.walletBalanceLimitMinorUnits());
            output.writeLong(commit.configurationGeneration());
            BinaryCodecSupport.writeString(output,
                    commit.currencyName(), 512);
            output.writeInt(commit.currencyDecimals());
            writeComponent(output, commit.exactItemTemplate(),
                    ItemStackSnapshotCodec.MAXIMUM_BYTES,
                    "Server shop sell item template");
            writeComponent(output,
                    ItemInventoryMutationReceiptCodec.encode(
                            commit.itemCustodyReceipt()),
                    ItemInventoryMutationReceiptCodec.MAX_ENCODED_BYTES,
                    "Server shop sell item custody");
            writeComponent(output, EscrowTransactionByteCodec.encode(
                            commit.completedTransaction()),
                    MAX_TRANSACTION_BYTES,
                    "Server shop sell transaction");
            writeComponent(output, LedgerJournalCodec.encode(
                            commit.ledgerTransaction()),
                    MAX_LEDGER_BYTES, "Server shop sell ledger");
            writeComponent(output, StockMutationCommandCodec.encode(
                            commit.stockReservation()),
                    MAX_STOCK_BYTES,
                    "Server shop sell stock reservation");
            writeComponent(output, StockMutationCommandCodec.encode(
                            commit.stockCommit()),
                    MAX_STOCK_BYTES, "Server shop sell stock commit");
            output.writeBoolean(commit.overflowClaim().isPresent());
            if (commit.overflowClaim().isPresent()) {
                writeComponent(output, ClaimJournalCodec.encodeClaim(
                                commit.overflowClaim().orElseThrow()),
                        MAX_CLAIM_BYTES,
                        "Server shop sell overflow claim");
            }
            output.flush();
            byte[] encoded = bytes.toByteArray();
            requireSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode server shop sell commit",
                    exception);
        }
    }

    public static ServerShopSellCommit decode(byte[] encoded) {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        requireSize(copy);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(copy))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Server shop sell commit magic is invalid");
            }
            if (input.readInt() != CURRENT_SCHEMA) {
                throw new IllegalArgumentException(
                        "Server shop sell commit schema is unsupported");
            }
            UUID requestId = BinaryCodecSupport.readUuid(input);
            UUID playerId = BinaryCodecSupport.readUuid(input);
            String shopId = BinaryCodecSupport.readString(input, 640);
            String listingId = BinaryCodecSupport.readString(input, 640);
            String itemId = BinaryCodecSupport.readString(input, 640);
            int quantity = input.readInt();
            long unitPrice = input.readLong();
            long quoteRevision = input.readLong();
            long stockRevision = input.readLong();
            Instant quoteCreatedAt = readInstant(input);
            long wallet = input.readLong();
            long debt = input.readLong();
            long reserved = input.readLong();
            long walletLimit = input.readLong();
            long configurationGeneration = input.readLong();
            String currencyName = BinaryCodecSupport.readString(
                    input, 512);
            int currencyDecimals = input.readInt();
            byte[] exactTemplate = readComponent(input,
                    ItemStackSnapshotCodec.MAXIMUM_BYTES,
                    "Server shop sell item template");
            ItemInventoryMutationReceipt custody =
                    ItemInventoryMutationReceiptCodec.decode(
                            readComponent(input,
                                    ItemInventoryMutationReceiptCodec
                                            .MAX_ENCODED_BYTES,
                                    "Server shop sell item custody"));
            EscrowTransaction transaction =
                    EscrowTransactionByteCodec.decode(readComponent(input,
                            MAX_TRANSACTION_BYTES,
                            "Server shop sell transaction"));
            com.enviouse.futureshops.server.escrow.ledger
                    .LedgerTransaction ledger = LedgerJournalCodec.decode(
                    readComponent(input, MAX_LEDGER_BYTES,
                            "Server shop sell ledger"));
            StockMutationCommand reserveCommand =
                    StockMutationCommandCodec.decode(readComponent(input,
                            MAX_STOCK_BYTES,
                            "Server shop sell stock reservation"));
            StockMutationCommand commitCommand =
                    StockMutationCommandCodec.decode(readComponent(input,
                            MAX_STOCK_BYTES,
                            "Server shop sell stock commit"));
            if (!(reserveCommand
                    instanceof StockMutationCommand.ReserveBatch reserve)
                    || !(commitCommand
                    instanceof StockMutationCommand.ResolveBatch commit)) {
                throw new IllegalArgumentException(
                        "Server shop sell stock command shape is invalid");
            }
            Optional<EscrowClaim> claim =
                    BinaryCodecSupport.readBoolean(input)
                            ? Optional.of(ClaimJournalCodec.decodeClaim(
                            readComponent(input, MAX_CLAIM_BYTES,
                                    "Server shop sell overflow claim")))
                            : Optional.empty();
            if (input.read() != -1) {
                throw new IllegalArgumentException(
                        "Server shop sell commit has trailing data");
            }
            ServerShopSellCommit decoded = new ServerShopSellCommit(
                    requestId, playerId, shopId, listingId, itemId,
                    quantity, unitPrice, quoteRevision, stockRevision,
                    quoteCreatedAt, wallet, debt, reserved, walletLimit,
                    configurationGeneration, currencyName,
                    currencyDecimals, exactTemplate, custody, transaction,
                    ledger, reserve, commit, claim);
            if (!Arrays.equals(copy, encode(decoded))) {
                throw new IllegalArgumentException(
                        "Server shop sell commit encoding is not canonical");
            }
            return decoded;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Server shop sell commit is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException(
                    "Server shop sell commit is invalid", exception);
        }
    }

    private static void writeInstant(
            DataOutputStream output,
            Instant value
    ) throws IOException {
        output.writeLong(value.getEpochSecond());
        output.writeInt(value.getNano());
    }

    private static Instant readInstant(DataInputStream input)
            throws IOException {
        long seconds = input.readLong();
        int nanos = input.readInt();
        if (nanos < 0 || nanos > 999_999_999) {
            throw new IllegalArgumentException(
                    "Server shop sell instant is invalid");
        }
        return Instant.ofEpochSecond(seconds, nanos);
    }

    private static void writeComponent(
            DataOutputStream output,
            byte[] component,
            int maximum,
            String label
    ) throws IOException {
        if (component.length == 0 || component.length > maximum) {
            throw new IllegalArgumentException(label + " is too large");
        }
        output.writeInt(component.length);
        output.write(component);
    }

    private static byte[] readComponent(
            DataInputStream input,
            int maximum,
            String label
    ) throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > maximum
                || length > input.available()) {
            throw new IllegalArgumentException(label + " size is invalid");
        }
        byte[] value = input.readNBytes(length);
        if (value.length != length) {
            throw new EOFException(label + " is truncated");
        }
        return value;
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Server shop sell commit size is invalid");
        }
    }
}
