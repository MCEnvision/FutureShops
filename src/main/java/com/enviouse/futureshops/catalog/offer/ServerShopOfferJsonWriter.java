package com.enviouse.futureshops.catalog.offer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Locale;

public final class ServerShopOfferJsonWriter {
    private ServerShopOfferJsonWriter() {
    }

    public static JsonArray writeListings(
            List<ServerShopOfferListing> listings
    ) {
        JsonArray array = new JsonArray();
        listings.forEach(listing -> array.add(writeListing(listing)));
        return array;
    }

    public static JsonObject writeListing(
            ServerShopOfferListing listing
    ) {
        JsonObject object = new JsonObject();
        object.addProperty("id", listing.listingId());
        object.addProperty("displayName", listing.displayName());
        object.addProperty("description", listing.description());
        object.addProperty("categoryId", listing.categoryId());
        JsonObject icon = new JsonObject();
        icon.addProperty("itemId", listing.iconItemId());
        if (!listing.iconNbt().isBlank()) {
            icon.addProperty("nbt", listing.iconNbt());
        }
        object.add("icon", icon);
        object.addProperty("active", listing.active());
        object.addProperty("expiresAtEpoch", listing.expiresAtEpoch());
        if (!listing.permissionNode().isBlank()) {
            object.addProperty("permission", listing.permissionNode());
        }
        object.add("outputs", writeComponents(listing.outputs()));
        JsonArray acquire = new JsonArray();
        listing.acquireOptions().forEach(option ->
                acquire.add(writeAcquire(option)));
        object.add("acquireOptions", acquire);
        JsonArray sell = new JsonArray();
        listing.sellOptions().forEach(option ->
                sell.add(writeSell(option)));
        object.add("sellOptions", sell);
        object.add("stock", writeStock(listing.stockPolicy()));
        object.add("limits", writeLimits(listing.limits()));
        object.add("schedule", writeSchedule(listing.schedule()));
        JsonArray comparisons = new JsonArray();
        listing.bundleComparisons().forEach(comparison -> {
            JsonObject value = new JsonObject();
            value.addProperty("componentId",
                    comparison.componentId());
            value.addProperty("listingId", comparison.listingId());
            value.addProperty("optionId", comparison.optionId());
            comparisons.add(value);
        });
        object.add("bundleComparisons", comparisons);
        return object;
    }

    private static JsonObject writeAcquire(AcquireOfferOption option) {
        JsonObject object = new JsonObject();
        object.addProperty("id", option.optionId());
        object.addProperty("label", option.label());
        object.addProperty("paymentType", paymentType(option));
        if (option.moneyCostPresent()) {
            object.addProperty("moneyCost",
                    option.moneyCostMinorUnits());
        }
        if (option.hasItemCosts()) {
            object.add("itemCosts",
                    writeComponents(option.itemCosts()));
        }
        object.addProperty("outputMultiplier",
                option.outputMultiplier());
        object.add("limits", writeLimits(option.limits()));
        object.add("schedule", writeSchedule(option.schedule()));
        if (!option.permissionNode().isBlank()) {
            object.addProperty("permission",
                    option.permissionNode());
        }
        return object;
    }

    private static JsonObject writeSell(SellOfferOption option) {
        JsonObject object = new JsonObject();
        object.addProperty("id", option.optionId());
        object.addProperty("label", option.label());
        object.add("inputs", writeComponents(option.itemInputs()));
        object.addProperty("moneyPayout",
                option.moneyPayoutMinorUnits());
        object.addProperty("capacity", option.capacity());
        object.add("limits", writeLimits(option.limits()));
        object.add("schedule", writeSchedule(option.schedule()));
        if (!option.permissionNode().isBlank()) {
            object.addProperty("permission",
                    option.permissionNode());
        }
        return object;
    }

    private static JsonArray writeComponents(
            List<OfferItemComponent> components
    ) {
        JsonArray array = new JsonArray();
        components.forEach(component -> {
            JsonObject object = new JsonObject();
            object.addProperty("id", component.componentId());
            object.addProperty("itemId", component.itemId());
            object.addProperty("count", component.count());
            if (!component.exactNbt().isBlank()) {
                object.addProperty("nbt", component.exactNbt());
            }
            array.add(object);
        });
        return array;
    }

    private static JsonObject writeStock(OfferStockPolicy stock) {
        JsonObject object = new JsonObject();
        object.addProperty("type", stock.type().name()
                .toLowerCase(Locale.ROOT));
        object.addProperty("quantity", stock.quantity());
        object.addProperty("refreshSeconds", stock.refreshSeconds());
        return object;
    }

    private static JsonObject writeLimits(OfferLimitPolicy limits) {
        JsonObject object = new JsonObject();
        object.addProperty("maximumPerRequest",
                limits.maximumPerRequest());
        object.addProperty("lifetime", limits.lifetimeLimit());
        object.addProperty("periodQuantity", limits.periodLimit());
        object.addProperty("periodSeconds", limits.periodSeconds());
        object.addProperty("cooldownSeconds",
                limits.cooldownSeconds());
        return object;
    }

    private static JsonObject writeSchedule(OfferSchedule schedule) {
        JsonObject object = new JsonObject();
        object.addProperty("startsAtEpoch", schedule.startsAtEpoch());
        object.addProperty("endsAtEpoch", schedule.endsAtEpoch());
        return object;
    }

    private static String paymentType(AcquireOfferOption option) {
        if (option.free()) {
            return "FREE";
        }
        if (option.compound()) {
            return "MONEY_AND_ITEMS";
        }
        return option.moneyCostPresent() ? "MONEY" : "ITEMS";
    }
}
