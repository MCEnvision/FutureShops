package com.enviouse.futureshops.catalog.offer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

public final class ServerShopOfferValidator {
    public static final int MAX_IDENTIFIER_LENGTH = 160;
    public static final int MAX_TEXT_LENGTH = 512;
    public static final int MAX_COMPONENTS = 36;
    public static final int MAX_OPTIONS = 32;
    public static final long MAX_MONEY_MINOR_UNITS =
            9_000_000_000_000_000L;
    private static final Pattern IDENTIFIER =
            Pattern.compile("[a-z0-9_.:/-]+");

    private ServerShopOfferValidator() {
    }

    public static OfferValidationResult validate(
            ServerShopOfferListing listing
    ) {
        return validate(listing, ignored -> true, ignored -> true);
    }

    public static OfferValidationResult validate(
            ServerShopOfferListing listing,
            Predicate<String> itemExists,
            Predicate<String> nbtValid
    ) {
        return validate(listing, itemExists, nbtValid, ignored -> 64);
    }

    public static OfferValidationResult validate(
            ServerShopOfferListing listing,
            Predicate<String> itemExists,
            Predicate<String> nbtValid,
            ToIntFunction<String> maximumStackSize
    ) {
        List<OfferValidationIssue> issues = new ArrayList<>();
        validateIdentifier(listing.listingId(), "listingId", issues);
        validateText(listing.displayName(), "displayName", issues);
        validateText(listing.description(), "description", issues);
        validateIdentifier(listing.categoryId(), "categoryId", issues);
        if (listing.revision() < 0L) {
            error(issues, "revision", "offer.revision.negative");
        }
        if (listing.expiresAtEpoch() < 0L) {
            error(issues, "expiresAtEpoch", "offer.expiry.negative");
        }
        if (listing.acquireOptions().isEmpty()
                && listing.sellOptions().isEmpty()) {
            error(issues, "options", "offer.options.empty");
        }
        validateComponents(listing.outputs(), "outputs",
                !listing.acquireOptions().isEmpty(),
                itemExists, nbtValid, issues);
        validateLimits(listing.limits(), "limits", issues);
        validateSchedule(listing.schedule(), "schedule", issues);
        validateStock(listing.stockPolicy(), "stock", issues);
        if (listing.acquireOptions().size() > MAX_OPTIONS) {
            error(issues, "acquireOptions", "offer.options.too_many");
        }
        if (listing.sellOptions().size() > MAX_OPTIONS) {
            error(issues, "sellOptions", "offer.options.too_many");
        }
        Set<String> optionIds = new HashSet<>();
        for (int index = 0; index < listing.acquireOptions().size();
             index++) {
            validateAcquire(listing.acquireOptions().get(index), index,
                    optionIds, itemExists, nbtValid, issues);
            validateAcquireFanout(listing,
                    listing.acquireOptions().get(index), index,
                    maximumStackSize, issues);
        }
        for (int index = 0; index < listing.sellOptions().size(); index++) {
            validateSell(listing.sellOptions().get(index), index, optionIds,
                    itemExists, nbtValid, issues);
            validateSellFanout(listing,
                    listing.sellOptions().get(index), index,
                    maximumStackSize, issues);
        }
        validateComparisons(listing, issues);
        return new OfferValidationResult(issues);
    }

    private static void validateAcquire(
            AcquireOfferOption option,
            int index,
            Set<String> optionIds,
            Predicate<String> itemExists,
            Predicate<String> nbtValid,
            List<OfferValidationIssue> issues
    ) {
        String path = "acquireOptions." + index;
        validateIdentifier(option.optionId(), path + ".optionId", issues);
        if (!optionIds.add(option.optionId())) {
            error(issues, path + ".optionId", "offer.option.duplicate_id");
        }
        validateText(option.label(), path + ".label", issues);
        if (option.moneyCostMinorUnits() < 0L
                || option.moneyCostMinorUnits() > MAX_MONEY_MINOR_UNITS) {
            error(issues, path + ".moneyCost",
                    "offer.money.out_of_bounds");
        }
        if (!option.moneyCostPresent()
                && option.moneyCostMinorUnits() != 0L) {
            error(issues, path + ".moneyCost",
                    "offer.money.unexpected");
        }
        if (option.free() && (option.moneyCostPresent()
                || !option.itemCosts().isEmpty())) {
            error(issues, path, "offer.free.has_cost");
        }
        if (!option.free() && !option.moneyCostPresent()
                && option.itemCosts().isEmpty()) {
            error(issues, path, "offer.acquire.cost_missing");
        }
        if (option.moneyCostPresent()
                && option.moneyCostMinorUnits() <= 0L) {
            error(issues, path + ".moneyCost",
                    "offer.money.not_positive");
        }
        if (option.outputMultiplier() < 1
                || option.outputMultiplier()
                > OfferLimitPolicy.DEFAULT_MAXIMUM_PER_REQUEST) {
            error(issues, path + ".outputMultiplier",
                    "offer.output_multiplier.out_of_bounds");
        }
        validateComponents(option.itemCosts(), path + ".itemCosts", false,
                itemExists, nbtValid, issues);
        validateLimits(option.limits(), path + ".limits", issues);
        validateSchedule(option.schedule(), path + ".schedule", issues);
        validateQuantityArithmetic(option, path, issues);
    }

    private static void validateSell(
            SellOfferOption option,
            int index,
            Set<String> optionIds,
            Predicate<String> itemExists,
            Predicate<String> nbtValid,
            List<OfferValidationIssue> issues
    ) {
        String path = "sellOptions." + index;
        validateIdentifier(option.optionId(), path + ".optionId", issues);
        if (!optionIds.add(option.optionId())) {
            error(issues, path + ".optionId", "offer.option.duplicate_id");
        }
        validateText(option.label(), path + ".label", issues);
        validateComponents(option.itemInputs(), path + ".itemInputs", true,
                itemExists, nbtValid, issues);
        if (option.moneyPayoutMinorUnits() <= 0L
                || option.moneyPayoutMinorUnits()
                > MAX_MONEY_MINOR_UNITS) {
            error(issues, path + ".moneyPayout",
                    "offer.money.not_positive");
        }
        if (option.capacity() < 0L) {
            error(issues, path + ".capacity",
                    "offer.capacity.negative");
        }
        validateLimits(option.limits(), path + ".limits", issues);
        validateSchedule(option.schedule(), path + ".schedule", issues);
        try {
            Math.multiplyExact(option.moneyPayoutMinorUnits(),
                    option.limits().maximumPerRequest());
        } catch (ArithmeticException exception) {
            error(issues, path + ".moneyPayout",
                    "offer.money.overflow");
        }
    }

    private static void validateQuantityArithmetic(
            AcquireOfferOption option,
            String path,
            List<OfferValidationIssue> issues
    ) {
        try {
            if (option.moneyCostPresent()) {
                Math.multiplyExact(option.moneyCostMinorUnits(),
                        option.limits().maximumPerRequest());
            }
            Math.multiplyExact(option.outputMultiplier(),
                    option.limits().maximumPerRequest());
            for (OfferItemComponent component : option.itemCosts()) {
                Math.multiplyExact(component.count(),
                        option.limits().maximumPerRequest());
            }
        } catch (ArithmeticException exception) {
            error(issues, path, "offer.quantity.overflow");
        }
    }

    private static void validateAcquireFanout(
            ServerShopOfferListing listing,
            AcquireOfferOption option,
            int index,
            ToIntFunction<String> maximumStackSize,
            List<OfferValidationIssue> issues
    ) {
        int maximum = Math.min(
                listing.limits().maximumPerRequest(),
                option.limits().maximumPerRequest());
        try {
            List<OfferEscrowFanout.ComponentUnits> outputs =
                    listing.outputs().stream().map(component ->
                            units(component, Math.multiplyExact(
                                    (long) option.outputMultiplier(),
                                    maximum))).toList();
            List<OfferEscrowFanout.ComponentUnits> inputs =
                    option.itemCosts().stream().map(component ->
                            units(component, maximum)).toList();
            if (!OfferEscrowFanout.fits(
                    inputs, outputs, maximumStackSize)) {
                error(issues, "acquireOptions." + index,
                        "offer.escrow.fanout_out_of_bounds");
            }
        } catch (ArithmeticException | IllegalArgumentException exception) {
            error(issues, "acquireOptions." + index,
                    "offer.escrow.fanout_out_of_bounds");
        }
    }

    private static void validateSellFanout(
            ServerShopOfferListing listing,
            SellOfferOption option,
            int index,
            ToIntFunction<String> maximumStackSize,
            List<OfferValidationIssue> issues
    ) {
        int maximum = Math.min(
                listing.limits().maximumPerRequest(),
                option.limits().maximumPerRequest());
        try {
            List<OfferEscrowFanout.ComponentUnits> inputs =
                    option.itemInputs().stream().map(component ->
                            units(component, maximum)).toList();
            if (!OfferEscrowFanout.fits(
                    inputs, List.of(), maximumStackSize)) {
                error(issues, "sellOptions." + index,
                        "offer.escrow.fanout_out_of_bounds");
            }
        } catch (ArithmeticException | IllegalArgumentException exception) {
            error(issues, "sellOptions." + index,
                    "offer.escrow.fanout_out_of_bounds");
        }
    }

    private static OfferEscrowFanout.ComponentUnits units(
            OfferItemComponent component,
            long multiplier
    ) {
        return new OfferEscrowFanout.ComponentUnits(
                component.itemId(), component.exactNbt(),
                Math.multiplyExact((long) component.count(), multiplier));
    }

    private static void validateComponents(
            List<OfferItemComponent> components,
            String path,
            boolean requireNonempty,
            Predicate<String> itemExists,
            Predicate<String> nbtValid,
            List<OfferValidationIssue> issues
    ) {
        if (requireNonempty && components.isEmpty()) {
            error(issues, path, "offer.components.empty");
        }
        if (components.size() > MAX_COMPONENTS) {
            error(issues, path, "offer.components.too_many");
        }
        Set<String> componentIds = new HashSet<>();
        Set<String> identities = new HashSet<>();
        for (int index = 0; index < components.size(); index++) {
            OfferItemComponent component = components.get(index);
            String componentPath = path + "." + index;
            validateIdentifier(component.componentId(),
                    componentPath + ".componentId", issues);
            if (!componentIds.add(component.componentId())) {
                error(issues, componentPath + ".componentId",
                        "offer.component.duplicate_id");
            }
            validateIdentifier(component.itemId(),
                    componentPath + ".itemId", issues);
            if (!itemExists.test(component.itemId())) {
                error(issues, componentPath + ".itemId",
                        "offer.item.unknown");
            }
            if (component.count() < 1
                    || component.count()
                    > OfferLimitPolicy.DEFAULT_MAXIMUM_PER_REQUEST) {
                error(issues, componentPath + ".count",
                        "offer.component.count_out_of_bounds");
            }
            if (!component.exactNbt().isBlank()
                    && !nbtValid.test(component.exactNbt())) {
                error(issues, componentPath + ".exactNbt",
                        "offer.nbt.invalid");
            }
            String identity = component.itemId() + "\u0000"
                    + component.exactNbt();
            if (!identities.add(identity)) {
                error(issues, componentPath,
                        "offer.component.not_normalized");
            }
        }
    }

    private static void validateLimits(
            OfferLimitPolicy limits,
            String path,
            List<OfferValidationIssue> issues
    ) {
        if (limits.maximumPerRequest() < 1
                || limits.maximumPerRequest()
                > OfferLimitPolicy.DEFAULT_MAXIMUM_PER_REQUEST) {
            error(issues, path + ".maximumPerRequest",
                    "offer.limit.request_out_of_bounds");
        }
        if (limits.lifetimeLimit() < 0L || limits.periodLimit() < 0L
                || limits.periodSeconds() < 0L
                || limits.cooldownSeconds() < 0L) {
            error(issues, path, "offer.limit.negative");
        }
        if ((limits.periodLimit() == 0L)
                != (limits.periodSeconds() == 0L)) {
            error(issues, path, "offer.limit.period_incomplete");
        }
    }

    private static void validateSchedule(
            OfferSchedule schedule,
            String path,
            List<OfferValidationIssue> issues
    ) {
        if (schedule.startsAtEpoch() < 0L
                || schedule.endsAtEpoch() < 0L
                || schedule.endsAtEpoch() > 0L
                && schedule.endsAtEpoch() <= schedule.startsAtEpoch()) {
            error(issues, path, "offer.schedule.invalid");
        }
    }

    private static void validateStock(
            OfferStockPolicy stock,
            String path,
            List<OfferValidationIssue> issues
    ) {
        if (stock.type() == OfferStockPolicy.Type.UNLIMITED
                && (stock.quantity() != 0L
                || stock.refreshSeconds() != 0L)) {
            error(issues, path, "offer.stock.unlimited_has_values");
        }
        if (stock.type() == OfferStockPolicy.Type.LIMITED_INDEPENDENT
                && stock.quantity() < 0L) {
            error(issues, path + ".quantity",
                    "offer.stock.quantity_negative");
        }
        if (stock.refreshSeconds() < 0L) {
            error(issues, path + ".refreshSeconds",
                    "offer.stock.refresh_negative");
        }
        if (stock.type() == OfferStockPolicy.Type.LINKED) {
            error(issues, path, "offer.stock.linked_not_supported");
        }
    }

    private static void validateComparisons(
            ServerShopOfferListing listing,
            List<OfferValidationIssue> issues
    ) {
        Set<String> outputIds = new HashSet<>();
        listing.outputs().forEach(component ->
                outputIds.add(component.componentId()));
        Set<String> compared = new HashSet<>();
        for (int index = 0; index < listing.bundleComparisons().size();
             index++) {
            OfferBundleComparison comparison =
                    listing.bundleComparisons().get(index);
            String path = "bundleComparisons." + index;
            if (!outputIds.contains(comparison.componentId())) {
                error(issues, path + ".componentId",
                        "offer.comparison.output_missing");
            }
            validateIdentifier(comparison.listingId(),
                    path + ".listingId", issues);
            validateIdentifier(comparison.optionId(),
                    path + ".optionId", issues);
            if (!compared.add(comparison.componentId())) {
                error(issues, path + ".componentId",
                        "offer.comparison.duplicate");
            }
            if (comparison.listingId().equals(listing.listingId())) {
                error(issues, path + ".listingId",
                        "offer.comparison.recursive");
            }
        }
    }

    private static void validateIdentifier(
            String value,
            String path,
            List<OfferValidationIssue> issues
    ) {
        if (value == null || value.isEmpty()
                || value.length() > MAX_IDENTIFIER_LENGTH
                || !IDENTIFIER.matcher(value).matches()) {
            error(issues, path, "offer.identifier.invalid");
        }
    }

    private static void validateText(
            String value,
            String path,
            List<OfferValidationIssue> issues
    ) {
        if (value != null && value.length() > MAX_TEXT_LENGTH) {
            error(issues, path, "offer.text.too_long");
        }
    }

    private static void error(
            List<OfferValidationIssue> issues,
            String path,
            String code
    ) {
        issues.add(new OfferValidationIssue(
                OfferValidationIssue.Severity.ERROR, path, code));
    }
}
