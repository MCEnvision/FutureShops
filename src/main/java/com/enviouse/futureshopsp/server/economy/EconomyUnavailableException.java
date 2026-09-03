package com.enviouse.futureshopsp.server.economy;

/** Raised when a balance read has no authoritative provider value. */
public final class EconomyUnavailableException extends IllegalStateException {
    private final String providerId;
    private final String lifecycle;

    public EconomyUnavailableException(String providerId, String lifecycle, String diagnostic) {
        super("Economy provider " + providerId + " is " + lifecycle + ". " + diagnostic);
        this.providerId = providerId;
        this.lifecycle = lifecycle;
    }

    public String providerId() {
        return providerId;
    }

    public String lifecycle() {
        return lifecycle;
    }
}
