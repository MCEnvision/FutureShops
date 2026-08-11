package com.enviouse.futureshops.server.escrow.stock.migration;

import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeService;
import com.enviouse.futureshops.server.escrow.stock.StockStoreSnapshot;
import net.minecraft.server.MinecraftServer;

import java.util.Objects;

public final class LiveCatalogStockMigrationDurabilityBarrier
        implements CatalogStockMigrationDurabilityBarrier {
    private final MinecraftServer server;
    private final EscrowRuntimeService runtime;

    public LiveCatalogStockMigrationDurabilityBarrier(
            MinecraftServer server,
            EscrowRuntimeService runtime
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public void flush() {
        requireServerThread();
        server.overworld().getDataStorage().save();
    }

    @Override
    public boolean checkpointVerified(
            String checksum,
            long completionSequence
    ) {
        requireServerThread();
        if (!runtime.isReady()) {
            return false;
        }
        if (!matches(checksum, completionSequence)) {
            return false;
        }
        runtime.checkpointNow();
        flush();
        return matches(checksum, completionSequence);
    }

    private boolean matches(
            String checksum,
            long completionSequence
    ) {
        StockStoreSnapshot snapshot = runtime.stockSnapshot();
        return snapshot.storeRevision() >= completionSequence
                && snapshot.catalogFingerprint().equals(checksum)
                && runtime.stockConservationReport().conserved();
    }

    private void requireServerThread() {
        if (!server.isSameThread()) {
            throw new IllegalStateException(
                    "Catalog stock durability requires the server thread");
        }
    }
}
