package com.enviouse.futureshops.catalog;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.catalog.offer.LegacyServerShopOfferCompiler;
import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferBundleComparison;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.catalog.offer.OfferSchedule;
import com.enviouse.futureshops.catalog.offer.OfferStockPolicy;
import com.enviouse.futureshops.catalog.offer.OfferValidationIssue;
import com.enviouse.futureshops.catalog.offer.OfferValidationResult;
import com.enviouse.futureshops.catalog.offer.SellOfferOption;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferCatalogValidator;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferJsonParser;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferJsonWriter;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferLegacyProjection;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferRevision;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reads shop definitions from {@code config/futureshops/shops/*.json}.
 *
 * <p>The bundled admin shop lives in {@code admin.json}. If that file is missing on any load
 * (e.g. removed or moved by the operator) it is rewritten from the bundled default.
 * A legacy {@code default.json} is auto-renamed to {@code admin.json} on first sight.
 * Existing operator files are never replaced when the bundled default changes.
 * Prices are in minor currency units (e.g., 1500 = $15.00 at 2 dp).
 */
public final class ShopDefinitionLoader {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Filename of the bundled admin shop config. */
    public static final String ADMIN_SHOP_FILENAME = "admin.json";
    private static final String LEGACY_DEFAULT_FILENAME = "default.json";
    private static final int MAX_SHOP_DEFINITION_FILES = 256;
    private static final long MAX_SHOP_DEFINITION_BYTES = 8L * 1024L * 1024L;
    private static final int MAX_LEGACY_CATEGORIES = 256;
    private static final int MAX_LEGACY_PROMOS = 1024;
    private static final int MAX_LEGACY_BARTER_RECIPES = 1024;
    private static final int MAX_LEGACY_BARTER_INGREDIENTS = 36;
    private static final int MAX_LEGACY_NBT_TEXT_LENGTH = 65_536;

    private ShopDefinitionLoader() {
    }

    /**
     * Returns the absolute path to the admin shop config file. Does not create the file.
     */
    public static Path adminShopPath() {
        return shopsDirectory().resolve(ADMIN_SHOP_FILENAME);
    }

    public static Path shopsDirectory() {
        return shopsDirectory(FMLPaths.CONFIGDIR.get());
    }

    public static boolean prepareStorage() {
        return prepareStorage(FMLPaths.CONFIGDIR.get());
    }

    static synchronized boolean prepareStorage(Path configDirectory) {
        Path root = Objects.requireNonNull(configDirectory, "configDirectory")
                .toAbsolutePath().normalize();
        Path futureshopsDir = root.resolve("futureshops");
        Path shopsDir = futureshopsDir.resolve("shops");
        try {
            if (!prepareDirectory(root, "Configuration root")
                    || !prepareDirectory(futureshopsDir,
                    "FutureShops configuration directory")
                    || !prepareDirectory(shopsDir,
                    "FutureShops shops directory")) {
                return false;
            }
        } catch (IOException exception) {
            LOGGER.error(
                    "[FutureShops] Could not create shops config directory '{}': {}",
                    shopsDir, exception.getMessage());
            return false;
        }

        Path adminFile = shopsDir.resolve(ADMIN_SHOP_FILENAME);
        if (Files.exists(adminFile, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isRegularFile(adminFile, LinkOption.NOFOLLOW_LINKS)) {
                return true;
            }
            LOGGER.error(
                    "[FutureShops] Admin shop path is not a regular file '{}'.",
                    adminFile);
            return false;
        }

        Path legacyFile = shopsDir.resolve(LEGACY_DEFAULT_FILENAME);
        if (Files.exists(legacyFile, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(
                    legacyFile, LinkOption.NOFOLLOW_LINKS)) {
                LOGGER.error(
                        "[FutureShops] Legacy shop path is not a regular file '{}'.",
                        legacyFile);
                return false;
            }
            try {
                Files.move(legacyFile, adminFile);
                LOGGER.info(
                        "[FutureShops] Migrated '{}' to '{}'.",
                        LEGACY_DEFAULT_FILENAME, ADMIN_SHOP_FILENAME);
                return true;
            } catch (FileAlreadyExistsException exception) {
                return Files.isRegularFile(
                        adminFile, LinkOption.NOFOLLOW_LINKS);
            } catch (IOException exception) {
                LOGGER.error(
                        "[FutureShops] Could not migrate '{}' to '{}': {}",
                        LEGACY_DEFAULT_FILENAME, ADMIN_SHOP_FILENAME,
                        exception.getMessage());
                return true;
            }
        }

        return writeFile(adminFile, defaultShopJson());
    }

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    /**
     * Loads all shop definitions from disk and returns them as an immutable list.
     * Never returns null; falls back to the hardcoded default if loading fails entirely.
     */
    public static List<ShopDefinition> loadAll() {
        return loadAll(FMLPaths.CONFIGDIR.get());
    }

    static List<ShopDefinition> loadAll(Path configDirectory) {
        Path shopsDir = shopsDirectory(configDirectory).toAbsolutePath().normalize();
        if (!prepareStorage(configDirectory) || !safeDirectory(shopsDir)) {
            return List.of(buildDefaultShop());
        }

        List<Path> jsonFiles = new ArrayList<>();
        try (var stream = Files.newDirectoryStream(shopsDir,
                path -> path.getFileName().toString().endsWith(".json"))) {
            for (Path path : stream) {
                jsonFiles.add(path);
                if (jsonFiles.size() > MAX_SHOP_DEFINITION_FILES) {
                    break;
                }
            }
        } catch (IOException e) {
            LOGGER.error("[FutureShops] Failed to list files in '{}': {}", shopsDir, e.getMessage());
            return List.of(buildDefaultShop());
        }
        if (jsonFiles.size() > MAX_SHOP_DEFINITION_FILES) {
            LOGGER.error("[FutureShops] Refused more than {} shop definition files in '{}'",
                    MAX_SHOP_DEFINITION_FILES, shopsDir);
            return List.of(buildDefaultShop());
        }
        for (Path file : jsonFiles) {
            if (!safeRegularFile(file)) {
                LOGGER.error("[FutureShops] Refused unsafe shop definition path '{}'",
                        file.getFileName());
                return List.of(buildDefaultShop());
            }
            try {
                if (Files.size(file) > MAX_SHOP_DEFINITION_BYTES) {
                    LOGGER.error("[FutureShops] Refused oversized shop definition '{}'",
                            file.getFileName());
                    return List.of(buildDefaultShop());
                }
            } catch (IOException exception) {
                LOGGER.error("[FutureShops] Failed to inspect shop definition '{}': {}",
                        file.getFileName(), exception.getMessage());
                return List.of(buildDefaultShop());
            }
        }

        List<ShopDefinition> result = new ArrayList<>();
        for (Path file : jsonFiles) {
            try {
                String content = readBounded(file);
                ShopDefinition def = parseJson(content, file.getFileName().toString());
                if (def != null) {
                    result.add(def);
                    LOGGER.info("[FutureShops] Loaded shop '{}' from {}", def.shopId(), file.getFileName());
                }
            } catch (IOException e) {
                LOGGER.error("[FutureShops] Failed to read '{}': {}", file.getFileName(), e.getMessage());
            }
        }

        if (result.isEmpty()) {
            LOGGER.warn("[FutureShops] No valid shop definitions loaded — using hardcoded default.");
            result.add(buildDefaultShop());
        }

        return List.copyOf(result);
    }

    // -------------------------------------------------------------------------
    // JSON parsing
    // -------------------------------------------------------------------------

    // package-private for unit testing (listingId defaulting + expiresAtEpoch parsing)
    static ShopDefinition parseJson(String json, String filename) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            int schemaVersion = getInt(root, "schemaVersion", 1);
            if (schemaVersion < 1
                    || schemaVersion
                    > ServerShopOfferJsonParser.SCHEMA_VERSION) {
                throw new IllegalArgumentException(
                        "Unsupported server shop schema version "
                                + schemaVersion);
            }

            String shopId      = getString(root, "shopId", "default");
            String displayName = getString(root, "displayName", shopId);

            List<CategoryDef> categories = new ArrayList<>();
            for (JsonElement el : optionalArray(root, "categories",
                    MAX_LEGACY_CATEGORIES)) {
                    JsonObject o = el.getAsJsonObject();
                    if (!o.has("id")) continue;
                    categories.add(new CategoryDef(
                            o.get("id").getAsString(),
                            getString(o, "displayName", o.get("id").getAsString()),
                            getInt(o, "sortOrder", 0)));
            }

            List<ItemDef> items = new ArrayList<>();
            for (JsonElement el : optionalArray(root, "items", maximumListings())) {
                    JsonObject o = el.getAsJsonObject();
                    if (!o.has("itemId")) continue;
                    String itemId = o.get("itemId").getAsString();
                    // Optional stable per-listing id. getString() only falls back on absent/JSON-null
                    // (NOT on blank), so the explicit isBlank() check is required — a literal "" must
                    // default to itemId rather than become a blank listingId. Absent/blank => listingId
                    // == itemId, which keeps legacy single-variant entries (and their stock/pricing
                    // persisted keys, which are itemId strings) byte-identical with zero migration.
                    // Lowercase the id so the stored catalog key matches the client echo and the
                    // case-insensitive command lookups (registry itemIds are already lowercase).
                    String rawId = getString(o, "id", null);
                    String listingId = (rawId != null && !rawId.isBlank())
                            ? rawId.trim().toLowerCase(java.util.Locale.ROOT)
                            : itemId;
                    items.add(new ItemDef(
                            listingId,
                            itemId,
                            getString(o, "displayName", null),
                            getLong(o, "buyPrice", 0L),
                            getLong(o, "sellPrice", 0L),
                            getInt(o, "stock", -1),
                            getBool(o, "barterEnabled", false),
                            getString(o, "categoryId", "all"),
                            getInt(o, "stockRefreshSeconds", 0),
                            // SNBT for items whose identity lives in NBT (enchanted books,
                            // Tacz guns, named/lored items). Empty string falls through to
                            // the bare-item code path so legacy admin.json entries that
                            // predate the field continue to work without a migration.
                            getString(o, "nbt", ""),
                            // Listing availability window (unix seconds); 0 = never expires.
                            getLong(o, "expiresAtEpoch", 0L)));
            }

            List<PromoDef> promos = new ArrayList<>();
            for (JsonElement el : optionalArray(root, "promos",
                    MAX_LEGACY_PROMOS)) {
                    JsonObject o = el.getAsJsonObject();
                    if (!o.has("promoId")) continue;
                    promos.add(new PromoDef(
                            o.get("promoId").getAsString(),
                            getString(o, "promoType", "PERCENTAGE"),
                            getString(o, "targetItemId", ""),
                            getDouble(o, "discountValue", 0.0),
                            getLong(o, "endTimeEpoch", 0L)));
            }

            List<BarterRecipeDef> barterRecipes = new ArrayList<>();
            for (JsonElement el : optionalArray(root, "barterRecipes",
                    MAX_LEGACY_BARTER_RECIPES)) {
                    JsonObject o = el.getAsJsonObject();
                    if (!o.has("recipeId") || !o.has("targetItemId") || !o.has("ingredients")) continue;

                    List<BarterIngredientDef> ingredients = new ArrayList<>();
                    for (JsonElement ingredientEl : optionalArray(o, "ingredients",
                            MAX_LEGACY_BARTER_INGREDIENTS)) {
                        JsonObject ingredient = ingredientEl.getAsJsonObject();
                        if (!ingredient.has("itemId")) continue;
                        ingredients.add(new BarterIngredientDef(
                                ingredient.get("itemId").getAsString(),
                                getInt(ingredient, "count", 1),
                                // Optional SNBT: non-blank makes the ingredient NBT-strict
                                // (exact tag), blank keeps lenient identity matching.
                                getString(ingredient, "nbt", "")));
                    }

                    barterRecipes.add(new BarterRecipeDef(
                            o.get("recipeId").getAsString(),
                            o.get("targetItemId").getAsString(),
                            getInt(o, "outputCount", 1),
                            List.copyOf(ingredients)));
                }

            List<ServerShopOfferListing> offers;
            if (schemaVersion == ServerShopOfferJsonParser.SCHEMA_VERSION) {
                offers = ServerShopOfferJsonParser.parse(root);
                OfferValidationResult validation =
                        ServerShopOfferCatalogValidator.validate(offers,
                                ShopDefinitionLoader::knownItem,
                                ShopDefinitionLoader::validNbt,
                                com.enviouse.futureshops.catalog.offer
                                        .OfferEscrowFanout
                                        ::registeredMaximumStackSize);
                if (!validation.valid()) {
                    OfferValidationIssue issue = validation.issues().stream()
                            .filter(candidate -> candidate.severity()
                                    == OfferValidationIssue.Severity.ERROR)
                            .findFirst().orElseThrow();
                    throw new IllegalArgumentException(issue.code()
                            + " at " + issue.path());
                }
                items = new ArrayList<>(
                        ServerShopOfferLegacyProjection.items(offers));
                barterRecipes = new ArrayList<>(
                        ServerShopOfferLegacyProjection.barterRecipes(
                                offers));
            } else {
                offers = LegacyServerShopOfferCompiler.compile(items,
                        barterRecipes);
            }
            return new ShopDefinition(schemaVersion, shopId, displayName,
                    List.copyOf(categories), List.copyOf(items),
                    List.copyOf(promos), List.copyOf(barterRecipes), offers);

        } catch (Exception e) {
            LOGGER.error("[FutureShops] Parse error in '{}': {}", filename, e.getMessage());
            return null;
        }
    }

    public static boolean validCandidate(String json, String filename) {
        return parseJson(json, filename) != null;
    }

    // -------------------------------------------------------------------------
    // JSON helpers
    // -------------------------------------------------------------------------

    private static String getString(JsonObject o, String key, String fallback) {
        String value = o.has(key) && !o.get(key).isJsonNull()
                ? o.get(key).getAsString() : fallback;
        if (value != null && value.length() > MAX_LEGACY_NBT_TEXT_LENGTH) {
            throw new IllegalArgumentException("Text value is too long");
        }
        return value;
    }

    private static int getInt(JsonObject o, String key, int fallback) {
        return o.has(key) ? o.get(key).getAsInt() : fallback;
    }

    private static long getLong(JsonObject o, String key, long fallback) {
        return o.has(key) ? o.get(key).getAsLong() : fallback;
    }

    private static double getDouble(JsonObject o, String key, double fallback) {
        return o.has(key) ? o.get(key).getAsDouble() : fallback;
    }

    private static boolean getBool(JsonObject o, String key, boolean fallback) {
        return o.has(key) ? o.get(key).getAsBoolean() : fallback;
    }

    private static boolean knownItem(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || "minecraft:air".equals(itemId)) {
            return false;
        }
        return BuiltInRegistries.ITEM.containsKey(id);
    }

    private static boolean validNbt(String nbt) {
        if (nbt == null || nbt.length() > MAX_LEGACY_NBT_TEXT_LENGTH) {
            return false;
        }
        try {
            TagParser.parseTag(nbt);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // File write helper
    // -------------------------------------------------------------------------

    private static boolean writeFile(Path path, String content) {
        try {
            Files.writeString(path, content,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            LOGGER.info("[FutureShops] Wrote admin shop config to {}", path);
            return true;
        } catch (FileAlreadyExistsException exception) {
            return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException exception) {
            LOGGER.error(
                    "[FutureShops] Failed to write admin shop to '{}': {}",
                    path, exception.getMessage());
            return false;
        }
    }

    private static Path shopsDirectory(Path configDirectory) {
        return Objects.requireNonNull(configDirectory, "configDirectory")
                .resolve("futureshops").resolve("shops");
    }

    private static JsonArray optionalArray(JsonObject object, String key,
                                           int maximum) {
        if (!object.has(key)) {
            return new JsonArray();
        }
        JsonElement value = object.get(key);
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException("Expected array " + key);
        }
        JsonArray array = value.getAsJsonArray();
        if (array.size() > maximum) {
            throw new IllegalArgumentException("Too many " + key);
        }
        return array;
    }

    private static int maximumListings() {
        return Math.min(ServerShopOfferJsonParser.MAX_LISTINGS,
                Math.max(1, Config.adminShopMaximumListings));
    }

    private static boolean prepareDirectory(Path directory, String description)
            throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(directory);
        }
        if (Files.isSymbolicLink(directory)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            LOGGER.error("[FutureShops] {} is not a safe directory '{}'.",
                    description, directory);
            return false;
        }
        return true;
    }

    private static boolean safeRegularFile(Path path) {
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(path)
                && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean safeDirectory(Path path) {
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(path)
                && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static String readBounded(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path,
                StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = channel.size();
            if (size > MAX_SHOP_DEFINITION_BYTES) {
                throw new IOException("Shop definition is too large");
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(
                    (int) Math.min(size, MAX_SHOP_DEFINITION_BYTES));
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            int read;
            while ((read = channel.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                buffer.flip();
                if (bytes.size() + buffer.remaining()
                        > MAX_SHOP_DEFINITION_BYTES) {
                    throw new IOException("Shop definition is too large");
                }
                while (buffer.hasRemaining()) {
                    bytes.write(buffer.get());
                }
                buffer.clear();
            }
            try {
                return StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes.toByteArray()))
                        .toString();
            } catch (CharacterCodingException exception) {
                throw new IOException("Shop definition is not valid UTF8",
                        exception);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Hardcoded default catalog — also written as the initial default.json
    // -------------------------------------------------------------------------

    private static ShopDefinition buildDefaultShop() {
        List<CategoryDef> cats = List.of(
                new CategoryDef("tools",     "Tools",     1),
                new CategoryDef("materials", "Materials", 2),
                new CategoryDef("food",      "Food",      3));

        List<ItemDef> items = List.of(
                new ItemDef("minecraft:diamond_pickaxe",         "Diamond Pickaxe",         2000L, 1000L, -1,  false, "tools"),
                new ItemDef("minecraft:diamond_sword",           "Diamond Sword",            1500L,  750L, -1,  false, "tools"),
                new ItemDef("minecraft:iron_pickaxe",            "Iron Pickaxe",              400L,  200L, -1,  false, "tools"),
                new ItemDef("minecraft:iron_sword",              "Iron Sword",                400L,  200L, -1,  false, "tools"),
                new ItemDef("minecraft:iron_shovel",             "Iron Shovel",               400L,  200L, -1,  false, "tools"),
                new ItemDef("minecraft:diamond",                 "Diamond",                   500L,  250L, -1,  false, "materials"),
                new ItemDef("minecraft:emerald",                 "Emerald",                   300L,  150L, -1,  true,  "materials"),
                new ItemDef("minecraft:iron_ingot",              "Iron Ingot",                 50L,   25L, 100, false, "materials"),
                new ItemDef("minecraft:gold_ingot",              "Gold Ingot",                150L,   75L, -1,  false, "materials"),
                new ItemDef("minecraft:netherite_ingot",         "Netherite Ingot",          5000L, 2500L, 10,  false, "materials"),
                new ItemDef("minecraft:bread",                   "Bread",                      10L,    5L, -1,  false, "food"),
                new ItemDef("minecraft:cooked_beef",             "Steak",                      20L,   10L, -1,  false, "food"),
                new ItemDef("minecraft:golden_apple",            "Golden Apple",              200L,  100L, 50,  false, "food"),
                new ItemDef("minecraft:enchanted_golden_apple",  "Enchanted Golden Apple",   5000L,    0L, 5,   false, "food"));

        List<PromoDef> promos = List.of(
                new PromoDef("promo_emerald_10", "PERCENTAGE", "minecraft:emerald", 10.0, 0L));

        List<BarterRecipeDef> barterRecipes = List.of(
                new BarterRecipeDef(
                        "emerald_trade_iron",
                        "minecraft:emerald",
                        1,
                        List.of(new BarterIngredientDef("minecraft:iron_ingot", 4))),
                new BarterRecipeDef(
                        "golden_apple_trade_gold",
                        "minecraft:golden_apple",
                        1,
                        List.of(new BarterIngredientDef("minecraft:gold_ingot", 8), new BarterIngredientDef("minecraft:apple", 1))));

        List<ServerShopOfferListing> offers = new ArrayList<>(
                LegacyServerShopOfferCompiler.compile(
                        items, barterRecipes));
        offers.add(defaultFreeOffer());
        offers.add(defaultSellOnlyOffer());
        offers.add(defaultBundleOffer());
        return new ShopDefinition(
                ServerShopOfferJsonParser.SCHEMA_VERSION,
                "default", "Server Shop",
                List.copyOf(cats), List.copyOf(items),
                List.copyOf(promos), List.copyOf(barterRecipes),
                List.copyOf(offers));
    }

    static String defaultShopJson() {
        ShopDefinition shop = buildDefaultShop();
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion",
                ServerShopOfferJsonParser.SCHEMA_VERSION);
        root.addProperty("shopId", shop.shopId());
        root.addProperty("displayName", shop.displayName());
        JsonArray categories = new JsonArray();
        shop.categories().forEach(category -> {
            JsonObject value = new JsonObject();
            value.addProperty("id", category.id());
            value.addProperty("displayName", category.displayName());
            value.addProperty("sortOrder", category.sortOrder());
            categories.add(value);
        });
        root.add("categories", categories);
        JsonArray promotions = new JsonArray();
        shop.promos().forEach(promotion -> {
            JsonObject value = new JsonObject();
            value.addProperty("promoId", promotion.promoId());
            value.addProperty("promoType", promotion.promoType());
            value.addProperty("targetItemId",
                    promotion.targetItemId());
            value.addProperty("discountValue",
                    promotion.discountValue());
            value.addProperty("endTimeEpoch",
                    promotion.endTimeEpoch());
            promotions.add(value);
        });
        root.add("promos", promotions);
        root.add("listings",
                ServerShopOfferJsonWriter.writeListings(shop.offers()));
        return new GsonBuilder().setPrettyPrinting().create()
                .toJson(root) + System.lineSeparator();
    }

    private static ServerShopOfferListing defaultFreeOffer() {
        return versioned(new ServerShopOfferListing(
                "free_welcome_cookie", 0L,
                "Free Welcome Cookie",
                "A free example. Each player can claim it once.",
                "food", "minecraft:cookie", "", true, 0L, "",
                List.of(new OfferItemComponent(
                        "cookie", "minecraft:cookie", 1, "")),
                List.of(AcquireOfferOption.free("get_free")),
                List.of(), OfferStockPolicy.unlimited(),
                new OfferLimitPolicy(1, 1L, 0L, 0L, 0L),
                OfferSchedule.always(), List.of()));
    }

    private static ServerShopOfferListing defaultSellOnlyOffer() {
        return versioned(new ServerShopOfferListing(
                "sell_rotten_flesh", 0L,
                "Sell Rotten Flesh",
                "Give the shop 8 rotten flesh and receive 1.00.",
                "materials", "minecraft:rotten_flesh", "",
                true, 0L, "", List.of(), List.of(),
                List.of(new SellOfferOption(
                        "sell_to_shop", "Sell 8 Rotten Flesh",
                        List.of(new OfferItemComponent(
                                "rotten_flesh",
                                "minecraft:rotten_flesh", 8, "")),
                        100L, 0L, OfferLimitPolicy.defaults(),
                        OfferSchedule.always(), "")),
                OfferStockPolicy.unlimited(),
                OfferLimitPolicy.defaults(),
                OfferSchedule.always(), List.of()));
    }

    private static ServerShopOfferListing defaultBundleOffer() {
        return versioned(new ServerShopOfferListing(
                "iron_tool_bundle", 0L,
                "Iron Tool Bundle",
                "Three iron tools for 10.00. Save 2.00.",
                "tools", "minecraft:iron_pickaxe", "",
                true, 0L, "",
                List.of(
                        new OfferItemComponent(
                                "pickaxe",
                                "minecraft:iron_pickaxe", 1, ""),
                        new OfferItemComponent(
                                "sword",
                                "minecraft:iron_sword", 1, ""),
                        new OfferItemComponent(
                                "shovel",
                                "minecraft:iron_shovel", 1, "")),
                List.of(AcquireOfferOption.money(
                        "get_money", 1000L)),
                List.of(), OfferStockPolicy.unlimited(),
                OfferLimitPolicy.defaults(), OfferSchedule.always(),
                List.of(
                        new OfferBundleComparison(
                                "pickaxe",
                                "minecraft:iron_pickaxe", "money"),
                        new OfferBundleComparison(
                                "sword",
                                "minecraft:iron_sword", "money"),
                        new OfferBundleComparison(
                                "shovel",
                                "minecraft:iron_shovel", "money"))));
    }

    private static ServerShopOfferListing versioned(
            ServerShopOfferListing listing
    ) {
        return listing.withRevision(
                ServerShopOfferRevision.compute(listing));
    }
}
