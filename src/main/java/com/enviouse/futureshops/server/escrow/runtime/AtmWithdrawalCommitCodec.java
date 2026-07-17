package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintEventCodec;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintJournalEvent;
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
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public final class AtmWithdrawalCommitCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES = 8_388_608;

    private static final int MAGIC = 0x46535741;
    private static final int MAX_LEDGER_BYTES = 16_384;
    private static final int MAX_MINT_EVENT_BYTES = 16_384;
    private static final int MAX_CLAIM_BYTES = 16_384;

    private AtmWithdrawalCommitCodec() {
    }

    public static byte[] encode(AtmWithdrawalCommit commit) {
        Objects.requireNonNull(commit, "commit");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            BinaryCodecSupport.writeUuid(output, commit.playerId());
            writeComponent(output,
                    EscrowTransactionByteCodec.encode(commit.committedTransaction()),
                    MAX_ENCODED_BYTES, "ATM withdrawal transaction");
            writeComponent(output, LedgerJournalCodec.encode(commit.ledgerTransaction()),
                    MAX_LEDGER_BYTES, "ATM withdrawal ledger");
            output.writeInt(commit.mintIssues().size());
            for (ProtectedMintJournalEvent issue : commit.mintIssues()) {
                writeComponent(output, ProtectedMintEventCodec.encode(issue),
                        MAX_MINT_EVENT_BYTES, "ATM withdrawal mint issue");
            }
            output.writeInt(commit.cashClaims().size());
            for (EscrowClaim claim : commit.cashClaims()) {
                writeComponent(output, ClaimJournalCodec.encodeClaim(claim),
                        MAX_CLAIM_BYTES, "ATM withdrawal cash claim");
            }
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES
                    || encoded.length > EscrowJournalEventCodec.MAX_BODY_BYTES) {
                throw new IllegalArgumentException(
                        "ATM withdrawal commit exceeds its binary limit");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode ATM withdrawal commit", exception);
        }
    }

    public static AtmWithdrawalCommit decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("ATM withdrawal commit size is invalid");
        }
        ByteArrayInputStream bytes = new ByteArrayInputStream(encoded);
        try (DataInputStream input = new DataInputStream(bytes)) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("ATM withdrawal commit magic is invalid");
            }
            int schema = input.readInt();
            if (schema > CURRENT_SCHEMA) {
                throw new IllegalStateException(
                        "ATM withdrawal commit schema is newer than this build");
            }
            if (schema != CURRENT_SCHEMA) {
                throw new IllegalArgumentException(
                        "ATM withdrawal commit schema is unsupported");
            }
            java.util.UUID playerId = BinaryCodecSupport.readUuid(input);
            EscrowTransaction transaction = EscrowTransactionByteCodec.decode(
                    readComponent(input, bytes, MAX_ENCODED_BYTES,
                            "ATM withdrawal transaction"));
            LedgerTransaction ledger = LedgerJournalCodec.decode(
                    readComponent(input, bytes, MAX_LEDGER_BYTES,
                            "ATM withdrawal ledger"));
            int issueCount = input.readInt();
            if (issueCount <= 0 || issueCount > AtmWithdrawalCommit.MAX_MINT_ISSUES) {
                throw new IllegalArgumentException(
                        "ATM withdrawal mint issue count is invalid");
            }
            List<ProtectedMintJournalEvent> issues = new ArrayList<>(issueCount);
            for (int index = 0; index < issueCount; index++) {
                issues.add(ProtectedMintEventCodec.decode(readComponent(
                        input, bytes, MAX_MINT_EVENT_BYTES,
                        "ATM withdrawal mint issue")));
            }
            int claimCount = input.readInt();
            if (claimCount <= 0 || claimCount > AtmWithdrawalCommit.MAX_CASH_CLAIMS) {
                throw new IllegalArgumentException(
                        "ATM withdrawal cash claim count is invalid");
            }
            List<EscrowClaim> claims = new ArrayList<>(claimCount);
            for (int index = 0; index < claimCount; index++) {
                claims.add(ClaimJournalCodec.decodeClaim(readComponent(
                        input, bytes, MAX_CLAIM_BYTES,
                        "ATM withdrawal cash claim")));
            }
            if (bytes.available() != 0) {
                throw new IllegalArgumentException(
                        "ATM withdrawal commit has trailing data");
            }
            AtmWithdrawalCommit commit = new AtmWithdrawalCommit(
                    playerId, transaction, ledger, issues, claims);
            if (!issues.equals(commit.mintIssues())
                    || !claims.equals(commit.cashClaims())) {
                throw new IllegalArgumentException(
                        "ATM withdrawal commit ordering is not canonical");
            }
            return commit;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("ATM withdrawal commit is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("ATM withdrawal commit is invalid", exception);
        }
    }

    public static String fingerprint(AtmWithdrawalCommit commit) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(encode(commit)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void writeComponent(DataOutputStream output,
                                       byte[] value,
                                       int maximumBytes,
                                       String label) throws IOException {
        if (value.length <= 0 || value.length > maximumBytes) {
            throw new IllegalArgumentException(label + " exceeds its binary limit");
        }
        output.writeInt(value.length);
        output.write(value);
    }

    private static byte[] readComponent(DataInputStream input,
                                        ByteArrayInputStream bytes,
                                        int maximumBytes,
                                        String label) throws IOException {
        int size = input.readInt();
        if (size <= 0 || size > maximumBytes || size > bytes.available()) {
            throw new IllegalArgumentException(label + " size is invalid");
        }
        byte[] value = input.readNBytes(size);
        if (value.length != size) {
            throw new EOFException(label + " is truncated");
        }
        return value;
    }
}
