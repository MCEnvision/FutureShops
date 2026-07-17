package com.enviouse.futureshops.server.market.bazaar;

import com.enviouse.futureshops.config.BazaarConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public final class BazaarProductCatalogRegistry {
    private volatile BazaarProductCatalogSnapshot current =
            BazaarProductCatalogSnapshot.empty();
    private volatile String lastRejection = "";

    public synchronized ReloadResult reload(
            Path directory,
            BazaarConfig.ProductDefaults defaults,
            int defaultMaximumQuantity,
            Predicate<String> itemExists
    ) {
        return reload(directory, defaults, defaultMaximumQuantity,
                itemExists, snapshot -> {
                });
    }

    public synchronized ReloadResult reload(
            Path directory,
            BazaarConfig.ProductDefaults defaults,
            int defaultMaximumQuantity,
            Predicate<String> itemExists,
            CatalogValidator validator
    ) {
        try {
            BazaarProductCatalogSnapshot loaded =
                    BazaarProductDefinitionLoader.load(directory, defaults,
                            defaultMaximumQuantity, itemExists);
            Objects.requireNonNull(validator, "validator").validate(loaded);
            boolean changed = !loaded.fingerprint().equals(
                    current.fingerprint());
            current = loaded;
            lastRejection = "";
            return new ReloadResult(true, changed, current,
                    Optional.empty());
        } catch (IOException | RuntimeException exception) {
            lastRejection = safeMessage(exception);
            return new ReloadResult(false, false, current,
                    Optional.of(lastRejection));
        }
    }

    public BazaarProductCatalogSnapshot current() {
        return current;
    }

    public Optional<String> lastRejection() {
        return lastRejection.isEmpty()
                ? Optional.empty() : Optional.of(lastRejection);
    }

    private static String safeMessage(Exception exception) {
        Objects.requireNonNull(exception, "exception");
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "Bazaar product reload failed" : message;
    }

    public record ReloadResult(
            boolean accepted,
            boolean changed,
            BazaarProductCatalogSnapshot snapshot,
            Optional<String> rejection
    ) {
        public ReloadResult {
            Objects.requireNonNull(snapshot, "snapshot");
            rejection = Objects.requireNonNull(rejection, "rejection");
            if (accepted == rejection.isPresent()
                    || !accepted && changed) {
                throw new IllegalArgumentException(
                        "Bazaar product reload result is invalid");
            }
        }
    }

    @FunctionalInterface
    public interface CatalogValidator {
        void validate(BazaarProductCatalogSnapshot snapshot);
    }
}
