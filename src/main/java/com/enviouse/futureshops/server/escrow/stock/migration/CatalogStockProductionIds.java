package com.enviouse.futureshops.server.escrow.stock.migration;

import com.enviouse.futureshops.server.escrow.stock.StockDefinition;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

final class CatalogStockProductionIds {
    private static final String DOMAIN =
            "futureshops.catalog.stock.production.v1";

    private CatalogStockProductionIds() {
    }

    static UUID reload(long storeRevision, String catalogFingerprint) {
        return named("reload", Long.toString(storeRevision),
                Objects.requireNonNull(catalogFingerprint,
                        "catalogFingerprint"));
    }

    static UUID refresh(
            StockDefinition definition,
            long listingRevision
    ) {
        return definitionChange("refresh", definition,
                listingRevision);
    }

    static UUID adminReset(
            StockDefinition definition,
            long listingRevision
    ) {
        return definitionChange("admin reset", definition,
                listingRevision);
    }

    private static UUID definitionChange(
            String operation,
            StockDefinition definition,
            long listingRevision
    ) {
        Objects.requireNonNull(definition, "definition");
        return named(operation, definition.key().shopId(),
                definition.key().listingId(),
                Long.toString(listingRevision),
                definition.policy().unlimited() ? "unlimited" : "limited",
                Long.toString(definition.policy().configuredQuantity()),
                definition.configFingerprint());
    }

    private static UUID named(String... values) {
        StringBuilder encoded = new StringBuilder(DOMAIN);
        for (String value : values) {
            encoded.append('\u0000').append(value);
        }
        return UUID.nameUUIDFromBytes(encoded.toString()
                .getBytes(StandardCharsets.UTF_8));
    }
}
