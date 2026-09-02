package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.catalog.offer.ServerShopBundleSavings;
import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ServerShopOfferCartPreparedSavedData
        extends SavedData {
    private static final String DATA_ID =
            "futureshops_server_shop_offer_cart_prepared";
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

    public ServerShopOfferCartPreparedSavedData() {
        this(MAXIMUM_ENTRIES, MAXIMUM_ARCHIVES);
    }

    ServerShopOfferCartPreparedSavedData(int maximumEntries) {
        this(maximumEntries, MAXIMUM_ARCHIVES);
    }

    ServerShopOfferCartPreparedSavedData(
            int maximumEntries,
            int maximumArchives
    ) {
        if (maximumEntries <= 0
                || maximumEntries > MAXIMUM_ENTRIES
                || maximumArchives <= 0
                || maximumArchives > MAXIMUM_ARCHIVES) {
            throw new IllegalArgumentException(
                    "Server shop offer cart prepared limit is invalid");
        }
        this.maximumEntries = maximumEntries;
        this.maximumArchives = maximumArchives;
    }

    public static ServerShopOfferCartPreparedSavedData get(
            MinecraftServer server
    ) {
        return server.overworld().getDataStorage().computeIfAbsent(
                ServerShopOfferCartPreparedSavedData::load,
                ServerShopOfferCartPreparedSavedData::new,
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
                    "Server shop offer cart recovery limit is invalid");
        }
        LinkedHashSet<UUID> requestIds =
                unresolvedByPlayer.get(playerId);
        if (requestIds == null || requestIds.isEmpty()) {
            return List.of();
        }
        ArrayList<Entry> result =
                new ArrayList<>(Math.min(limit, requestIds.size()));
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
                    "Server shop offer cart replay receipt has no matching entry");
        }
        ServerShopOfferReplayReceipt existing =
                archives.get(receipt.requestId());
        if (existing != null) {
            if (!existing.equals(receipt)) {
                throw new IllegalStateException(
                        "Server shop offer cart replay receipt conflicts");
            }
            return false;
        }
        if (archives.size() >= maximumArchives) {
            throw new IllegalStateException(
                    "Server shop offer cart replay archive is full");
        }
        int playerCount = archivePlayerCounts.getOrDefault(
                receipt.playerId(), 0);
        if (playerCount >= MAXIMUM_ARCHIVES_PER_PLAYER) {
            throw new IllegalStateException(
                    "Server shop offer cart replay player archive is full");
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

    public synchronized boolean prepare(Entry entry) {
        ServerShopOfferReplayReceipt archived =
                archives.get(entry.requestId());
        if (archived != null) {
            if (!archived.matches(entry)) {
                throw new IllegalStateException(
                        "Server shop offer cart archived identity conflicts");
            }
            return false;
        }
        Entry existing = entries.get(entry.requestId());
        if (existing != null) {
            if (!existing.equals(entry)) {
                throw new IllegalStateException(
                        "Server shop offer cart prepared entry conflicts");
            }
            return false;
        }
        if (entries.size() >= maximumEntries) {
            throw new IllegalStateException(
                    "Server shop offer cart prepared repository is full");
        }
        entries.put(entry.requestId(), entry);
        unresolvedByPlayer.computeIfAbsent(
                entry.playerId(), ignored -> new LinkedHashSet<>())
                .add(entry.requestId());
        setDirty();
        return true;
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
        for (Entry entry : entries.values()) {
            values.add(entry.save());
        }
        tag.put("Entries", values);
        ListTag archived = new ListTag();
        for (ServerShopOfferReplayReceipt receipt
                : archives.values()) {
            archived.add(receipt.save());
        }
        tag.put("Archives", archived);
        return tag;
    }

    public static ServerShopOfferCartPreparedSavedData load(
            CompoundTag tag
    ) {
        int loadedVersion = SavedDataMigrations.readVersion(tag);
        if (loadedVersion > CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Server shop offer cart prepared version is unsupported");
        }
        SavedDataMigrations.needsMigration(
                DATA_ID, loadedVersion, CURRENT_VERSION);
        ServerShopOfferCartPreparedSavedData data =
                new ServerShopOfferCartPreparedSavedData();
        ListTag values = SavedDataMigrations.requireList(
                tag, "Entries", Tag.TAG_COMPOUND,
                MAXIMUM_ENTRIES, "Server shop offer cart prepared entries");
        for (int index = 0;
             index < values.size()
                     && data.entries.size() < MAXIMUM_ENTRIES;
             index++) {
            try {
                Entry entry = Entry.load(values.getCompound(index));
                if (data.entries.putIfAbsent(
                        entry.requestId(), entry) != null) {
                    throw new IllegalArgumentException(
                            "Duplicate server shop offer cart prepared entry");
                }
                data.unresolvedByPlayer.computeIfAbsent(
                        entry.playerId(),
                        ignored -> new LinkedHashSet<>())
                        .add(entry.requestId());
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException(
                        "Server shop offer cart prepared row " + index
                                + " is invalid", exception);
            }
        }
        if (loadedVersion >= 2) {
            ListTag archived = SavedDataMigrations.requireList(
                    tag, "Archives", Tag.TAG_COMPOUND,
                    MAXIMUM_ARCHIVES,
                    "Server shop offer cart replay archives");
            for (int index = 0;
                 index < archived.size(); index++) {
                try {
                    ServerShopOfferReplayReceipt receipt =
                            ServerShopOfferReplayReceipt.load(
                                    archived.getCompound(index));
                    if (receipt.kind()
                            != ServerShopOfferReplayReceipt.Kind.CART
                            || data.entries.containsKey(receipt.requestId())
                            && !receipt.matches(data.entries.get(
                            receipt.requestId()))
                            || data.archives.putIfAbsent(
                            receipt.requestId(), receipt) != null) {
                        throw new IllegalArgumentException(
                                "Server shop offer cart replay archive conflicts");
                    }
                    data.removeUnresolved(
                            receipt.playerId(), receipt.requestId());
                    int playerCount = data.archivePlayerCounts.merge(
                            receipt.playerId(), 1, Math::addExact);
                    if (playerCount
                            > MAXIMUM_ARCHIVES_PER_PLAYER) {
                        throw new IllegalArgumentException(
                                "Server shop offer cart replay player archive is full");
                    }
                } catch (RuntimeException exception) {
                    throw new IllegalArgumentException(
                            "Server shop offer cart replay archive row "
                                    + index + " is invalid", exception);
                }
            }
        }
        return data;
    }

    public record Entry(
            UUID requestId,
            UUID playerId,
            String shopId,
            String requestFingerprint,
            Optional<PaymentSource> paymentSource,
            Instant quotedAt,
            List<QuotedLine> lines,
            PlayerShopEscrowIntent intent,
            StockMutationCommand.ReserveBatch stockReservation
    ) {
        public Entry {
            java.util.Objects.requireNonNull(requestId, "requestId");
            java.util.Objects.requireNonNull(playerId, "playerId");
            shopId = java.util.Objects.requireNonNull(
                    shopId, "shopId").strip();
            requestFingerprint = java.util.Objects.requireNonNull(
                    requestFingerprint, "requestFingerprint");
            paymentSource = java.util.Objects.requireNonNull(
                    paymentSource, "paymentSource");
            java.util.Objects.requireNonNull(quotedAt, "quotedAt");
            lines = List.copyOf(lines);
            java.util.Objects.requireNonNull(intent, "intent");
            java.util.Objects.requireNonNull(
                    stockReservation, "stockReservation");
            boolean moneyRequired = false;
            long moneyTotal = 0L;
            LinkedHashMap<String, Long> stockQuantities =
                    new LinkedHashMap<>();
            LinkedHashMap<String, Long> stockRevisions =
                    new LinkedHashMap<>();
            Set<String> lineKeys = new HashSet<>();
            List<ServerShopOfferCartService.LineRequest> requestLines =
                    new ArrayList<>(lines.size());
            for (QuotedLine line : lines) {
                AcquireOfferOption option = line.listing()
                        .acquireOptions().stream()
                        .filter(value -> value.optionId().equals(
                                line.optionId()))
                        .findFirst().orElse(null);
                if (option == null
                        || !lineKeys.add(line.listing().listingId()
                        + "\u0000" + line.optionId())
                        || option.moneyCostPresent()
                        != (line.moneyTotalMinorUnits() > 0L)) {
                    throw new IllegalArgumentException(
                            "Server shop offer cart quoted line is invalid");
                }
                moneyRequired |= option.moneyCostPresent();
                moneyTotal = Math.addExact(
                        moneyTotal, line.moneyTotalMinorUnits());
                long stockQuantity = Math.multiplyExact(
                        line.quantity(), option.outputMultiplier());
                stockQuantities.merge(line.listing().listingId(),
                        stockQuantity, Math::addExact);
                Long priorRevision = stockRevisions.putIfAbsent(
                        line.listing().listingId(), line.stockRevision());
                if (priorRevision != null
                        && priorRevision != line.stockRevision()) {
                    throw new IllegalArgumentException(
                            "Server shop offer cart stock revision conflicts");
                }
                requestLines.add(new ServerShopOfferCartService.LineRequest(
                        line.listing().listingId(), line.optionId(),
                        line.quantity(), line.listing().revision()));
            }
            ServerShopOfferCartService.Request reconstructed =
                    new ServerShopOfferCartService.Request(
                            requestId, playerId, shopId, requestLines,
                            paymentSource, 0);
            PlayerShopPaymentSource expectedSource =
                    paymentSource.map(source -> source == PaymentSource.WALLET
                                    ? PlayerShopPaymentSource.WALLET
                                    : PlayerShopPaymentSource.INVENTORY_CASH)
                            .orElse(PlayerShopPaymentSource.NONE);
            Map<StockKey, StockReservationRequest> reservations =
                    new LinkedHashMap<>();
            for (StockReservationRequest reservation
                    : stockReservation.reservations()) {
                if (reservations.put(
                        reservation.stockKey(), reservation) != null) {
                    throw new IllegalArgumentException(
                            "Server shop offer cart stock entry is duplicated");
                }
            }
            boolean reservationMismatch =
                    reservations.size() != stockQuantities.size();
            for (Map.Entry<String, Long> expected
                    : stockQuantities.entrySet()) {
                StockReservationRequest reservation = reservations.get(
                        new StockKey(shopId, expected.getKey()));
                if (reservation == null
                        || reservation.direction()
                        != StockReservationDirection.OUTBOUND
                        || reservation.quantity()
                        != expected.getValue()
                        || reservation.expectedListingRevision()
                        != stockRevisions.get(expected.getKey())) {
                    reservationMismatch = true;
                }
            }
            boolean moneyMismatch = moneyRequired
                    ? intent.moneyTransfers().size() != 1
                    || intent.moneyTransfers().get(0)
                    .amountMinorUnits() != moneyTotal
                    : !intent.moneyTransfers().isEmpty();
            if (requestId.equals(new UUID(0L, 0L))
                    || playerId.equals(new UUID(0L, 0L))
                    || shopId.isEmpty() || shopId.length() > 160
                    || !requestFingerprint.matches("[0-9a-f]{64}")
                    || lines.isEmpty()
                    || lines.size()
                    > ServerShopOfferCartCommit.MAXIMUM_LINES
                    || !requestFingerprint.equals(
                    reconstructed.fingerprint())
                    || reconstructed.lines().size() != lines.size()
                    || moneyRequired != paymentSource.isPresent()
                    || moneyMismatch
                    || !intent.requestId().equals(requestId)
                    || !intent.actorId().equals(playerId)
                    || !intent.shopIdentity().shopId().equals(shopId)
                    || intent.shopIdentity().identityRevision() != 0L
                    || intent.operation()
                    != PlayerShopOperation.SERVER_SHOP_OFFER_ACQUIRE
                    || intent.paymentSource() != expectedSource
                    || intent.requestedUnits() != 1
                    || !intent.quoteCreatedAt().equals(quotedAt)
                    || intent.status()
                    != PlayerShopEscrowIntent.Status.PREPARED
                    || intent.listing() == null
                    || !"offer_cart".equals(
                    intent.listing().listingId())
                    || !stockReservation.requestId().equals(
                    ServerShopOfferCartCommit.stockReserveRequestId(
                            requestId))
                    || !stockReservation.transactionId()
                    .equals(requestId)
                    || !stockReservation.appliedAt().equals(quotedAt)
                    || reservationMismatch) {
                throw new IllegalArgumentException(
                        "Server shop offer cart prepared entry is invalid");
            }
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Request", requestId);
            tag.putUUID("Player", playerId);
            tag.putString("Shop", shopId);
            tag.putString("Fingerprint", requestFingerprint);
            paymentSource.ifPresent(value ->
                    tag.putString("PaymentSource", value.name()));
            tag.putLong("QuotedSecond", quotedAt.getEpochSecond());
            tag.putInt("QuotedNano", quotedAt.getNano());
            ListTag lineTags = new ListTag();
            for (QuotedLine line : lines) {
                lineTags.add(line.save());
            }
            tag.put("Lines", lineTags);
            tag.putByteArray("Intent",
                    PlayerShopIntentCodec.encode(intent));
            tag.putByteArray("StockReservation",
                    StockMutationCommandCodec.encode(stockReservation));
            return tag;
        }

        private static Entry load(CompoundTag tag) {
            Optional<PaymentSource> source =
                    tag.contains("PaymentSource", Tag.TAG_STRING)
                            ? Optional.of(PaymentSource.valueOf(
                            tag.getString("PaymentSource")))
                            : Optional.empty();
            ListTag lineTags = SavedDataMigrations.requireList(
                    tag, "Lines", Tag.TAG_COMPOUND,
                    ServerShopOfferCartCommit.MAXIMUM_LINES,
                    "Server shop offer cart prepared lines");
            List<QuotedLine> lines =
                    new ArrayList<>(lineTags.size());
            for (int index = 0; index < lineTags.size(); index++) {
                lines.add(QuotedLine.load(
                        lineTags.getCompound(index)));
            }
            StockMutationCommand stock =
                    StockMutationCommandCodec.decode(
                            tag.getByteArray("StockReservation"));
            if (!(stock instanceof
                    StockMutationCommand.ReserveBatch reserve)) {
                throw new IllegalArgumentException(
                        "Server shop offer cart prepared stock is invalid");
            }
            return new Entry(
                    tag.getUUID("Request"),
                    tag.getUUID("Player"),
                    tag.getString("Shop"),
                    tag.getString("Fingerprint"),
                    source,
                    Instant.ofEpochSecond(
                            tag.getLong("QuotedSecond"),
                            tag.getInt("QuotedNano")),
                    lines,
                    PlayerShopIntentCodec.decode(
                            tag.getByteArray("Intent")),
                    reserve);
        }
    }

    public record QuotedLine(
            ServerShopOfferListing listing,
            String optionId,
            int quantity,
            long stockRevision,
            long moneyTotalMinorUnits,
            Optional<ServerShopBundleSavings.Snapshot> savings
    ) {
        public QuotedLine {
            java.util.Objects.requireNonNull(listing, "listing");
            optionId = java.util.Objects.requireNonNull(
                    optionId, "optionId").strip();
            savings = java.util.Objects.requireNonNull(
                    savings, "savings");
            if (optionId.isEmpty() || optionId.length() > 160
                    || quantity <= 0 || quantity > 2304
                    || stockRevision < 0L
                    || moneyTotalMinorUnits < 0L) {
                throw new IllegalArgumentException(
                        "Server shop offer cart quoted line is invalid");
            }
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putByteArray("Listing",
                    ServerShopOfferNetworkCodec
                            .encodeListingBytes(listing));
            tag.putString("Option", optionId);
            tag.putInt("Quantity", quantity);
            tag.putLong("StockRevision", stockRevision);
            tag.putLong("MoneyTotal", moneyTotalMinorUnits);
            savings.ifPresent(value -> {
                CompoundTag savingsTag = new CompoundTag();
                savingsTag.putLong("Individual",
                        value.individualTotalMinorUnits());
                savingsTag.putLong("Bundle",
                        value.bundleTotalMinorUnits());
                savingsTag.putLong("Savings",
                        value.savingsMinorUnits());
                savingsTag.putLong("BasisPoints",
                        value.savingsBasisPoints());
                ListTag revisions = new ListTag();
                for (ServerShopBundleSavings.ComparisonRevision revision
                        : value.comparisonRevisions()) {
                    CompoundTag row = new CompoundTag();
                    row.putString("Component", revision.componentId());
                    row.putString("Listing", revision.listingId());
                    row.putString("Option", revision.optionId());
                    row.putLong("Revision", revision.revision());
                    revisions.add(row);
                }
                savingsTag.put("Revisions", revisions);
                tag.put("SavingsSnapshot", savingsTag);
            });
            return tag;
        }

        private static QuotedLine load(CompoundTag tag) {
            Optional<ServerShopBundleSavings.Snapshot> savings =
                    Optional.empty();
            if (tag.contains("SavingsSnapshot", Tag.TAG_COMPOUND)) {
                CompoundTag savingsTag =
                        tag.getCompound("SavingsSnapshot");
                ListTag revisionTags = SavedDataMigrations.requireList(
                        savingsTag, "Revisions", Tag.TAG_COMPOUND,
                        ServerShopBundleSavings.MAXIMUM_COMPARISON_REVISIONS,
                        "Server shop bundle savings revisions");
                List<ServerShopBundleSavings.ComparisonRevision>
                        revisions = new ArrayList<>(revisionTags.size());
                for (int index = 0;
                     index < revisionTags.size(); index++) {
                    CompoundTag row =
                            revisionTags.getCompound(index);
                    revisions.add(new ServerShopBundleSavings
                            .ComparisonRevision(
                            row.getString("Component"),
                            row.getString("Listing"),
                            row.getString("Option"),
                            row.getLong("Revision")));
                }
                savings = Optional.of(
                        new ServerShopBundleSavings.Snapshot(
                                savingsTag.getLong("Individual"),
                                savingsTag.getLong("Bundle"),
                                savingsTag.getLong("Savings"),
                                savingsTag.getLong("BasisPoints"),
                                revisions));
            }
            return new QuotedLine(
                    ServerShopOfferNetworkCodec.decodeListingBytes(
                            tag.getByteArray("Listing")),
                    tag.getString("Option"),
                    tag.getInt("Quantity"),
                    tag.getLong("StockRevision"),
                    tag.getLong("MoneyTotal"),
                    savings);
        }

        public QuotedLine(
                ServerShopOfferListing listing,
                String optionId,
                int quantity,
                long stockRevision,
                Optional<ServerShopBundleSavings.Snapshot> savings
        ) {
            this(listing, optionId, quantity, stockRevision,
                    listing.acquireOptions().stream()
                            .filter(option -> option.optionId()
                                    .equals(optionId))
                            .findFirst()
                            .filter(com.enviouse.futureshops.catalog.offer
                                    .AcquireOfferOption
                                    ::moneyCostPresent)
                            .map(option -> Math.multiplyExact(
                                    option.moneyCostMinorUnits(), quantity))
                            .orElse(0L),
                    savings);
        }
    }
}
