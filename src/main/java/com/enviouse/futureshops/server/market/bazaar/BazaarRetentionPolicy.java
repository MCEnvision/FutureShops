package com.enviouse.futureshops.server.market.bazaar;

public record BazaarRetentionPolicy(
        int maximumReceipts,
        int maximumTerminalTransactions
) {
    public static final int MAXIMUM_RECEIPT_LIMIT = 65_536;
    public static final int MAXIMUM_TERMINAL_TRANSACTION_LIMIT = 131_072;
    public static final BazaarRetentionPolicy DEFAULT =
            new BazaarRetentionPolicy(MAXIMUM_RECEIPT_LIMIT,
                    MAXIMUM_TERMINAL_TRANSACTION_LIMIT);

    public BazaarRetentionPolicy {
        if (maximumReceipts <= 0
                || maximumReceipts > MAXIMUM_RECEIPT_LIMIT
                || maximumTerminalTransactions <= 0
                || maximumTerminalTransactions
                > MAXIMUM_TERMINAL_TRANSACTION_LIMIT) {
            throw new IllegalArgumentException("Bazaar retention policy is invalid");
        }
    }
}
