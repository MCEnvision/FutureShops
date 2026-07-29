package com.enviouse.futureshops.client.editor;

import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.catalog.offer.OfferSchedule;
import com.enviouse.futureshops.catalog.offer.OfferStockPolicy;
import com.enviouse.futureshops.catalog.offer.SellOfferOption;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferRevision;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class OfferEditorTemplates {
    private OfferEditorTemplates() {
    }

    public static ServerShopOfferListing apply(
            ServerShopOfferListing base,
            Template template,
            Optional<OfferItemComponent> heldComponent
    ) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(heldComponent, "heldComponent");
        List<OfferItemComponent> outputs = outputComponents(
                base, template, heldComponent);
        List<AcquireOfferOption> acquire = new ArrayList<>();
        List<SellOfferOption> sell = new ArrayList<>();
        switch (template) {
            case MONEY -> acquire.add(money());
            case BARTER -> acquire.add(items());
            case MONEY_OR_BARTER -> {
                acquire.add(money());
                acquire.add(items());
            }
            case MONEY_AND_BARTER -> acquire.add(compound());
            case FREE -> acquire.add(AcquireOfferOption.free("get_free"));
            case SELL -> sell.add(sell(heldComponent));
            case BUY_AND_SELL -> {
                acquire.add(money());
                sell.add(sell(heldComponent));
            }
            case BUNDLE -> acquire.add(money());
            case ADVANCED -> {
            }
        }
        OfferItemComponent icon = outputs.isEmpty()
                ? heldComponent.orElse(null) : outputs.get(0);
        String displayName = base.displayName().isBlank()
                && icon != null ? icon.itemId() : base.displayName();
        return new ServerShopOfferListing(
                base.listingId(), base.revision(), displayName,
                base.description(), base.categoryId(),
                icon == null ? base.iconItemId() : icon.itemId(),
                icon == null ? base.iconNbt() : icon.exactNbt(),
                base.active(), base.expiresAtEpoch(),
                base.permissionNode(), outputs, acquire, sell,
                base.stockPolicy(), base.limits(), base.schedule(),
                List.of());
    }

    public static ServerShopOfferListing quickAdd(
            ServerShopOfferListing base,
            Template template,
            List<OfferItemComponent> selectedComponents,
            String displayName,
            long basePriceMinor,
            long stock
    ) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(template, "template");
        List<OfferItemComponent> components = List.copyOf(
                Objects.requireNonNull(selectedComponents,
                        "selectedComponents"));
        if (components.isEmpty()) {
            throw new IllegalArgumentException(
                    "Quick add requires at least one item");
        }
        if (template != Template.MONEY
                && template != Template.SELL
                && template != Template.BARTER
                && template != Template.BUNDLE) {
            throw new IllegalArgumentException(
                    "Quick add template is unsupported");
        }
        if (template != Template.BARTER && basePriceMinor <= 0L) {
            throw new IllegalArgumentException(
                    "Quick add price must be positive");
        }

        OfferItemComponent icon = components.get(0);
        List<OfferItemComponent> outputs =
                template == Template.SELL ? List.of() : components;
        List<AcquireOfferOption> acquire = switch (template) {
            case MONEY, BUNDLE -> List.of(
                    AcquireOfferOption.money(
                            "get_money", basePriceMinor));
            case BARTER -> List.of(items());
            default -> List.of();
        };
        long capacity = stock < 0L ? 0L : stock;
        List<SellOfferOption> sell = template == Template.SELL
                ? List.of(new SellOfferOption(
                "sell_money", "Sell to Shop", components,
                basePriceMinor, capacity, OfferLimitPolicy.defaults(),
                OfferSchedule.always(), ""))
                : List.of();
        OfferStockPolicy stockPolicy = template == Template.SELL
                || stock < 0L
                ? OfferStockPolicy.unlimited()
                : OfferStockPolicy.limited(stock, 0L);
        ServerShopOfferListing listing = new ServerShopOfferListing(
                base.listingId(), base.revision(),
                Objects.requireNonNullElse(displayName, ""),
                base.description(), base.categoryId(),
                icon.itemId(), icon.exactNbt(), base.active(),
                base.expiresAtEpoch(), base.permissionNode(),
                outputs, acquire, sell, stockPolicy,
                base.limits(), base.schedule(), List.of());
        return listing.withRevision(
                ServerShopOfferRevision.compute(listing));
    }

    private static List<OfferItemComponent> outputComponents(
            ServerShopOfferListing base,
            Template template,
            Optional<OfferItemComponent> held
    ) {
        if (template == Template.SELL || template == Template.ADVANCED) {
            return template == Template.ADVANCED
                    ? base.outputs() : List.of();
        }
        return held.<List<OfferItemComponent>>map(List::of)
                .orElse(base.outputs());
    }

    private static AcquireOfferOption money() {
        return AcquireOfferOption.money("get_money", 1L);
    }

    private static AcquireOfferOption items() {
        return new AcquireOfferOption(
                "get_items", "Items", false, false, 0L,
                List.of(), 1, OfferLimitPolicy.defaults(),
                OfferSchedule.always(), "");
    }

    private static AcquireOfferOption compound() {
        return new AcquireOfferOption(
                "get_money_items", "Money and Items",
                false, true, 1L, List.of(), 1,
                OfferLimitPolicy.defaults(), OfferSchedule.always(), "");
    }

    private static SellOfferOption sell(
            Optional<OfferItemComponent> held
    ) {
        return new SellOfferOption(
                "sell_money", "Sell to Shop",
                held.<List<OfferItemComponent>>map(List::of)
                        .orElse(List.of()),
                1L, 0L, OfferLimitPolicy.defaults(),
                OfferSchedule.always(), "");
    }

    public enum Template {
        MONEY("money"),
        BARTER("barter"),
        MONEY_OR_BARTER("money_or_barter"),
        MONEY_AND_BARTER("money_and_barter"),
        FREE("free"),
        SELL("sell"),
        BUY_AND_SELL("buy_and_sell"),
        BUNDLE("bundle"),
        ADVANCED("advanced");

        private final String key;

        Template(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }
}
