package com.enviouse.futureshops.server.transaction;

import com.enviouse.futureshops.catalog.offer.OfferAction;
import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.server.SavedDataMigrations;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopOfferSelection;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopOfferCartCommit;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopOfferCartCommitSavedData;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopOfferCartPreparedSavedData;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopOfferCommit;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopOfferCommitSavedData;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopOfferReplayLedger;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopOfferReplayReceipt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ServerShopOfferUsageSavedData extends SavedData {
    private static final Logger LOGGER =
            com.mojang.logging.LogUtils.getLogger();
    private static final String DATA_ID =
            "futureshops_server_shop_offer_usage";
    private static final int CURRENT_VERSION = 2;
    private static final int MAX_SCOPES = 100_000;
    private static final int MAX_REQUESTS_PER_SCOPE = 131_072;
    private static final UUID CAPACITY_SCOPE_PLAYER =
            UUID.fromString("f541a62f-2c3b-5bc3-a56d-32f313fe21dc");
    private final Map<Scope, Usage> usages = new HashMap<>();
    private long reconciledSingleRevision = Long.MIN_VALUE;
    private long reconciledCartRevision = Long.MIN_VALUE;
    private long replayDiscoveryOffset;
    private boolean replayRecoveryPending;

    public static ServerShopOfferUsageSavedData get(
            MinecraftServer server
    ) {
        ServerShopOfferUsageSavedData data =
                server.overworld().getDataStorage().computeIfAbsent(
                ServerShopOfferUsageSavedData::load,
                ServerShopOfferUsageSavedData::new,
                DATA_ID);
        data.reconcileCommittedOffers(server);
        data.reconcileDurableReplayBatch(server);
        return data;
    }

    private synchronized void reconcileCommittedOffers(
            MinecraftServer server
    ) {
        ServerShopOfferCommitSavedData single =
                ServerShopOfferCommitSavedData.get(server);
        ServerShopOfferCartCommitSavedData carts =
                ServerShopOfferCartCommitSavedData.get(server);
        long singleRevision = single.mutationRevision();
        long cartRevision = carts.mutationRevision();
        if (reconciledSingleRevision != singleRevision) {
            for (ServerShopOfferCommit commit : single.entries()) {
                reconcileSingle(commit);
            }
            for (com.enviouse.futureshops.server.escrow.runtime
                    .ServerShopOfferReplayReceipt receipt
                    : single.archivedEntries()) {
                reconcileArchived(receipt, false);
            }
            reconciledSingleRevision = singleRevision;
        }
        if (reconciledCartRevision != cartRevision) {
            ServerShopOfferCartPreparedSavedData prepared =
                    ServerShopOfferCartPreparedSavedData.get(server);
            for (ServerShopOfferCartCommit commit : carts.entries()) {
                java.util.Optional<com.enviouse.futureshops.server
                        .escrow.runtime.ServerShopOfferReplayReceipt>
                        archived = carts.findArchived(
                        commit.requestId());
                if (archived.isPresent()) {
                    reconcileArchived(
                            archived.orElseThrow(), true);
                } else {
                    ServerShopOfferCartPreparedSavedData.Entry entry =
                            prepared.find(commit.requestId()).orElseThrow(
                                    () -> new IllegalStateException(
                                            "Committed server offer cart is missing durable quote evidence"));
                    reconcileCart(commit, entry);
                }
            }
            for (com.enviouse.futureshops.server.escrow.runtime
                    .ServerShopOfferReplayReceipt receipt
                    : carts.archivedEntries()) {
                reconcileArchived(receipt, true);
            }
            reconciledCartRevision = cartRevision;
        }
    }

    private synchronized void reconcileDurableReplayBatch(
            MinecraftServer server
    ) {
        ServerShopOfferReplayLedger.DiscoveryBatch batch =
                ServerShopOfferReplayLedger.get(server)
                        .readDiscoveryBatch(
                                replayDiscoveryOffset,
                                ServerShopOfferReplayLedger
                                        .MAXIMUM_DISCOVERY_BATCH);
        for (ServerShopOfferReplayReceipt receipt
                : batch.receipts()) {
            if (!receipt.successful()) {
                continue;
            }
            reconcileArchived(
                    receipt,
                    receipt.kind()
                            == ServerShopOfferReplayReceipt.Kind.CART);
        }
        if (replayDiscoveryOffset != batch.nextByteOffset()) {
            replayDiscoveryOffset = batch.nextByteOffset();
            setDirty();
        }
        replayRecoveryPending = !batch.endOfIndex();
    }

    synchronized void reconcileArchived(
            com.enviouse.futureshops.server.escrow.runtime
                    .ServerShopOfferReplayReceipt receipt,
            boolean cart
    ) {
        List<com.enviouse.futureshops.server.escrow.runtime
                .ServerShopOfferReplayReceipt.UsageEvidence> evidence =
                receipt.usageEvidence();
        if (cart != (receipt.kind()
                == com.enviouse.futureshops.server.escrow.runtime
                .ServerShopOfferReplayReceipt.Kind.CART)
                || !cart && evidence.size() != 1
                || cart && evidence.stream().anyMatch(line ->
                line.action() != OfferAction.ACQUIRE_FROM_SHOP)) {
            throw new IllegalStateException(
                    "Server shop offer replay usage kind conflicts");
        }
        Map<String, Integer> listingTotals =
                new LinkedHashMap<>();
        Map<String, com.enviouse.futureshops.server.escrow.runtime
                .ServerShopOfferReplayReceipt.UsageEvidence> samples =
                new LinkedHashMap<>();
        for (com.enviouse.futureshops.server.escrow.runtime
                .ServerShopOfferReplayReceipt.UsageEvidence line
                : evidence) {
            listingTotals.merge(
                    line.listingId(), line.quantity(),
                    Math::addExact);
            samples.putIfAbsent(line.listingId(), line);
        }
        for (Map.Entry<String, Integer> total
                : listingTotals.entrySet()) {
            com.enviouse.futureshops.server.escrow.runtime
                    .ServerShopOfferReplayReceipt.UsageEvidence sample =
                    samples.get(total.getKey());
            recordListing(
                    receipt.requestId(), sample.playerId(),
                    sample.shopId(), sample.listingId(),
                    sample.action(), total.getValue(),
                    sample.listingLimits(),
                    sample.committedAtEpoch());
        }
        for (com.enviouse.futureshops.server.escrow.runtime
                .ServerShopOfferReplayReceipt.UsageEvidence line
                : evidence) {
            recordOption(
                    receipt.requestId(), line.playerId(),
                    line.shopId(), line.listingId(),
                    line.optionId(), line.action(),
                    line.quantity(), line.optionLimits(),
                    line.committedAtEpoch());
            if (line.action() == OfferAction.SELL_TO_SHOP) {
                recordCapacity(
                        receipt.requestId(), line.shopId(),
                        line.listingId(), line.optionId(),
                        line.quantity(), line.capacity(),
                        line.committedAtEpoch());
            }
        }
    }

    private void reconcileSingle(ServerShopOfferCommit commit) {
        PlayerShopOfferSelection selection = commit.valueCommit()
                .committedIntent().offerSelection().orElseThrow(
                        () -> new IllegalStateException(
                                "Committed server offer is missing selection evidence"));
        if (!selection.listingId().equals(commit.listingId())
                || !selection.optionId().equals(commit.optionId())
                || selection.offerRevision() != commit.offerRevision()
                || selection.action() != commit.action()) {
            throw new IllegalStateException(
                    "Committed server offer selection evidence conflicts");
        }
        long now = commit.quotedAt().getEpochSecond();
        record(commit.requestId(), commit.playerId(), commit.shopId(),
                commit.listingId(), commit.optionId(), commit.action(),
                commit.quantity(), selection.listingLimits(),
                selection.optionLimits(), now);
        if (commit.action() == OfferAction.SELL_TO_SHOP) {
            recordCapacity(commit.requestId(), commit.shopId(),
                    commit.listingId(), commit.optionId(),
                    commit.quantity(), selection.capacity(), now);
        }
    }

    private void reconcileCart(
            ServerShopOfferCartCommit commit,
            ServerShopOfferCartPreparedSavedData.Entry entry
    ) {
        if (!entry.requestId().equals(commit.requestId())
                || !entry.playerId().equals(commit.playerId())
                || !entry.shopId().equals(commit.shopId())
                || !entry.paymentSource().equals(commit.paymentSource())
                || !entry.quotedAt().equals(commit.quotedAt())
                || entry.lines().size() != commit.lines().size()) {
            throw new IllegalStateException(
                    "Committed server offer cart quote evidence conflicts");
        }
        Map<String, Integer> listingTotals = new LinkedHashMap<>();
        for (ServerShopOfferCartPreparedSavedData.QuotedLine line
                : entry.lines()) {
            ServerShopOfferCartCommit.Line captured =
                    ServerShopOfferCartCommit.captureLine(
                            line.listing(), line.optionId(),
                            line.quantity(), line.savings());
            if (!commit.lines().contains(captured)) {
                throw new IllegalStateException(
                        "Committed server offer cart line evidence conflicts");
            }
            listingTotals.merge(line.listing().listingId(),
                    line.quantity(), Math::addExact);
        }
        long now = commit.quotedAt().getEpochSecond();
        for (Map.Entry<String, Integer> total
                : listingTotals.entrySet()) {
            ServerShopOfferCartPreparedSavedData.QuotedLine sample =
                    entry.lines().stream().filter(line ->
                            line.listing().listingId().equals(
                                    total.getKey()))
                            .findFirst().orElseThrow();
            recordListing(commit.requestId(), commit.playerId(),
                    commit.shopId(), total.getKey(),
                    OfferAction.ACQUIRE_FROM_SHOP, total.getValue(),
                    sample.listing().limits(), now);
        }
        for (ServerShopOfferCartPreparedSavedData.QuotedLine line
                : entry.lines()) {
            AcquireOfferOption option = line.listing()
                    .acquireOptions().stream()
                    .filter(value -> value.optionId().equals(
                            line.optionId()))
                    .findFirst().orElseThrow();
            recordOption(commit.requestId(), commit.playerId(),
                    commit.shopId(), line.listing().listingId(),
                    line.optionId(), OfferAction.ACQUIRE_FROM_SHOP,
                    line.quantity(), option.limits(), now);
        }
    }

    public synchronized Decision check(
            UUID playerId,
            String shopId,
            String listingId,
            String optionId,
            OfferAction action,
            int quantity,
            OfferLimitPolicy listingLimits,
            OfferLimitPolicy optionLimits,
            long nowEpoch
    ) {
        if (replayRecoveryPending) {
            return Decision.RECOVERY_REQUIRED;
        }
        Decision listing = checkScope(new Scope(
                        playerId, shopId, listingId, "", action),
                quantity, listingLimits, nowEpoch);
        if (listing != Decision.ALLOWED) {
            return listing;
        }
        return checkScope(new Scope(
                        playerId, shopId, listingId, optionId, action),
                quantity, optionLimits, nowEpoch);
    }

    public synchronized Decision checkListing(
            UUID playerId,
            String shopId,
            String listingId,
            OfferAction action,
            int quantity,
            OfferLimitPolicy limits,
            long nowEpoch
    ) {
        if (replayRecoveryPending) {
            return Decision.RECOVERY_REQUIRED;
        }
        return checkScope(new Scope(
                        playerId, shopId, listingId, "", action),
                quantity, limits, nowEpoch);
    }

    public synchronized Decision checkOption(
            UUID playerId,
            String shopId,
            String listingId,
            String optionId,
            OfferAction action,
            int quantity,
            OfferLimitPolicy limits,
            long nowEpoch
    ) {
        if (replayRecoveryPending) {
            return Decision.RECOVERY_REQUIRED;
        }
        return checkScope(new Scope(
                        playerId, shopId, listingId, optionId, action),
                quantity, limits, nowEpoch);
    }

    public synchronized Decision checkCapacity(
            String shopId,
            String listingId,
            String optionId,
            int quantity,
            long capacity,
            long nowEpoch
    ) {
        if (replayRecoveryPending) {
            return Decision.RECOVERY_REQUIRED;
        }
        if (capacity <= 0L) {
            return Decision.ALLOWED;
        }
        return checkScope(new Scope(
                        CAPACITY_SCOPE_PLAYER, shopId, listingId,
                        optionId, OfferAction.SELL_TO_SHOP),
                quantity, capacityLimits(capacity), nowEpoch);
    }

    public synchronized void record(
            UUID requestId,
            UUID playerId,
            String shopId,
            String listingId,
            String optionId,
            OfferAction action,
            int quantity,
            OfferLimitPolicy listingLimits,
            OfferLimitPolicy optionLimits,
            long nowEpoch
    ) {
        boolean first = recordScope(requestId, new Scope(
                        playerId, shopId, listingId, "", action),
                quantity, listingLimits, nowEpoch);
        boolean second = recordScope(requestId, new Scope(
                        playerId, shopId, listingId, optionId, action),
                quantity, optionLimits, nowEpoch);
        if (first || second) {
            setDirty();
        }
    }

    public synchronized void recordListing(
            UUID requestId,
            UUID playerId,
            String shopId,
            String listingId,
            OfferAction action,
            int quantity,
            OfferLimitPolicy limits,
            long nowEpoch
    ) {
        if (recordScope(requestId, new Scope(
                        playerId, shopId, listingId, "", action),
                quantity, limits, nowEpoch)) {
            setDirty();
        }
    }

    public synchronized void recordOption(
            UUID requestId,
            UUID playerId,
            String shopId,
            String listingId,
            String optionId,
            OfferAction action,
            int quantity,
            OfferLimitPolicy limits,
            long nowEpoch
    ) {
        if (recordScope(requestId, new Scope(
                        playerId, shopId, listingId, optionId, action),
                quantity, limits, nowEpoch)) {
            setDirty();
        }
    }

    public synchronized void recordCapacity(
            UUID requestId,
            String shopId,
            String listingId,
            String optionId,
            int quantity,
            long capacity,
            long nowEpoch
    ) {
        if (!reserveCapacity(requestId, shopId, listingId, optionId,
                quantity, capacity, nowEpoch)) {
            throw new IllegalStateException(
                    "Server shop offer capacity was exceeded");
        }
    }

    public synchronized boolean reserveCapacity(
            UUID requestId,
            String shopId,
            String listingId,
            String optionId,
            int quantity,
            long capacity,
            long nowEpoch
    ) {
        if (capacity <= 0L) {
            return true;
        }
        Scope scope = new Scope(
                CAPACITY_SCOPE_PLAYER, shopId, listingId,
                optionId, OfferAction.SELL_TO_SHOP);
        Usage existing = usages.get(scope);
        if (existing != null
                && existing.processedRequests.contains(requestId)) {
            return true;
        }
        OfferLimitPolicy limits = capacityLimits(capacity);
        if (checkScope(scope, quantity, limits, nowEpoch)
                != Decision.ALLOWED) {
            return false;
        }
        if (recordScope(requestId, scope, quantity, limits, nowEpoch)) {
            setDirty();
        }
        return true;
    }

    public synchronized void releaseCapacity(
            UUID requestId,
            String shopId,
            String listingId,
            String optionId,
            int quantity,
            long capacity
    ) {
        if (capacity <= 0L) {
            return;
        }
        Scope scope = new Scope(
                CAPACITY_SCOPE_PLAYER, shopId, listingId,
                optionId, OfferAction.SELL_TO_SHOP);
        Usage usage = usages.get(scope);
        if (usage == null
                || !usage.processedRequests.remove(requestId)) {
            return;
        }
        usage.lifetimeQuantity = Math.subtractExact(
                usage.lifetimeQuantity, quantity);
        usage.periodQuantity = Math.max(0L, Math.subtractExact(
                usage.periodQuantity, quantity));
        if (usage.lifetimeQuantity == 0L
                && usage.processedRequests.isEmpty()) {
            usages.remove(scope);
        }
        setDirty();
    }

    private Decision checkScope(
            Scope scope,
            int quantity,
            OfferLimitPolicy limits,
            long nowEpoch
    ) {
        if (quantity <= 0
                || quantity > limits.maximumPerRequest()) {
            return Decision.LIFETIME_LIMIT;
        }
        Usage usage = usages.get(scope);
        if (usage == null) {
            return Decision.ALLOWED;
        }
        if (limits.lifetimeLimit() > 0L
                && exceeds(usage.lifetimeQuantity,
                quantity, limits.lifetimeLimit())) {
            return Decision.LIFETIME_LIMIT;
        }
        if (limits.cooldownSeconds() > 0L
                && usage.lastCommittedAt > 0L
                && nowEpoch < Math.addExact(
                usage.lastCommittedAt, limits.cooldownSeconds())) {
            return Decision.COOLDOWN;
        }
        Period period = period(usage, limits, nowEpoch);
        if (limits.periodLimit() > 0L
                && exceeds(period.used(), quantity,
                limits.periodLimit())) {
            return Decision.PERIOD_LIMIT;
        }
        return Decision.ALLOWED;
    }

    private boolean recordScope(
            UUID requestId,
            Scope scope,
            int quantity,
            OfferLimitPolicy limits,
            long nowEpoch
    ) {
        if (!tracksUsage(limits)) {
            return false;
        }
        if (!usages.containsKey(scope) && usages.size() >= MAX_SCOPES) {
            throw new IllegalStateException(
                    "Server shop offer usage scope limit is exceeded");
        }
        Usage usage = usages.get(scope);
        if (usage != null
                && limits.lifetimeLimit() == 0L
                && expired(usage, limits, nowEpoch)) {
            usage.lifetimeQuantity = 0L;
            usage.periodStartedAt = 0L;
            usage.periodQuantity = 0L;
            usage.lastCommittedAt = 0L;
            usage.processedRequests.clear();
        }
        if (usage != null
                && usage.processedRequests.contains(requestId)) {
            return false;
        }
        if (usage != null
                && usage.processedRequests.size()
                >= MAX_REQUESTS_PER_SCOPE) {
            throw new IllegalStateException(
                    "Server shop offer usage request limit is exceeded");
        }
        if (usage == null) {
            usage = new Usage();
            usages.put(scope, usage);
        }
        usage.processedRequests.add(requestId);
        usage.lifetimeQuantity = Math.addExact(
                usage.lifetimeQuantity, quantity);
        Period period = period(usage, limits, nowEpoch);
        usage.periodStartedAt = period.startedAt();
        usage.periodQuantity = Math.addExact(period.used(), quantity);
        usage.lastCommittedAt = nowEpoch;
        return true;
    }

    private static Period period(
            Usage usage,
            OfferLimitPolicy limits,
            long nowEpoch
    ) {
        if (limits.periodSeconds() == 0L
                || usage.periodStartedAt == 0L
                || nowEpoch >= Math.addExact(
                usage.periodStartedAt, limits.periodSeconds())) {
            return new Period(nowEpoch, 0L);
        }
        return new Period(usage.periodStartedAt, usage.periodQuantity);
    }

    private static boolean exceeds(
            long current,
            int quantity,
            long maximum
    ) {
        return Math.addExact(current, quantity) > maximum;
    }

    private static OfferLimitPolicy capacityLimits(long capacity) {
        return new OfferLimitPolicy(
                OfferLimitPolicy.DEFAULT_MAXIMUM_PER_REQUEST,
                capacity, 0L, 0L, 0L);
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        if (usages.size() > MAX_SCOPES) {
            throw new IllegalStateException(
                    "Server shop offer usage scope limit is exceeded");
        }
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        ListTag rows = new ListTag();
        for (Map.Entry<Scope, Usage> mapEntry : usages.entrySet()) {
            Scope scope = mapEntry.getKey();
            Usage usage = mapEntry.getValue();
            CompoundTag row = new CompoundTag();
            row.putUUID("Player", scope.playerId());
            row.putString("Shop", scope.shopId());
            row.putString("Listing", scope.listingId());
            row.putString("Option", scope.optionId());
            row.putString("Action", scope.action().name());
            row.putLong("Lifetime", usage.lifetimeQuantity);
            row.putLong("PeriodStart", usage.periodStartedAt);
            row.putLong("PeriodQuantity", usage.periodQuantity);
            row.putLong("LastCommitted", usage.lastCommittedAt);
            ListTag requests = new ListTag();
            for (UUID requestId : usage.processedRequests) {
                CompoundTag request = new CompoundTag();
                request.putUUID("Id", requestId);
                requests.add(request);
            }
            row.put("Requests", requests);
            rows.add(row);
        }
        tag.put("Usages", rows);
        tag.putLong("ReplayDiscoveryOffset",
                replayDiscoveryOffset);
        return tag;
    }

    public static ServerShopOfferUsageSavedData load(CompoundTag tag) {
        SavedDataMigrations.needsMigration(
                DATA_ID, SavedDataMigrations.readVersion(tag),
                CURRENT_VERSION);
        ServerShopOfferUsageSavedData data =
                new ServerShopOfferUsageSavedData();
        if (SavedDataMigrations.readVersion(tag) >= 2) {
            data.replayDiscoveryOffset = requireNonnegative(
                    tag.getLong("ReplayDiscoveryOffset"),
                    "replay discovery offset");
        }
        ListTag rows = tag.getList("Usages", Tag.TAG_COMPOUND);
        if (rows.size() > MAX_SCOPES) {
            throw new IllegalArgumentException(
                    "Server shop offer usage scope limit is exceeded");
        }
        for (int index = 0; index < rows.size(); index++) {
            try {
                CompoundTag row = rows.getCompound(index);
                Scope scope = new Scope(
                        row.getUUID("Player"),
                        row.getString("Shop"),
                        row.getString("Listing"),
                        row.getString("Option"),
                        OfferAction.valueOf(row.getString("Action")));
                Usage usage = new Usage();
                usage.lifetimeQuantity =
                        requireNonnegative(row.getLong("Lifetime"),
                                "lifetime quantity");
                usage.periodStartedAt =
                        requireNonnegative(row.getLong("PeriodStart"),
                                "period start");
                usage.periodQuantity =
                        requireNonnegative(row.getLong("PeriodQuantity"),
                                "period quantity");
                usage.lastCommittedAt =
                        requireNonnegative(row.getLong("LastCommitted"),
                                "last committed time");
                ListTag requests = row.getList(
                        "Requests", Tag.TAG_COMPOUND);
                if (requests.size() > MAX_REQUESTS_PER_SCOPE) {
                    throw new IllegalArgumentException(
                            "Server shop offer usage request limit is exceeded");
                }
                for (int requestIndex = 0;
                     requestIndex < requests.size(); requestIndex++) {
                    UUID requestId = requests.getCompound(requestIndex)
                            .getUUID("Id");
                    if (requestId.equals(new UUID(0L, 0L))
                            || !usage.processedRequests.add(requestId)) {
                        throw new IllegalArgumentException(
                                "Server shop offer usage request is invalid");
                    }
                }
                if (usage.periodQuantity > usage.lifetimeQuantity
                        || data.usages.putIfAbsent(scope, usage) != null) {
                    throw new IllegalArgumentException(
                            "Server shop offer usage row conflicts");
                }
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException(
                        "Server shop offer usage row " + index
                                + " is invalid", exception);
            }
        }
        return data;
    }

    private static long requireNonnegative(long value, String label) {
        if (value < 0L) {
            throw new IllegalArgumentException(
                    "Server shop offer usage " + label + " is invalid");
        }
        return value;
    }

    public enum Decision {
        ALLOWED,
        LIFETIME_LIMIT,
        PERIOD_LIMIT,
        COOLDOWN,
        RECOVERY_REQUIRED
    }

    private record Scope(
            UUID playerId,
            String shopId,
            String listingId,
            String optionId,
            OfferAction action
    ) {
        private Scope {
            Objects.requireNonNull(playerId, "playerId");
            if (playerId.equals(new UUID(0L, 0L))) {
                throw new IllegalArgumentException(
                        "Server shop offer usage player is invalid");
            }
            shopId = identifier(shopId);
            listingId = identifier(listingId);
            optionId = optionId == null ? "" : optionId.strip();
            if (optionId.length() > 160
                    || !optionId.isEmpty()
                    && !optionId.matches("[a-z0-9_.:/-]+")) {
                throw new IllegalArgumentException(
                        "Server shop offer usage option is invalid");
            }
            Objects.requireNonNull(action, "action");
        }

        private static String identifier(String value) {
            String candidate = Objects.requireNonNull(
                    value, "identifier").strip();
            if (candidate.isEmpty() || candidate.length() > 160
                    || !candidate.matches("[a-z0-9_.:/-]+")) {
                throw new IllegalArgumentException(
                        "Server shop offer usage identifier is invalid");
            }
            return candidate;
        }
    }

    private static final class Usage {
        private long lifetimeQuantity;
        private long periodStartedAt;
        private long periodQuantity;
        private long lastCommittedAt;
        private final LinkedHashSet<UUID> processedRequests =
                new LinkedHashSet<>();
    }

    private record Period(long startedAt, long used) {
    }

    private static boolean tracksUsage(OfferLimitPolicy limits) {
        return limits.lifetimeLimit() > 0L
                || limits.periodLimit() > 0L
                || limits.cooldownSeconds() > 0L;
    }

    private static boolean expired(
            Usage usage,
            OfferLimitPolicy limits,
            long nowEpoch
    ) {
        long periodEnd = limits.periodLimit() > 0L
                && usage.periodStartedAt > 0L
                ? Math.addExact(
                usage.periodStartedAt, limits.periodSeconds()) : 0L;
        long cooldownEnd = limits.cooldownSeconds() > 0L
                && usage.lastCommittedAt > 0L
                ? Math.addExact(
                usage.lastCommittedAt, limits.cooldownSeconds()) : 0L;
        return nowEpoch >= Math.max(periodEnd, cooldownEnd);
    }
}
