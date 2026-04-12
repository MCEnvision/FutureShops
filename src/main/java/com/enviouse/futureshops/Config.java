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
    }
}
