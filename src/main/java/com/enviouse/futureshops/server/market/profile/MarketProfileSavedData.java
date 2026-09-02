package com.enviouse.futureshops.server.market.profile;

import com.enviouse.futureshops.server.SavedDataMigrations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class MarketProfileSavedData extends SavedData {
    public static final String DATA_NAME = "futureshops_market_profiles";
    public static final int CURRENT_VERSION = 3;
    public static final int MAX_PROFILES = 100000;
    public static final int MAX_WATCHED_AUCTIONS = 256;
    public static final int MAX_FAVORITE_PRODUCTS = 256;
    public static final int MAX_RECENT_PRODUCTS = 32;
    public static final int MAX_PRICE_ALERTS = 64;
    public static final int MAX_NOTIFICATIONS = 128;
    public static final int MAX_MUTATION_RECEIPTS = 512;
    public static final int MAX_MUTATION_TOMBSTONES = 512;

    private static final int LEGACY_REPLAY_FILTER_VERSION = 1;
    private static final int LEGACY_REPLAY_FILTER_BITS = 32768;
    private static final int LEGACY_REPLAY_FILTER_WORDS =
            LEGACY_REPLAY_FILTER_BITS / Long.SIZE;
    private static final int LEGACY_REPLAY_FILTER_HASHES = 5;

    private static final UUID ZERO = new UUID(0L, 0L);

    private final Map<UUID, MutableProfile> profiles =
            new LinkedHashMap<>();

    public static MarketProfileSavedData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(
                MarketProfileSavedData::load,
                MarketProfileSavedData::new,
                DATA_NAME);
    }

    public static MarketProfileSavedData load(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        requireType(tag, "schemaVersion", Tag.TAG_INT);
        int version = SavedDataMigrations.readVersion(tag);
        if (version < 1 || version > CURRENT_VERSION) {
            throw new IllegalStateException(
                    "Market profile schema is unsupported");
        }
        requireType(tag, "players", Tag.TAG_LIST);
        ListTag players = SavedDataMigrations.requireList(
                tag, "players", Tag.TAG_COMPOUND, MAX_PROFILES,
                "Market profile players");
        MarketProfileSavedData data = new MarketProfileSavedData();
        for (int index = 0; index < players.size(); index++) {
            CompoundTag encoded = players.getCompound(index);
            if (!encoded.hasUUID("player")) {
                throw new IllegalStateException(
                        "Market profile player identity is missing");
            }
            UUID playerId = requireUuid(encoded.getUUID("player"),
                    "player");
            MutableProfile profile = readProfile(encoded, version,
                    playerId);
            if (data.profiles.put(playerId, profile) != null) {
                throw new IllegalStateException(
                        "Market profile player is duplicated");
            }
        }
        if (version < CURRENT_VERSION) {
            data.setDirty();
        }
        return data;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        if (profiles.size() > MAX_PROFILES) {
            throw new IllegalStateException(
                    "Market profile player limit is exceeded");
        }
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        ListTag players = new ListTag();
        profiles.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(UUID::toString)))
                .forEach(entry -> players.add(writeProfile(
                        entry.getKey(), entry.getValue())));
        tag.put("players", players);
        return tag;
    }

    public synchronized Snapshot snapshot(UUID playerId) {
        MutableProfile profile = profiles.get(requireUuid(
                playerId, "playerId"));
        return profile == null ? Snapshot.empty() : profile.snapshot();
    }

    public synchronized boolean setAuctionWatched(
            UUID playerId,
            UUID listingId,
            boolean watched
    ) {
        MutableProfile profile = profile(playerId);
        UUID listing = requireUuid(listingId, "listingId");
        boolean changed;
        if (watched) {
            changed = profile.watchedAuctions.add(listing);
            trimFirst(profile.watchedAuctions, MAX_WATCHED_AUCTIONS);
        } else {
            changed = profile.watchedAuctions.remove(listing);
        }
        markChanged(profile, changed);
        return changed;
    }

    public synchronized boolean setProductFavorite(
            UUID playerId,
            ProductKey product,
            boolean favorite
    ) {
        MutableProfile profile = profile(playerId);
        product = Objects.requireNonNull(product, "product");
        boolean changed;
        if (favorite) {
            changed = profile.favoriteProducts.add(product);
            trimFirst(profile.favoriteProducts,
                    MAX_FAVORITE_PRODUCTS);
        } else {
            changed = profile.favoriteProducts.remove(product);
        }
        markChanged(profile, changed);
        return changed;
    }

    public synchronized void recordRecentProduct(
            UUID playerId,
            ProductKey product
    ) {
        MutableProfile profile = profile(playerId);
        ProductKey safe = Objects.requireNonNull(product, "product");
        boolean changed = profile.recentProducts.isEmpty()
                || !profile.recentProducts.get(0).equals(safe);
        profile.recentProducts.remove(safe);
        profile.recentProducts.add(0, safe);
        trimTail(profile.recentProducts, MAX_RECENT_PRODUCTS);
        markChanged(profile, changed);
    }

    public synchronized boolean putPriceAlert(
            UUID playerId,
            PriceAlert alert
    ) {
        MutableProfile profile = profile(playerId);
        alert = Objects.requireNonNull(alert, "alert");
        boolean exists = profile.priceAlerts.containsKey(alert.alertId());
        if (!exists && profile.priceAlerts.size() >= MAX_PRICE_ALERTS) {
            return false;
        }
        PriceAlert previous = profile.priceAlerts.put(
                alert.alertId(), alert);
        boolean changed = !alert.equals(previous);
        markChanged(profile, changed);
        return changed;
    }

    public synchronized boolean removePriceAlert(
            UUID playerId,
            UUID alertId
    ) {
        MutableProfile profile = profiles.get(requireUuid(
                playerId, "playerId"));
        if (profile == null) {
            return false;
        }
        boolean changed = profile.priceAlerts.remove(requireUuid(
                alertId, "alertId")) != null;
        markChanged(profile, changed);
        return changed;
    }

    public synchronized List<PriceAlert> evaluatePrice(
            UUID playerId,
            ProductKey product,
            long priceMinor,
            long nowMillis
    ) {
        requireUuid(playerId, "playerId");
        product = Objects.requireNonNull(product, "product");
        if (priceMinor <= 0L || nowMillis < 0L) {
            throw new IllegalArgumentException(
                    "Market alert price sample is invalid");
        }
        MutableProfile profile = profiles.get(playerId);
        if (profile == null) {
            return List.of();
        }
        List<PriceAlert> triggered = new ArrayList<>();
        for (Map.Entry<UUID, PriceAlert> entry
                : profile.priceAlerts.entrySet()) {
            PriceAlert alert = entry.getValue();
            if (!alert.enabled() || !alert.product().equals(product)
                    || !alert.direction().matches(
                    priceMinor, alert.thresholdMinor())) {
                continue;
            }
            PriceAlert updated = alert.triggered(nowMillis);
            entry.setValue(updated);
            triggered.add(updated);
            appendNotification(profile, new Notification(
                    notificationId(alert.alertId(), nowMillis),
                    Module.BAZAAR, NotificationKind.PRICE_ALERT,
                    product.productId(),
                    "gui.futureshops.market.notification.price_alert",
                    Long.toString(priceMinor), nowMillis, false));
        }
        if (!triggered.isEmpty()) {
            markChanged(profile, true);
        }
        return List.copyOf(triggered);
    }

    public synchronized void notify(
            UUID playerId,
            Notification notification
    ) {
        MutableProfile profile = profile(playerId);
        boolean changed = appendNotification(profile,
                Objects.requireNonNull(notification, "notification"));
        markChanged(profile, changed);
    }

    public synchronized boolean markNotificationRead(
            UUID playerId,
            UUID notificationId
    ) {
        MutableProfile profile = profiles.get(requireUuid(
                playerId, "playerId"));
        UUID safeId = requireUuid(notificationId, "notificationId");
        if (profile == null) {
            return false;
        }
        for (int index = 0; index < profile.notifications.size();
             index++) {
            Notification current = profile.notifications.get(index);
            if (current.notificationId().equals(safeId)
                    && !current.read()) {
                profile.notifications.set(index, current.markRead());
                markChanged(profile, true);
                return true;
            }
        }
        return false;
    }

    public synchronized int markNotificationsRead(
            UUID playerId,
            Module module,
            List<UUID> notificationIds
    ) {
        UUID player = requireUuid(playerId, "playerId");
        Module safeModule = Objects.requireNonNull(module, "module");
        List<UUID> requested = List.copyOf(Objects.requireNonNull(
                notificationIds, "notificationIds"));
        if (requested.isEmpty()
                || requested.size() > MAX_NOTIFICATIONS) {
            throw new IllegalArgumentException(
                    "Market notification read request is invalid");
        }
        Set<UUID> unique = new LinkedHashSet<>();
        for (UUID notificationId : requested) {
            if (!unique.add(requireUuid(notificationId,
                    "notificationId"))) {
                throw new IllegalArgumentException(
                        "Market notification read request is duplicated");
            }
        }
        MutableProfile profile = profiles.get(player);
        if (profile == null) {
            return 0;
        }
        int changed = 0;
        for (int index = 0; index < profile.notifications.size();
             index++) {
            Notification notification = profile.notifications.get(
                    index);
            if (!notification.read()
                    && notification.module() == safeModule
                    && unique.contains(notification.notificationId())) {
                profile.notifications.set(index,
                        notification.markRead());
                changed++;
            }
        }
        markChanged(profile, changed > 0);
        return changed;
    }

    public synchronized int clearReadNotifications(UUID playerId) {
        MutableProfile profile = profiles.get(requireUuid(
                playerId, "playerId"));
        if (profile == null) {
            return 0;
        }
        int before = profile.notifications.size();
        profile.notifications.removeIf(Notification::read);
        int removed = before - profile.notifications.size();
        if (removed > 0) {
            markChanged(profile, true);
        }
        return removed;
    }

    public synchronized java.util.Optional<MarketProfileMutationReceipt>
    mutationReceipt(
            UUID playerId,
            UUID requestId
    ) {
        MutableProfile profile = profiles.get(requireUuid(
                playerId, "playerId"));
        UUID request = requireUuid(requestId, "requestId");
        return profile == null ? java.util.Optional.empty()
                : java.util.Optional.ofNullable(
                profile.mutationReceipts.get(request));
    }

    public synchronized void recordMutationReceipt(
            UUID playerId,
            MarketProfileMutationReceipt receipt
    ) {
        UUID player = requireUuid(playerId, "playerId");
        MarketProfileMutationReceipt value = Objects.requireNonNull(
                receipt, "receipt");
        if (!value.ownerId().equals(player)) {
            throw new IllegalArgumentException(
                    "Market profile receipt owner is invalid");
        }
        MutableProfile profile = profile(player);
        MarketProfileMutationReceipt existing =
                profile.mutationReceipts.get(value.requestId());
        if (existing != null) {
            if (!existing.equals(value)) {
                throw new IllegalStateException(
                        "Market profile request identity was reused");
            }
            return;
        }
        if (profile.mutationTombstones.containsKey(value.requestId())) {
            throw new IllegalStateException(
                    "Market profile request identity was retired");
        }
        ensureMutationReceiptCapacity(profile);
        profile.mutationReceipts.put(value.requestId(), value);
        while (profile.mutationReceipts.size()
                > MAX_MUTATION_RECEIPTS) {
            UUID eldest = profile.mutationReceipts.keySet()
                    .iterator().next();
            MarketProfileMutationReceipt retired =
                    profile.mutationReceipts.remove(eldest);
            retireMutationRequest(profile, retired.requestId(),
                    retired.fingerprint());
        }
        setDirty();
    }

    public synchronized RetiredMutationRequest retiredMutationRequest(
            UUID playerId,
            UUID requestId,
            String fingerprint
    ) {
        MutableProfile profile = profiles.get(requireUuid(
                playerId, "playerId"));
        UUID request = requireUuid(requestId, "requestId");
        String requestFingerprint = requireFingerprint(fingerprint);
        if (profile == null) {
            return RetiredMutationRequest.NONE;
        }
        String retired = profile.mutationTombstones.get(request);
        if (retired != null) {
            return retired.equals(requestFingerprint)
                    ? RetiredMutationRequest.EXACT
                    : RetiredMutationRequest.CONFLICT;
        }
        return RetiredMutationRequest.NONE;
    }

    public synchronized Snapshot prepareMutationReceipt(UUID playerId) {
        MutableProfile profile = profile(playerId);
        ensureMutationReceiptCapacity(profile);
        return profile.snapshot();
    }

    private MutableProfile profile(UUID playerId) {
        UUID safeId = requireUuid(playerId, "playerId");
        MutableProfile existing = profiles.get(safeId);
        if (existing != null) {
            return existing;
        }
        if (profiles.size() >= MAX_PROFILES) {
            throw new IllegalStateException(
                    "Market profile player limit is exceeded");
        }
        MutableProfile created = new MutableProfile();
        profiles.put(safeId, created);
        return created;
    }

    private void markChanged(
            MutableProfile profile,
            boolean changed
    ) {
        if (changed) {
            profile.revision = Math.incrementExact(profile.revision);
            setDirty();
        }
    }

    private static boolean appendNotification(
            MutableProfile profile,
            Notification notification
    ) {
        List<Notification> before = List.copyOf(profile.notifications);
        profile.notifications.removeIf(existing ->
                existing.notificationId().equals(
                        notification.notificationId()));
        profile.notifications.add(0, notification);
        trimTail(profile.notifications, MAX_NOTIFICATIONS);
        return !before.equals(profile.notifications);
    }

    private static CompoundTag writeProfile(
            UUID playerId,
            MutableProfile profile
    ) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("player", playerId);
        tag.putLong("revision", profile.revision);
        ListTag auctions = new ListTag();
        for (UUID listingId : profile.watchedAuctions) {
            CompoundTag value = new CompoundTag();
            value.putUUID("id", listingId);
            auctions.add(value);
        }
        tag.put("watchedAuctions", auctions);
        tag.put("favoriteProducts", writeProducts(
                profile.favoriteProducts));
        tag.put("recentProducts", writeProducts(
                profile.recentProducts));
        ListTag alerts = new ListTag();
        for (PriceAlert alert : profile.priceAlerts.values()) {
            alerts.add(writeAlert(alert));
        }
        tag.put("priceAlerts", alerts);
        ListTag notifications = new ListTag();
        for (Notification notification : profile.notifications) {
            notifications.add(writeNotification(notification));
        }
        tag.put("notifications", notifications);
        ListTag receipts = new ListTag();
        for (MarketProfileMutationReceipt receipt
                : profile.mutationReceipts.values()) {
            receipts.add(writeMutationReceipt(receipt));
        }
        tag.put("mutationReceipts", receipts);
        ListTag tombstones = new ListTag();
        for (Map.Entry<UUID, String> tombstone
                : profile.mutationTombstones.entrySet()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("request", tombstone.getKey());
            value.putString("fingerprint", tombstone.getValue());
            tombstones.add(value);
        }
        tag.put("mutationTombstones", tombstones);
        tag.putLong("mutationReplayEpoch", profile.mutationReplayEpoch);
        return tag;
    }

    private static MutableProfile readProfile(
            CompoundTag tag,
            int version,
            UUID playerId
    ) {
        ListTag watchedAuctions = SavedDataMigrations.requireList(
                tag, "watchedAuctions", Tag.TAG_COMPOUND,
                MAX_WATCHED_AUCTIONS, "Market watched auctions");
        ListTag favoriteProducts = SavedDataMigrations.requireList(
                tag, "favoriteProducts", Tag.TAG_COMPOUND,
                MAX_FAVORITE_PRODUCTS, "Market favorite products");
        ListTag recentProducts = SavedDataMigrations.requireList(
                tag, "recentProducts", Tag.TAG_COMPOUND,
                MAX_RECENT_PRODUCTS, "Market recent products");
        ListTag priceAlerts = SavedDataMigrations.requireList(
                tag, "priceAlerts", Tag.TAG_COMPOUND,
                MAX_PRICE_ALERTS, "Market price alerts");
        ListTag notifications = SavedDataMigrations.requireList(
                tag, "notifications", Tag.TAG_COMPOUND,
                MAX_NOTIFICATIONS, "Market notifications");
        for (String key : List.of("watchedAuctions", "favoriteProducts",
                "recentProducts", "priceAlerts", "notifications")) {
            requireType(tag, key, Tag.TAG_LIST);
        }
        ListTag mutationReceipts = new ListTag();
        ListTag mutationTombstones = new ListTag();
        if (version >= 2) {
            requireType(tag, "mutationReceipts", Tag.TAG_LIST);
            mutationReceipts = SavedDataMigrations.requireList(
                    tag, "mutationReceipts", Tag.TAG_COMPOUND,
                    MAX_MUTATION_RECEIPTS, "Market mutation receipts");
            requireType(tag, "mutationTombstones", Tag.TAG_LIST);
            mutationTombstones = SavedDataMigrations.requireList(
                    tag, "mutationTombstones", Tag.TAG_COMPOUND,
                    MAX_MUTATION_TOMBSTONES, "Market mutation tombstones");
            if (version == 2) {
                requireType(tag, "mutationReplayFilter",
                        Tag.TAG_LONG_ARRAY);
                requireType(tag, "mutationReplayFilterVersion",
                        Tag.TAG_INT);
                requireType(tag, "mutationReplayFilterBits", Tag.TAG_INT);
                requireType(tag, "mutationReplayFilterHashes",
                        Tag.TAG_INT);
            } else {
                requireType(tag, "mutationReplayEpoch", Tag.TAG_LONG);
            }
        }
        MutableProfile profile = new MutableProfile();
        if (version >= 2) {
            requireType(tag, "revision", Tag.TAG_LONG);
            profile.revision = tag.getLong("revision");
            if (profile.revision < 0L) {
                throw new IllegalStateException(
                        "Market profile revision is invalid");
            }
            profile.mutationReplayEpoch = version == 2 ? 1L
                    : tag.getLong("mutationReplayEpoch");
            if (profile.mutationReplayEpoch < 0L) {
                throw new IllegalStateException(
                        "Market profile replay epoch is invalid");
            }
        }
        for (int index = 0; index < watchedAuctions.size(); index++) {
            CompoundTag value = watchedAuctions.getCompound(index);
            if (!value.hasUUID("id")
                    || !profile.watchedAuctions.add(requireUuid(
                    value.getUUID("id"), "listingId"))) {
                throw new IllegalStateException(
                        "Market watched auction is invalid");
            }
        }
        readProducts(favoriteProducts, profile.favoriteProducts,
                MAX_FAVORITE_PRODUCTS);
        readProducts(recentProducts, profile.recentProducts,
                MAX_RECENT_PRODUCTS);
        for (int index = 0; index < priceAlerts.size(); index++) {
            PriceAlert alert = readAlert(priceAlerts.getCompound(index));
            if (profile.priceAlerts.put(alert.alertId(), alert) != null) {
                throw new IllegalStateException(
                        "Market price alert is duplicated");
            }
        }
        Set<UUID> notificationIds = new LinkedHashSet<>();
        for (int index = 0; index < notifications.size(); index++) {
            Notification notification = readNotification(
                    notifications.getCompound(index));
            if (!notificationIds.add(notification.notificationId())) {
                throw new IllegalStateException(
                        "Market notification is duplicated");
            }
            profile.notifications.add(notification);
        }
        if (version >= 2) {
            for (int index = 0; index < mutationReceipts.size(); index++) {
                MarketProfileMutationReceipt receipt =
                        readMutationReceipt(mutationReceipts.getCompound(index),
                                version);
                if (!receipt.ownerId().equals(playerId)) {
                    throw new IllegalStateException(
                            "Market profile receipt owner is invalid");
                }
                if (profile.mutationReceipts.put(
                        receipt.requestId(), receipt) != null) {
                    throw new IllegalStateException(
                            "Market profile receipt is duplicated");
                }
            }
            for (int index = 0; index < mutationTombstones.size(); index++) {
                CompoundTag encoded = mutationTombstones.getCompound(index);
                if (!encoded.hasUUID("request")) {
                    throw new IllegalStateException(
                            "Market profile tombstone identity is missing");
                }
                requireType(encoded, "fingerprint", Tag.TAG_STRING);
                UUID requestId = requireUuid(
                        encoded.getUUID("request"), "requestId");
                String fingerprint = requireFingerprint(
                        encoded.getString("fingerprint"));
                if (profile.mutationReceipts.containsKey(requestId)
                        || profile.mutationTombstones.put(requestId,
                        fingerprint) != null) {
                    throw new IllegalStateException(
                            "Market profile tombstone is duplicated");
                }
            }
            if (version == 2) {
                long[] replayFilter = tag.getLongArray(
                        "mutationReplayFilter");
                if (tag.getInt("mutationReplayFilterVersion")
                        != LEGACY_REPLAY_FILTER_VERSION
                        || tag.getInt("mutationReplayFilterBits")
                        != LEGACY_REPLAY_FILTER_BITS
                        || tag.getInt("mutationReplayFilterHashes")
                        != LEGACY_REPLAY_FILTER_HASHES
                        || replayFilter.length != 0
                        && replayFilter.length
                        != LEGACY_REPLAY_FILTER_WORDS) {
                    throw new IllegalStateException(
                            "Market profile replay filter is invalid");
                }
            }
        }
        return profile;
    }

    private static void retireMutationRequest(
            MutableProfile profile,
            UUID requestId,
            String fingerprint
    ) {
        requireUuid(requestId, "requestId");
        String value = requireFingerprint(fingerprint);
        String existing = profile.mutationTombstones.putIfAbsent(
                requestId, value);
        if (existing != null && !existing.equals(value)) {
            throw new IllegalStateException(
                    "Market profile tombstone conflicts");
        }
    }

    private static void ensureMutationReceiptCapacity(
            MutableProfile profile) {
        if (profile.mutationReceipts.size() >= MAX_MUTATION_RECEIPTS
                && profile.mutationTombstones.size()
                >= MAX_MUTATION_TOMBSTONES) {
            profile.mutationReplayEpoch = Math.incrementExact(
                    profile.mutationReplayEpoch);
            profile.mutationTombstones.clear();
        }
    }

    private static String requireFingerprint(String fingerprint) {
        String value = Objects.requireNonNull(fingerprint,
                "fingerprint");
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "Market profile request fingerprint is invalid");
        }
        return value;
    }

    private static CompoundTag writeMutationReceipt(
            MarketProfileMutationReceipt receipt
    ) {
        MarketProfileMutationResult result = receipt.result();
        CompoundTag tag = new CompoundTag();
        tag.putUUID("owner", receipt.ownerId());
        tag.putString("fingerprint", receipt.fingerprint());
        tag.putUUID("request", result.requestId());
        tag.putUUID("route", result.routeNonce());
        tag.putString("module", result.module().name());
        tag.putString("type", result.type().name());
        tag.putString("result", result.resultCode().name());
        tag.putLong("revision", result.profileRevision());
        tag.putLong("replayEpoch", result.replayEpoch());
        tag.putInt("watched", result.watchedAuctionCount());
        tag.putInt("favorites", result.favoriteProductCount());
        tag.putInt("alerts", result.priceAlertCount());
        tag.putInt("notifications", result.notificationCount());
        tag.putInt("unread", result.unreadNotificationCount());
        tag.putInt("affected", result.affectedCount());
        tag.putBoolean("changed", result.changed());
        return tag;
    }

    private static MarketProfileMutationReceipt readMutationReceipt(
            CompoundTag tag,
            int version
    ) {
        if (!tag.hasUUID("owner") || !tag.hasUUID("request")
                || !tag.hasUUID("route")) {
            throw new IllegalStateException(
                    "Market profile receipt identity is missing");
        }
        for (Map.Entry<String, Byte> field : Map.ofEntries(
                Map.entry("fingerprint", Tag.TAG_STRING),
                Map.entry("module", Tag.TAG_STRING),
                Map.entry("type", Tag.TAG_STRING),
                Map.entry("result", Tag.TAG_STRING),
                Map.entry("revision", Tag.TAG_LONG),
                Map.entry("watched", Tag.TAG_INT),
                Map.entry("favorites", Tag.TAG_INT),
                Map.entry("alerts", Tag.TAG_INT),
                Map.entry("notifications", Tag.TAG_INT),
                Map.entry("unread", Tag.TAG_INT),
                Map.entry("affected", Tag.TAG_INT),
                Map.entry("changed", Tag.TAG_BYTE)).entrySet()) {
            requireType(tag, field.getKey(), field.getValue());
        }
        if (version >= 3) {
            requireType(tag, "replayEpoch", Tag.TAG_LONG);
        }
        try {
            MarketProfileMutationResult result =
                    new MarketProfileMutationResult(
                            requireUuid(tag.getUUID("request"),
                                    "requestId"),
                            requireUuid(tag.getUUID("route"),
                                    "routeNonce"),
                            com.enviouse.futureshops.client.market
                                    .MarketModule.valueOf(
                                    tag.getString("module")),
                            MarketProfileMutationType.valueOf(
                                    tag.getString("type")),
                            MarketProfileMutationResultCode.valueOf(
                                    tag.getString("result")),
                            tag.getLong("revision"),
                            version >= 3 ? tag.getLong("replayEpoch") : 0L,
                            tag.getInt("watched"),
                            tag.getInt("favorites"),
                            tag.getInt("alerts"),
                            tag.getInt("notifications"),
                            tag.getInt("unread"),
                            tag.getInt("affected"),
                            tag.getBoolean("changed"), false);
            return new MarketProfileMutationReceipt(
                    requireUuid(tag.getUUID("owner"), "ownerId"),
                    tag.getString("fingerprint"), result);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Market profile receipt is invalid", exception);
        }
    }

    private static ListTag writeProducts(Iterable<ProductKey> products) {
        ListTag values = new ListTag();
        for (ProductKey product : products) {
            CompoundTag tag = new CompoundTag();
            tag.putString("product", product.productId());
            tag.putLong("version", product.version());
            values.add(tag);
        }
        return values;
    }

    private static void readProducts(
            ListTag values,
            java.util.Collection<ProductKey> destination,
            int limit
    ) {
        if (values.size() > limit) {
            throw new IllegalStateException(
                    "Market product collection limit is exceeded");
        }
        Set<ProductKey> unique = new LinkedHashSet<>();
        for (int index = 0; index < values.size(); index++) {
            CompoundTag tag = values.getCompound(index);
            requireType(tag, "product", Tag.TAG_STRING);
            requireType(tag, "version", Tag.TAG_LONG);
            ProductKey product = new ProductKey(
                    tag.getString("product"), tag.getLong("version"));
            if (!unique.add(product)) {
                throw new IllegalStateException(
                        "Market product collection is duplicated");
            }
        }
        destination.addAll(unique);
    }

    private static CompoundTag writeAlert(PriceAlert alert) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", alert.alertId());
        tag.putString("product", alert.product().productId());
        tag.putLong("version", alert.product().version());
        tag.putString("direction", alert.direction().name());
        tag.putLong("threshold", alert.thresholdMinor());
        tag.putBoolean("enabled", alert.enabled());
        tag.putLong("createdAt", alert.createdAtMillis());
        tag.putLong("triggeredAt", alert.lastTriggeredAtMillis());
        return tag;
    }

    private static PriceAlert readAlert(CompoundTag tag) {
        if (!tag.hasUUID("id")) {
            throw new IllegalStateException(
                    "Market price alert identity is missing");
        }
        for (Map.Entry<String, Byte> field : Map.of(
                "product", Tag.TAG_STRING,
                "version", Tag.TAG_LONG,
                "direction", Tag.TAG_STRING,
                "threshold", Tag.TAG_LONG,
                "enabled", Tag.TAG_BYTE,
                "createdAt", Tag.TAG_LONG,
                "triggeredAt", Tag.TAG_LONG).entrySet()) {
            requireType(tag, field.getKey(), field.getValue());
        }
        try {
            return new PriceAlert(requireUuid(tag.getUUID("id"),
                    "alertId"), new ProductKey(tag.getString("product"),
                    tag.getLong("version")), AlertDirection.valueOf(
                    tag.getString("direction")),
                    tag.getLong("threshold"), tag.getBoolean("enabled"),
                    tag.getLong("createdAt"), tag.getLong("triggeredAt"));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Market price alert is invalid", exception);
        }
    }

    private static CompoundTag writeNotification(
            Notification notification
    ) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", notification.notificationId());
        tag.putString("module", notification.module().name());
        tag.putString("kind", notification.kind().name());
        tag.putString("reference", notification.reference());
        tag.putString("title", notification.titleKey());
        tag.putString("detail", notification.detail());
        tag.putLong("createdAt", notification.createdAtMillis());
        tag.putBoolean("read", notification.read());
        return tag;
    }

    private static Notification readNotification(CompoundTag tag) {
        if (!tag.hasUUID("id")) {
            throw new IllegalStateException(
                    "Market notification identity is missing");
        }
        for (Map.Entry<String, Byte> field : Map.of(
                "module", Tag.TAG_STRING,
                "kind", Tag.TAG_STRING,
                "reference", Tag.TAG_STRING,
                "title", Tag.TAG_STRING,
                "detail", Tag.TAG_STRING,
                "createdAt", Tag.TAG_LONG,
                "read", Tag.TAG_BYTE).entrySet()) {
            requireType(tag, field.getKey(), field.getValue());
        }
        try {
            return new Notification(requireUuid(tag.getUUID("id"),
                    "notificationId"), Module.valueOf(
                    tag.getString("module")), NotificationKind.valueOf(
                    tag.getString("kind")), tag.getString("reference"),
                    tag.getString("title"), tag.getString("detail"),
                    tag.getLong("createdAt"), tag.getBoolean("read"));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Market notification is invalid", exception);
        }
    }

    private static void requireType(
            CompoundTag tag,
            String key,
            int type
    ) {
        if (!tag.contains(key, type)) {
            throw new IllegalStateException(
                    "Market profile field has the wrong type");
        }
    }

    private static void requireCompoundList(Tag tag, String label) {
        if (tag instanceof ListTag list && !list.isEmpty()
                && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalStateException(
                    "Market profile " + label + " list is malformed");
        }
    }

    private static UUID requireUuid(UUID value, String label) {
        UUID safe = Objects.requireNonNull(value, label);
        if (ZERO.equals(safe)) {
            throw new IllegalArgumentException(
                    "Market profile " + label + " is invalid");
        }
        return safe;
    }

    private static String requireText(
            String value,
            int maximum,
            String label
    ) {
        String safe = Objects.requireNonNull(value, label).strip();
        if (safe.isEmpty() || safe.length() > maximum
                || !wellFormedUtf16(safe)) {
            throw new IllegalArgumentException(
                    "Market profile " + label + " is invalid");
        }
        return safe;
    }

    private static boolean wellFormedUtf16(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(
                        value.charAt(index + 1))) {
                    return false;
                }
                index++;
            } else if (Character.isLowSurrogate(character)) {
                return false;
            }
        }
        return true;
    }

    private static <T> void trimFirst(Set<T> values, int limit) {
        while (values.size() > limit) {
            T first = values.iterator().next();
            values.remove(first);
        }
    }

    private static <T> void trimTail(List<T> values, int limit) {
        if (values.size() > limit) {
            values.subList(limit, values.size()).clear();
        }
    }

    private static UUID notificationId(UUID alertId, long nowMillis) {
        return UUID.nameUUIDFromBytes(("market alert notification\u0000"
                + alertId + "\u0000" + nowMillis).getBytes(
                java.nio.charset.StandardCharsets.UTF_8));
    }

    public record ProductKey(String productId, long version) {
        public ProductKey {
            productId = requireText(productId, 160, "productId");
            if (version <= 0L) {
                throw new IllegalArgumentException(
                        "Market product version is invalid");
            }
        }
    }

    public record PriceAlert(
            UUID alertId,
            ProductKey product,
            AlertDirection direction,
            long thresholdMinor,
            boolean enabled,
            long createdAtMillis,
            long lastTriggeredAtMillis
    ) {
        public PriceAlert {
            alertId = requireUuid(alertId, "alertId");
            product = Objects.requireNonNull(product, "product");
            direction = Objects.requireNonNull(direction, "direction");
            if (thresholdMinor <= 0L || createdAtMillis < 0L
                    || lastTriggeredAtMillis < -1L
                    || lastTriggeredAtMillis >= 0L
                    && lastTriggeredAtMillis < createdAtMillis) {
                throw new IllegalArgumentException(
                        "Market price alert values are invalid");
            }
        }

        public PriceAlert triggered(long nowMillis) {
            if (nowMillis < createdAtMillis) {
                throw new IllegalArgumentException(
                        "Market price alert time is invalid");
            }
            return new PriceAlert(alertId, product, direction,
                    thresholdMinor, false, createdAtMillis, nowMillis);
        }
    }

    public record Notification(
            UUID notificationId,
            Module module,
            NotificationKind kind,
            String reference,
            String titleKey,
            String detail,
            long createdAtMillis,
            boolean read
    ) {
        public Notification {
            notificationId = requireUuid(notificationId,
                    "notificationId");
            module = Objects.requireNonNull(module, "module");
            kind = Objects.requireNonNull(kind, "kind");
            reference = requireText(reference, 192, "reference");
            titleKey = requireText(titleKey, 192, "titleKey");
            detail = Objects.requireNonNull(detail, "detail");
            if (detail.length() > 512 || !wellFormedUtf16(detail)
                    || createdAtMillis < 0L) {
                throw new IllegalArgumentException(
                        "Market notification values are invalid");
            }
        }

        public Notification markRead() {
            return read ? this : new Notification(notificationId,
                    module, kind, reference, titleKey, detail,
                    createdAtMillis, true);
        }
    }

    public record Snapshot(
            List<UUID> watchedAuctions,
            List<ProductKey> favoriteProducts,
            List<ProductKey> recentProducts,
            List<PriceAlert> priceAlerts,
            List<Notification> notifications,
            long revision,
            long replayEpoch
    ) {
        public Snapshot {
            watchedAuctions = List.copyOf(watchedAuctions);
            favoriteProducts = List.copyOf(favoriteProducts);
            recentProducts = List.copyOf(recentProducts);
            priceAlerts = List.copyOf(priceAlerts);
            notifications = List.copyOf(notifications);
            if (revision < 0L || replayEpoch < 0L) {
                throw new IllegalArgumentException(
                        "Market profile revision is invalid");
            }
        }

        public Snapshot(
                List<UUID> watchedAuctions,
                List<ProductKey> favoriteProducts,
                List<ProductKey> recentProducts,
                List<PriceAlert> priceAlerts,
                List<Notification> notifications
        ) {
            this(watchedAuctions, favoriteProducts, recentProducts,
                    priceAlerts, notifications, 0L, 0L);
        }

        public Snapshot(
                List<UUID> watchedAuctions,
                List<ProductKey> favoriteProducts,
                List<ProductKey> recentProducts,
                List<PriceAlert> priceAlerts,
                List<Notification> notifications,
                long revision
        ) {
            this(watchedAuctions, favoriteProducts, recentProducts,
                    priceAlerts, notifications, revision, 0L);
        }

        public static Snapshot empty() {
            return new Snapshot(List.of(), List.of(), List.of(),
                    List.of(), List.of(), 0L, 0L);
        }

        public int unreadNotifications() {
            return Math.toIntExact(notifications.stream()
                    .filter(value -> !value.read()).count());
        }
    }

    public enum AlertDirection {
        AT_OR_ABOVE {
            @Override
            boolean matches(long price, long threshold) {
                return price >= threshold;
            }
        },
        AT_OR_BELOW {
            @Override
            boolean matches(long price, long threshold) {
                return price <= threshold;
            }
        };

        abstract boolean matches(long price, long threshold);
    }

    public enum Module {
        AUCTION_HOUSE,
        BAZAAR
    }

    public enum NotificationKind {
        OUTBID,
        AUCTION_SOLD,
        AUCTION_ENDING,
        ORDER_FILLED,
        ORDER_PARTIALLY_FILLED,
        ORDER_EXPIRED,
        PRICE_ALERT,
        CLAIM_READY,
        MODERATION
    }

    public enum RetiredMutationRequest {
        NONE,
        EXACT,
        CONFLICT
    }

    private static final class MutableProfile {
        private final LinkedHashSet<UUID> watchedAuctions =
                new LinkedHashSet<>();
        private final LinkedHashSet<ProductKey> favoriteProducts =
                new LinkedHashSet<>();
        private final List<ProductKey> recentProducts = new ArrayList<>();
        private final LinkedHashMap<UUID, PriceAlert> priceAlerts =
                new LinkedHashMap<>();
        private final List<Notification> notifications =
                new ArrayList<>();
        private final LinkedHashMap<UUID, MarketProfileMutationReceipt>
                mutationReceipts = new LinkedHashMap<>();
        private final LinkedHashMap<UUID, String> mutationTombstones =
                new LinkedHashMap<>();
        private long mutationReplayEpoch;
        private long revision;

        private Snapshot snapshot() {
            return new Snapshot(new ArrayList<>(watchedAuctions),
                    new ArrayList<>(favoriteProducts), recentProducts,
                    new ArrayList<>(priceAlerts.values()),
                    notifications, revision, mutationReplayEpoch);
        }
    }
}
