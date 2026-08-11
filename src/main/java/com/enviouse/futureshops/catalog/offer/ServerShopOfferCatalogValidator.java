package com.enviouse.futureshops.catalog.offer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public final class ServerShopOfferCatalogValidator {
    private ServerShopOfferCatalogValidator() {
    }

    public static OfferValidationResult validate(
            List<ServerShopOfferListing> listings,
            Predicate<String> itemExists,
            Predicate<String> nbtValid
    ) {
        return validate(listings, itemExists, nbtValid, ignored -> 64);
    }

    public static OfferValidationResult validate(
            List<ServerShopOfferListing> listings,
            Predicate<String> itemExists,
            Predicate<String> nbtValid,
            ToIntFunction<String> maximumStackSize
    ) {
        List<OfferValidationIssue> issues = new ArrayList<>();
        Set<String> listingIds = new HashSet<>();
        Map<String, ServerShopOfferListing> byId = new HashMap<>();
        for (int index = 0; index < listings.size(); index++) {
            ServerShopOfferListing listing = listings.get(index);
            if (!listingIds.add(listing.listingId())) {
                issues.add(new OfferValidationIssue(
                        OfferValidationIssue.Severity.ERROR,
                        "listings." + index + ".listingId",
                        "offer.listing.duplicate_id"));
            }
            byId.putIfAbsent(listing.listingId(), listing);
            OfferValidationResult result = ServerShopOfferValidator.validate(
                    listing, itemExists, nbtValid, maximumStackSize);
            for (OfferValidationIssue issue : result.issues()) {
                issues.add(new OfferValidationIssue(issue.severity(),
                        "listings." + index + "." + issue.path(),
                        issue.code()));
            }
        }
        for (int listingIndex = 0; listingIndex < listings.size();
             listingIndex++) {
            ServerShopOfferListing listing = listings.get(listingIndex);
            for (int comparisonIndex = 0;
                 comparisonIndex < listing.bundleComparisons().size();
                 comparisonIndex++) {
                OfferBundleComparison comparison =
                        listing.bundleComparisons().get(comparisonIndex);
                ServerShopOfferListing target =
                        byId.get(comparison.listingId());
                String path = "listings." + listingIndex
                        + ".bundleComparisons." + comparisonIndex;
                if (target == null) {
                    issues.add(new OfferValidationIssue(
                            OfferValidationIssue.Severity.ERROR,
                            path + ".listingId",
                            "offer.comparison.listing_missing"));
                    continue;
                }
                OfferItemComponent component = listing.outputs().stream()
                        .filter(output -> output.componentId().equals(
                                comparison.componentId()))
                        .findFirst().orElse(null);
                if (!target.active()) {
                    issues.add(new OfferValidationIssue(
                            OfferValidationIssue.Severity.ERROR,
                            path + ".listingId",
                            "offer.comparison.listing_inactive"));
                }
                if (component == null || target.outputs().size() != 1) {
                    issues.add(new OfferValidationIssue(
                            OfferValidationIssue.Severity.ERROR,
                            path + ".listingId",
                            "offer.comparison.output_mismatch"));
                } else {
                    OfferItemComponent standalone =
                            target.outputs().get(0);
                    if (!standalone.itemId().equals(component.itemId())
                            || !standalone.exactNbt().equals(
                            component.exactNbt())
                            || standalone.count() != component.count()) {
                        issues.add(new OfferValidationIssue(
                                OfferValidationIssue.Severity.ERROR,
                                path + ".listingId",
                                "offer.comparison.output_mismatch"));
                    }
                }
                boolean optionExists = target.acquireOptions().stream()
                        .anyMatch(option -> option.optionId()
                                .equals(comparison.optionId())
                                && option.moneyCostPresent()
                                && !option.free()
                                && !option.hasItemCosts());
                if (!optionExists) {
                    issues.add(new OfferValidationIssue(
                            OfferValidationIssue.Severity.ERROR,
                            path + ".optionId",
                            "offer.comparison.money_option_missing"));
                }
            }
        }
        return new OfferValidationResult(issues);
    }
}
