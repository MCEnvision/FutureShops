package com.enviouse.futureshops.server.market.bazaar;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record BazaarProductReconciliationPlan(
        String sourceBookFingerprint,
        String catalogFingerprint,
        List<BazaarProductReconciliationAction> actions
) {
    public BazaarProductReconciliationPlan {
        sourceBookFingerprint = requireFingerprint(sourceBookFingerprint);
        catalogFingerprint = requireFingerprint(catalogFingerprint);
        actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
    }

    public static BazaarProductReconciliationPlan create(
            BazaarOrderBookSnapshot book,
            BazaarProductCatalogSnapshot catalog
    ) {
        Objects.requireNonNull(book, "book");
        Objects.requireNonNull(catalog, "catalog");
        Map<BazaarProductVersionKey, BazaarProduct> persistedVersions =
                new HashMap<>();
        Map<String, BazaarProduct> persistedCurrent = new HashMap<>();
        for (BazaarProduct product : book.products()) {
            persistedVersions.put(BazaarProductVersionKey.of(product),
                    product);
            BazaarProduct prior = persistedCurrent.get(product.productId());
            if (prior == null || product.version() > prior.version()) {
                persistedCurrent.put(product.productId(), product);
            }
        }
        for (BazaarProductDefinition definition : catalog.definitions()) {
            BazaarProduct persisted = persistedVersions.get(definition.key());
            if (persisted != null && !sameImmutableDefinition(
                    persisted, definition.product())) {
                throw new IllegalArgumentException(
                        "Bazaar product version changed without a version bump, "
                                + definition.product().productId());
            }
        }
        Map<String, BazaarProductDefinition> desired =
                new LinkedHashMap<>(catalog.currentDefinitions());
        List<BazaarProductReconciliationAction> actions =
                new ArrayList<>();
        desired.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> reconcileDesired(entry.getValue(),
                        persistedCurrent.get(entry.getKey()), actions));
        persistedCurrent.entrySet().stream()
                .filter(entry -> !desired.containsKey(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .filter(product -> product.status()
                        != BazaarProductStatus.RETIRED)
                .forEach(product -> actions.add(
                        BazaarProductReconciliationAction.status(
                                product.productId(),
                                BazaarProductStatus.RETIRED)));
        actions.sort(Comparator.comparing(
                        (BazaarProductReconciliationAction action) ->
                                action.type().ordinal())
                .thenComparing(action -> action.product().map(
                        BazaarProduct::productId).orElse(
                        action.productId())));
        return new BazaarProductReconciliationPlan(
                BazaarOrderBookSnapshotCodec.fingerprint(book),
                catalog.fingerprint(), actions);
    }

    public List<BazaarLifecycleCommand> commands() {
        List<BazaarLifecycleCommand> commands = new ArrayList<>();
        for (int index = 0; index < actions.size(); index++) {
            BazaarProductReconciliationAction action = actions.get(index);
            UUID mutationId = mutationId(index, action);
            commands.add(action.type()
                    == BazaarProductReconciliationAction.Type.REGISTER
                    ? BazaarLifecycleCommand.registerProduct(mutationId,
                    action.product().orElseThrow())
                    : BazaarLifecycleCommand.setProductStatus(mutationId,
                    action.productId(), action.status().orElseThrow()));
        }
        return List.copyOf(commands);
    }

    private static void reconcileDesired(
            BazaarProductDefinition definition,
            BazaarProduct persisted,
            List<BazaarProductReconciliationAction> actions
    ) {
        BazaarProduct desired = definition.product();
        if (persisted == null) {
            actions.add(BazaarProductReconciliationAction.register(desired));
            return;
        }
        if (desired.version() < persisted.version()) {
            throw new IllegalArgumentException(
                    "Bazaar product version cannot move backward, "
                            + desired.productId());
        }
        if (desired.version() > persisted.version()) {
            actions.add(BazaarProductReconciliationAction.register(desired));
            return;
        }
        if (!sameImmutableDefinition(desired, persisted)) {
            throw new IllegalArgumentException(
                    "Bazaar product version changed without a version bump, "
                            + desired.productId());
        }
        if (desired.status() != persisted.status()) {
            actions.add(BazaarProductReconciliationAction.status(
                    desired.productId(), desired.status()));
        }
    }

    private UUID mutationId(
            int index,
            BazaarProductReconciliationAction action
    ) {
        String identity = "futureshops.bazaar.product.reconcile."
                + sourceBookFingerprint + "." + catalogFingerprint + "."
                + index + "." + action.type().name() + "."
                + action.product().map(product -> product.productId() + "."
                + product.version()).orElseGet(() -> action.productId() + "."
                + action.status().orElseThrow().name());
        return UUID.nameUUIDFromBytes(identity.getBytes(
                StandardCharsets.UTF_8));
    }

    private static boolean sameImmutableDefinition(BazaarProduct first,
                                                   BazaarProduct second) {
        return first.productId().equals(second.productId())
                && first.version() == second.version()
                && first.registryId().equals(second.registryId())
                && first.exactIdentity().equals(second.exactIdentity())
                && first.categoryId().equals(second.categoryId())
                && first.lotSize() == second.lotSize()
                && first.priceTickMinor() == second.priceTickMinor()
                && first.minimumPriceMinor() == second.minimumPriceMinor()
                && first.maximumPriceMinor() == second.maximumPriceMinor()
                && first.maximumQuantity() == second.maximumQuantity();
    }

    private static String requireFingerprint(String value) {
        Objects.requireNonNull(value, "fingerprint");
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "Bazaar reconciliation fingerprint is invalid");
        }
        return value;
    }
}
