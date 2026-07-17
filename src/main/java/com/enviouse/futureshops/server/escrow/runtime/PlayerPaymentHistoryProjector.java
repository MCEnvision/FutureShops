package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import com.enviouse.futureshops.server.transaction.TransactionHistoryService;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

final class PlayerPaymentHistoryProjector {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final EscrowTransactionSavedData transactions;
    private final LedgerSavedData ledger;
    private final ClaimSavedData claims;
    private final Consumer<PlayerPaymentCommit> sink;
    private Optional<EscrowTransactionId> cursor = Optional.empty();
    private boolean complete;

    PlayerPaymentHistoryProjector(
            MinecraftServer server,
            EscrowTransactionSavedData transactions,
            LedgerSavedData ledger,
            ClaimSavedData claims
    ) {
        this(transactions, ledger, claims,
                commit -> reconcile(Objects.requireNonNull(
                        server, "server"), commit));
    }

    PlayerPaymentHistoryProjector(
            EscrowTransactionSavedData transactions,
            LedgerSavedData ledger,
            ClaimSavedData claims,
            Consumer<PlayerPaymentCommit> sink
    ) {
        this.transactions = Objects.requireNonNull(
                transactions, "transactions");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    int reconcileBatch(int limit) {
        if (complete || limit <= 0) {
            return 0;
        }
        List<EscrowTransaction> batch = transactions.transactionsAfter(
                cursor, limit);
        int inspected = 0;
        for (EscrowTransaction transaction : batch) {
            inspected++;
            if (transaction.operation() != EscrowOperation.PLAYER_PAYMENT
                    || transaction.state() != EscrowState.COMPLETED) {
                cursor = Optional.of(transaction.transactionId());
                continue;
            }
            if (!reconcileTransaction(transaction)) {
                return inspected;
            }
            cursor = Optional.of(transaction.transactionId());
        }
        complete = batch.size() < limit;
        return batch.size();
    }

    boolean complete() {
        return complete;
    }

    private boolean reconcileTransaction(EscrowTransaction transaction) {
        PlayerPaymentCommit commit;
        try {
            commit = PlayerPaymentCommit.fromEvidence(
                    transaction,
                    ledger.transactionReceipt(
                            transaction.transactionId().value())
                            .orElseThrow().transaction(),
                    claims.claimsForTransaction(
                            transaction.transactionId().value()));
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Player payment history evidence is invalid for {}",
                    transaction.transactionId().value(), exception);
            return true;
        }
        try {
            sink.accept(commit);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Player payment history reconciliation failed for {}",
                    transaction.transactionId().value(), exception);
            return false;
        }
        return true;
    }

    static void reconcile(
            MinecraftServer server,
            PlayerPaymentCommit commit
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(commit, "commit");
        TransactionHistoryService.recordPlayerPayment(
                server, commit.payerId(), commit.recipientId(),
                commit.transactionId(), commit.amountMinorUnits(),
                commit.completedTransaction().timestamps().terminalAt()
                        .orElseThrow());
    }
}
