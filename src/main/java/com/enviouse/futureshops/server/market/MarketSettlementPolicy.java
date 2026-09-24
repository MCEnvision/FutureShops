package com.enviouse.futureshops.server.market;

import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeService;

/** Admission policy for market money routes until an external market adapter is available. */
public final class MarketSettlementPolicy {
    private MarketSettlementPolicy() {
    }

    /** Returns true only when the selected provider owns the durable internal wallet. */
    public static boolean internalProviderReady() {
        return BalanceManager.isInternalProviderSelected();
    }

    /** Returns true when the market runtime and its admitted money provider are both ready. */
    public static boolean ready(EscrowRuntimeService runtime) {
        return runtime != null && runtime.isReady() && internalProviderReady();
    }
}
