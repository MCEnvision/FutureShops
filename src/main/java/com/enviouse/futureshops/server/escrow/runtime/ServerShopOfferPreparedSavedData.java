package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.catalog.offer.OfferAction;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.network.ServerShopOfferNetworkCodec;
import com.enviouse.futureshops.server.SavedDataMigrations;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopEscrowIntent;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopIntentCodec;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopOperation;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopPaymentSource;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommand;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommandCodec;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
import com.enviouse.futureshops.server.escrow.stock.StockReservationDirection;
import com.enviouse.futureshops.server.escrow.stock.StockReservationRequest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ServerShopOfferPreparedSavedData extends SavedData {
    private static final String DATA_ID =
            "futureshops_server_shop_offer_prepared";
    private static final int CURRENT_VERSION = 2;
    private static final int MAXIMUM_ENTRIES = 10_000;
    private static final int MAXIMUM_ARCHIVES = 65_536;
    private static final int MAXIMUM_ARCHIVES_PER_PLAYER = 4_096;
    private static final org.slf4j.Logger LOGGER =
            com.mojang.logging.LogUtils.getLogger();
    private final LinkedHashMap<UUID, Entry> entries =
            new LinkedHashMap<>();
    private final LinkedHashMap<UUID, ServerShopOfferReplayReceipt> archives =
            new LinkedHashMap<>();
    private final Map<UUID, LinkedHashSet<UUID>> unresolvedByPlayer =
            new LinkedHashMap<>();
    private final java.util.Map<UUID, Integer> archivePlayerCounts =
            new java.util.HashMap<>();
    private final int maximumEntries;
    private final int maximumArchives;

    public ServerShopOfferPreparedSavedData() {
        this(MAXIMUM_ENTRIES, MAXIMUM_ARCHIVES);
    }

    ServerShopOfferPreparedSavedData(int maximumEntries) {
        this(maximumEntries, MAXIMUM_ARCHIVES);
    }

    ServerShopOfferPreparedSavedData(
            int maximumEntries,
            int maximumArchives
    ) {
        if (maximumEntries <= 0
                || maximumEntries > MAXIMUM_ENTRIES
                || maximumArchives <= 0
                || maximumArchives > MAXIMUM_ARCHIVES) {
            throw new IllegalArgumentException(
                    "Prepared server offer repository limit is invalid");
        }
        this.maximumEntries = maximumEntries;
        this.maximumArchives = maximumArchives;
    }

    public static ServerShopOfferPreparedSavedData get(
            MinecraftServer server
    ) {
        return server.overworld().getDataStorage().computeIfAbsent(
                ServerShopOfferPreparedSavedData::load,
                ServerShopOfferPreparedSavedData::new,
                DATA_ID);
    }

    public synchronized Optional<Entry> find(UUID requestId) {
        return Optional.ofNullable(entries.get(requestId));
    }

    public synchronized Optional<ServerShopOfferReplayReceipt> findArchived(
            UUID requestId
    ) {
        return Optional.ofNullable(archives.get(requestId));
    }

    public synchronized boolean canPrepare(UUID requestId) {
        java.util.Objects.requireNonNull(requestId, "requestId");
        return entries.containsKey(requestId)
                || archives.containsKey(requestId)
                || entries.size() < maximumEntries;
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized List<Entry> takeUnresolvedForPlayer(
            UUID playerId,
            int limit
    ) {
        java.util.Objects.requireNonNull(playerId, "playerId");
        if (limit <= 0 || limit > 64) {
            throw new IllegalArgumentException(
                    "Prepared server offer recovery limit is invalid");
        }
        LinkedHashSet<UUID> requestIds =
                unresolvedByPlayer.get(playerId);
        if (requestIds == null || requestIds.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<Entry> result =
                new java.util.ArrayList<>(Math.min(
                        limit, requestIds.size()));
        int examined = 0;
        int available = requestIds.size();
        while (examined < available && result.size() < limit) {
            java.util.Iterator<UUID> iterator = requestIds.iterator();
            UUID requestId = iterator.next();
            Entry entry = entries.get(requestId);
            iterator.remove();
            examined++;
            if (entry != null && !archives.containsKey(requestId)) {
                result.add(entry);
                requestIds.add(requestId);
            }
        }
        if (requestIds.isEmpty()) {
            unresolvedByPlayer.remove(playerId);
        }
        return List.copyOf(result);
    }

    public synchronized boolean canRecordReplayReceipt(UUID requestId) {
        return canRecordReplayReceipt(requestId, null);
    }

    public synchronized boolean canRecordReplayReceipt(
            UUID requestId,
            UUID playerId
    ) {
        java.util.Objects.requireNonNull(requestId, "requestId");
        return archives.containsKey(requestId)
                || archives.size() < maximumArchives
                && (playerId == null
                || archivePlayerCounts.getOrDefault(
                playerId, 0) < MAXIMUM_ARCHIVES_PER_PLAYER);
    }

    public synchronized boolean recordReplayReceipt(
            ServerShopOfferReplayReceipt receipt
    ) {
        java.util.Objects.requireNonNull(receipt, "receipt");
        Entry entry = entries.get(receipt.requestId());
        if (entry == null || !receipt.matches(entry)) {
            throw new IllegalStateException(
                    "Prepared server offer replay receipt has no matching entry");
        }
        ServerShopOfferReplayReceipt existing =
                archives.get(receipt.requestId());
        if (existing != null) {
            if (!existing.equals(receipt)) {
                throw new IllegalStateException(
                        "Prepared server offer replay receipt conflicts");
            }
            return false;
        }
        if (archives.size() >= maximumArchives) {
            throw new IllegalStateException(
                    "Prepared server offer replay archive is full");
        }
        int playerCount = archivePlayerCounts.getOrDefault(
                receipt.playerId(), 0);
        if (playerCount >= MAXIMUM_ARCHIVES_PER_PLAYER) {
            throw new IllegalStateException(
                    "Prepared server offer replay player archive is full");
        }
        archives.put(receipt.requestId(), receipt);
        archivePlayerCounts.put(
                receipt.playerId(), Math.addExact(playerCount, 1));
        removeUnresolved(receipt.playerId(), receipt.requestId());
        setDirty();
        return true;
    }

    public synchronized Optional<UUID> compactOldestReplay() {
        java.util.Iterator<java.util.Map.Entry<UUID, Entry>> iterator =
                entries.entrySet().iterator();
        while (iterator.hasNext()) {
            java.util.Map.Entry<UUID, Entry> entry =
                    iterator.next();
            ServerShopOfferReplayReceipt receipt =
                    archives.get(entry.getKey());
            if (receipt != null && receipt.matches(entry.getValue())) {
                UUID requestId = entry.getKey();
                iterator.remove();
                removeUnresolved(
                        entry.getValue().playerId(), requestId);
                setDirty();
                return Optional.of(requestId);
            }
        }
        return Optional.empty();
    }

    public synchronized Optional<UUID> compactOldestReplay(
            ServerShopOfferReplayLedger ledger
    ) {
        java.util.Objects.requireNonNull(ledger, "ledger");
        java.util.Iterator<java.util.Map.Entry<UUID, Entry>> iterator =
                entries.entrySet().iterator();
        while (iterator.hasNext()) {
            java.util.Map.Entry<UUID, Entry> entry =
                    iterator.next();
            Optional<ServerShopOfferReplayReceipt> receipt =
                    ledger.find(entry.getKey());
            if (receipt.isPresent()
                    && receipt.orElseThrow().matches(
                    entry.getValue())) {
                UUID requestId = entry.getKey();
                iterator.remove();
                removeUnresolved(
                        entry.getValue().playerId(), requestId);
                setDirty();
                return Optional.of(requestId);
            }
        }
        return Optional.empty();
    }

    public synchronized void prepare(Entry entry) {
        ServerShopOfferReplayReceipt archived =
                archives.get(entry.requestId());
        if (archived != null) {
            if (!archived.matches(entry)) {
                throw new IllegalStateException(
                        "Prepared server offer archived identity conflicts");
            }
            return;
        }
        Entry existing = entries.get(entry.requestId());
        if (existing != null) {
            if (!existing.equals(entry)) {
                throw new IllegalStateException(
                        "Server shop prepared offer identity conflicts");
            }
            return;
        }
        if (entries.size() >= maximumEntries) {
            throw new IllegalStateException(
                    "Prepared server offer repository is full");
        }
        entries.put(entry.requestId(), entry);
        unresolvedByPlayer.computeIfAbsent(
                entry.playerId(), ignored -> new LinkedHashSet<>())
                .add(entry.requestId());
        setDirty();
    }

    public synchronized void remove(UUID requestId) {
        Entry removed = entries.remove(requestId);
        if (removed != null) {
            removeUnresolved(removed.playerId(), requestId);
            setDirty();
        }
    }

    private void removeUnresolved(UUID playerId, UUID requestId) {
        LinkedHashSet<UUID> requestIds =
                unresolvedByPlayer.get(playerId);
        if (requestIds == null) {
            return;
        }
        requestIds.remove(requestId);
        if (requestIds.isEmpty()) {
            unresolvedByPlayer.remove(playerId);
        }
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        ListTag values = new ListTag();
        entries.values().forEach(entry ->
                values.add(encodeEntry(entry)));
        tag.put("Entries", values);
        ListTag archived = new ListTag();
        for (ServerShopOfferReplayReceipt receipt
                : archives.values()) {
            archived.add(receipt.save());
        }
        tag.put("Archives", archived);
        return tag;
    }

    public static ServerShopOfferPreparedSavedData load(CompoundTag tag) {
        int loadedVersion = SavedDataMigrations.readVersion(tag);
        if (loadedVersion > CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Prepared server offer version is unsupported");
        }
        SavedDataMigrations.needsMigration(
                DATA_ID, loadedVersion, CURRENT_VERSION);
        ServerShopOfferPreparedSavedData data =
                new ServerShopOfferPreparedSavedData();
        ListTag values = SavedDataMigrations.requireList(
                tag, "Entries", Tag.TAG_COMPOUND,
                MAXIMUM_ENTRIES, "Prepared server offer entries");
        for (int index = 0;
             index < values.size()
                     && data.entries.size() < MAXIMUM_ENTRIES;
             index++) {
            try {
                Entry entry = decodeEntry(values.getCompound(index));
                if (data.entries.putIfAbsent(
                        entry.requestId(), entry) != null) {
                    throw new IllegalArgumentException(
                            "Duplicate prepared server offer");
                }
                data.unresolvedByPlayer.computeIfAbsent(
                        entry.playerId(),
                        ignored -> new LinkedHashSet<>())
                        .add(entry.requestId());
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException(
                        "Prepared server offer row " + index
                                + " is invalid", exception);
            }
        }
        if (loadedVersion >= 2) {
            ListTag archived = SavedDataMigrations.requireList(
                    tag, "Archives", Tag.TAG_COMPOUND,
                    MAXIMUM_ARCHIVES, "Prepared server offer replay archives");
            for (int index = 0;
                 index < archived.size(); index++) {
                try {
                    ServerShopOfferReplayReceipt receipt =
                            ServerShopOfferReplayReceipt.load(
                                    archived.getCompound(index));
                    if (receipt.kind()
                            != ServerShopOfferReplayReceipt.Kind.SINGLE
                            || data.entries.containsKey(receipt.requestId())
                            && !receipt.matches(data.entries.get(
                            receipt.requestId()))
                            || data.archives.putIfAbsent(
                            receipt.requestId(), receipt) != null) {
                        throw new IllegalArgumentException(
                                "Prepared server offer replay archive conflicts");
                    }
                    data.removeUnresolved(
                            receipt.playerId(), receipt.requestId());
                    int playerCount = data.archivePlayerCounts.merge(
                            receipt.playerId(), 1, Math::addExact);
                    if (playerCount
                            > MAXIMUM_ARCHIVES_PER_PLAYER) {
                        throw new IllegalArgumentException(
                                "Prepared server offer replay player archive is full");
                    }
                } catch (RuntimeException exception) {
                    throw new IllegalArgumentException(
                            "Prepared server offer replay archive row "
                                    + index + " is invalid", exception);
                }
            }
        }
        return data;
    }

    private static CompoundTag encodeEntry(Entry entry) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Request", entry.requestId());
        tag.putUUID("Player", entry.playerId());
        tag.putString("Shop", entry.shopId());
        tag.putString("Listing", entry.listingId());
        tag.putString("Option", entry.optionId());
        tag.putString("Action", entry.action().name());
        tag.putInt("Quantity", entry.quantity());
        tag.putLong("Revision", entry.offerRevision());
        tag.putBoolean("HasPayment", entry.paymentSource().isPresent());
        entry.paymentSource().ifPresent(source ->
                tag.putString("Payment", source.name()));
        tag.putLong("QuotedSecond",
                entry.quotedAt().getEpochSecond());
        tag.putInt("QuotedNano", entry.quotedAt().getNano());
        tag.putByteArray("Offer",
                ServerShopOfferNetworkCodec.encodeListingBytes(
                        entry.listing()));
        tag.putByteArray("Intent",
                PlayerShopIntentCodec.encode(entry.intent()));
        tag.putByteArray("StockReservation",
                StockMutationCommandCodec.encode(
                        entry.stockReservation()));
        return tag;
    }

    private static Entry decodeEntry(CompoundTag tag) {
        Optional<PaymentSource> source = tag.getBoolean("HasPayment")
                ? Optional.of(PaymentSource.valueOf(
                tag.getString("Payment"))) : Optional.empty();
        StockMutationCommand command = StockMutationCommandCodec.decode(
                tag.getByteArray("StockReservation"));
        if (!(command instanceof StockMutationCommand.ReserveBatch reserve)) {
            throw new IllegalArgumentException(
                    "Prepared server offer stock command is invalid");
        }
        return new Entry(
                tag.getUUID("Request"),
                tag.getUUID("Player"),
                tag.getString("Shop"),
                tag.getString("Listing"),
                tag.getString("Option"),
                OfferAction.valueOf(tag.getString("Action")),
                tag.getInt("Quantity"),
                tag.getLong("Revision"),
                source,
                Instant.ofEpochSecond(
                        tag.getLong("QuotedSecond"),
                        tag.getInt("QuotedNano")),
                ServerShopOfferNetworkCodec.decodeListingBytes(
                        tag.getByteArray("Offer")),
                PlayerShopIntentCodec.decode(
                        tag.getByteArray("Intent")),
                reserve);
    }

    public record Entry(
            UUID requestId,
            UUID playerId,
            String shopId,
            String listingId,
            String optionId,
            OfferAction action,
            int quantity,
            long offerRevision,
            Optional<PaymentSource> paymentSource,
            Instant quotedAt,
            ServerShopOfferListing listing,
            PlayerShopEscrowIntent intent,
            StockMutationCommand.ReserveBatch stockReservation
    ) {
        public Entry {
            java.util.Objects.requireNonNull(requestId, "requestId");
            java.util.Objects.requireNonNull(playerId, "playerId");
            java.util.Objects.requireNonNull(shopId, "shopId");
            java.util.Objects.requireNonNull(listingId, "listingId");
            java.util.Objects.requireNonNull(optionId, "optionId");
            java.util.Objects.requireNonNull(action, "action");
            java.util.Objects.requireNonNull(
                    paymentSource, "paymentSource");
            java.util.Objects.requireNonNull(quotedAt, "quotedAt");
            java.util.Objects.requireNonNull(listing, "listing");
            java.util.Objects.requireNonNull(intent, "intent");
            java.util.Objects.requireNonNull(
                    stockReservation, "stockReservation");
            int stockQuantity = quantity;
            boolean moneyRequired = false;
            boolean optionPresent;
            if (action == OfferAction.ACQUIRE_FROM_SHOP) {
                com.enviouse.futureshops.catalog.offer.AcquireOfferOption
                        selected = listing.acquireOptions().stream()
                        .filter(option -> option.optionId().equals(optionId))
                        .findFirst().orElse(null);
                optionPresent = selected != null;
                if (selected != null) {
                    stockQuantity = Math.multiplyExact(
                            quantity, selected.outputMultiplier());
                    moneyRequired = selected.moneyCostPresent();
                }
            } else {
                optionPresent = listing.sellOptions().stream()
                        .anyMatch(option -> option.optionId()
                                .equals(optionId));
            }
            PlayerShopOperation expectedOperation =
                    action == OfferAction.ACQUIRE_FROM_SHOP
                            ? PlayerShopOperation.SERVER_SHOP_OFFER_ACQUIRE
                            : PlayerShopOperation.SERVER_SHOP_OFFER_SELL;
            PlayerShopPaymentSource expectedSource =
                    paymentSource.map(source -> source == PaymentSource.WALLET
                                    ? PlayerShopPaymentSource.WALLET
                                    : PlayerShopPaymentSource.INVENTORY_CASH)
                            .orElse(PlayerShopPaymentSource.NONE);
            StockReservationDirection direction =
                    action == OfferAction.ACQUIRE_FROM_SHOP
                            ? StockReservationDirection.OUTBOUND
                            : StockReservationDirection.INBOUND;
            StockReservationRequest reservation =
                    stockReservation.reservations().size() == 1
                            ? stockReservation.reservations().get(0) : null;
            if (requestId.equals(new UUID(0L, 0L))
                    || playerId.equals(new UUID(0L, 0L))
                    || shopId.isBlank() || shopId.length() > 160
                    || listingId.isBlank() || listingId.length() > 160
                    || optionId.isBlank() || optionId.length() > 160
                    || quantity <= 0 || quantity > 2304
                    || offerRevision < 0L
                    || !optionPresent
                    || moneyRequired != paymentSource.isPresent()
                    || action == OfferAction.SELL_TO_SHOP
                    && paymentSource.isPresent()
                    || !intent.requestId().equals(requestId)
                    || !intent.actorId().equals(playerId)
                    || !intent.shopIdentity().shopId().equals(shopId)
                    || intent.shopIdentity().identityRevision()
                    != offerRevision
                    || intent.operation() != expectedOperation
                    || intent.paymentSource() != expectedSource
                    || intent.status()
                    != PlayerShopEscrowIntent.Status.PREPARED
                    || !listing.listingId().equals(listingId)
                    || listing.revision() != offerRevision
                    || intent.listing() == null
                    || !intent.listing().listingId().equals(listingId)
                    || intent.requestedUnits() != quantity
                    || !intent.quoteCreatedAt().equals(quotedAt)
                    || !stockReservation.requestId().equals(
                    ServerShopOfferCommit.stockReserveRequestId(requestId))
                    || !stockReservation.transactionId()
                    .equals(requestId)
                    || !stockReservation.appliedAt().equals(quotedAt)
                    || reservation == null
                    || !reservation.stockKey().equals(
                    new StockKey(shopId, listingId))
                    || reservation.direction() != direction
                    || reservation.quantity() != stockQuantity) {
                throw new IllegalArgumentException(
                        "Prepared server offer evidence is invalid");
            }
        }

        public boolean matches(ServerShopOfferService.Request request) {
            return requestId.equals(request.requestId())
                    && playerId.equals(request.playerId())
                    && shopId.equals(request.shopId())
                    && listingId.equals(request.listingId())
                    && optionId.equals(request.optionId())
                    && action == request.action()
                    && quantity == request.quantity()
                    && offerRevision
                    == request.expectedOfferRevision()
                    && paymentSource.equals(request.paymentSource());
        }
    }
}
