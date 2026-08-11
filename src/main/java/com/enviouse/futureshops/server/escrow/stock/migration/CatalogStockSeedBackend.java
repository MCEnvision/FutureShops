package com.enviouse.futureshops.server.escrow.stock.migration;

import com.enviouse.futureshops.server.escrow.stock.StockCommandResult;
import com.enviouse.futureshops.server.escrow.stock.StockConservationReport;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommand;
import com.enviouse.futureshops.server.escrow.stock.StockStoreSnapshot;

public interface CatalogStockSeedBackend {
    boolean ready();

    StockCommandResult commit(StockMutationCommand command);

    StockStoreSnapshot snapshot();

    StockConservationReport conservation();
}
