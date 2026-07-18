package com.enviouse.futureshops.command;

import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAction;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAuditSavedData;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeRecord;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairTarget;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import com.enviouse.futureshops.server.escrow.runtime.AuctionExpirationScheduler;
import com.enviouse.futureshops.server.escrow.runtime.BazaarExpirationScheduler;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeManager;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeService;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeState;
import com.enviouse.futureshops.server.escrow.runtime.LiveAdministrativeBalanceBackend;
import com.enviouse.futureshops.server.market.auction.AuctionHouseBook;
import com.enviouse.futureshops.server.market.auction.AuctionHouseSnapshot;
import com.enviouse.futureshops.server.market.auction.AuctionListing;
import com.enviouse.futureshops.server.market.auction.AuctionListingState;
import com.enviouse.futureshops.server.market.auction.AuctionOperationResult;
import com.enviouse.futureshops.server.market.auction.AuctionOperationType;
import com.enviouse.futureshops.server.market.auction.CancelAuctionCommand;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionCreateEscrowIntent;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowCommit;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowItemCustody;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLifecycleEvent;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLifecyclePlanner;
import com.enviouse.futureshops.server.market.bazaar.BazaarLifecycleCommand;
import com.enviouse.futureshops.server.market.bazaar.BazaarMutation;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrder;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderState;
import com.enviouse.futureshops.server.market.bazaar.BazaarProduct;
import com.enviouse.futureshops.server.market.bazaar.BazaarProductStatus;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarCreateEscrowIntent;
import com.enviouse.futureshops.server.market.MarketPermissions;
import com.enviouse.futureshops.server.market.control.MarketControlActor;
import com.enviouse.futureshops.server.market.control.MarketControlCommitResult;
import com.enviouse.futureshops.server.market.control.MarketControlModule;
import com.enviouse.futureshops.server.market.control.MarketControlSavedData;
import com.enviouse.futureshops.server.market.control.MarketControlTransitionCommand;
import com.enviouse.futureshops.server.market.control.MarketModuleControl;
import com.enviouse.futureshops.server.market.control.MarketModuleStatus;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers the {@code /marketadmin} (alias {@code /madmin}) command tree — the plan §13
 * administrative surface for the market modules (shop / bazaar / auction house).
 *
 * <p>Permission ladder (operator-level fallback per §13):
 * <ul>
 *   <li>level 2 — read-only: {@code status}, {@code audit}, {@code recovery}</li>
 *   <li>level 3 — mutations: {@code freeze} / {@code resume} / {@code disable} / {@code enable},
 *       {@code sweep}, {@code bazaar product}</li>
 *   <li>level 4 — forced value operations: {@code auction cancel} (explicit two-step confirm,
 *       written reason, immutable audit record, idempotent request id)</li>
 * </ul>
 *
 * <p>The tree registers unconditionally — commands remain registered while modules are disabled
 * (§13); the module state only affects what the backends accept.
 *
 * <p>Design decisions (documented for reviewers):
 * <ul>
 *   <li><b>disable/enable mapping</b> — the market-control model has no DISABLED status
 *       ({@link MarketModuleStatus}: ENABLED / FROZEN / DRAINING / CANCEL_AND_REFUND).
 *       {@code disable} maps to DRAINING (plan §11 "Drain": no new value operations, existing
 *       value operations settle out) and {@code enable} / {@code resume} both map to ENABLED.
 *       CANCEL_AND_REFUND requires a composite cancellation plan the runtime refuses to take
 *       standalone, so it is deliberately not command-reachable here.</li>
 * </ul>
 */
public final class MarketAdminCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(MarketAdminCommand.class);

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"));

    /** Wallet currency id — mirrors {@code AuctionActionService.currencyId()} (package-private). */
    private static final String CURRENCY_ID = "futureshops:wallet";

    /** Upper bound on recovery intents listed per kind. */
    private static final int RECOVERY_LIST_LIMIT = 100;


    /** Console / non-player confirm bucket (matches MarketControlActor.SYSTEM_ACTOR_ID). */
    private static final UUID CONSOLE_CONFIRM_KEY = new UUID(0L, 0L);

    /** Armed level-4 confirmations keyed by administrator identity. */
    private static final Map<UUID, Logic.PendingConfirm> PENDING_CONFIRMS =
            new ConcurrentHashMap<>();

    private static final String KEY = "command.futureshops.marketadmin.";
    private static final String KEY_MODULE = KEY + "module.";
    private static final String KEY_MODULE_STATE = KEY + "module_state.";
    private static final String KEY_RUNTIME_STATE = KEY + "runtime_state.";
    private static final String KEY_REASON_PROBLEM = KEY + "control.reason.";
    private static final String KEY_PRODUCT_STATE = KEY + "bazaar.product_state.";

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_MODULES =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    java.util.Arrays.stream(MarketControlModule.values())
                            .map(MarketControlModule::id),
                    builder);

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_BAZAAR_PRODUCTS =
            (ctx, builder) -> {
                try {
                    EscrowRuntimeService runtime = readyRuntime();
                    if (runtime != null) {
                        return SharedSuggestionProvider.suggest(
                                runtime.bazaarSnapshot().products().stream()
                                        .map(BazaarProduct::productId).distinct(),
                                builder);
                    }
                } catch (RuntimeException ignored) {
                    // Suggestions are best-effort; never fail command parsing over them.
                }
                return builder.buildFuture();
            };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_OPEN_AUCTIONS =
            (ctx, builder) -> {
                try {
                    EscrowRuntimeService runtime = readyRuntime();
                    if (runtime != null) {
                        return SharedSuggestionProvider.suggest(
                                runtime.auctionHouseSnapshot().listings().values().stream()
                                        .filter(listing -> Logic.openAuctionListing(
                                                listing.state()))
                                        .map(listing -> listing.listingId().toString()),
                                builder);
                    }
                } catch (RuntimeException ignored) {
                    // Suggestions are best-effort; never fail command parsing over them.
                }
                return builder.buildFuture();
            };

    private MarketAdminCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(tree("marketadmin"));
        dispatcher.register(tree("madmin"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tree(String literal) {
        return Commands.literal(literal)
                .requires(src -> src.hasPermission(2)
                        && MarketPermissions.canAdmin(src))

                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource())))

                .then(controlNode("freeze"))
                .then(controlNode("resume"))
                .then(controlNode("disable"))
                .then(controlNode("enable"))

                .then(Commands.literal("audit")
                        .executes(ctx -> audit(ctx.getSource(),
                                Logic.DEFAULT_AUDIT_RECORDS))
                        .then(Commands.argument("count",
                                        IntegerArgumentType.integer(1, Logic.MAX_AUDIT_RECORDS))
                                .executes(ctx -> audit(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "count")))))

                .then(Commands.literal("recovery")
                        .executes(ctx -> recovery(ctx.getSource())))

                .then(Commands.literal("sweep")
                        .requires(src -> src.hasPermission(3)
                                && MarketPermissions.canEscrowAdmin(src))
                        .executes(ctx -> sweep(ctx.getSource())))

                .then(Commands.literal("auction")
                        .requires(src -> src.hasPermission(4)
                                && MarketPermissions.canAuctionAdmin(src))
                        .then(Commands.literal("cancel")
                                .then(Commands.argument("listingId", UuidArgument.uuid())
                                        .suggests(SUGGEST_OPEN_AUCTIONS)
                                        .then(Commands.argument("reason",
                                                        StringArgumentType.greedyString())
                                                .executes(ctx -> auctionCancel(ctx.getSource(),
                                                        UuidArgument.getUuid(ctx, "listingId"),
                                                        StringArgumentType.getString(
                                                                ctx, "reason")))))))

                .then(Commands.literal("bazaar")
                        .requires(src -> src.hasPermission(3)
                                && MarketPermissions.canBazaarAdmin(src))
                        .then(Commands.literal("product")
                                .then(Commands.argument("productId", StringArgumentType.word())
                                        .suggests(SUGGEST_BAZAAR_PRODUCTS)
                                        .then(productStatusNode("active",
                                                BazaarProductStatus.ACTIVE))
                                        .then(productStatusNode("halted",
                                                BazaarProductStatus.HALTED))
                                        .then(productStatusNode("retired",
                                                BazaarProductStatus.RETIRED)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> controlNode(String verb) {
        return Commands.literal(verb)
                .requires(src -> src.hasPermission(3)
                        && MarketPermissions.canEscrowAdmin(src))
                .then(Commands.argument("module", StringArgumentType.word())
                        .suggests(SUGGEST_MODULES)
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> controlTransition(ctx.getSource(), verb,
                                        StringArgumentType.getString(ctx, "module"),
                                        StringArgumentType.getString(ctx, "reason")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> productStatusNode(
            String literal, BazaarProductStatus status) {
        return Commands.literal(literal)
                .executes(ctx -> bazaarProduct(ctx.getSource(),
                        StringArgumentType.getString(ctx, "productId"), status));
    }

    // ─────────────────────────────── status ───────────────────────────────

    private static int status(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        source.sendSuccess(() -> Component.translatable(KEY + "status.header")
                .withStyle(ChatFormatting.GOLD), false);
        try {
            var state = MarketControlSavedData.get(server).snapshot();
            for (MarketControlModule module : MarketControlModule.values()) {
                MarketModuleControl control = state.module(module);
                Component moduleName = Component.translatable(
                        KEY_MODULE + module.id());
                Component moduleState = Component.translatable(
                        KEY_MODULE_STATE + Logic.statusKeySuffix(control.status()));
                long revision = control.revision();
                String reason = control.reason();
                source.sendSuccess(() -> Component.translatable(
                                KEY + "status.module_line",
                                moduleName, moduleState, revision, reason)
                        .withStyle(ChatFormatting.GRAY), false);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Market control status read failed", exception);
            source.sendFailure(Component.translatable(KEY + "error.internal",
                    String.valueOf(exception.getMessage())).withStyle(ChatFormatting.RED));
        }

        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        EscrowRuntimeState runtimeState = runtime == null
                ? EscrowRuntimeState.STOPPED : runtime.state();
        source.sendSuccess(() -> Component.translatable(KEY + "status.runtime_line",
                        Component.translatable(KEY_RUNTIME_STATE
                                + Logic.statusKeySuffix(runtimeState)))
                .withStyle(ChatFormatting.GRAY), false);

        if (runtime == null || runtimeState != EscrowRuntimeState.READY) {
            source.sendSuccess(() -> Component.translatable(
                            KEY + "status.counts_unavailable")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            return 1;
        }
        try {
            long openAuctions = runtime.auctionHouseSnapshot().listings().values().stream()
                    .filter(listing -> Logic.openAuctionListing(listing.state())).count();
            long openOrders = runtime.bazaarSnapshot().orders().stream()
                    .map(BazaarOrder::state)
                    .filter(Logic::openBazaarOrder).count();
            source.sendSuccess(() -> Component.translatable(KEY + "status.counts_line",
                            openAuctions, openOrders)
                    .withStyle(ChatFormatting.GRAY), false);
            int auctionRecovery = runtime
                    .pendingAuctionCreateRecovery(RECOVERY_LIST_LIMIT).size();
            int bazaarRecovery = runtime
                    .pendingBazaarCreateRecovery(RECOVERY_LIST_LIMIT).size();
            source.sendSuccess(() -> Component.translatable(KEY + "status.recovery_line",
                            auctionRecovery, bazaarRecovery)
                    .withStyle(ChatFormatting.GRAY), false);
            long maintenanceRevision = runtime.maintenanceRevision(
                    MaintenanceRepairTarget.runtime());
            source.sendSuccess(() -> Component.translatable(KEY + "status.maintenance_line",
                            maintenanceRevision)
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        } catch (RuntimeException exception) {
            LOGGER.error("Market status counts failed", exception);
            source.sendSuccess(() -> Component.translatable(
                            KEY + "status.counts_unavailable")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        }
        return 1;
    }

    // ──────────────────── freeze / resume / disable / enable ────────────────────

    private static int controlTransition(CommandSourceStack source, String verb,
                                         String moduleToken, String rawReason) {
        Optional<MarketControlModule> parsed = Logic.parseModule(moduleToken);
        if (parsed.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "control.unknown_module",
                    moduleToken).withStyle(ChatFormatting.RED));
            return 0;
        }
        MarketControlModule module = parsed.orElseThrow();
        MarketModuleStatus target = Logic.targetStatus(verb);

        String reason = Logic.normalizeReason(rawReason);
        Optional<String> problem = Logic.reasonProblem(reason);
        if (problem.isPresent()) {
            source.sendFailure(Component.translatable(
                            KEY_REASON_PROBLEM + problem.orElseThrow(),
                            Logic.MAX_REASON_BYTES)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        EscrowRuntimeService runtime = readyRuntime();
        if (runtime == null) {
            sendRuntimeUnavailable(source);
            return 0;
        }
        try {
            MarketModuleControl current = runtime.marketModuleControl(module);
            if (current.status() == target) {
                source.sendFailure(Component.translatable(KEY + "control.noop",
                                Component.translatable(KEY_MODULE + module.id()),
                                Component.translatable(KEY_MODULE_STATE
                                        + Logic.statusKeySuffix(target)))
                        .withStyle(ChatFormatting.YELLOW));
                return 0;
            }
            long now = System.currentTimeMillis();
            // Deterministic per (module, target, revision): a retry of the same transition
            // replays instead of double-applying; once the revision moves, a new id is derived.
            UUID requestId = Logic.controlRequestId(module, target, current.revision());
            MarketControlTransitionCommand command = new MarketControlTransitionCommand(
                    requestId, module, current.revision(), target,
                    Logic.controlActor(actorId(source), source.getTextName()),
                    reason, now, now, Optional.empty(), Optional.empty());
            MarketControlCommitResult result =
                    runtime.commitMarketControlTransition(command);
            Component moduleName = Component.translatable(KEY_MODULE + module.id());
            Component stateName = Component.translatable(
                    KEY_MODULE_STATE + Logic.statusKeySuffix(target));
            long newRevision = result.auditEntry().moduleRevision();
            if (result.replayed()) {
                source.sendSuccess(() -> Component.translatable(KEY + "control.replayed",
                                moduleName, stateName)
                        .withStyle(ChatFormatting.YELLOW), true);
            } else {
                source.sendSuccess(() -> Component.translatable(KEY + "control.applied",
                                moduleName, stateName, newRevision)
                        .withStyle(ChatFormatting.GREEN), true);
            }
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            // Includes MarketControlConflictException (revision race, unsafe transition,
            // missing CANCEL_AND_REFUND resume evidence, …) — surfaced, never swallowed.
            source.sendFailure(Component.translatable(KEY + "control.rejected",
                            String.valueOf(exception.getMessage()))
                    .withStyle(ChatFormatting.RED));
            return 0;
        } catch (RuntimeException exception) {
            LOGGER.error("Market control transition failed", exception);
            source.sendFailure(Component.translatable(KEY + "error.internal",
                    String.valueOf(exception.getMessage())).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    // ─────────────────────────────── audit ───────────────────────────────

    private static int audit(CommandSourceStack source, int requested) {
        int count = Logic.boundAuditCount(requested);
        List<EscrowAdministrativeRecord> records;
        try {
            records = EscrowAdministrativeAuditSavedData
                    .get(source.getServer()).latest(count);
        } catch (RuntimeException exception) {
            LOGGER.error("Administrative audit read failed", exception);
            source.sendFailure(Component.translatable(KEY + "error.internal",
                    String.valueOf(exception.getMessage())).withStyle(ChatFormatting.RED));
            return 0;
        }
        if (records.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(KEY + "audit.empty")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        int shown = records.size();
        source.sendSuccess(() -> Component.translatable(KEY + "audit.header", shown)
                .withStyle(ChatFormatting.GOLD), false);
        for (EscrowAdministrativeRecord record : records) {
            String timestamp = TS_FMT.format(record.createdAt());
            String action = record.action().name();
            String key = record.successful() ? KEY + "audit.line_ok"
                    : KEY + "audit.line_failed";
            source.sendSuccess(() -> Component.translatable(key,
                            timestamp, action, record.actor(), record.reason(),
                            record.outcome())
                    .withStyle(record.successful()
                            ? ChatFormatting.GRAY : ChatFormatting.RED), false);
        }
        return shown;
    }

    // ─────────────────────────────── recovery ───────────────────────────────

    private static int recovery(CommandSourceStack source) {
        EscrowRuntimeService runtime = readyRuntime();
        if (runtime == null) {
            sendRuntimeUnavailable(source);
            return 0;
        }
        try {
            List<AuctionCreateEscrowIntent> auction =
                    runtime.pendingAuctionCreateRecovery(RECOVERY_LIST_LIMIT);
            List<BazaarCreateEscrowIntent> bazaar =
                    runtime.pendingBazaarCreateRecovery(RECOVERY_LIST_LIMIT);
            if (auction.isEmpty() && bazaar.isEmpty()) {
                source.sendSuccess(() -> Component.translatable(KEY + "recovery.empty")
                        .withStyle(ChatFormatting.GRAY), false);
                return 1;
            }
            int total = auction.size() + bazaar.size();
            source.sendSuccess(() -> Component.translatable(KEY + "recovery.header", total)
                    .withStyle(ChatFormatting.GOLD), false);
            long now = System.currentTimeMillis();
            for (AuctionCreateEscrowIntent intent : auction) {
                recoveryLine(source, KEY + "recovery.kind.auction",
                        intent.requestId(), intent.preparedAt(), now);
            }
            for (BazaarCreateEscrowIntent intent : bazaar) {
                recoveryLine(source, KEY + "recovery.kind.bazaar",
                        intent.requestId(), intent.preparedAt(), now);
            }
            return total;
        } catch (RuntimeException exception) {
            LOGGER.error("Recovery listing failed", exception);
            source.sendFailure(Component.translatable(KEY + "error.internal",
                    String.valueOf(exception.getMessage())).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static void recoveryLine(CommandSourceStack source, String kindKey,
                                     UUID requestId, Instant preparedAt, long nowMillis) {
        long ageSeconds = Logic.ageSeconds(preparedAt, nowMillis);
        source.sendSuccess(() -> Component.translatable(KEY + "recovery.line",
                        Component.translatable(kindKey), requestId.toString(), ageSeconds)
                .withStyle(ChatFormatting.GRAY), false);
    }

    // ─────────────────────────────── sweep ───────────────────────────────

    private static int sweep(CommandSourceStack source) {
        EscrowRuntimeService runtime = readyRuntime();
        if (runtime == null) {
            sendRuntimeUnavailable(source);
            return 0;
        }
        MinecraftServer server = source.getServer();
        try {
            BazaarExpirationScheduler.trigger(server);
            AuctionExpirationScheduler.trigger(server);
            source.sendSuccess(() -> Component.translatable(KEY + "sweep.done")
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (RuntimeException exception) {
            LOGGER.error("Expiration sweep trigger failed", exception);
            source.sendFailure(Component.translatable(KEY + "error.internal",
                    String.valueOf(exception.getMessage())).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    // ─────────────────────────────── auction cancel ───────────────────────────────

    private static int auctionCancel(CommandSourceStack source, UUID listingId,
                                     String rawReason) {
        String reason = Logic.normalizeReason(rawReason);
        Optional<String> problem = Logic.reasonProblem(reason);
        if (problem.isPresent()) {
            source.sendFailure(Component.translatable(
                            KEY_REASON_PROBLEM + problem.orElseThrow(),
                            Logic.MAX_REASON_BYTES)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        EscrowRuntimeService runtime = readyRuntime();
        if (runtime == null) {
            sendRuntimeUnavailable(source);
            return 0;
        }
        try {
            AuctionListing listing = runtime.auctionHouseListing(listingId).orElse(null);
            if (listing == null) {
                source.sendFailure(Component.translatable(KEY + "cancel.not_found",
                        listingId.toString()).withStyle(ChatFormatting.RED));
                return 0;
            }
            // §13 idempotent administration request id: deterministic over the exact listing
            // state being cancelled — a retry replays, a revision change derives a fresh intent.
            UUID requestId = Logic.adminCancelRequestId(listingId, listing.revision());
            if (runtime.auctionEscrowCommit(requestId).isPresent()) {
                source.sendSuccess(() -> Component.translatable(KEY + "cancel.replayed",
                        listingId.toString()).withStyle(ChatFormatting.YELLOW), false);
                return 1;
            }
            if (listing.state().terminal()) {
                source.sendFailure(Component.translatable(KEY + "cancel.terminal",
                                listingId.toString(), listing.state().name())
                        .withStyle(ChatFormatting.RED));
                return 0;
            }

            // §13 explicit confirmation: re-run the exact command within the window to execute.
            long now = System.currentTimeMillis();
            UUID confirmKey = actorId(source);
            String fingerprint = Logic.cancelFingerprint(listingId,
                    listing.revision(), reason);
            Logic.PendingConfirm existing = PENDING_CONFIRMS.get(confirmKey);
            if (Logic.confirmDecision(existing, fingerprint, now)
                    == Logic.ConfirmDecision.ARM) {
                PENDING_CONFIRMS.put(confirmKey,
                        new Logic.PendingConfirm(fingerprint, now));
                source.sendSuccess(() -> Component.translatable(KEY + "cancel.armed",
                                listingId.toString(), listing.sellerId().toString(),
                                listing.state().name(), listing.revision())
                        .withStyle(ChatFormatting.GOLD), false);
                if (listing.highestBid().isPresent()) {
                    source.sendSuccess(() -> Component.translatable(
                                    KEY + "cancel.armed_bid_warning")
                            .withStyle(ChatFormatting.RED), false);
                }
                source.sendSuccess(() -> Component.translatable(KEY + "cancel.armed_hint",
                                Logic.CONFIRM_WINDOW_MILLIS / 1000L)
                        .withStyle(ChatFormatting.GRAY), false);
                return 1;
            }
            PENDING_CONFIRMS.remove(confirmKey);
            return executeAuctionCancel(source, runtime, listing, requestId, reason, now);
        } catch (RuntimeException exception) {
            LOGGER.error("Admin auction cancel failed for {}", listingId, exception);
            source.sendFailure(Component.translatable(KEY + "error.internal",
                    String.valueOf(exception.getMessage())).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int executeAuctionCancel(CommandSourceStack source,
                                            EscrowRuntimeService runtime,
                                            AuctionListing listing, UUID requestId,
                                            String reason, long nowMillis) {
        UUID listingId = listing.listingId();
        AuctionEscrowItemCustody custody = runtime.auctionEscrowLifecycleState()
                .commits().values().stream()
                .filter(commit -> commit.operation() == AuctionOperationType.CREATE)
                .filter(commit -> commit.listingId().equals(listingId))
                .flatMap(commit -> commit.itemCustody().stream())
                .findFirst().orElse(null);
        if (custody == null) {
            source.sendFailure(Component.translatable(KEY + "cancel.no_custody",
                    listingId.toString()).withStyle(ChatFormatting.RED));
            return 0;
        }
        // Actor decision (see class javadoc): the book's cancel path is seller-only, so the
        // command runs as the seller while the administrator is recorded in the audit store.
        CancelAuctionCommand command = new CancelAuctionCommand(requestId, listingId,
                listing.revision(), listing.sellerId(),
                Logic.cancelTerminalTransactionId(requestId), nowMillis,
                true);
        AuctionHouseSnapshot snapshot = runtime.auctionHouseSnapshot();
        AuctionOperationResult preview = new AuctionHouseBook(snapshot).cancel(command);
        if (!preview.applied()) {
            source.sendFailure(Component.translatable(KEY + "cancel.rejected",
                            listingId.toString(), preview.status().name())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        AuctionEscrowCommit commit = AuctionEscrowLifecyclePlanner.cancel(snapshot,
                command, custody, CURRENCY_ID, Instant.ofEpochMilli(nowMillis));
        runtime.commitAuctionEscrowLifecycle(
                new AuctionEscrowLifecycleEvent.Commit(Optional.empty(), commit));

        EscrowTransactionId transactionId = commit.completedTransaction()
                .map(EscrowTransaction::transactionId)
                .orElseGet(() -> new EscrowTransactionId(command.terminalTransactionId()));
        recordCancelAudit(source, requestId, transactionId, listing, reason);
        source.sendSuccess(() -> Component.translatable(KEY + "cancel.done",
                        listingId.toString(), transactionId.toString())
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /**
     * Immutable §13 audit record for the forced cancel — same request id as the escrow commit
     * so the durable journal entry and the audit entry are joinable. Written through the public
     * durable audit path; an id conflict after a retried command means the record already
     * exists and is treated as recorded.
     */
    private static void recordCancelAudit(CommandSourceStack source, UUID requestId,
                                          EscrowTransactionId transactionId,
                                          AuctionListing listing, String reason) {
        try {
            String outcome = "Cancelled auction listing " + listing.listingId()
                    + " at revision " + listing.revision()
                    + "; item custody returned to seller " + listing.sellerId()
                    + " as a claim";
            new LiveAdministrativeBalanceBackend().commitAudit(new EscrowAdministrativeRecord(
                    requestId, auditActor(source), EscrowAdministrativeAction.FORCE_REFUND,
                    Optional.of(transactionId), reason, Instant.now(), true, outcome));
        } catch (RuntimeException exception) {
            // The value operation is already durably committed; never roll it back over the
            // audit line, but do surface the miss loudly.
            LOGGER.error("Audit record write failed for admin auction cancel {}",
                    requestId, exception);
            source.sendFailure(Component.translatable(KEY + "cancel.audit_failed",
                            String.valueOf(exception.getMessage()))
                    .withStyle(ChatFormatting.RED));
        }
    }

    // ─────────────────────────────── bazaar product ───────────────────────────────

    private static int bazaarProduct(CommandSourceStack source, String productId,
                                     BazaarProductStatus target) {
        EscrowRuntimeService runtime = readyRuntime();
        if (runtime == null) {
            sendRuntimeUnavailable(source);
            return 0;
        }
        try {
            String normalizedId = productId.strip().toLowerCase(Locale.ROOT);
            BazaarProduct product = runtime.bazaarProduct(normalizedId).orElse(null);
            if (product == null) {
                source.sendFailure(Component.translatable(KEY + "bazaar.not_found",
                        normalizedId).withStyle(ChatFormatting.RED));
                return 0;
            }
            Component stateName = Component.translatable(
                    KEY_PRODUCT_STATE + Logic.statusKeySuffix(target));
            if (product.status() == target) {
                source.sendFailure(Component.translatable(KEY + "bazaar.noop",
                        normalizedId, stateName).withStyle(ChatFormatting.YELLOW));
                return 0;
            }
            // SET_PRODUCT_STATUS is state-idempotent and the no-op pre-check above prevents
            // pointless commits, so each invocation is a fresh mutation intent.
            BazaarMutation mutation = BazaarMutation.lifecycle(runtime.bazaarSnapshot(),
                    BazaarLifecycleCommand.setProductStatus(UUID.randomUUID(),
                            normalizedId, target));
            runtime.commitBazaarMutation(mutation);
            source.sendSuccess(() -> Component.translatable(KEY + "bazaar.updated",
                    normalizedId, stateName).withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.translatable(KEY + "bazaar.invalid_product",
                            productId, String.valueOf(exception.getMessage()))
                    .withStyle(ChatFormatting.RED));
            return 0;
        } catch (RuntimeException exception) {
            LOGGER.error("Bazaar product status change failed for {}", productId, exception);
            source.sendFailure(Component.translatable(KEY + "error.internal",
                    String.valueOf(exception.getMessage())).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    // ─────────────────────────────── shared plumbing ───────────────────────────────

    /** READY escrow runtime or null — mirrors {@code AuctionActionService.readyRuntime()}. */
    private static EscrowRuntimeService readyRuntime() {
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (runtime == null || runtime.state() != EscrowRuntimeState.READY) {
            return null;
        }
        return runtime;
    }

    private static void sendRuntimeUnavailable(CommandSourceStack source) {
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        EscrowRuntimeState state = runtime == null
                ? EscrowRuntimeState.STOPPED : runtime.state();
        source.sendFailure(Component.translatable(KEY + "runtime_unavailable",
                        Component.translatable(KEY_RUNTIME_STATE
                                + Logic.statusKeySuffix(state)))
                .withStyle(ChatFormatting.RED));
    }

    private static UUID actorId(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player.getUUID();
        }
        return CONSOLE_CONFIRM_KEY;
    }

    /** Audit-store actor string — same idiom as {@code ShopAdminCommand}. */
    private static String auditActor(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return "player " + player.getUUID();
        }
        return "command source " + source.getTextName();
    }

    // ─────────────────────────────── pure decision logic ───────────────────────────────

    /**
     * Pure, Minecraft-free decision logic — unit-tested directly
     * (MarketAdminCommandLogicTest). Loading this nested class does not initialize the outer
     * command class.
     */
    static final class Logic {
        /** Confirmation window for level-4 forced operations. */
        static final long CONFIRM_WINDOW_MILLIS = 30_000L;
        /** Reason bound — mirrors {@link MarketModuleControl#MAX_REASON_BYTES} (UTF-8 bytes). */
        static final int MAX_REASON_BYTES = MarketModuleControl.MAX_REASON_BYTES;
        /** Hard cap on audit records shown per invocation (§13 bounded output). */
        static final int MAX_AUDIT_RECORDS = 50;
        static final int DEFAULT_AUDIT_RECORDS = 10;

        enum ConfirmDecision {
            ARM,
            EXECUTE
        }

        /** An armed level-4 confirmation: exact-command fingerprint plus arm time. */
        record PendingConfirm(String fingerprint, long armedAtMillis) {
        }

        private Logic() {
        }

        /**
         * Two-step confirm: EXECUTE only when an armed confirmation exists, carries the exact
         * same fingerprint, and is inside the window. Everything else (nothing armed, expired,
         * different command, or a clock that ran backwards) re-arms.
         */
        static ConfirmDecision confirmDecision(PendingConfirm existing, String fingerprint,
                                               long nowMillis) {
            if (existing == null || !existing.fingerprint().equals(fingerprint)) {
                return ConfirmDecision.ARM;
            }
            long elapsed = nowMillis - existing.armedAtMillis();
            if (elapsed < 0L || elapsed > CONFIRM_WINDOW_MILLIS) {
                return ConfirmDecision.ARM;
            }
            return ConfirmDecision.EXECUTE;
        }

        static String normalizeReason(String raw) {
            return raw == null ? "" : raw.strip();
        }

        /**
         * Validation problems for a written reason, mirroring the market-control model rules
         * ({@code MarketControlText.require}): non-empty, at most {@link #MAX_REASON_BYTES}
         * UTF-8 bytes, no ISO control characters. Empty optional means the reason is valid.
         * Rejecting (not truncating) keeps the operator's written reason intact end to end.
         */
        static Optional<String> reasonProblem(String normalized) {
            if (normalized == null || normalized.isEmpty()) {
                return Optional.of("empty");
            }
            for (int index = 0; index < normalized.length(); index++) {
                if (Character.isISOControl(normalized.charAt(index))) {
                    return Optional.of("control_character");
                }
            }
            if (normalized.getBytes(StandardCharsets.UTF_8).length > MAX_REASON_BYTES) {
                return Optional.of("too_long");
            }
            return Optional.empty();
        }

        static Optional<MarketControlModule> parseModule(String token) {
            if (token == null) {
                return Optional.empty();
            }
            String normalized = token.strip().toLowerCase(Locale.ROOT);
            for (MarketControlModule module : MarketControlModule.values()) {
                if (module.id().equals(normalized)) {
                    return Optional.of(module);
                }
            }
            return Optional.empty();
        }

        /**
         * Command verb → target status. {@code disable} maps to DRAINING (there is no DISABLED
         * status in the model — see the class javadoc); {@code enable} and {@code resume} both
         * target ENABLED.
         */
        static MarketModuleStatus targetStatus(String verb) {
            return switch (verb) {
                case "freeze" -> MarketModuleStatus.FROZEN;
                case "resume", "enable" -> MarketModuleStatus.ENABLED;
                case "disable" -> MarketModuleStatus.DRAINING;
                default -> throw new IllegalArgumentException(
                        "Unknown market control verb " + verb);
            };
        }

        /** Deterministic control-transition request id (idempotent per module × target × revision). */
        static UUID controlRequestId(MarketControlModule module, MarketModuleStatus target,
                                     long expectedRevision) {
            return UUID.nameUUIDFromBytes(("marketadmin.control." + module.id()
                    + "." + target.name() + "." + expectedRevision)
                    .getBytes(StandardCharsets.UTF_8));
        }

        /** §13 idempotent admin request id: nameUUID over listingId + revision + "admincancel". */
        static UUID adminCancelRequestId(UUID listingId, long revision) {
            return UUID.nameUUIDFromBytes((listingId + ":" + revision + ":admincancel")
                    .getBytes(StandardCharsets.UTF_8));
        }

        /** Terminal transaction id derivation — same convention as the player cancel path. */
        static UUID cancelTerminalTransactionId(UUID requestId) {
            return UUID.nameUUIDFromBytes(("auction.cancel." + requestId)
                    .getBytes(StandardCharsets.UTF_8));
        }

        /** Exact-command fingerprint the two-step confirm compares. */
        static String cancelFingerprint(UUID listingId, long revision, String reason) {
            return listingId + "|" + revision + "|" + reason;
        }

        static int boundAuditCount(int requested) {
            return Math.max(1, Math.min(requested, MAX_AUDIT_RECORDS));
        }

        /** Lang-key suffix for an enum-valued state (stable lowercase name). */
        static String statusKeySuffix(Enum<?> value) {
            return value.name().toLowerCase(Locale.ROOT);
        }

        /** "Open" auction listings for the status counts: everything not yet terminal. */
        static boolean openAuctionListing(AuctionListingState state) {
            return !state.terminal();
        }

        /** "Open" bazaar orders for the status counts: everything not yet terminal. */
        static boolean openBazaarOrder(BazaarOrderState state) {
            return !state.terminal();
        }

        /** Whole seconds between a prepared instant and now, clamped at zero. */
        static long ageSeconds(Instant preparedAt, long nowMillis) {
            return Math.max(0L, (nowMillis - preparedAt.toEpochMilli()) / 1000L);
        }

        /**
         * Market-control actor for a command source: player UUID with the player name as label,
         * or the SYSTEM actor id with the source name for the console. The label is bounded to
         * the model's 64-byte limit and stripped of control characters so command input can
         * never fail the model's own validation.
         */
        static com.enviouse.futureshops.server.market.control.MarketControlActor controlActor(
                UUID actorId, String label) {
            return new com.enviouse.futureshops.server.market.control.MarketControlActor(
                    actorId, actorLabel(label));
        }

        /** Bounded, control-character-free actor label; falls back to {@code "admin"}. */
        static String actorLabel(String raw) {
            String cleaned = raw == null ? "" : raw.strip();
            StringBuilder builder = new StringBuilder(cleaned.length());
            for (int index = 0; index < cleaned.length(); index++) {
                char character = cleaned.charAt(index);
                if (!Character.isISOControl(character)) {
                    builder.append(character);
                }
            }
            String label = builder.toString().strip();
            if (label.isEmpty()) {
                return "admin";
            }
            while (label.getBytes(StandardCharsets.UTF_8).length
                    > com.enviouse.futureshops.server.market.control
                    .MarketControlActor.MAX_LABEL_BYTES) {
                label = label.substring(0, label.length() - 1).strip();
                if (label.isEmpty()) {
                    return "admin";
                }
            }
            return label;
        }
    }
}
