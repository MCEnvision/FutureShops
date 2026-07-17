package com.enviouse.futureshops.server.escrow.stock.migration;

import com.enviouse.futureshops.catalog.CatalogStockAuthority;
import com.enviouse.futureshops.catalog.CatalogStockAuthorityMode;
import com.enviouse.futureshops.catalog.ShopCatalog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CatalogStockCutoverCoordinatorTest {
    private static final String CHECKSUM = "a".repeat(64);

    @AfterEach
    void resetCatalog() {
        ShopCatalog.clear();
    }

    @Test
    void readinessWaitsForEveryTransactionScopedCaller() {
        CatalogStockCutoverCoordinator coordinator =
                new CatalogStockCutoverCoordinator();
        CatalogStockMigrationSavedData migration =
                new CatalogStockMigrationSavedData();

        assertEquals(
                CatalogStockCutoverReadiness
                        .WAITING_FOR_TRANSACTIONAL_CALLERS,
                coordinator.readiness(migration,
                        CatalogStockActivationCoverage
                                .legacyCallersPresent()));
    }

    @Test
    void frozenAuthorityRejectsLegacyMutationAndIncompleteActivation() {
        ShopCatalog.freezeStockForCutover(CHECKSUM);
        assertEquals(CatalogStockAuthorityMode.CUTOVER_FROZEN,
                ShopCatalog.stockAuthorityMode());
        assertThrows(IllegalStateException.class,
                () -> ShopCatalog.reserveStock(
                        "default", "minecraft:diamond", 1));
        CatalogStockAuthority authority = new CatalogStockAuthority() {
            @Override
            public String seedChecksum() {
                return CHECKSUM;
            }

            @Override
            public int currentStock(
                    String shopId,
                    String listingId
            ) {
                return 1;
            }
        };

        assertThrows(IllegalStateException.class,
                () -> ShopCatalog.activateDurableStockAuthority(
                        authority,
                        CatalogStockActivationCoverage
                                .legacyCallersPresent()));
        assertEquals(CatalogStockAuthorityMode.CUTOVER_FROZEN,
                ShopCatalog.stockAuthorityMode());
    }
}
