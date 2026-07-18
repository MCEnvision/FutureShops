package com.enviouse.futureshops;

import com.enviouse.futureshops.config.ConfigValidation;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

import java.util.Locale;
import java.util.Set;

@Mod.EventBusSubscriber(modid = Futureshops.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> DENSITIES = Set.of("compact", "normal", "comfortable");
    private static final Set<String> CARD_SIZES = Set.of("small", "medium", "large");
    private static final Set<String> THEME_PRESETS = Set.of("server", "nocturne", "emerald", "crimson");
    private static final Set<String> COLORBLIND_MODES = Set.of(
        "none", "deuteranopia", "protanopia", "tritanopia", "monochrome");
    private static final Set<String> CONFIRMATION_MODES = Set.of("always", "large_only", "never");
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue USE_12_HOUR_TIME = BUILDER
        .comment("Display transaction and settlement timestamps with twelve hour AM and PM time.")
        .define("ui.use_12_hour_time", false);

    private static final ForgeConfigSpec.ConfigValue<String> DENSITY = BUILDER
        .comment("Interface density. Allowed values are compact, normal, and comfortable.")
        .define("ui.density", "normal", value -> isOption(value, DENSITIES));

    private static final ForgeConfigSpec.ConfigValue<String> CARD_SIZE = BUILDER
        .comment("Market card size. Allowed values are small, medium, and large.")
        .define("ui.card_size", "medium", value -> isOption(value, CARD_SIZES));

    private static final ForgeConfigSpec.ConfigValue<String> CURRENCY_SYMBOL = BUILDER
        .comment("Currency symbol shown before market prices. Use an empty value to hide it.")
        .define("ui.currency_symbol", "$", ClientConfig::isCurrencySymbol);

    private static final ForgeConfigSpec.BooleanValue REMEMBER_TAB = BUILDER
        .comment("Remember the last selected tab for each market module.")
        .define("ui.remember_tab", true);

    private static final ForgeConfigSpec.BooleanValue REMEMBER_FILTER = BUILDER
        .comment("Remember filters and categories for each market module.")
        .define("ui.remember_filter", true);

    private static final ForgeConfigSpec.BooleanValue REMEMBER_SORT = BUILDER
        .comment("Remember sorting for each market module.")
        .define("ui.remember_sort", true);

    private static final ForgeConfigSpec.BooleanValue REMEMBER_PAYMENT_SOURCE = BUILDER
        .comment("Remember the payment source for the current client session.")
        .define("ui.remember_payment_source", true);

    private static final ForgeConfigSpec.BooleanValue PREDICTIVE_SEARCH = BUILDER
        .comment("Enable predictive matching while search text is incomplete.")
        .define("search.predictive", true);

    private static final ForgeConfigSpec.IntValue SEARCH_DEBOUNCE_MILLIS = BUILDER
        .comment("Delay before a remote market search request is sent in milliseconds.")
        .defineInRange("search.debounce_millis", 180, 0, 2000);

    private static final ForgeConfigSpec.ConfigValue<String> THEME_PRESET = BUILDER
        .comment("Theme preset. Allowed values are server, nocturne, emerald, and crimson.")
        .define("theme.preset", "server", value -> isOption(value, THEME_PRESETS));

    private static final ForgeConfigSpec.BooleanValue CUSTOM_ACCENT_ENABLED = BUILDER
        .comment("Use the custom accent instead of the module accent.")
        .define("theme.custom_accent_enabled", false);

    private static final ForgeConfigSpec.ConfigValue<String> CUSTOM_ACCENT = BUILDER
        .comment("Custom accent color as RRGGBB or AARRGGBB hexadecimal. Alpha is ignored.")
        .define("theme.custom_accent", "#9184D9", ConfigValidation::isHexColor);

    private static final ForgeConfigSpec.BooleanValue HIGH_CONTRAST = BUILDER
        .comment("Increase text, focus, and border contrast.")
        .define("accessibility.high_contrast", false);

    private static final ForgeConfigSpec.ConfigValue<String> COLORBLIND_MODE = BUILDER
        .comment("Colorblind mode. Allowed values are none, deuteranopia, protanopia, tritanopia, and monochrome.")
        .define("accessibility.colorblind_mode", "none", value -> isOption(value, COLORBLIND_MODES));

    private static final ForgeConfigSpec.BooleanValue REDUCED_MOTION = BUILDER
        .comment("Disable nonessential interface movement.")
        .define("accessibility.reduced_motion", false);

    private static final ForgeConfigSpec.IntValue ANIMATION_SPEED_PERCENT = BUILDER
        .comment("Interface animation speed as a percentage. Zero disables animation.")
        .defineInRange("motion.animation_speed_percent", 100, 0, 300);

    private static final ForgeConfigSpec.BooleanValue MARKET_SOUNDS = BUILDER
        .comment("Enable market interface sounds.")
        .define("sound.enabled", true);

    private static final ForgeConfigSpec.IntValue MARKET_SOUND_VOLUME_PERCENT = BUILDER
        .comment("Market interface sound volume as a percentage.")
        .defineInRange("sound.volume_percent", 70, 0, 100);

    private static final ForgeConfigSpec.ConfigValue<String> PURCHASE_CONFIRMATION_MODE = BUILDER
        .comment("Purchase confirmation policy. Allowed values are always, large_only, and never.")
        .define("confirmation.purchase_mode", "always", value -> isOption(value, CONFIRMATION_MODES));

    private static final ForgeConfigSpec.LongValue LARGE_PURCHASE_THRESHOLD_MINOR = BUILDER
        .comment("Minimum purchase value that requires confirmation in large only mode.")
        .defineInRange("confirmation.large_purchase_threshold_minor", 100000L, 1L, Long.MAX_VALUE);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    private static volatile Settings settings = Settings.defaults();

    private ClientConfig() {
    }

    public static Settings settings() {
        return settings;
    }

    public static boolean use12HourTime() {
        return settings.presentation().use12HourTime();
    }

    @SubscribeEvent
    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        try {
            settings = readSettings();
        } catch (IllegalArgumentException exception) {
            LOGGER.error("Rejected FutureShops client configuration. {}", exception.getMessage());
        }
    }

    private static Settings readSettings() {
        return new Settings(
            new Presentation(
                USE_12_HOUR_TIME.get(), DENSITY.get(), CARD_SIZE.get(), CURRENCY_SYMBOL.get(), REMEMBER_TAB.get(),
                REMEMBER_FILTER.get(), REMEMBER_SORT.get(), REMEMBER_PAYMENT_SOURCE.get()),
            new Search(PREDICTIVE_SEARCH.get(), SEARCH_DEBOUNCE_MILLIS.get()),
            new Theme(THEME_PRESET.get(), CUSTOM_ACCENT_ENABLED.get(), CUSTOM_ACCENT.get()),
            new Accessibility(HIGH_CONTRAST.get(), COLORBLIND_MODE.get(), REDUCED_MOTION.get()),
            new Motion(ANIMATION_SPEED_PERCENT.get()),
            new Sound(MARKET_SOUNDS.get(), MARKET_SOUND_VOLUME_PERCENT.get()),
            new Confirmation(PURCHASE_CONFIRMATION_MODE.get(), LARGE_PURCHASE_THRESHOLD_MINOR.get())
        );
    }

    private static boolean isOption(Object value, Set<String> options) {
        return value instanceof String text && options.contains(text.strip().toLowerCase(Locale.ROOT));
    }

    public record Settings(
        Presentation presentation,
        Search search,
        Theme theme,
        Accessibility accessibility,
        Motion motion,
        Sound sound,
        Confirmation confirmation
    ) {
        public Settings {
            if (presentation == null || search == null || theme == null || accessibility == null
                || motion == null || sound == null || confirmation == null) {
                throw new IllegalArgumentException("Client settings groups are required.");
            }
        }

        public static Settings defaults() {
            return new Settings(
                new Presentation(false, "normal", "medium", "$", true, true, true, true),
                new Search(true, 180),
                new Theme("server", false, "#9184D9"),
                new Accessibility(false, "none", false),
                new Motion(100),
                new Sound(true, 70),
                new Confirmation("always", 100000L)
            );
        }
    }

    public record Presentation(
        boolean use12HourTime,
        String density,
        String cardSize,
        String currencySymbol,
        boolean rememberTab,
        boolean rememberFilter,
        boolean rememberSort,
        boolean rememberPaymentSource
    ) {
        public Presentation {
            density = ConfigValidation.requireOption(density, DENSITIES, "Client density");
            cardSize = ConfigValidation.requireOption(cardSize, CARD_SIZES, "Client card size");
            ConfigValidation.require(isCurrencySymbol(currencySymbol),
                "Client currency symbol must contain at most four visible characters.");
        }
    }

    private static boolean isCurrencySymbol(Object value) {
        if (!(value instanceof String text) || text.length() > 4) {
            return false;
        }
        return text.chars().noneMatch(character -> Character.isISOControl(character));
    }

    public record Search(boolean predictive, int debounceMillis) {
        public Search {
            ConfigValidation.require(debounceMillis >= 0 && debounceMillis <= 2000,
                "Search delay must be between zero and two thousand milliseconds.");
        }
    }

    public record Theme(String preset, boolean customAccentEnabled, String customAccent) {
        public Theme {
            preset = ConfigValidation.requireOption(preset, THEME_PRESETS, "Client theme preset");
            customAccent = ConfigValidation.requireHexColor(customAccent, "Client custom accent");
        }
    }

    public record Accessibility(boolean highContrast, String colorblindMode, boolean reducedMotion) {
        public Accessibility {
            colorblindMode = ConfigValidation.requireOption(
                colorblindMode, COLORBLIND_MODES, "Client colorblind mode");
        }
    }

    public record Motion(int animationSpeedPercent) {
        public Motion {
            ConfigValidation.require(animationSpeedPercent >= 0 && animationSpeedPercent <= 300,
                "Animation speed must be between zero and three hundred percent.");
        }
    }

    public record Sound(boolean enabled, int volumePercent) {
        public Sound {
            ConfigValidation.require(volumePercent >= 0 && volumePercent <= 100,
                "Sound volume must be between zero and one hundred percent.");
        }
    }

    public record Confirmation(String purchaseMode, long largePurchaseThresholdMinor) {
        public Confirmation {
            purchaseMode = ConfigValidation.requireOption(
                purchaseMode, CONFIRMATION_MODES, "Purchase confirmation mode");
            ConfigValidation.require(largePurchaseThresholdMinor > 0L,
                "Large purchase confirmation threshold must be positive.");
        }

        public boolean requiresPurchaseConfirmation(long totalMinor) {
            if (totalMinor < 0L) {
                throw new IllegalArgumentException("Purchase total must not be negative.");
            }
            return "always".equals(purchaseMode)
                || "large_only".equals(purchaseMode) && totalMinor >= largePurchaseThresholdMinor;
        }
    }
}
