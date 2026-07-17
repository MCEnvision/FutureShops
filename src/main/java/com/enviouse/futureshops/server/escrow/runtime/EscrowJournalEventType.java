package com.enviouse.futureshops.server.escrow.runtime;

public enum EscrowJournalEventType {
    JOURNAL_LINEAGE(5),
    TRANSACTION_UPSERT(1),
    LEDGER_APPLY(2),
    CLAIM_CREATE(3),
    CLAIM_DELIVERY(4),
    MONEY_CLAIM_SETTLEMENT(6),
    ADMIN_AUDIT(7),
    CUSTODY_PREPARE(8),
    CUSTODY_MUTATION(9),
    PROTECTED_MINT(10),
    CLAIM_QUARANTINE(11),
    CUSTODY_BATCH(12),
    MAINTENANCE_REPAIR(13),
    ATM_WITHDRAWAL_COMMIT(14);

    private final int wireId;

    EscrowJournalEventType(int wireId) {
        this.wireId = wireId;
    }

    public int wireId() {
        return wireId;
    }

    public static EscrowJournalEventType fromWireId(int wireId) {
        for (EscrowJournalEventType value : values()) {
            if (value.wireId == wireId) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown escrow journal event type");
    }
}
