package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationToken;

import java.time.Instant;
import java.util.Objects;

public record ItemInventoryMutationAbort(
        ItemInventoryMutationToken token,
        ItemInventoryAbortReason reason,
        Instant abortedAt
) {
    public ItemInventoryMutationAbort {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(abortedAt, "abortedAt");
        if (abortedAt.getNano() % 1_000_000 != 0) {
            throw new IllegalArgumentException(
                    "Item inventory abort time is not canonical");
        }
    }
}
