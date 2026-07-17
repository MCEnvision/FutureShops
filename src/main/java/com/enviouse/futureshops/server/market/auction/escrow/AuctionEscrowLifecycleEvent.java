package com.enviouse.futureshops.server.market.auction.escrow;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public sealed interface AuctionEscrowLifecycleEvent permits
        AuctionEscrowLifecycleEvent.Prepare,
        AuctionEscrowLifecycleEvent.Abort,
        AuctionEscrowLifecycleEvent.Commit {

    UUID requestId();

    record Prepare(
            AuctionCreateEscrowIntent intent
    ) implements AuctionEscrowLifecycleEvent {
        public Prepare {
            intent = Objects.requireNonNull(intent, "intent");
            if (intent.status()
                    != AuctionCreateEscrowIntent.Status.PREPARED) {
                throw new IllegalArgumentException(
                        "Auction creation prepare intent is not prepared");
            }
        }

        @Override
        public UUID requestId() {
            return intent.requestId();
        }
    }

    record Abort(
            AuctionCreateEscrowIntent expectedIntent,
            AuctionCreateEscrowIntent terminalIntent
    ) implements AuctionEscrowLifecycleEvent {
        public Abort {
            expectedIntent = Objects.requireNonNull(expectedIntent,
                    "expectedIntent");
            terminalIntent = Objects.requireNonNull(terminalIntent,
                    "terminalIntent");
            if (expectedIntent.status()
                    != AuctionCreateEscrowIntent.Status.PREPARED
                    || terminalIntent.status()
                    == AuctionCreateEscrowIntent.Status.PREPARED
                    || terminalIntent.status()
                    == AuctionCreateEscrowIntent.Status.COMMITTED
                    || !expectedIntent.requestId().equals(
                    terminalIntent.requestId())
                    || !expectedIntent.intentFingerprint().equals(
                    terminalIntent.intentFingerprint())) {
                throw new IllegalArgumentException(
                        "Auction creation abort transition is invalid");
            }
        }

        @Override
        public UUID requestId() {
            return expectedIntent.requestId();
        }
    }

    record Commit(
            Optional<AuctionCreateEscrowIntent> completedIntent,
            AuctionEscrowCommit commit
    ) implements AuctionEscrowLifecycleEvent {
        public Commit {
            completedIntent = Objects.requireNonNull(completedIntent,
                    "completedIntent");
            commit = Objects.requireNonNull(commit, "commit");
            boolean creation = commit.operation()
                    == com.enviouse.futureshops.server.market.auction
                    .AuctionOperationType.CREATE;
            if (creation != completedIntent.isPresent()) {
                throw new IllegalArgumentException(
                        "Auction lifecycle create intent shape is invalid");
            }
            if (completedIntent.isPresent()) {
                AuctionCreateEscrowIntent intent = completedIntent
                        .orElseThrow();
                if (intent.status()
                        != AuctionCreateEscrowIntent.Status.COMMITTED
                        || !intent.requestId().equals(commit.requestId())
                        || !commit.createIntentFingerprint().filter(
                        intent.intentFingerprint()::equals).isPresent()
                        || !commit.itemCustody().filter(
                        intent.plannedCustody()::equals).isPresent()) {
                    throw new IllegalArgumentException(
                            "Auction lifecycle committed intent is invalid");
                }
            }
        }

        @Override
        public UUID requestId() {
            return commit.requestId();
        }
    }
}
