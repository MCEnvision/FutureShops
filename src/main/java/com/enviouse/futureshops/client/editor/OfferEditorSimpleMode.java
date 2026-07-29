package com.enviouse.futureshops.client.editor;

import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.catalog.offer.OfferSchedule;
import com.enviouse.futureshops.catalog.offer.SellOfferOption;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;

import java.util.List;
import java.util.Objects;

public final class OfferEditorSimpleMode {
    private OfferEditorSimpleMode() {
    }

    public static Mode detect(ServerShopOfferListing listing) {
        Objects.requireNonNull(listing, "listing");
        List<AcquireOfferOption> acquire = listing.acquireOptions();
        List<SellOfferOption> sell = listing.sellOptions();
        if (acquire.isEmpty() && sell.size() == 1) {
            return Mode.SELL_ONLY;
        }
        if (sell.size() > 1 || acquire.size() > 2) {
            return Mode.ADVANCED;
        }
        if (acquire.size() == 2 && sell.isEmpty()) {
            boolean money = acquire.stream().anyMatch(option ->
                    option.moneyCostPresent()
                            && option.itemCosts().isEmpty()
                            && !option.free());
            boolean barter = acquire.stream().anyMatch(option ->
                    !option.moneyCostPresent()
                            && !option.free());
            return money && barter
                    ? Mode.MONEY_OR_BARTER : Mode.ADVANCED;
        }
        if (acquire.size() != 1) {
            return Mode.ADVANCED;
        }
        AcquireOfferOption option = acquire.get(0);
        if (option.free() && sell.isEmpty()) {
            return Mode.FREE;
        }
        if (option.moneyCostPresent()
                && (!option.itemCosts().isEmpty()
                || option.optionId().contains("barter"))
                && sell.isEmpty()) {
            return Mode.MONEY_AND_BARTER;
        }
        if (!option.moneyCostPresent()
                && sell.isEmpty()) {
            return Mode.BARTER;
        }
        if (option.moneyCostPresent() && option.itemCosts().isEmpty()) {
            return sell.isEmpty() ? Mode.MONEY : Mode.BUY_AND_SELL;
        }
        return Mode.ADVANCED;
    }

    public static ServerShopOfferListing apply(
            ServerShopOfferListing listing,
            Mode mode
    ) {
        Objects.requireNonNull(listing, "listing");
        Objects.requireNonNull(mode, "mode");
        if (mode == Mode.ADVANCED) {
            return listing;
        }
        OfferItemComponent primary = primaryComponent(listing);
        List<OfferItemComponent> outputs = mode == Mode.SELL_ONLY
                ? List.of() : restoredOutputs(listing, primary);
        long money = existingMoneyCost(listing);
        List<OfferItemComponent> costs = existingItemCosts(listing);
        List<AcquireOfferOption> acquire = switch (mode) {
            case MONEY, BUY_AND_SELL -> List.of(
                    AcquireOfferOption.money("get_money", money));
            case FREE -> List.of(
                    AcquireOfferOption.free("get_free"));
            case BARTER -> List.of(items(costs));
            case MONEY_OR_BARTER -> List.of(
                    AcquireOfferOption.money("get_money", money),
                    items(costs));
            case MONEY_AND_BARTER -> List.of(compound(money, costs));
            case SELL_ONLY, ADVANCED -> List.of();
        };
        List<SellOfferOption> sell = switch (mode) {
            case SELL_ONLY, BUY_AND_SELL ->
                    List.of(sell(listing, primary));
            default -> List.of();
        };
        String iconItemId = listing.iconItemId();
        String iconNbt = listing.iconNbt();
        OfferItemComponent iconSource = outputs.isEmpty()
                ? sell.stream().findFirst()
                .flatMap(option -> option.itemInputs().stream().findFirst())
                .orElse(primary)
                : outputs.get(0);
        if (iconSource != null) {
            iconItemId = iconSource.itemId();
            iconNbt = iconSource.exactNbt();
        }
        return new ServerShopOfferListing(
                listing.listingId(), listing.revision(),
                listing.displayName(), listing.description(),
                listing.categoryId(), iconItemId, iconNbt,
                listing.active(), listing.expiresAtEpoch(),
                listing.permissionNode(), outputs, acquire, sell,
                listing.stockPolicy(), listing.limits(),
                listing.schedule(), listing.bundleComparisons());
    }

    private static AcquireOfferOption items(
            List<OfferItemComponent> costs
    ) {
        return new AcquireOfferOption(
                "get_barter", "Barter", false, false, 0L,
                costs, 1, OfferLimitPolicy.defaults(),
                OfferSchedule.always(), "");
    }

    private static AcquireOfferOption compound(
            long money,
            List<OfferItemComponent> costs
    ) {
        return new AcquireOfferOption(
                "get_money_barter", "Money and barter",
                false, true, money, costs, 1,
                OfferLimitPolicy.defaults(),
                OfferSchedule.always(), "");
    }

    private static SellOfferOption sell(
            ServerShopOfferListing listing,
            OfferItemComponent primary
    ) {
        SellOfferOption existing = listing.sellOptions().stream()
                .findFirst().orElse(null);
        List<OfferItemComponent> inputs = existing == null
                || existing.itemInputs().isEmpty()
                ? listing.outputs().isEmpty()
                ? primary == null ? List.of()
                : List.of(asInput(primary))
                : listing.outputs().stream()
                        .map(OfferEditorSimpleMode::asInput)
                        .toList()
                : existing.itemInputs();
        return new SellOfferOption(
                "sell_to_shop", "Sell to Shop", inputs,
                existing == null
                        ? 1L : existing.moneyPayoutMinorUnits(),
                existing == null ? 0L : existing.capacity(),
                existing == null
                        ? OfferLimitPolicy.defaults() : existing.limits(),
                existing == null
                        ? OfferSchedule.always() : existing.schedule(),
                existing == null ? "" : existing.permissionNode());
    }

    private static OfferItemComponent primaryComponent(
            ServerShopOfferListing listing
    ) {
        if (!listing.outputs().isEmpty()) {
            return listing.outputs().get(0);
        }
        return listing.sellOptions().stream()
                .flatMap(option -> option.itemInputs().stream())
                .findFirst().orElse(null);
    }

    private static List<OfferItemComponent> restoredOutputs(
            ServerShopOfferListing listing,
            OfferItemComponent primary
    ) {
        if (!listing.outputs().isEmpty()) {
            return listing.outputs();
        }
        List<OfferItemComponent> restored = listing.sellOptions().stream()
                .findFirst()
                .map(SellOfferOption::itemInputs)
                .orElse(List.of())
                .stream()
                .map(OfferEditorSimpleMode::asOutput)
                .toList();
        if (!restored.isEmpty()) {
            return restored;
        }
        return primary == null
                ? List.of() : List.of(asOutput(primary));
    }

    private static OfferItemComponent asOutput(
            OfferItemComponent component
    ) {
        return new OfferItemComponent(
                "output", component.itemId(),
                component.count(), component.exactNbt());
    }

    private static OfferItemComponent asInput(
            OfferItemComponent component
    ) {
        return new OfferItemComponent(
                "input", component.itemId(),
                component.count(), component.exactNbt());
    }

    private static long existingMoneyCost(
            ServerShopOfferListing listing
    ) {
        return listing.acquireOptions().stream()
                .filter(AcquireOfferOption::moneyCostPresent)
                .mapToLong(AcquireOfferOption::moneyCostMinorUnits)
                .findFirst().orElse(1L);
    }

    private static List<OfferItemComponent> existingItemCosts(
            ServerShopOfferListing listing
    ) {
        return listing.acquireOptions().stream()
                .filter(AcquireOfferOption::hasItemCosts)
                .findFirst().map(AcquireOfferOption::itemCosts)
                .orElse(List.of());
    }

    public enum Mode {
        MONEY("money"),
        FREE("free"),
        BARTER("barter"),
        MONEY_OR_BARTER("money_or_barter"),
        MONEY_AND_BARTER("money_and_barter"),
        SELL_ONLY("sell"),
        BUY_AND_SELL("buy_and_sell"),
        ADVANCED("advanced");

        private final String key;

        Mode(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }
}
