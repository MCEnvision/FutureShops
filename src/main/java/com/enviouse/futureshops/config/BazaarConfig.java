package com.enviouse.futureshops.config;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.Futureshops;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Mod.EventBusSubscriber(modid = Futureshops.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BazaarConfig {
    public static final String FILE_NAME = "futureshops-bazaar.toml";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> DISABLE_MODES = Set.of("freeze", "drain", "cancel_and_refund");
    private static final Set<String> SELF_TRADE_POLICIES = Set.of("cancel_taker", "cancel_maker", "skip_self");
    private static final Set<String> EXECUTION_PRICE_POLICIES = Set.of("maker", "taker", "midpoint");
    private static final Set<String> PAYMENT_SOURCES = Set.of("wallet", "physical", "prompt");
    private static final Set<String> PHYSICAL_REMAINDER_POLICIES = Set.of("wallet_claim", "original_source");
    private static final Set<String> FEE_DESTINATIONS = Set.of("void", "treasury");
    private static final Set<String> CATALOG_CONTROLS = Set.of("admin", "players");
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.ConfigValue<String> BAZAAR_CONTROL = BUILDER
        .comment("Controls who can add Bazaar products. Admin uses the product JSON catalog. Players lets players add held items without a catalog size limit.")
        .define("bazaar_control", "admin",
            value -> ConfigValidation.isOption(value, CATALOG_CONTROLS));

    private static final ForgeConfigSpec.ConfigValue<String> BRANDING_DISPLAY_NAME = BUILDER
        .comment("Display name used by the Bazaar interface.")
        .define("branding.display_name", "Bazaar",
            value -> value instanceof String text && !text.isBlank() && text.length() <= 64);

    private static final ForgeConfigSpec.ConfigValue<String> BRANDING_ACCENT_COLOR = BUILDER
        .comment("Bazaar accent color as RRGGBB or AARRGGBB hexadecimal.")
        .define("branding.accent_color", "#9184D9", ConfigValidation::isHexColor);

    private static final ForgeConfigSpec.ConfigValue<String> LIFECYCLE_DISABLE_MODE = BUILDER
        .comment("Behavior when the module is disabled. Allowed values are freeze, drain, and cancel_and_refund.")
        .define("lifecycle.disable_mode", "freeze", value -> ConfigValidation.isOption(value, DISABLE_MODES));

    private static final ForgeConfigSpec.BooleanValue ORDERS_ALLOW_BUY = BUILDER
        .comment("Allow players to create Bazaar buy orders.")
        .define("orders.allow_buy", true);

    private static final ForgeConfigSpec.BooleanValue ORDERS_ALLOW_SELL = BUILDER
        .comment("Allow players to create Bazaar sell orders.")
        .define("orders.allow_sell", true);

    private static final ForgeConfigSpec.BooleanValue ORDERS_ALLOW_LIMIT = BUILDER
        .comment("Allow persistent limit orders.")
        .define("orders.allow_limit", true);

    private static final ForgeConfigSpec.BooleanValue ORDERS_ALLOW_INSTANT = BUILDER
        .comment("Allow bounded instant buy and instant sell orders.")
        .define("orders.allow_instant", true);

    private static final ForgeConfigSpec.BooleanValue ORDERS_ALLOW_IMMEDIATE_OR_CANCEL = BUILDER
        .comment("Allow Immediate Or Cancel time in force.")
        .define("orders.allow_immediate_or_cancel", false);

    private static final ForgeConfigSpec.BooleanValue ORDERS_ALLOW_FILL_OR_KILL = BUILDER
        .comment("Allow Fill Or Kill time in force.")
        .define("orders.allow_fill_or_kill", false);

    private static final ForgeConfigSpec.IntValue ORDERS_MAXIMUM_OPEN_PER_PLAYER = BUILDER
        .comment("Maximum open Bazaar orders owned by one player.")
        .defineInRange("orders.maximum_open_per_player", 32, 1, 100000);

    private static final ForgeConfigSpec.IntValue ORDERS_MAXIMUM_OPEN_PER_PRODUCT_PER_PLAYER = BUILDER
        .comment("Maximum open orders one player may own for one product.")
        .defineInRange("orders.maximum_open_per_product_per_player", 8, 1, 10000);

    private static final ForgeConfigSpec.IntValue ORDERS_MAXIMUM_QUANTITY = BUILDER
        .comment("Maximum product units accepted by one order.")
        .defineInRange("orders.maximum_quantity", 1000000, 1, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.LongValue ORDERS_MAXIMUM_NOTIONAL_MINOR = BUILDER
        .comment("Maximum price multiplied by quantity for one order in minor units.")
        .defineInRange("orders.maximum_notional_minor", 100000000000L, 1L, Long.MAX_VALUE);

    private static final ForgeConfigSpec.LongValue ORDERS_MAXIMUM_ESCROWED_VALUE_PER_PLAYER_MINOR = BUILDER
        .comment("Maximum Bazaar money value held for one player in minor units.")
        .defineInRange("orders.maximum_escrowed_value_per_player_minor", 500000000000L, 1L, Long.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue ORDERS_DEFAULT_EXPIRATION_HOURS = BUILDER
        .comment("Default expiration for persistent orders in hours.")
        .defineInRange("orders.default_expiration_hours", 168, 1, 87600);

    private static final ForgeConfigSpec.IntValue ORDERS_MAXIMUM_EXPIRATION_HOURS = BUILDER
        .comment("Maximum expiration for persistent orders in hours.")
        .defineInRange("orders.maximum_expiration_hours", 720, 1, 87600);

    private static final ForgeConfigSpec.IntValue PRODUCTS_DEFAULT_LOT_SIZE = BUILDER
        .comment("Default product quantity step used when a product definition does not override it.")
        .defineInRange("products.default_lot_size", 1, 1, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.LongValue PRODUCTS_DEFAULT_PRICE_TICK_MINOR = BUILDER
        .comment("Default price step in minor units used when a product definition does not override it.")
        .defineInRange("products.default_price_tick_minor", 1L, 1L, Long.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue MATCHING_MAXIMUM_FILLS_PER_TICK = BUILDER
        .comment("Maximum Bazaar fills committed during one server tick.")
        .defineInRange("matching.maximum_fills_per_tick", 128, 1, 100000);

    private static final ForgeConfigSpec.ConfigValue<String> MATCHING_EXECUTION_PRICE_POLICY = BUILDER
        .comment("Crossed order execution price. Allowed values are maker, taker, and midpoint.")
        .define("matching.execution_price_policy", "maker",
            value -> ConfigValidation.isOption(value, EXECUTION_PRICE_POLICIES));

    private static final ForgeConfigSpec.ConfigValue<String> MATCHING_SELF_TRADE_POLICY = BUILDER
        .comment("Self trade prevention. Allowed values are cancel_taker, cancel_maker, and skip_self.")
        .define("matching.self_trade_policy", "cancel_taker",
            value -> ConfigValidation.isOption(value, SELF_TRADE_POLICIES));

    private static final ForgeConfigSpec.IntValue MATCHING_MAXIMUM_ORDER_REQUESTS_PER_SECOND = BUILDER
        .comment("Maximum order mutation requests accepted from one player each second.")
        .defineInRange("matching.maximum_order_requests_per_second", 8, 1, 1000);

    private static final ForgeConfigSpec.IntValue FEES_MAKER_BASIS_POINTS = BUILDER
        .comment("Fee charged to a resting maker order in basis points.")
        .defineInRange("fees.maker_basis_points", 10, 0, 10000);

    private static final ForgeConfigSpec.IntValue FEES_TAKER_BASIS_POINTS = BUILDER
        .comment("Fee charged to the incoming taker order in basis points.")
        .defineInRange("fees.taker_basis_points", 25, 0, 10000);

    private static final ForgeConfigSpec.ConfigValue<String> FEES_DESTINATION = BUILDER
        .comment("Destination for Bazaar fees. Allowed values are void and treasury.")
        .define("fees.destination", "void", value -> ConfigValidation.isOption(value, FEE_DESTINATIONS));

    private static final ForgeConfigSpec.IntValue MARKET_MAXIMUM_SLIPPAGE_BASIS_POINTS = BUILDER
        .comment("Hard maximum slippage accepted by an instant order in basis points.")
        .defineInRange("market.maximum_slippage_basis_points", 1000, 0, 10000);

    private static final ForgeConfigSpec.BooleanValue MARKET_CIRCUIT_BREAKER = BUILDER
        .comment("Enable product price bands and circuit breaker halts.")
        .define("market.circuit_breaker", true);

    private static final ForgeConfigSpec.IntValue MARKET_PRICE_BAND_BASIS_POINTS = BUILDER
        .comment("Maximum deviation from the reference price before a product halts in basis points.")
        .defineInRange("market.price_band_basis_points", 5000, 1, 100000);

    private static final ForgeConfigSpec.IntValue MARKET_CIRCUIT_BREAKER_COOLDOWN_SECONDS = BUILDER
        .comment("Automatic product halt duration after a circuit breaker event in seconds.")
        .defineInRange("market.circuit_breaker_cooldown_seconds", 300, 1, 604800);

    private static final ForgeConfigSpec.IntValue HISTORY_RETENTION_DAYS = BUILDER
        .comment("Retention period for aggregated Bazaar price history in days.")
        .defineInRange("history.retention_days", 90, 1, 3650);

    private static final ForgeConfigSpec.IntValue HISTORY_BUCKET_MINUTES = BUILDER
        .comment("Aggregation bucket length for Bazaar price history in minutes.")
        .defineInRange("history.bucket_minutes", 15, 1, 10080);

    private static final ForgeConfigSpec.IntValue HISTORY_MAXIMUM_CHART_POINTS = BUILDER
        .comment("Maximum chart points returned in one Bazaar response.")
        .defineInRange("history.maximum_chart_points", 256, 16, 4096);

    private static final ForgeConfigSpec.BooleanValue PAYMENT_ALLOW_WALLET = BUILDER
        .comment("Allow wallet funds for Bazaar buy orders.")
        .define("payment.allow_wallet", true);

    private static final ForgeConfigSpec.BooleanValue PAYMENT_ALLOW_PHYSICAL = BUILDER
        .comment(Config.FOREIGN_CURRENCY_WARNING, "Allow inventory currency to enter escrow for Bazaar buy orders.")
        .define("payment.allow_physical", true);

    private static final ForgeConfigSpec.ConfigValue<String> PAYMENT_DEFAULT_SOURCE = BUILDER
        .comment("Default source for Bazaar payments. Allowed values are wallet, physical, and prompt.")
        .define("payment.default_source", "wallet", value -> ConfigValidation.isOption(value, PAYMENT_SOURCES));

    private static final ForgeConfigSpec.ConfigValue<String> PAYMENT_PHYSICAL_REMAINDER_POLICY = BUILDER
        .comment("Destination for unused physical order value. Allowed values are wallet_claim and original_source.")
        .define("payment.physical_remainder_policy", "wallet_claim",
            value -> ConfigValidation.isOption(value, PHYSICAL_REMAINDER_POLICIES));

    private static final ForgeConfigSpec.BooleanValue NOTIFICATIONS_FILLED = BUILDER
        .comment("Notify players when an order fills.")
        .define("notifications.filled", true);

    private static final ForgeConfigSpec.BooleanValue NOTIFICATIONS_PARTIAL_FILL = BUILDER
        .comment("Notify players when an order partially fills.")
        .define("notifications.partial_fill", true);

    private static final ForgeConfigSpec.BooleanValue NOTIFICATIONS_EXPIRED = BUILDER
        .comment("Notify players when an order expires.")
        .define("notifications.expired", true);

    private static final ForgeConfigSpec.IntValue BROWSE_PAGE_SIZE = BUILDER
        .comment("Maximum product cards returned on one browse page.")
        .defineInRange("browse.page_size", 28, 4, 100);

    private static final ForgeConfigSpec.IntValue BROWSE_ORDER_BOOK_DEPTH = BUILDER
        .comment("Maximum price levels returned for each side of an order book.")
        .defineInRange("browse.order_book_depth", 20, 1, 100);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private static volatile Settings settings = Settings.defaults();

    private BazaarConfig() {
    }

    public static Settings settings() {
        return settings;
    }

    public static CatalogControl catalogControl() {
        return CatalogControl.fromWire(BAZAAR_CONTROL.get());
    }

    @SubscribeEvent
    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        try {
            settings = readSettings();
        } catch (IllegalArgumentException exception) {
            LOGGER.error("Rejected FutureShops Bazaar configuration. {}", exception.getMessage());
        }
    }

    private static Settings readSettings() {
        return new Settings(
            new Branding(BRANDING_DISPLAY_NAME.get(), BRANDING_ACCENT_COLOR.get()),
            new Lifecycle(LIFECYCLE_DISABLE_MODE.get()),
            new OrderRules(
                ORDERS_ALLOW_BUY.get(), ORDERS_ALLOW_SELL.get(), ORDERS_ALLOW_LIMIT.get(),
                ORDERS_ALLOW_INSTANT.get(), ORDERS_ALLOW_IMMEDIATE_OR_CANCEL.get(),
                ORDERS_ALLOW_FILL_OR_KILL.get(), ORDERS_MAXIMUM_OPEN_PER_PLAYER.get(),
                ORDERS_MAXIMUM_OPEN_PER_PRODUCT_PER_PLAYER.get(), ORDERS_MAXIMUM_QUANTITY.get(),
                ORDERS_MAXIMUM_NOTIONAL_MINOR.get(), ORDERS_MAXIMUM_ESCROWED_VALUE_PER_PLAYER_MINOR.get(),
                ORDERS_DEFAULT_EXPIRATION_HOURS.get(), ORDERS_MAXIMUM_EXPIRATION_HOURS.get()),
            new ProductDefaults(PRODUCTS_DEFAULT_LOT_SIZE.get(), PRODUCTS_DEFAULT_PRICE_TICK_MINOR.get()),
            new MatchingRules(
                MATCHING_MAXIMUM_FILLS_PER_TICK.get(), MATCHING_EXECUTION_PRICE_POLICY.get(),
                MATCHING_SELF_TRADE_POLICY.get(), MATCHING_MAXIMUM_ORDER_REQUESTS_PER_SECOND.get()),
            new FeeRules(FEES_MAKER_BASIS_POINTS.get(), FEES_TAKER_BASIS_POINTS.get(), FEES_DESTINATION.get()),
            new MarketSafetyRules(
                MARKET_MAXIMUM_SLIPPAGE_BASIS_POINTS.get(), MARKET_CIRCUIT_BREAKER.get(),
                MARKET_PRICE_BAND_BASIS_POINTS.get(), MARKET_CIRCUIT_BREAKER_COOLDOWN_SECONDS.get()),
            new HistoryRules(
                HISTORY_RETENTION_DAYS.get(), HISTORY_BUCKET_MINUTES.get(), HISTORY_MAXIMUM_CHART_POINTS.get()),
            new PaymentRules(
                PAYMENT_ALLOW_WALLET.get(), PAYMENT_ALLOW_PHYSICAL.get(), PAYMENT_DEFAULT_SOURCE.get(),
                PAYMENT_PHYSICAL_REMAINDER_POLICY.get()),
            new NotificationRules(
                NOTIFICATIONS_FILLED.get(), NOTIFICATIONS_PARTIAL_FILL.get(), NOTIFICATIONS_EXPIRED.get()),
            new BrowseRules(BROWSE_PAGE_SIZE.get(), BROWSE_ORDER_BOOK_DEPTH.get())
        );
    }

    public record Settings(
        Branding branding,
        Lifecycle lifecycle,
        OrderRules orders,
        ProductDefaults productDefaults,
        MatchingRules matching,
        FeeRules fees,
        MarketSafetyRules marketSafety,
        HistoryRules history,
        PaymentRules payment,
        NotificationRules notifications,
        BrowseRules browse
    ) {
        public Settings {
            if (branding == null || lifecycle == null || orders == null || productDefaults == null
                || matching == null || fees == null || marketSafety == null || history == null
                || payment == null || notifications == null || browse == null) {
                throw new IllegalArgumentException("Bazaar settings groups are required.");
            }
        }

        public static Settings defaults() {
            return new Settings(
                new Branding("Bazaar", "#48B978"),
                new Lifecycle("freeze"),
                new OrderRules(true, true, true, true, false, false, 32, 8, 1000000,
                    100000000000L, 500000000000L, 168, 720),
                new ProductDefaults(1, 1L),
                new MatchingRules(128, "maker", "cancel_taker", 8),
                new FeeRules(10, 25, "void"),
                new MarketSafetyRules(1000, true, 5000, 300),
                new HistoryRules(90, 15, 256),
                new PaymentRules(true, true, "wallet", "wallet_claim"),
                new NotificationRules(true, true, true),
                new BrowseRules(28, 20)
            );
        }
    }

    public record Branding(String displayName, String accentColor) {
        public Branding {
            ConfigValidation.require(displayName != null && !displayName.isBlank() && displayName.length() <= 64,
                "Bazaar display name must contain between one and sixty four characters.");
            displayName = displayName.strip();
            accentColor = ConfigValidation.requireHexColor(accentColor, "Bazaar accent color");
        }
    }

    public record Lifecycle(String disableMode) {
        public Lifecycle {
            disableMode = ConfigValidation.requireOption(disableMode, DISABLE_MODES, "Bazaar disable mode");
        }
    }

    public record OrderRules(
        boolean allowBuy,
        boolean allowSell,
        boolean allowLimit,
        boolean allowInstant,
        boolean allowImmediateOrCancel,
        boolean allowFillOrKill,
        int maximumOpenPerPlayer,
        int maximumOpenPerProductPerPlayer,
        int maximumQuantity,
        long maximumNotionalMinor,
        long maximumEscrowedValuePerPlayerMinor,
        int defaultExpirationHours,
        int maximumExpirationHours
    ) {
        public OrderRules {
            ConfigValidation.require(allowBuy || allowSell, "Bazaar must allow buy orders or sell orders.");
            ConfigValidation.require(allowLimit || allowInstant, "Bazaar must allow limit orders or instant orders.");
            ConfigValidation.require(maximumOpenPerPlayer > 0, "Maximum open Bazaar orders must be positive.");
            ConfigValidation.require(maximumOpenPerProductPerPlayer > 0,
                "Maximum open Bazaar orders per product must be positive.");
            ConfigValidation.require(maximumOpenPerProductPerPlayer <= maximumOpenPerPlayer,
                "Per product Bazaar order limit must not exceed the total player order limit.");
            ConfigValidation.require(maximumQuantity > 0, "Maximum Bazaar order quantity must be positive.");
            ConfigValidation.require(maximumNotionalMinor > 0, "Maximum Bazaar order notional must be positive.");
            ConfigValidation.require(maximumEscrowedValuePerPlayerMinor >= maximumNotionalMinor,
                "Maximum player escrow value must permit at least one maximum value order.");
            ConfigValidation.require(defaultExpirationHours > 0, "Default Bazaar expiration must be positive.");
            ConfigValidation.require(maximumExpirationHours >= defaultExpirationHours,
                "Maximum Bazaar expiration must not be less than the default expiration.");
        }
    }

    public record ProductDefaults(int lotSize, long priceTickMinor) {
        public ProductDefaults {
            ConfigValidation.require(lotSize > 0, "Default Bazaar lot size must be positive.");
            ConfigValidation.require(priceTickMinor > 0, "Default Bazaar price tick must be positive.");
        }
    }

    public record MatchingRules(
        int maximumFillsPerTick,
        String executionPricePolicy,
        String selfTradePolicy,
        int maximumOrderRequestsPerSecond
    ) {
        public MatchingRules {
            ConfigValidation.require(maximumFillsPerTick > 0, "Maximum Bazaar fills per tick must be positive.");
            executionPricePolicy = ConfigValidation.requireOption(
                executionPricePolicy, EXECUTION_PRICE_POLICIES, "Bazaar execution price policy");
            selfTradePolicy = ConfigValidation.requireOption(
                selfTradePolicy, SELF_TRADE_POLICIES, "Bazaar self trade policy");
            ConfigValidation.require(maximumOrderRequestsPerSecond > 0,
                "Maximum Bazaar order requests per second must be positive.");
        }
    }

    public record FeeRules(int makerBasisPoints, int takerBasisPoints, String destination) {
        public FeeRules {
            ConfigValidation.require(makerBasisPoints >= 0 && makerBasisPoints <= 10000,
                "Bazaar maker fee must be between zero and ten thousand basis points.");
            ConfigValidation.require(takerBasisPoints >= 0 && takerBasisPoints <= 10000,
                "Bazaar taker fee must be between zero and ten thousand basis points.");
            destination = ConfigValidation.requireOption(destination, FEE_DESTINATIONS, "Bazaar fee destination");
        }
    }

    public record MarketSafetyRules(
        int maximumSlippageBasisPoints,
        boolean circuitBreaker,
        int priceBandBasisPoints,
        int circuitBreakerCooldownSeconds
    ) {
        public MarketSafetyRules {
            ConfigValidation.require(maximumSlippageBasisPoints >= 0 && maximumSlippageBasisPoints <= 10000,
                "Maximum Bazaar slippage must be between zero and ten thousand basis points.");
            ConfigValidation.require(priceBandBasisPoints > 0, "Bazaar price band must be positive.");
            ConfigValidation.require(circuitBreakerCooldownSeconds > 0,
                "Bazaar circuit breaker cooldown must be positive.");
        }
    }

    public record HistoryRules(int retentionDays, int bucketMinutes, int maximumChartPoints) {
        public HistoryRules {
            ConfigValidation.require(retentionDays > 0, "Bazaar history retention must be positive.");
            ConfigValidation.require(bucketMinutes > 0, "Bazaar history bucket length must be positive.");
            ConfigValidation.require(maximumChartPoints > 0, "Maximum Bazaar chart points must be positive.");
        }
    }

    public record PaymentRules(
        boolean allowWallet,
        boolean allowPhysical,
        String defaultSource,
        String physicalRemainderPolicy
    ) {
        public PaymentRules {
            ConfigValidation.require(allowWallet || allowPhysical, "Bazaar must allow at least one payment source.");
            defaultSource = ConfigValidation.requireOption(defaultSource, PAYMENT_SOURCES,
                "Bazaar default payment source");
            ConfigValidation.require(!"wallet".equals(defaultSource) || allowWallet,
                "Wallet cannot be the default when wallet payment is disabled.");
            ConfigValidation.require(!"physical".equals(defaultSource) || allowPhysical,
                "Physical cannot be the default when physical payment is disabled.");
            physicalRemainderPolicy = ConfigValidation.requireOption(
                physicalRemainderPolicy, PHYSICAL_REMAINDER_POLICIES, "Bazaar physical remainder policy");
        }
    }

    public record NotificationRules(boolean filled, boolean partialFill, boolean expired) {
    }

    public record BrowseRules(int pageSize, int orderBookDepth) {
        public BrowseRules {
            ConfigValidation.require(pageSize > 0 && pageSize <= 100,
                "Bazaar page size must be between one and one hundred.");
            ConfigValidation.require(orderBookDepth > 0 && orderBookDepth <= 100,
                "Bazaar order book depth must be between one and one hundred.");
        }
    }

    public enum CatalogControl {
        ADMIN,
        PLAYERS;

        public static CatalogControl fromWire(String value) {
            return "players".equals(Objects.requireNonNull(value, "value")
                    .strip().toLowerCase(Locale.ROOT)) ? PLAYERS : ADMIN;
        }

        public String wire() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
