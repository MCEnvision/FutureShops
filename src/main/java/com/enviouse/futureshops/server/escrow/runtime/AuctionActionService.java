package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.config.AuctionHouseConfig;
import com.enviouse.futureshops.money.CurrencyManager;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SAuctionBidPacket;
import com.enviouse.futureshops.network.packets.C2SAuctionBuyNowPacket;
import com.enviouse.futureshops.network.packets.C2SAuctionCancelPacket;
import com.enviouse.futureshops.network.packets.C2SAuctionCreatePacket;
import com.enviouse.futureshops.network.packets.S2CMarketActionResponsePacket;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayload;
import com.enviouse.futureshops.server.escrow.item.ItemInputMatcher;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchEntry;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchPlanner;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationPlan;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationToken;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryState;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryExecutionResult;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryExecutionStatus;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryMutationIntent;
import com.enviouse.futureshops.server.escrow.item.runtime.ServerPlayerItemInventoryAccess;
import com.enviouse.futureshops.server.market.MarketModuleAccessPolicy;
import com.enviouse.futureshops.server.market.auction.AuctionBuyNowCommand;
import com.enviouse.futureshops.server.market.auction.AuctionHouseBook;
import com.enviouse.futureshops.server.market.auction.AuctionHouseSnapshot;
import com.enviouse.futureshops.server.market.auction.AuctionItemLot;
import com.enviouse.futureshops.server.market.auction.AuctionListing;
import com.enviouse.futureshops.server.market.auction.AuctionListingState;
import com.enviouse.futureshops.server.market.auction.AuctionListingType;
import com.enviouse.futureshops.server.market.auction.AuctionOperationResult;
import com.enviouse.futureshops.server.market.auction.AuctionOperationType;
import com.enviouse.futureshops.server.market.auction.AuctionRequestReceipt;
import com.enviouse.futureshops.server.market.auction.AuctionRuleSnapshot;
import com.enviouse.futureshops.server.market.auction.AuctionTimeBasis;
import com.enviouse.futureshops.server.market.auction.CancelAuctionCommand;
import com.enviouse.futureshops.server.market.auction.CreateAuctionCommand;
import com.enviouse.futureshops.server.market.auction.PlaceAuctionBidCommand;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionCreateEscrowIntent;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowCommit;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowIds;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowItemCustody;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLifecycleEvent;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLifecyclePlanner;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowWalletSnapshot;
import com.enviouse.futureshops.server.market.control.MarketControlModule;
import com.enviouse.futureshops.server.market.control.MarketControlSavedData;
import com.enviouse.futureshops.server.market.control.MarketModuleControl;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Player-facing Auction House actions (plan §8) — the packet-to-escrow bridge. Every mutation is
 * planned by {@link AuctionEscrowLifecyclePlanner} and committed durably through
 * {@link EscrowRuntimeService#commitAuctionEscrowLifecycle}; this service never mutates value
 * directly. Idempotency is enforced in four layers (stored escrow commit, book request receipt,
 * lifecycle repository, WAL coordinator) — the pre-checks here just short-circuit replays into
 * their original responses. Auction money is WALLET-only in this release (the escrow layer has no
 * physical funding path for bids; {@code payment.allow_physical} is honored as denied).
 *
 * <p>Lives in {@code server.escrow.runtime} deliberately: item custody runs through the
 * package-private exact-item runtime the same way {@link ServerShopSellService} does.
 */
public final class AuctionActionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuctionActionService.class);
    private static final String MODULE = "auction_house";
    /** Main-inventory slots only (0-35): armor/offhand are not listable surfaces. */
    private static final int MAX_MAIN_SLOT = 35;

    // Per-player token buckets (plan §8 listing and bid rate limits), mirroring the bazaar
    // service. buy-now shares the bid bucket — both compete for the same listing lock.
    private static final String ACTION_CREATE = "auction.create";
    private static final String ACTION_BID = "auction.bid";
    private static final String ACTION_CANCEL = "auction.cancel";
    private static final Object LIMITER_LOCK = new Object();
    private static final int LIMITER_MAX_KEYS = 4096;
    private static final java.time.Duration LIMITER_IDLE_RETENTION =
            java.time.Duration.ofMinutes(10);
    private static com.enviouse.futureshops.server.security.ServerRequestRateLimiter limiter;
    private static int limiterRatePerSecond = -1;

    private AuctionActionService() {
    }

    static boolean rateLimited(UUID playerId, String action) {
        int rate = AuctionHouseConfig.settings().bids().maximumRequestsPerSecond();
        synchronized (LIMITER_LOCK) {
            if (limiter == null || limiterRatePerSecond != rate) {
                var policy = new com.enviouse.futureshops.server.security
                        .ServerRequestRateLimiter.BucketPolicy(rate, rate,
                        java.time.Duration.ofSeconds(1));
                limiter = new com.enviouse.futureshops.server.security.ServerRequestRateLimiter(
                        java.util.Map.of(ACTION_CREATE, policy, ACTION_BID, policy,
                                ACTION_CANCEL, policy),
                        LIMITER_MAX_KEYS, LIMITER_IDLE_RETENTION, System::nanoTime);
                limiterRatePerSecond = rate;
            }
            return !limiter.tryAcquire(playerId, action).allowed();
        }
    }

    /** Plan §12: a mutation's route nonce must match the player's current open market route. */
    static boolean routeInvalid(ServerPlayer player, UUID routeNonce) {
        return !com.enviouse.futureshops.server.market.MarketModuleService
                .routeValid(player.getUUID(), routeNonce);
    }

    private static void abortCreateQuietly(EscrowRuntimeService runtime,
                                           AuctionCreateEscrowIntent intent,
                                           AuctionCreateEscrowIntent.Status status) {
        try {
            runtime.commitAuctionEscrowLifecycle(new AuctionEscrowLifecycleEvent.Abort(
                    intent, intent.abort(status)));
        } catch (RuntimeException exception) {
            LOGGER.error("Auction create intent {} could not abort; recovery will resolve it",
                    intent.command().requestId(), exception);
        }
    }

    // ═══════════════════════════ CREATE ═══════════════════════════

    public static void create(ServerPlayer player, C2SAuctionCreatePacket packet) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(packet, "packet");
        UUID requestId = packet.requestId();
        EscrowRuntimeService runtime = readyRuntime();
        if (runtime == null) {
            respond(player, requestId, "CREATE", "ESCROW_UNAVAILABLE", null, 0L, "");
            return;
        }
        try {
            // Replay FIRST — a duplicate of an APPLIED request must return the stored result
            // even if the module has since been disabled (at-most-once beats availability).
            Optional<AuctionEscrowCommit> replayed = runtime.auctionEscrowCommit(requestId);
            if (replayed.isPresent()) {
                respondFromCommit(player, requestId, "CREATE", replayed.orElseThrow());
                return;
            }
            // An unfinished intent means a crash window is being recovered; fail closed rather
            // than double-planning — the recovery scheduler owns completion (plan §4.2).
            if (runtime.auctionCreateIntent(requestId).isPresent()) {
                respond(player, requestId, "CREATE", "RECOVERY_REQUIRED", null, 0L, "");
                return;
            }
            if (routeInvalid(player, packet.routeNonce())) {
                respond(player, requestId, "CREATE", "INVALID_REQUEST", null, 0L, "route");
                return;
            }
            if (!actionAllowed(player.getServer())) {
                respond(player, requestId, "CREATE", "MODULE_DISABLED", null, 0L, "");
                return;
            }
            if (rateLimited(player.getUUID(), ACTION_CREATE)) {
                respond(player, requestId, "CREATE", "RATE_LIMITED", null, 0L, "");
                return;
            }

            AuctionHouseConfig.Settings settings = AuctionHouseConfig.settings();
            AuctionListingType type = parseType(packet.listingType());
            if (type == null || !typeAllowed(settings.listings(), type)) {
                respond(player, requestId, "CREATE", "TYPE_DISABLED", null, 0L, "");
                return;
            }
            long nowMillis = System.currentTimeMillis();
            long deadlineMillis = 0L;
            if (type != AuctionListingType.BUY_NOW) {
                long minSeconds = settings.listings().minimumDurationMinutes() * 60L;
                long maxSeconds = settings.listings().maximumDurationMinutes() * 60L;
                if (packet.durationSeconds() < minSeconds
                        || packet.durationSeconds() > maxSeconds) {
                    respond(player, requestId, "CREATE", "DURATION_INVALID", null, 0L, "");
                    return;
                }
                deadlineMillis = Math.addExact(nowMillis,
                        Math.multiplyExact(packet.durationSeconds(), 1000L));
            }

            ItemStack stack = packet.inventorySlot() <= MAX_MAIN_SLOT
                    ? player.getInventory().getItem(packet.inventorySlot())
                    : ItemStack.EMPTY;
            if (stack.isEmpty()) {
                respond(player, requestId, "CREATE", "ITEMS_MISSING", null, 0L, "");
                return;
            }
            // Plan §8 step 7: the slot's LIVE content must still be the item the player selected
            // — a shifted inventory would otherwise silently list whatever moved into the slot.
            if (!packet.itemFingerprint().equals(
                    C2SAuctionCreatePacket.fingerprintOf(stack))) {
                respond(player, requestId, "CREATE", "ITEMS_MISSING", null, 0L, "fingerprint");
                return;
            }
            int quantity = packet.quantity();
            if (quantity > stack.getCount()
                    || quantity > settings.listings().maximumItemCount()) {
                respond(player, requestId, "CREATE", "ITEMS_MISSING", null, 0L, "");
                return;
            }
            String restriction = restrictionDenial(settings.restrictions(), stack);
            if (restriction != null) {
                respond(player, requestId, "CREATE", "RESTRICTED_ITEM", null, 0L, restriction);
                return;
            }

            AuctionHouseSnapshot snapshot = runtime.auctionHouseSnapshot();
            long active = snapshot.listings().values().stream()
                    .filter(listing -> listing.sellerId().equals(player.getUUID()))
                    .filter(listing -> !listing.state().terminal())
                    .count();
            if (active >= settings.listings().maximumActivePerPlayer()) {
                respond(player, requestId, "CREATE", "CAPACITY_EXCEEDED", null, 0L, "");
                return;
            }

            // ── Deterministic identities: retries after a crash re-derive the same tree ──
            UUID listingId = derived("auction.listing.", requestId);
            UUID activationId = derived("auction.activation.", requestId);
            UUID custodyRequestId = AuctionEscrowIds.custodyRequestId(requestId);

            // Plan the exact-stack extraction against the live inventory (no mutation yet).
            ServerPlayerItemInventoryAccess access = new ServerPlayerItemInventoryAccess(player);
            ItemInventoryState state = access.capture();
            ItemStack template = stack.copyWithCount(1);
            List<ItemInventoryBatchEntry> entries = List.of(ItemInventoryBatchEntry.extract(
                    derived("auction.entry.", requestId),
                    ItemInputMatcher.exact(template), quantity));
            ItemInventoryMutationPlan plan;
            try {
                plan = ItemInventoryBatchPlanner.plan(state, entries);
            } catch (RuntimeException exception) {
                respond(player, requestId, "CREATE", "ITEMS_MISSING", null, 0L, "");
                return;
            }
            Instant now = Instant.ofEpochMilli(nowMillis);
            ItemInventoryMutationToken token = ItemInventoryMutationToken.create(
                    player.getUUID(), activationId, custodyRequestId, plan);
            ItemInventoryMutationReceipt plannedReceipt =
                    ItemInventoryMutationReceipt.create(token, plan, now);
            ItemInventoryMutationIntent itemIntent =
                    ItemInventoryMutationIntent.create(token, plan, plannedReceipt);

            byte[] templateBytes = ItemStackSnapshotCodec.encode(template);
            List<ExactItemClaimPayload> exactItems = new ArrayList<>();
            int portionCount = plannedReceipt.actualPortions().size();
            int nbtBytes = 0;
            for (int index = 0; index < portionCount; index++) {
                var portion = plannedReceipt.actualPortions().get(index);
                byte[] portionSnapshot = portion.actualStackSnapshot();
                nbtBytes = Math.max(nbtBytes, portionSnapshot.length);
                exactItems.add(ExactItemClaimPayload.preserveRaw(activationId,
                        AuctionEscrowItemCustody.sourceKey(listingId), index, portionCount,
                        registryId(stack), portion.count(), templateBytes, portionSnapshot));
            }
            if (nbtBytes > settings.listings().maximumItemNbtBytes()) {
                respond(player, requestId, "CREATE", "RESTRICTED_ITEM", null, 0L, "nbt");
                return;
            }
            ItemInventoryMutationReceipt receipt = plannedReceipt;
            AuctionEscrowItemCustody custody = new AuctionEscrowItemCustody(
                    listingId, activationId, receipt, List.copyOf(exactItems));

            CreateAuctionCommand command = new CreateAuctionCommand(requestId, listingId,
                    player.getUUID(), activationId,
                    new AuctionItemLot(derived("auction.lot.", requestId), registryId(stack),
                            sha256(exactItems.get(0).serializedStackSnapshot()), quantity,
                            nbtBytes, itemCategory(stack), searchDocument(stack)),
                    type, type == AuctionListingType.BUY_NOW ? 0L : packet.startingBidMinor(),
                    packet.buyoutMinor(), rules(settings), nowMillis, deadlineMillis);

            AuctionEscrowWalletSnapshot sellerWallet = walletSnapshot(runtime, player.getUUID());
            if (settings.fees().listingFeeMinor() > 0L) {
                try {
                    sellerWallet.requireAvailable(settings.fees().listingFeeMinor());
                } catch (RuntimeException exception) {
                    respond(player, requestId, "CREATE", "INSUFFICIENT_FUNDS", null, 0L, "");
                    return;
                }
            }

            AuctionCreateEscrowIntent intent = AuctionEscrowLifecyclePlanner.prepareCreate(
                    command, itemIntent, custody, sellerWallet, currencyId(), now);
            runtime.commitAuctionEscrowLifecycle(
                    new AuctionEscrowLifecycleEvent.Prepare(intent));

            // Journal OUR planned item intent BEFORE extraction so execute() replays the STORED
            // intent and commits the STORED planned receipt. Without this, execute() re-plans
            // with its own clock instant and the receipt-equality below (and commitCreate's, and
            // the recovery decision's) fails on a SUCCESSFUL extraction — items gone, listing
            // never created, no claim: permanent loss. Mirrors the bazaar sell sequence and the
            // AuctionEscrowLifecycleWalIntegrationTest ordering exactly.
            try {
                runtime.itemInventoryMutationGateway().appendPreparedDurably(itemIntent);
            } catch (RuntimeException exception) {
                LOGGER.error("Auction create {} could not journal its item intent", requestId,
                        exception);
                // Nothing left the inventory — abort the durable intent; player keeps items.
                abortCreateQuietly(runtime, intent,
                        AuctionCreateEscrowIntent.Status.ABORTED_MISSING_ITEMS);
                respond(player, requestId, "CREATE", "RECOVERY_REQUIRED", null, 0L, "");
                return;
            }
            ItemInventoryExecutionResult executed;
            try {
                executed = runtime.exactItemInventoryRuntime()
                        .execute(access, activationId, custodyRequestId, entries);
            } catch (RuntimeException exception) {
                LOGGER.error("Auction create {} extraction failed", requestId, exception);
                respond(player, requestId, "CREATE", "RECOVERY_REQUIRED", null, 0L, "");
                return;
            }
            boolean extracted = (executed.status() == ItemInventoryExecutionStatus.APPLIED
                    || executed.status() == ItemInventoryExecutionStatus.REPLAYED)
                    && executed.receipt().filter(receipt::equals).isPresent();
            if (!extracted) {
                if (executed.status() == ItemInventoryExecutionStatus.INSUFFICIENT_ITEMS
                        || executed.status() == ItemInventoryExecutionStatus.INSUFFICIENT_CAPACITY
                        || executed.status() == ItemInventoryExecutionStatus.UNSUPPORTED_STACK
                        || executed.status() == ItemInventoryExecutionStatus.ABORTED) {
                    // The item journal terminally refused the mutation — nothing left the
                    // inventory, so the durable create intent aborts cleanly.
                    abortCreateQuietly(runtime, intent,
                            AuctionCreateEscrowIntent.Status.ABORTED_MISSING_ITEMS);
                    respond(player, requestId, "CREATE", "ITEMS_MISSING", null, 0L, "");
                    return;
                }
                // RECOVERY_REQUIRED / MANUAL_REVIEW / mismatched receipt: custody is undecided
                // (items may already be extracted). The PREPARED intent stays for the recovery
                // pass — aborting here would orphan a committed extraction (plan §4.2).
                respond(player, requestId, "CREATE", "RECOVERY_REQUIRED", null, 0L, "");
                return;
            }

            AuctionEscrowCommit commit = AuctionEscrowLifecyclePlanner.commitCreate(
                    runtime.auctionHouseSnapshot(), intent, executed.receipt().orElseThrow());
            runtime.commitAuctionEscrowLifecycle(new AuctionEscrowLifecycleEvent.Commit(
                    Optional.of(intent.complete()), commit));
            respondFromCommit(player, requestId, "CREATE", commit);
        } catch (RuntimeException exception) {
            LOGGER.error("Auction create failed for {}", player.getGameProfile().getName(),
                    exception);
            respond(player, requestId, "CREATE", "RECOVERY_REQUIRED", null, 0L, "");
        }
    }

    // ═══════════════════════════ BID ═══════════════════════════

    public static void bid(ServerPlayer player, C2SAuctionBidPacket packet) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(packet, "packet");
        UUID requestId = packet.requestId();
        EscrowRuntimeService runtime = readyRuntime();
        if (runtime == null) {
            respond(player, requestId, "BID", "ESCROW_UNAVAILABLE", null, 0L, "");
            return;
        }
        try {
            // Replay before the module gate — duplicates return the stored result always.
            if (respondIfReplayed(player, runtime, requestId, "BID")) {
                return;
            }
            if (routeInvalid(player, packet.routeNonce())) {
                respond(player, requestId, "BID", "INVALID_REQUEST", null, 0L, "route");
                return;
            }
            if (!actionAllowed(player.getServer())) {
                respond(player, requestId, "BID", "MODULE_DISABLED", null, 0L, "");
                return;
            }
            if (rateLimited(player.getUUID(), ACTION_BID)) {
                respond(player, requestId, "BID", "RATE_LIMITED", null, 0L, "");
                return;
            }
            AuctionListing listing = runtime.auctionHouseListing(packet.listingId())
                    .orElse(null);
            if (listing == null) {
                respond(player, requestId, "BID", "NOT_FOUND", null, 0L, "");
                return;
            }
            long nowMillis = System.currentTimeMillis();
            long heldDelta = heldDelta(listing, player.getUUID(), packet.amountMinor());
            PlaceAuctionBidCommand command = new PlaceAuctionBidCommand(requestId,
                    packet.listingId(), packet.expectedRevision(),
                    derived("auction.bid.", requestId), player.getUUID(),
                    // Raising an own top bid must reuse the STANDING hold account — the book's
                    // identity check (and the delta-hold ledger legs) key off it; a fresh
                    // account would make every self-raise fail with HOLD_MISMATCH.
                    standingHoldAccount(listing, player.getUUID())
                            .orElseGet(() -> derived("auction.hold.", requestId)),
                    derived("auction.holdtxn.", requestId),
                    packet.amountMinor(), heldDelta, nowMillis);

            AuctionHouseSnapshot snapshot = runtime.auctionHouseSnapshot();
            AuctionOperationResult preview = new AuctionHouseBook(snapshot).bid(command);
            if (!preview.applied()) {
                respondFromResult(player, requestId, "BID", preview);
                return;
            }
            AuctionEscrowWalletSnapshot wallet = walletSnapshot(runtime, player.getUUID());
            try {
                wallet.requireAvailable(heldDelta);
            } catch (RuntimeException exception) {
                respond(player, requestId, "BID", "INSUFFICIENT_FUNDS", null,
                        listing.revision(), "");
                return;
            }
            AuctionEscrowCommit commit = AuctionEscrowLifecyclePlanner.bid(snapshot, command,
                    wallet, currencyId(), Instant.ofEpochMilli(nowMillis));
            runtime.commitAuctionEscrowLifecycle(new AuctionEscrowLifecycleEvent.Commit(
                    Optional.empty(), commit));
            respondFromCommit(player, requestId, "BID", commit);
        } catch (RuntimeException exception) {
            LOGGER.error("Auction bid failed for {}", player.getGameProfile().getName(),
                    exception);
            respond(player, requestId, "BID", "RECOVERY_REQUIRED", null, 0L, "");
        }
    }

    // ═══════════════════════════ BUY NOW ═══════════════════════════

    public static void buyNow(ServerPlayer player, C2SAuctionBuyNowPacket packet) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(packet, "packet");
        UUID requestId = packet.requestId();
        EscrowRuntimeService runtime = readyRuntime();
        if (runtime == null) {
            respond(player, requestId, "BUY_NOW", "ESCROW_UNAVAILABLE", null, 0L, "");
            return;
        }
        try {
            // Replay before the module gate — duplicates return the stored result always.
            if (respondIfReplayed(player, runtime, requestId, "BUY_NOW")) {
                return;
            }
            if (routeInvalid(player, packet.routeNonce())) {
                respond(player, requestId, "BUY_NOW", "INVALID_REQUEST", null, 0L, "route");
                return;
            }
            if (!actionAllowed(player.getServer())) {
                respond(player, requestId, "BUY_NOW", "MODULE_DISABLED", null, 0L, "");
                return;
            }
            if (rateLimited(player.getUUID(), ACTION_BID)) {
                respond(player, requestId, "BUY_NOW", "RATE_LIMITED", null, 0L, "");
                return;
            }
            AuctionListing listing = runtime.auctionHouseListing(packet.listingId())
                    .orElse(null);
            if (listing == null) {
                respond(player, requestId, "BUY_NOW", "NOT_FOUND", null, 0L, "");
                return;
            }
            AuctionEscrowItemCustody custody =
                    custodyFor(runtime, packet.listingId()).orElse(null);
            if (custody == null) {
                respond(player, requestId, "BUY_NOW", "RECOVERY_REQUIRED", null,
                        listing.revision(), "");
                return;
            }
            long nowMillis = System.currentTimeMillis();
            long heldDelta = heldDelta(listing, player.getUUID(), listing.buyoutMinor());
            AuctionBuyNowCommand command = new AuctionBuyNowCommand(requestId,
                    packet.listingId(), packet.expectedRevision(), player.getUUID(),
                    // A top bidder buying out must reuse their standing hold account (same
                    // identity rule as raising a bid) — else the book rejects HOLD_MISMATCH.
                    standingHoldAccount(listing, player.getUUID())
                            .orElseGet(() -> derived("auction.hold.", requestId)),
                    derived("auction.holdtxn.", requestId),
                    derived("auction.settle.", requestId), heldDelta, nowMillis);

            AuctionHouseSnapshot snapshot = runtime.auctionHouseSnapshot();
            AuctionOperationResult preview = new AuctionHouseBook(snapshot).buyNow(command);
            if (!preview.applied()) {
                respondFromResult(player, requestId, "BUY_NOW", preview);
                return;
            }
            AuctionEscrowWalletSnapshot buyerWallet = walletSnapshot(runtime, player.getUUID());
            try {
                buyerWallet.requireAvailable(heldDelta);
            } catch (RuntimeException exception) {
                respond(player, requestId, "BUY_NOW", "INSUFFICIENT_FUNDS", null,
                        listing.revision(), "");
                return;
            }
            AuctionEscrowWalletSnapshot sellerWallet =
                    walletSnapshot(runtime, listing.sellerId());
            AuctionEscrowCommit commit = AuctionEscrowLifecyclePlanner.buyNow(snapshot, command,
                    buyerWallet, sellerWallet, custody, currencyId(),
                    Instant.ofEpochMilli(nowMillis));
            runtime.commitAuctionEscrowLifecycle(new AuctionEscrowLifecycleEvent.Commit(
                    Optional.empty(), commit));
            respondFromCommit(player, requestId, "BUY_NOW", commit);
        } catch (RuntimeException exception) {
            LOGGER.error("Auction buy-now failed for {}", player.getGameProfile().getName(),
                    exception);
            respond(player, requestId, "BUY_NOW", "RECOVERY_REQUIRED", null, 0L, "");
        }
    }

    // ═══════════════════════════ CANCEL ═══════════════════════════

    public static void cancel(ServerPlayer player, C2SAuctionCancelPacket packet) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(packet, "packet");
        UUID requestId = packet.requestId();
        EscrowRuntimeService runtime = readyRuntime();
        if (runtime == null) {
            respond(player, requestId, "CANCEL", "ESCROW_UNAVAILABLE", null, 0L, "");
            return;
        }
        // Cancellation stays available while the module is disabled (plan §1) — no module gate.
        try {
            if (respondIfReplayed(player, runtime, requestId, "CANCEL")) {
                return;
            }
            if (routeInvalid(player, packet.routeNonce())) {
                respond(player, requestId, "CANCEL", "INVALID_REQUEST", null, 0L, "route");
                return;
            }
            if (rateLimited(player.getUUID(), ACTION_CANCEL)) {
                respond(player, requestId, "CANCEL", "RATE_LIMITED", null, 0L, "");
                return;
            }
            AuctionListing listing = runtime.auctionHouseListing(packet.listingId())
                    .orElse(null);
            if (listing == null) {
                respond(player, requestId, "CANCEL", "NOT_FOUND", null, 0L, "");
                return;
            }
            AuctionEscrowItemCustody custody =
                    custodyFor(runtime, packet.listingId()).orElse(null);
            if (custody == null) {
                respond(player, requestId, "CANCEL", "RECOVERY_REQUIRED", null,
                        listing.revision(), "");
                return;
            }
            long nowMillis = System.currentTimeMillis();
            CancelAuctionCommand command = new CancelAuctionCommand(requestId,
                    packet.listingId(), packet.expectedRevision(), player.getUUID(),
                    derived("auction.cancel.", requestId), nowMillis);
            AuctionHouseSnapshot snapshot = runtime.auctionHouseSnapshot();
            AuctionOperationResult preview = new AuctionHouseBook(snapshot).cancel(command);
            if (!preview.applied()) {
                respondFromResult(player, requestId, "CANCEL", preview);
                return;
            }
            AuctionEscrowCommit commit = AuctionEscrowLifecyclePlanner.cancel(snapshot, command,
                    custody, currencyId(), Instant.ofEpochMilli(nowMillis));
            runtime.commitAuctionEscrowLifecycle(new AuctionEscrowLifecycleEvent.Commit(
                    Optional.empty(), commit));
            respondFromCommit(player, requestId, "CANCEL", commit);
        } catch (RuntimeException exception) {
            LOGGER.error("Auction cancel failed for {}", player.getGameProfile().getName(),
                    exception);
            respond(player, requestId, "CANCEL", "RECOVERY_REQUIRED", null, 0L, "");
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

    /** Module gate for NEW value operations; claims/cancel stay available while disabled. */
    static boolean actionAllowed(MinecraftServer server) {
        if (server == null) {
            return false;
        }
        Optional<MarketModuleControl> control;
        try {
            control = Optional.of(MarketControlSavedData.get(server).snapshot()
                    .module(MarketControlModule.AUCTION_HOUSE));
        } catch (RuntimeException exception) {
            control = Optional.empty();
        }
        return MarketModuleAccessPolicy.capability(
                com.enviouse.futureshops.client.market.MarketModule.AUCTION_HOUSE,
                Config.auctionHouseEnabled(), true, control, 0L)
                .allowsNewValueOperations();
    }

    /** Config → per-listing frozen rule snapshot (plan §1: live contracts never change). */
    static AuctionRuleSnapshot rules(AuctionHouseConfig.Settings settings) {
        return new AuctionRuleSnapshot(settings.fees().listingFeeMinor(),
                settings.fees().saleTaxBasisPoints(),
                settings.bids().minimumIncrementMinor(),
                settings.bids().minimumIncrementBasisPoints(),
                settings.antiSnipe().enabled(),
                settings.antiSnipe().triggerSeconds() * 1000L,
                settings.antiSnipe().extensionSeconds() * 1000L,
                settings.antiSnipe().maximumCumulativeSeconds() * 1000L,
                settings.antiSnipe().maximumExtensionCount(),
                settings.listings().allowSellerCancelBeforeBid(),
                "online_time".equals(settings.lifecycle().timeBasis())
                        ? AuctionTimeBasis.ONLINE_TIME : AuctionTimeBasis.REAL_TIME,
                settings.lifecycle().pauseWhileFrozen(),
                0L);
    }

    static AuctionEscrowWalletSnapshot walletSnapshot(EscrowRuntimeService runtime,
                                                      UUID playerId) {
        try (CurrencyManager.ConfigurationReadLease lease =
                     CurrencyManager.acquireConfigurationReadLease()) {
            return new AuctionEscrowWalletSnapshot(playerId,
                    runtime.ledgerBalance(com.enviouse.futureshops.server.market.auction.escrow
                            .AuctionEscrowLedgerAccounts.wallet(playerId)),
                    runtime.ledgerBalance(com.enviouse.futureshops.server.market.auction.escrow
                            .AuctionEscrowLedgerAccounts.debt(playerId)),
                    0L, Config.economyMaxBalanceMinorUnits, lease.generation());
        }
    }

    /** New bidder holds the full amount; raising an own top bid holds only the delta. */
    static long heldDelta(AuctionListing listing, UUID bidderId, long amountMinor) {
        return listing.highestBid()
                .filter(standing -> standing.bidderId().equals(bidderId))
                .map(standing -> Math.subtractExact(amountMinor, standing.amountMinor()))
                .orElse(amountMinor);
    }

    /** The standing top bid's hold account when {@code playerId} owns it (self-raise/buyout). */
    static Optional<UUID> standingHoldAccount(AuctionListing listing, UUID playerId) {
        return listing.highestBid()
                .filter(standing -> standing.bidderId().equals(playerId))
                .map(standing -> standing.holdAccountId());
    }

    /**
     * The CREATE commit that activated {@code listingId} carries the item custody every
     * settlement-side operation (buy-now / cancel / expire / settle) must hand back to the
     * planner. Scan is O(commits) — fine at action rate; the expiration scheduler batches.
     */
    static Optional<AuctionEscrowItemCustody> custodyFor(EscrowRuntimeService runtime,
                                                         UUID listingId) {
        return runtime.auctionEscrowLifecycleState().commits().values().stream()
                .filter(commit -> commit.operation() == AuctionOperationType.CREATE)
                .filter(commit -> commit.listingId().equals(listingId))
                .flatMap(commit -> commit.itemCustody().stream())
                .findFirst();
    }

    static boolean respondIfReplayed(ServerPlayer player, EscrowRuntimeService runtime,
                                     UUID requestId, String action) {
        Optional<AuctionEscrowCommit> commit = runtime.auctionEscrowCommit(requestId);
        if (commit.isPresent()) {
            respondFromCommit(player, requestId, action, commit.orElseThrow());
            return true;
        }
        Optional<AuctionRequestReceipt> receipt = runtime.auctionHouseReceipt(requestId);
        if (receipt.isPresent()) {
            respondFromResult(player, requestId, action, receipt.orElseThrow().result());
            return true;
        }
        return false;
    }

    private static void respondFromCommit(ServerPlayer player, UUID requestId, String action,
                                          AuctionEscrowCommit commit) {
        long revision = commit.bookMutations().get(commit.bookMutations().size() - 1)
                .requestReceipt().result().listing()
                .map(AuctionListing::revision).orElse(0L);
        UUID transactionId = commit.completedTransaction()
                .map(transaction -> transaction.transactionId().value()).orElse(null);
        respond(player, requestId, action, "APPLIED", transactionId, revision,
                commit.listingId().toString());
    }

    private static void respondFromResult(ServerPlayer player, UUID requestId, String action,
                                          AuctionOperationResult result) {
        respond(player, requestId, action, result.status().name(), null,
                result.observedRevision(), result.listingId().toString());
    }

    static void respond(ServerPlayer player, UUID requestId, String action, String status,
                        UUID transactionId, long revision, String detail) {
        ShopPackets.sendToPlayer(player, new S2CMarketActionResponsePacket(requestId,
                transactionId == null ? S2CMarketActionResponsePacket.NO_TRANSACTION
                        : transactionId,
                MODULE, action, status, Math.max(0L, revision), 0L,
                System.currentTimeMillis(), detail == null ? "" : detail));
    }

    // ── item helpers ──

    private static boolean typeAllowed(AuctionHouseConfig.ListingRules rules,
                                       AuctionListingType type) {
        return switch (type) {
            case BUY_NOW -> rules.allowBuyNow();
            case TIMED_AUCTION -> rules.allowTimedAuctions();
            case AUCTION_WITH_BUYOUT -> rules.allowTimedAuctions() && rules.allowAuctionBuyout();
        };
    }

    private static AuctionListingType parseType(String wire) {
        try {
            return AuctionListingType.valueOf(wire.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * Restriction screen (plan §8: soulbound / oversized NBT / containers / capability items
     * denied by default). Returns the denial token or null when listable. Capability probing is
     * NBT-based (serialized ForgeCaps) — safe without touching capability class-init.
     */
    static String restrictionDenial(AuctionHouseConfig.RestrictionRules restrictions,
                                    ItemStack stack) {
        String registryId = registryId(stack);
        if (restrictions.deniedItemIds().contains(registryId)) {
            return "denied_id";
        }
        for (String tag : restrictions.deniedItemTags()) {
            if (stack.getTags().anyMatch(key -> key.location().toString().equals(tag))) {
                return "denied_tag";
            }
        }
        return serializedDenial(restrictions, stack.serializeNBT());
    }

    /**
     * The serialized-NBT half of the restriction screen, split out so unit tests can probe the
     * capability / container branches with synthetic tags — a plain JUnit ItemStack can never
     * serialize a real root-level {@code ForgeCaps} entry (capability providers only attach
     * under the Forge runtime).
     */
    static String serializedDenial(AuctionHouseConfig.RestrictionRules restrictions,
                                   net.minecraft.nbt.CompoundTag serialized) {
        if ("deny".equals(restrictions.capabilityPolicy())
                && serialized.contains("ForgeCaps")) {
            return "capability";
        }
        if ("deny".equals(restrictions.containerPolicy()) && serialized.contains("tag")) {
            var tag = serialized.getCompound("tag");
            if (tag.contains("BlockEntityTag")
                    && tag.getCompound("BlockEntityTag").contains("Items")) {
                return "container";
            }
            if (tag.contains("Items")) {
                return "container";
            }
        }
        return null;
    }

    private static String registryId(ItemStack stack) {
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? "minecraft:air" : key.toString();
    }

    private static String itemCategory(ItemStack stack) {
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? "" : key.getNamespace();
    }

    private static String searchDocument(ItemStack stack) {
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        String id = registryId(stack).toLowerCase(Locale.ROOT).replace(':', ' ');
        String document = (name + " " + id).strip();
        return document.length() > 256 ? document.substring(0, 256) : document;
    }

    static String currencyId() {
        return "futureshops:wallet";
    }

    static UUID derived(String prefix, UUID requestId) {
        return UUID.nameUUIDFromBytes(
                (prefix + requestId).getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
