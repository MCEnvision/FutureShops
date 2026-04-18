package com.enviouse.futureshops;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Forge's config APIs
@Mod.EventBusSubscriber(modid = Futureshops.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER.comment("Whether to log the dirt block on common setup").define("logDirtBlock", true);

    private static final ForgeConfigSpec.IntValue MAGIC_NUMBER = BUILDER.comment("A magic number").defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER.comment("What you want the introduction message to be for the magic number").define("magicNumberIntroduction", "The magic number is... ");

    // a list of strings that are treated as resource locations for items
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER.comment("A list of items to log on common setup.").defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), Config::validateItemName);

    private static final ForgeConfigSpec.ConfigValue<String> ECONOMY_CURRENCY_NAME = BUILDER
        .comment("Display name of the economy currency")
        .define("economy.currency_name", "Coins");

    private static final ForgeConfigSpec.IntValue ECONOMY_DECIMALS = BUILDER
        .comment("Number of decimal places for displayed balances")
        .defineInRange("economy.currency_decimals", 2, 0, 6);

    private static final ForgeConfigSpec.LongValue ECONOMY_STARTING_BALANCE_MINOR_UNITS = BUILDER
        .comment("Starting balance for first-time players in minor units")
        .defineInRange("economy.starting_balance_minor_units", 100000L, 0L, Long.MAX_VALUE);

    private static final ForgeConfigSpec.LongValue ECONOMY_MAX_BALANCE_MINOR_UNITS = BUILDER
        .comment("Maximum allowed balance in minor units")
        .defineInRange("economy.max_balance_minor_units", 99999999999L, 0L, Long.MAX_VALUE);

    private static final ForgeConfigSpec.BooleanValue ECONOMY_ALLOW_NEGATIVE = BUILDER
        .comment("Whether balances are allowed to go below zero")
        .define("economy.allow_negative", false);

    private static final ForgeConfigSpec.ConfigValue<String> COIN_CHECKSUM_SALT = BUILDER
        .comment("Server-side salt used for CoinItem checksum generation")
        .define("coins.checksum_salt", "change-me-before-production");

    private static final ForgeConfigSpec.ConfigValue<String> COIN_MINT_SERVER_ID = BUILDER
        .comment("Identifier embedded in CoinItem mint metadata")
        .define("coins.mint_server_id", "futureshops-dev");

    private static final ForgeConfigSpec.IntValue COIN_MAX_AGE_DAYS = BUILDER
        .comment("Maximum age for valid CoinItems")
        .defineInRange("coins.max_age_days", 365, 1, 3650);

    private static final ForgeConfigSpec.IntValue SESSION_MAX_DISTANCE_BLOCKS = BUILDER
        .comment("Distance (in blocks) a player may move from the shop block before the session auto-closes. 0 = disabled.")
        .defineInRange("session.max_distance_blocks", 8, 0, 256);

    private static final ForgeConfigSpec.BooleanValue SESSION_CLOSE_ON_DAMAGE = BUILDER
        .comment("Whether taking any damage while a shop session is open will force-close the GUI.")
        .define("session.close_on_damage", false);

    // ---- Dynamic Pricing (spec §30) ----
    private static final ForgeConfigSpec.BooleanValue DYNAMIC_PRICING_ENABLED = BUILDER
        .comment("Master toggle for dynamic pricing. When enabled, prices fluctuate based on supply/demand.")
        .define("dynamic_pricing.enabled", false);

    private static final ForgeConfigSpec.IntValue DYNAMIC_PRICING_RECALC_INTERVAL_SEC = BUILDER
        .comment("Seconds between dynamic pricing recalculations.")
        .defineInRange("dynamic_pricing.recalc_interval_sec", 300, 10, 86400);

    private static final ForgeConfigSpec.DoubleValue DYNAMIC_PRICING_MAX_INCREASE_PCT = BUILDER
        .comment("Maximum percentage increase from base price.")
        .defineInRange("dynamic_pricing.max_increase_pct", 50.0D, 0.0D, 1000.0D);

    private static final ForgeConfigSpec.DoubleValue DYNAMIC_PRICING_MAX_DECREASE_PCT = BUILDER
        .comment("Maximum percentage decrease from base price.")
        .defineInRange("dynamic_pricing.max_decrease_pct", 30.0D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue DYNAMIC_PRICING_DEMAND_WEIGHT = BUILDER
        .comment("Weight multiplier for buy activity (demand) in the pricing formula.")
        .defineInRange("dynamic_pricing.demand_weight", 0.6D, 0.0D, 10.0D);

    private static final ForgeConfigSpec.DoubleValue DYNAMIC_PRICING_SUPPLY_WEIGHT = BUILDER
        .comment("Weight multiplier for sell activity (supply) in the pricing formula.")
        .defineInRange("dynamic_pricing.supply_weight", 0.4D, 0.0D, 10.0D);

    private static final ForgeConfigSpec.DoubleValue DYNAMIC_PRICING_DECAY_RATE = BUILDER
        .comment("Return-to-base multiplier applied each recalculation cycle (0.0 = instant reset, 1.0 = no decay).")
        .defineInRange("dynamic_pricing.decay_rate", 0.95D, 0.0D, 1.0D);

    // ---- Stock Refresh (spec §31) ----
    private static final ForgeConfigSpec.IntValue STOCK_REFRESH_CHECK_INTERVAL_SEC = BUILDER
        .comment("How often (seconds) the stock refresh scheduler checks for items due for restock. Default 60.")
        .defineInRange("stock_refresh.check_interval_sec", 60, 5, 3600);

    private static final ForgeConfigSpec.BooleanValue STOCK_REFRESH_ENABLED = BUILDER
        .comment("Master toggle for the stock refresh scheduler. When disabled, admin shop stock never auto-restocks.")
        .define("stock_refresh.enabled", true);

    // ---- Events (spec §33) ----
    private static final ForgeConfigSpec.BooleanValue EVENTS_TRANSACTION_ENABLED = BUILDER
        .comment("Fire ShopTransactionEvent and BarterTradeEvent on every trade. Disable for slight performance gain if no listeners.")
        .define("events.transaction_events", true);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean logDirtBlock;
    public static int magicNumber;
    public static String magicNumberIntroduction;
    public static Set<Item> items;

    public static String economyCurrencyName;
    public static int economyCurrencyDecimals;
    public static long economyStartingBalanceMinorUnits;
    public static long economyMaxBalanceMinorUnits;
    public static boolean economyAllowNegative;

    public static String coinChecksumSalt;
    public static String coinMintServerId;
    public static int coinMaxAgeDays;

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

    private static boolean validateItemName(final Object obj) {
        return obj instanceof final String itemName && ForgeRegistries.ITEMS.containsKey(ResourceLocation.parse(itemName));
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        logDirtBlock = LOG_DIRT_BLOCK.get();
        magicNumber = MAGIC_NUMBER.get();
        magicNumberIntroduction = MAGIC_NUMBER_INTRODUCTION.get();

        // convert the list of strings into a set of items
        items = ITEM_STRINGS.get().stream().map(itemName -> ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemName))).collect(Collectors.toSet());

        economyCurrencyName = ECONOMY_CURRENCY_NAME.get();
        economyCurrencyDecimals = ECONOMY_DECIMALS.get();
        economyStartingBalanceMinorUnits = ECONOMY_STARTING_BALANCE_MINOR_UNITS.get();
        economyMaxBalanceMinorUnits = ECONOMY_MAX_BALANCE_MINOR_UNITS.get();
        economyAllowNegative = ECONOMY_ALLOW_NEGATIVE.get();

        coinChecksumSalt = COIN_CHECKSUM_SALT.get();
        coinMintServerId = COIN_MINT_SERVER_ID.get();
        coinMaxAgeDays = COIN_MAX_AGE_DAYS.get();

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
    }
}
