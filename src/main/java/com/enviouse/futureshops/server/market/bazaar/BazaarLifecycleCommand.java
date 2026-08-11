package com.enviouse.futureshops.server.market.bazaar;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public record BazaarLifecycleCommand(
        UUID mutationId,
        BazaarLifecycleType type,
        Optional<BazaarRuleSnapshot> rules,
        Optional<BazaarProduct> product,
        String productId,
        Optional<BazaarProductStatus> productStatus,
        long referencePriceMinor
) {
    private static final UUID ZERO = new UUID(0L, 0L);
    private static final Pattern PRODUCT_ID = Pattern.compile(
            "[a-z0-9_.]{1,96}");

    public BazaarLifecycleCommand {
        Objects.requireNonNull(mutationId, "mutationId");
        Objects.requireNonNull(type, "type");
        rules = Objects.requireNonNull(rules, "rules");
        product = Objects.requireNonNull(product, "product");
        productId = Objects.requireNonNull(productId, "productId")
                .strip().toLowerCase(Locale.ROOT);
        productStatus = Objects.requireNonNull(productStatus,
                "productStatus");
        if (ZERO.equals(mutationId)
                || !validShape(type, rules, product, productId,
                productStatus, referencePriceMinor)) {
            throw new IllegalArgumentException(
                    "Bazaar lifecycle command is invalid");
        }
    }

    public static BazaarLifecycleCommand setEffectiveRules(
            UUID mutationId, BazaarRuleSnapshot rules) {
        return new BazaarLifecycleCommand(mutationId,
                BazaarLifecycleType.SET_EFFECTIVE_RULES,
                Optional.of(rules), Optional.empty(), "",
                Optional.empty(), 0L);
    }

    public static BazaarLifecycleCommand registerProduct(
            UUID mutationId, BazaarProduct product) {
        return new BazaarLifecycleCommand(mutationId,
                BazaarLifecycleType.REGISTER_PRODUCT,
                Optional.empty(), Optional.of(product), "",
                Optional.empty(), 0L);
    }

    public static BazaarLifecycleCommand setProductStatus(
            UUID mutationId, String productId,
            BazaarProductStatus status) {
        return new BazaarLifecycleCommand(mutationId,
                BazaarLifecycleType.SET_PRODUCT_STATUS,
                Optional.empty(), Optional.empty(), productId,
                Optional.of(status), 0L);
    }

    public static BazaarLifecycleCommand setReferencePrice(
            UUID mutationId, String productId, long priceMinor) {
        return new BazaarLifecycleCommand(mutationId,
                BazaarLifecycleType.SET_REFERENCE_PRICE,
                Optional.empty(), Optional.empty(), productId,
                Optional.empty(), priceMinor);
    }

    void apply(BazaarOrderBook book) {
        Objects.requireNonNull(book, "book");
        switch (type) {
            case SET_EFFECTIVE_RULES -> book.setEffectiveRules(
                    rules.orElseThrow());
            case REGISTER_PRODUCT -> book.registerProduct(
                    product.orElseThrow());
            case SET_PRODUCT_STATUS -> book.setProductStatus(productId,
                    productStatus.orElseThrow());
            case SET_REFERENCE_PRICE -> book.setReferencePrice(productId,
                    referencePriceMinor);
        }
    }

    private static boolean validShape(
            BazaarLifecycleType type,
            Optional<BazaarRuleSnapshot> rules,
            Optional<BazaarProduct> product,
            String productId,
            Optional<BazaarProductStatus> status,
            long referencePrice) {
        return switch (type) {
            case SET_EFFECTIVE_RULES -> rules.isPresent()
                    && product.isEmpty() && productId.isEmpty()
                    && status.isEmpty() && referencePrice == 0L;
            case REGISTER_PRODUCT -> rules.isEmpty()
                    && product.isPresent() && productId.isEmpty()
                    && status.isEmpty() && referencePrice == 0L;
            case SET_PRODUCT_STATUS -> rules.isEmpty()
                    && product.isEmpty()
                    && PRODUCT_ID.matcher(productId).matches()
                    && status.isPresent() && referencePrice == 0L;
            case SET_REFERENCE_PRICE -> rules.isEmpty()
                    && product.isEmpty()
                    && PRODUCT_ID.matcher(productId).matches()
                    && status.isEmpty() && referencePrice > 0L;
        };
    }
}
