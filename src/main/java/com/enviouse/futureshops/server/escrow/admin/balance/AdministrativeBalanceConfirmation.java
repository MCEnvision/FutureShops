package com.enviouse.futureshops.server.escrow.admin.balance;

public enum AdministrativeBalanceConfirmation {
    EXPLICIT_COMMAND(true),
    EXPLICIT_API(true),
    LEGACY_API_INVOCATION(true),
    UNCONFIRMED(false);

    private final boolean confirmed;

    AdministrativeBalanceConfirmation(boolean confirmed) {
        this.confirmed = confirmed;
    }

    public boolean confirmed() {
        return confirmed;
    }
}
