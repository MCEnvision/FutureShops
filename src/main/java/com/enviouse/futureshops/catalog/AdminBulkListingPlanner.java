package com.enviouse.futureshops.catalog;

import com.enviouse.futureshops.command.EconomyCommandUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pure planning and candidate construction for the administrator bulk editor. No method in this
 * class writes files, reloads a catalog, or trusts a client supplied listing id. The server passes
 * its registry snapshot and the current raw catalog to {@link #preview} on every request.
 */
public final class AdminBulkListingPlanner {
    public static final int MAX_SELECTIONS = 256;
    public static final int MAX_PRICE_TEXT = 32;
    public static final int MAX_CATEGORY_TEXT = 64;

    private AdminBulkListingPlanner() {
    }

    public enum Action {
        CREATE,
        SKIP,
        REPLACE,
        BLOCKING
    }

    public record Selection(String itemId, String nbt, String displayName) {
        public Selection(String itemId, String nbt) {
            this(itemId, nbt, "");
        }

        public Selection {
            itemId = itemId == null ? "" : itemId.trim().toLowerCase(Locale.ROOT);
            nbt = nbt == null ? "" : nbt.trim();
            displayName = displayName == null ? "" : displayName.trim();
        }
    }

    public record Row(
            int ordinal,
            String itemId,
            String nbt,
            String canonicalNbt,
            String identityDigest,
            String listingId,
            String displayName,
            Action action,
            String reason,
            boolean replaceEligible) {
        public Row {
            itemId = itemId == null ? "" : itemId;
            nbt = nbt == null ? "" : nbt;
            canonicalNbt = canonicalNbt == null ? "" : canonicalNbt;
            identityDigest = identityDigest == null ? "" : identityDigest;
            listingId = listingId == null ? "" : listingId;
            displayName = displayName == null ? "" : displayName;
            reason = reason == null ? "" : reason;
        }

        public boolean mutates() {
            return action == Action.CREATE || action == Action.REPLACE;
        }
    }

    public record Preview(
            UUID requestId,
            String registryFingerprint,
            String catalogFingerprint,
            String categoryId,
            String priceText,
            String stockText,
            long priceMinor,
            int stock,
            List<Row> rows,
            String fingerprint,
            JsonObject candidate) {
        public Preview {
            requestId = requestId == null ? new UUID(0L, 0L) : requestId;
            registryFingerprint = registryFingerprint == null ? "" : registryFingerprint;
            catalogFingerprint = catalogFingerprint == null ? "" : catalogFingerprint;
            categoryId = categoryId == null ? "" : categoryId;
            priceText = priceText == null ? "" : priceText;
            stockText = stockText == null ? "" : stockText;
            rows = List.copyOf(rows == null ? List.of() : rows);
            fingerprint = fingerprint == null ? "" : fingerprint;
            candidate = candidate == null ? new JsonObject() : candidate.deepCopy();
        }

        public List<Row> blockingRows() {
            return rows.stream().filter(row -> row.action() == Action.BLOCKING).toList();
        }

        public List<Row> conflicts() {
            return rows.stream().filter(row -> row.replaceEligible()).toList();
        }
    }

    public record Result(Preview preview, String error) {
        public static Result success(Preview preview) {
            return new Result(preview, "");
        }

        public static Result failure(String error) {
            return new Result(null, error == null ? "invalid bulk request" : error);
        }

        public boolean valid() {
            return preview != null && error.isBlank();
        }
    }

    /**
     * Creates a deterministic, server-authoritative preview. {@code approvedRegistryIds} must be
     * captured from the server registry for the current connection and is never derived from the
     * client list. Existing conflicts are SKIP by default and can only be changed by the explicit
     * listing ids passed to {@link #apply}.
     */
    public static Result preview(
            UUID requestId,
            Set<String> approvedRegistryIds,
            JsonObject root,
            List<Selection> selections,
            String categoryId,
            String priceText,
            String stockText,
            int currencyDecimals,
            int maximumStock) {
        if (root == null) {
            return Result.failure("catalog is unavailable");
        }
        if (approvedRegistryIds == null || approvedRegistryIds.isEmpty()) {
            return Result.failure("server registry snapshot is empty");
        }
        if (selections == null || selections.isEmpty()) {
            return Result.failure("select at least one item");
        }
        if (selections.size() > MAX_SELECTIONS) {
            return Result.failure("selection exceeds " + MAX_SELECTIONS + " items");
        }
        String normalizedCategory = normalizeCategory(categoryId);
        if (normalizedCategory.length() > MAX_CATEGORY_TEXT) {
            return Result.failure("category is too long");
        }
        if (priceText == null || priceText.trim().length() > MAX_PRICE_TEXT) {
            return Result.failure("price is too long");
        }
        long priceMinor;
        try {
            priceMinor = EconomyCommandUtil.parseAmountToMinorUnits(
                    priceText == null ? "" : priceText.trim(), currencyDecimals);
        } catch (RuntimeException exception) {
            return Result.failure("price must be a valid decimal amount");
        }
        if (priceMinor < 0L) {
            return Result.failure("price must not be negative");
        }
        StockValue stock = parseStock(stockText, maximumStock);
        if (!stock.valid()) {
            return Result.failure(stock.error());
        }

        Set<String> registry = new HashSet<>();
        for (String id : approvedRegistryIds) {
            if (id != null && !id.isBlank()) {
                registry.add(id.trim().toLowerCase(Locale.ROOT));
            }
        }
        List<PreparedSelection> prepared = new ArrayList<>();
        for (Selection selection : selections) {
            if (selection == null) {
                return Result.failure("selection is null");
            }
            ResourceLocation identifier = ResourceLocation.tryParse(selection.itemId());
            if (identifier == null || "minecraft:air".equals(selection.itemId())
                    || !registry.contains(selection.itemId())) {
                return Result.failure("item is not in the server registry snapshot: " + selection.itemId());
            }
            AdminBulkListingIdentity.Result identity = AdminBulkListingIdentity.parse(
                    selection.itemId(), selection.nbt());
            if (!identity.valid()) {
                return Result.failure(selection.itemId() + ": " + identity.error());
            }
            prepared.add(new PreparedSelection(selection, identity.identity()));
        }
        prepared.sort(Comparator.comparing((PreparedSelection value) -> value.identity.itemId())
                .thenComparing(value -> value.identity.hasExactNbt() ? 1 : 0)
                .thenComparing(value -> value.identity.digest()));

        String registryFingerprint = registryFingerprint(registry);
        String catalogFingerprint = catalogFingerprint(root);
        JsonArray items = items(root);
        ExistingIndex existingIndex = indexExisting(items);
        Map<String, List<JsonObject>> existing = existingIndex.matches();
        Set<String> usedListingIds = existing.values().stream()
                .flatMap(List::stream)
                .map(AdminBulkListingPlanner::listingId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<String> seenIdentity = new LinkedHashSet<>();
        List<Row> rows = new ArrayList<>();
        List<PreparedSelection> unique = new ArrayList<>();
        for (PreparedSelection value : prepared) {
            String identityKey = identityKey(value.identity);
            if (!seenIdentity.add(identityKey)) {
                rows.add(new Row(0, value.identity.itemId(), value.selection.nbt(),
                        value.identity.canonicalNbt(), value.identity.digest(), "",
                        displayName(value.selection), Action.SKIP,
                        "duplicate selection collapsed", false));
                continue;
            }
            unique.add(value);
        }

        for (PreparedSelection value : unique) {
            String key = identityKey(value.identity);
            List<JsonObject> matches = existing.getOrDefault(key, List.of());
            String listingId = "";
            Action action = Action.CREATE;
            String reason = "new listing";
            boolean replaceEligible = false;
            if (existingIndex.invalidItemIds().contains(value.identity.itemId())) {
                action = Action.BLOCKING;
                reason = "another listing for this item has invalid identity data";
            } else if (matches.size() > 1) {
                action = Action.BLOCKING;
                reason = "multiple existing listings have this exact identity";
            } else if (matches.size() == 1) {
                listingId = listingId(matches.get(0));
                if (existingIndex.duplicateListingIds().contains(listingId.toLowerCase(Locale.ROOT))) {
                    action = Action.BLOCKING;
                    reason = "multiple listings share this listing id";
                } else {
                    action = Action.SKIP;
                    reason = "existing listing skipped by default";
                    replaceEligible = true;
                }
            } else {
                listingId = nextListingId(usedListingIds, value.identity.itemId());
                usedListingIds.add(listingId);
            }
            rows.add(new Row(0, value.identity.itemId(), value.selection.nbt(),
                    value.identity.canonicalNbt(), value.identity.digest(), listingId,
                    displayName(value.selection), action, reason, replaceEligible));
        }
        rows.sort(Comparator.comparing(Row::itemId)
                .thenComparing(row -> row.canonicalNbt().isBlank() ? 0 : 1)
                .thenComparing(Row::identityDigest)
                .thenComparing(Row::reason));
        List<Row> numbered = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            numbered.add(new Row(index + 1, row.itemId(), row.nbt(), row.canonicalNbt(),
                    row.identityDigest(), row.listingId(), row.displayName(), row.action(),
                    row.reason(), row.replaceEligible()));
        }
        String previewFingerprint = previewFingerprint(registryFingerprint, catalogFingerprint,
                normalizedCategory, priceMinor, stock.value(), numbered);
        JsonObject candidate = applyRows(root, numbered, normalizedCategory, priceMinor,
                stock.value(), Set.of());
        Preview preview = new Preview(requestId, registryFingerprint, catalogFingerprint,
                normalizedCategory, priceText == null ? "" : priceText.trim(),
                stockText == null ? "" : stockText.trim(), priceMinor, stock.value(),
                numbered, previewFingerprint, candidate);
        return Result.success(preview);
    }

    /**
     * Applies explicit replacement choices to a preview candidate. A preview row not listed in
     * {@code replaceListingIds} remains skipped. The returned object is a deep copy and can be
     * validated and written by the synchronized catalog service.
     */
    public static JsonObject apply(Preview preview, Set<String> replaceListingIds) {
        if (preview == null) {
            throw new IllegalArgumentException("preview is required");
        }
        Set<String> replacements = replaceListingIds == null ? Set.of() : normalizeIds(replaceListingIds);
        return applyRows(preview.candidate(), preview.rows(), preview.categoryId(),
                preview.priceMinor(), preview.stock(), replacements);
    }

    private static JsonObject applyRows(JsonObject source, List<Row> rows, String categoryId,
                                        long priceMinor, int stock, Set<String> replacements) {
        JsonObject candidate = source.deepCopy();
        JsonArray items = items(candidate);
        Set<String> existingIds = new HashSet<>();
        for (JsonElement element : items) {
            if (element.isJsonObject()) {
                existingIds.add(listingId(element.getAsJsonObject()).toLowerCase(Locale.ROOT));
            }
        }
        for (Row row : rows) {
            if (row.action() == Action.BLOCKING) {
                continue;
            }
            boolean replace = row.replaceEligible()
                    && replacements.contains(row.listingId().toLowerCase(Locale.ROOT));
            if (row.action() == Action.SKIP && !replace) {
                continue;
            }
            if (row.action() == Action.CREATE) {
                if (existingIds.contains(row.listingId().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                JsonObject entry = new JsonObject();
                entry.addProperty("id", row.listingId());
                entry.addProperty("itemId", row.itemId());
                if (!row.displayName().isBlank()) {
                    entry.addProperty("displayName", row.displayName());
                }
                entry.addProperty("buyPrice", priceMinor);
                entry.addProperty("sellPrice", 0L);
                entry.addProperty("stock", stock);
                if (!categoryId.isBlank()) {
                    entry.addProperty("categoryId", categoryId);
                }
                if (!row.nbt().isBlank()) {
                    entry.addProperty("nbt", row.nbt());
                }
                items.add(entry);
                existingIds.add(row.listingId().toLowerCase(Locale.ROOT));
                continue;
            }
            if (!replace || row.listingId().isBlank() || !existingIds.contains(row.listingId().toLowerCase(Locale.ROOT))) {
                continue;
            }
            int index = indexOf(items, row.listingId());
            if (index < 0 || !items.get(index).isJsonObject()) {
                continue;
            }
            JsonObject entry = items.get(index).getAsJsonObject();
            entry.addProperty("itemId", row.itemId());
            entry.addProperty("buyPrice", priceMinor);
            entry.addProperty("sellPrice", 0L);
            entry.addProperty("stock", stock);
            if (row.nbt().isBlank()) {
                entry.remove("nbt");
            } else {
                entry.addProperty("nbt", row.nbt());
            }
        }
        return candidate;
    }

    private static ExistingIndex indexExisting(JsonArray items) {
        Map<String, List<JsonObject>> result = new HashMap<>();
        Set<String> invalidItemIds = new HashSet<>();
        Map<String, Integer> listingIdCounts = new HashMap<>();
        for (JsonElement element : items) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String existingListingId = listingId(object).toLowerCase(Locale.ROOT);
            listingIdCounts.merge(existingListingId, 1, Integer::sum);
            String itemId = text(object, "itemId");
            if (itemId.isBlank()) {
                invalidItemIds.add("");
                continue;
            }
            AdminBulkListingIdentity.Result identity = AdminBulkListingIdentity.parse(itemId,
                    text(object, "nbt"));
            if (!identity.valid()) {
                invalidItemIds.add(itemId.toLowerCase(Locale.ROOT));
                continue;
            }
            result.computeIfAbsent(identityKey(identity.identity()), ignored -> new ArrayList<>())
                    .add(object);
        }
        Set<String> duplicateListingIds = listingIdCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        return new ExistingIndex(result, invalidItemIds, duplicateListingIds);
    }

    private static String identityKey(AdminBulkListingIdentity identity) {
        return identity.itemId() + "\u0000" + identity.canonicalNbt();
    }

    private static String nextListingId(Set<String> used, String itemId) {
        String base = itemId;
        int colon = base.indexOf(':');
        if (colon >= 0 && colon + 1 < base.length()) {
            base = base.substring(colon + 1);
        }
        base = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (base.isBlank()) {
            base = "item";
        }
        for (int index = 1; index < Integer.MAX_VALUE; index++) {
            String candidate = base + "_" + index;
            if (!used.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("listing id space exhausted");
    }

    private static String listingId(JsonObject object) {
        String id = text(object, "id");
        return id.isBlank() ? text(object, "itemId") : id;
    }

    private static int indexOf(JsonArray items, String id) {
        for (int index = 0; index < items.size(); index++) {
            if (!items.get(index).isJsonObject()) {
                continue;
            }
            if (listingId(items.get(index).getAsJsonObject()).equalsIgnoreCase(id)) {
                return index;
            }
        }
        return -1;
    }

    private static String displayName(Selection selection) {
        if (selection.displayName() != null && !selection.displayName().isBlank()) {
            return selection.displayName();
        }
        String id = selection.itemId();
        int colon = id.indexOf(':');
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        return path.replace('_', ' ');
    }

    private static String normalizeCategory(String categoryId) {
        if (categoryId == null || categoryId.isBlank() || "all".equalsIgnoreCase(categoryId.trim())) {
            return "";
        }
        return categoryId.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static Set<String> normalizeIds(Set<String> values) {
        Set<String> result = new HashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    private static JsonArray items(JsonObject root) {
        if (!root.has("items") || !root.get("items").isJsonArray()) {
            JsonArray array = new JsonArray();
            root.add("items", array);
            return array;
        }
        return root.getAsJsonArray("items");
    }

    private static String text(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString().trim() : "";
    }

    private static String previewFingerprint(String registryFingerprint, String catalogFingerprint,
                                             String categoryId, long priceMinor, int stock,
                                             List<Row> rows) {
        StringBuilder value = new StringBuilder(registryFingerprint).append('|')
                .append(catalogFingerprint).append('|').append(categoryId).append('|')
                .append(priceMinor).append('|').append(stock);
        for (Row row : rows) {
            value.append('|').append(row.itemId()).append('|').append(row.canonicalNbt())
                    .append('|').append(row.listingId()).append('|').append(row.action());
        }
        return fingerprint(value.toString());
    }

    private static String fingerprint(List<String> values) {
        return fingerprint(String.join("\u0000", values));
    }

    public static String registryFingerprint(Set<String> registryIds) {
        if (registryIds == null) {
            return fingerprint("");
        }
        return fingerprint(registryIds.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .sorted().toList());
    }

    public static String catalogFingerprint(JsonObject root) {
        return fingerprint(root == null ? "" : root.toString());
    }

    private static String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("sha-256 is unavailable", exception);
        }
    }

    private static StockValue parseStock(String source, int maximumStock) {
        String value = source == null ? "" : source.trim();
        if (value.isBlank() || "∞".equals(value) || "-1".equals(value)) {
            return new StockValue(-1, true, "");
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0L || parsed > Integer.MAX_VALUE || parsed > Math.max(0, maximumStock)) {
                return new StockValue(0, false, "stock is outside the configured limit");
            }
            return new StockValue((int) parsed, true, "");
        } catch (NumberFormatException exception) {
            return new StockValue(0, false, "stock must be a whole number or unlimited");
        }
    }

    private record PreparedSelection(Selection selection, AdminBulkListingIdentity identity) {
    }

    private record ExistingIndex(Map<String, List<JsonObject>> matches,
                                 Set<String> invalidItemIds,
                                 Set<String> duplicateListingIds) {
    }

    private record StockValue(int value, boolean valid, String error) {
    }
}
