package com.enviouse.futureshopsp;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * FutureShops common config. Ported 1:1 from the 1.20.1 Forge {@code ForgeConfigSpec}
 * version — {@code ForgeConfigSpec} -> {@code net.neoforged.neoforge.common.ModConfigSpec}
 * is a drop-in API. Field semantics are unchanged.
 */
@EventBusSubscriber(modid = Futureshops.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.ConfigValue<String> ECONOMY_CURRENCY_NAME = BUILDER
        .comment("Display name of the economy currency")
        .define("economy.currency_name", "Coins");

    private static final ModConfigSpec.IntValue ECONOMY_DECIMALS = BUILDER
        .comment("Number of decimal places for displayed balances")
        .defineInRange("economy.currency_decimals", 2, 0, 6);

    private static final ModConfigSpec.LongValue ECONOMY_STARTING_BALANCE_MINOR_UNITS = BUILDER
        .comment("Starting balance for first-time players in minor units")
        .defineInRange("economy.starting_balance_minor_units", 100000L, 0L, Long.MAX_VALUE);

    private static final ModConfigSpec.LongValue ECONOMY_MAX_BALANCE_MINOR_UNITS = BUILDER
        .comment("Maximum allowed balance in minor units")
        .defineInRange("economy.max_balance_minor_units", 99999999999L, 0L, Long.MAX_VALUE);

    private static final ModConfigSpec.BooleanValue ECONOMY_ALLOW_NEGATIVE = BUILDER
        .comment(
            "Whether admins may push a player's balance below zero via /shopadmin bal remove.",
            "Player-driven transactions (BUY, SELL, BARTER, TRANSFER, WITHDRAW, etc.) are always",
            "blocked when they would leave the balance negative — i.e. a player already in debt",
            "cannot buy anything until their balance is back at zero or above."
        )
        .define("economy.allow_negative", false);

    private static final ModConfigSpec.ConfigValue<String> MONEY_CHECKSUM_SALT = BUILDER
        .comment("Server-side salt used for MoneyItem checksum generation")
        .define("money.checksum_salt", "change-me-before-production");

    private static final ModConfigSpec.ConfigValue<String> MONEY_MINT_SERVER_ID = BUILDER
        .comment("Identifier embedded in MoneyItem mint metadata")
        .define("money.mint_server_id", "futureshops-dev");

    private static final ModConfigSpec.IntValue MONEY_MAX_AGE_DAYS = BUILDER
        .comment("Maximum age for valid MoneyItems")
        .defineInRange("money.max_age_days", 365, 1, 3650);

    private static final ModConfigSpec.IntValue SESSION_MAX_DISTANCE_BLOCKS = BUILDER
        .comment("Distance (in blocks) a player may move from the shop block before the session auto-closes. 0 = disabled.")
        .defineInRange("session.max_distance_blocks", 8, 0, 256);

    private static final ModConfigSpec.BooleanValue SESSION_CLOSE_ON_DAMAGE = BUILDER
        .comment("Whether taking any damage while a shop session is open will force-close the GUI.")
        .define("session.close_on_damage", false);

    // ---- Dynamic Pricing (spec §30) ----
    private static final ModConfigSpec.BooleanValue DYNAMIC_PRICING_ENABLED = BUILDER
        .comment("Master toggle for dynamic pricing. When enabled, prices fluctuate based on supply/demand.")
        .define("dynamic_pricing.enabled", false);

    private static final ModConfigSpec.IntValue DYNAMIC_PRICING_RECALC_INTERVAL_SEC = BUILDER
        .comment("Seconds between dynamic pricing recalculations.")
        .defineInRange("dynamic_pricing.recalc_interval_sec", 300, 10, 86400);

    private static final ModConfigSpec.DoubleValue DYNAMIC_PRICING_MAX_INCREASE_PCT = BUILDER
        .comment("Maximum percentage increase from base price.")
        .defineInRange("dynamic_pricing.max_increase_pct", 50.0D, 0.0D, 1000.0D);

    private static final ModConfigSpec.DoubleValue DYNAMIC_PRICING_MAX_DECREASE_PCT = BUILDER
        .comment("Maximum percentage decrease from base price.")
        .defineInRange("dynamic_pricing.max_decrease_pct", 30.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue DYNAMIC_PRICING_DEMAND_WEIGHT = BUILDER
        .comment("Weight multiplier for buy activity (demand) in the pricing formula.")
        .defineInRange("dynamic_pricing.demand_weight", 0.6D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue DYNAMIC_PRICING_SUPPLY_WEIGHT = BUILDER
        .comment("Weight multiplier for sell activity (supply) in the pricing formula.")
        .defineInRange("dynamic_pricing.supply_weight", 0.4D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue DYNAMIC_PRICING_DECAY_RATE = BUILDER
        .comment("Return-to-base multiplier applied each recalculation cycle (0.0 = instant reset, 1.0 = no decay).")
        .defineInRange("dynamic_pricing.decay_rate", 0.95D, 0.0D, 1.0D);

    // ---- Stock Refresh (spec §31) ----
    private static final ModConfigSpec.IntValue STOCK_REFRESH_CHECK_INTERVAL_SEC = BUILDER
        .comment("How often (seconds) the stock refresh scheduler checks for items due for restock. Default 60.")
        .defineInRange("stock_refresh.check_interval_sec", 60, 5, 3600);

    private static final ModConfigSpec.BooleanValue STOCK_REFRESH_ENABLED = BUILDER
        .comment("Master toggle for the stock refresh scheduler. When disabled, admin shop stock never auto-restocks.")
        .define("stock_refresh.enabled", true);

    // ---- Events (spec §33) ----
    private static final ModConfigSpec.BooleanValue EVENTS_TRANSACTION_ENABLED = BUILDER
        .comment("Fire ShopTransactionEvent and BarterTradeEvent on every trade. Disable for slight performance gain if no listeners.")
        .define("events.transaction_events", true);

    // ---- Local Listings (player-shop discovery via /shop) ----
    private static final ModConfigSpec.IntValue LOCAL_LISTINGS_SCAN_RADIUS_BLOCKS = BUILDER
        .comment(
            "Radius (in blocks) the /shop -> Local Listings (and Nearby tab) will search around the player for player-owned shop blocks.",
            "Set to 0 for unlimited distance — every player shop in the current dimension whose chunk is currently loaded will be included.",
            "Shops whose chunks are not currently loaded are always skipped, regardless of radius — that is intentional, since unloaded shops cannot report fresh stock counts."
        )
        .defineInRange("local_listings.scan_radius_blocks", 64, 0, 1024);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static String economyCurrencyName;
    public static int economyCurrencyDecimals;
    public static long economyStartingBalanceMinorUnits;
    public static long economyMaxBalanceMinorUnits;
    public static boolean economyAllowNegative;

    public static String moneyChecksumSalt;
    public static String moneyMintServerId;
    public static int moneyMaxAgeDays;

    public static int sessionMaxDistanceBlocks;
    public static boolean sessionCloseOnDamage;

    // Dynamic Pricing
    public static boolean dynamicPricingEnabled;
    public static int dynamicPricingRecalcIntervalSec;
    public static double dynamicPricingMaxIncreasePct;
    public static double dynamicPricingMaxDecreasePct;
    public static double dynamicPricingDemandWeight;
    public static double dynamicPricingSupplyWeight;
    public static double dynamicPricingDecayRate;

    // Stock Refresh (spec §31)
    public static int stockRefreshCheckIntervalSec;
    public static boolean stockRefreshEnabled;

    // Events (spec §33)
    public static boolean eventsTransactionEnabled;

    // Local Listings
    public static int localListingsScanRadiusBlocks;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        economyCurrencyName = ECONOMY_CURRENCY_NAME.get();
        economyCurrencyDecimals = ECONOMY_DECIMALS.get();
        economyStartingBalanceMinorUnits = ECONOMY_STARTING_BALANCE_MINOR_UNITS.get();
        economyMaxBalanceMinorUnits = ECONOMY_MAX_BALANCE_MINOR_UNITS.get();
        economyAllowNegative = ECONOMY_ALLOW_NEGATIVE.get();

        moneyChecksumSalt = MONEY_CHECKSUM_SALT.get();
        moneyMintServerId = MONEY_MINT_SERVER_ID.get();
        moneyMaxAgeDays = MONEY_MAX_AGE_DAYS.get();

        sessionMaxDistanceBlocks = SESSION_MAX_DISTANCE_BLOCKS.get();
        sessionCloseOnDamage = SESSION_CLOSE_ON_DAMAGE.get();

        dynamicPricingEnabled = DYNAMIC_PRICING_ENABLED.get();
        dynamicPricingRecalcIntervalSec = DYNAMIC_PRICING_RECALC_INTERVAL_SEC.get();
        dynamicPricingMaxIncreasePct = DYNAMIC_PRICING_MAX_INCREASE_PCT.get();
        dynamicPricingMaxDecreasePct = DYNAMIC_PRICING_MAX_DECREASE_PCT.get();
        dynamicPricingDemandWeight = DYNAMIC_PRICING_DEMAND_WEIGHT.get();
        dynamicPricingSupplyWeight = DYNAMIC_PRICING_SUPPLY_WEIGHT.get();
        dynamicPricingDecayRate = DYNAMIC_PRICING_DECAY_RATE.get();

        stockRefreshCheckIntervalSec = STOCK_REFRESH_CHECK_INTERVAL_SEC.get();
        stockRefreshEnabled = STOCK_REFRESH_ENABLED.get();
        eventsTransactionEnabled = EVENTS_TRANSACTION_ENABLED.get();

        localListingsScanRadiusBlocks = LOCAL_LISTINGS_SCAN_RADIUS_BLOCKS.get();
    }
}
