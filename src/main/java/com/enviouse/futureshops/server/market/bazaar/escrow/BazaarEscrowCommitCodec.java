package com.enviouse.futureshops.server.market.bazaar.escrow;

import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.runtime.ClaimJournalCodec;
import com.enviouse.futureshops.server.escrow.runtime.EscrowJournalEventCodec;
import com.enviouse.futureshops.server.escrow.runtime.LedgerJournalCodec;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionByteCodec;
import com.enviouse.futureshops.server.market.bazaar.BazaarMutation;
import com.enviouse.futureshops.server.market.bazaar.BazaarMutationCodec;
import com.enviouse.futureshops.server.market.bazaar.BazaarOperationType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class BazaarEscrowCommitCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES =
            EscrowJournalEventCodec.MAX_BODY_BYTES - 64;

    private static final int MAGIC = 0x425A434D;

    private BazaarEscrowCommitCodec() {
    }

    public static byte[] encode(BazaarEscrowCommit commit) {
        Objects.requireNonNull(commit, "commit");
        byte[] payload = payloadBytes(commit);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            BazaarEscrowBinarySupport.writeBytes(output, payload,
                    MAX_ENCODED_BYTES);
            BazaarEscrowBinarySupport.writeText(output,
                    fingerprint(commit));
            output.flush();
            byte[] result = bytes.toByteArray();
            requireSize(result);
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode Bazaar escrow commit", exception);
        }
    }

    public static BazaarEscrowCommit decode(byte[] encoded) {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        requireSize(copy);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(copy))) {
            if (input.readInt() != MAGIC) {
                throw invalid("Bazaar escrow commit magic is invalid");
            }
            if (input.readInt() != CURRENT_SCHEMA) {
                throw invalid(
                        "Bazaar escrow commit schema is unsupported");
            }
            byte[] payload = BazaarEscrowBinarySupport.readBytes(input,
                    MAX_ENCODED_BYTES);
            String storedFingerprint = BazaarEscrowBinarySupport.readText(
                    input, 64, false);
            if (input.read() != -1) {
                throw invalid("Bazaar escrow commit has trailing data");
            }
            BazaarEscrowCommit result = decodePayload(payload);
            if (!storedFingerprint.equals(fingerprint(result))
                    || !Arrays.equals(copy, encode(result))) {
                throw invalid(
                        "Bazaar escrow commit encoding is not canonical");
            }
            return result;
        } catch (EOFException exception) {
            throw invalid("Bazaar escrow commit is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw invalid("Bazaar escrow commit is invalid", exception);
        }
    }

    static String fingerprint(BazaarEscrowCommit commit) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(payloadBytes(commit)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable",
                    exception);
        }
    }

    private static byte[] payloadBytes(BazaarEscrowCommit commit) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            BazaarEscrowBinarySupport.writeUuid(output,
                    commit.requestId());
            BazaarEscrowBinarySupport.writeUuid(output, commit.orderId());
            output.writeInt(commit.operation().wireCode());
            BazaarEscrowBinarySupport.writeBytes(output,
                    BazaarMutationCodec.encode(commit.bookMutation()),
                    BazaarMutationCodec.MAX_ENCODED_BYTES);
            output.writeBoolean(commit.createIntentFingerprint().isPresent());
            if (commit.createIntentFingerprint().isPresent()) {
                BazaarEscrowBinarySupport.writeText(output,
                        commit.createIntentFingerprint().orElseThrow());
            }
            output.writeInt(commit.orderTransitions().size());
            for (BazaarEscrowOrderTransition transition
                    : commit.orderTransitions()) {
                writeTransition(output, transition);
            }
            BazaarEscrowBinarySupport.writeText(output,
                    commit.currencyId());
            BazaarEscrowBinarySupport.writeInstant(output,
                    commit.decidedAt());
            output.writeInt(commit.completedTransactions().size());
            for (EscrowTransaction transaction
                    : commit.completedTransactions()) {
                BazaarEscrowBinarySupport.writeBytes(output,
                        EscrowTransactionByteCodec.encode(transaction),
                        EscrowTransactionByteCodec.MAX_ENCODED_BYTES);
            }
            output.writeInt(commit.ledgerTransactions().size());
            for (LedgerTransaction ledger : commit.ledgerTransactions()) {
                BazaarEscrowBinarySupport.writeBytes(output,
                        LedgerJournalCodec.encode(ledger),
                        EscrowJournalEventCodec.MAX_BODY_BYTES);
            }
            output.writeInt(commit.claims().size());
            for (EscrowClaim claim : commit.claims()) {
                BazaarEscrowBinarySupport.writeBytes(output,
                        ClaimJournalCodec.encodeClaim(claim),
                        EscrowJournalEventCodec.MAX_BODY_BYTES);
            }
            output.flush();
            byte[] result = bytes.toByteArray();
            requireSize(result);
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode Bazaar escrow commit payload",
                    exception);
        }
    }

    private static BazaarEscrowCommit decodePayload(byte[] payload)
            throws IOException {
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(payload))) {
            UUID requestId = BazaarEscrowBinarySupport.readUuid(input);
            UUID orderId = BazaarEscrowBinarySupport.readUuid(input);
            BazaarOperationType operation =
                    BazaarOperationType.fromWireCode(input.readInt());
            BazaarMutation mutation = BazaarMutationCodec.decode(
                    BazaarEscrowBinarySupport.readBytes(input,
                            BazaarMutationCodec.MAX_ENCODED_BYTES));
            Optional<String> createFingerprint = input.readBoolean()
                    ? Optional.of(BazaarEscrowBinarySupport.readText(input,
                    64, false)) : Optional.empty();
            int transitionCount = input.readInt();
            if (transitionCount < 0 || transitionCount
                    > BazaarEscrowCommit.MAX_ORDER_TRANSITIONS) {
                throw invalid(
                        "Bazaar order transition count is invalid");
            }
            List<BazaarEscrowOrderTransition> transitions =
                    new ArrayList<>(transitionCount);
            for (int index = 0; index < transitionCount; index++) {
                transitions.add(readTransition(input));
            }
            String currencyId = BazaarEscrowBinarySupport.readText(input,
                    BazaarBuyFundingEvidence.MAX_CURRENCY_ID_LENGTH,
                    false);
            Instant decidedAt = BazaarEscrowBinarySupport.readInstant(
                    input);
            int transactionCount = input.readInt();
            if (transactionCount < 0 || transactionCount
                    > BazaarEscrowCommit.MAX_TRANSACTIONS) {
                throw invalid("Bazaar transaction count is invalid");
            }
            List<EscrowTransaction> transactions = new ArrayList<>(
                    transactionCount);
            for (int index = 0; index < transactionCount; index++) {
                transactions.add(EscrowTransactionByteCodec.decode(
                        BazaarEscrowBinarySupport.readBytes(input,
                                EscrowTransactionByteCodec
                                        .MAX_ENCODED_BYTES)));
            }
            int ledgerCount = input.readInt();
            if (ledgerCount < 0 || ledgerCount
                    > BazaarEscrowCommit.MAX_LEDGER_TRANSACTIONS) {
                throw invalid("Bazaar ledger count is invalid");
            }
            List<LedgerTransaction> ledgers = new ArrayList<>(ledgerCount);
            for (int index = 0; index < ledgerCount; index++) {
                ledgers.add(LedgerJournalCodec.decode(
                        BazaarEscrowBinarySupport.readBytes(input,
                                EscrowJournalEventCodec.MAX_BODY_BYTES)));
            }
            int claimCount = input.readInt();
            if (claimCount < 0 || claimCount
                    > BazaarEscrowCommit.MAX_CLAIMS) {
                throw invalid("Bazaar claim count is invalid");
            }
            List<EscrowClaim> claims = new ArrayList<>(claimCount);
            for (int index = 0; index < claimCount; index++) {
                claims.add(ClaimJournalCodec.decodeClaim(
                        BazaarEscrowBinarySupport.readBytes(input,
                                EscrowJournalEventCodec.MAX_BODY_BYTES)));
            }
            if (input.read() != -1) {
                throw invalid(
                        "Bazaar escrow commit payload has trailing data");
            }
            return new BazaarEscrowCommit(requestId, orderId, operation,
                    mutation, createFingerprint, transitions, currencyId,
                    decidedAt, transactions, ledgers, claims);
        }
    }

    private static void writeTransition(
            DataOutputStream output,
            BazaarEscrowOrderTransition transition
    ) throws IOException {
        BazaarEscrowBinarySupport.writeUuid(output, transition.orderId());
        output.writeBoolean(transition.beforeOrder().isPresent());
        if (transition.beforeOrder().isPresent()) {
            BazaarEscrowBinarySupport.writeView(output,
                    transition.beforeOrder().orElseThrow());
        }
        output.writeBoolean(transition.afterOrder().isPresent());
        if (transition.afterOrder().isPresent()) {
            BazaarEscrowBinarySupport.writeView(output,
                    transition.afterOrder().orElseThrow());
        }
        output.writeBoolean(transition.beforeBacking().isPresent());
        if (transition.beforeBacking().isPresent()) {
            BazaarEscrowBinarySupport.writeBacking(output,
                    transition.beforeBacking().orElseThrow());
        }
        output.writeBoolean(transition.afterBacking().isPresent());
        if (transition.afterBacking().isPresent()) {
            BazaarEscrowBinarySupport.writeBacking(output,
                    transition.afterBacking().orElseThrow());
        }
    }

    private static BazaarEscrowOrderTransition readTransition(
            DataInputStream input
    ) throws IOException {
        UUID orderId = BazaarEscrowBinarySupport.readUuid(input);
        Optional<BazaarEscrowOrderView> beforeOrder = input.readBoolean()
                ? Optional.of(BazaarEscrowBinarySupport.readView(input))
                : Optional.empty();
        Optional<BazaarEscrowOrderView> afterOrder = input.readBoolean()
                ? Optional.of(BazaarEscrowBinarySupport.readView(input))
                : Optional.empty();
        Optional<BazaarEscrowOrderBacking> beforeBacking =
                input.readBoolean()
                        ? Optional.of(BazaarEscrowBinarySupport
                        .readBacking(input)) : Optional.empty();
        Optional<BazaarEscrowOrderBacking> afterBacking =
                input.readBoolean()
                        ? Optional.of(BazaarEscrowBinarySupport
                        .readBacking(input)) : Optional.empty();
        return new BazaarEscrowOrderTransition(orderId, beforeOrder,
                afterOrder, beforeBacking, afterBacking);
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw invalid("Bazaar escrow commit size is invalid");
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
