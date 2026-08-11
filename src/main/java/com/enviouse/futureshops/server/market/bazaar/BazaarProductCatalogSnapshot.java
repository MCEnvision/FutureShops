package com.enviouse.futureshops.server.market.bazaar;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record BazaarProductCatalogSnapshot(
        List<BazaarProductDefinition> definitions,
        String fingerprint
) {
    public BazaarProductCatalogSnapshot {
        definitions = Objects.requireNonNull(definitions, "definitions")
                .stream().sorted(Comparator.comparing(
                        (BazaarProductDefinition value) ->
                                value.product().productId())
                        .thenComparingLong(value ->
                                value.product().version())).toList();
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        if (!fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "Bazaar product catalog fingerprint is invalid");
        }
    }

    public static BazaarProductCatalogSnapshot of(
            List<BazaarProductDefinition> definitions) {
        List<BazaarProductDefinition> ordered = Objects.requireNonNull(
                definitions, "definitions").stream().sorted(
                Comparator.comparing((BazaarProductDefinition value) ->
                                value.product().productId())
                        .thenComparingLong(value ->
                                value.product().version())).toList();
        return new BazaarProductCatalogSnapshot(ordered,
                fingerprint(ordered));
    }

    public static BazaarProductCatalogSnapshot empty() {
        return of(List.of());
    }

    public Map<String, BazaarProductDefinition> currentDefinitions() {
        Map<String, BazaarProductDefinition> current = new LinkedHashMap<>();
        for (BazaarProductDefinition definition : definitions) {
            current.put(definition.product().productId(), definition);
        }
        return Map.copyOf(current);
    }

    public Optional<BazaarProductDefinition> definition(
            String productId,
            long version) {
        return definitions.stream().filter(value ->
                value.product().productId().equals(productId)
                        && value.product().version() == version)
                .findFirst();
    }

    public Optional<BazaarProductDefinition> currentDefinition(
            String productId) {
        return Optional.ofNullable(currentDefinitions().get(productId));
    }

    private static String fingerprint(
            List<BazaarProductDefinition> definitions) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(1);
            output.writeInt(definitions.size());
            for (BazaarProductDefinition definition : definitions) {
                BazaarProduct product = definition.product();
                write(output, product.productId());
                output.writeLong(product.version());
                write(output, product.registryId());
                write(output, product.exactIdentity());
                write(output, product.categoryId());
                output.writeInt(product.lotSize());
                output.writeLong(product.priceTickMinor());
                output.writeLong(product.minimumPriceMinor());
                output.writeLong(product.maximumPriceMinor());
                output.writeInt(product.maximumQuantity());
                output.writeInt(product.status().wireCode());
                write(output, definition.displayName());
                write(output, definition.iconRegistryId());
                output.writeInt(definition.identityPolicy().ordinal());
                output.writeInt(definition.allowedDimensions().size());
                for (String dimension : definition.allowedDimensions()) {
                    write(output, dimension);
                }
                BazaarItemRestrictions restrictions = definition.restrictions();
                output.writeBoolean(restrictions.allowDamaged());
                output.writeBoolean(restrictions.allowNamed());
                output.writeBoolean(restrictions.allowEnchanted());
                output.writeBoolean(restrictions.allowContainers());
                output.writeBoolean(restrictions.allowCapabilities());
            }
            output.flush();
            return HexFormat.of().formatHex(MessageDigest.getInstance(
                    "SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Unable to fingerprint Bazaar product catalog", exception);
        }
    }

    private static void write(DataOutputStream output, String value)
            throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }
}
