package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record AtmPreparedWithdrawal(
        EscrowTransaction createdTransaction,
        EscrowTransaction heldTransaction,
        Optional<AtmWithdrawalCommit> protectedCommit,
        Optional<ForeignAtmWithdrawalCommit> foreignCommit,
        long amountMinorUnits,
        int billCount
) {
    public AtmPreparedWithdrawal {
        Objects.requireNonNull(createdTransaction, "createdTransaction");
        Objects.requireNonNull(heldTransaction, "heldTransaction");
        Objects.requireNonNull(protectedCommit, "protectedCommit");
        Objects.requireNonNull(foreignCommit, "foreignCommit");
        if (protectedCommit.isPresent() == foreignCommit.isPresent()
                || createdTransaction.state() != EscrowState.CREATED
                || heldTransaction.state() != EscrowState.HELD
                || !createdTransaction.transactionId().equals(
                heldTransaction.transactionId())
                || amountMinorUnits <= 0L
                || billCount <= 0
                || billCount
                > ProtectedAtmWithdrawalRequest.MAXIMUM_BILLS) {
            throw new IllegalArgumentException(
                    "Prepared ATM withdrawal is invalid");
        }
        EscrowTransaction decision = protectedCommit
                .map(AtmWithdrawalCommit::committedTransaction)
                .orElseGet(() -> foreignCommit.orElseThrow()
                        .committedTransaction());
        long committedAmount = protectedCommit
                .map(AtmWithdrawalCommit::amountMinorUnits)
                .orElseGet(() -> foreignCommit.orElseThrow()
                        .amountMinorUnits());
        if (!decision.transactionId().equals(
                createdTransaction.transactionId())
                || decision.state() != EscrowState.COMMIT_DECIDED
                || amountMinorUnits != committedAmount) {
            throw new IllegalArgumentException(
                    "Prepared ATM composite does not match");
        }
    }

    public static AtmPreparedWithdrawal protectedPlan(
            ProtectedAtmWithdrawalPlan plan
    ) {
        Objects.requireNonNull(plan, "plan");
        AtmWithdrawalCommit commit = plan.commitFor(
                plan.heldTransaction());
        return new AtmPreparedWithdrawal(
                plan.createdTransaction(), plan.heldTransaction(),
                Optional.of(commit), Optional.empty(),
                plan.request().amountMinorUnits(),
                plan.request().selections().stream()
                        .mapToInt(AtmBillSelection::billCount).sum());
    }

    public static AtmPreparedWithdrawal foreignPlan(
            ForeignAtmWithdrawalPlan plan
    ) {
        Objects.requireNonNull(plan, "plan");
        ForeignAtmWithdrawalCommit commit = plan.commitFor(
                plan.heldTransaction());
        return new AtmPreparedWithdrawal(
                plan.createdTransaction(), plan.heldTransaction(),
                Optional.empty(), Optional.of(commit),
                plan.request().amountMinorUnits(),
                plan.request().billCount());
    }

    public UUID requestId() {
        return createdTransaction.transactionId().value();
    }

    public EscrowTransaction committedTransaction() {
        return protectedCommit
                .map(AtmWithdrawalCommit::committedTransaction)
                .orElseGet(() -> foreignCommit.orElseThrow()
                        .committedTransaction());
    }

}
