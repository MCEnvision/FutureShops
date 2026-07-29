package com.enviouse.futureshops.catalog.offer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public final class ServerShopBundleSavings {
    private ServerShopBundleSavings() {
    }

    public static Optional<Snapshot> calculate(
            ServerShopOfferListing bundle,
            AcquireOfferOption bundleOption,
            int quantity,
            Map<String, ServerShopOfferListing> listings,
            Instant now
    ) {
        return calculate(bundle, bundleOption, quantity, listings, now,
                permission -> permission == null
                        || permission.isBlank(),
                (listing, option, requestedQuantity) ->
                        Math.multiplyExact(
                                option.moneyCostMinorUnits(),
                                requestedQuantity));
    }

    public static Optional<Snapshot> calculate(
            ServerShopOfferListing bundle,
            AcquireOfferOption bundleOption,
            int quantity,
            Map<String, ServerShopOfferListing> listings,
            Instant now,
            Predicate<String> permissionAllowed,
            MoneyTotalResolver moneyResolver
    ) {
        java.util.Objects.requireNonNull(bundle, "bundle");
        java.util.Objects.requireNonNull(bundleOption, "bundleOption");
        java.util.Objects.requireNonNull(listings, "listings");
        java.util.Objects.requireNonNull(now, "now");
        java.util.Objects.requireNonNull(
                permissionAllowed, "permissionAllowed");
        java.util.Objects.requireNonNull(moneyResolver, "moneyResolver");
        if (quantity <= 0 || !bundleOption.moneyCostPresent()
                || bundle.outputs().size() < 2
                || bundle.bundleComparisons().size()
                != bundle.outputs().size()) {
            return Optional.empty();
        }
        long individualPerBundle = 0L;
        List<ComparisonRevision> revisions = new ArrayList<>();
        for (OfferItemComponent component : bundle.outputs()) {
            OfferBundleComparison comparison =
                    bundle.bundleComparisons().stream()
                    .filter(value -> value.componentId().equals(
                            component.componentId()))
                    .findFirst().orElse(null);
            if (comparison == null) {
                return Optional.empty();
            }
            ServerShopOfferListing standalone =
                    listings.get(comparison.listingId());
            if (!available(standalone, now)
                    || !permissionAllowed.test(
                    standalone.permissionNode())
                    || standalone.outputs().size() != 1) {
                return Optional.empty();
            }
            OfferItemComponent standaloneOutput =
                    standalone.outputs().get(0);
            if (!standaloneOutput.itemId().equals(component.itemId())
                    || !standaloneOutput.exactNbt()
                    .equals(component.exactNbt())) {
                return Optional.empty();
            }
            AcquireOfferOption option =
                    standalone.acquireOptions().stream()
                    .filter(value -> value.optionId().equals(
                            comparison.optionId()))
                    .filter(AcquireOfferOption::moneyCostPresent)
                    .filter(value -> !value.free()
                            && !value.hasItemCosts())
                    .findFirst().orElse(null);
            if (option == null) {
                return Optional.empty();
            }
            long epoch = now.getEpochSecond();
            if (!option.schedule().activeAt(epoch)
                    || !permissionAllowed.test(
                    option.permissionNode())) {
                return Optional.empty();
            }
            int standaloneUnits = Math.multiplyExact(
                    standaloneOutput.count(),
                    option.outputMultiplier());
            int bundleUnits = Math.multiplyExact(
                    component.count(),
                    bundleOption.outputMultiplier());
            if (standaloneUnits <= 0
                    || bundleUnits % standaloneUnits != 0) {
                return Optional.empty();
            }
            individualPerBundle = Math.addExact(
                    individualPerBundle,
                    moneyResolver.total(
                            standalone, option,
                            bundleUnits / standaloneUnits));
            revisions.add(new ComparisonRevision(
                    component.componentId(),
                    standalone.listingId(),
                    option.optionId(),
                    standalone.revision()));
        }
        long bundleTotal = moneyResolver.total(
                bundle, bundleOption, quantity);
        long individualTotal = Math.multiplyExact(
                individualPerBundle, quantity);
        if (individualTotal <= bundleTotal) {
            return Optional.empty();
        }
        long savings = Math.subtractExact(
                individualTotal, bundleTotal);
        long basisPoints = Math.floorDiv(
                Math.multiplyExact(savings, 10_000L),
                individualTotal);
        return Optional.of(new Snapshot(
                individualTotal, bundleTotal, savings,
                basisPoints, List.copyOf(revisions)));
    }

    private static boolean available(
            ServerShopOfferListing listing,
            Instant now
    ) {
        if (listing == null || !listing.active()) {
            return false;
        }
        long epoch = now.getEpochSecond();
        return (listing.expiresAtEpoch() == 0L
                || epoch < listing.expiresAtEpoch())
                && listing.schedule().activeAt(epoch);
    }

    public record Snapshot(
            long individualTotalMinorUnits,
            long bundleTotalMinorUnits,
            long savingsMinorUnits,
            long savingsBasisPoints,
            List<ComparisonRevision> comparisonRevisions
    ) {
        public Snapshot {
            comparisonRevisions = List.copyOf(comparisonRevisions);
            Set<String> components = new HashSet<>();
            if (individualTotalMinorUnits <= 0L
                    || bundleTotalMinorUnits < 0L
                    || savingsMinorUnits <= 0L
                    || savingsBasisPoints <= 0L
                    || savingsBasisPoints > 10_000L
                    || individualTotalMinorUnits
                    - bundleTotalMinorUnits != savingsMinorUnits
                    || comparisonRevisions.isEmpty()
                    || comparisonRevisions.size() > 36
                    || comparisonRevisions.stream().anyMatch(revision ->
                    revision.componentId() == null
                            || revision.componentId().isBlank()
                            || revision.listingId() == null
                            || revision.listingId().isBlank()
                            || revision.optionId() == null
                            || revision.optionId().isBlank()
                            || revision.revision() < 0L
                            || !components.add(
                            revision.componentId()))) {
                throw new IllegalArgumentException(
                        "Bundle savings snapshot is invalid");
            }
        }
    }

    public record ComparisonRevision(
            String componentId,
            String listingId,
            String optionId,
            long revision
    ) {
    }

    @FunctionalInterface
    public interface MoneyTotalResolver {
        long total(
                ServerShopOfferListing listing,
                AcquireOfferOption option,
                int quantity
        );
    }
}
