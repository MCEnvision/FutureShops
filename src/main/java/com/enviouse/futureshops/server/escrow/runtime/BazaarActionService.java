package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.config.BazaarConfig;
import com.enviouse.futureshops.money.CurrencyManager;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SBazaarCancelPacket;
import com.enviouse.futureshops.network.packets.C2SBazaarOrderPacket;
import com.enviouse.futureshops.network.packets.S2CMarketActionResponsePacket;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayload;
import com.enviouse.futureshops.server.escrow.item.ItemInputMatcher;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryAllocation;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchEntry;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchPlanner;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationPlan;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationToken;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryExecutionResult;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryExecutionStatus;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryMutationIntent;
import com.enviouse.futureshops.server.escrow.item.runtime.ServerPlayerItemInventoryAccess;
import com.enviouse.futureshops.server.market.MarketModuleAccessPolicy;
import com.enviouse.futureshops.server.market.bazaar.BazaarLifecycleCommand;
import com.enviouse.futureshops.server.market.bazaar.BazaarMutation;
import com.enviouse.futureshops.server.market.bazaar.BazaarOperationResult;
import com.enviouse.futureshops.server.market.bazaar.BazaarOperationStatus;
import com.enviouse.futureshops.server.market.bazaar.BazaarOperationType;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderBook;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderBookSnapshot;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderSide;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderType;
import com.enviouse.futureshops.server.market.bazaar.BazaarIds;
import com.enviouse.futureshops.server.market.bazaar.BazaarProduct;
import com.enviouse.futureshops.server.market.bazaar.BazaarRequestReceipt;
import com.enviouse.futureshops.server.market.bazaar.BazaarRuleSnapshot;
import com.enviouse.futureshops.server.market.bazaar.BazaarRuleSnapshotFactory;
import com.enviouse.futureshops.server.market.bazaar.BazaarSavedData;
import com.enviouse.futureshops.server.market.bazaar.BazaarTimeInForce;
import com.enviouse.futureshops.server.market.bazaar.CancelBazaarOrderCommand;
import com.enviouse.futureshops.server.market.bazaar.CreateBazaarOrderCommand;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarBuyFundingEvidence;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarCreateEscrowIntent;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowCommit;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowLedgerAccounts;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowLifecycleEvent;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowLifecyclePlanner;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowLifecycleRepository;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowLifecycleState;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowPaymentSource;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowWalletSnapshot;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarSellItemCustody;
import com.enviouse.futureshops.server.market.control.MarketControlModule;
import com.enviouse.futureshops.server.market.control.MarketControlSavedData;
import com.enviouse.futureshops.server.market.control.MarketModuleControl;
import com.enviouse.futureshops.server.security.ServerRequestRateLimiter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Player-facing Bazaar order actions (plan §9) — the packet-to-escrow bridge. Every mutation is
 * planned by {@link BazaarEscrowLifecyclePlanner} and committed durably through
 * {@link EscrowRuntimeService#commitBazaarEscrowLifecycle}; this service never moves value
 * directly. The order book auto-matches inside {@code create}, so fills and their settlements are
 * embedded in the single durable commit. Idempotency layers: stored book request receipt →
 * lifecycle create intents → WAL coordinator replay; the pre-checks here just short-circuit
 * replays into their original responses (plan §1: at most one economic result per request UUID).
 *
 * <p>Bazaar buy money is WALLET-only in this release. {@code payment.allow_physical} is honored
 * as denied: {@link BazaarBuyFundingEvidence} requires physical funding that already credited the
 * reserve into the wallet ledger, and no bazaar-shaped bridge over
 * {@link EscrowCashDepositService} exists yet.
 * TODO(bazaar-physical): consume inventory cash into escrow at placement (plan §6) by depositing
 * exactly the reserve through {@link EscrowCashDepositService} with a deterministic funding
 * request id, then building {@code BazaarPhysicalFundingEvidence} from the deposit settlement.
 *
 * <p>Lives in {@code server.escrow.runtime} deliberately: wallet reads use the package-private
 * ledger accessor and item custody runs through the exact-item runtime the same way
 * {@link ServerShopSellService} and {@link AuctionActionService} do.
 */
public final class BazaarActionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BazaarActionService.class);
    private static final String MODULE = "bazaar";
    private static final String ACTION_ORDER = "bazaar.order";
    private static final String ACTION_CANCEL = "bazaar.cancel";
    private static final int LIMITER_MAX_KEYS = 4096;
    private static final Duration LIMITER_IDLE_RETENTION = Duration.ofMinutes(10);
    private static final long MILLIS_PER_HOUR = 3_600_000L;

    private static final Object LIMITER_LOCK = new Object();
    private static ServerRequestRateLimiter limiter;
    private static int limiterRatePerSecond;

    private BazaarActionService() {
    }

    // ═══════════════════════════ ORDER (BUY / SELL / INSTANT) ═══════════════════════════

    public static void order(ServerPlayer player, C2SBazaarOrderPacket packet) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(packet, "packet");
        UUID requestId = packet.requestId();
        EscrowRuntimeService runtime = readyRuntime();
        if (runtime == null) {
            respond(player, requestId, "ORDER", "ESCROW_UNAVAILABLE", null, 0L, 0L, "");
            return;
        }
        try {
            // Duplicate packet returns the original result before any gate can change it
            // (plan §15) — replays must not depend on current module state or rate budget.
            if (respondIfReplayed(player, runtime, requestId, "ORDER")) {
                return;
            }
            if (!actionAllowed(player.getServer())) {
                respond(player, requestId, "ORDER", "MODULE_DISABLED", null, 0L, 0L, "");
                return;
            }
            if (routeInvalid(player, packet.routeNonce())) {
                respond(player, requestId, "ORDER", "INVALID_REQUEST", null, 0L, 0L, "route");
                return;
            }
            if (rateLimited(player.getUUID(), ACTION_ORDER)) {
                respond(player, requestId, "ORDER", "RATE_LIMITED", null, 0L, 0L, "");
                return;
            }

            BazaarConfig.Settings settings = BazaarConfig.settings();
            BazaarOrderSide side = parseSide(packet.side());
            BazaarOrderType type = parseType(packet.orderType());
            BazaarTimeInForce timeInForce = parseTimeInForce(packet.timeInForce());
            if (side == null || type == null || timeInForce == null) {
                respond(player, requestId, "ORDER", "INVALID_REQUEST", null, 0L, 0L, "enum");
                return;
            }
            String shapeDenial = shapeDenial(settings.orders(), side, type, timeInForce);
            if (shapeDenial != null) {
                respond(player, requestId, "ORDER", "INVALID_REQUEST", null, 0L, 0L,
                        shapeDenial);
                return;
            }
            BazaarEscrowPaymentSource paymentSource = BazaarEscrowPaymentSource.WALLET;
            if (side == BazaarOrderSide.BUY) {
                paymentSource = parsePaymentSource(packet.paymentSource());
                if (paymentSource == null) {
                    respond(player, requestId, "ORDER", "INVALID_REQUEST", null, 0L, 0L,
                            "payment_source");
                    return;
                }
                if (paymentSource == BazaarEscrowPaymentSource.INVENTORY_CASH) {
                    // Honored as denied until the physical funding bridge exists — never merely
                    // "checked" (plan §6 forbids leaving checked physical money accessible).
                    respond(player, requestId, "ORDER", "PAYMENT_SOURCE_DENIED", null, 0L, 0L,
                            "physical");
                    return;
                }
                if (!settings.payment().allowWallet()) {
                    respond(player, requestId, "ORDER", "PAYMENT_SOURCE_DENIED", null, 0L, 0L,
                            "wallet");
                    return;
                }
            }

            long nowMillis = System.currentTimeMillis();
            long expiresAtMillis;
            try {
                expiresAtMillis = expiresAtMillis(settings.orders(), timeInForce,
                        packet.expirationHours(), nowMillis);
            } catch (IllegalArgumentException | ArithmeticException exception) {
                respond(player, requestId, "ORDER", "INVALID_REQUEST", null, 0L, 0L,
                        "expiration");
                return;
            }

            // New contracts snapshot the CURRENT config (plan §10 reload behavior): push a new
            // effective-rules revision durably when the config changed meaning.
            BazaarOrderBookSnapshot snapshot = synchronizeEffectiveRules(runtime);
            BazaarRuleSnapshot rules = effectiveRules(snapshot).orElse(null);
            if (rules == null) {
                respond(player, requestId, "ORDER", "MODULE_DISABLED", null, 0L, 0L, "rules");
                return;
            }

            // Deterministic identities: retries after a crash re-derive the same tree.
            UUID orderId = derived("bazaar.order.", requestId);
            UUID activationId = derived("bazaar.activation.", requestId);
            CreateBazaarOrderCommand command = new CreateBazaarOrderCommand(requestId, orderId,
                    player.getUUID(), activationId,
                    side == BazaarOrderSide.BUY
                            ? Optional.of(derived("bazaar.hold.", requestId)) : Optional.empty(),
                    side == BazaarOrderSide.SELL
                            ? Optional.of(derived("bazaar.lot.", requestId)) : Optional.empty(),
                    packet.productId(), packet.productVersion(), side, type, timeInForce,
                    packet.limitPriceMinor(), packet.quantity(), nowMillis, expiresAtMillis,
                    rules);

            // Read-only preview on a restored copy: rejections (PRODUCT_MISSING, LIMIT_EXCEEDED,
            // FILL_OR_KILL_UNAVAILABLE, …) respond without journaling and without touching any
            // wallet or inventory. The same command re-runs deterministically at commit time —
            // nothing else can interleave on the server thread.
            BazaarOperationResult preview = BazaarOrderBook.restore(snapshot).create(command);
            if (preview.status() != BazaarOperationStatus.APPLIED) {
                respondFromResult(player, requestId, "ORDER", preview,
                        configRevision(snapshot));
                return;
            }
            if (side == BazaarOrderSide.BUY) {
                buyOrder(player, runtime, command);
            } else {
                sellOrder(player, runtime, command);
            }
            // API events (plan §18 Phase 6), post-commit only: the stored receipt exists iff the
            // durable commit happened, and the replay short-circuit above means this original
            // processing is the only path that reaches here — listeners can never run inside the
            // escrow path or observe the same fill twice.
            postFillEvents(runtime, requestId);
        } catch (RuntimeException exception) {
            LOGGER.error("Bazaar order failed for {}", player.getGameProfile().getName(),
                    exception);
            respond(player, requestId, "ORDER", "RECOVERY_REQUIRED", null, 0L, 0L, "");
        }
    }

    /**
     * BUY: the reserve (limit × quantity + maximum fee) moves wallet → hold inside the single
     * durable Commit; nothing is debited before it and a rejected create debits nothing (the
     * fill-or-kill lifecycle test is the spec for that path).
     */
    private static void buyOrder(ServerPlayer player, EscrowRuntimeService runtime,
                                 CreateBazaarOrderCommand command) {
        UUID requestId = command.requestId();
        long configRevision = command.rules().configRevision();
        BazaarProduct buyProduct = runtime.bazaarProduct(command.productId()).orElse(null);
        if (buyProduct != null && !buyProduct.exactIdentity().isEmpty()) {
            // Symmetric with the sell-side exact-identity gate: while such products cannot be
            // SOLD (no reversible template), accepting BUY orders would lock buyer reserves
            // against a permanently empty sell side — deny both sides until template-SNBT
            // product definitions land.
            respond(player, requestId, "ORDER", "INVALID_REQUEST", null, 0L, configRevision,
                    "exact_identity");
            return;
        }
        long reserve;
        try {
            reserve = BazaarCreateEscrowIntent.initialReserve(command);
        } catch (ArithmeticException exception) {
            respond(player, requestId, "ORDER", "ARITHMETIC_OVERFLOW", null, 0L,
                    configRevision, "");
            return;
        }
        long walletMinor;
        long configurationGeneration;
        try (CurrencyManager.ConfigurationReadLease lease =
                     CurrencyManager.acquireConfigurationReadLease()) {
            walletMinor = runtime.ledgerBalance(
                    BazaarEscrowLedgerAccounts.wallet(player.getUUID()));
            configurationGeneration = lease.generation();
        }
        if (walletMinor < reserve) {
            respond(player, requestId, "ORDER", "INSUFFICIENT_FUNDS", null, 0L,
                    configRevision, "");
            return;
        }
        BazaarBuyFundingEvidence funding = new BazaarBuyFundingEvidence(requestId,
                command.orderId(), command.ownerId(),
                command.moneyHoldAccountId().orElseThrow(),
                BazaarEscrowPaymentSource.WALLET,
                new BazaarEscrowWalletSnapshot(command.ownerId(), walletMinor,
                        configurationGeneration),
                Optional.empty(), currencyId());
        Instant now = Instant.ofEpochMilli(command.createdAtMillis());
        BazaarCreateEscrowIntent intent = BazaarEscrowLifecyclePlanner.prepareBuy(command,
                funding, now);
        BazaarEscrowLifecycleRepository.ApplyResult prepared =
                runtime.commitBazaarEscrowLifecycle(
                        new BazaarEscrowLifecycleEvent.Prepare(intent));
        try {
            BazaarEscrowLifecyclePlanner.PlannedCreate planned =
                    BazaarEscrowLifecyclePlanner.commitCreate(prepared.orderBook(),
                            prepared.lifecycle(), intent, Optional.empty(), now);
            runtime.commitBazaarEscrowLifecycle(new BazaarEscrowLifecycleEvent.Commit(
                    Optional.of(planned.terminalIntent()), planned.commit()));
            respondFromCommit(player, requestId, "ORDER", planned.commit());
        } catch (RuntimeException exception) {
            LOGGER.error("Bazaar buy order {} failed after prepare", requestId, exception);
            // No value has moved for a buy until the Commit event lands — abort the durable
            // intent so recovery does not try to finish it. If the abort loses the race with a
            // commit that already landed, the retry replays the stored result.
            abortQuietly(runtime, intent);
            respond(player, requestId, "ORDER", "RECOVERY_REQUIRED", null, 0L,
                    configRevision, "");
        }
    }

    /**
     * SELL: real exact-stack custody out of the live player inventory (plan §9 seller custody).
     * Order of durability: bazaar Prepare intent → prepared item mutation → live extraction →
     * committed item mutation → bazaar Commit. The prepared item intent is appended first so the
     * exact-item runtime replays OUR planned receipt byte-for-byte — the lifecycle planner
     * requires the committed receipt to equal the one snapshotted inside the intent.
     */
    private static void sellOrder(ServerPlayer player, EscrowRuntimeService runtime,
                                  CreateBazaarOrderCommand command) {
        UUID requestId = command.requestId();
        long configRevision = command.rules().configRevision();
        BazaarProduct product = runtime.bazaarProduct(command.productId()).orElse(null);
        if (product == null || product.version() != command.productVersion()) {
            // The preview already vetted this; fail closed if the world changed under us.
            respond(player, requestId, "ORDER", "PRODUCT_MISSING", null, 0L,
                    configRevision, "");
            return;
        }
        if (!product.exactIdentity().isEmpty()) {
            // TODO(bazaar-exact-products): BazaarSellItemCustody validates exactIdentity as a
            // sha256 of the canonical one-count template, which cannot be reversed into the
            // extraction template this path needs. Exact-identity products stay sell-disabled
            // until the product definition carries the template SNBT.
            respond(player, requestId, "ORDER", "INVALID_REQUEST", null, 0L, configRevision,
                    "exact_identity");
            return;
        }
        Item item = registryItem(product.registryId());
        if (item == null) {
            respond(player, requestId, "ORDER", "PRODUCT_UNAVAILABLE", null, 0L,
                    configRevision, "");
            return;
        }
        if (!runtime.itemInventoryMutationGateway()
                .preparedForPlayer(player.getUUID(), 1).isEmpty()) {
            // An unresolved exact-item mutation owns this inventory; recovery must finish first.
            respond(player, requestId, "ORDER", "RECOVERY_REQUIRED", null, 0L,
                    configRevision, "");
            return;
        }

        // Commodity identity: a tagless one-count template. Damaged / named / enchanted stacks
        // carry NBT and are excluded by exact matching (plan §9: commodity default accepts
        // tagless and undamaged stacks only).
        ItemStack template = new ItemStack(item, 1);
        ServerPlayerItemInventoryAccess access = new ServerPlayerItemInventoryAccess(player);
        List<ItemInventoryBatchEntry> entries = List.of(ItemInventoryBatchEntry.extract(
                derived("bazaar.entry.", requestId), ItemInputMatcher.exact(template),
                command.quantity()));
        ItemInventoryMutationPlan plan;
        try {
            plan = ItemInventoryBatchPlanner.plan(access.capture(), entries);
        } catch (RuntimeException exception) {
            respond(player, requestId, "ORDER", "ITEMS_MISSING", null, 0L, configRevision, "");
            return;
        }
        if (!plan.applicable()) {
            respond(player, requestId, "ORDER", "ITEMS_MISSING", null, 0L, configRevision, "");
            return;
        }
        UUID activationId = command.activationTransactionId();
        UUID custodyRequestId = derived("bazaar.custody.request.", requestId);
        Instant now = Instant.ofEpochMilli(command.createdAtMillis());
        ItemInventoryMutationToken token = ItemInventoryMutationToken.create(player.getUUID(),
                activationId, custodyRequestId, plan);
        ItemInventoryMutationReceipt plannedReceipt = ItemInventoryMutationReceipt.create(
                token, plan, now);
        ItemInventoryMutationIntent itemIntent = ItemInventoryMutationIntent.create(token,
                plan, plannedReceipt);

        byte[] templateBytes = ItemStackSnapshotCodec.encode(template);
        List<ItemInventoryAllocation> portions = plannedReceipt.actualPortions();
        List<ExactItemClaimPayload> exactItems = new ArrayList<>(portions.size());
        for (int index = 0; index < portions.size(); index++) {
            ItemInventoryAllocation portion = portions.get(index);
            exactItems.add(ExactItemClaimPayload.preserveRaw(activationId,
                    BazaarSellItemCustody.sourceKey(command.orderId()), index,
                    portions.size(), product.registryId(), portion.count(), templateBytes,
                    portion.actualStackSnapshot()));
        }
        BazaarSellItemCustody custody = BazaarSellItemCustody.create(command, product,
                plannedReceipt, exactItems);
        BazaarCreateEscrowIntent intent = BazaarEscrowLifecyclePlanner.prepareSell(command,
                product, itemIntent, custody, currencyId(), now);
        BazaarEscrowLifecycleRepository.ApplyResult prepared =
                runtime.commitBazaarEscrowLifecycle(
                        new BazaarEscrowLifecycleEvent.Prepare(intent));

        try {
            runtime.itemInventoryMutationGateway().appendPreparedDurably(itemIntent);
        } catch (RuntimeException exception) {
            LOGGER.error("Bazaar sell order {} could not journal its item intent", requestId,
                    exception);
            // Nothing left the inventory — abort the bazaar intent; the player keeps the items.
            abortQuietly(runtime, intent);
            respond(player, requestId, "ORDER", "RECOVERY_REQUIRED", null, 0L,
                    configRevision, "");
            return;
        }
        ItemInventoryExecutionResult executed;
        try {
            executed = runtime.exactItemInventoryRuntime().execute(access, activationId,
                    custodyRequestId, entries);
        } catch (RuntimeException exception) {
            LOGGER.error("Bazaar sell order {} extraction failed", requestId, exception);
            respond(player, requestId, "ORDER", "RECOVERY_REQUIRED", null, 0L,
                    configRevision, "");
            return;
        }
        boolean extracted = (executed.status() == ItemInventoryExecutionStatus.APPLIED
                || executed.status() == ItemInventoryExecutionStatus.REPLAYED)
                && executed.receipt().filter(plannedReceipt::equals).isPresent();
        if (!extracted) {
            if (executed.status() == ItemInventoryExecutionStatus.INSUFFICIENT_ITEMS
                    || executed.status() == ItemInventoryExecutionStatus.INSUFFICIENT_CAPACITY
                    || executed.status() == ItemInventoryExecutionStatus.UNSUPPORTED_STACK
                    || executed.status() == ItemInventoryExecutionStatus.ABORTED) {
                // The item journal terminally refused the mutation — nothing left the
                // inventory, so the durable create intent aborts cleanly.
                abortQuietly(runtime, intent);
                respond(player, requestId, "ORDER", "ITEMS_MISSING", null, 0L,
                        configRevision, "");
                return;
            }
            // RECOVERY_REQUIRED / MANUAL_REVIEW / mismatched receipt: custody is undecided.
            // The prepared intent stays for the recovery scheduler — never guess here.
            respond(player, requestId, "ORDER", "RECOVERY_REQUIRED", null, 0L,
                    configRevision, "");
            return;
        }
        try {
            BazaarEscrowLifecyclePlanner.PlannedCreate planned =
                    BazaarEscrowLifecyclePlanner.commitCreate(prepared.orderBook(),
                            prepared.lifecycle(), intent, executed.receipt(), now);
            runtime.commitBazaarEscrowLifecycle(new BazaarEscrowLifecycleEvent.Commit(
                    Optional.of(planned.terminalIntent()), planned.commit()));
            respondFromCommit(player, requestId, "ORDER", planned.commit());
        } catch (RuntimeException exception) {
            LOGGER.error("Bazaar sell order {} failed after custody", requestId, exception);
            // Items are durably in custody with a durable PREPARED intent — recovery completes
            // (or refunds) this create; aborting here would strand the extracted stacks.
            respond(player, requestId, "ORDER", "RECOVERY_REQUIRED", null, 0L,
                    configRevision, "");
        }
    }

    // ═══════════════════════════ CANCEL ═══════════════════════════

    public static void cancel(ServerPlayer player, C2SBazaarCancelPacket packet) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(packet, "packet");
        UUID requestId = packet.requestId();
        EscrowRuntimeService runtime = readyRuntime();
        if (runtime == null) {
            respond(player, requestId, "CANCEL", "ESCROW_UNAVAILABLE", null, 0L, 0L, "");
            return;
        }
        // Cancellation stays available while the module is disabled (plan §1/§11) — no module
        // gate. Rate limiting still applies (plan §9 cancellation rate limits).
        try {
            if (respondIfReplayed(player, runtime, requestId, "CANCEL")) {
                return;
            }
            if (routeInvalid(player, packet.routeNonce())) {
                respond(player, requestId, "CANCEL", "INVALID_REQUEST", null, 0L, 0L, "route");
                return;
            }
            if (rateLimited(player.getUUID(), ACTION_CANCEL)) {
                respond(player, requestId, "CANCEL", "RATE_LIMITED", null, 0L, 0L, "");
                return;
            }
            MinecraftServer server = player.getServer();
            if (server == null) {
                respond(player, requestId, "CANCEL", "ESCROW_UNAVAILABLE", null, 0L, 0L, "");
                return;
            }
            long nowMillis = System.currentTimeMillis();
            CancelBazaarOrderCommand command = new CancelBazaarOrderCommand(requestId,
                    packet.orderId(), player.getUUID(),
                    BazaarIds.terminal(requestId, packet.orderId(), BazaarOperationType.CANCEL),
                    packet.expectedRevision(), nowMillis);
            BazaarOrderBookSnapshot snapshot = runtime.bazaarSnapshot();
            // Read-only preview: ORDER_MISSING / OWNER_MISMATCH / REVISION_CHANGED /
            // ORDER_NOT_OPEN respond without a durable write.
            BazaarOperationResult preview = BazaarOrderBook.restore(snapshot).cancel(command);
            if (preview.status() != BazaarOperationStatus.APPLIED) {
                respondFromResult(player, requestId, "CANCEL", preview,
                        configRevision(snapshot));
                return;
            }
            BazaarEscrowLifecycleState lifecycle =
                    BazaarSavedData.get(server).escrowLifecycleSnapshot();
            BazaarEscrowCommit commit = BazaarEscrowLifecyclePlanner.cancel(snapshot, lifecycle,
                    command, currencyId(), Instant.ofEpochMilli(nowMillis));
            runtime.commitBazaarEscrowLifecycle(new BazaarEscrowLifecycleEvent.Commit(
                    Optional.empty(), commit));
            respondFromCommit(player, requestId, "CANCEL", commit);
        } catch (RuntimeException exception) {
            LOGGER.error("Bazaar cancel failed for {}", player.getGameProfile().getName(),
                    exception);
            respond(player, requestId, "CANCEL", "RECOVERY_REQUIRED", null, 0L, 0L, "");
        }
    }

    // ═══════════════════════════ shared plumbing ═══════════════════════════

    static EscrowRuntimeService readyRuntime() {
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (runtime == null || runtime.state() != EscrowRuntimeState.READY) {
            return null;
        }
        return runtime;
    }

    /** Module gate for NEW value operations; cancellation and claims never pass through it. */
    static boolean actionAllowed(MinecraftServer server) {
        if (server == null) {
            return false;
        }
        Optional<MarketModuleControl> control;
        try {
            control = Optional.of(MarketControlSavedData.get(server).snapshot()
                    .module(MarketControlModule.BAZAAR));
        } catch (RuntimeException exception) {
            control = Optional.empty();
        }
        return MarketModuleAccessPolicy.capability(MarketModule.BAZAAR,
                Config.bazaarEnabled(), true, control, 0L).allowsNewValueOperations();
    }

    /**
     * Pushes a {@code SET_EFFECTIVE_RULES} lifecycle mutation when the configuration no longer
     * means what the book's effective snapshot says (or the book has none yet). Existing orders
     * keep their own snapshots — only NEW contracts see the new revision (plan §10). Called from
     * the order path and usable from server-start wiring.
     */
    public static BazaarOrderBookSnapshot synchronizeEffectiveRules(
            EscrowRuntimeService runtime) {
        Objects.requireNonNull(runtime, "runtime");
        BazaarOrderBookSnapshot snapshot = runtime.bazaarSnapshot();
        Optional<BazaarRuleSnapshot> effective = effectiveRules(snapshot);
        BazaarRuleSnapshot next = BazaarRuleSnapshotFactory.nextEffective(
                BazaarConfig.settings(), effective);
        if (effective.filter(next::equals).isPresent()) {
            return snapshot;
        }
        BazaarMutation mutation = BazaarMutation.lifecycle(snapshot,
                BazaarLifecycleCommand.setEffectiveRules(UUID.randomUUID(), next));
        return runtime.commitBazaarMutation(mutation).snapshot();
    }

    static Optional<BazaarRuleSnapshot> effectiveRules(BazaarOrderBookSnapshot snapshot) {
        long revision = snapshot.effectiveRuleRevision();
        return snapshot.ruleSnapshots().stream()
                .filter(rules -> rules.configRevision() == revision)
                .findFirst();
    }

    /**
     * Persistent-order expiry deadline in epoch millis; {@code 0} for every non-GOOD_UNTIL_TIME
     * time in force. Zero requested hours means the configured default; anything beyond the
     * configured maximum fails closed.
     */
    static long expiresAtMillis(BazaarConfig.OrderRules orders, BazaarTimeInForce timeInForce,
                                long requestedHours, long nowMillis) {
        if (timeInForce != BazaarTimeInForce.GOOD_UNTIL_TIME) {
            if (requestedHours != 0L) {
                throw new IllegalArgumentException(
                        "Bazaar expiration requires GOOD_UNTIL_TIME");
            }
            return 0L;
        }
        long hours = requestedHours == 0L ? orders.defaultExpirationHours() : requestedHours;
        if (hours <= 0L || hours > orders.maximumExpirationHours()) {
            throw new IllegalArgumentException("Bazaar expiration is out of bounds");
        }
        return Math.addExact(nowMillis, Math.multiplyExact(hours, MILLIS_PER_HOUR));
    }

    /** Returns the machine denial token for a disallowed order shape, or null when allowed. */
    static String shapeDenial(BazaarConfig.OrderRules orders, BazaarOrderSide side,
                              BazaarOrderType type, BazaarTimeInForce timeInForce) {
        if (side == BazaarOrderSide.BUY && !orders.allowBuy()
                || side == BazaarOrderSide.SELL && !orders.allowSell()) {
            return "side";
        }
        if (type == BazaarOrderType.LIMIT && !orders.allowLimit()
                || type == BazaarOrderType.INSTANT && !orders.allowInstant()) {
            return "type";
        }
        if (type == BazaarOrderType.INSTANT && timeInForce.rests()) {
            return "time_in_force";
        }
        // The IOC/FOK config flags govern LIMIT orders only. An INSTANT order's non-resting
        // TIF is mechanical (plan §9 ships instant buy/sell in the initial release) — gating it
        // behind allow_immediate_or_cancel (default false) made every instant trade dead on
        // arrival while the UI always sends INSTANT + IMMEDIATE_OR_CANCEL.
        if (type == BazaarOrderType.LIMIT
                && (timeInForce == BazaarTimeInForce.IMMEDIATE_OR_CANCEL
                && !orders.allowImmediateOrCancel()
                || timeInForce == BazaarTimeInForce.FILL_OR_KILL
                && !orders.allowFillOrKill())) {
            return "time_in_force";
        }
        return null;
    }

    static BazaarOrderSide parseSide(String wire) {
        return switch (normalized(wire)) {
            case "buy" -> BazaarOrderSide.BUY;
            case "sell" -> BazaarOrderSide.SELL;
            default -> null;
        };
    }

    static BazaarOrderType parseType(String wire) {
        return switch (normalized(wire)) {
            case "limit" -> BazaarOrderType.LIMIT;
            case "instant" -> BazaarOrderType.INSTANT;
            default -> null;
        };
    }

    static BazaarTimeInForce parseTimeInForce(String wire) {
        return switch (normalized(wire)) {
            case "good_until_cancelled" -> BazaarTimeInForce.GOOD_UNTIL_CANCELLED;
            case "good_until_time" -> BazaarTimeInForce.GOOD_UNTIL_TIME;
            case "immediate_or_cancel" -> BazaarTimeInForce.IMMEDIATE_OR_CANCEL;
            case "fill_or_kill" -> BazaarTimeInForce.FILL_OR_KILL;
            default -> null;
        };
    }

    static BazaarEscrowPaymentSource parsePaymentSource(String wire) {
        return switch (normalized(wire)) {
            case "wallet" -> BazaarEscrowPaymentSource.WALLET;
            case "physical", "inventory_cash" -> BazaarEscrowPaymentSource.INVENTORY_CASH;
            default -> null;
        };
    }

    private static String normalized(String wire) {
        return wire == null ? "" : wire.strip().toLowerCase(Locale.ROOT);
    }

    private static Item registryItem(String registryId) {
        ResourceLocation key = ResourceLocation.tryParse(registryId);
        Item item = key == null ? null : ForgeRegistries.ITEMS.getValue(key);
        return item == null || item == Items.AIR ? null : item;
    }

    /**
     * Replays: a stored book receipt IS the original result; an unresolved create intent means
     * a crash window the recovery scheduler owns; an aborted intent means the original attempt
     * released everything before committing.
     */
    static boolean respondIfReplayed(ServerPlayer player, EscrowRuntimeService runtime,
                                     UUID requestId, String action) {
        Optional<BazaarRequestReceipt> receipt = runtime.bazaarReceipt(requestId);
        if (receipt.isPresent()) {
            respondFromResult(player, requestId, action, receipt.orElseThrow().result(),
                    configRevision(runtime.bazaarSnapshot()));
            return true;
        }
        Optional<BazaarCreateEscrowIntent> intent = runtime.bazaarCreateIntent(requestId);
        if (intent.isPresent()) {
            BazaarCreateEscrowIntent.Status status = intent.orElseThrow().status();
            if (status == BazaarCreateEscrowIntent.Status.ABORTED) {
                respond(player, requestId, action, "ITEMS_MISSING", null, 0L, 0L,
                        intent.orElseThrow().orderId().toString());
            } else {
                respond(player, requestId, action, "RECOVERY_REQUIRED", null, 0L, 0L,
                        intent.orElseThrow().orderId().toString());
            }
            return true;
        }
        return false;
    }

    private static void abortQuietly(EscrowRuntimeService runtime,
                                     BazaarCreateEscrowIntent intent) {
        try {
            runtime.commitBazaarEscrowLifecycle(new BazaarEscrowLifecycleEvent.Resolve(intent,
                    intent.transition(BazaarCreateEscrowIntent.Status.ABORTED)));
        } catch (RuntimeException exception) {
            LOGGER.error("Bazaar create intent {} could not abort; recovery will resolve it",
                    intent.requestId(), exception);
        }
    }

    private static void respondFromCommit(ServerPlayer player, UUID requestId, String action,
                                          BazaarEscrowCommit commit) {
        BazaarOperationResult result = commit.bookMutation().requestReceipt()
                .orElseThrow().result();
        UUID transactionId = commit.completedTransactions().isEmpty() ? null
                : commit.completedTransactions().get(0).transactionId().value();
        long configRevision = result.order()
                .map(order -> order.rules().configRevision()).orElse(0L);
        respond(player, requestId, action, result.status().name(), transactionId,
                result.observedRevision(), configRevision, commit.orderId().toString());
    }

    private static void respondFromResult(ServerPlayer player, UUID requestId, String action,
                                          BazaarOperationResult result, long configRevision) {
        respond(player, requestId, action, result.status().name(), null,
                result.observedRevision(), configRevision, result.orderId().toString());
    }

    static void respond(ServerPlayer player, UUID requestId, String action, String status,
                        UUID transactionId, long revision, long configRevision, String detail) {
        ShopPackets.sendToPlayer(player, new S2CMarketActionResponsePacket(requestId,
                transactionId == null ? S2CMarketActionResponsePacket.NO_TRANSACTION
                        : transactionId,
                MODULE, action, status, Math.max(0L, revision), Math.max(0L, configRevision),
                System.currentTimeMillis(), detail == null ? "" : detail));
    }

    static long configRevision(BazaarOrderBookSnapshot snapshot) {
        return Math.max(0L, snapshot.effectiveRuleRevision());
    }

    static String currencyId() {
        return "futureshops:wallet";
    }

    static UUID derived(String prefix, UUID requestId) {
        return UUID.nameUUIDFromBytes(
                (prefix + requestId).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Per-player token buckets for {@code matching.maximum_order_requests_per_second}. The
     * limiter is rebuilt when the configured rate changes (bucket policies are immutable).
     */
    /** Plan §12: a mutation's route nonce must match the player's current open market route. */
    static boolean routeInvalid(ServerPlayer player, UUID routeNonce) {
        return !com.enviouse.futureshops.server.market.MarketModuleService
                .routeValid(player.getUUID(), routeNonce);
    }

    private static void postFillEvents(EscrowRuntimeService runtime, UUID requestId) {
        try {
            runtime.bazaarReceipt(requestId)
                    .map(receipt -> receipt.result())
                    .filter(result -> result.durablyApplied())
                    .ifPresent(result -> result.fills().forEach(fill ->
                            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                                    new com.enviouse.futureshops.event.BazaarFillEvent(
                                            fill.fillId(), fill.productId(),
                                            fill.productVersion(), fill.quantity(),
                                            fill.priceMinor(), fill.buyOrderId(),
                                            fill.sellOrderId()))));
        } catch (RuntimeException exception) {
            LOGGER.error("Bazaar fill event listeners failed for {}", requestId, exception);
        }
    }

    static boolean rateLimited(UUID playerId, String action) {
        int rate = BazaarConfig.settings().matching().maximumOrderRequestsPerSecond();
        synchronized (LIMITER_LOCK) {
            if (limiter == null || limiterRatePerSecond != rate) {
                ServerRequestRateLimiter.BucketPolicy policy =
                        new ServerRequestRateLimiter.BucketPolicy(rate, rate,
                                Duration.ofSeconds(1));
                limiter = new ServerRequestRateLimiter(
                        Map.of(ACTION_ORDER, policy, ACTION_CANCEL, policy),
                        LIMITER_MAX_KEYS, LIMITER_IDLE_RETENTION, System::nanoTime);
                limiterRatePerSecond = rate;
            }
            return !limiter.tryAcquire(playerId, action).allowed();
        }
    }
}
