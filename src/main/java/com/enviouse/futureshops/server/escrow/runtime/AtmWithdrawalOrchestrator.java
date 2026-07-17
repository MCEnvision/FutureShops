package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLot;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLotType;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowParty;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

final class AtmWithdrawalOrchestrator {
    private static final ThreadLocal<Set<UUID>> ACTIVE_PLAYERS =
            ThreadLocal.withInitial(HashSet::new);

    private final AtmWithdrawalBackend backend;
    private final AtmBalanceEventGateway events;
    private final Clock clock;
    private final AtmCurrencyConfigurationLeaseProvider currencyConfiguration;

    AtmWithdrawalOrchestrator(
            AtmWithdrawalBackend backend,
            AtmBalanceEventGateway events,
            Clock clock,
            AtmCurrencyConfigurationLeaseProvider currencyConfiguration
    ) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.currencyConfiguration = Objects.requireNonNull(
                currencyConfiguration, "currencyConfiguration");
    }

    AtmWithdrawalOutcome submit(
            UUID requestId,
            UUID playerId,
            String currencySignature,
            List<Integer> denominationCounts,
            Supplier<AtmPreparedWithdrawal> preparation
    ) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(currencySignature, "currencySignature");
        Objects.requireNonNull(denominationCounts, "denominationCounts");
        Objects.requireNonNull(preparation, "preparation");
        Set<UUID> active = ACTIVE_PLAYERS.get();
        if (!active.add(playerId)) {
            return failure(requestId, AtmWithdrawalStatus.SERVER_ERROR,
                    false, false, playerId, 0L, 0,
                    currencySignature);
        }
        try {
            Optional<EscrowTransaction> existing =
                    backend.transaction(requestId);
            if (existing.isPresent()) {
                return existingResult(existing.orElseThrow(), playerId,
                        currencySignature, denominationCounts);
            }
            if (!backend.migrationComplete()) {
                return failure(requestId,
                        AtmWithdrawalStatus.MIGRATION_PENDING,
                        true, false, playerId, 0L, 0,
                        currencySignature);
            }
            if (backend.runtimeState() != EscrowRuntimeState.READY) {
                return failure(requestId,
                        unavailableStatus(backend.runtimeState()),
                        true, false, playerId, 0L, 0,
                        currencySignature);
            }
            AtmPreparedWithdrawal plan;
            long preparedGeneration;
            AtmCurrencyConfigurationLease preparationLease;
            try {
                preparationLease = currencyConfiguration.acquire();
            } catch (RuntimeException exception) {
                return failure(requestId,
                        AtmWithdrawalStatus.CURRENCY_CHANGED,
                        false, false, playerId, 0L, 0,
                        currencySignature);
            }
            try (AtmCurrencyConfigurationLease lease = preparationLease) {
                if (!currencySignature.equals(
                        lease.currencySignature())) {
                    return failure(requestId,
                            AtmWithdrawalStatus.CURRENCY_CHANGED,
                            false, false, playerId, 0L, 0,
                            currencySignature);
                }
                preparedGeneration = lease.generation();
                plan = Objects.requireNonNull(
                        preparation.get(), "preparedWithdrawal");
            } catch (AtmPreparationException exception) {
                return failure(requestId, exception.status(), false,
                        false, playerId, 0L, 0,
                        currencySignature);
            } catch (RuntimeException exception) {
                return failure(requestId,
                        AtmWithdrawalStatus.INVALID_PLAN,
                        false, false, playerId, 0L, 0,
                        currencySignature);
            }
            if (!plan.requestId().equals(requestId)
                    || !matchesSemantics(plan.createdTransaction(),
                    playerId, currencySignature, denominationCounts)) {
                return failure(requestId, AtmWithdrawalStatus.CONFLICT,
                        false, false, playerId, 0L, 0,
                        currencySignature);
            }
            long balance = backend.balance(playerId);
            if (balance < plan.amountMinorUnits()) {
                return failureKnown(requestId,
                        AtmWithdrawalStatus.INSUFFICIENT_FUNDS,
                        false, false, balance, plan.amountMinorUnits(),
                        0, currencySignature);
            }
            if (events.beforeDebit(
                    playerId, plan.amountMinorUnits(), balance)) {
                return failureKnown(requestId,
                        AtmWithdrawalStatus.CANCELLED,
                        false, false, balance, plan.amountMinorUnits(),
                        0, currencySignature);
            }
            EscrowCommitResult composite;
            AtmCurrencyConfigurationLease commitLease;
            try {
                commitLease = currencyConfiguration.acquire();
            } catch (RuntimeException exception) {
                return failureKnown(requestId,
                        AtmWithdrawalStatus.CURRENCY_CHANGED,
                        false, false, balance,
                        plan.amountMinorUnits(), 0,
                        currencySignature);
            }
            try (AtmCurrencyConfigurationLease lease = commitLease) {
                if (preparedGeneration != lease.generation()
                        || !currencySignature.equals(
                        lease.currencySignature())) {
                    return failureKnown(requestId,
                            AtmWithdrawalStatus.CURRENCY_CHANGED,
                            false, false, balance,
                            plan.amountMinorUnits(), 0,
                            currencySignature);
                }
                try {
                    persistHeld(plan);
                } catch (RuntimeException exception) {
                    return pendingOrUnavailable(
                            requestId, playerId, currencySignature,
                            plan.amountMinorUnits());
                }
                try {
                    composite = plan.protectedCommit().isPresent()
                            ? backend.commitProtected(
                            plan.protectedCommit().orElseThrow())
                            : backend.commitForeign(
                            plan.foreignCommit().orElseThrow());
                } catch (RuntimeException exception) {
                    if (abortIfSafe(requestId)) {
                        return failure(requestId,
                                AtmWithdrawalStatus.SERVER_ERROR,
                                false, false, playerId,
                                plan.amountMinorUnits(), 0,
                                currencySignature);
                    }
                    return pendingOrUnavailable(
                            requestId, playerId, currencySignature,
                            plan.amountMinorUnits());
                }
            } catch (RuntimeException exception) {
                return pendingOrUnavailable(
                        requestId, playerId, currencySignature,
                        plan.amountMinorUnits());
            }
            long resultingBalance = backend.balance(playerId);
            if (!composite.replayed()) {
                events.afterDebit(playerId, plan.amountMinorUnits(),
                        resultingBalance);
            }
            try {
                complete(plan.committedTransaction());
            } catch (RuntimeException exception) {
                return failureKnown(requestId,
                        AtmWithdrawalStatus.RECOVERY_PENDING,
                        true, composite.replayed(), resultingBalance,
                        plan.amountMinorUnits(), plan.billCount(),
                        currencySignature);
            }
            return new AtmWithdrawalOutcome(
                    requestId, AtmWithdrawalStatus.CLAIMED,
                    false, composite.replayed(), true,
                    resultingBalance, plan.amountMinorUnits(),
                    0, plan.billCount(), currencySignature);
        } catch (RuntimeException exception) {
            return failure(requestId, AtmWithdrawalStatus.SERVER_ERROR,
                    true, false, playerId, 0L, 0,
                    currencySignature);
        } finally {
            active.remove(playerId);
            if (active.isEmpty()) {
                ACTIVE_PLAYERS.remove();
            }
        }
    }

    private AtmWithdrawalOutcome existingResult(
            EscrowTransaction transaction,
            UUID playerId,
            String signature,
            List<Integer> counts
    ) {
        if (!matchesSemantics(transaction, playerId, signature, counts)) {
            return failure(transaction.transactionId().value(),
                    AtmWithdrawalStatus.CONFLICT,
                    false, true, playerId, 0L,
                    0, signature);
        }
        long amount = amount(transaction);
        List<EscrowClaim> persistedClaims = backend.claims(
                transaction.transactionId().value());
        if (requiresNoClaims(transaction.state())
                && !persistedClaims.isEmpty()) {
            throw new IllegalArgumentException(
                    "ATM transaction state cannot contain cash claims");
        }
        ClaimCounts claimCounts = requiresExactClaims(
                transaction.state(), persistedClaims)
                ? claimCounts(persistedClaims,
                transaction.transactionId().value(), playerId,
                amount, selectedBillCount(counts))
                : new ClaimCounts(0, 0, false);
        return switch (transaction.state()) {
            case COMPLETED -> completedOutcome(
                    transaction.transactionId().value(), playerId,
                    amount, claimCounts, signature);
            case REFUNDED -> failure(
                    transaction.transactionId().value(),
                    AtmWithdrawalStatus.CANCELLED,
                    false, true, playerId, amount, 0, signature);
            case MANUAL_REVIEW -> failure(
                    transaction.transactionId().value(),
                    AtmWithdrawalStatus.MANUAL_REVIEW,
                    false, true, playerId, amount,
                    claimCounts.remaining(), signature);
            default -> failure(
                    transaction.transactionId().value(),
                    AtmWithdrawalStatus.RECOVERY_PENDING,
                    true, true, playerId, amount,
                    claimCounts.remaining(), signature);
        };
    }

    private AtmWithdrawalOutcome completedOutcome(
            UUID requestId,
            UUID playerId,
            long amount,
            ClaimCounts claims,
            String signature
    ) {
        int delivered = Math.subtractExact(
                claims.original(), claims.remaining());
        if (claims.quarantined()) {
            return failureKnown(requestId,
                    AtmWithdrawalStatus.MANUAL_REVIEW,
                    false, true, backend.balance(playerId), amount,
                    claims.remaining(), signature);
        }
        AtmWithdrawalStatus status = delivered == 0
                ? AtmWithdrawalStatus.CLAIMED
                : claims.remaining() == 0
                ? AtmWithdrawalStatus.DELIVERED
                : AtmWithdrawalStatus.PARTIALLY_DELIVERED;
        return new AtmWithdrawalOutcome(
                requestId, status, false, true, true,
                backend.balance(playerId), amount,
                delivered, claims.remaining(), signature);
    }

    private void persistHeld(AtmPreparedWithdrawal plan) {
        EscrowTransaction created = plan.createdTransaction();
        backend.commitTransaction(created);
        Instant at = created.timestamps().updatedAt();
        EscrowTransaction validated = created.transitionTo(
                EscrowState.VALIDATED, at);
        backend.commitTransaction(validated);
        EscrowTransaction holding = validated.transitionTo(
                EscrowState.HOLDING, at);
        backend.commitTransaction(holding);
        EscrowTransaction held = holding.transitionTo(
                EscrowState.HELD, at);
        if (!held.equals(plan.heldTransaction())) {
            throw new IllegalArgumentException(
                    "Prepared ATM held state changed");
        }
        backend.commitTransaction(held);
    }

    private void complete(EscrowTransaction decision) {
        Instant at = laterOf(
                decision.timestamps().updatedAt(), clock.instant());
        EscrowTransaction committed = decision.transitionTo(
                EscrowState.COMMITTED, at);
        backend.commitTransaction(committed);
        EscrowTransaction claimsCreated = committed.transitionTo(
                EscrowState.CLAIMS_CREATED, at);
        backend.commitTransaction(claimsCreated);
        backend.commitTransaction(claimsCreated.transitionTo(
                EscrowState.COMPLETED, at));
    }

    private boolean abortIfSafe(UUID requestId) {
        if (backend.runtimeState() != EscrowRuntimeState.READY
                || !backend.claims(requestId).isEmpty()) {
            return false;
        }
        Optional<EscrowTransaction> found = backend.transaction(requestId);
        if (found.isEmpty()) {
            return true;
        }
        EscrowTransaction current = found.orElseThrow();
        if (current.state().requiresCommitDecision()
                || current.state().isTerminal()) {
            return false;
        }
        Instant at = laterOf(
                current.timestamps().updatedAt(), clock.instant());
        try {
            if (current.state() != EscrowState.ABORTING
                    && current.state() != EscrowState.REFUND_PENDING) {
                current = current.transitionTo(EscrowState.ABORTING, at);
                backend.commitTransaction(current);
            }
            if (current.state() == EscrowState.ABORTING) {
                current = current.transitionTo(
                        EscrowState.REFUND_PENDING, at);
                backend.commitTransaction(current);
            }
            backend.commitTransaction(current.transitionTo(
                    EscrowState.REFUNDED, at));
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private AtmWithdrawalOutcome pendingOrUnavailable(
            UUID requestId,
            UUID playerId,
            String signature,
            long amount
    ) {
        AtmWithdrawalStatus status = unavailableStatus(
                backend.runtimeState());
        return failure(requestId, status, true, false,
                playerId, amount, 0, signature);
    }

    private AtmWithdrawalOutcome failure(
            UUID requestId,
            AtmWithdrawalStatus status,
            boolean retryable,
            boolean replayed,
            UUID playerId,
            long amount,
            int claimed,
            String signature
    ) {
        try {
            return failureKnown(requestId, status, retryable, replayed,
                    backend.balance(playerId), amount, claimed, signature);
        } catch (RuntimeException exception) {
            return AtmWithdrawalOutcome.failure(
                    requestId, status, retryable, replayed,
                    false, 0L, amount, claimed, signature);
        }
    }

    private static AtmWithdrawalOutcome failureKnown(
            UUID requestId,
            AtmWithdrawalStatus status,
            boolean retryable,
            boolean replayed,
            long balance,
            long amount,
            int claimed,
            String signature
    ) {
        return AtmWithdrawalOutcome.failure(
                requestId, status, retryable, replayed,
                true, balance, amount, claimed, signature);
    }

    private static AtmWithdrawalStatus unavailableStatus(
            EscrowRuntimeState state
    ) {
        return state == EscrowRuntimeState.RECOVERING
                || state == EscrowRuntimeState.STARTING
                ? AtmWithdrawalStatus.RECOVERY_PENDING
                : AtmWithdrawalStatus.ESCROW_UNAVAILABLE;
    }

    private static boolean matchesSemantics(
            EscrowTransaction transaction,
            UUID playerId,
            String signature,
            List<Integer> counts
    ) {
        try {
            if (transaction.operation() != EscrowOperation.ATM_WITHDRAWAL
                    || transaction.participants().stream().noneMatch(
                    participant -> participant.party().equals(
                            EscrowParty.player(playerId)))) {
                return false;
            }
            Map<String, String> attributes = walletAsset(transaction)
                    .attributes();
            return signature.equals(attributes.get(
                    ProtectedAtmWithdrawalPlan.SIGNATURE_ATTRIBUTE))
                    && AtmRequestSemantics.matchesCounts(
                    attributes.get(
                            ProtectedAtmWithdrawalPlan
                                    .SELECTION_SHAPE_ATTRIBUTE),
                    counts);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static long amount(EscrowTransaction transaction) {
        return walletAsset(transaction).money().orElseThrow().minorUnits();
    }

    private static EscrowAssetLot walletAsset(
            EscrowTransaction transaction
    ) {
        List<EscrowAssetLot> wallets = transaction.assetLots().stream()
                .filter(lot -> lot.type() == EscrowAssetLotType.WALLET_MONEY)
                .toList();
        if (wallets.size() != 1) {
            throw new IllegalArgumentException(
                    "ATM transaction wallet asset is invalid");
        }
        return wallets.get(0);
    }

    private static boolean requiresExactClaims(
            EscrowState state,
            List<EscrowClaim> claims
    ) {
        return state.requiresCommitDecision()
                || state == EscrowState.MANUAL_REVIEW
                || state == EscrowState.RECOVERY_REQUIRED
                && !claims.isEmpty();
    }

    private static boolean requiresNoClaims(EscrowState state) {
        return switch (state) {
            case CREATED, VALIDATED, HOLDING, HELD, ABORTING,
                    REFUND_PENDING, REFUNDED -> true;
            default -> false;
        };
    }

    private static int selectedBillCount(List<Integer> counts) {
        int total = 0;
        for (Integer count : counts) {
            if (count == null || count < 0) {
                throw new IllegalArgumentException(
                        "ATM request bill count is invalid");
            }
            total = Math.addExact(total, count);
        }
        if (total <= 0
                || total > ProtectedAtmWithdrawalRequest.MAXIMUM_BILLS) {
            throw new IllegalArgumentException(
                    "ATM request bill count is invalid");
        }
        return total;
    }

    private static ClaimCounts claimCounts(
            List<EscrowClaim> claims,
            UUID transactionId,
            UUID playerId,
            long expectedTotalUnits,
            int expectedBills
    ) {
        long original = 0L;
        long remaining = 0L;
        long originalUnits = 0L;
        boolean quarantined = false;
        Set<UUID> claimIds = new HashSet<>();
        for (EscrowClaim claim : claims) {
            if (!claim.transactionId().equals(transactionId)
                    || !claim.ownerId().equals(playerId)
                    || !claimIds.add(claim.claimId())) {
                throw new IllegalArgumentException(
                        "ATM cash claim identity is invalid");
            }
            int billCount;
            long claimUnits;
            if (claim.kind() == ClaimKind.PROTECTED_CASH) {
                ProtectedCashClaimPayload payload =
                        ProtectedCashClaimPayloadCodec.decode(
                                claim.payload());
                billCount = payload.billCount();
                claimUnits = Math.multiplyExact(
                        payload.denominationMinorUnits(),
                        (long) payload.billCount());
            } else if (claim.kind() == ClaimKind.FOREIGN_CASH) {
                ForeignCashClaimPayload payload =
                        ForeignCashClaimPayloadCodec.decode(
                                claim.payload());
                billCount = payload.stackCount();
                claimUnits = Math.multiplyExact(
                        payload.denominationMinorUnits(),
                        (long) payload.stackCount());
            } else {
                throw new IllegalArgumentException(
                        "ATM transaction contains a non cash claim");
            }
            if (claim.originalUnits() != claimUnits
                    || (claim.status() == ClaimStatus.COMPLETED
                    && claim.remainingUnits() != 0L)
                    || (claim.status() != ClaimStatus.COMPLETED
                    && claim.remainingUnits() != claimUnits)
                    || claim.status() == ClaimStatus.PARTIALLY_DELIVERED) {
                throw new IllegalArgumentException(
                        "ATM cash claim is not an all or nothing stack");
            }
            quarantined |= claim.status() == ClaimStatus.QUARANTINED;
            original = Math.addExact(original, billCount);
            originalUnits = Math.addExact(originalUnits, claimUnits);
            if (claim.status() != ClaimStatus.COMPLETED) {
                remaining = Math.addExact(remaining, billCount);
            }
        }
        if (original < 0L
                || original > ProtectedAtmWithdrawalRequest.MAXIMUM_BILLS
                || remaining < 0L
                || remaining > original
                || originalUnits != expectedTotalUnits
                || original != expectedBills) {
            throw new IllegalArgumentException(
                    "ATM claim bill count is invalid");
        }
        return new ClaimCounts(
                Math.toIntExact(original), Math.toIntExact(remaining),
                quarantined);
    }

    private static Instant laterOf(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }

    private record ClaimCounts(
            int original,
            int remaining,
            boolean quarantined
    ) {
    }
}
