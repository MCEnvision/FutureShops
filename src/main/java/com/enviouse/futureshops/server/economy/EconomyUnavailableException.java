package com.enviouse.futureshops.server.economy;

/** Raised when the selected provider cannot authoritatively answer a balance query. */
public final class EconomyUnavailableException extends IllegalStateException {
    public EconomyUnavailableException(String providerId, String lifecycle, String diagnostic) {
        super("economy provider " + providerId + " is unavailable in state "
                + lifecycle + ": " + diagnostic);
    }
}
