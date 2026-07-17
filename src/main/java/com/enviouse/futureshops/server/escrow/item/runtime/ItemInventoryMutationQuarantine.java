package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationToken;

import java.time.Instant;
import java.util.Objects;

public record ItemInventoryMutationQuarantine(
        ItemInventoryMutationToken token,
        ItemInventoryQuarantineReason reason,
        Instant quarantinedAt
) {
    public ItemInventoryMutationQuarantine {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(quarantinedAt, "quarantinedAt");
        if (quarantinedAt.getNano() % 1_000_000 != 0) {
            throw new IllegalArgumentException(
                    "Item inventory quarantine time is not canonical");
        }
    }
}
