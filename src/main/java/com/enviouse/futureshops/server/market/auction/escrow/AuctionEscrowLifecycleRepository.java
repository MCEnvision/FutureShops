package com.enviouse.futureshops.server.market.auction.escrow;

import com.enviouse.futureshops.server.market.auction.AuctionHouseMutation;
import com.enviouse.futureshops.server.market.auction.AuctionHouseSnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class AuctionEscrowLifecycleRepository {
    private AuctionEscrowLifecycleRepository() {
    }

    public static ApplyResult apply(
            AuctionHouseSnapshot auctionHouse,
            AuctionEscrowLifecycleState lifecycle,
            AuctionEscrowLifecycleEvent event
    ) {
        Objects.requireNonNull(auctionHouse, "auctionHouse");
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(event, "event");
        try {
            if (event instanceof AuctionEscrowLifecycleEvent.Prepare value) {
                return prepare(auctionHouse, lifecycle, value.intent());
            }
            if (event instanceof AuctionEscrowLifecycleEvent.Abort value) {
                return abort(auctionHouse, lifecycle,
                        value.expectedIntent(), value.terminalIntent());
            }
            if (event instanceof AuctionEscrowLifecycleEvent.Commit value) {
                return commit(auctionHouse, lifecycle, value);
            }
            throw conflict("Auction escrow lifecycle event is unknown");
        } catch (AuctionEscrowLifecycleConflictException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw conflict("Auction escrow lifecycle apply failed",
                    exception);
        }
    }

    private static ApplyResult prepare(
            AuctionHouseSnapshot auctionHouse,
            AuctionEscrowLifecycleState lifecycle,
            AuctionCreateEscrowIntent intent
    ) {
        AuctionCreateEscrowIntent existing = lifecycle.createIntents()
                .get(intent.requestId());
        if (existing != null) {
            if (!existing.equals(intent)
                    && !existing.intentFingerprint().equals(
                    intent.intentFingerprint())) {
                throw conflict(
                        "Auction creation request conflicts with its intent");
            }
            return new ApplyResult(auctionHouse, lifecycle, true);
        }
        if (lifecycle.commits().containsKey(intent.requestId())
                || lifecycle.createIntents().size()
                >= AuctionEscrowLifecycleState.MAX_CREATE_INTENTS) {
            throw conflict(
                    "Auction creation intent capacity or identity conflicts");
        }
        Map<java.util.UUID, AuctionCreateEscrowIntent> intents =
                new HashMap<>(lifecycle.createIntents());
        intents.put(intent.requestId(), intent);
        return new ApplyResult(auctionHouse,
                new AuctionEscrowLifecycleState(intents,
                        lifecycle.commits()), false);
    }

    private static ApplyResult abort(
            AuctionHouseSnapshot auctionHouse,
            AuctionEscrowLifecycleState lifecycle,
            AuctionCreateEscrowIntent expected,
            AuctionCreateEscrowIntent terminal
    ) {
        AuctionCreateEscrowIntent existing = lifecycle.createIntents()
                .get(expected.requestId());
        if (terminal.equals(existing)) {
            return new ApplyResult(auctionHouse, lifecycle, true);
        }
        if (!expected.equals(existing)
                || lifecycle.commits().containsKey(expected.requestId())) {
            throw conflict(
                    "Auction creation abort ancestry conflicts");
        }
        Map<java.util.UUID, AuctionCreateEscrowIntent> intents =
                new HashMap<>(lifecycle.createIntents());
        intents.put(terminal.requestId(), terminal);
        return new ApplyResult(auctionHouse,
                new AuctionEscrowLifecycleState(intents,
                        lifecycle.commits()), false);
    }

    private static ApplyResult commit(
            AuctionHouseSnapshot auctionHouse,
            AuctionEscrowLifecycleState lifecycle,
            AuctionEscrowLifecycleEvent.Commit event
    ) {
        AuctionEscrowCommit commit = event.commit();
        AuctionEscrowCommit existing = lifecycle.commits().get(
                commit.requestId());
        if (existing != null) {
            if (!existing.equals(commit)) {
                throw conflict(
                        "Auction escrow request conflicts with its commit");
            }
            return new ApplyResult(auctionHouse, lifecycle, true);
        }
        if (lifecycle.commits().size()
                >= AuctionEscrowLifecycleState.MAX_COMMITS) {
            throw conflict("Auction escrow commit capacity is exhausted");
        }
        Map<java.util.UUID, AuctionCreateEscrowIntent> intents =
                new HashMap<>(lifecycle.createIntents());
        if (event.completedIntent().isPresent()) {
            AuctionCreateEscrowIntent completed = event.completedIntent()
                    .orElseThrow();
            AuctionCreateEscrowIntent prepared = intents.get(
                    completed.requestId());
            if (prepared == null
                    || prepared.status()
                    != AuctionCreateEscrowIntent.Status.PREPARED
                    || !prepared.intentFingerprint().equals(
                    completed.intentFingerprint())) {
                throw conflict(
                        "Auction creation commit has no prepared intent");
            }
            intents.put(completed.requestId(), completed);
        }
        AuctionHouseSnapshot next = auctionHouse;
        for (AuctionHouseMutation mutation : commit.bookMutations()) {
            AuctionHouseMutation.ApplyResult applied = mutation.apply(next);
            if (applied.replayed()) {
                throw conflict(
                        "Auction lifecycle commit book mutation already exists");
            }
            next = applied.snapshot();
        }
        Map<java.util.UUID, AuctionEscrowCommit> commits = new HashMap<>(
                lifecycle.commits());
        commits.put(commit.requestId(), commit);
        return new ApplyResult(next,
                new AuctionEscrowLifecycleState(intents, commits), false);
    }

    private static AuctionEscrowLifecycleConflictException conflict(
            String message
    ) {
        return new AuctionEscrowLifecycleConflictException(message);
    }

    private static AuctionEscrowLifecycleConflictException conflict(
            String message,
            Throwable cause
    ) {
        return new AuctionEscrowLifecycleConflictException(message, cause);
    }

    public record ApplyResult(
            AuctionHouseSnapshot auctionHouse,
            AuctionEscrowLifecycleState lifecycle,
            boolean replayed
    ) {
        public ApplyResult {
            auctionHouse = Objects.requireNonNull(auctionHouse,
                    "auctionHouse");
            lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        }
    }
}
