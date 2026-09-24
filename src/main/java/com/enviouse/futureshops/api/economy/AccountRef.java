package com.enviouse.futureshops.api.economy;

import java.util.Objects;
import java.util.UUID;

/** Immutable server-owned account identity used to create a provider binding. */
public record AccountRef(UUID accountUuid) {
    public AccountRef {
        Objects.requireNonNull(accountUuid, "accountUuid");
        if (accountUuid.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("accountUuid must not be zero");
        }
    }
}
