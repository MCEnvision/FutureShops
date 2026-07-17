package com.enviouse.futureshops.server.escrow.item.runtime;

public enum ItemInventoryJournalStatus {
    PREPARED(1, false),
    COMMITTED(2, true),
    ABORTED(3, true),
    QUARANTINED(4, true);

    private final int wireCode;
    private final boolean terminal;

    ItemInventoryJournalStatus(int wireCode, boolean terminal) {
        this.wireCode = wireCode;
        this.terminal = terminal;
    }

    public int wireCode() {
        return wireCode;
    }

    public boolean terminal() {
        return terminal;
    }

    public static ItemInventoryJournalStatus fromWireCode(int wireCode) {
        for (ItemInventoryJournalStatus status : values()) {
            if (status.wireCode == wireCode) {
                return status;
            }
        }
        throw new IllegalArgumentException(
                "Unknown item inventory journal status");
    }
}
