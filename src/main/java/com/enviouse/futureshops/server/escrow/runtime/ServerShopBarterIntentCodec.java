package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayload;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayloadCodec;
import com.enviouse.futureshops.server.escrow.item.ItemMatchMode;
import com.enviouse.futureshops.server.escrow.model.DimensionAwareShopReference;

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

public final class ServerShopBarterIntentCodec {
    public static final int CURRENT_SCHEMA = 2;
    public static final int MAX_ENCODED_BYTES =
            EscrowJournalEventCodec.MAX_BODY_BYTES;

    private static final int MAGIC = 0x46534249;
    private static final int MAX_IDENTIFIER_BYTES =
            ServerShopBarterCommit.MAX_IDENTIFIER_LENGTH * 4;

    private ServerShopBarterIntentCodec() {
    }

    public static byte[] encode(ServerShopBarterIntent intent) {
        Objects.requireNonNull(intent, "intent");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(
                    new BoundedOutput(bytes, MAX_ENCODED_BYTES));
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            BinaryCodecSupport.writeUuid(output, intent.requestId());
            BinaryCodecSupport.writeUuid(output, intent.playerId());
            BinaryCodecSupport.writeString(output, intent.shopId(),
                    MAX_IDENTIFIER_BYTES);
            BinaryCodecSupport.writeString(output, intent.recipeId(),
                    MAX_IDENTIFIER_BYTES);
            output.writeInt(intent.multiplier());
            output.writeLong(intent.quoteRevision());
            output.writeLong(intent.recipeRevision());
            writeInstant(output, intent.quoteCreatedAt());
            output.writeInt(intent.ingredients().size());
            for (ServerShopBarterCommit.Ingredient ingredient
                    : intent.ingredients()) {
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
                        "Server shop barter intent ingredient template");
            }
            output.writeInt(intent.outputs().size());
            for (ServerShopBarterCommit.OutputLine outputLine
                    : intent.outputs()) {
                output.writeInt(outputLine.outputIndex());
                BinaryCodecSupport.writeString(output,
                        outputLine.listingId(), MAX_IDENTIFIER_BYTES);
                BinaryCodecSupport.writeString(output,
                        outputLine.itemId(), MAX_IDENTIFIER_BYTES);
                output.writeInt(outputLine.quantityPerTrade());
                output.writeLong(outputLine.expectedStockRevision());
                output.writeInt(outputLine.portions().size());
                for (ExactItemClaimPayload payload
                        : outputLine.portions()) {
                    writeComponent(output,
                            ExactItemClaimPayloadCodec.encode(payload),
                            ExactItemClaimPayloadCodec.MAX_ENCODED_BYTES,
                            "Server shop barter intent output portion");
                }
            }
            BinaryCodecSupport.writeString(output,
                    intent.shopReference().shopId(),
                    MAX_IDENTIFIER_BYTES);
            BinaryCodecSupport.writeString(output,
                    intent.shopReference().dimensionId(),
                    MAX_IDENTIFIER_BYTES);
            output.writeInt(intent.shopReference().blockX());
            output.writeInt(intent.shopReference().blockY());
            output.writeInt(intent.shopReference().blockZ());
            BinaryCodecSupport.writeString(output,
                    intent.status().name(), 160);
            output.writeLong(intent.revision());
            output.flush();
            byte[] encoded = bytes.toByteArray();
            requireSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode server shop barter intent",
                    exception);
        }
    }

    public static ServerShopBarterIntent decode(byte[] encoded) {
        byte[] copy = Objects.requireNonNull(
                encoded, "encoded").clone();
        requireSize(copy);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(copy))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Server shop barter intent magic is invalid");
            }
            int schema = input.readInt();
            if (schema < 1 || schema > CURRENT_SCHEMA) {
                throw new IllegalArgumentException(
                        "Server shop barter intent schema is unsupported");
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
                        "Server shop barter intent ingredient template");
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
                            "Server shop barter intent output portions exceed their limit");
                }
                List<ExactItemClaimPayload> portions = new ArrayList<>(
                        portionCount);
                for (int portionIndex = 0;
                     portionIndex < portionCount; portionIndex++) {
                    portions.add(ExactItemClaimPayloadCodec.decode(
                            readComponent(input,
                                    ExactItemClaimPayloadCodec
                                            .MAX_ENCODED_BYTES,
                                    "Server shop barter intent output portion")));
                }
                outputs.add(new ServerShopBarterCommit.OutputLine(
                        outputIndex, listingId, itemId, quantity,
                        stockRevision, portions));
            }
            DimensionAwareShopReference shopReference =
                    new DimensionAwareShopReference(
                            BinaryCodecSupport.readString(input,
                                    MAX_IDENTIFIER_BYTES),
                            BinaryCodecSupport.readString(input,
                                    MAX_IDENTIFIER_BYTES),
                            input.readInt(), input.readInt(),
                            input.readInt());
            ServerShopBarterIntent.Status status;
            try {
                status = ServerShopBarterIntent.Status.valueOf(
                        BinaryCodecSupport.readString(input, 160));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Server shop barter intent status is invalid",
                        exception);
            }
            long revision = input.readLong();
            if (input.read() != -1) {
                throw new IllegalArgumentException(
                        "Server shop barter intent has trailing data");
            }
            ServerShopBarterIntent decoded = new ServerShopBarterIntent(
                    requestId, playerId, shopId, recipeId, multiplier,
                    quoteRevision, recipeRevision, quoteCreatedAt,
                    ingredients, outputs, shopReference, status, revision);
            if (schema == CURRENT_SCHEMA
                    && !Arrays.equals(copy, encode(decoded))) {
                throw new IllegalArgumentException(
                        "Server shop barter intent encoding is not canonical");
            }
            return decoded;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Server shop barter intent is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException(
                    "Server shop barter intent is invalid", exception);
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
                    "Server shop barter intent " + label
                            + " count is invalid");
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
                    "Server shop barter intent instant is invalid");
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
                    "Server shop barter intent size is invalid");
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
                        "Server shop barter intent exceeds its size limit");
            }
        }
    }
}
