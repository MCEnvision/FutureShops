package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.block.ShopBlockEntity;
import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.catalog.offer.OfferAction;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.SellOfferOption;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.data.BulkSellQuote;
import com.enviouse.futureshops.data.BulkSellTarget;
import com.enviouse.futureshops.data.NearbyShopEntry;
import com.enviouse.futureshops.network.packets.C2SPlayerShopOfferPacket;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopOfferService;
import com.enviouse.futureshops.server.security.ServerRequestAction;
import com.enviouse.futureshops.server.security.ServerRequestSecurityManager;
import com.enviouse.futureshops.server.session.ShopSessionManager;
import com.enviouse.futureshops.server.transaction.ServerShopOfferPermissionPolicy;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class BulkSellService {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final long QUOTE_LIFETIME_MILLIS = 60_000L;
    private static final int MAX_CANDIDATES = 512;
    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    private static final Map<MinecraftServer, Map<UUID, StoredQuote>> QUOTES =
            new WeakHashMap<>();

    private BulkSellService() {
    }

    public static QuoteResult quote(
            ServerPlayer player,
            BulkSellTarget target,
            String requestedShopId,
            boolean selectEligibleByDefault
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(target, "target");
        ServerRequestSecurityManager.GateDecision gate =
                ServerRequestSecurityManager.tryAcquire(
                        player, ServerRequestAction.BULK_SELL);
        if (!gate.allowed()) {
            return QuoteResult.failure(gate.status()
                    == ServerRequestSecurityManager.GateStatus.RATE_LIMITED
                    ? Status.RATE_LIMITED : Status.UNAVAILABLE);
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return QuoteResult.failure(Status.UNAVAILABLE);
        }
        String shopId = target == BulkSellTarget.ADMIN_SHOP
                ? ShopDataService.resolveShopId(requestedShopId)
                : "playershops";
        if (target == BulkSellTarget.ADMIN_SHOP) {
            if (!AdminShopToggleSavedData.get(server)
                    .isAdminShopEnabled()
                    || ShopCatalog.get(shopId).isEmpty()) {
                return QuoteResult.failure(Status.NOT_AVAILABLE);
            }
            ShopSessionManager.open(player.getUUID(), shopId);
        }
        try {
            return QuoteResult.success(buildQuote(
                    player, target, shopId, selectEligibleByDefault));
        } catch (ArithmeticException | IllegalArgumentException exception) {
            LOGGER.warn(
                    "Rejected invalid bulk sell quote for player {} and target {}.",
                    player.getUUID(), target, exception);
            return QuoteResult.failure(Status.INVALID_REQUEST);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Unable to build bulk sell quote for player {} and target {}.",
                    player.getUUID(), target, exception);
            return QuoteResult.failure(Status.UNAVAILABLE);
        }
    }

    public static CommitResult commit(
            ServerPlayer player,
            UUID quoteId,
            List<String> selectedLineIds
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(quoteId, "quoteId");
        List<String> selected = List.copyOf(Objects.requireNonNull(
                selectedLineIds, "selectedLineIds"));
        if (ZERO_UUID.equals(quoteId)
                || selected.isEmpty()
                || selected.size() > BulkSellQuote.MAX_LINES
                || new LinkedHashSet<>(selected).size()
                != selected.size()) {
            return CommitResult.failure(
                    quoteId, Status.INVALID_SELECTION);
        }
        ServerRequestSecurityManager.GateDecision gate =
                ServerRequestSecurityManager.tryAcquire(
                        player, ServerRequestAction.BULK_SELL);
        if (!gate.allowed()) {
            return CommitResult.failure(quoteId,
                    gate.status()
                    == ServerRequestSecurityManager.GateStatus.RATE_LIMITED
                            ? Status.RATE_LIMITED : Status.UNAVAILABLE);
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return CommitResult.failure(quoteId, Status.UNAVAILABLE);
        }
        StoredQuote stored;
        synchronized (QUOTES) {
            cleanup(server, System.currentTimeMillis());
            stored = QUOTES.getOrDefault(server, Map.of())
                    .get(player.getUUID());
            if (stored == null
                    || !stored.quote.quoteId().equals(quoteId)) {
                return CommitResult.failure(
                        quoteId, Status.QUOTE_EXPIRED);
            }
            if (stored.completed != null) {
                return stored.completed.asReplayed();
            }
            if (stored.inProgress
                    || System.currentTimeMillis()
                    > stored.quote.expiresAtEpochMillis()) {
                return CommitResult.failure(
                        quoteId, Status.QUOTE_EXPIRED);
            }
            if (!stored.lines.keySet().containsAll(selected)) {
                return CommitResult.failure(
                        quoteId, Status.INVALID_SELECTION);
            }
            stored.inProgress = true;
        }
        boolean current;
        try {
            current = selected.stream()
                    .map(stored.lines::get)
                    .allMatch(line ->
                            currentLine(player, line));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Bulk sell quote validation failed for player {} and quote {}.",
                    player.getUUID(), quoteId, exception);
            current = false;
        }
        if (!current) {
            synchronized (QUOTES) {
                stored.inProgress = false;
                Map<UUID, StoredQuote> values =
                        QUOTES.get(server);
                if (values != null
                        && values.get(player.getUUID()) == stored) {
                    values.remove(player.getUUID());
                }
            }
            return CommitResult.failure(
                    quoteId, Status.QUOTE_EXPIRED);
        }

        int sold = 0;
        int failed = 0;
        int recovery = 0;
        long paid = 0L;
        for (String lineId : selected) {
            StoredLine line = stored.lines.get(lineId);
            LineResult result;
            try {
                result = executeLine(
                        player, stored.quote, line);
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Bulk sell line {} failed unexpectedly for player {} and quote {}.",
                        lineId, player.getUUID(), quoteId, exception);
                recovery++;
                continue;
            }
            if (result.success) {
                sold++;
                paid = Math.addExact(paid, result.paidMinorUnits);
            } else if (result.recoveryRequired) {
                recovery++;
            } else {
                failed++;
            }
        }
        Status status = recovery > 0
                ? Status.RECOVERY_REQUIRED
                : sold == selected.size()
                ? Status.SUCCESS
                : sold > 0 ? Status.PARTIAL : Status.REJECTED;
        CommitResult result = new CommitResult(
                quoteId, status, sold, failed, recovery, paid, false);
        synchronized (QUOTES) {
            stored.inProgress = false;
            stored.completed = result;
        }
        try {
            refresh(player, stored.quote, sold > 0);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Bulk sell refresh failed for player {} and quote {}.",
                    player.getUUID(), quoteId, exception);
        }
        return result;
    }

    public static CommitResult commitAll(
            ServerPlayer player,
            QuoteResult result
    ) {
        if (!result.success()) {
            return CommitResult.failure(
                    ZERO_UUID, result.status());
        }
        List<String> selected = result.quote().lines().stream()
                .filter(BulkSellQuote.Line::eligible)
                .map(BulkSellQuote.Line::lineId)
                .toList();
        if (selected.isEmpty()) {
            return CommitResult.failure(
                    result.quote().quoteId(), Status.NOTHING_ELIGIBLE);
        }
        return commit(player, result.quote().quoteId(), selected);
    }

    public static void cancel(ServerPlayer player, UUID quoteId) {
        MinecraftServer server = player.getServer();
        if (server == null || quoteId == null) {
            return;
        }
        synchronized (QUOTES) {
            Map<UUID, StoredQuote> values = QUOTES.get(server);
            StoredQuote stored = values == null
                    ? null : values.get(player.getUUID());
            if (stored != null && !stored.inProgress
                    && stored.quote.quoteId().equals(quoteId)) {
                values.remove(player.getUUID());
            }
        }
    }

    public static void clearPlayer(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        synchronized (QUOTES) {
            Map<UUID, StoredQuote> values = QUOTES.get(server);
            if (values != null) {
                values.remove(player.getUUID());
            }
        }
    }

    public static void clearServer(MinecraftServer server) {
        synchronized (QUOTES) {
            QUOTES.remove(Objects.requireNonNull(server, "server"));
        }
    }

    private static BulkSellQuote buildQuote(
            ServerPlayer player,
            BulkSellTarget target,
            String shopId,
            boolean selectEligibleByDefault
    ) {
        UUID quoteId = UUID.randomUUID();
        long expiresAt = Math.addExact(
                System.currentTimeMillis(), QUOTE_LIFETIME_MILLIS);
        Map<InventoryKey, Integer> inventory = inventory(player);
        List<Candidate> candidates = target == BulkSellTarget.ADMIN_SHOP
                ? adminCandidates(player, shopId)
                : playerShopCandidates(player);
        candidates.sort(Candidate.ORDER);
        Set<InventoryKey> recognized = new LinkedHashSet<>();
        for (Candidate candidate : candidates) {
            for (OfferItemComponent component
                    : candidate.option.itemInputs()) {
                InventoryKey key = inventoryKey(component);
                if (key != null) {
                    recognized.add(key);
                }
            }
        }

        Map<InventoryKey, Integer> remaining =
                new LinkedHashMap<>(inventory);
        List<BulkSellQuote.Line> lines = new ArrayList<>();
        Map<String, StoredLine> storedLines = new LinkedHashMap<>();
        int ordinal = 0;
        for (Candidate candidate : candidates) {
            if (lines.size() >= BulkSellQuote.MAX_LINES) {
                break;
            }
            int quantity = executableQuantity(
                    player, candidate, remaining);
            if (quantity < 1) {
                continue;
            }
            String lineId = deterministicLineId(
                    quoteId, candidate.identity, ordinal++);
            long total = Math.multiplyExact(
                    candidate.option.moneyPayoutMinorUnits(),
                    (long) quantity);
            List<BulkSellQuote.Component> components =
                    candidate.option.itemInputs().stream()
                            .map(component ->
                                    new BulkSellQuote.Component(
                                            component.itemId(),
                                            component.count(),
                                            component.exactNbt()))
                            .toList();
            BulkSellQuote.Line line = new BulkSellQuote.Line(
                    lineId, trim(candidate.destination),
                    components, quantity,
                    candidate.option.moneyPayoutMinorUnits(),
                    total, true,
                    "gui.futureshops.bulk_sell.reason.eligible");
            lines.add(line);
            storedLines.put(lineId, new StoredLine(
                    candidate.target, candidate.shopId,
                    candidate.shopPos, candidate.listingIndex,
                    candidate.offer.listingId(),
                    candidate.option.optionId(),
                    candidate.offer.revision(),
                    quantity, total));
            reserve(candidate, remaining, quantity);
        }
        for (Map.Entry<InventoryKey, Integer> entry
                : remaining.entrySet()) {
            if (entry.getValue() <= 0
                    || lines.size() >= BulkSellQuote.MAX_LINES) {
                continue;
            }
            String lineId = deterministicLineId(
                    quoteId, "unaccepted." + entry.getKey(), ordinal++);
            lines.add(new BulkSellQuote.Line(
                    lineId,
                    "gui.futureshops.bulk_sell.destination.none",
                    List.of(new BulkSellQuote.Component(
                            entry.getKey().itemId,
                            entry.getValue(),
                            entry.getKey().exactNbt)),
                    1, 0L, 0L, false,
                    recognized.contains(entry.getKey())
                            ? "gui.futureshops.bulk_sell.reason.unavailable"
                            : "gui.futureshops.bulk_sell.reason.not_accepted"));
        }
        BulkSellQuote quote = new BulkSellQuote(
                quoteId, target, shopId, expiresAt,
                BalanceManager.getProvider().getCurrencyName(),
                BalanceManager.getProvider().getDecimalPlaces(),
                selectEligibleByDefault, lines);
        synchronized (QUOTES) {
            QUOTES.computeIfAbsent(player.getServer(),
                            ignored -> new HashMap<>())
                    .put(player.getUUID(),
                            new StoredQuote(quote, storedLines));
        }
        return quote;
    }

    private static List<Candidate> adminCandidates(
            ServerPlayer player,
            String shopId
    ) {
        long now = Instant.now().getEpochSecond();
        List<Candidate> candidates = new ArrayList<>();
        ShopCatalog.get(shopId).ifPresent(definition -> {
            for (ServerShopOfferListing offer : definition.offers()) {
                addCandidates(player, candidates,
                        BulkSellTarget.ADMIN_SHOP, shopId,
                        null, -1, shopId, 0.0D, offer, now);
            }
        });
        return candidates;
    }

    private static List<Candidate> playerShopCandidates(
            ServerPlayer player
    ) {
        long now = Instant.now().getEpochSecond();
        List<Candidate> candidates = new ArrayList<>();
        List<NearbyShopEntry> nearby = NearbyShopScanner.scanNearby(
                player, Config.localListingsScanRadiusBlocks);
        for (NearbyShopEntry entry : nearby) {
            if (candidates.size() >= MAX_CANDIDATES) {
                break;
            }
            if (!(player.level().getBlockEntity(entry.pos())
                    instanceof ShopBlockEntity shop)) {
                continue;
            }
            List<ShopBlockEntity.Listing> listings =
                    shop.getListings();
            for (int index = 0; index < listings.size(); index++) {
                ShopBlockEntity.Listing physical = listings.get(index);
                if (physical.hidden() || physical.showcase()) {
                    continue;
                }
                ServerShopOfferListing offer =
                        physical.normalizedOffer().orElse(null);
                if (offer == null) {
                    continue;
                }
                addCandidates(player, candidates,
                        BulkSellTarget.PLAYER_SHOPS,
                        "player_shop:" + entry.pos().asLong(),
                        entry.pos(), index, entry.shopName(),
                        entry.distance(), offer, now);
                if (candidates.size() >= MAX_CANDIDATES) {
                    break;
                }
            }
        }
        return candidates;
    }

    private static void addCandidates(
            ServerPlayer player,
            List<Candidate> candidates,
            BulkSellTarget target,
            String shopId,
            @Nullable BlockPos shopPos,
            int listingIndex,
            String destination,
            double distance,
            ServerShopOfferListing offer,
            long now
    ) {
        if (candidates.size() >= MAX_CANDIDATES
                || !offer.active()
                || offer.expiresAtEpoch() > 0L
                && now >= offer.expiresAtEpoch()
                || !offer.schedule().activeAt(now)
                || !ServerShopOfferPermissionPolicy.allowed(
                player, offer.permissionNode())) {
            return;
        }
        for (SellOfferOption option : offer.sellOptions()) {
            if (candidates.size() >= MAX_CANDIDATES) {
                return;
            }
            if (option.moneyPayoutMinorUnits() <= 0L
                    || option.itemInputs().isEmpty()
                    || !option.schedule().activeAt(now)
                    || !ServerShopOfferPermissionPolicy.allowed(
                    player, option.permissionNode())
                    || option.itemInputs().stream()
                    .anyMatch(component -> inventoryKey(component)
                            == null)) {
                continue;
            }
            String identity = target.name() + "." + shopId + "."
                    + offer.listingId() + "." + option.optionId();
            candidates.add(new Candidate(
                    target, shopId, shopPos, listingIndex,
                    destination, distance, offer, option, identity));
        }
    }

    private static int executableQuantity(
            ServerPlayer player,
            Candidate candidate,
            Map<InventoryKey, Integer> remaining
    ) {
        long quantity = Math.min(
                candidate.offer.limits().maximumPerRequest(),
                candidate.option.limits().maximumPerRequest());
        if (candidate.option.capacity() > 0L) {
            quantity = Math.min(quantity, candidate.option.capacity());
        }
        for (Map.Entry<InventoryKey, Integer> requirement
                : requirements(candidate).entrySet()) {
            quantity = Math.min(quantity,
                    remaining.getOrDefault(
                            requirement.getKey(), 0)
                            / requirement.getValue());
        }
        int inventoryQuantity = (int) Math.max(
                0L, Math.min(2304L, quantity));
        return BulkSellPlanning.maximumExecutableQuantity(
                inventoryQuantity,
                value -> canExecute(player, candidate, value));
    }

    private static boolean canExecute(
            ServerPlayer player,
            Candidate candidate,
            int quantity
    ) {
        UUID requestId = UUID.nameUUIDFromBytes((
                "futureshops.bulk.sell.preview.v1\u0000"
                        + player.getUUID() + "\u0000"
                        + candidate.identity + "\u0000"
                        + quantity).getBytes(StandardCharsets.UTF_8));
        if (candidate.target == BulkSellTarget.ADMIN_SHOP) {
            return ServerShopOfferService.canExecuteBulkLine(
                    player, new ServerShopOfferService.Request(
                            requestId, player.getUUID(),
                            candidate.shopId,
                            candidate.offer.listingId(),
                            candidate.option.optionId(),
                            OfferAction.SELL_TO_SHOP,
                            quantity,
                            candidate.offer.revision(),
                            Optional.empty(), 0));
        }
        return PlayerShopEscrowTransactionService
                .canExecuteBulkOffer(
                        player,
                        new C2SPlayerShopOfferPacket(
                                Objects.requireNonNull(
                                        candidate.shopPos),
                                candidate.listingIndex,
                                candidate.offer.listingId(),
                                candidate.option.optionId(),
                                OfferAction.SELL_TO_SHOP,
                                quantity,
                                candidate.offer.revision(),
                                Optional.empty(),
                                requestId, 0));
    }

    private static boolean currentLine(
            ServerPlayer player,
            StoredLine line
    ) {
        ServerShopOfferListing offer;
        if (line.target == BulkSellTarget.ADMIN_SHOP) {
            offer = ShopCatalog.getOffer(
                    line.shopId, line.listingId).orElse(null);
        } else {
            if (line.shopPos == null
                    || !(player.level().getBlockEntity(
                    line.shopPos) instanceof ShopBlockEntity shop)) {
                return false;
            }
            ShopBlockEntity.Listing listing =
                    shop.getListing(line.listingIndex);
            offer = listing == null
                    ? null
                    : listing.normalizedOffer().orElse(null);
        }
        if (offer == null
                || !offer.listingId().equals(line.listingId)
                || offer.revision() != line.revision
                || !offer.active()) {
            return false;
        }
        long now = Instant.now().getEpochSecond();
        if (offer.expiresAtEpoch() > 0L
                && now >= offer.expiresAtEpoch()
                || !offer.schedule().activeAt(now)
                || !ServerShopOfferPermissionPolicy.allowed(
                player, offer.permissionNode())) {
            return false;
        }
        return offer.sellOptions().stream()
                .filter(option -> option.optionId().equals(
                        line.optionId))
                .filter(option ->
                        option.schedule().activeAt(now))
                .filter(option ->
                        ServerShopOfferPermissionPolicy.allowed(
                                player,
                                option.permissionNode()))
                .anyMatch(option -> {
                    try {
                        return Math.multiplyExact(
                                option.moneyPayoutMinorUnits(),
                                (long) line.quantity)
                                >= line.quotedPayout;
                    } catch (ArithmeticException exception) {
                        return false;
                    }
                });
    }

    private static void reserve(
            Candidate candidate,
            Map<InventoryKey, Integer> remaining,
            int quantity
    ) {
        for (Map.Entry<InventoryKey, Integer> requirement
                : requirements(candidate).entrySet()) {
            int amount = Math.multiplyExact(
                    requirement.getValue(), quantity);
            remaining.compute(
                    requirement.getKey(), (ignored, current) ->
                    Math.subtractExact(
                            Objects.requireNonNullElse(current, 0),
                            amount));
        }
    }

    private static Map<InventoryKey, Integer> requirements(
            Candidate candidate
    ) {
        Map<InventoryKey, Integer> requirements =
                new LinkedHashMap<>();
        for (OfferItemComponent component
                : candidate.option.itemInputs()) {
            InventoryKey key = Objects.requireNonNull(
                    inventoryKey(component));
            requirements.merge(
                    key, component.count(), Math::addExact);
        }
        return requirements;
    }

    private static int compareCandidates(
            Candidate left,
            Candidate right
    ) {
        long leftItems = inputCount(left);
        long rightItems = inputCount(right);
        return BulkSellPlanning.compare(
                left.option.moneyPayoutMinorUnits(),
                leftItems, left.distance, left.identity,
                right.option.moneyPayoutMinorUnits(),
                rightItems, right.distance, right.identity);
    }

    private static long inputCount(Candidate candidate) {
        return candidate.option.itemInputs().stream()
                .mapToLong(OfferItemComponent::count)
                .sum();
    }

    private static LineResult executeLine(
            ServerPlayer player,
            BulkSellQuote quote,
            StoredLine line
    ) {
        UUID childId = UUID.nameUUIDFromBytes((
                "futureshops.bulk.sell.v1\u0000"
                        + quote.quoteId() + "\u0000"
                        + line.lineIdentity()).getBytes(
                StandardCharsets.UTF_8));
        boolean success;
        boolean recovery;
        boolean replayed;
        long settledPayout;
        if (line.target == BulkSellTarget.ADMIN_SHOP) {
            ShopSessionManager.open(player.getUUID(), line.shopId);
            ServerShopOfferService.Result result =
                    ServerShopOfferService.executeBulkLine(
                            player, new ServerShopOfferService.Request(
                                    childId, player.getUUID(),
                                    line.shopId, line.listingId,
                                    line.optionId,
                                    OfferAction.SELL_TO_SHOP,
                                    line.quantity, line.revision,
                                    Optional.empty(), 0),
                            line.quotedPayout);
            success = result.status().success();
            recovery = result.status()
                    == ServerShopOfferService.Status.RECOVERY_REQUIRED
                    || result.status()
                    == ServerShopOfferService.Status.QUARANTINED;
            replayed = result.replayed();
            settledPayout = result.settledMoneyMinorUnits();
        } else {
            PlayerShopEscrowTransactionService.BulkOfferResult result =
                    PlayerShopEscrowTransactionService
                            .executeBulkOffer(
                                    player,
                                    new C2SPlayerShopOfferPacket(
                                            Objects.requireNonNull(
                                                    line.shopPos),
                                            line.listingIndex,
                                            line.listingId,
                                            line.optionId,
                                            OfferAction.SELL_TO_SHOP,
                                            line.quantity,
                                            line.revision,
                                            Optional.empty(),
                                            childId, 0),
                                    line.quotedPayout);
            success = result.success();
            recovery = result.recoveryRequired();
            replayed = result.replayed();
            settledPayout = result.paidMinorUnits();
        }
        long paid = success ? settledPayout : 0L;
        if (success && replayed && paid == 0L) {
            paid = line.quotedPayout;
        }
        return new LineResult(success, recovery, paid);
    }

    private static void refresh(
            ServerPlayer player,
            BulkSellQuote quote,
            boolean changed
    ) {
        if (!changed || player.getServer() == null) {
            return;
        }
        if (quote.target() == BulkSellTarget.ADMIN_SHOP) {
            InventorySyncService.sendOwnedCounts(
                    player, quote.shopId());
            ShopDataService.resendSessionsViewingShop(
                    player.getServer(), quote.shopId());
        } else {
            LocalShopAggregator.sendLocalShops(player, "");
        }
    }

    private static Map<InventoryKey, Integer> inventory(
            ServerPlayer player
    ) {
        Map<InventoryKey, Integer> counts = new LinkedHashMap<>();
        accumulate(counts, player.getInventory().items);
        accumulate(counts, player.getInventory().offhand);
        return counts;
    }

    private static void accumulate(
            Map<InventoryKey, Integer> counts,
            List<ItemStack> stacks
    ) {
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(
                    stack.getItem());
            if (id == null) {
                continue;
            }
            String nbt = stack.getTag() == null
                    ? "" : stack.getTag().toString();
            counts.merge(new InventoryKey(id.toString(), nbt),
                    stack.getCount(), Math::addExact);
        }
    }

    @Nullable
    private static InventoryKey inventoryKey(
            OfferItemComponent component
    ) {
        if (!component.exactMatch()) {
            return new InventoryKey(component.itemId(), "");
        }
        try {
            CompoundTag tag = TagParser.parseTag(
                    component.exactNbt());
            return new InventoryKey(component.itemId(),
                    tag.toString());
        } catch (com.mojang.brigadier.exceptions
                .CommandSyntaxException | RuntimeException exception) {
            return null;
        }
    }

    private static String deterministicLineId(
            UUID quoteId,
            String identity,
            int ordinal
    ) {
        return UUID.nameUUIDFromBytes((
                "futureshops.bulk.sell.line.v1\u0000"
                        + quoteId + "\u0000" + identity + "\u0000"
                        + ordinal).getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    private static String trim(String value) {
        String candidate = Objects.requireNonNullElse(
                value, "Shop").strip();
        if (candidate.isEmpty()) {
            candidate = "Shop";
        }
        return candidate.length() <= BulkSellQuote.MAX_TEXT_LENGTH
                ? candidate
                : candidate.substring(
                0, BulkSellQuote.MAX_TEXT_LENGTH);
    }

    private static void cleanup(
            MinecraftServer server,
            long nowMillis
    ) {
        Map<UUID, StoredQuote> values = QUOTES.get(server);
        if (values == null) {
            return;
        }
        values.entrySet().removeIf(entry ->
                !entry.getValue().inProgress
                        && nowMillis
                        > entry.getValue().quote
                        .expiresAtEpochMillis()
                        + QUOTE_LIFETIME_MILLIS);
        if (values.isEmpty()) {
            QUOTES.remove(server);
        }
    }

    public record QuoteResult(
            Status status,
            @Nullable BulkSellQuote quote
    ) {
        public QuoteResult {
            Objects.requireNonNull(status, "status");
            if (status.success() != (quote != null)) {
                throw new IllegalArgumentException(
                        "Bulk sell quote result is invalid");
            }
        }

        public boolean success() {
            return status.success();
        }

        private static QuoteResult success(BulkSellQuote quote) {
            return new QuoteResult(Status.SUCCESS,
                    Objects.requireNonNull(quote, "quote"));
        }

        private static QuoteResult failure(Status status) {
            return new QuoteResult(status, null);
        }
    }

    public record CommitResult(
            UUID quoteId,
            Status status,
            int soldLines,
            int failedLines,
            int recoveryLines,
            long paidMinorUnits,
            boolean replayed
    ) {
        public CommitResult {
            Objects.requireNonNull(quoteId, "quoteId");
            Objects.requireNonNull(status, "status");
            if (soldLines < 0 || failedLines < 0
                    || recoveryLines < 0 || paidMinorUnits < 0L
                    || replayed && status == Status.QUOTE_EXPIRED
                    || !validShape(
                    status, soldLines, failedLines,
                    recoveryLines, paidMinorUnits)) {
                throw new IllegalArgumentException(
                        "Bulk sell result is invalid");
            }
        }

        private static boolean validShape(
                Status status,
                int sold,
                int failed,
                int recovery,
                long paid
        ) {
            if ((sold > 0) != (paid > 0L)) {
                return false;
            }
            return switch (status) {
                case SUCCESS -> sold > 0
                        && failed == 0 && recovery == 0;
                case PARTIAL -> sold > 0
                        && failed > 0 && recovery == 0;
                case REJECTED -> sold == 0
                        && failed > 0 && recovery == 0;
                case RECOVERY_REQUIRED -> recovery > 0;
                default -> sold == 0
                        && failed == 0 && recovery == 0
                        && paid == 0L;
            };
        }

        public CommitResult asReplayed() {
            return new CommitResult(
                    quoteId, status, soldLines, failedLines,
                    recoveryLines, paidMinorUnits, true);
        }

        private static CommitResult failure(
                UUID quoteId,
                Status status
        ) {
            return new CommitResult(
                    quoteId, status, 0, 0, 0, 0L, false);
        }
    }

    public enum Status {
        SUCCESS(true),
        PARTIAL(false),
        REJECTED(false),
        RECOVERY_REQUIRED(false),
        NOTHING_ELIGIBLE(false),
        QUOTE_EXPIRED(false),
        INVALID_SELECTION(false),
        INVALID_REQUEST(false),
        NOT_AVAILABLE(false),
        RATE_LIMITED(false),
        UNAVAILABLE(false);

        private final boolean success;

        Status(boolean success) {
            this.success = success;
        }

        public boolean success() {
            return success;
        }
    }

    private static final class StoredQuote {
        private final BulkSellQuote quote;
        private final Map<String, StoredLine> lines;
        private boolean inProgress;
        private CommitResult completed;

        private StoredQuote(
                BulkSellQuote quote,
                Map<String, StoredLine> lines
        ) {
            this.quote = quote;
            this.lines = Map.copyOf(lines);
        }
    }

    private record StoredLine(
            BulkSellTarget target,
            String shopId,
            @Nullable BlockPos shopPos,
            int listingIndex,
            String listingId,
            String optionId,
            long revision,
            int quantity,
            long quotedPayout
    ) {
        private String lineIdentity() {
            return target + "." + shopId + "." + listingId + "."
                    + optionId + "." + listingIndex;
        }
    }

    private record Candidate(
            BulkSellTarget target,
            String shopId,
            @Nullable BlockPos shopPos,
            int listingIndex,
            String destination,
            double distance,
            ServerShopOfferListing offer,
            SellOfferOption option,
            String identity
    ) {
        private static final Comparator<Candidate> ORDER =
                BulkSellService::compareCandidates;
    }

    private record InventoryKey(
            String itemId,
            String exactNbt
    ) {
    }

    private record LineResult(
            boolean success,
            boolean recoveryRequired,
            long paidMinorUnits
    ) {
    }
}
