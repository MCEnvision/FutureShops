package com.enviouse.futureshops.server.market.bazaar;

import com.enviouse.futureshops.config.BazaarConfig;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public final class BazaarProductCatalogCoordinator {
    private final BazaarProductCatalogRegistry registry;

    public BazaarProductCatalogCoordinator(
            BazaarProductCatalogRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public synchronized ReconciliationResult reloadAndReconcile(
            Path directory,
            BazaarConfig.ProductDefaults defaults,
            int defaultMaximumQuantity,
            Predicate<String> itemExists,
            MutationBackend backend
    ) {
        Objects.requireNonNull(backend, "backend");
        BazaarOrderBookSnapshot initial = backend.snapshot();
        BazaarProductCatalogRegistry.ReloadResult reload = registry.reload(
                directory, defaults, defaultMaximumQuantity, itemExists,
                snapshot -> BazaarProductReconciliationPlan.create(initial,
                        snapshot));
        if (!reload.accepted()) {
            return new ReconciliationResult(reload, 0, 0, false,
                    reload.rejection());
        }
        int planned = 0;
        int applied = 0;
        try {
            BazaarOrderBookSnapshot current = backend.snapshot();
            BazaarProductReconciliationPlan plan =
                    BazaarProductReconciliationPlan.create(current,
                    reload.snapshot());
            List<BazaarLifecycleCommand> commands = plan.commands();
            planned = commands.size();
            for (BazaarLifecycleCommand command : commands) {
                current = backend.snapshot();
                BazaarMutation mutation = BazaarMutation.lifecycle(current,
                        command);
                BazaarMutation.ApplyResult result = backend.commit(mutation);
                BazaarOrderBookSnapshot committed = backend.snapshot();
                String expected = BazaarOrderBookSnapshotCodec.fingerprint(
                        result.snapshot());
                String actual = BazaarOrderBookSnapshotCodec.fingerprint(
                        committed);
                if (!expected.equals(actual)) {
                    throw new IllegalStateException(
                            "Bazaar product commit result changed");
                }
                applied++;
            }
            return new ReconciliationResult(reload, planned,
                    applied, true, Optional.empty());
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            return new ReconciliationResult(reload, planned, applied,
                    false, Optional.of(message == null || message.isBlank()
                    ? "Bazaar product reconciliation failed" : message));
        }
    }

    public BazaarProductCatalogSnapshot current() {
        return registry.current();
    }

    public interface MutationBackend {
        BazaarOrderBookSnapshot snapshot();

        BazaarMutation.ApplyResult commit(BazaarMutation mutation);
    }

    public record ReconciliationResult(
            BazaarProductCatalogRegistry.ReloadResult reload,
            int plannedActions,
            int appliedActions,
            boolean complete,
            Optional<String> failure
    ) {
        public ReconciliationResult {
            Objects.requireNonNull(reload, "reload");
            failure = Objects.requireNonNull(failure, "failure");
            if (plannedActions < 0 || appliedActions < 0
                    || appliedActions > plannedActions
                    || complete == failure.isPresent()
                    || complete && appliedActions != plannedActions) {
                throw new IllegalArgumentException(
                        "Bazaar product reconciliation result is invalid");
            }
        }
    }
}
