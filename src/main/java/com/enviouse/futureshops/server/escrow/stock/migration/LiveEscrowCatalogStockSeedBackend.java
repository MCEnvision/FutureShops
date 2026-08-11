package com.enviouse.futureshops.server.escrow.stock.migration;

import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeService;
import com.enviouse.futureshops.server.escrow.stock.StockCommandResult;
import com.enviouse.futureshops.server.escrow.stock.StockConservationReport;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommand;
import com.enviouse.futureshops.server.escrow.stock.StockStoreSnapshot;

import java.util.Objects;

public final class LiveEscrowCatalogStockSeedBackend
        implements CatalogStockSeedBackend {
    private final EscrowRuntimeService runtime;

    public LiveEscrowCatalogStockSeedBackend(
            EscrowRuntimeService runtime
    ) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public boolean ready() {
        return runtime.isReady();
    }

    @Override
    public StockCommandResult commit(StockMutationCommand command) {
        return runtime.commitStockMutation(command);
    }

    @Override
    public StockStoreSnapshot snapshot() {
        return runtime.stockSnapshot();
    }

    @Override
    public StockConservationReport conservation() {
        return runtime.stockConservationReport();
    }
}
