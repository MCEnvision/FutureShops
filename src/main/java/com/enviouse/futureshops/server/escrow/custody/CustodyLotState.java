package com.enviouse.futureshops.server.escrow.custody;

public enum CustodyLotState {
    HELD,
    RELEASED,
    CONSUMED,
    QUARANTINED;

    public boolean isTerminal() {
        return this != HELD;
    }
}
