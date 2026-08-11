package com.enviouse.futureshops.server.escrow.item;

public enum ItemInventoryCheckpointTerminalState {
    COMMITTED(true),
    COMPENSATED(true),
    PREPARED(false),
    RECOVERING(false);

    private final boolean terminal;

    ItemInventoryCheckpointTerminalState(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean terminal() {
        return terminal;
    }
}
