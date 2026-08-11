package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionByteCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PlayerPaymentCommitCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES = 131_072;

    private static final int MAGIC = 0x46535059;
    private static final int MAX_TRANSACTION_BYTES = 65_536;
    private static final int MAX_LEDGER_BYTES = 16_384;
    private static final int MAX_CLAIM_BYTES = 16_384;
    private static final int MAX_CURRENCY_NAME_BYTES = 512;

    private PlayerPaymentCommitCodec() {
    }

    public static byte[] encode(PlayerPaymentCommit commit) {
        Objects.requireNonNull(commit, "commit");
        PlayerPaymentConservationValidator.validate(commit);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            BinaryCodecSupport.writeUuid(output, commit.requestId());
            BinaryCodecSupport.writeUuid(output, commit.payerId());
            BinaryCodecSupport.writeUuid(output, commit.recipientId());
            output.writeLong(commit.payerWalletBeforeMinorUnits());
            output.writeLong(commit.payerDebtBeforeMinorUnits());
            output.writeLong(commit.recipientWalletBeforeMinorUnits());
            output.writeLong(commit.recipientDebtBeforeMinorUnits());
            output.writeLong(commit.recipientReservedBeforeMinorUnits());
            output.writeLong(commit.walletBalanceLimitMinorUnits());
            BinaryCodecSupport.writeString(output, commit.currencyName(),
                    MAX_CURRENCY_NAME_BYTES);
            output.writeInt(commit.currencyDecimals());
            writeComponent(output, EscrowTransactionByteCodec.encode(
                    commit.completedTransaction()),
                    MAX_TRANSACTION_BYTES,
                    "Player payment transaction");
            writeComponent(output, LedgerJournalCodec.encode(
                    commit.ledgerTransaction()),
                    MAX_LEDGER_BYTES,
                    "Player payment ledger");
            output.writeBoolean(commit.overflowClaim().isPresent());
            if (commit.overflowClaim().isPresent()) {
                writeComponent(output, ClaimJournalCodec.encodeClaim(
                        commit.overflowClaim().orElseThrow()),
                        MAX_CLAIM_BYTES,
                        "Player payment overflow claim");
            }
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length == 0
                    || encoded.length > MAX_ENCODED_BYTES
                    || encoded.length
                    > EscrowJournalEventCodec.MAX_BODY_BYTES) {
                throw new IllegalArgumentException(
                        "Player payment commit exceeds its binary limit");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode player payment commit", exception);
        }
    }

    public static PlayerPaymentCommit decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0
                || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Player payment commit size is invalid");
        }
        ByteArrayInputStream bytes = new ByteArrayInputStream(encoded);
        try (DataInputStream input = new DataInputStream(bytes)) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Player payment commit magic is invalid");
            }
            int schema = input.readInt();
            if (schema > CURRENT_SCHEMA) {
                throw new IllegalStateException(
                        "Player payment commit schema is newer than this build");
            }
            if (schema != CURRENT_SCHEMA) {
                throw new IllegalArgumentException(
                        "Player payment commit schema is unsupported");
            }
            UUID requestId = BinaryCodecSupport.readUuid(input);
            UUID payerId = BinaryCodecSupport.readUuid(input);
            UUID recipientId = BinaryCodecSupport.readUuid(input);
            long payerWallet = input.readLong();
            long payerDebt = input.readLong();
            long recipientWallet = input.readLong();
            long recipientDebt = input.readLong();
            long recipientReserved = input.readLong();
            long walletLimit = input.readLong();
            String currencyName = BinaryCodecSupport.readString(
                    input, MAX_CURRENCY_NAME_BYTES);
            int currencyDecimals = input.readInt();
            EscrowTransaction transaction =
                    EscrowTransactionByteCodec.decode(readComponent(
                            input, bytes, MAX_TRANSACTION_BYTES,
                            "Player payment transaction"));
            LedgerTransaction ledger = LedgerJournalCodec.decode(
                    readComponent(input, bytes, MAX_LEDGER_BYTES,
                            "Player payment ledger"));
            Optional<EscrowClaim> claim = BinaryCodecSupport.readBoolean(
                    input)
                    ? Optional.of(ClaimJournalCodec.decodeClaim(
                    readComponent(input, bytes, MAX_CLAIM_BYTES,
                            "Player payment overflow claim")))
                    : Optional.empty();
            if (bytes.available() != 0) {
                throw new IllegalArgumentException(
                        "Player payment commit has trailing data");
            }
            return new PlayerPaymentCommit(
                    requestId, payerId, recipientId,
                    payerWallet, payerDebt,
                    recipientWallet, recipientDebt, recipientReserved,
                    walletLimit, currencyName, currencyDecimals,
                    transaction, ledger, claim);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Player payment commit is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Player payment commit is invalid", exception);
        }
    }

    public static String fingerprint(PlayerPaymentCommit commit) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(
                    "SHA-256").digest(encode(commit)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable", exception);
        }
    }

    private static void writeComponent(
            DataOutputStream output,
            byte[] value,
            int maximumBytes,
            String label
    ) throws IOException {
        if (value.length <= 0 || value.length > maximumBytes) {
            throw new IllegalArgumentException(
                    label + " exceeds its binary limit");
        }
        output.writeInt(value.length);
        output.write(value);
    }

    private static byte[] readComponent(
            DataInputStream input,
            ByteArrayInputStream bytes,
            int maximumBytes,
            String label
    ) throws IOException {
        int size = input.readInt();
        if (size <= 0 || size > maximumBytes
                || size > bytes.available()) {
            throw new IllegalArgumentException(label + " size is invalid");
        }
        byte[] value = input.readNBytes(size);
        if (value.length != size) {
            throw new EOFException(label + " is truncated");
        }
        return value;
    }
}
