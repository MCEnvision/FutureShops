package com.enviouse.futureshops.catalog.offer;

import com.enviouse.futureshops.Config;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ServerShopOfferJsonParser {
    public static final int SCHEMA_VERSION = 2;
    public static final int MAX_LISTINGS = 10_000;
    private static final int MAX_NBT_TEXT_LENGTH = 65_536;

    private ServerShopOfferJsonParser() {
    }

    public static List<ServerShopOfferListing> parse(JsonObject root) {
        int schemaVersion = integer(root, "schemaVersion", 1);
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported server shop schema version "
                            + schemaVersion);
        }
        JsonArray listings = requiredArray(root, "listings");
        requireMaximum(listings,
                Math.min(MAX_LISTINGS, Config.adminShopMaximumListings),
                "offer listings");
        List<ServerShopOfferListing> parsed = new ArrayList<>();
        for (JsonElement element : listings) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException(
                        "Offer listing must be an object");
            }
            parsed.add(parseListing(element.getAsJsonObject()));
        }
        return List.copyOf(parsed);
    }

    private static ServerShopOfferListing parseListing(JsonObject object) {
        String listingId = identifier(object, "id");
        List<OfferItemComponent> outputs = components(
                requiredArray(object, "outputs"));
        List<AcquireOfferOption> acquire = new ArrayList<>();
        JsonArray acquireOptions = array(object, "acquireOptions");
        requireMaximum(acquireOptions, ServerShopOfferValidator.MAX_OPTIONS,
                "acquire options");
        for (JsonElement element : acquireOptions) {
            requireObject(element, "Acquire option");
            acquire.add(parseAcquire(element.getAsJsonObject()));
        }
        List<SellOfferOption> sell = new ArrayList<>();
        JsonArray sellOptions = array(object, "sellOptions");
        requireMaximum(sellOptions, ServerShopOfferValidator.MAX_OPTIONS,
                "sell options");
        for (JsonElement element : sellOptions) {
            requireObject(element, "Sell option");
            sell.add(parseSell(element.getAsJsonObject()));
        }
        JsonObject icon = object.has("icon")
                ? object.getAsJsonObject("icon") : null;
        String iconItemId = icon == null
                ? outputs.isEmpty() ? "" : outputs.get(0).itemId()
                : text(icon, "itemId", "");
        String iconNbt = icon == null
                ? outputs.isEmpty() ? "" : outputs.get(0).exactNbt()
                : text(icon, "nbt", "");
        ServerShopOfferListing listing = new ServerShopOfferListing(
                listingId, 0L, text(object, "displayName", listingId),
                text(object, "description", ""),
                identifier(object, "categoryId", "all"),
                iconItemId, iconNbt, bool(object, "active", true),
                longValue(object, "expiresAtEpoch", 0L),
                text(object, "permission", ""), outputs, acquire, sell,
                stock(object), limits(object), schedule(object),
                comparisons(object));
        return listing.withRevision(ServerShopOfferRevision.compute(listing));
    }

    private static AcquireOfferOption parseAcquire(JsonObject object) {
        String paymentType = text(object, "paymentType", "")
                .strip().toUpperCase(Locale.ROOT);
        boolean free = "FREE".equals(paymentType);
        boolean money = "MONEY".equals(paymentType)
                || "MONEY_AND_ITEMS".equals(paymentType);
        boolean items = "ITEMS".equals(paymentType)
                || "MONEY_AND_ITEMS".equals(paymentType);
        if (!free && !money && !items) {
            throw new IllegalArgumentException(
                    "Unknown acquire payment type");
        }
        List<OfferItemComponent> itemCosts = items
                ? components(requiredArray(object, "itemCosts"))
                : List.of();
        return new AcquireOfferOption(identifier(object, "id"),
                text(object, "label", paymentType), free, money,
                money ? longValue(object, "moneyCost", -1L) : 0L,
                itemCosts, integer(object, "outputMultiplier", 1),
                limits(object), schedule(object),
                text(object, "permission", ""));
    }

    private static SellOfferOption parseSell(JsonObject object) {
        return new SellOfferOption(identifier(object, "id"),
                text(object, "label", "Sell to Shop"),
                components(requiredArray(object, "inputs")),
                longValue(object, "moneyPayout", -1L),
                longValue(object, "capacity", 0L),
                limits(object), schedule(object),
                text(object, "permission", ""));
    }

    private static List<OfferItemComponent> components(JsonArray array) {
        requireMaximum(array, ServerShopOfferValidator.MAX_COMPONENTS,
                "offer components");
        List<OfferItemComponent> components = new ArrayList<>();
        for (JsonElement element : array) {
            requireObject(element, "Offer component");
            JsonObject object = element.getAsJsonObject();
            components.add(new OfferItemComponent(
                    identifier(object, "id"),
                    identifier(object, "itemId"),
                    integer(object, "count", 1),
                    text(object, "nbt", "")));
        }
        return OfferComponentNormalizer.normalize(components);
    }

    private static OfferStockPolicy stock(JsonObject owner) {
        if (!owner.has("stock")) {
            return OfferStockPolicy.unlimited();
        }
        JsonObject object = owner.getAsJsonObject("stock");
        String type = text(object, "type", "unlimited")
                .strip().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "UNLIMITED" -> OfferStockPolicy.unlimited();
            case "LIMITED", "LIMITED_INDEPENDENT" ->
                    OfferStockPolicy.limited(
                            longValue(object, "quantity", 0L),
                            longValue(object, "refreshSeconds", 0L));
            case "LINKED" -> new OfferStockPolicy(
                    OfferStockPolicy.Type.LINKED,
                    longValue(object, "quantity", 0L),
                    longValue(object, "refreshSeconds", 0L));
            default -> throw new IllegalArgumentException(
                    "Unknown offer stock type");
        };
    }

    private static OfferLimitPolicy limits(JsonObject owner) {
        if (!owner.has("limits")) {
            return OfferLimitPolicy.defaults();
        }
        JsonObject object = owner.getAsJsonObject("limits");
        return new OfferLimitPolicy(
                integer(object, "maximumPerRequest",
                        OfferLimitPolicy.DEFAULT_MAXIMUM_PER_REQUEST),
                longValue(object, "lifetime", 0L),
                longValue(object, "periodQuantity", 0L),
                longValue(object, "periodSeconds", 0L),
                longValue(object, "cooldownSeconds", 0L));
    }

    private static OfferSchedule schedule(JsonObject owner) {
        if (!owner.has("schedule")) {
            return OfferSchedule.always();
        }
        JsonObject object = owner.getAsJsonObject("schedule");
        return new OfferSchedule(
                longValue(object, "startsAtEpoch", 0L),
                longValue(object, "endsAtEpoch", 0L));
    }

    private static List<OfferBundleComparison> comparisons(
            JsonObject owner
    ) {
        List<OfferBundleComparison> comparisons = new ArrayList<>();
        JsonArray values = array(owner, "bundleComparisons");
        requireMaximum(values, ServerShopOfferValidator.MAX_COMPONENTS,
                "bundle comparisons");
        for (JsonElement element : values) {
            requireObject(element, "Bundle comparison");
            JsonObject object = element.getAsJsonObject();
            comparisons.add(new OfferBundleComparison(
                    identifier(object, "componentId"),
                    identifier(object, "listingId"),
                    identifier(object, "optionId")));
        }
        return List.copyOf(comparisons);
    }

    private static JsonArray requiredArray(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            throw new IllegalArgumentException(
                    "Missing offer array " + key);
        }
        return object.getAsJsonArray(key);
    }

    private static JsonArray array(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray()
                ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static String identifier(JsonObject object, String key) {
        if (!object.has(key)) {
            throw new IllegalArgumentException(
                    "Missing offer identifier " + key);
        }
        return identifier(object, key, "");
    }

    private static String identifier(
            JsonObject object,
            String key,
            String fallback
    ) {
        String value = text(object, key, fallback).strip()
                .toLowerCase(Locale.ROOT).replace(' ', '_');
        if (value.length() > ServerShopOfferValidator.MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException(
                    "Offer identifier is too long");
        }
        return value;
    }

    private static String text(
            JsonObject object,
            String key,
            String fallback
    ) {
        String value = object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString() : fallback;
        if (value.length() > MAX_NBT_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "Offer text value is too long");
        }
        return value;
    }

    private static int integer(
            JsonObject object,
            String key,
            int fallback
    ) {
        return object.has(key)
                ? object.get(key).getAsInt() : fallback;
    }

    private static long longValue(
            JsonObject object,
            String key,
            long fallback
    ) {
        return object.has(key)
                ? object.get(key).getAsLong() : fallback;
    }

    private static boolean bool(
            JsonObject object,
            String key,
            boolean fallback
    ) {
        return object.has(key)
                ? object.get(key).getAsBoolean() : fallback;
    }

    private static void requireMaximum(
            JsonArray values,
            int maximum,
            String label
    ) {
        if (values.size() > maximum) {
            throw new IllegalArgumentException(
                    "Too many " + label);
        }
    }

    private static void requireObject(JsonElement value, String label) {
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException(label + " must be an object");
        }
    }
}
