package com.enviouse.futureshops.server.market.bazaar;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public record BazaarProductDefinition(
        BazaarProduct product,
        String displayName,
        String iconRegistryId,
        BazaarProductIdentityPolicy identityPolicy,
        List<String> allowedDimensions,
        BazaarItemRestrictions restrictions,
        String sourceName
) {
    private static final Pattern CATEGORY_ID = Pattern.compile(
            "[a-z0-9_.-]{1,96}");

    public BazaarProductDefinition {
        product = Objects.requireNonNull(product, "product");
        displayName = Objects.requireNonNull(displayName, "displayName")
                .strip();
        iconRegistryId = Objects.requireNonNull(iconRegistryId,
                "iconRegistryId").strip().toLowerCase(java.util.Locale.ROOT);
        identityPolicy = Objects.requireNonNull(identityPolicy,
                "identityPolicy");
        allowedDimensions = Objects.requireNonNull(allowedDimensions,
                "allowedDimensions").stream().map(value ->
                Objects.requireNonNull(value, "dimension").strip()
                        .toLowerCase(java.util.Locale.ROOT))
                .distinct().sorted().toList();
        restrictions = Objects.requireNonNull(restrictions, "restrictions");
        sourceName = Objects.requireNonNull(sourceName, "sourceName").strip();
        if (displayName.length() > 128 || iconRegistryId.length() > 256
                || sourceName.isEmpty() || sourceName.length() > 256
                || allowedDimensions.size() > 64
                || !CATEGORY_ID.matcher(product.categoryId()).matches()) {
            throw new IllegalArgumentException(
                    "Bazaar product presentation is invalid");
        }
        if (identityPolicy == BazaarProductIdentityPolicy.COMMODITY
                && !product.exactIdentity().isEmpty()) {
            throw new IllegalArgumentException(
                    "Commodity Bazaar products cannot define exact identity");
        }
        if (identityPolicy == BazaarProductIdentityPolicy.EXACT
                && product.exactIdentity().isEmpty()) {
            throw new IllegalArgumentException(
                    "Exact Bazaar products require exact identity");
        }
        if (allowedDimensions.stream().anyMatch(value ->
                !BazaarProductDefinitionLoader.validResourceId(value))) {
            throw new IllegalArgumentException(
                    "Bazaar product dimension is invalid");
        }
        allowedDimensions = allowedDimensions.stream()
                .sorted(Comparator.naturalOrder()).toList();
    }

    public BazaarProductVersionKey key() {
        return BazaarProductVersionKey.of(product);
    }

    public String identityKey() {
        return product.registryId() + "\u0000" + identityPolicy.name()
                + "\u0000" + product.exactIdentity();
    }
}
