package com.enviouse.futureshops.server.escrow.admin.balance;

import com.enviouse.futureshops.server.economy.TransactionResult;
import com.enviouse.futureshops.server.escrow.admin.AdminAuditConflictException;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAction;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeRecord;
import com.enviouse.futureshops.server.escrow.runtime.LiveAdministrativeBalanceBackend;
import com.enviouse.futureshops.server.shop.ShopResultCode;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public final class AdministrativeBalanceMutationService {
    private static final Object EXECUTION_LOCK = new Object();

    private final AdministrativeBalanceBackend backend;
    private final Clock clock;

    public AdministrativeBalanceMutationService(
            AdministrativeBalanceBackend backend,
            Clock clock
    ) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static AdministrativeBalanceMutationService live() {
        return new AdministrativeBalanceMutationService(
                new LiveAdministrativeBalanceBackend(),
                Clock.systemUTC());
    }

    public AdministrativeBalanceMutationResult execute(
            AdministrativeBalanceMutation mutation
    ) {
        Objects.requireNonNull(mutation, "mutation");
        synchronized (EXECUTION_LOCK) {
            return executeLocked(mutation);
        }
    }

    private AdministrativeBalanceMutationResult executeLocked(
            AdministrativeBalanceMutation mutation
    ) {
        Optional<AdministrativeBalanceEvidence> storedOutcome =
                evidence(mutation,
                        AdministrativeBalanceRequestIds.outcome(
                                mutation.requestId()),
                        AdministrativeBalanceEvidencePhase.OUTCOME);
        if (storedOutcome.isPresent()) {
            AdministrativeBalanceEvidence intent = evidence(mutation,
                    AdministrativeBalanceRequestIds.intent(
                            mutation.requestId()),
                    AdministrativeBalanceEvidencePhase.INTENT)
                    .orElseThrow(() -> new AdminAuditConflictException(
                            "Balance outcome lacks durable intent evidence"));
            AdministrativeBalanceEvidence outcome =
                    storedOutcome.orElseThrow();
            return new AdministrativeBalanceMutationResult(
                    transactionResult(outcome), intent, outcome, true);
        }

        Optional<AdministrativeBalanceEvidence> storedIntent =
                evidence(mutation,
                        AdministrativeBalanceRequestIds.intent(
                                mutation.requestId()),
                        AdministrativeBalanceEvidencePhase.INTENT);
        boolean resumed = storedIntent.isPresent();
        AdministrativeBalanceEvidence intent = storedIntent.orElseGet(
                () -> commitIntent(mutation));

        TransactionResult result;
        if (!mutation.confirmation().confirmed()) {
            result = TransactionResult.error(
                    ShopResultCode.INVALID_REQUEST,
                    intent.balanceBefore());
        } else if (requiresPositiveAmount(mutation.operation())
                && mutation.amountMinor() <= 0L) {
            result = TransactionResult.error(
                    ShopResultCode.INVALID_AMOUNT,
                    intent.balanceBefore());
        } else if (mutation.operation()
                == AdministrativeBalanceOperation.TRANSFER
                && mutation.counterpartyPlayerId().orElseThrow()
                .equals(mutation.targetPlayerId())) {
            result = TransactionResult.error(
                    ShopResultCode.INVALID_TARGET,
                    intent.balanceBefore());
        } else if (mutation.operation()
                == AdministrativeBalanceOperation.DEBIT
                && !mutation.allowNegative()
                && intent.balanceBefore() < mutation.amountMinor()) {
            result = TransactionResult.error(
                    ShopResultCode.INSUFFICIENT_FUNDS,
                    intent.balanceBefore());
        } else {
            result = backend.apply(mutation);
        }

        OptionalLong counterpartyAfter = mutation.counterpartyPlayerId()
                .map(value -> OptionalLong.of(backend.balance(value)))
                .orElseGet(OptionalLong::empty);
        AdministrativeBalanceEvidence outcome =
                new AdministrativeBalanceEvidence(
                        AdministrativeBalanceRequestIds.outcome(
                                mutation.requestId()),
                        mutation.requestId(),
                        mutation.semanticFingerprint(),
                        AdministrativeBalanceEvidencePhase.OUTCOME,
                        mutation.operation(),
                        mutation.targetPlayerId(),
                        mutation.counterpartyPlayerId(),
                        mutation.amountMinor(), mutation.allowNegative(),
                        mutation.confirmation(),
                        intent.counterpartyBalanceBefore(),
                        intent.balanceBefore(),
                        result.resultingBalance(),
                        counterpartyAfter,
                        result.success(), result.errorCode(),
                        Instant.now(clock));
        commitRecord(mutation, outcome);
        return new AdministrativeBalanceMutationResult(
                result, intent, outcome, resumed);
    }

    private AdministrativeBalanceEvidence commitIntent(
            AdministrativeBalanceMutation mutation
    ) {
        long before = backend.balance(mutation.targetPlayerId());
        OptionalLong counterpartyBefore = mutation.counterpartyPlayerId()
                .map(value -> OptionalLong.of(backend.balance(value)))
                .orElseGet(OptionalLong::empty);
        AdministrativeBalanceEvidence intent =
                new AdministrativeBalanceEvidence(
                        AdministrativeBalanceRequestIds.intent(
                                mutation.requestId()),
                        mutation.requestId(),
                        mutation.semanticFingerprint(),
                        AdministrativeBalanceEvidencePhase.INTENT,
                        mutation.operation(), mutation.targetPlayerId(),
                        mutation.counterpartyPlayerId(),
                        mutation.amountMinor(), mutation.allowNegative(),
                        mutation.confirmation(), counterpartyBefore,
                        before, before, counterpartyBefore,
                        false, ShopResultCode.OK, Instant.now(clock));
        commitRecord(mutation, intent);
        return intent;
    }

    private void commitRecord(
            AdministrativeBalanceMutation mutation,
            AdministrativeBalanceEvidence evidence
    ) {
        backend.commitAudit(new EscrowAdministrativeRecord(
                evidence.evidenceId(), mutation.actor(),
                EscrowAdministrativeAction.BALANCE_MUTATION,
                Optional.empty(), mutation.reason(),
                evidence.recordedAt(),
                evidence.phase()
                        == AdministrativeBalanceEvidencePhase.OUTCOME
                        && evidence.successful(),
                AdministrativeBalanceEvidenceCodec.encode(evidence)));
    }

    private Optional<AdministrativeBalanceEvidence> evidence(
            AdministrativeBalanceMutation mutation,
            java.util.UUID evidenceId,
            AdministrativeBalanceEvidencePhase phase
    ) {
        return backend.auditRecord(evidenceId).map(record -> {
            if (!record.requestId().equals(evidenceId)
                    || record.action()
                    != EscrowAdministrativeAction.BALANCE_MUTATION
                    || record.transactionId().isPresent()
                    || !record.actor().equals(mutation.actor())
                    || !record.reason().equals(mutation.reason())) {
                throw new AdminAuditConflictException(
                        "Balance evidence conflicts with its mutation");
            }
            AdministrativeBalanceEvidence evidence;
            try {
                evidence = AdministrativeBalanceEvidenceCodec.decode(
                        record.outcome());
            } catch (IllegalArgumentException exception) {
                throw new AdminAuditConflictException(
                        "Balance evidence is invalid");
            }
            if (!record.createdAt().equals(evidence.recordedAt())
                    || record.successful()
                    != (phase == AdministrativeBalanceEvidencePhase.OUTCOME
                    && evidence.successful())
                    || !evidence.evidenceId().equals(evidenceId)
                    || evidence.phase() != phase
                    || !evidence.mutationRequestId().equals(
                    mutation.requestId())
                    || !evidence.mutationFingerprint().equals(
                    mutation.semanticFingerprint())
                    || !evidence.targetPlayerId().equals(
                    mutation.targetPlayerId())
                    || evidence.operation() != mutation.operation()
                    || !evidence.counterpartyPlayerId().equals(
                    mutation.counterpartyPlayerId())
                    || evidence.amountMinor() != mutation.amountMinor()
                    || evidence.allowNegative() != mutation.allowNegative()
                    || evidence.confirmation()
                    != mutation.confirmation()) {
                throw new AdminAuditConflictException(
                        "Balance evidence conflicts with its mutation");
            }
            return evidence;
        });
    }

    private static TransactionResult transactionResult(
            AdministrativeBalanceEvidence outcome
    ) {
        return outcome.successful()
                ? TransactionResult.ok(outcome.resultingBalance())
                : TransactionResult.error(outcome.resultCode(),
                outcome.resultingBalance());
    }

    private static boolean requiresPositiveAmount(
            AdministrativeBalanceOperation operation
    ) {
        return operation == AdministrativeBalanceOperation.CREDIT
                || operation == AdministrativeBalanceOperation.DEBIT
                || operation == AdministrativeBalanceOperation.TRANSFER;
    }
}
