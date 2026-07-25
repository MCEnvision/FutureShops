package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.catalog.offer.OfferAction;
import com.enviouse.futureshops.catalog.offer.ServerShopBundleSavings;
import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopAtomicCommit;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopAtomicCommitCodec;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommand;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommandCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ServerShopOfferCommitCodec {
    public static final int MAX_ENCODED_BYTES = 40 * 1_048_576;

    private static final int MAGIC = 0x46534f46;

    private ServerShopOfferCommitCodec() {
    }

    public static byte[] encode(ServerShopOfferCommit commit) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(commit.schemaVersion());
            writeUuid(output, commit.requestId());
            writeUuid(output, commit.playerId());
            output.writeUTF(commit.shopId());
            output.writeUTF(commit.listingId());
            output.writeUTF(commit.optionId());
            output.writeByte(commit.action().ordinal());
            output.writeInt(commit.quantity());
            output.writeLong(commit.offerRevision());
            output.writeBoolean(commit.paymentSource().isPresent());
            if (commit.paymentSource().isPresent()) {
                output.writeByte(
                        commit.paymentSource().orElseThrow().ordinal());
            }
            output.writeLong(commit.quotedAt().getEpochSecond());
            output.writeInt(commit.quotedAt().getNano());
            output.writeBoolean(commit.claimsPending());
            writeBytes(output, PlayerShopAtomicCommitCodec.encode(
                    commit.valueCommit()));
            writeBytes(output, StockMutationCommandCodec.encode(
                    commit.stockReservation()));
            writeBytes(output, StockMutationCommandCodec.encode(
                    commit.stockCommit()));
            output.writeBoolean(commit.bundleSavings().isPresent());
            if (commit.bundleSavings().isPresent()) {
                writeSavings(output, commit.bundleSavings().orElseThrow());
            }
            output.writeUTF(commit.configurationFingerprint());
            output.flush();
            byte[] encoded = bytes.toByteArray();
            requireSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode server shop offer commit", exception);
        }
    }

    public static ServerShopOfferCommit decode(byte[] encoded) {
        requireSize(encoded);
        try {
            ByteArrayInputStream bytes = new ByteArrayInputStream(encoded);
            DataInputStream input = new DataInputStream(bytes);
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Server shop offer commit magic is invalid");
            }
            int schema = input.readInt();
            UUID requestId = readUuid(input);
            UUID playerId = readUuid(input);
            String shopId = input.readUTF();
            String listingId = input.readUTF();
            String optionId = input.readUTF();
            OfferAction action = enumValue(
                    OfferAction.values(), input.readUnsignedByte(),
                    "action");
            int quantity = input.readInt();
            long revision = input.readLong();
            Optional<PaymentSource> source = input.readBoolean()
                    ? Optional.of(enumValue(PaymentSource.values(),
                    input.readUnsignedByte(), "payment source"))
                    : Optional.empty();
            Instant quotedAt = Instant.ofEpochSecond(
                    input.readLong(), input.readInt());
            boolean claimsPending = input.readBoolean();
            PlayerShopAtomicCommit value =
                    PlayerShopAtomicCommitCodec.decode(readBytes(input));
            StockMutationCommand reserve =
                    StockMutationCommandCodec.decode(readBytes(input));
            StockMutationCommand commit =
                    StockMutationCommandCodec.decode(readBytes(input));
            Optional<ServerShopBundleSavings.Snapshot> savings =
                    input.readBoolean()
                            ? Optional.of(readSavings(input))
                            : Optional.empty();
            String fingerprint = input.readUTF();
            if (bytes.available() != 0
                    || !(reserve
                    instanceof StockMutationCommand.ReserveBatch batch)
                    || !(commit
                    instanceof StockMutationCommand.ResolveBatch resolve)) {
                throw new IllegalArgumentException(
                        "Server shop offer commit shape is invalid");
            }
            ServerShopOfferCommit result = new ServerShopOfferCommit(
                    schema, requestId, playerId, shopId, listingId,
                    optionId, action, quantity, revision, source, quotedAt,
                    claimsPending, value, batch, resolve, savings,
                    fingerprint);
            if (!Arrays.equals(encoded, encode(result))) {
                throw new IllegalArgumentException(
                        "Server shop offer commit is not canonical");
            }
            return result;
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException(
                    "Server shop offer commit is malformed", exception);
        }
    }

    private static void writeBytes(
            DataOutputStream output,
            byte[] value
    ) throws IOException {
        output.writeInt(value.length);
        output.write(value);
    }

    private static void writeSavings(
            DataOutputStream output,
            ServerShopBundleSavings.Snapshot savings
    ) throws IOException {
        output.writeLong(savings.individualTotalMinorUnits());
        output.writeLong(savings.bundleTotalMinorUnits());
        output.writeLong(savings.savingsMinorUnits());
        output.writeLong(savings.savingsBasisPoints());
        output.writeInt(savings.comparisonRevisions().size());
        for (ServerShopBundleSavings.ComparisonRevision revision
                : savings.comparisonRevisions()) {
            output.writeUTF(revision.componentId());
            output.writeUTF(revision.listingId());
            output.writeUTF(revision.optionId());
            output.writeLong(revision.revision());
        }
    }

    private static ServerShopBundleSavings.Snapshot readSavings(
            DataInputStream input
    ) throws IOException {
        long individual = input.readLong();
        long bundle = input.readLong();
        long saved = input.readLong();
        long basisPoints = input.readLong();
        int count = input.readInt();
        if (count <= 0 || count > 36) {
            throw new IllegalArgumentException(
                    "Server shop offer savings count is invalid");
        }
        List<ServerShopBundleSavings.ComparisonRevision> revisions =
                new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            revisions.add(new ServerShopBundleSavings.ComparisonRevision(
                    input.readUTF(), input.readUTF(), input.readUTF(),
                    input.readLong()));
        }
        return new ServerShopBundleSavings.Snapshot(
                individual, bundle, saved, basisPoints, revisions);
    }

    private static byte[] readBytes(DataInputStream input)
            throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Server shop offer component size is invalid");
        }
        return input.readNBytes(length);
    }

    private static void writeUuid(
            DataOutputStream output,
            UUID value
    ) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input)
            throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static <T> T enumValue(
            T[] values,
            int ordinal,
            String label
    ) {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException(
                    "Server shop offer " + label + " is invalid");
        }
        return values[ordinal];
    }

    private static void requireSize(byte[] encoded) {
        if (encoded == null || encoded.length == 0
                || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Server shop offer commit size is invalid");
        }
    }
}
