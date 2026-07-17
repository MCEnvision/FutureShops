package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.stock.StockMutationCommand;

import java.util.Objects;
import java.util.UUID;

public sealed interface ServerShopBarterLifecycleEvent permits
        ServerShopBarterLifecycleEvent.Prepare,
        ServerShopBarterLifecycleEvent.Abort,
        ServerShopBarterLifecycleEvent.Commit {

    UUID requestId();

    record Prepare(
            ServerShopBarterIntent intent,
            StockMutationCommand.ReserveBatch stockReservation
    ) implements ServerShopBarterLifecycleEvent {
        public Prepare {
            intent = Objects.requireNonNull(intent, "intent");
            stockReservation = Objects.requireNonNull(
                    stockReservation, "stockReservation");
            if (intent.status()
                    != ServerShopBarterIntent.Status.PREPARED
                    || !intent.stockReservation().equals(
                    stockReservation)) {
                throw new IllegalArgumentException(
                        "Server shop barter prepare transition is invalid");
            }
        }

        @Override
        public UUID requestId() {
            return intent.requestId();
        }
    }

    record Abort(
            ServerShopBarterIntent terminalIntent,
            StockMutationCommand.ResolveBatch stockRelease
    ) implements ServerShopBarterLifecycleEvent {
        public Abort {
            terminalIntent = Objects.requireNonNull(
                    terminalIntent, "terminalIntent");
            stockRelease = Objects.requireNonNull(
                    stockRelease, "stockRelease");
            if (terminalIntent.status()
                    == ServerShopBarterIntent.Status.PREPARED
                    || terminalIntent.status()
                    == ServerShopBarterIntent.Status.COMMITTED
                    || terminalIntent.revision() != 1L
                    || !terminalIntent.stockRelease().equals(
                    stockRelease)) {
                throw new IllegalArgumentException(
                        "Server shop barter abort transition is invalid");
            }
        }

        @Override
        public UUID requestId() {
            return terminalIntent.requestId();
        }
    }

    record Commit(
            ServerShopBarterIntent completedIntent,
            ServerShopBarterCommit commit
    ) implements ServerShopBarterLifecycleEvent {
        public Commit {
            completedIntent = Objects.requireNonNull(
                    completedIntent, "completedIntent");
            commit = Objects.requireNonNull(commit, "commit");
            if (completedIntent.status()
                    != ServerShopBarterIntent.Status.COMMITTED
                    || !completedIntent.requestId().equals(
                    commit.requestId())
                    || !completedIntent.commit(
                    commit.ingredientCustodyReceipt()).equals(commit)) {
                throw new IllegalArgumentException(
                        "Server shop barter commit transition is invalid");
            }
        }

        @Override
        public UUID requestId() {
            return completedIntent.requestId();
        }
    }
}
