package com.enviouse.futureshopsp.server.debug;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum DebugModule {
    ALL("all"),
    PROVIDER("provider"),
    LIFECYCLE("lifecycle"),
    TRANSACTION("transaction"),
    RECEIPT("receipt"),
    RECOVERY("recovery"),
    PIXELMON("pixelmon"),
    DANCONOMY("danconomy"),
    VAULT("vault"),
    NETWORK("network"),
    SURFACE("surface");

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
        return Arrays.stream(values()).filter(module -> module.id.equals(normalized)).findFirst();
    }
}
