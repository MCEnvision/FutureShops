package com.enviouse.futureshops.server.market.bazaar;

import com.enviouse.futureshops.config.BazaarConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarProductCatalogCoordinatorTest {
    @TempDir
    Path directory;

    @Test
    void acceptedCatalogCommitsThroughLifecycleMutations() throws Exception {
        Files.writeString(directory.resolve("iron.json"), """
                {"id":"iron","version":1,"item":"minecraft:iron_ingot"}
                """);
        BazaarProductCatalogCoordinator coordinator =
                new BazaarProductCatalogCoordinator(
                        new BazaarProductCatalogRegistry());
        InMemoryBackend backend = new InMemoryBackend();

        BazaarProductCatalogCoordinator.ReconciliationResult result =
                coordinator.reloadAndReconcile(directory,
                        new BazaarConfig.ProductDefaults(1, 1L), 1000,
                        value -> true, backend);

        assertTrue(result.complete());
        assertEquals(1, result.plannedActions());
        assertEquals(1, result.appliedActions());
        assertEquals("iron", backend.snapshot().products().get(0)
                .productId());
    }

    @Test
    void persistenceFailureReportsPartialProgressAndCanResume()
            throws Exception {
        Files.writeString(directory.resolve("products.json"), """
                {
                  "products": [
                    {"id":"gold","version":1,"item":"minecraft:gold_ingot"},
                    {"id":"iron","version":1,"item":"minecraft:iron_ingot"}
                  ]
                }
                """);
        BazaarProductCatalogCoordinator coordinator =
                new BazaarProductCatalogCoordinator(
                        new BazaarProductCatalogRegistry());
        InMemoryBackend backend = new InMemoryBackend();
        backend.failAfter = 1;

        BazaarProductCatalogCoordinator.ReconciliationResult interrupted =
                coordinator.reloadAndReconcile(directory,
                        new BazaarConfig.ProductDefaults(1, 1L), 1000,
                        value -> true, backend);

        assertFalse(interrupted.complete());
        assertEquals(2, interrupted.plannedActions());
        assertEquals(1, interrupted.appliedActions());
        backend.failAfter = Integer.MAX_VALUE;
        BazaarProductCatalogCoordinator.ReconciliationResult resumed =
                coordinator.reloadAndReconcile(directory,
                        new BazaarConfig.ProductDefaults(1, 1L), 1000,
                        value -> true, backend);
        assertTrue(resumed.complete());
        assertEquals(2, backend.snapshot().products().size());
    }

    private static final class InMemoryBackend implements
            BazaarProductCatalogCoordinator.MutationBackend {
        private BazaarOrderBookSnapshot snapshot =
                new BazaarOrderBook().snapshot();
        private int commits;
        private int failAfter = Integer.MAX_VALUE;

        @Override
        public BazaarOrderBookSnapshot snapshot() {
            return snapshot;
        }

        @Override
        public BazaarMutation.ApplyResult commit(BazaarMutation mutation) {
            if (commits >= failAfter) {
                throw new IllegalStateException("Injected product commit failure");
            }
            BazaarMutation.ApplyResult result = mutation.apply(snapshot);
            snapshot = result.snapshot();
            commits++;
            return result;
        }
    }
}
