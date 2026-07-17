package com.enviouse.futureshops.server.escrow.checkpoint;

public enum EscrowCheckpointStore {
    TRANSACTIONS(1),
    LEDGER(2),
    CLAIMS(3),
    ADMINISTRATIVE_AUDIT(4),
    CUSTODY(5),
    PROTECTED_MINT(6),
    RUNTIME_METADATA(7),
    STOCK(8);

    private final int wireId;

    EscrowCheckpointStore(int wireId) {
        this.wireId = wireId;
    }

    public int wireId() {
        return wireId;
    }

    public static EscrowCheckpointStore fromWireId(int wireId) {
        for (EscrowCheckpointStore store : values()) {
            if (store.wireId == wireId) {
                return store;
            }
        }
        throw new IllegalArgumentException("Unknown escrow checkpoint store");
    }
}
