package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.SellOfferOption;
import com.enviouse.futureshops.catalog.offer.ServerShopBundleSavings;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ServerShopOfferPresentation {
    private ServerShopOfferPresentation() {
    }

    public static Projection project(
            ServerShopOfferListing offer,
            PreviewState state,
            String currencyName,
            Map<String, ServerShopOfferListing> comparisonListings
    ) {
        Objects.requireNonNull(offer, "offer");
        Objects.requireNonNull(state, "state");
        String currency = Objects.requireNonNullElse(
                currencyName, "");
        Map<String, ServerShopOfferListing> comparisons = Map.copyOf(
                Objects.requireNonNull(
                        comparisonListings,
                        "comparisonListings"));
        List<Component> detail = new ArrayList<>();
        detail.add(Component.translatable(
                "gui.futureshops.offer.presentation.outputs",
                joinComponents(offer.outputs(), 1)));
        detail.add(Component.translatable(
                "gui.futureshops.offer.presentation.stock",
                offer.stockPolicy().type().name()
                        .toLowerCase(java.util.Locale.ROOT),
                offer.stockPolicy().quantity()));
        detail.add(Component.translatable(
                "gui.futureshops.offer.presentation.schedule",
                offer.schedule().startsAtEpoch(),
                offer.schedule().endsAtEpoch()));
        List<Component> acquire = offer.acquireOptions().stream()
                .map(option -> (Component) Component.literal(
                        acquireSummary(option, offer, currency)))
                .toList();
        List<Component> sell = offer.sellOptions().stream()
                .map(option -> (Component) Component.literal(
                        sellSummary(option, currency)))
                .toList();
        if (!offer.acquireOptions().isEmpty()) {
            AcquireOfferOption option =
                    offer.acquireOptions().get(0);
            ServerShopBundleSavings.calculate(
                    offer, option, 1,
                    comparisons, Instant.now())
                    .ifPresent(savings -> detail.add(
                            Component.translatable(
                                    "gui.futureshops.offer.savings",
                                    ShopUiUtil.formatMinorUnits(
                                            savings
                                                    .individualTotalMinorUnits()),
                                    ShopUiUtil.formatMinorUnits(
                                            savings
                                                    .bundleTotalMinorUnits()),
                                    ShopUiUtil.formatMinorUnits(
                                            savings.savingsMinorUnits()),
                                    savings.savingsBasisPoints()
                                            / 100.0D)));
        }
        return new Projection(
                Component.literal(offer.displayName()),
                Component.literal(offer.description()),
                Component.translatable(
                        "gui.futureshops.offer.preview.state."
                                + state.key()),
                List.copyOf(detail), acquire, sell,
                offer.iconItemId(), offer.iconNbt(),
                offer.bundle(), offer.sellOnly());
    }

    public static String acquireSummary(
            AcquireOfferOption option,
            ServerShopOfferListing offer,
            String currencyName
    ) {
        return acquireSummary(option, offer, currencyName, 1);
    }

    public static String acquireSummary(
            AcquireOfferOption option,
            ServerShopOfferListing offer,
            String currencyName,
            int quantity
    ) {
        int checkedQuantity = requirePositiveQuantity(quantity);
        String cost = acquireCostSummary(
                option, currencyName, checkedQuantity);
        int outputMultiplier = Math.multiplyExact(
                option.outputMultiplier(), checkedQuantity);
        return optionLabel(option.label(), option.optionId())
                + "  " + cost + "  "
                + Component.translatable(
                "gui.futureshops.offer.presentation.receives")
                .getString() + " "
                + joinComponents(offer.outputs(),
                outputMultiplier);
    }

    public static String acquireCostSummary(
            AcquireOfferOption option,
            String currencyName
    ) {
        return acquireCostSummary(option, currencyName, 1);
    }

    public static String acquireCostSummary(
            AcquireOfferOption option,
            String currencyName,
            int quantity
    ) {
        int checkedQuantity = requirePositiveQuantity(quantity);
        if (option.free()) {
            return Component.translatable(
                    "gui.futureshops.offer.free").getString();
        }
        List<String> parts = new ArrayList<>();
        if (option.moneyCostPresent()) {
            parts.add(ShopUiUtil.formatMinorUnits(
                    Math.multiplyExact(
                            option.moneyCostMinorUnits(),
                            (long) checkedQuantity))
                    + " " + currencyName);
        }
        if (!option.itemCosts().isEmpty()) {
            parts.add(joinComponents(
                    option.itemCosts(), checkedQuantity));
        }
        return String.join(" + ", parts);
    }

    public static String sellSummary(
            SellOfferOption option,
            String currencyName
    ) {
        return sellSummary(option, currencyName, 1);
    }

    public static String sellSummary(
            SellOfferOption option,
            String currencyName,
            int quantity
    ) {
        int checkedQuantity = requirePositiveQuantity(quantity);
        return optionLabel(option.label(), option.optionId())
                + "  " + joinComponents(
                option.itemInputs(), checkedQuantity)
                + "  " + Component.translatable(
                "gui.futureshops.offer.presentation.receives")
                .getString() + " "
                + sellPayoutSummary(
                option, currencyName, checkedQuantity);
    }

    public static String sellPayoutSummary(
            SellOfferOption option,
            String currencyName
    ) {
        return sellPayoutSummary(option, currencyName, 1);
    }

    public static String sellPayoutSummary(
            SellOfferOption option,
            String currencyName,
            int quantity
    ) {
        int checkedQuantity = requirePositiveQuantity(quantity);
        return ShopUiUtil.formatMinorUnits(
                Math.multiplyExact(option.moneyPayoutMinorUnits(),
                        (long) checkedQuantity))
                + " " + currencyName;
    }

    public static String joinComponents(
            List<OfferItemComponent> components,
            int multiplier
    ) {
        return components.stream().map(component ->
                Math.multiplyExact(component.count(), multiplier)
                        + " " + ShopUiUtil
                        .getItemDisplayNameWithNbt(
                                component.itemId(),
                                component.exactNbt()))
                .collect(java.util.stream.Collectors
                        .joining(" + "));
    }

    private static String optionLabel(
            String label,
            String optionId
    ) {
        return label == null || label.isBlank()
                ? optionId : label;
    }

    private static int requirePositiveQuantity(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException(
                    "Quantity must be positive");
        }
        return quantity;
    }

    public enum PreviewState {
        ACTIVE("active"),
        EMPTY("empty"),
        DISABLED("disabled"),
        EXPIRED("expired"),
        OUT_OF_STOCK("out_of_stock"),
        LIMIT_REACHED("limit_reached");

        private final String key;

        PreviewState(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public PreviewState next() {
            PreviewState[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public record Projection(
            Component title,
            Component description,
            Component status,
            List<Component> detailRows,
            List<Component> acquireRows,
            List<Component> sellRows,
            String iconItemId,
            String iconNbt,
            boolean bundle,
            boolean sellOnly
    ) {
        public Projection {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(status, "status");
            detailRows = List.copyOf(detailRows);
            acquireRows = List.copyOf(acquireRows);
            sellRows = List.copyOf(sellRows);
            iconItemId = Objects.requireNonNullElse(
                    iconItemId, "");
            iconNbt = Objects.requireNonNullElse(iconNbt, "");
        }
    }
}
