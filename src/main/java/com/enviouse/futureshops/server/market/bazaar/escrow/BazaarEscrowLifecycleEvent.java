package com.enviouse.futureshops.server.market.bazaar.escrow;

import com.enviouse.futureshops.server.market.bazaar.BazaarOperationStatus;
import com.enviouse.futureshops.server.market.bazaar.BazaarOperationType;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public sealed interface BazaarEscrowLifecycleEvent permits
        BazaarEscrowLifecycleEvent.Prepare,
        BazaarEscrowLifecycleEvent.Resolve,
        BazaarEscrowLifecycleEvent.Commit {

    UUID requestId();

    record Prepare(
            BazaarCreateEscrowIntent intent
    ) implements BazaarEscrowLifecycleEvent {
        public Prepare {
            intent = Objects.requireNonNull(intent, "intent");
            if (intent.status()
                    != BazaarCreateEscrowIntent.Status.PREPARED) {
                throw new IllegalArgumentException(
                        "Bazaar creation prepare intent is not prepared");
            }
        }

        @Override
        public UUID requestId() {
            return intent.requestId();
        }
    }

    record Resolve(
            BazaarCreateEscrowIntent expectedIntent,
            BazaarCreateEscrowIntent resolvedIntent
    ) implements BazaarEscrowLifecycleEvent {
        public Resolve {
            expectedIntent = Objects.requireNonNull(expectedIntent,
                    "expectedIntent");
            resolvedIntent = Objects.requireNonNull(resolvedIntent,
                    "resolvedIntent");
            if (expectedIntent.terminal()
                    || resolvedIntent.status()
                    != BazaarCreateEscrowIntent.Status.ABORTED
                    && resolvedIntent.status()
                    != BazaarCreateEscrowIntent.Status.RECOVERY_REQUIRED
                    || !expectedIntent.requestId().equals(
                    resolvedIntent.requestId())
                    || !expectedIntent.intentFingerprint().equals(
                    resolvedIntent.intentFingerprint())
                    || resolvedIntent.revision()
                    != Math.addExact(expectedIntent.revision(), 1L)) {
                throw new IllegalArgumentException(
                        "Bazaar creation resolution is invalid");
            }
        }

        @Override
        public UUID requestId() {
            return expectedIntent.requestId();
        }
    }

    record Commit(
            Optional<BazaarCreateEscrowIntent> completedIntent,
            BazaarEscrowCommit commit
    ) implements BazaarEscrowLifecycleEvent {
        public Commit {
            completedIntent = Objects.requireNonNull(completedIntent,
                    "completedIntent");
            commit = Objects.requireNonNull(commit, "commit");
            boolean create = commit.operation()
                    == BazaarOperationType.CREATE;
            if (create != completedIntent.isPresent()) {
                throw new IllegalArgumentException(
                        "Bazaar lifecycle create intent shape is invalid");
            }
            if (completedIntent.isPresent()) {
                BazaarCreateEscrowIntent intent = completedIntent
                        .orElseThrow();
                BazaarOperationStatus result = commit.bookMutation()
                        .requestReceipt().orElseThrow().result().status();
                BazaarCreateEscrowIntent.Status expected = result
                        == BazaarOperationStatus.APPLIED
                        ? BazaarCreateEscrowIntent.Status.COMMITTED
                        : BazaarCreateEscrowIntent.Status.REJECTED;
                if (intent.status() != expected
                        || !intent.requestId().equals(commit.requestId())
                        || !commit.createIntentFingerprint().filter(
                        intent.intentFingerprint()::equals).isPresent()) {
                    throw new IllegalArgumentException(
                            "Bazaar lifecycle committed intent is invalid");
                }
            }
        }

        @Override
        public UUID requestId() {
            return commit.requestId();
        }
    }
}
