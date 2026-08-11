package com.enviouse.futureshops.server.market.auction.escrow;

import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.runtime.ClaimJournalCodec;
import com.enviouse.futureshops.server.escrow.runtime.EscrowJournalEventCodec;
import com.enviouse.futureshops.server.escrow.runtime.LedgerJournalCodec;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionByteCodec;
import com.enviouse.futureshops.server.market.auction.AuctionHouseMutation;
import com.enviouse.futureshops.server.market.auction.AuctionHouseMutationCodec;
import com.enviouse.futureshops.server.market.auction.AuctionOperationType;

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
import java.util.Optional;
import java.util.UUID;

public final class AuctionEscrowCommitCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES =
            EscrowJournalEventCodec.MAX_BODY_BYTES - 64;

    private static final int MAGIC = 0x4145434D;
    private static final int MAX_TEXT_BYTES = 1024;

    private AuctionEscrowCommitCodec() {
    }

    public static byte[] encode(AuctionEscrowCommit commit) {
        Objects.requireNonNull(commit, "commit");
        byte[] payload = payloadBytes(commit);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            writeBytes(output, payload, MAX_ENCODED_BYTES);
            writeText(output, fingerprint(commit));
            output.flush();
            byte[] result = bytes.toByteArray();
            requireSize(result);
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode auction escrow commit", exception);
        }
    }

    public static AuctionEscrowCommit decode(byte[] encoded) {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        requireSize(copy);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(copy))) {
            if (input.readInt() != MAGIC) {
                throw invalid("Auction escrow commit magic is invalid");
            }
            if (input.readInt() != CURRENT_SCHEMA) {
                throw invalid(
                        "Auction escrow commit schema is unsupported");
            }
            byte[] payload = readBytes(input, MAX_ENCODED_BYTES);
            String storedFingerprint = readText(input, 64);
            if (input.read() != -1) {
                throw invalid(
                        "Auction escrow commit has trailing data");
            }
            AuctionEscrowCommit result = decodePayload(payload);
            if (!storedFingerprint.equals(fingerprint(result))
                    || !Arrays.equals(copy, encode(result))) {
                throw invalid(
                        "Auction escrow commit encoding is not canonical");
            }
            return result;
        } catch (EOFException exception) {
            throw invalid("Auction escrow commit is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw invalid("Auction escrow commit is invalid", exception);
        }
    }

    static String fingerprint(AuctionEscrowCommit commit) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(payloadBytes(commit)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable",
                    exception);
        }
    }

    private static byte[] payloadBytes(AuctionEscrowCommit commit) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            writeUuid(output, commit.requestId());
            writeUuid(output, commit.listingId());
            output.writeInt(commit.operation().ordinal());
            output.writeInt(commit.bookMutations().size());
            for (AuctionHouseMutation mutation : commit.bookMutations()) {
                writeBytes(output, AuctionHouseMutationCodec.encode(
                        mutation),
                        AuctionHouseMutationCodec.MAX_ENCODED_BYTES);
            }
            writeOptionalText(output, commit.createIntentFingerprint());
            output.writeBoolean(commit.itemCustody().isPresent());
            if (commit.itemCustody().isPresent()) {
                AuctionCreateEscrowIntentCodec.writeCustody(output,
                        commit.itemCustody().orElseThrow());
            }
            output.writeInt(commit.walletSnapshots().size());
            for (AuctionEscrowWalletSnapshot wallet
                    : commit.walletSnapshots()) {
                writeUuid(output, wallet.playerId());
                output.writeLong(wallet.walletMinor());
                output.writeLong(wallet.debtMinor());
                output.writeLong(wallet.reservedMinor());
                output.writeLong(wallet.walletLimitMinor());
                output.writeLong(wallet.configurationGeneration());
            }
            writeText(output, commit.currencyId());
            writeInstant(output, commit.decidedAt());
            output.writeBoolean(commit.completedTransaction().isPresent());
            if (commit.completedTransaction().isPresent()) {
                writeBytes(output, EscrowTransactionByteCodec.encode(
                        commit.completedTransaction().orElseThrow()),
                        EscrowTransactionByteCodec.MAX_ENCODED_BYTES);
            }
            output.writeBoolean(commit.ledgerTransaction().isPresent());
            if (commit.ledgerTransaction().isPresent()) {
                writeBytes(output, LedgerJournalCodec.encode(
                        commit.ledgerTransaction().orElseThrow()),
                        EscrowJournalEventCodec.MAX_BODY_BYTES);
            }
            output.writeInt(commit.claims().size());
            for (EscrowClaim claim : commit.claims()) {
                writeBytes(output, ClaimJournalCodec.encodeClaim(claim),
                        EscrowJournalEventCodec.MAX_BODY_BYTES);
            }
            output.flush();
            byte[] result = bytes.toByteArray();
            if (result.length == 0 || result.length > MAX_ENCODED_BYTES) {
                throw invalid("Auction escrow commit payload is too large");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode auction escrow commit payload",
                    exception);
        }
    }

    private static AuctionEscrowCommit decodePayload(byte[] payload)
            throws IOException {
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(payload))) {
            UUID requestId = readUuid(input);
            UUID listingId = readUuid(input);
            int operationIndex = input.readInt();
            if (operationIndex < 0 || operationIndex
                    >= AuctionOperationType.values().length) {
                throw invalid("Auction escrow operation is invalid");
            }
            int mutationCount = input.readInt();
            if (mutationCount <= 0 || mutationCount
                    > AuctionEscrowCommit.MAX_BOOK_MUTATIONS) {
                throw invalid(
                        "Auction escrow mutation count is invalid");
            }
            List<AuctionHouseMutation> mutations = new ArrayList<>(
                    mutationCount);
            for (int index = 0; index < mutationCount; index++) {
                mutations.add(AuctionHouseMutationCodec.decode(
                        readBytes(input,
                                AuctionHouseMutationCodec
                                        .MAX_ENCODED_BYTES)));
            }
            Optional<String> createFingerprint = readOptionalText(input,
                    64);
            Optional<AuctionEscrowItemCustody> custody = input.readBoolean()
                    ? Optional.of(AuctionCreateEscrowIntentCodec
                    .readCustody(input)) : Optional.empty();
            int walletCount = input.readInt();
            if (walletCount < 0 || walletCount
                    > AuctionEscrowCommit.MAX_WALLET_SNAPSHOTS) {
                throw invalid("Auction wallet count is invalid");
            }
            List<AuctionEscrowWalletSnapshot> wallets = new ArrayList<>(
                    walletCount);
            for (int index = 0; index < walletCount; index++) {
                wallets.add(new AuctionEscrowWalletSnapshot(
                        readUuid(input), input.readLong(), input.readLong(),
                        input.readLong(), input.readLong(),
                        input.readLong()));
            }
            String currencyId = readText(input,
                    AuctionCreateEscrowIntent.MAX_CURRENCY_ID_LENGTH);
            Instant decidedAt = readInstant(input);
            Optional<EscrowTransaction> transaction = input.readBoolean()
                    ? Optional.of(EscrowTransactionByteCodec.decode(
                    readBytes(input,
                            EscrowTransactionByteCodec.MAX_ENCODED_BYTES)))
                    : Optional.empty();
            Optional<LedgerTransaction> ledger = input.readBoolean()
                    ? Optional.of(LedgerJournalCodec.decode(readBytes(input,
                    EscrowJournalEventCodec.MAX_BODY_BYTES)))
                    : Optional.empty();
            int claimCount = input.readInt();
            if (claimCount < 0 || claimCount
                    > AuctionEscrowCommit.MAX_CLAIMS) {
                throw invalid("Auction claim count is invalid");
            }
            List<EscrowClaim> claims = new ArrayList<>(claimCount);
            for (int index = 0; index < claimCount; index++) {
                claims.add(ClaimJournalCodec.decodeClaim(readBytes(input,
                        EscrowJournalEventCodec.MAX_BODY_BYTES)));
            }
            if (input.read() != -1) {
                throw invalid(
                        "Auction escrow commit payload has trailing data");
            }
            return new AuctionEscrowCommit(requestId, listingId,
                    AuctionOperationType.values()[operationIndex], mutations,
                    createFingerprint, custody, wallets, currencyId,
                    decidedAt, transaction, ledger, claims);
        }
    }

    private static void writeOptionalText(
            DataOutputStream output,
            Optional<String> value
    ) throws IOException {
        output.writeBoolean(value.isPresent());
        if (value.isPresent()) {
            writeText(output, value.orElseThrow());
        }
    }

    private static Optional<String> readOptionalText(
            DataInputStream input,
            int maximum
    ) throws IOException {
        return input.readBoolean()
                ? Optional.of(readText(input, maximum))
                : Optional.empty();
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
            throw invalid("Auction escrow instant is invalid");
        }
        try {
            return Instant.ofEpochSecond(seconds, nanos);
        } catch (DateTimeException exception) {
            throw invalid("Auction escrow instant is invalid", exception);
        }
    }

    private static void writeText(DataOutputStream output, String value)
            throws IOException {
        byte[] bytes = Objects.requireNonNull(value, "value")
                .getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > MAX_TEXT_BYTES) {
            throw invalid("Auction escrow text size is invalid");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readText(DataInputStream input, int maximum)
            throws IOException {
        int length = input.readInt();
        int maximumBytes = Math.multiplyExact(maximum, 4);
        if (length <= 0 || length > maximumBytes
                || length > input.available()) {
            throw invalid("Auction escrow text size is invalid");
        }
        byte[] bytes = input.readNBytes(length);
        String result = new String(bytes, StandardCharsets.UTF_8);
        if (!Arrays.equals(bytes,
                result.getBytes(StandardCharsets.UTF_8))) {
            throw invalid("Auction escrow text is not valid UTF8");
        }
        return result;
    }

    private static void writeBytes(
            DataOutputStream output,
            byte[] value,
            int maximum
    ) throws IOException {
        if (value.length == 0 || value.length > maximum) {
            throw invalid("Auction escrow component size is invalid");
        }
        output.writeInt(value.length);
        output.write(value);
    }

    private static byte[] readBytes(DataInputStream input, int maximum)
            throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > maximum
                || length > input.available()) {
            throw invalid("Auction escrow component size is invalid");
        }
        byte[] result = input.readNBytes(length);
        if (result.length != length) {
            throw new EOFException(
                    "Auction escrow component is truncated");
        }
        return result;
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw invalid("Auction escrow commit size is invalid");
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
}
