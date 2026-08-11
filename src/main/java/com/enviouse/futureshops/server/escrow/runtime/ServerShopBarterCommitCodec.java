package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayload;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayloadCodec;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceiptCodec;
import com.enviouse.futureshops.server.escrow.item.ItemMatchMode;
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
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ServerShopBarterCommitCodec {
    public static final int CURRENT_SCHEMA = 2;
    public static final int MAX_ENCODED_BYTES =
            EscrowJournalEventCodec.MAX_BODY_BYTES;

    private static final int MAGIC = 0x46534252;
    private static final int MAX_IDENTIFIER_BYTES =
            ServerShopBarterCommit.MAX_IDENTIFIER_LENGTH * 4;
    private static final int MAX_CLAIM_BYTES =
            EscrowJournalEventCodec.MAX_BODY_BYTES;

    private ServerShopBarterCommitCodec() {
    }

    public static byte[] encode(ServerShopBarterCommit commit) {
        Objects.requireNonNull(commit, "commit");
        ServerShopBarterConservationValidator.validate(commit);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(
                    new BoundedOutput(bytes, MAX_ENCODED_BYTES));
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            BinaryCodecSupport.writeUuid(output, commit.requestId());
            BinaryCodecSupport.writeUuid(output, commit.playerId());
            BinaryCodecSupport.writeString(output, commit.shopId(),
                    MAX_IDENTIFIER_BYTES);
            BinaryCodecSupport.writeString(output, commit.recipeId(),
                    MAX_IDENTIFIER_BYTES);
            output.writeInt(commit.multiplier());
            output.writeLong(commit.quoteRevision());
            output.writeLong(commit.recipeRevision());
            writeInstant(output, commit.quoteCreatedAt());
            output.writeInt(commit.ingredients().size());
            for (ServerShopBarterCommit.Ingredient ingredient
                    : commit.ingredients()) {
                output.writeInt(ingredient.ingredientIndex());
                BinaryCodecSupport.writeString(output,
                        ingredient.ingredientId(), MAX_IDENTIFIER_BYTES);
                BinaryCodecSupport.writeString(output,
                        ingredient.itemId(), MAX_IDENTIFIER_BYTES);
                output.writeInt(ingredient.quantityPerTrade());
                output.writeByte(
                        ingredient.matchMode().fingerprintCode());
                writeComponent(output, ingredient.exactItemTemplate(),
                        ItemStackSnapshotCodec.MAXIMUM_BYTES,
                        "Server shop barter ingredient template");
            }
            output.writeInt(commit.outputs().size());
            for (ServerShopBarterCommit.OutputLine line
                    : commit.outputs()) {
                output.writeInt(line.outputIndex());
                BinaryCodecSupport.writeString(output, line.listingId(),
                        MAX_IDENTIFIER_BYTES);
                BinaryCodecSupport.writeString(output, line.itemId(),
                        MAX_IDENTIFIER_BYTES);
                output.writeInt(line.quantityPerTrade());
                output.writeLong(line.expectedStockRevision());
                output.writeInt(line.portions().size());
                for (ExactItemClaimPayload portion : line.portions()) {
                    writeComponent(output,
                            ExactItemClaimPayloadCodec.encode(portion),
                            ExactItemClaimPayloadCodec.MAX_ENCODED_BYTES,
                            "Server shop barter output portion");
                }
            }
            writeComponent(output,
                    ItemInventoryMutationReceiptCodec.encode(
                            commit.ingredientCustodyReceipt()),
                    ItemInventoryMutationReceiptCodec.MAX_ENCODED_BYTES,
                    "Server shop barter ingredient custody");
            writeComponent(output, EscrowTransactionByteCodec.encode(
                            commit.completedTransaction()),
                    EscrowTransactionByteCodec.MAX_ENCODED_BYTES,
                    "Server shop barter transaction");
            writeComponent(output, StockMutationCommandCodec.encode(
                            commit.stockReservation()),
                    StockMutationCommandCodec.MAX_ENCODED_BYTES,
                    "Server shop barter stock reservation");
            writeComponent(output, StockMutationCommandCodec.encode(
                            commit.stockCommit()),
                    StockMutationCommandCodec.MAX_ENCODED_BYTES,
                    "Server shop barter stock commit");
            output.writeInt(commit.outputClaims().size());
            for (EscrowClaim claim : commit.outputClaims()) {
                writeComponent(output,
                        ClaimJournalCodec.encodeClaim(claim),
                        MAX_CLAIM_BYTES,
                        "Server shop barter output claim");
            }
            output.flush();
            byte[] encoded = bytes.toByteArray();
            requireSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode server shop barter commit",
                    exception);
        }
    }

    public static ServerShopBarterCommit decode(byte[] encoded) {
        byte[] copy = Objects.requireNonNull(
                encoded, "encoded").clone();
        requireSize(copy);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(copy))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Server shop barter commit magic is invalid");
            }
            int schema = input.readInt();
            if (schema < 1 || schema > CURRENT_SCHEMA) {
                throw new IllegalArgumentException(
                        "Server shop barter commit schema is unsupported");
            }
            UUID requestId = BinaryCodecSupport.readUuid(input);
            UUID playerId = BinaryCodecSupport.readUuid(input);
            String shopId = BinaryCodecSupport.readString(input,
                    MAX_IDENTIFIER_BYTES);
            String recipeId = BinaryCodecSupport.readString(input,
                    MAX_IDENTIFIER_BYTES);
            int multiplier = input.readInt();
            long quoteRevision = input.readLong();
            long recipeRevision = input.readLong();
            Instant quoteCreatedAt = readInstant(input);
            int ingredientCount = readCount(input,
                    ServerShopBarterCommit.MAX_INGREDIENTS,
                    "ingredient");
            List<ServerShopBarterCommit.Ingredient> ingredients =
                    new ArrayList<>(ingredientCount);
            for (int index = 0; index < ingredientCount; index++) {
                int ingredientIndex = input.readInt();
                String ingredientId = BinaryCodecSupport.readString(
                        input, MAX_IDENTIFIER_BYTES);
                String itemId = BinaryCodecSupport.readString(input,
                        MAX_IDENTIFIER_BYTES);
                int quantity = input.readInt();
                ItemMatchMode matchMode = schema >= 2
                        ? ItemMatchMode.fromFingerprintCode(
                        input.readUnsignedByte()) : null;
                byte[] template = readComponent(input,
                        ItemStackSnapshotCodec.MAXIMUM_BYTES,
                        "Server shop barter ingredient template");
                ingredients.add(schema >= 2
                        ? new ServerShopBarterCommit.Ingredient(
                        ingredientIndex, ingredientId, itemId, quantity,
                        matchMode, template)
                        : new ServerShopBarterCommit.Ingredient(
                        ingredientIndex, ingredientId, itemId, quantity,
                        template));
            }
            int outputCount = readCount(input,
                    ServerShopBarterCommit.MAX_OUTPUT_LINES, "output");
            List<ServerShopBarterCommit.OutputLine> outputs =
                    new ArrayList<>(outputCount);
            int totalPortions = 0;
            for (int index = 0; index < outputCount; index++) {
                int outputIndex = input.readInt();
                String listingId = BinaryCodecSupport.readString(input,
                        MAX_IDENTIFIER_BYTES);
                String itemId = BinaryCodecSupport.readString(input,
                        MAX_IDENTIFIER_BYTES);
                int quantity = input.readInt();
                long stockRevision = input.readLong();
                int portionCount = readCount(input,
                        ExactItemClaimPayload.MAX_PORTIONS,
                        "output portion");
                totalPortions = Math.addExact(totalPortions,
                        portionCount);
                if (totalPortions
                        > ServerShopBarterCommit.MAX_TOTAL_OUTPUT_PORTIONS) {
                    throw new IllegalArgumentException(
                            "Server shop barter output portions exceed their limit");
                }
                List<ExactItemClaimPayload> portions = new ArrayList<>(
                        portionCount);
                for (int portionIndex = 0;
                     portionIndex < portionCount; portionIndex++) {
                    portions.add(ExactItemClaimPayloadCodec.decode(
                            readComponent(input,
                                    ExactItemClaimPayloadCodec
                                            .MAX_ENCODED_BYTES,
                                    "Server shop barter output portion")));
                }
                outputs.add(new ServerShopBarterCommit.OutputLine(
                        outputIndex, listingId, itemId, quantity,
                        stockRevision, portions));
            }
            ItemInventoryMutationReceipt custody =
                    ItemInventoryMutationReceiptCodec.decode(
                            readComponent(input,
                                    ItemInventoryMutationReceiptCodec
                                            .MAX_ENCODED_BYTES,
                                    "Server shop barter ingredient custody"));
            EscrowTransaction transaction =
                    EscrowTransactionByteCodec.decode(readComponent(input,
                            EscrowTransactionByteCodec.MAX_ENCODED_BYTES,
                            "Server shop barter transaction"));
            StockMutationCommand reserveCommand =
                    StockMutationCommandCodec.decode(readComponent(input,
                            StockMutationCommandCodec.MAX_ENCODED_BYTES,
                            "Server shop barter stock reservation"));
            StockMutationCommand commitCommand =
                    StockMutationCommandCodec.decode(readComponent(input,
                            StockMutationCommandCodec.MAX_ENCODED_BYTES,
                            "Server shop barter stock commit"));
            if (!(reserveCommand
                    instanceof StockMutationCommand.ReserveBatch reserve)
                    || !(commitCommand
                    instanceof StockMutationCommand.ResolveBatch commit)) {
                throw new IllegalArgumentException(
                        "Server shop barter stock command shape is invalid");
            }
            int claimCount = readCount(input,
                    ServerShopBarterCommit.MAX_TOTAL_OUTPUT_PORTIONS,
                    "output claim");
            List<EscrowClaim> claims = new ArrayList<>(claimCount);
            for (int index = 0; index < claimCount; index++) {
                claims.add(ClaimJournalCodec.decodeClaim(readComponent(
                        input, MAX_CLAIM_BYTES,
                        "Server shop barter output claim")));
            }
            if (input.read() != -1) {
                throw new IllegalArgumentException(
                        "Server shop barter commit has trailing data");
            }
            ServerShopBarterCommit decoded = new ServerShopBarterCommit(
                    requestId, playerId, shopId, recipeId, multiplier,
                    quoteRevision, recipeRevision, quoteCreatedAt,
                    ingredients, outputs, custody, transaction, reserve,
                    commit, claims);
            if (schema == CURRENT_SCHEMA
                    && !Arrays.equals(copy, encode(decoded))) {
                throw new IllegalArgumentException(
                        "Server shop barter commit encoding is not canonical");
            }
            return decoded;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Server shop barter commit is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException(
                    "Server shop barter commit is invalid", exception);
        }
    }

    private static int readCount(
            DataInputStream input,
            int maximum,
            String label
    ) throws IOException {
        int count = input.readInt();
        if (count <= 0 || count > maximum) {
            throw new IllegalArgumentException(
                    "Server shop barter " + label + " count is invalid");
        }
        return count;
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
                    "Server shop barter instant is invalid");
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
                    "Server shop barter commit size is invalid");
        }
    }

    private static final class BoundedOutput extends OutputStream {
        private final ByteArrayOutputStream destination;
        private final int maximum;
        private int count;

        private BoundedOutput(
                ByteArrayOutputStream destination,
                int maximum
        ) {
            this.destination = Objects.requireNonNull(
                    destination, "destination");
            this.maximum = maximum;
        }

        @Override
        public void write(int value) {
            requireCapacity(1);
            destination.write(value);
            count++;
        }

        @Override
        public void write(byte[] value, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, value.length);
            requireCapacity(length);
            destination.write(value, offset, length);
            count += length;
        }

        private void requireCapacity(int length) {
            if (length < 0 || (long) count + length > maximum) {
                throw new IllegalArgumentException(
                        "Server shop barter commit exceeds its size limit");
            }
        }
    }
}
