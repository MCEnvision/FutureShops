package com.enviouse.futureshops.server.debug;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Bounded server diagnostic modules exposed to operators. */
public enum DebugModule {
    ALL("all"),
    ECONOMY("economy"),
    ESCROW("escrow"),
    SHOP("shop"),
    MARKET("market"),
    CASH("cash"),
    NETWORK("network"),
    PERSISTENCE("persistence");

    private final String id;

    DebugModule(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<DebugModule> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(module -> module.id.equals(normalized))
                .findFirst();
    }
}
