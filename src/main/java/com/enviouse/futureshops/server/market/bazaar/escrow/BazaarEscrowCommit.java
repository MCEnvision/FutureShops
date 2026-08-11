package com.enviouse.futureshops.server.market.bazaar.escrow;

import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.market.bazaar.BazaarMutation;
import com.enviouse.futureshops.server.market.bazaar.BazaarOperationType;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record BazaarEscrowCommit(
        UUID requestId,
        UUID orderId,
        BazaarOperationType operation,
        BazaarMutation bookMutation,
        Optional<String> createIntentFingerprint,
        List<BazaarEscrowOrderTransition> orderTransitions,
        String currencyId,
        Instant decidedAt,
        List<EscrowTransaction> completedTransactions,
        List<LedgerTransaction> ledgerTransactions,
        List<EscrowClaim> claims
) {
    public static final int MAX_ORDER_TRANSITIONS = 4_097;
    public static final int MAX_TRANSACTIONS = 4_100;
    public static final int MAX_LEDGER_TRANSACTIONS = 4_100;
    public static final int MAX_CLAIMS = 20_000;

    public BazaarEscrowCommit(
            UUID requestId,
            UUID orderId,
            BazaarOperationType operation,
            BazaarMutation bookMutation,
            Optional<String> createIntentFingerprint,
            List<BazaarEscrowOrderTransition> orderTransitions,
            String currencyId,
            Instant decidedAt,
            List<EscrowTransaction> completedTransactions,
            List<LedgerTransaction> ledgerTransactions,
            List<EscrowClaim> claims
    ) {
        requestId = BazaarEscrowIds.requireId(requestId, "requestId");
        orderId = BazaarEscrowIds.requireId(orderId, "orderId");
        operation = Objects.requireNonNull(operation, "operation");
        bookMutation = Objects.requireNonNull(bookMutation,
                "bookMutation");
        createIntentFingerprint = Objects.requireNonNull(
                createIntentFingerprint, "createIntentFingerprint");
        orderTransitions = Objects.requireNonNull(orderTransitions,
                "orderTransitions").stream().sorted(Comparator.comparing(
                value -> value.orderId().toString())).toList();
        currencyId = BazaarBuyFundingEvidence.requireCurrencyId(currencyId);
        decidedAt = Objects.requireNonNull(decidedAt, "decidedAt");
        completedTransactions = List.copyOf(Objects.requireNonNull(
                completedTransactions, "completedTransactions"));
        ledgerTransactions = List.copyOf(Objects.requireNonNull(
                ledgerTransactions, "ledgerTransactions"));
        claims = Objects.requireNonNull(claims, "claims").stream()
                .sorted(Comparator.comparing(value ->
                        value.claimId().toString())).toList();
        this.requestId = requestId;
        this.orderId = orderId;
        this.operation = operation;
        this.bookMutation = bookMutation;
        this.createIntentFingerprint = createIntentFingerprint;
        this.orderTransitions = orderTransitions;
        this.currencyId = currencyId;
        this.decidedAt = decidedAt;
        this.completedTransactions = completedTransactions;
        this.ledgerTransactions = ledgerTransactions;
        this.claims = claims;
        validateShape();
        BazaarEscrowConservationValidator.validate(this);
    }

    public String fingerprint() {
        return BazaarEscrowCommitCodec.fingerprint(this);
    }

    private void validateShape() {
        if (!bookMutation.mutationId().equals(requestId)
                || bookMutation.requestReceipt().isEmpty()
                || bookMutation.lifecycleCommand().isPresent()
                || !bookMutation.requestReceipt().orElseThrow()
                .result().requestId().equals(requestId)
                || !bookMutation.requestReceipt().orElseThrow()
                .result().orderId().equals(orderId)
                || bookMutation.requestReceipt().orElseThrow()
                .result().operation() != operation
                || orderTransitions.size() > MAX_ORDER_TRANSITIONS
                || completedTransactions.size() > MAX_TRANSACTIONS
                || ledgerTransactions.size() > MAX_LEDGER_TRANSACTIONS
                || claims.size() > MAX_CLAIMS) {
            throw new IllegalArgumentException(
                    "Bazaar escrow commit shape is invalid");
        }
        boolean create = operation == BazaarOperationType.CREATE;
        if (create != createIntentFingerprint.isPresent()
                || createIntentFingerprint.filter(value ->
                !value.matches("[0-9a-f]{64}")).isPresent()) {
            throw new IllegalArgumentException(
                    "Bazaar creation commit fingerprint is invalid");
        }
        Set<UUID> transitionOrders = new HashSet<>();
        for (BazaarEscrowOrderTransition transition : orderTransitions) {
            if (!transitionOrders.add(transition.orderId())) {
                throw new IllegalArgumentException(
                        "Bazaar order transition is duplicated");
            }
        }
        Set<UUID> transactionIds = new HashSet<>();
        for (EscrowTransaction transaction : completedTransactions) {
            UUID transactionId = transaction.transactionId().value();
            if (!transactionIds.add(transactionId)
                    || transaction.state() != EscrowState.COMPLETED) {
                throw new IllegalArgumentException(
                        "Bazaar escrow transaction is invalid");
            }
        }
        Set<UUID> ledgerIds = new HashSet<>();
        Set<String> ledgerKeys = new HashSet<>();
        for (LedgerTransaction ledger : ledgerTransactions) {
            if (!transactionIds.contains(ledger.transactionId())
                    || !ledgerIds.add(ledger.transactionId())
                    || !ledgerKeys.add(ledger.idempotencyKey())) {
                throw new IllegalArgumentException(
                        "Bazaar ledger transaction is invalid");
            }
        }
        Set<UUID> claimIds = new HashSet<>();
        Set<String> claimSources = new HashSet<>();
        for (EscrowClaim claim : claims) {
            if (!transactionIds.contains(claim.transactionId())
                    || !claimIds.add(claim.claimId())
                    || !claimSources.add(claim.sourceKey())) {
                throw new IllegalArgumentException(
                        "Bazaar escrow claim is invalid");
            }
        }
    }
}
