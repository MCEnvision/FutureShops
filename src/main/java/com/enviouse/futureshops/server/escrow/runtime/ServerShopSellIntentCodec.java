package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.escrow.model.DimensionAwareShopReference;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class ServerShopSellIntentCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES =
            ItemStackSnapshotCodec.MAXIMUM_BYTES + 4096;

    private static final int MAGIC = 0x46535349;

    private ServerShopSellIntentCodec() {
    }

    public static byte[] encode(ServerShopSellIntent intent) {
        Objects.requireNonNull(intent, "intent");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            BinaryCodecSupport.writeUuid(output, intent.requestId());
            BinaryCodecSupport.writeUuid(output, intent.playerId());
            BinaryCodecSupport.writeString(output, intent.shopId(), 640);
            BinaryCodecSupport.writeString(output, intent.listingId(), 640);
            BinaryCodecSupport.writeString(output, intent.itemId(), 640);
            output.writeInt(intent.quantity());
            output.writeLong(intent.unitPriceMinorUnits());
            output.writeLong(intent.quoteRevision());
            output.writeLong(intent.expectedStockRevision());
            output.writeLong(intent.quoteCreatedAt().getEpochSecond());
            output.writeInt(intent.quoteCreatedAt().getNano());
            output.writeLong(intent.walletBeforeMinorUnits());
            output.writeLong(intent.debtBeforeMinorUnits());
            output.writeLong(intent.reservedBeforeMinorUnits());
            output.writeLong(intent.walletBalanceLimitMinorUnits());
            output.writeLong(intent.configurationGeneration());
            BinaryCodecSupport.writeString(output, intent.currencyName(), 512);
            output.writeInt(intent.currencyDecimals());
            byte[] template = intent.exactItemTemplate();
            output.writeInt(template.length);
            output.write(template);
            DimensionAwareShopReference reference = intent.shopReference();
            BinaryCodecSupport.writeString(output, reference.shopId(), 640);
            BinaryCodecSupport.writeString(output, reference.dimensionId(), 640);
            output.writeInt(reference.blockX());
            output.writeInt(reference.blockY());
            output.writeInt(reference.blockZ());
            output.writeInt(intent.status().ordinal());
            output.writeLong(intent.revision());
            BinaryCodecSupport.writeString(output, intent.intentFingerprint(), 128);
            output.flush();
            byte[] encoded = bytes.toByteArray();
            requireSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode server shop sell intent", exception);
        }
    }

    public static ServerShopSellIntent decode(byte[] encoded) {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        requireSize(copy);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(copy))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Server shop sell intent magic is invalid");
            }
            if (input.readInt() != CURRENT_SCHEMA) {
                throw new IllegalArgumentException("Server shop sell intent schema is unsupported");
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
            long seconds = input.readLong();
            int nanos = input.readInt();
            if (nanos < 0 || nanos > 999_999_999) {
                throw new IllegalArgumentException("Server shop sell intent instant is invalid");
            }
            Instant quotedAt = Instant.ofEpochSecond(seconds, nanos);
            long wallet = input.readLong();
            long debt = input.readLong();
            long reserved = input.readLong();
            long walletLimit = input.readLong();
            long configurationGeneration = input.readLong();
            String currencyName = BinaryCodecSupport.readString(input, 512);
            int currencyDecimals = input.readInt();
            int templateSize = input.readInt();
            if (templateSize <= 0
                    || templateSize > ItemStackSnapshotCodec.MAXIMUM_BYTES
                    || templateSize > input.available()) {
                throw new IllegalArgumentException("Server shop sell intent template size is invalid");
            }
            byte[] template = input.readNBytes(templateSize);
            if (template.length != templateSize) {
                throw new EOFException("Server shop sell intent template is truncated");
            }
            DimensionAwareShopReference reference =
                    new DimensionAwareShopReference(
                            BinaryCodecSupport.readString(input, 640),
                            BinaryCodecSupport.readString(input, 640),
                            input.readInt(), input.readInt(), input.readInt());
            int statusIndex = input.readInt();
            if (statusIndex < 0
                    || statusIndex >= ServerShopSellIntent.Status.values().length) {
                throw new IllegalArgumentException("Server shop sell intent status is invalid");
            }
            long revision = input.readLong();
            String fingerprint = BinaryCodecSupport.readString(input, 128);
            if (input.read() != -1) {
                throw new IllegalArgumentException("Server shop sell intent has trailing data");
            }
            ServerShopSellIntent intent = new ServerShopSellIntent(requestId,
                    playerId, shopId, listingId, itemId, quantity, unitPrice,
                    quoteRevision, stockRevision, quotedAt, wallet, debt,
                    reserved, walletLimit, configurationGeneration,
                    currencyName, currencyDecimals, template, reference,
                    ServerShopSellIntent.Status.values()[statusIndex], revision);
            if (!intent.intentFingerprint().equals(fingerprint)
                    || !Arrays.equals(copy, encode(intent))) {
                throw new IllegalArgumentException("Server shop sell intent is not canonical");
            }
            return intent;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Server shop sell intent is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException("Server shop sell intent is invalid", exception);
        }
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("Server shop sell intent size is invalid");
        }
    }
}
