package com.enviouse.futureshops.server.market.bazaar;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record BazaarOrderBookSnapshot(
        int schemaVersion,
        long nextSequence,
        List<BazaarProduct> products,
        List<BazaarOrder> orders,
        List<BazaarFill> fills,
        Map<UUID, BazaarRequestReceipt> receipts,
        Map<String, Long> referencePrices,
        Set<UUID> terminalTransactions,
        List<BazaarRuleSnapshot> ruleSnapshots,
        long effectiveRuleRevision,
        BazaarRetentionPolicy retentionPolicy,
        Map<UUID, String> lifecycleReceipts
) {
    public static final int CURRENT_SCHEMA_VERSION = 3;

    public BazaarOrderBookSnapshot {
        products = products.stream().sorted(java.util.Comparator
                .comparing(BazaarProduct::productId)
                .thenComparingLong(BazaarProduct::version)).toList();
        orders = orders.stream().sorted(java.util.Comparator
                .comparing(BazaarOrder::orderId)).toList();
        fills = fills.stream().sorted(java.util.Comparator
                .comparingLong(BazaarFill::sequence)
                .thenComparing(BazaarFill::fillId)).toList();
        receipts = Map.copyOf(receipts);
        referencePrices = Map.copyOf(referencePrices);
        terminalTransactions = Set.copyOf(terminalTransactions);
        ruleSnapshots = ruleSnapshots.stream().sorted(java.util.Comparator
                .comparingLong(BazaarRuleSnapshot::configRevision)).toList();
        java.util.Objects.requireNonNull(retentionPolicy, "retentionPolicy");
        lifecycleReceipts = Map.copyOf(lifecycleReceipts);
        if (schemaVersion != CURRENT_SCHEMA_VERSION
                || nextSequence <= 0L
                || nextSequence == Long.MAX_VALUE
                || effectiveRuleRevision < -1L
                || Math.addExact(receipts.size(), lifecycleReceipts.size())
                > retentionPolicy.maximumReceipts()
                || terminalTransactions.size()
                > retentionPolicy.maximumTerminalTransactions()) {
            throw new IllegalArgumentException("Bazaar snapshot header is invalid");
        }
    }
}
