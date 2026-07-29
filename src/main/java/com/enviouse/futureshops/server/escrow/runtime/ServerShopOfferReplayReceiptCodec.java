package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.catalog.offer.OfferAction;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.zip.CRC32C;

final class ServerShopOfferReplayReceiptCodec {
    static final int MAXIMUM_BYTES = 262_144;
    private static final int MAGIC = 0x46534F52;
    private static final int VERSION = 1;
    private static final int MAXIMUM_IDENTIFIER_BYTES = 640;
    private static final int FINGERPRINT_BYTES = 32;
    private static final int CHECKSUM_BYTES = Integer.BYTES;

    private ServerShopOfferReplayReceiptCodec() {
    }

    static byte[] encode(ServerShopOfferReplayReceipt receipt) {
        java.util.Objects.requireNonNull(receipt, "receipt");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(
                    new LimitedOutputStream(bytes,
                            MAXIMUM_BYTES - CHECKSUM_BYTES));
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            BinaryCodecSupport.writeUuid(output, receipt.requestId());
            output.writeByte(kindCode(receipt.kind()));
            output.write(HexFormat.of().parseHex(
                    receipt.requestFingerprint()));
            output.writeByte(statusCode(receipt.status()));
            output.writeInt(receipt.usageEvidence().size());
            for (ServerShopOfferReplayReceipt.UsageEvidence evidence
                    : receipt.usageEvidence()) {
                writeEvidence(output, evidence);
            }
            output.flush();
            byte[] body = bytes.toByteArray();
            CRC32C checksum = new CRC32C();
            checksum.update(body, 0, body.length);
            DataOutputStream framed = new DataOutputStream(
                    new LimitedOutputStream(bytes, MAXIMUM_BYTES));
            framed.writeInt((int) checksum.getValue());
            framed.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode server shop offer replay receipt",
                    exception);
        }
    }

    static ServerShopOfferReplayReceipt decode(byte[] encoded) {
        byte[] value = java.util.Objects.requireNonNull(
                encoded, "encoded").clone();
        if (value.length <= CHECKSUM_BYTES
                || value.length > MAXIMUM_BYTES) {
            throw new IllegalArgumentException(
                    "Server shop offer replay receipt size is invalid");
        }
        int bodyLength = value.length - CHECKSUM_BYTES;
        int storedChecksum = java.nio.ByteBuffer.wrap(
                value, bodyLength, CHECKSUM_BYTES).getInt();
        CRC32C checksum = new CRC32C();
        checksum.update(value, 0, bodyLength);
        if ((int) checksum.getValue() != storedChecksum) {
            throw new IllegalArgumentException(
                    "Server shop offer replay receipt checksum is invalid");
        }
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(value, 0, bodyLength))) {
            if (input.readInt() != MAGIC
                    || input.readInt() != VERSION) {
                throw new IllegalArgumentException(
                        "Server shop offer replay receipt header is invalid");
            }
            UUID requestId = BinaryCodecSupport.readUuid(input);
            ServerShopOfferReplayReceipt.Kind kind =
                    kind(input.readUnsignedByte());
            byte[] fingerprint = input.readNBytes(FINGERPRINT_BYTES);
            if (fingerprint.length != FINGERPRINT_BYTES) {
                throw new EOFException();
            }
            ServerShopOfferService.Status status =
                    status(input.readUnsignedByte());
            int usageCount = input.readInt();
            if (usageCount < 0
                    || usageCount
                    > ServerShopOfferReplayReceipt.MAXIMUM_USAGE_LINES) {
                throw new IllegalArgumentException(
                        "Server shop offer replay usage count is invalid");
            }
            List<ServerShopOfferReplayReceipt.UsageEvidence> usage =
                    new ArrayList<>(usageCount);
            for (int index = 0; index < usageCount; index++) {
                usage.add(readEvidence(input));
            }
            if (input.read() != -1) {
                throw new IllegalArgumentException(
                        "Server shop offer replay receipt has trailing data");
            }
            ServerShopOfferReplayReceipt decoded =
                    new ServerShopOfferReplayReceipt(
                            requestId, kind,
                            HexFormat.of().formatHex(fingerprint),
                            status, usage);
            if (!Arrays.equals(value, encode(decoded))) {
                throw new IllegalArgumentException(
                        "Server shop offer replay receipt is not canonical");
            }
            return decoded;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Server shop offer replay receipt is truncated",
                    exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException(
                    "Server shop offer replay receipt is invalid",
                    exception);
        }
    }

    private static void writeEvidence(
            DataOutputStream output,
            ServerShopOfferReplayReceipt.UsageEvidence evidence
    ) throws IOException {
        BinaryCodecSupport.writeUuid(output, evidence.requestId());
        BinaryCodecSupport.writeUuid(output, evidence.playerId());
        BinaryCodecSupport.writeString(
                output, evidence.shopId(), MAXIMUM_IDENTIFIER_BYTES);
        BinaryCodecSupport.writeString(
                output, evidence.listingId(), MAXIMUM_IDENTIFIER_BYTES);
        BinaryCodecSupport.writeString(
                output, evidence.optionId(), MAXIMUM_IDENTIFIER_BYTES);
        output.writeByte(actionCode(evidence.action()));
        output.writeInt(evidence.quantity());
        writeLimits(output, evidence.listingLimits());
        writeLimits(output, evidence.optionLimits());
        output.writeLong(evidence.capacity());
        output.writeLong(evidence.committedAtEpoch());
    }

    private static ServerShopOfferReplayReceipt.UsageEvidence readEvidence(
            DataInputStream input
    ) throws IOException {
        return new ServerShopOfferReplayReceipt.UsageEvidence(
                BinaryCodecSupport.readUuid(input),
                BinaryCodecSupport.readUuid(input),
                BinaryCodecSupport.readString(
                        input, MAXIMUM_IDENTIFIER_BYTES),
                BinaryCodecSupport.readString(
                        input, MAXIMUM_IDENTIFIER_BYTES),
                BinaryCodecSupport.readString(
                        input, MAXIMUM_IDENTIFIER_BYTES),
                action(input.readUnsignedByte()),
                input.readInt(),
                readLimits(input),
                readLimits(input),
                input.readLong(),
                input.readLong());
    }

    private static void writeLimits(
            DataOutputStream output,
            OfferLimitPolicy limits
    ) throws IOException {
        output.writeInt(limits.maximumPerRequest());
        output.writeLong(limits.lifetimeLimit());
        output.writeLong(limits.periodLimit());
        output.writeLong(limits.periodSeconds());
        output.writeLong(limits.cooldownSeconds());
    }

    private static OfferLimitPolicy readLimits(
            DataInputStream input
    ) throws IOException {
        return new OfferLimitPolicy(
                input.readInt(), input.readLong(), input.readLong(),
                input.readLong(), input.readLong());
    }

    private static int kindCode(ServerShopOfferReplayReceipt.Kind kind) {
        return switch (kind) {
            case SINGLE -> 1;
            case CART -> 2;
        };
    }

    private static ServerShopOfferReplayReceipt.Kind kind(int code) {
        return switch (code) {
            case 1 -> ServerShopOfferReplayReceipt.Kind.SINGLE;
            case 2 -> ServerShopOfferReplayReceipt.Kind.CART;
            default -> throw new IllegalArgumentException(
                    "Server shop offer replay kind is invalid");
        };
    }

    private static int statusCode(ServerShopOfferService.Status status) {
        return switch (status) {
            case SUCCESS -> 1;
            case CLAIMS_PENDING -> 2;
            case OUT_OF_STOCK -> 3;
            case REJECTED -> 4;
            case INVALID_REQUEST -> 5;
            case STALE_REVISION -> 6;
            case NOT_FOUND -> 7;
            case NOT_AVAILABLE -> 8;
            case LIMIT_REACHED -> 9;
            case COOLDOWN -> 10;
            case CANCELLED_BY_EVENT -> 11;
            default -> throw new IllegalArgumentException(
                    "Server shop offer replay status is invalid");
        };
    }

    private static ServerShopOfferService.Status status(int code) {
        return switch (code) {
            case 1 -> ServerShopOfferService.Status.SUCCESS;
            case 2 -> ServerShopOfferService.Status.CLAIMS_PENDING;
            case 3 -> ServerShopOfferService.Status.OUT_OF_STOCK;
            case 4 -> ServerShopOfferService.Status.REJECTED;
            case 5 -> ServerShopOfferService.Status.INVALID_REQUEST;
            case 6 -> ServerShopOfferService.Status.STALE_REVISION;
            case 7 -> ServerShopOfferService.Status.NOT_FOUND;
            case 8 -> ServerShopOfferService.Status.NOT_AVAILABLE;
            case 9 -> ServerShopOfferService.Status.LIMIT_REACHED;
            case 10 -> ServerShopOfferService.Status.COOLDOWN;
            case 11 -> ServerShopOfferService.Status.CANCELLED_BY_EVENT;
            default -> throw new IllegalArgumentException(
                    "Server shop offer replay status is invalid");
        };
    }

    private static int actionCode(OfferAction action) {
        return switch (action) {
            case ACQUIRE_FROM_SHOP -> 1;
            case SELL_TO_SHOP -> 2;
        };
    }

    private static OfferAction action(int code) {
        return switch (code) {
            case 1 -> OfferAction.ACQUIRE_FROM_SHOP;
            case 2 -> OfferAction.SELL_TO_SHOP;
            default -> throw new IllegalArgumentException(
                    "Server shop offer replay action is invalid");
        };
    }

    private static final class LimitedOutputStream extends OutputStream {
        private final ByteArrayOutputStream target;
        private final int maximumBytes;

        private LimitedOutputStream(
                ByteArrayOutputStream target,
                int maximumBytes
        ) {
            this.target = target;
            this.maximumBytes = maximumBytes;
        }

        @Override
        public void write(int value) throws IOException {
            requireCapacity(1);
            target.write(value);
        }

        @Override
        public void write(byte[] value, int offset, int length)
                throws IOException {
            java.util.Objects.checkFromIndexSize(
                    offset, length, value.length);
            requireCapacity(length);
            target.write(value, offset, length);
        }

        private void requireCapacity(int additionalBytes)
                throws IOException {
            if (additionalBytes < 0
                    || target.size()
                    > maximumBytes - additionalBytes) {
                throw new IOException(
                        "Server shop offer replay receipt exceeds its limit");
            }
        }
    }
}
