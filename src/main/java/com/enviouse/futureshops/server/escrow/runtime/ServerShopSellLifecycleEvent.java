package com.enviouse.futureshops.server.escrow.runtime;

import java.util.Objects;
import java.util.UUID;

public sealed interface ServerShopSellLifecycleEvent permits
        ServerShopSellLifecycleEvent.Prepare,
        ServerShopSellLifecycleEvent.Abort,
        ServerShopSellLifecycleEvent.Commit {

    UUID requestId();

    record Prepare(
            ServerShopSellIntent intent
    ) implements ServerShopSellLifecycleEvent {
        public Prepare {
            intent = Objects.requireNonNull(intent, "intent");
            if (intent.status() != ServerShopSellIntent.Status.PREPARED) {
                throw new IllegalArgumentException(
                        "Server shop sell prepare intent is not prepared");
            }
        }

        @Override
        public UUID requestId() {
            return intent.requestId();
        }
    }

    record Abort(
            ServerShopSellIntent expectedIntent,
            ServerShopSellIntent terminalIntent
    ) implements ServerShopSellLifecycleEvent {
        public Abort {
            expectedIntent = Objects.requireNonNull(
                    expectedIntent, "expectedIntent");
            terminalIntent = Objects.requireNonNull(
                    terminalIntent, "terminalIntent");
            if (expectedIntent.status()
                    != ServerShopSellIntent.Status.PREPARED
                    || terminalIntent.status()
                    == ServerShopSellIntent.Status.PREPARED
                    || terminalIntent.status()
                    == ServerShopSellIntent.Status.COMMITTED
                    || !expectedIntent.requestId().equals(
                    terminalIntent.requestId())
                    || !expectedIntent.intentFingerprint().equals(
                    terminalIntent.intentFingerprint())
                    || terminalIntent.revision() != 1L) {
                throw new IllegalArgumentException(
                        "Server shop sell abort transition is invalid");
            }
        }

        @Override
        public UUID requestId() {
            return expectedIntent.requestId();
        }
    }

    record Commit(
            ServerShopSellIntent completedIntent,
            ServerShopSellCommit commit
    ) implements ServerShopSellLifecycleEvent {
        public Commit {
            completedIntent = Objects.requireNonNull(
                    completedIntent, "completedIntent");
            commit = Objects.requireNonNull(commit, "commit");
            if (completedIntent.status()
                    != ServerShopSellIntent.Status.COMMITTED
                    || !completedIntent.requestId().equals(
                    commit.requestId())
                    || !completedIntent.commit(
                    commit.itemCustodyReceipt()).equals(commit)) {
                throw new IllegalArgumentException(
                        "Server shop sell commit transition is invalid");
            }
        }

        @Override
        public UUID requestId() {
            return completedIntent.requestId();
        }
    }
}
