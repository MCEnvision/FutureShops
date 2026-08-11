package com.enviouse.futureshops.server.escrow.playershop;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class PlayerShopAtomicCommitCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES =
            PlayerShopEscrowConstants.MAX_ENCODED_BYTES;

    private static final int MAGIC = 0x46535041;

    private PlayerShopAtomicCommitCodec() {
    }

    public static byte[] encode(PlayerShopAtomicCommit commit) {
        Objects.requireNonNull(commit, "commit");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            PlayerShopBinarySupport.writeUuid(output, commit.commitId());
            PlayerShopBinarySupport.writeBytes(output,
                    PlayerShopIntentCodec.encode(commit.committedIntent()),
                    PlayerShopIntentCodec.MAX_ENCODED_BYTES);
            output.writeLong(commit.committedAt().getEpochSecond());
            output.writeInt(commit.committedAt().getNano());
            writeEvidence(output, commit.moneyReceipts(),
                    commit.itemReceipts(), commit.storageReceipts(),
                    commit.createdClaims());
            PlayerShopBinarySupport.writeString(output,
                    commit.commitFingerprint(), 64);
            output.flush();
            byte[] encoded = bytes.toByteArray();
            requireSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode player shop commit", exception);
        }
    }

    public static PlayerShopAtomicCommit decode(byte[] encoded) {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        requireSize(copy);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(copy))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Player shop commit magic is invalid");
            }
            if (input.readInt() != CURRENT_SCHEMA) {
                throw new IllegalArgumentException("Player shop commit schema is unsupported");
            }
            UUID commitId = PlayerShopBinarySupport.readUuid(input,
                    "commit id");
            PlayerShopEscrowIntent intent = PlayerShopIntentCodec.decode(
                    PlayerShopBinarySupport.readBytes(input,
                            PlayerShopIntentCodec.MAX_ENCODED_BYTES,
                            "committed intent"));
            long seconds = input.readLong();
            int nanos = input.readInt();
            if (nanos < 0 || nanos > 999_999_999) {
                throw new IllegalArgumentException("Player shop commit instant is invalid");
            }
            Instant committedAt = Instant.ofEpochSecond(seconds, nanos);
            List<PlayerShopMoneyMutationReceipt> money = readMoneyReceipts(input);
            List<PlayerShopItemMutationReceipt> items = readItemReceipts(input);
            List<PlayerShopStorageCustodyReceipt> storage = readStorageReceipts(input);
            List<PlayerShopClaimPlan> claims = readClaims(input);
            String fingerprint = PlayerShopBinarySupport.readString(input, 64,
                    "commit fingerprint");
            PlayerShopBinarySupport.requireFinished(input, "commit");
            PlayerShopAtomicCommit commit = new PlayerShopAtomicCommit(
                    commitId, intent, committedAt, money, items, storage,
                    claims, fingerprint);
            if (!Arrays.equals(copy, encode(commit))) {
                throw new IllegalArgumentException("Player shop commit is not canonical");
            }
            return commit;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Player shop commit is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException("Player shop commit is invalid", exception);
        }
    }

    static void writeEvidence(
            DataOutputStream output,
            List<PlayerShopMoneyMutationReceipt> money,
            List<PlayerShopItemMutationReceipt> items,
            List<PlayerShopStorageCustodyReceipt> storage,
            List<PlayerShopClaimPlan> claims
    ) throws IOException {
        output.writeInt(money.size());
        for (PlayerShopMoneyMutationReceipt receipt : money) {
            writeMoneyReceipt(output, receipt);
        }
        output.writeInt(items.size());
        for (PlayerShopItemMutationReceipt receipt : items) {
            writeItemReceipt(output, receipt);
        }
        output.writeInt(storage.size());
        for (PlayerShopStorageCustodyReceipt receipt : storage) {
            PlayerShopStorageCustodyReceiptCodec.writeBody(output, receipt);
        }
        output.writeInt(claims.size());
        for (PlayerShopClaimPlan claim : claims) {
            PlayerShopIntentCodec.writeClaim(output, claim);
        }
    }

    static void writeMoneyReceipt(
            DataOutputStream output,
            PlayerShopMoneyMutationReceipt receipt
    ) throws IOException {
        PlayerShopBinarySupport.writeUuid(output, receipt.requestId());
        PlayerShopIntentCodec.writeMoneyTransfer(output, receipt.transfer());
        output.writeLong(receipt.sourceBalanceAfterMinorUnits());
        output.writeLong(receipt.destinationBalanceAfterMinorUnits());
        PlayerShopBinarySupport.writeBytes(output, receipt.providerEvidence(),
                PlayerShopEscrowConstants.MAX_COMPONENT_BYTES);
        PlayerShopBinarySupport.writeString(output,
                receipt.receiptFingerprint(), 64);
    }

    static PlayerShopMoneyMutationReceipt readMoneyReceipt(
            DataInputStream input
    ) throws IOException {
        return new PlayerShopMoneyMutationReceipt(
                PlayerShopBinarySupport.readUuid(input,
                        "money receipt request id"),
                PlayerShopIntentCodec.readMoneyTransfer(input),
                input.readLong(), input.readLong(),
                PlayerShopBinarySupport.readBytes(input,
                        PlayerShopEscrowConstants.MAX_COMPONENT_BYTES,
                        "money provider evidence"),
                PlayerShopBinarySupport.readString(input, 64,
                        "money receipt fingerprint"));
    }

    static void writeItemReceipt(
            DataOutputStream output,
            PlayerShopItemMutationReceipt receipt
    ) throws IOException {
        PlayerShopBinarySupport.writeUuid(output, receipt.requestId());
        PlayerShopIntentCodec.writeItemTransfer(output, receipt.transfer());
        output.writeByte(receipt.fundingKind().ordinal());
        PlayerShopBinarySupport.writeBytes(output, receipt.custodyEvidence(),
                PlayerShopEscrowConstants.MAX_COMPONENT_BYTES);
        PlayerShopBinarySupport.writeString(output,
                receipt.receiptFingerprint(), 64);
    }

    static PlayerShopItemMutationReceipt readItemReceipt(
            DataInputStream input
    ) throws IOException {
        return new PlayerShopItemMutationReceipt(
                PlayerShopBinarySupport.readUuid(input,
                        "item receipt request id"),
                PlayerShopIntentCodec.readItemTransfer(input),
                PlayerShopBinarySupport.readEnum(input,
                        PlayerShopItemMutationReceipt.FundingKind.values(),
                        "item funding kind"),
                PlayerShopBinarySupport.readBytes(input,
                        PlayerShopEscrowConstants.MAX_COMPONENT_BYTES,
                        "item custody evidence"),
                PlayerShopBinarySupport.readString(input, 64,
                        "item receipt fingerprint"));
    }

    private static List<PlayerShopMoneyMutationReceipt> readMoneyReceipts(
            DataInputStream input
    ) throws IOException {
        return readList(input, PlayerShopEscrowConstants.MAX_TRANSFERS,
                PlayerShopAtomicCommitCodec::readMoneyReceipt,
                "money receipts");
    }

    private static List<PlayerShopItemMutationReceipt> readItemReceipts(
            DataInputStream input
    ) throws IOException {
        return readList(input, PlayerShopEscrowConstants.MAX_TRANSFERS,
                PlayerShopAtomicCommitCodec::readItemReceipt,
                "item receipts");
    }

    private static List<PlayerShopStorageCustodyReceipt> readStorageReceipts(
            DataInputStream input
    ) throws IOException {
        return readList(input,
                PlayerShopEscrowConstants.MAX_STORAGE_MUTATIONS,
                PlayerShopStorageCustodyReceiptCodec::readBody,
                "storage receipts");
    }

    private static List<PlayerShopClaimPlan> readClaims(
            DataInputStream input
    ) throws IOException {
        return readList(input, PlayerShopEscrowConstants.MAX_CLAIMS,
                PlayerShopIntentCodec::readClaim, "claims");
    }

    private static <T> List<T> readList(
            DataInputStream input,
            int maximum,
            Reader<T> reader,
            String label
    ) throws IOException {
        int size = input.readInt();
        if (size < 0 || size > maximum) {
            throw new IllegalArgumentException("Player shop " + label + " size is invalid");
        }
        List<T> result = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            result.add(reader.read(input));
        }
        return List.copyOf(result);
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("Player shop commit size is invalid");
        }
    }

    @FunctionalInterface
    private interface Reader<T> {
        T read(DataInputStream input) throws IOException;
    }
}
