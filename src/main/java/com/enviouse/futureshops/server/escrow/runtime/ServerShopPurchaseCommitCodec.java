package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayload;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayloadCodec;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ServerShopPurchaseCommitCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES =
            EscrowJournalEventCodec.MAX_BODY_BYTES;

    private static final int MAGIC = 0x46535350;
    private static final int MAX_TRANSACTION_BYTES = 8_388_608;
    private static final int MAX_LEDGER_BYTES = 65_536;
    private static final int MAX_STOCK_BYTES = 8_388_608;
    private static final int MAX_CLAIM_BYTES = 4_500_000;

    private ServerShopPurchaseCommitCodec() {
    }

    public static byte[] encode(ServerShopPurchaseCommit commit) {
        Objects.requireNonNull(commit, "commit");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            BinaryCodecSupport.writeUuid(output, commit.requestId());
            BinaryCodecSupport.writeUuid(output, commit.playerId());
            BinaryCodecSupport.writeString(output, commit.shopId(), 640);
            output.writeBoolean(commit.cartCheckout());
            BinaryCodecSupport.writeString(output,
                    commit.paymentSource().name(), 64);
            output.writeLong(commit.walletBeforeMinorUnits());
            output.writeLong(commit.debtBeforeMinorUnits());
            BinaryCodecSupport.writeString(output,
                    commit.currencyName(), 512);
            output.writeInt(commit.currencyDecimals());
            output.writeInt(commit.lines().size());
            for (ServerShopPurchaseCommit.Line line : commit.lines()) {
                output.writeInt(line.lineIndex());
                BinaryCodecSupport.writeString(output,
                        line.listingId(), 640);
                BinaryCodecSupport.writeString(output, line.itemId(), 640);
                output.writeInt(line.quantity());
                output.writeLong(line.lineCostMinorUnits());
                output.writeLong(line.expectedStockRevision());
                output.writeInt(line.outputs().size());
                for (ExactItemClaimPayload value : line.outputs()) {
                    writeComponent(output,
                            ExactItemClaimPayloadCodec.encode(value),
                            ExactItemClaimPayloadCodec.MAX_ENCODED_BYTES,
                            "Server shop output");
                }
            }
            writeComponent(output, EscrowTransactionByteCodec.encode(
                    commit.completedTransaction()),
                    MAX_TRANSACTION_BYTES, "Server shop transaction");
            output.writeInt(commit.completedLineTransactions().size());
            for (EscrowTransaction transaction
                    : commit.completedLineTransactions()) {
                writeComponent(output,
                        EscrowTransactionByteCodec.encode(transaction),
                        MAX_TRANSACTION_BYTES,
                        "Server shop line transaction");
            }
            writeComponent(output, LedgerJournalCodec.encode(
                    commit.ledgerTransaction()), MAX_LEDGER_BYTES,
                    "Server shop ledger");
            writeComponent(output, StockMutationCommandCodec.encode(
                    commit.stockReservation()), MAX_STOCK_BYTES,
                    "Server shop stock reservation");
            writeComponent(output, StockMutationCommandCodec.encode(
                    commit.stockCommit()), MAX_STOCK_BYTES,
                    "Server shop stock commit");
            output.writeInt(commit.itemClaims().size());
            for (EscrowClaim claim : commit.itemClaims()) {
                writeComponent(output, ClaimJournalCodec.encodeClaim(claim),
                        MAX_CLAIM_BYTES, "Server shop item claim");
            }
            output.flush();
            byte[] encoded = bytes.toByteArray();
            requireSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode server shop purchase commit",
                    exception);
        }
    }

    public static ServerShopPurchaseCommit decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        requireSize(encoded);
        ByteArrayInputStream bytes = new ByteArrayInputStream(encoded);
        try (DataInputStream input = new DataInputStream(bytes)) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Server shop purchase commit magic is invalid");
            }
            if (input.readInt() != CURRENT_SCHEMA) {
                throw new IllegalArgumentException(
                        "Server shop purchase commit schema is unsupported");
            }
            java.util.UUID requestId = BinaryCodecSupport.readUuid(input);
            java.util.UUID playerId = BinaryCodecSupport.readUuid(input);
            String shopId = BinaryCodecSupport.readString(input, 640);
            boolean cartCheckout = BinaryCodecSupport.readBoolean(input);
            PaymentSource paymentSource;
            try {
                paymentSource = PaymentSource.valueOf(
                        BinaryCodecSupport.readString(input, 64));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Server shop payment source is invalid", exception);
            }
            long walletBefore = input.readLong();
            long debtBefore = input.readLong();
            String currencyName = BinaryCodecSupport.readString(input, 512);
            int currencyDecimals = input.readInt();
            int lineCount = readCount(input,
                    ServerShopPurchaseCommit.MAX_LINES, false,
                    "Server shop line count");
            List<ServerShopPurchaseCommit.Line> lines =
                    new ArrayList<>(lineCount);
            int totalOutputs = 0;
            for (int index = 0; index < lineCount; index++) {
                int lineIndex = input.readInt();
                String listingId = BinaryCodecSupport.readString(input, 640);
                String itemId = BinaryCodecSupport.readString(input, 640);
                int quantity = input.readInt();
                long lineCost = input.readLong();
                long stockRevision = input.readLong();
                int outputCount = readCount(input,
                        ExactItemClaimPayload.MAX_PORTIONS, false,
                        "Server shop output count");
                totalOutputs = Math.addExact(totalOutputs, outputCount);
                if (totalOutputs > ExactItemClaimPayload.MAX_PORTIONS) {
                    throw new IllegalArgumentException(
                            "Server shop total output count is invalid");
                }
                List<ExactItemClaimPayload> outputs =
                        new ArrayList<>(outputCount);
                for (int outputIndex = 0;
                     outputIndex < outputCount; outputIndex++) {
                    outputs.add(ExactItemClaimPayloadCodec.decode(
                            readComponent(input,
                                    ExactItemClaimPayloadCodec
                                            .MAX_ENCODED_BYTES,
                                    "Server shop output")));
                }
                lines.add(new ServerShopPurchaseCommit.Line(lineIndex,
                        listingId, itemId, quantity, lineCost,
                        stockRevision, outputs));
            }
            EscrowTransaction parent = EscrowTransactionByteCodec.decode(
                    readComponent(input, MAX_TRANSACTION_BYTES,
                            "Server shop transaction"));
            int childCount = readCount(input,
                    ServerShopPurchaseCommit.MAX_LINES, true,
                    "Server shop child transaction count");
            List<EscrowTransaction> children = new ArrayList<>(childCount);
            for (int index = 0; index < childCount; index++) {
                children.add(EscrowTransactionByteCodec.decode(
                        readComponent(input, MAX_TRANSACTION_BYTES,
                                "Server shop line transaction")));
            }
            com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction
                    ledger = LedgerJournalCodec.decode(readComponent(input,
                    MAX_LEDGER_BYTES, "Server shop ledger"));
            StockMutationCommand reserveCommand =
                    StockMutationCommandCodec.decode(readComponent(input,
                            MAX_STOCK_BYTES,
                            "Server shop stock reservation"));
            StockMutationCommand commitCommand =
                    StockMutationCommandCodec.decode(readComponent(input,
                            MAX_STOCK_BYTES,
                            "Server shop stock commit"));
            if (!(reserveCommand
                    instanceof StockMutationCommand.ReserveBatch reserve)
                    || !(commitCommand
                    instanceof StockMutationCommand.ResolveBatch commit)) {
                throw new IllegalArgumentException(
                        "Server shop stock command shape is invalid");
            }
            int claimCount = readCount(input,
                    ExactItemClaimPayload.MAX_PORTIONS, false,
                    "Server shop claim count");
            List<EscrowClaim> claims = new ArrayList<>(claimCount);
            for (int index = 0; index < claimCount; index++) {
                claims.add(ClaimJournalCodec.decodeClaim(readComponent(input,
                        MAX_CLAIM_BYTES, "Server shop item claim")));
            }
            if (input.read() != -1) {
                throw new IllegalArgumentException(
                        "Server shop purchase commit has trailing data");
            }
            return new ServerShopPurchaseCommit(requestId, playerId, shopId,
                    cartCheckout, paymentSource, walletBefore, debtBefore,
                    currencyName, currencyDecimals, lines, parent, children,
                    ledger, reserve, commit, claims);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Server shop purchase commit is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Server shop purchase commit is invalid", exception);
        }
    }

    private static int readCount(
            DataInputStream input,
            int maximum,
            boolean allowEmpty,
            String label
    ) throws IOException {
        int count = input.readInt();
        if (count < (allowEmpty ? 0 : 1) || count > maximum) {
            throw new IllegalArgumentException(label + " is invalid");
        }
        return count;
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
        if (length <= 0 || length > maximum || length > input.available()) {
            throw new IllegalArgumentException(label + " size is invalid");
        }
        return input.readNBytes(length);
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Server shop purchase commit size is invalid");
        }
    }
}
