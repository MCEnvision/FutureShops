package com.enviouse.futureshops.config;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.Futureshops;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(modid = Futureshops.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AuctionHouseConfig {
    public static final String FILE_NAME = "futureshops-auction-house.toml";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> DISABLE_MODES = Set.of("freeze", "drain", "cancel_and_refund");
    private static final Set<String> TIME_BASES = Set.of("real_time", "online_time");
    private static final Set<String> PAYMENT_SOURCES = Set.of("wallet", "physical", "prompt");
    private static final Set<String> PHYSICAL_REMAINDER_POLICIES = Set.of("wallet_claim", "original_source");
    private static final Set<String> FEE_DESTINATIONS = Set.of("void", "treasury");
    private static final Set<String> CONTAINER_POLICIES = Set.of("deny", "allow_empty", "allow_all");
    private static final Set<String> CAPABILITY_POLICIES = Set.of("deny", "allow");
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.ConfigValue<String> BRANDING_DISPLAY_NAME = BUILDER
        .comment("Display name used by the Auction House interface.")
        .define("branding.display_name", "Auction House",
            value -> value instanceof String text && !text.isBlank() && text.length() <= 64);

    private static final ForgeConfigSpec.ConfigValue<String> BRANDING_ACCENT_COLOR = BUILDER
        .comment("Auction House accent color as RRGGBB or AARRGGBB hexadecimal.")
        .define("branding.accent_color", "#D85B68", ConfigValidation::isHexColor);

    private static final ForgeConfigSpec.ConfigValue<String> LIFECYCLE_DISABLE_MODE = BUILDER
        .comment("Behavior when the module is disabled. Allowed values are freeze, drain, and cancel_and_refund.")
        .define("lifecycle.disable_mode", "freeze", value -> ConfigValidation.isOption(value, DISABLE_MODES));

    private static final ForgeConfigSpec.ConfigValue<String> LIFECYCLE_TIME_BASIS = BUILDER
        .comment("Auction deadline clock. Allowed values are real_time and online_time.")
        .define("lifecycle.time_basis", "real_time", value -> ConfigValidation.isOption(value, TIME_BASES));

    private static final ForgeConfigSpec.BooleanValue LIFECYCLE_PAUSE_WHILE_FROZEN = BUILDER
        .comment("Pause remaining auction time while the module is frozen.")
        .define("lifecycle.pause_while_frozen", true);

    private static final ForgeConfigSpec.BooleanValue LISTINGS_ALLOW_BUY_NOW = BUILDER
        .comment("Allow fixed price Buy Now listings.")
        .define("listings.allow_buy_now", true);

    private static final ForgeConfigSpec.BooleanValue LISTINGS_ALLOW_TIMED_AUCTIONS = BUILDER
        .comment("Allow timed auction listings.")
        .define("listings.allow_timed_auctions", true);

    private static final ForgeConfigSpec.BooleanValue LISTINGS_ALLOW_AUCTION_BUYOUT = BUILDER
        .comment("Allow timed auctions to include an optional buyout price.")
        .define("listings.allow_auction_buyout", true);

    private static final ForgeConfigSpec.IntValue LISTINGS_MAXIMUM_ACTIVE_PER_PLAYER = BUILDER
        .comment("Maximum active listings owned by one player.")
        .defineInRange("listings.maximum_active_per_player", 14, 1, 10000);

    private static final ForgeConfigSpec.IntValue LISTINGS_MAXIMUM_ITEM_COUNT = BUILDER
        .comment("Maximum total item count in one listing.")
        .defineInRange("listings.maximum_item_count", 64, 1, 2304);

    private static final ForgeConfigSpec.IntValue LISTINGS_MAXIMUM_ITEM_NBT_BYTES = BUILDER
        .comment("Maximum serialized NBT bytes accepted for one listing item.")
        .defineInRange("listings.maximum_item_nbt_bytes", 262144, 1024, 1048576);

    private static final ForgeConfigSpec.LongValue LISTINGS_MAXIMUM_HELD_VALUE_MINOR = BUILDER
        .comment("Maximum total bid value one player may hold in escrow in minor units.")
        .defineInRange("listings.maximum_held_value_minor", 100000000000L, 1L, Long.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue LISTINGS_MINIMUM_DURATION_MINUTES = BUILDER
        .comment("Minimum auction duration in minutes.")
        .defineInRange("listings.minimum_duration_minutes", 5, 1, 525600);

    private static final ForgeConfigSpec.IntValue LISTINGS_MAXIMUM_DURATION_MINUTES = BUILDER
        .comment("Maximum auction duration in minutes.")
        .defineInRange("listings.maximum_duration_minutes", 10080, 1, 525600);

    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> LISTINGS_DURATION_PRESETS_MINUTES = BUILDER
        .comment("Auction duration choices shown by the listing editor in minutes.")
        .defineList("listings.duration_presets_minutes", List.of(60, 360, 1440, 4320, 10080),
            value -> value instanceof Integer number && number > 0);

    private static final ForgeConfigSpec.BooleanValue LISTINGS_ALLOW_SELLER_CANCEL_BEFORE_BID = BUILDER
        .comment("Allow a seller to cancel an active auction before its first accepted bid.")
        .define("listings.allow_seller_cancel_before_bid", true);

    private static final ForgeConfigSpec.IntValue BIDS_MAXIMUM_ACTIVE_PER_PLAYER = BUILDER
        .comment("Maximum auctions on which one player may hold the leading bid.")
        .defineInRange("bids.maximum_active_per_player", 50, 1, 10000);

    private static final ForgeConfigSpec.LongValue BIDS_MINIMUM_INCREMENT_MINOR = BUILDER
        .comment("Minimum fixed bid increment in minor units.")
        .defineInRange("bids.minimum_increment_minor", 100L, 1L, Long.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue BIDS_MINIMUM_INCREMENT_BASIS_POINTS = BUILDER
        .comment("Additional minimum bid increment as basis points of the current bid.")
        .defineInRange("bids.minimum_increment_basis_points", 0, 0, 100000);

    private static final ForgeConfigSpec.IntValue BIDS_MAXIMUM_REQUESTS_PER_SECOND = BUILDER
        .comment("Maximum bid requests accepted from one player each second.")
        .defineInRange("bids.maximum_requests_per_second", 8, 1, 1000);

    private static final ForgeConfigSpec.BooleanValue BIDS_ALLOW_RETRACTION = BUILDER
        .comment("Allow a bidder to retract the leading bid. Disabled by default for market safety.")
        .define("bids.allow_retraction", false);

    private static final ForgeConfigSpec.BooleanValue ANTI_SNIPE_ENABLED = BUILDER
        .comment("Extend auctions when a valid bid arrives near the deadline.")
        .define("anti_snipe.enabled", true);

    private static final ForgeConfigSpec.IntValue ANTI_SNIPE_TRIGGER_SECONDS = BUILDER
        .comment("Remaining time threshold that triggers an extension in seconds.")
        .defineInRange("anti_snipe.trigger_seconds", 60, 1, 86400);

    private static final ForgeConfigSpec.IntValue ANTI_SNIPE_EXTENSION_SECONDS = BUILDER
        .comment("Time added by one anti snipe extension in seconds.")
        .defineInRange("anti_snipe.extension_seconds", 60, 1, 86400);

    private static final ForgeConfigSpec.IntValue ANTI_SNIPE_MAXIMUM_CUMULATIVE_SECONDS = BUILDER
        .comment("Maximum total time added to one auction by anti snipe extensions.")
        .defineInRange("anti_snipe.maximum_cumulative_seconds", 600, 0, 604800);

    private static final ForgeConfigSpec.IntValue ANTI_SNIPE_MAXIMUM_EXTENSION_COUNT = BUILDER
        .comment("Maximum anti snipe extensions applied to one auction.")
        .defineInRange("anti_snipe.maximum_extension_count", 10, 0, 10000);

    private static final ForgeConfigSpec.LongValue FEES_LISTING_FEE_MINOR = BUILDER
        .comment("Flat listing fee in minor units.")
        .defineInRange("fees.listing_fee_minor", 100L, 0L, Long.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue FEES_SALE_TAX_BASIS_POINTS = BUILDER
        .comment("Seller tax charged on completed sales in basis points.")
        .defineInRange("fees.sale_tax_basis_points", 250, 0, 10000);

    private static final ForgeConfigSpec.ConfigValue<String> FEES_DESTINATION = BUILDER
        .comment("Destination for fees. Allowed values are void and treasury.")
        .define("fees.destination", "void", value -> ConfigValidation.isOption(value, FEE_DESTINATIONS));

    private static final ForgeConfigSpec.BooleanValue PAYMENT_ALLOW_WALLET = BUILDER
        .comment("Allow wallet funds for listing fees, bids, and purchases.")
        .define("payment.allow_wallet", true);

    private static final ForgeConfigSpec.BooleanValue PAYMENT_ALLOW_PHYSICAL = BUILDER
        .comment(Config.FOREIGN_CURRENCY_WARNING, "Allow inventory currency to enter escrow for fees, bids, and purchases.")
        .define("payment.allow_physical", true);

    private static final ForgeConfigSpec.ConfigValue<String> PAYMENT_DEFAULT_SOURCE = BUILDER
        .comment("Default source for Auction House payments. Allowed values are wallet, physical, and prompt.")
        .define("payment.default_source", "wallet", value -> ConfigValidation.isOption(value, PAYMENT_SOURCES));

    private static final ForgeConfigSpec.ConfigValue<String> PAYMENT_PHYSICAL_REMAINDER_POLICY = BUILDER
        .comment("Destination for unused physical payment value. Allowed values are wallet_claim and original_source.")
        .define("payment.physical_remainder_policy", "wallet_claim",
            value -> ConfigValidation.isOption(value, PHYSICAL_REMAINDER_POLICIES));

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> RESTRICTIONS_DENIED_ITEM_IDS = BUILDER
        .comment("Item registry identifiers that cannot be listed.")
        .defineList("restrictions.denied_item_ids", List.of(), AuctionHouseConfig::isRegistryIdentifier);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> RESTRICTIONS_DENIED_ITEM_TAGS = BUILDER
        .comment("Item tag identifiers whose members cannot be listed.")
        .defineList("restrictions.denied_item_tags", List.of(), AuctionHouseConfig::isRegistryIdentifier);

    private static final ForgeConfigSpec.ConfigValue<String> RESTRICTIONS_CONTAINER_POLICY = BUILDER
        .comment("Container item policy. Allowed values are deny, allow_empty, and allow_all.")
        .define("restrictions.container_policy", "deny",
            value -> ConfigValidation.isOption(value, CONTAINER_POLICIES));

    private static final ForgeConfigSpec.ConfigValue<String> RESTRICTIONS_CAPABILITY_POLICY = BUILDER
        .comment("Capability backed item policy. Allowed values are deny and allow.")
        .define("restrictions.capability_policy", "deny",
            value -> ConfigValidation.isOption(value, CAPABILITY_POLICIES));

    private static final ForgeConfigSpec.BooleanValue NOTIFICATIONS_OUTBID = BUILDER
        .comment("Notify players when they are outbid.")
        .define("notifications.outbid", true);

    private static final ForgeConfigSpec.BooleanValue NOTIFICATIONS_SOLD = BUILDER
        .comment("Notify sellers when a listing sells.")
        .define("notifications.sold", true);

    private static final ForgeConfigSpec.BooleanValue NOTIFICATIONS_ENDING_SOON = BUILDER
        .comment("Notify interested players when a watched auction is ending soon.")
        .define("notifications.ending_soon", true);

    private static final ForgeConfigSpec.BooleanValue PRIVACY_PUBLIC_BID_HISTORY = BUILDER
        .comment("Expose bidder identities in public bid history.")
        .define("privacy.public_bid_history", false);

    private static final ForgeConfigSpec.IntValue BROWSE_PAGE_SIZE = BUILDER
        .comment("Maximum listing cards returned on one browse page.")
        .defineInRange("browse.page_size", 28, 4, 100);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private static volatile Settings settings = Settings.defaults();

    private AuctionHouseConfig() {
    }

    public static Settings settings() {
        return settings;
    }

    @SubscribeEvent
    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        try {
            settings = readSettings();
        } catch (IllegalArgumentException exception) {
            LOGGER.error("Rejected FutureShops Auction House configuration. {}", exception.getMessage());
        }
    }

    private static Settings readSettings() {
        return new Settings(
            new Branding(BRANDING_DISPLAY_NAME.get(), BRANDING_ACCENT_COLOR.get()),
            new Lifecycle(
                LIFECYCLE_DISABLE_MODE.get(), LIFECYCLE_TIME_BASIS.get(), LIFECYCLE_PAUSE_WHILE_FROZEN.get()),
            new ListingRules(
                LISTINGS_ALLOW_BUY_NOW.get(), LISTINGS_ALLOW_TIMED_AUCTIONS.get(),
                LISTINGS_ALLOW_AUCTION_BUYOUT.get(), LISTINGS_MAXIMUM_ACTIVE_PER_PLAYER.get(),
                LISTINGS_MAXIMUM_ITEM_COUNT.get(), LISTINGS_MAXIMUM_ITEM_NBT_BYTES.get(),
                LISTINGS_MAXIMUM_HELD_VALUE_MINOR.get(), LISTINGS_MINIMUM_DURATION_MINUTES.get(),
                LISTINGS_MAXIMUM_DURATION_MINUTES.get(), LISTINGS_DURATION_PRESETS_MINUTES.get(),
                LISTINGS_ALLOW_SELLER_CANCEL_BEFORE_BID.get()),
            new BidRules(
                BIDS_MAXIMUM_ACTIVE_PER_PLAYER.get(), BIDS_MINIMUM_INCREMENT_MINOR.get(),
                BIDS_MINIMUM_INCREMENT_BASIS_POINTS.get(), BIDS_MAXIMUM_REQUESTS_PER_SECOND.get(),
                BIDS_ALLOW_RETRACTION.get()),
            new AntiSnipeRules(
                ANTI_SNIPE_ENABLED.get(), ANTI_SNIPE_TRIGGER_SECONDS.get(), ANTI_SNIPE_EXTENSION_SECONDS.get(),
                ANTI_SNIPE_MAXIMUM_CUMULATIVE_SECONDS.get(), ANTI_SNIPE_MAXIMUM_EXTENSION_COUNT.get()),
            new FeeRules(FEES_LISTING_FEE_MINOR.get(), FEES_SALE_TAX_BASIS_POINTS.get(), FEES_DESTINATION.get()),
            new PaymentRules(
                PAYMENT_ALLOW_WALLET.get(), PAYMENT_ALLOW_PHYSICAL.get(), PAYMENT_DEFAULT_SOURCE.get(),
                PAYMENT_PHYSICAL_REMAINDER_POLICY.get()),
            new RestrictionRules(
                RESTRICTIONS_DENIED_ITEM_IDS.get(), RESTRICTIONS_DENIED_ITEM_TAGS.get(),
                RESTRICTIONS_CONTAINER_POLICY.get(), RESTRICTIONS_CAPABILITY_POLICY.get()),
            new NotificationRules(NOTIFICATIONS_OUTBID.get(), NOTIFICATIONS_SOLD.get(), NOTIFICATIONS_ENDING_SOON.get()),
            new BrowseRules(PRIVACY_PUBLIC_BID_HISTORY.get(), BROWSE_PAGE_SIZE.get())
        );
    }

    private static boolean isRegistryIdentifier(Object value) {
        return value instanceof String text && text.length() <= 256 && text.indexOf(':') > 0;
    }

    public record Settings(
        Branding branding,
        Lifecycle lifecycle,
        ListingRules listings,
        BidRules bids,
        AntiSnipeRules antiSnipe,
        FeeRules fees,
        PaymentRules payment,
        RestrictionRules restrictions,
        NotificationRules notifications,
        BrowseRules browse
    ) {
        public Settings {
            if (branding == null || lifecycle == null || listings == null || bids == null || antiSnipe == null
                || fees == null || payment == null || restrictions == null || notifications == null || browse == null) {
                throw new IllegalArgumentException("Auction House settings groups are required.");
            }
        }

        public static Settings defaults() {
            return new Settings(
                new Branding("Auction House", "#D85B68"),
                new Lifecycle("freeze", "real_time", true),
                new ListingRules(true, true, true, 14, 64, 262144, 100000000000L,
                    5, 10080, List.of(60, 360, 1440, 4320, 10080), true),
                new BidRules(50, 100L, 0, 8, false),
                new AntiSnipeRules(true, 60, 60, 600, 10),
                new FeeRules(100L, 250, "void"),
                new PaymentRules(true, true, "wallet", "wallet_claim"),
                new RestrictionRules(List.of(), List.of(), "deny", "deny"),
                new NotificationRules(true, true, true),
                new BrowseRules(false, 28)
            );
        }
    }

    public record Branding(String displayName, String accentColor) {
        public Branding {
            ConfigValidation.require(displayName != null && !displayName.isBlank() && displayName.length() <= 64,
                "Auction House display name must contain between one and sixty four characters.");
            displayName = displayName.strip();
            accentColor = ConfigValidation.requireHexColor(accentColor, "Auction House accent color");
        }
    }

    public record Lifecycle(String disableMode, String timeBasis, boolean pauseWhileFrozen) {
        public Lifecycle {
            disableMode = ConfigValidation.requireOption(disableMode, DISABLE_MODES, "Auction House disable mode");
            timeBasis = ConfigValidation.requireOption(timeBasis, TIME_BASES, "Auction House time basis");
        }
    }

    public record ListingRules(
        boolean allowBuyNow,
        boolean allowTimedAuctions,
        boolean allowAuctionBuyout,
        int maximumActivePerPlayer,
        int maximumItemCount,
        int maximumItemNbtBytes,
        long maximumHeldValueMinor,
        int minimumDurationMinutes,
        int maximumDurationMinutes,
        List<? extends Integer> durationPresetsMinutes,
        boolean allowSellerCancelBeforeBid
    ) {
        public ListingRules {
            ConfigValidation.require(allowBuyNow || allowTimedAuctions,
                "At least one Auction House listing type must be allowed.");
            ConfigValidation.require(!allowAuctionBuyout || allowTimedAuctions,
                "Auction buyout requires timed auctions.");
            ConfigValidation.require(maximumActivePerPlayer > 0, "Maximum active listings must be positive.");
            ConfigValidation.require(maximumItemCount > 0, "Maximum listing item count must be positive.");
            ConfigValidation.require(maximumItemNbtBytes > 0, "Maximum listing NBT bytes must be positive.");
            ConfigValidation.require(maximumHeldValueMinor > 0, "Maximum held bid value must be positive.");
            ConfigValidation.require(minimumDurationMinutes > 0, "Minimum auction duration must be positive.");
            ConfigValidation.require(maximumDurationMinutes >= minimumDurationMinutes,
                "Maximum auction duration must not be less than the minimum duration.");
            durationPresetsMinutes = ConfigValidation.requirePositiveList(
                durationPresetsMinutes, "Auction duration presets");
            ConfigValidation.require(durationPresetsMinutes.size() <= 8,
                "Auction duration presets must contain no more than eight choices.");
            ConfigValidation.require(durationPresetsMinutes.stream().allMatch(
                    duration -> duration >= minimumDurationMinutes && duration <= maximumDurationMinutes),
                "Auction duration presets must remain inside the configured duration range.");
        }
    }

    public record BidRules(
        int maximumActivePerPlayer,
        long minimumIncrementMinor,
        int minimumIncrementBasisPoints,
        int maximumRequestsPerSecond,
        boolean allowRetraction
    ) {
        public BidRules {
            ConfigValidation.require(maximumActivePerPlayer > 0, "Maximum active bids must be positive.");
            ConfigValidation.require(minimumIncrementMinor > 0, "Minimum bid increment must be positive.");
            ConfigValidation.require(minimumIncrementBasisPoints >= 0,
                "Minimum bid increment basis points must not be negative.");
            ConfigValidation.require(maximumRequestsPerSecond > 0,
                "Maximum bid requests per second must be positive.");
        }
    }

    public record AntiSnipeRules(
        boolean enabled,
        int triggerSeconds,
        int extensionSeconds,
        int maximumCumulativeSeconds,
        int maximumExtensionCount
    ) {
        public AntiSnipeRules {
            ConfigValidation.require(triggerSeconds > 0, "Anti snipe trigger must be positive.");
            ConfigValidation.require(extensionSeconds > 0, "Anti snipe extension must be positive.");
            ConfigValidation.require(maximumCumulativeSeconds >= 0,
                "Maximum anti snipe duration must not be negative.");
            ConfigValidation.require(maximumExtensionCount >= 0,
                "Maximum anti snipe extension count must not be negative.");
            if (enabled) {
                ConfigValidation.require(maximumCumulativeSeconds >= extensionSeconds,
                    "Maximum anti snipe duration must permit at least one extension.");
                ConfigValidation.require(maximumExtensionCount > 0,
                    "Enabled anti snipe rules require at least one extension.");
            }
        }
    }

    public record FeeRules(long listingFeeMinor, int saleTaxBasisPoints, String destination) {
        public FeeRules {
            ConfigValidation.require(listingFeeMinor >= 0, "Listing fee must not be negative.");
            ConfigValidation.require(saleTaxBasisPoints >= 0 && saleTaxBasisPoints <= 10000,
                "Sale tax basis points must be between zero and ten thousand.");
            destination = ConfigValidation.requireOption(destination, FEE_DESTINATIONS, "Auction fee destination");
        }
    }

    public record PaymentRules(
        boolean allowWallet,
        boolean allowPhysical,
        String defaultSource,
        String physicalRemainderPolicy
    ) {
        public PaymentRules {
            ConfigValidation.require(allowWallet || allowPhysical,
                "Auction House must allow at least one payment source.");
            defaultSource = ConfigValidation.requireOption(defaultSource, PAYMENT_SOURCES,
                "Auction House default payment source");
            ConfigValidation.require(!"wallet".equals(defaultSource) || allowWallet,
                "Wallet cannot be the default when wallet payment is disabled.");
            ConfigValidation.require(!"physical".equals(defaultSource) || allowPhysical,
                "Physical cannot be the default when physical payment is disabled.");
            physicalRemainderPolicy = ConfigValidation.requireOption(
                physicalRemainderPolicy, PHYSICAL_REMAINDER_POLICIES, "Physical remainder policy");
        }
    }

    public record RestrictionRules(
        List<? extends String> deniedItemIds,
        List<? extends String> deniedItemTags,
        String containerPolicy,
        String capabilityPolicy
    ) {
        public RestrictionRules {
            deniedItemIds = deniedItemIds == null ? List.of() : List.copyOf(deniedItemIds);
            deniedItemTags = deniedItemTags == null ? List.of() : List.copyOf(deniedItemTags);
            ConfigValidation.require(deniedItemIds.stream().allMatch(AuctionHouseConfig::isRegistryIdentifier),
                "Denied Auction House item identifiers must be valid registry identifiers.");
            ConfigValidation.require(deniedItemTags.stream().allMatch(AuctionHouseConfig::isRegistryIdentifier),
                "Denied Auction House tag identifiers must be valid registry identifiers.");
            containerPolicy = ConfigValidation.requireOption(containerPolicy, CONTAINER_POLICIES,
                "Auction House container policy");
            capabilityPolicy = ConfigValidation.requireOption(capabilityPolicy, CAPABILITY_POLICIES,
                "Auction House capability policy");
        }
    }

    public record NotificationRules(boolean outbid, boolean sold, boolean endingSoon) {
    }

    public record BrowseRules(boolean publicBidHistory, int pageSize) {
        public BrowseRules {
            ConfigValidation.require(pageSize > 0 && pageSize <= 100,
                "Auction House page size must be between one and one hundred.");
        }
    }
}
