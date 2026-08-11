package com.enviouse.futureshops.config;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.Futureshops;
import com.enviouse.futureshops.server.security.ServerRequestSecuritySettings;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

@Mod.EventBusSubscriber(modid = Futureshops.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EscrowConfig {
    public static final String FILE_NAME = "futureshops-escrow.toml";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> REFUND_POLICIES = Set.of("wallet_claim", "original_source");
    private static final Set<String> AUDIT_LEVELS = Set.of("minimal", "standard", "verbose");
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ServerRequestSecuritySettings REQUEST_SECURITY_DEFAULTS =
        ServerRequestSecuritySettings.defaults();

    private static final ForgeConfigSpec.IntValue RECOVERY_WORK_PER_TICK = BUILDER
        .comment("Maximum recoverable transactions processed during one server tick.")
        .defineInRange("recovery.work_per_tick", 64, 1, 10000);

    private static final ForgeConfigSpec.IntValue RECOVERY_INITIAL_RETRY_DELAY_TICKS = BUILDER
        .comment("Initial retry delay for a recoverable transaction in server ticks.")
        .defineInRange("recovery.initial_retry_delay_ticks", 20, 1, 1200000);

    private static final ForgeConfigSpec.IntValue RECOVERY_MAXIMUM_RETRY_DELAY_TICKS = BUILDER
        .comment("Maximum retry delay for a recoverable transaction in server ticks.")
        .defineInRange("recovery.maximum_retry_delay_ticks", 1200, 1, 1200000);

    private static final ForgeConfigSpec.IntValue RECOVERY_MAX_PENDING_TRANSACTIONS = BUILDER
        .comment("Maximum pending transaction records accepted before new value mutations fail closed.")
        .defineInRange("recovery.max_pending_transactions", 100000, 100, 10000000);

    private static final ForgeConfigSpec.IntValue MIGRATION_WALLET_ENTRIES_PER_TICK = BUILDER
        .comment("Maximum legacy wallet entries imported during one server tick.")
        .defineInRange("migration.wallet_entries_per_tick", 256, 1, 1024);

    private static final ForgeConfigSpec.IntValue PERSISTENCE_CHECKPOINT_INTERVAL_SECONDS = BUILDER
        .comment("Target interval between durable escrow checkpoints in seconds.")
        .defineInRange("persistence.checkpoint_interval_seconds", 30, 5, 86400);

    private static final ForgeConfigSpec.IntValue PERSISTENCE_CHECKPOINT_GENERATION_RETENTION = BUILDER
        .comment("Number of verified checkpoint and journal generation pairs retained on disk.")
        .defineInRange("persistence.checkpoint_generation_retention", 2, 2, 16);

    private static final ForgeConfigSpec.LongValue PERSISTENCE_CHECKPOINT_MAXIMUM_JOURNAL_BYTES = BUILDER
        .comment("Force a checkpoint when the active escrow journal reaches this many bytes.")
        .defineInRange("persistence.checkpoint_maximum_journal_bytes",
            67108864L, 1048576L, 2147483647L);

    private static final ForgeConfigSpec.IntValue PERSISTENCE_CHECKPOINT_MAXIMUM_JOURNAL_RECORDS = BUILDER
        .comment("Force a checkpoint when the active escrow journal reaches this many records.")
        .defineInRange("persistence.checkpoint_maximum_journal_records", 50000, 100, 10000000);

    private static final ForgeConfigSpec.IntValue PERSISTENCE_ARCHIVE_RETENTION_DAYS = BUILDER
        .comment("Retention period for terminal transaction audit summaries in days.")
        .defineInRange("persistence.archive_retention_days", 365, 1, 36500);

    private static final ForgeConfigSpec.BooleanValue CLAIMS_AUTOMATIC_DELIVERY = BUILDER
        .comment("Attempt automatic claim delivery when the beneficiary is online. Failed delivery remains claimable.")
        .define("claims.automatic_delivery", true);

    private static final ForgeConfigSpec.IntValue CLAIMS_MAX_ENTRIES_PER_REQUEST = BUILDER
        .comment("Maximum claim entries handled by one claim request.")
        .defineInRange("claims.max_entries_per_request", 64, 1, 1024);

    private static final ForgeConfigSpec.IntValue CLAIMS_DELIVERY_WORK_PER_TICK = BUILDER
        .comment("Maximum automatic claim delivery attempts during one server tick.")
        .defineInRange("claims.delivery_work_per_tick", 32, 1, 10000);

    private static final ForgeConfigSpec.IntValue CLAIMS_EXACT_ITEM_DELIVERY_OPERATIONS_PER_TICK = BUILDER
        .comment("Maximum exact item claim delivery operations during one server tick. Each operation may force player data to durable storage.")
        .defineInRange("claims.exact_item_delivery_operations_per_tick", 1, 1, 1);

    private static final ForgeConfigSpec.IntValue ASSETS_MAX_PER_TRANSACTION = BUILDER
        .comment("Maximum asset lots stored by one transaction.")
        .defineInRange("assets.max_per_transaction", 256, 1, 4096);

    private static final ForgeConfigSpec.IntValue ASSETS_MAX_NBT_BYTES = BUILDER
        .comment("Maximum serialized NBT bytes accepted for one asset lot.")
        .defineInRange("assets.max_nbt_bytes", 1048576, 1024, 1048576);

    private static final ForgeConfigSpec.IntValue ASSETS_MAX_TOTAL_NBT_BYTES = BUILDER
        .comment("Maximum combined serialized NBT bytes accepted by one escrow transaction.")
        .defineInRange("assets.max_total_nbt_bytes", 8388608, 1024, 8388608);

    private static final ForgeConfigSpec.BooleanValue STORAGE_STRICT_EXTERNAL_STORAGE = BUILDER
        .comment("Reject external storage operations that cannot provide deterministic reconciliation evidence.")
        .define("storage.strict_external_storage", true);

    private static final ForgeConfigSpec.IntValue REQUEST_SECURITY_TRACKED_KEY_CAP = BUILDER
        .comment("Maximum player and action request buckets retained by one server. Applied when the server starts.")
        .defineInRange("request_security.tracked_key_cap",
            REQUEST_SECURITY_DEFAULTS.trackedKeyCap(), 64, 1000000);

    private static final ForgeConfigSpec.IntValue REQUEST_SECURITY_IDLE_RETENTION_SECONDS = BUILDER
        .comment("Seconds a fully refilled idle request bucket remains tracked.")
        .defineInRange("request_security.idle_retention_seconds",
            Math.toIntExact(REQUEST_SECURITY_DEFAULTS.idleRetention().toSeconds()),
            10, 86400);

    private static final ForgeConfigSpec.IntValue REQUEST_SECURITY_ATM_DATA_CAPACITY = BUILDER
        .comment("ATM data request burst capacity per player.")
        .defineInRange("request_security.atm_data.capacity",
            REQUEST_SECURITY_DEFAULTS.atmData().capacity(), 1, 1000);

    private static final ForgeConfigSpec.IntValue REQUEST_SECURITY_ATM_DATA_REFILL_TOKENS = BUILDER
        .comment("ATM data tokens restored each refill period. This value cannot exceed capacity.")
        .defineInRange("request_security.atm_data.refill_tokens",
            REQUEST_SECURITY_DEFAULTS.atmData().refillTokens(), 1, 1000);

    private static final ForgeConfigSpec.LongValue REQUEST_SECURITY_ATM_DATA_REFILL_PERIOD_MILLIS = BUILDER
        .comment("ATM data token refill period in milliseconds.")
        .defineInRange("request_security.atm_data.refill_period_millis",
            REQUEST_SECURITY_DEFAULTS.atmData().refillPeriod().toMillis(),
            50L, 3600000L);

    private static final ForgeConfigSpec.IntValue REQUEST_SECURITY_ATM_WITHDRAWAL_CAPACITY = BUILDER
        .comment("ATM withdrawal request burst capacity per player.")
        .defineInRange("request_security.atm_withdrawal.capacity",
            REQUEST_SECURITY_DEFAULTS.atmWithdrawal().capacity(), 1, 1000);

    private static final ForgeConfigSpec.IntValue REQUEST_SECURITY_ATM_WITHDRAWAL_REFILL_TOKENS = BUILDER
        .comment("ATM withdrawal tokens restored each refill period. This value cannot exceed capacity.")
        .defineInRange("request_security.atm_withdrawal.refill_tokens",
            REQUEST_SECURITY_DEFAULTS.atmWithdrawal().refillTokens(), 1, 1000);

    private static final ForgeConfigSpec.LongValue REQUEST_SECURITY_ATM_WITHDRAWAL_REFILL_PERIOD_MILLIS = BUILDER
        .comment("ATM withdrawal token refill period in milliseconds.")
        .defineInRange("request_security.atm_withdrawal.refill_period_millis",
            REQUEST_SECURITY_DEFAULTS.atmWithdrawal().refillPeriod().toMillis(),
            50L, 3600000L);

    private static final ForgeConfigSpec.IntValue REQUEST_SECURITY_ATM_CASH_COLLECTION_CAPACITY = BUILDER
        .comment("ATM cash collection request burst capacity per player.")
        .defineInRange("request_security.atm_cash_collection.capacity",
            REQUEST_SECURITY_DEFAULTS.atmCashCollection().capacity(), 1, 1000);

    private static final ForgeConfigSpec.IntValue REQUEST_SECURITY_ATM_CASH_COLLECTION_REFILL_TOKENS = BUILDER
        .comment("ATM cash collection tokens restored each refill period. This value cannot exceed capacity.")
        .defineInRange("request_security.atm_cash_collection.refill_tokens",
            REQUEST_SECURITY_DEFAULTS.atmCashCollection().refillTokens(), 1, 1000);

    private static final ForgeConfigSpec.LongValue REQUEST_SECURITY_ATM_CASH_COLLECTION_REFILL_PERIOD_MILLIS = BUILDER
        .comment("ATM cash collection token refill period in milliseconds.")
        .defineInRange("request_security.atm_cash_collection.refill_period_millis",
            REQUEST_SECURITY_DEFAULTS.atmCashCollection().refillPeriod().toMillis(),
            50L, 3600000L);

    private static final ForgeConfigSpec.IntValue REQUEST_SECURITY_ATM_DEPOSIT_CAPACITY = BUILDER
        .comment("Reserved ATM deposit request burst capacity per player.")
        .defineInRange("request_security.atm_deposit.capacity",
            REQUEST_SECURITY_DEFAULTS.atmDeposit().capacity(), 1, 1000);

    private static final ForgeConfigSpec.IntValue REQUEST_SECURITY_ATM_DEPOSIT_REFILL_TOKENS = BUILDER
        .comment("Reserved ATM deposit tokens restored each refill period. This value cannot exceed capacity.")
        .defineInRange("request_security.atm_deposit.refill_tokens",
            REQUEST_SECURITY_DEFAULTS.atmDeposit().refillTokens(), 1, 1000);

    private static final ForgeConfigSpec.LongValue REQUEST_SECURITY_ATM_DEPOSIT_REFILL_PERIOD_MILLIS = BUILDER
        .comment("Reserved ATM deposit token refill period in milliseconds.")
        .defineInRange("request_security.atm_deposit.refill_period_millis",
            REQUEST_SECURITY_DEFAULTS.atmDeposit().refillPeriod().toMillis(),
            50L, 3600000L);

    private static final ForgeConfigSpec.IntValue REQUEST_SECURITY_PAY_CAPACITY = BUILDER
        .comment("Player payment request burst capacity per player.")
        .defineInRange("request_security.pay.capacity",
            REQUEST_SECURITY_DEFAULTS.pay().capacity(), 1, 1000);

    private static final ForgeConfigSpec.IntValue REQUEST_SECURITY_PAY_REFILL_TOKENS = BUILDER
        .comment("Player payment tokens restored each refill period. This value cannot exceed capacity.")
        .defineInRange("request_security.pay.refill_tokens",
            REQUEST_SECURITY_DEFAULTS.pay().refillTokens(), 1, 1000);

    private static final ForgeConfigSpec.LongValue REQUEST_SECURITY_PAY_REFILL_PERIOD_MILLIS = BUILDER
        .comment("Player payment token refill period in milliseconds.")
        .defineInRange("request_security.pay.refill_period_millis",
            REQUEST_SECURITY_DEFAULTS.pay().refillPeriod().toMillis(),
            50L, 3600000L);

    private static final ForgeConfigSpec.ConfigValue<String> CURRENCY_PHYSICAL_REFUND_POLICY = BUILDER
        .comment(
            Config.FOREIGN_CURRENCY_WARNING,
            "Physical refund policy for long lived bids and orders. Allowed values are wallet_claim and original_source."
        )
        .define("currency.physical_refund_policy", "wallet_claim",
            value -> ConfigValidation.isOption(value, REFUND_POLICIES));

    private static final ForgeConfigSpec.ConfigValue<String> AUDIT_DETAIL = BUILDER
        .comment("Audit detail level. Allowed values are minimal, standard, and verbose.")
        .define("audit.detail", "standard", value -> ConfigValidation.isOption(value, AUDIT_LEVELS));

    private static final ForgeConfigSpec.BooleanValue ADMINISTRATION_REQUIRE_REASON = BUILDER
        .comment("Require a written reason for administrative recovery actions.")
        .define("administration.require_reason", true);

    private static final ForgeConfigSpec.BooleanValue ADMINISTRATION_REQUIRE_CONFIRMATION = BUILDER
        .comment("Require explicit confirmation for administrative refund and settlement actions.")
        .define("administration.require_confirmation", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private static volatile Settings settings = Settings.defaults();

    private EscrowConfig() {
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
            LOGGER.error("Rejected FutureShops escrow configuration. {}", exception.getMessage());
        }
    }

    private static Settings readSettings() {
        return new Settings(
            RECOVERY_WORK_PER_TICK.get(),
            RECOVERY_INITIAL_RETRY_DELAY_TICKS.get(),
            RECOVERY_MAXIMUM_RETRY_DELAY_TICKS.get(),
            RECOVERY_MAX_PENDING_TRANSACTIONS.get(),
            MIGRATION_WALLET_ENTRIES_PER_TICK.get(),
            PERSISTENCE_CHECKPOINT_INTERVAL_SECONDS.get(),
            PERSISTENCE_CHECKPOINT_GENERATION_RETENTION.get(),
            PERSISTENCE_CHECKPOINT_MAXIMUM_JOURNAL_BYTES.get(),
            PERSISTENCE_CHECKPOINT_MAXIMUM_JOURNAL_RECORDS.get(),
            PERSISTENCE_ARCHIVE_RETENTION_DAYS.get(),
            CLAIMS_AUTOMATIC_DELIVERY.get(),
            CLAIMS_MAX_ENTRIES_PER_REQUEST.get(),
            CLAIMS_DELIVERY_WORK_PER_TICK.get(),
            CLAIMS_EXACT_ITEM_DELIVERY_OPERATIONS_PER_TICK.get(),
            ASSETS_MAX_PER_TRANSACTION.get(),
            ASSETS_MAX_NBT_BYTES.get(),
            ASSETS_MAX_TOTAL_NBT_BYTES.get(),
            STORAGE_STRICT_EXTERNAL_STORAGE.get(),
            CURRENCY_PHYSICAL_REFUND_POLICY.get(),
            AUDIT_DETAIL.get(),
            ADMINISTRATION_REQUIRE_REASON.get(),
            ADMINISTRATION_REQUIRE_CONFIRMATION.get(),
            readRequestSecuritySettings()
        );
    }

    private static ServerRequestSecuritySettings readRequestSecuritySettings() {
        return new ServerRequestSecuritySettings(
            REQUEST_SECURITY_TRACKED_KEY_CAP.get(),
            Duration.ofSeconds(REQUEST_SECURITY_IDLE_RETENTION_SECONDS.get()),
            new ServerRequestSecuritySettings.ActionLimit(
                REQUEST_SECURITY_ATM_DATA_CAPACITY.get(),
                REQUEST_SECURITY_ATM_DATA_REFILL_TOKENS.get(),
                Duration.ofMillis(REQUEST_SECURITY_ATM_DATA_REFILL_PERIOD_MILLIS.get())),
            new ServerRequestSecuritySettings.ActionLimit(
                REQUEST_SECURITY_ATM_WITHDRAWAL_CAPACITY.get(),
                REQUEST_SECURITY_ATM_WITHDRAWAL_REFILL_TOKENS.get(),
                Duration.ofMillis(REQUEST_SECURITY_ATM_WITHDRAWAL_REFILL_PERIOD_MILLIS.get())),
            new ServerRequestSecuritySettings.ActionLimit(
                REQUEST_SECURITY_ATM_CASH_COLLECTION_CAPACITY.get(),
                REQUEST_SECURITY_ATM_CASH_COLLECTION_REFILL_TOKENS.get(),
                Duration.ofMillis(REQUEST_SECURITY_ATM_CASH_COLLECTION_REFILL_PERIOD_MILLIS.get())),
            new ServerRequestSecuritySettings.ActionLimit(
                REQUEST_SECURITY_ATM_DEPOSIT_CAPACITY.get(),
                REQUEST_SECURITY_ATM_DEPOSIT_REFILL_TOKENS.get(),
                Duration.ofMillis(REQUEST_SECURITY_ATM_DEPOSIT_REFILL_PERIOD_MILLIS.get())),
            new ServerRequestSecuritySettings.ActionLimit(
                REQUEST_SECURITY_PAY_CAPACITY.get(),
                REQUEST_SECURITY_PAY_REFILL_TOKENS.get(),
                Duration.ofMillis(REQUEST_SECURITY_PAY_REFILL_PERIOD_MILLIS.get()))
        );
    }

    public record Settings(
        int recoveryWorkPerTick,
        int initialRetryDelayTicks,
        int maximumRetryDelayTicks,
        int maximumPendingTransactions,
        int walletMigrationEntriesPerTick,
        int checkpointIntervalSeconds,
        int checkpointGenerationRetention,
        long checkpointMaximumJournalBytes,
        int checkpointMaximumJournalRecords,
        int archiveRetentionDays,
        boolean automaticClaimDelivery,
        int maximumClaimEntriesPerRequest,
        int claimDeliveryWorkPerTick,
        int exactItemDeliveryOperationsPerTick,
        int maximumAssetsPerTransaction,
        int maximumAssetNbtBytes,
        int maximumTotalAssetNbtBytes,
        boolean strictExternalStorage,
        String physicalRefundPolicy,
        String auditDetail,
        boolean requireAdministrativeReason,
        boolean requireAdministrativeConfirmation,
        ServerRequestSecuritySettings requestSecurity
    ) {
        public Settings {
            ConfigValidation.require(recoveryWorkPerTick > 0, "Recovery work per tick must be positive.");
            ConfigValidation.require(initialRetryDelayTicks > 0, "Initial retry delay must be positive.");
            ConfigValidation.require(maximumRetryDelayTicks >= initialRetryDelayTicks,
                "Maximum retry delay must not be less than the initial retry delay.");
            ConfigValidation.require(maximumPendingTransactions > 0,
                "Maximum pending transactions must be positive.");
            ConfigValidation.require(walletMigrationEntriesPerTick > 0,
                "Wallet migration entries per tick must be positive.");
            ConfigValidation.require(checkpointIntervalSeconds > 0, "Checkpoint interval must be positive.");
            ConfigValidation.require(checkpointGenerationRetention >= 2
                    && checkpointGenerationRetention <= 16,
                "Checkpoint generation retention must preserve the current and previous pair.");
            ConfigValidation.require(checkpointMaximumJournalBytes >= 1048576L,
                "Checkpoint journal byte threshold must be at least one MiB.");
            ConfigValidation.require(checkpointMaximumJournalRecords >= 100,
                "Checkpoint journal record threshold must be at least one hundred.");
            ConfigValidation.require(archiveRetentionDays > 0, "Archive retention must be positive.");
            ConfigValidation.require(maximumClaimEntriesPerRequest > 0,
                "Maximum claim entries per request must be positive.");
            ConfigValidation.require(claimDeliveryWorkPerTick > 0,
                "Claim delivery work per tick must be positive.");
            ConfigValidation.require(exactItemDeliveryOperationsPerTick == 1,
                "Exact item delivery operations per tick must be one.");
            ConfigValidation.require(maximumAssetsPerTransaction > 0,
                "Maximum assets per transaction must be positive.");
            ConfigValidation.require(maximumAssetNbtBytes > 0, "Maximum asset NBT bytes must be positive.");
            ConfigValidation.require(maximumTotalAssetNbtBytes >= maximumAssetNbtBytes,
                "Maximum total asset NBT bytes must permit one maximum size asset.");
            physicalRefundPolicy = ConfigValidation.requireOption(
                physicalRefundPolicy, REFUND_POLICIES, "Physical refund policy");
            auditDetail = ConfigValidation.requireOption(auditDetail, AUDIT_LEVELS, "Audit detail");
            requestSecurity = Objects.requireNonNull(
                requestSecurity, "requestSecurity");
        }

        public static Settings defaults() {
            return new Settings(
                64, 20, 1200, 100000, 256, 30, 2, 67108864L, 50000, 365,
                true, 64, 32, 1, 256, 1048576, 8388608,
                true, "wallet_claim", "standard", true, true,
                ServerRequestSecuritySettings.defaults()
            );
        }
    }
}
