package com.enviouse.futureshops.server.market.auction.escrow;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record AuctionEscrowLifecycleState(
        Map<UUID, AuctionCreateEscrowIntent> createIntents,
        Map<UUID, AuctionEscrowCommit> commits
) {
    public static final int MAX_CREATE_INTENTS = 200_000;
    public static final int MAX_COMMITS = 200_000;

    public AuctionEscrowLifecycleState {
        createIntents = Map.copyOf(new HashMap<>(Objects.requireNonNull(
                createIntents, "createIntents")));
        commits = Map.copyOf(new HashMap<>(Objects.requireNonNull(
                commits, "commits")));
        if (createIntents.size() > MAX_CREATE_INTENTS
                || commits.size() > MAX_COMMITS) {
            throw new IllegalArgumentException(
                    "Auction escrow lifecycle state exceeds its limit");
        }
        for (Map.Entry<UUID, AuctionCreateEscrowIntent> entry
                : createIntents.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().requestId())) {
                throw new IllegalArgumentException(
                        "Auction creation intent index is invalid");
            }
        }
        for (Map.Entry<UUID, AuctionEscrowCommit> entry
                : commits.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().requestId())) {
                throw new IllegalArgumentException(
                        "Auction escrow commit index is invalid");
            }
        }
    }

    public static AuctionEscrowLifecycleState empty() {
        return new AuctionEscrowLifecycleState(Map.of(), Map.of());
    }

    public boolean hasMaterializedState() {
        return !createIntents.isEmpty() || !commits.isEmpty();
    }
}
