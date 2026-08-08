package com.enviouse.futureshops.server.escrow.stock.migration;

import com.enviouse.futureshops.catalog.CatalogStockAuthorityMode;
import com.enviouse.futureshops.catalog.ItemDef;
import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.catalog.ShopDefinition;
import com.enviouse.futureshops.catalog.ShopDefinitionLoader;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeManager;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeService;
import com.enviouse.futureshops.server.escrow.stock.CatalogStockState;
import com.enviouse.futureshops.server.escrow.stock.CatalogStockStatus;
import com.enviouse.futureshops.server.escrow.stock.PersistentStockRepository;
import com.enviouse.futureshops.server.escrow.stock.StockCommandResult;
import com.enviouse.futureshops.server.escrow.stock.StockDefinition;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommand;
import com.enviouse.futureshops.server.escrow.stock.StockMutationType;
import com.enviouse.futureshops.server.escrow.stock.StockStoreSnapshot;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class CatalogStockRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(
            CatalogStockRuntime.class);
    private static final CatalogStockActivationCoverage COVERAGE =
            CatalogStockActivationCoverage.productionCutover();
    private static final CatalogStockCutoverCoordinator CUTOVER =
            new CatalogStockCutoverCoordinator();
    private static String loggedFailureSignature = "";

    private CatalogStockRuntime() {
    }

    public static void initialize(
            MinecraftServer server,
            EscrowRuntimeService runtime
    ) {
        requireServerThread(server);
        loggedFailureSignature = "";
        advance(server, Objects.requireNonNull(runtime, "runtime"));
    }

    public static Status status(MinecraftServer server) {
        requireServerThread(server);
        CatalogStockMigrationSavedData migration =
                CatalogStockMigrationSavedData.get(server);
        return new Status(
                ShopCatalog.stockAuthorityMode(),
                migration.stage(),
                migration.failure(),
                migration.failureDetail(),
                migration.nextEntryIndex(),
                migration.totalEntries());
    }

    public static void tick(MinecraftServer server) {
        requireServerThread(server);
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (runtime != null) {
            advance(server, runtime);
        }
    }

    public static void reload(MinecraftServer server) {
        requireServerThread(server);
        CatalogStockAuthorityMode mode = ShopCatalog.stockAuthorityMode();
        if (mode == CatalogStockAuthorityMode.LEGACY) {
            ShopCatalog.reload(server);
            return;
        }
        if (mode != CatalogStockAuthorityMode.DURABLE) {
            throw new IllegalStateException(
                    "Catalog stock migration is not ready for reload");
        }
        EscrowRuntimeService runtime = EscrowRuntimeManager.requireReady();
        ShopCatalog.reloadDurable(server,
                definitions -> reconcile(runtime, definitions, Instant.now()));
    }

    public static Optional<StockCommandResult> setStock(
            String shopId,
            String listingId,
            int newStock
    ) {
        if (ShopCatalog.stockAuthorityMode()
                != CatalogStockAuthorityMode.DURABLE) {
            throw new IllegalStateException(
                    "Durable catalog stock is not active");
        }
        if (newStock < 0) {
            throw new IllegalArgumentException(
                    "Catalog stock must not be negative");
        }
        EscrowRuntimeService runtime = EscrowRuntimeManager.requireReady();
        ResolvedListing resolved = resolveListing(shopId, listingId)
                .orElse(null);
        if (resolved == null) {
            return Optional.empty();
        }
        if (resolved.item().isUnlimited()) {
            return Optional.empty();
        }
        StockKey key = new StockKey(
                resolved.shopId(), resolved.item().resolutionKey());
        StockStoreSnapshot snapshot = runtime.stockSnapshot();
        CatalogStockState state = requireListing(snapshot, key);
        StockDefinition identity = CatalogStockSeedCapture.definition(
                resolved.shopId(), resolved.item());
        StockDefinition definition = CatalogStockMutationPlanner
                .adminResetDefinition(identity, newStock, snapshot);
        if (state.status() == CatalogStockStatus.ACTIVE
                && state.policy().equals(definition.policy())
                && state.configFingerprint().equals(
                definition.configFingerprint())
                && state.availableQuantity() == newStock) {
            return Optional.empty();
        }
        StockMutationCommand.DefinitionChange command =
                new StockMutationCommand.DefinitionChange(
                        CatalogStockProductionIds.adminReset(
                                definition, state.revision()),
                        StockMutationType.ADMIN_RESET,
                        definition, state.revision(),
                        monotonicNow(state.updatedAt()));
        return Optional.of(runtime.commitStockMutation(command));
    }

    public static boolean refreshStock(
            String shopId,
            ItemDef item
    ) {
        Objects.requireNonNull(item, "item");
        if (ShopCatalog.stockAuthorityMode()
                != CatalogStockAuthorityMode.DURABLE) {
            return false;
        }
        EscrowRuntimeService runtime = EscrowRuntimeManager.requireReady();
        StockDefinition definition = CatalogStockSeedCapture.definition(
                shopId, item);
        StockStoreSnapshot snapshot = runtime.stockSnapshot();
        CatalogStockState state = requireListing(
                snapshot, definition.key());
        if (!CatalogStockMutationPlanner.refreshNeeded(
                state, definition, snapshot)) {
            return false;
        }
        runtime.commitStockMutation(
                new StockMutationCommand.DefinitionChange(
                        CatalogStockProductionIds.refresh(
                                definition, state.revision()),
                        StockMutationType.REFRESH,
                        definition, state.revision(),
                        monotonicNow(state.updatedAt())));
        return true;
    }

    static void reconcile(
            EscrowRuntimeService runtime,
            List<ShopDefinition> definitions,
            Instant now
    ) {
        Objects.requireNonNull(runtime, "runtime");
        definitions = List.copyOf(Objects.requireNonNull(
                definitions, "definitions"));
        CatalogStockSeedSnapshot target =
                CatalogStockSeedCapture.captureConfiguration(definitions);
        StockStoreSnapshot current = runtime.stockSnapshot();
        if (current.catalogFingerprint().equals(target.fingerprint())) {
            return;
        }
        Instant appliedAt = monotonicNow(current, now);
        runtime.commitStockMutation(new StockMutationCommand.Reconcile(
                CatalogStockProductionIds.reload(
                        current.storeRevision(), target.fingerprint()),
                target.definitions(), target.fingerprint(), appliedAt));
    }

    private static void advance(
            MinecraftServer server,
            EscrowRuntimeService runtime
    ) {
        CatalogStockAuthorityMode mode = ShopCatalog.stockAuthorityMode();
        if (mode == CatalogStockAuthorityMode.DURABLE) {
            return;
        }
        CatalogStockMigrationSavedData migration =
                CatalogStockMigrationSavedData.get(server);
        if (migration.stage() == CatalogStockMigrationStage.FAILED) {
            if (!migration.canRetryMaterializedState()
                    || mode != CatalogStockAuthorityMode.LEGACY) {
                recordMigrationFailure(migration);
                freezeFailedMigration(migration);
                return;
            }
        }
        if (!runtime.isReady()) {
            return;
        }
        if (migration.stage() != CatalogStockMigrationStage.COMPLETE) {
            CatalogStockMigrationResult result = CUTOVER.migrateBatch(
                    server, runtime, COVERAGE,
                    CatalogStockMigrator.MAXIMUM_BATCH_SIZE);
            if (result.stage() == CatalogStockMigrationStage.FAILED) {
                recordMigrationFailure(migration);
                freezeFailedMigration(migration);
                return;
            }
            if (result.stage() != CatalogStockMigrationStage.COMPLETE) {
                return;
            }
        }
        activateCompleted(server, runtime, migration);
    }

    private static void activateCompleted(
            MinecraftServer server,
            EscrowRuntimeService runtime,
            CatalogStockMigrationSavedData migration
    ) {
        if (ShopCatalog.stockAuthorityMode()
                == CatalogStockAuthorityMode.LEGACY) {
            ShopCatalog.freezeStockForCutover(
                    migration.snapshot().fingerprint());
        }
        List<ShopDefinition> definitions = List.copyOf(
                ShopDefinitionLoader.loadAll());
        reconcile(runtime, definitions, Instant.now());
        CUTOVER.activate(runtime, migration, COVERAGE);
        ShopCatalog.publishDurableDefinitions(server, definitions);
    }

    private static void recordMigrationFailure(
            CatalogStockMigrationSavedData migration
    ) {
        String signature = migration.failure().name() + "\n"
                + migration.failureDetail();
        if (signature.equals(loggedFailureSignature)) {
            return;
        }
        loggedFailureSignature = signature;
        LOGGER.error(
                "Catalog stock migration failed. Failure: {}. Detail: {}",
                migration.failure(), migration.failureDetail());
    }

    private static void freezeFailedMigration(
            CatalogStockMigrationSavedData migration
    ) {
        if (ShopCatalog.stockAuthorityMode()
                != CatalogStockAuthorityMode.LEGACY) {
            return;
        }
        String checksum;
        try {
            checksum = migration.snapshot().fingerprint();
        } catch (IllegalStateException exception) {
            checksum = PersistentStockRepository
                    .EMPTY_CATALOG_FINGERPRINT;
        }
        ShopCatalog.freezeStockForCutover(checksum);
    }

    private static CatalogStockState requireListing(
            StockStoreSnapshot snapshot,
            StockKey key
    ) {
        CatalogStockState state = snapshot.listings().get(key);
        if (state == null) {
            throw new IllegalStateException(
                    "Durable catalog stock listing is unavailable");
        }
        return state;
    }

    private static Optional<ResolvedListing> resolveListing(
            String shopId,
            String listingId
    ) {
        ShopDefinition shop = ShopCatalog.get(shopId)
                .or(() -> ShopCatalog.get("default"))
                .orElse(null);
        if (shop == null) {
            return Optional.empty();
        }
        return shop.items().stream()
                .filter(value -> value.resolutionKey()
                        .equals(listingId))
                .findFirst().map(item -> new ResolvedListing(
                        shop.shopId(), item));
    }

    private static Instant monotonicNow(Instant minimum) {
        Instant now = Instant.now();
        return now.isBefore(minimum) ? minimum : now;
    }

    private static Instant monotonicNow(
            StockStoreSnapshot snapshot,
            Instant now
    ) {
        Instant appliedAt = Objects.requireNonNull(now, "now");
        for (CatalogStockState state : snapshot.listings().values()) {
            if (appliedAt.isBefore(state.updatedAt())) {
                appliedAt = state.updatedAt();
            }
        }
        return appliedAt;
    }

    private static void requireServerThread(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (!server.isSameThread()) {
            throw new IllegalStateException(
                    "Catalog stock lifecycle requires the server thread");
        }
    }

    private record ResolvedListing(String shopId, ItemDef item) {
    }

    public record Status(
            CatalogStockAuthorityMode authorityMode,
            CatalogStockMigrationStage migrationStage,
            CatalogStockMigrationFailure migrationFailure,
            String failureDetail,
            int processedEntries,
            int totalEntries
    ) {
        public Status {
            Objects.requireNonNull(authorityMode, "authorityMode");
            Objects.requireNonNull(migrationStage, "migrationStage");
            Objects.requireNonNull(migrationFailure, "migrationFailure");
            failureDetail = Objects.requireNonNull(
                    failureDetail, "failureDetail");
        }
    }
}
