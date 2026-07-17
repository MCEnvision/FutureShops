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
    ATM_WITHDRAWAL_COMMIT(14),
    FOREIGN_ATM_WITHDRAWAL_COMMIT(15),
    CASH_CLAIM_DELIVERY_COMMIT(16),
    PROTECTED_CASH_REDEMPTION_RESERVATION(17),
    PROTECTED_CASH_REDEMPTION_SETTLEMENT(18),
    PROTECTED_CASH_REDEMPTION_CANCELLATION(19),
    FOREIGN_CASH_DEPOSIT_RESERVATION(20),
    FOREIGN_CASH_DEPOSIT_SETTLEMENT(21),
    FOREIGN_CASH_DEPOSIT_CANCELLATION(22),
    PLAYER_PAYMENT_COMMIT(23),
    STOCK_MUTATION(24),
    SERVER_SHOP_PURCHASE_COMMIT(25),
    ITEM_INVENTORY_MUTATION(26),
    EXACT_ITEM_CLAIM_DELIVERY_COMMIT(27),
    ITEM_INVENTORY_QUARANTINE_ADMINISTRATION(28),
    ITEM_INVENTORY_JOURNAL_COMPACTION(29),
    SERVER_SHOP_SELL_COMMIT(30),
    SERVER_SHOP_BARTER_COMMIT(31),
    AUCTION_HOUSE_MUTATION(32),
    BAZAAR_MUTATION(33),
    AUCTION_HOUSE_ESCROW_LIFECYCLE(34),
    PLAYER_SHOP_ESCROW_LIFECYCLE(35),
    BAZAAR_ESCROW_LIFECYCLE(36),
    MARKET_CONTROL_MUTATION(37),
    SERVER_SHOP_FUNDING_RELEASE(38);

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
