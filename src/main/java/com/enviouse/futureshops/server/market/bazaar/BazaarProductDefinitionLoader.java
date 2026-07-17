package com.enviouse.futureshops.server.market.bazaar;

import com.enviouse.futureshops.config.BazaarConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.minecraft.nbt.TagParser;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class BazaarProductDefinitionLoader {
    public static final int MAXIMUM_FILES = 512;
    public static final int MAXIMUM_PRODUCTS = 2048;
    public static final long MAXIMUM_FILE_BYTES = 262144L;
    public static final long MAXIMUM_TOTAL_BYTES = 4194304L;

    private static final Pattern RESOURCE_NAMESPACE = Pattern.compile(
            "[a-z0-9_.-]+");
    private static final Pattern RESOURCE_PATH = Pattern.compile(
            "[a-z0-9/._-]+");
    private static final Set<String> ROOT_FIELDS = Set.of(
            "schema", "products");
    private static final Set<String> PRODUCT_FIELDS = Set.of(
            "id", "version", "item", "exactNbt", "category",
            "displayName", "iconItem", "status", "enabled", "lotSize",
            "priceTickMinor", "minimumPriceMinor", "maximumPriceMinor",
            "maximumQuantity", "identityPolicy", "allowedDimensions",
            "restrictions");
    private static final Set<String> RESTRICTION_FIELDS = Set.of(
            "allowDamaged", "allowNamed", "allowEnchanted",
            "allowContainers", "allowCapabilities");

    private BazaarProductDefinitionLoader() {
    }

    public static BazaarProductCatalogSnapshot load(
            Path directory,
            BazaarConfig.ProductDefaults defaults,
            int defaultMaximumQuantity,
            Predicate<String> itemExists
    ) throws IOException {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(defaults, "defaults");
        Objects.requireNonNull(itemExists, "itemExists");
        if (defaultMaximumQuantity <= 0) {
            throw new IllegalArgumentException(
                    "Default Bazaar maximum quantity is invalid");
        }
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return BazaarProductCatalogSnapshot.empty();
        }
        BasicFileAttributes directoryAttributes = Files.readAttributes(
                directory, BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        if (!directoryAttributes.isDirectory()
                || directoryAttributes.isSymbolicLink()) {
            throw new IllegalArgumentException(
                    "Bazaar product path must be a real directory");
        }
        List<Path> files;
        try (var stream = Files.list(directory)) {
            files = stream.filter(path -> path.getFileName().toString()
                            .toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted((first, second) -> first.getFileName().toString()
                            .compareTo(second.getFileName().toString()))
                    .toList();
        }
        if (files.size() > MAXIMUM_FILES) {
            throw new IllegalArgumentException(
                    "Bazaar product file limit is exceeded");
        }
        List<BazaarProductDefinition> definitions = new ArrayList<>();
        long totalBytes = 0L;
        for (Path file : files) {
            BasicFileAttributes attributes = Files.readAttributes(file,
                    BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
                throw rejected(file, "Product file must be a regular file");
            }
            if (attributes.size() > MAXIMUM_FILE_BYTES) {
                throw rejected(file, "Product file size limit is exceeded");
            }
            byte[] encoded = Files.readAllBytes(file);
            if (encoded.length > MAXIMUM_FILE_BYTES) {
                throw rejected(file, "Product file size changed while loading");
            }
            totalBytes = Math.addExact(totalBytes, encoded.length);
            if (totalBytes > MAXIMUM_TOTAL_BYTES) {
                throw new IllegalArgumentException(
                        "Bazaar product directory size limit is exceeded");
            }
            parseFile(decodeUtf8(encoded, file),
                    file.getFileName().toString(), defaults,
                    defaultMaximumQuantity, itemExists, definitions);
            if (definitions.size() > MAXIMUM_PRODUCTS) {
                throw new IllegalArgumentException(
                        "Bazaar product definition limit is exceeded");
            }
        }
        validateCatalog(definitions);
        return BazaarProductCatalogSnapshot.of(definitions);
    }

    static void parseFile(
            String json,
            String sourceName,
            BazaarConfig.ProductDefaults defaults,
            int defaultMaximumQuantity,
            Predicate<String> itemExists,
            List<BazaarProductDefinition> destination
    ) {
        List<BazaarProductDefinition> parsedDefinitions = new ArrayList<>();
        try {
            rejectDuplicateFields(json);
            JsonElement parsed = JsonParser.parseString(
                    Objects.requireNonNull(json, "json"));
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException(
                        "Product file root must be an object");
            }
            JsonObject root = parsed.getAsJsonObject();
            if (root.has("products")) {
                requireKnownFields(root, ROOT_FIELDS, "Product file root");
                int schema = integer(root, "schema", 1, true);
                if (schema != 1) {
                    throw new IllegalArgumentException(
                            "Product file schema is unsupported");
                }
                JsonElement productsElement = root.get("products");
                if (productsElement == null || !productsElement.isJsonArray()) {
                    throw new IllegalArgumentException(
                            "Product collection must be an array");
                }
                JsonArray products = productsElement.getAsJsonArray();
                for (int index = 0; index < products.size(); index++) {
                    JsonElement element = products.get(index);
                    if (!element.isJsonObject()) {
                        throw new IllegalArgumentException(
                                "Product entry must be an object");
                    }
                    parsedDefinitions.add(parseProduct(element.getAsJsonObject(),
                            sourceName, defaults, defaultMaximumQuantity,
                            itemExists));
                }
            } else {
                Set<String> allowed = new HashSet<>(PRODUCT_FIELDS);
                allowed.add("schema");
                requireKnownFields(root, allowed, "Product file root");
                int schema = integer(root, "schema", 1, true);
                if (schema != 1) {
                    throw new IllegalArgumentException(
                            "Product file schema is unsupported");
                }
                JsonObject productObject = root.deepCopy();
                productObject.remove("schema");
                parsedDefinitions.add(parseProduct(productObject, sourceName, defaults,
                        defaultMaximumQuantity, itemExists));
            }
            destination.addAll(parsedDefinitions);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException
                    && exception.getMessage() != null
                    && exception.getMessage().startsWith(sourceName + ". ")) {
                throw exception;
            }
            throw new IllegalArgumentException(sourceName + ". "
                    + safeMessage(exception), exception);
        }
    }

    static boolean validResourceId(String value) {
        if (value == null || value.length() > 256) {
            return false;
        }
        int separator = value.indexOf(':');
        return separator > 0 && separator == value.lastIndexOf(':')
                && separator < value.length() - 1
                && RESOURCE_NAMESPACE.matcher(value.substring(0,
                separator)).matches()
                && RESOURCE_PATH.matcher(value.substring(separator + 1))
                .matches();
    }

    private static BazaarProductDefinition parseProduct(
            JsonObject object,
            String sourceName,
            BazaarConfig.ProductDefaults defaults,
            int defaultMaximumQuantity,
            Predicate<String> itemExists
    ) {
        requireKnownFields(object, PRODUCT_FIELDS, "Product entry");
        String productId = string(object, "id", null, false);
        long version = longInteger(object, "version", 0L, false);
        String registryId = normalizedResourceId(
                string(object, "item", null, false), "Product item");
        BazaarProductStatus status = parseStatus(object);
        if (status != BazaarProductStatus.RETIRED
                && !itemExists.test(registryId)) {
            throw new IllegalArgumentException(
                    "Product item is not registered");
        }
        String category = string(object, "category", "misc", true)
                .strip().toLowerCase(Locale.ROOT);
        String displayName = string(object, "displayName", "", true);
        String iconRegistryId = normalizedResourceId(string(object,
                "iconItem", registryId, true), "Product icon item");
        if (status != BazaarProductStatus.RETIRED
                && !itemExists.test(iconRegistryId)) {
            throw new IllegalArgumentException(
                    "Product icon item is not registered");
        }
        BazaarProductIdentityPolicy identityPolicy =
                BazaarProductIdentityPolicy.parse(string(object,
                        "identityPolicy", "commodity", true));
        String exactIdentity = parseExactIdentity(object, identityPolicy);
        int lotSize = integer(object, "lotSize", defaults.lotSize(), true);
        long priceTick = longInteger(object, "priceTickMinor",
                defaults.priceTickMinor(), true);
        long minimumPrice = longInteger(object, "minimumPriceMinor",
                priceTick, true);
        long maximumPrice = longInteger(object, "maximumPriceMinor",
                Long.MAX_VALUE, true);
        int maximumQuantity = integer(object, "maximumQuantity",
                defaultMaximumQuantity, true);
        List<String> dimensions = dimensions(object);
        BazaarItemRestrictions restrictions = restrictions(object);
        BazaarProduct product = new BazaarProduct(productId, version,
                registryId, exactIdentity, category, lotSize, priceTick,
                minimumPrice, maximumPrice, maximumQuantity, status);
        return new BazaarProductDefinition(product, displayName,
                iconRegistryId, identityPolicy, dimensions, restrictions,
                sourceName);
    }

    private static BazaarProductStatus parseStatus(JsonObject object) {
        boolean hasStatus = object.has("status");
        boolean hasEnabled = object.has("enabled");
        BazaarProductStatus status = BazaarProductStatus.ACTIVE;
        if (hasStatus) {
            String value = string(object, "status", null, false);
            try {
                status = BazaarProductStatus.valueOf(value.strip()
                        .toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Product status is invalid");
            }
        }
        if (hasEnabled) {
            boolean enabled = bool(object, "enabled", true);
            BazaarProductStatus enabledStatus = enabled
                    ? BazaarProductStatus.ACTIVE
                    : BazaarProductStatus.RETIRED;
            if (hasStatus && status != enabledStatus) {
                throw new IllegalArgumentException(
                        "Product status and enabled fields conflict");
            }
            status = enabledStatus;
        }
        return status;
    }

    private static String parseExactIdentity(JsonObject object,
                                             BazaarProductIdentityPolicy policy) {
        String exactNbt = string(object, "exactNbt", "", true).strip();
        if (policy == BazaarProductIdentityPolicy.COMMODITY) {
            if (!exactNbt.isEmpty()) {
                throw new IllegalArgumentException(
                        "Commodity products cannot define exact NBT");
            }
            return "";
        }
        if (exactNbt.isEmpty()) {
            throw new IllegalArgumentException(
                    "Exact products require exact NBT");
        }
        try {
            String canonical = TagParser.parseTag(exactNbt).toString();
            if (canonical.length() > 256) {
                throw new IllegalArgumentException(
                        "Exact product NBT exceeds its size limit");
            }
            return canonical;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Exact product NBT is invalid", exception);
        }
    }

    private static List<String> dimensions(JsonObject object) {
        if (!object.has("allowedDimensions")) {
            return List.of();
        }
        JsonElement element = object.get("allowedDimensions");
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException(
                    "Allowed dimensions must be an array");
        }
        List<String> dimensions = new ArrayList<>();
        for (JsonElement entry : element.getAsJsonArray()) {
            if (!entry.isJsonPrimitive()
                    || !entry.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(
                        "Allowed dimension must be a string");
            }
            String dimension = entry.getAsString().strip()
                    .toLowerCase(Locale.ROOT);
            if (!validResourceId(dimension)) {
                throw new IllegalArgumentException(
                        "Allowed dimension is invalid");
            }
            dimensions.add(dimension);
        }
        return dimensions;
    }

    private static BazaarItemRestrictions restrictions(JsonObject object) {
        if (!object.has("restrictions")) {
            return BazaarItemRestrictions.safeDefaults();
        }
        JsonElement element = object.get("restrictions");
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(
                    "Product restrictions must be an object");
        }
        JsonObject restrictions = element.getAsJsonObject();
        requireKnownFields(restrictions, RESTRICTION_FIELDS,
                "Product restrictions");
        return new BazaarItemRestrictions(
                bool(restrictions, "allowDamaged", false),
                bool(restrictions, "allowNamed", false),
                bool(restrictions, "allowEnchanted", false),
                bool(restrictions, "allowContainers", false),
                bool(restrictions, "allowCapabilities", false));
    }

    private static void validateCatalog(
            List<BazaarProductDefinition> definitions) {
        Map<BazaarProductVersionKey, BazaarProductDefinition> versions =
                new HashMap<>();
        Map<String, BazaarProductDefinition> current = new HashMap<>();
        Map<String, BazaarProductDefinition> activeIdentity = new HashMap<>();
        for (BazaarProductDefinition definition : definitions) {
            BazaarProductDefinition duplicate = versions.putIfAbsent(
                    definition.key(), definition);
            if (duplicate != null) {
                throw new IllegalArgumentException(
                        "Duplicate Bazaar product version in "
                                + duplicate.sourceName() + " and "
                                + definition.sourceName());
            }
            BazaarProductDefinition prior = current.get(
                    definition.product().productId());
            if (prior == null || definition.product().version()
                    > prior.product().version()) {
                current.put(definition.product().productId(), definition);
            }
        }
        for (BazaarProductDefinition definition : definitions) {
            BazaarProductDefinition latest = current.get(
                    definition.product().productId());
            if (definition != latest
                    && definition.product().status()
                    != BazaarProductStatus.RETIRED) {
                throw new IllegalArgumentException(
                        "Historical Bazaar product versions must be retired");
            }
        }
        for (BazaarProductDefinition definition : current.values()) {
            if (definition.product().status()
                    == BazaarProductStatus.RETIRED) {
                continue;
            }
            BazaarProductDefinition duplicate = activeIdentity.putIfAbsent(
                    definition.identityKey(), definition);
            if (duplicate != null && !duplicate.product().productId()
                    .equals(definition.product().productId())) {
                throw new IllegalArgumentException(
                        "Active Bazaar products have conflicting identities");
            }
        }
    }

    private static void requireKnownFields(JsonObject object,
                                           Set<String> known,
                                           String context) {
        for (String key : object.keySet()) {
            if (!known.contains(key)) {
                throw new IllegalArgumentException(context
                        + " contains unknown field " + key);
            }
        }
    }

    private static String string(JsonObject object, String key,
                                 String fallback, boolean optional) {
        if (!object.has(key)) {
            if (!optional) {
                throw new IllegalArgumentException(
                        "Required product field is missing, " + key);
            }
            return fallback;
        }
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(
                    "Product field must be a string, " + key);
        }
        return element.getAsString();
    }

    private static int integer(JsonObject object, String key,
                               int fallback, boolean optional) {
        long value = longInteger(object, key, fallback, optional);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Product integer field is outside its range, " + key);
        }
        return (int) value;
    }

    private static long longInteger(JsonObject object, String key,
                                    long fallback, boolean optional) {
        if (!object.has(key)) {
            if (!optional) {
                throw new IllegalArgumentException(
                        "Required product field is missing, " + key);
            }
            return fallback;
        }
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(
                    "Product field must be an integer, " + key);
        }
        try {
            return new BigDecimal(element.getAsString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Product integer field is invalid, " + key);
        }
    }

    private static boolean bool(JsonObject object, String key,
                                boolean fallback) {
        if (!object.has(key)) {
            return fallback;
        }
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(
                    "Product field must be a boolean, " + key);
        }
        return element.getAsBoolean();
    }

    private static String normalizedResourceId(String value,
                                               String field) {
        String normalized = Objects.requireNonNull(value, field).strip()
                .toLowerCase(Locale.ROOT);
        if (!validResourceId(normalized)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }

    private static IllegalArgumentException rejected(Path file,
                                                      String reason) {
        return new IllegalArgumentException(file.getFileName() + ". "
                + reason);
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "Product definition is invalid"
                : message;
    }

    private static String decodeUtf8(byte[] encoded, Path file) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded)).toString();
        } catch (CharacterCodingException exception) {
            throw rejected(file, "Product file is not valid UTF8");
        }
    }

    private static void rejectDuplicateFields(String json) {
        try {
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.setLenient(false);
            scanValue(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException(
                        "Product file contains trailing content");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "Product file JSON is invalid", exception);
        }
    }

    private static void scanValue(JsonReader reader) throws IOException {
        JsonToken token = reader.peek();
        switch (token) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                Set<String> fields = new HashSet<>();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (!fields.add(name)) {
                        throw new IllegalArgumentException(
                                "Product object contains duplicate field "
                                        + name);
                    }
                    scanValue(reader);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) {
                    scanValue(reader);
                }
                reader.endArray();
            }
            case STRING, NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new IllegalArgumentException(
                    "Product file JSON value is invalid");
        }
    }
}
