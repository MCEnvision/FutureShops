package com.enviouse.futureshops.server.market.query;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.claim.OpenClaimPage;
import com.enviouse.futureshops.server.market.auction.AuctionHouseSnapshot;
import com.enviouse.futureshops.server.market.auction.AuctionListing;
import com.enviouse.futureshops.server.market.auction.AuctionListingState;
import com.enviouse.futureshops.server.market.bazaar.BazaarFill;
import com.enviouse.futureshops.server.market.bazaar.BazaarMarketAnalytics;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrder;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderBookSnapshot;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderSide;
import com.enviouse.futureshops.server.market.bazaar.BazaarProduct;
import com.enviouse.futureshops.server.market.bazaar.BazaarProductBrowseIndex;
import com.enviouse.futureshops.server.market.bazaar.BazaarProductStatus;
import com.enviouse.futureshops.server.market.bazaar.BazaarProductVersionKey;
import com.enviouse.futureshops.server.market.profile.MarketProfileSavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public final class MarketPageProjector {
    private static final long RECENT_WINDOW_MILLIS = 86_400_000L;

    private MarketPageProjector() {
    }

    public static MarketPageSnapshot auction(
            MarketPageQuery query,
            UUID playerId,
            AuctionHouseSnapshot snapshot,
            MarketProfileSavedData.Snapshot profile,
            List<EscrowClaim> claims
    ) {
        requireModule(query, "auction_house");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(profile, "profile");
        claims = ownedClaims(playerId, claims, "auction.");
        Set<UUID> watched = Set.copyOf(profile.watchedAuctions());
        List<String> categories = snapshot.listings().values().stream()
                .map(value -> value.itemLot().categoryId())
                .filter(value -> !value.isEmpty())
                .distinct().sorted().limit(256).toList();
        List<MarketPageCard> cards;
        int total;
        int pages;
        if ("claims".equals(query.view())) {
            PageSlice<EscrowClaim> slice = slice(claims, query);
            cards = slice.values().stream().map(
                    MarketPageProjector::claimCard).toList();
            total = slice.total();
            pages = slice.pages();
        } else {
            List<AuctionListing> selected = auctionListings(query,
                    playerId, snapshot, watched);
            PageSlice<AuctionListing> slice = slice(selected, query);
            cards = slice.values().stream().map(value -> auctionCard(
                    value, watched.contains(value.listingId()),
                    query.serverTimeMillis(), playerId)).toList();
            total = slice.total();
            pages = slice.pages();
        }
        return new MarketPageSnapshot(query.requestId(),
                query.routeNonce(), query.module(), query.view(),
                query.pageIndex(), query.pageSize(), total, pages,
                snapshot.nextAcceptedSequence(),
                profile.revision(), profile.replayEpoch(),
                query.serverTimeMillis(), profile.unreadNotifications(),
                sumPrimary(cards), sumQuantity(cards), categories, cards);
    }

    public static MarketPageSnapshot auction(
            MarketPageQuery query,
            UUID playerId,
            AuctionHouseSnapshot snapshot,
            MarketProfileSavedData.Snapshot profile,
            OpenClaimPage claims
    ) {
        requireModule(query, "auction_house");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(profile, "profile");
        List<String> categories = snapshot.listings().values().stream()
                .map(value -> value.itemLot().categoryId())
                .filter(value -> !value.isEmpty())
                .distinct().sorted().limit(256).toList();
        return claimPage(query, playerId, profile, claims,
                "auction.", snapshot.nextAcceptedSequence(),
                categories);
    }

    public static MarketPageSnapshot bazaar(
            MarketPageQuery query,
            UUID playerId,
            BazaarOrderBookSnapshot snapshot,
            MarketProfileSavedData.Snapshot profile,
            List<EscrowClaim> claims
    ) {
        requireModule(query, "bazaar");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(profile, "profile");
        claims = ownedClaims(playerId, claims, "bazaar.");
        Set<BazaarProductVersionKey> watched = new LinkedHashSet<>();
        for (MarketProfileSavedData.ProductKey key
                : profile.favoriteProducts()) {
            watched.add(new BazaarProductVersionKey(
                    key.productId(), key.version()));
        }
        List<String> categories = latestProducts(snapshot).values()
                .stream().map(BazaarProduct::categoryId)
                .filter(value -> !value.isEmpty())
                .distinct().sorted().limit(256).toList();
        List<MarketPageCard> cards;
        int total;
        int pages;
        if ("products".equals(query.view())
                || "watched".equals(query.view())) {
            BazaarProductBrowseIndex.Page page = "watched".equals(
                    query.view()) ? bazaarWatchedProducts(query,
                    snapshot, watched) : bazaarProducts(
                    query, snapshot, watched);
            cards = page.cards().stream()
                    .map(value -> bazaarProductCard(value, playerId))
                    .toList();
            total = page.totalResults();
            pages = page.pageCount();
        } else if ("claims".equals(query.view())) {
            PageSlice<EscrowClaim> slice = slice(claims, query);
            cards = slice.values().stream().map(
                    MarketPageProjector::claimCard).toList();
            total = slice.total();
            pages = slice.pages();
        } else if ("history".equals(query.view())) {
            List<BazaarFill> fills = ownedFills(
                    snapshot, playerId, query.search());
            PageSlice<BazaarFill> slice = slice(fills, query);
            Map<UUID, BazaarOrder> orders = orders(snapshot);
            cards = slice.values().stream().map(value ->
                    bazaarFillCard(value, orders, playerId)).toList();
            total = slice.total();
            pages = slice.pages();
        } else if ("portfolio".equals(query.view())) {
            List<PortfolioLine> portfolio = portfolio(snapshot, playerId);
            PageSlice<PortfolioLine> slice = slice(portfolio, query);
            cards = slice.values().stream().map(
                    MarketPageProjector::portfolioCard).toList();
            total = slice.total();
            pages = slice.pages();
        } else {
            List<BazaarOrder> selected = ownedOrders(query, playerId,
                    snapshot);
            PageSlice<BazaarOrder> slice = slice(selected, query);
            cards = slice.values().stream().map(
                    MarketPageProjector::bazaarOrderCard).toList();
            total = slice.total();
            pages = slice.pages();
        }
        return new MarketPageSnapshot(query.requestId(),
                query.routeNonce(), query.module(), query.view(),
                query.pageIndex(), query.pageSize(), total, pages,
                snapshot.nextSequence(), profile.revision(),
                profile.replayEpoch(), query.serverTimeMillis(),
                profile.unreadNotifications(), sumPrimary(cards),
                sumQuantity(cards), categories, cards);
    }

    public static MarketPageSnapshot bazaar(
            MarketPageQuery query,
            UUID playerId,
            BazaarOrderBookSnapshot snapshot,
            MarketProfileSavedData.Snapshot profile,
            OpenClaimPage claims
    ) {
        requireModule(query, "bazaar");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(profile, "profile");
        List<String> categories = latestProducts(snapshot).values()
                .stream().map(BazaarProduct::categoryId)
                .filter(value -> !value.isEmpty())
                .distinct().sorted().limit(256).toList();
        return claimPage(query, playerId, profile, claims,
                "bazaar.", snapshot.nextSequence(), categories);
    }

    private static List<AuctionListing> auctionListings(
            MarketPageQuery query,
            UUID playerId,
            AuctionHouseSnapshot snapshot,
            Set<UUID> watched
    ) {
        Predicate<AuctionListing> route = switch (query.view()) {
            case "mine" -> listing -> listing.sellerId().equals(playerId);
            case "bids" -> listing -> listing.highestBid()
                    .filter(value -> value.bidderId().equals(playerId))
                    .isPresent();
            case "watched" -> listing -> watched.contains(
                    listing.listingId());
            case "history" -> listing -> listing.state().terminal()
                    && (listing.sellerId().equals(playerId)
                    || listing.sale().filter(value -> value.buyerId()
                    .equals(playerId)).isPresent()
                    || listing.highestBid().filter(value -> value.bidderId()
                    .equals(playerId)).isPresent());
            case "browse" -> listing ->
                    listing.state() == AuctionListingState.ACTIVE;
            default -> listing -> false;
        };
        String search = query.search().toLowerCase(Locale.ROOT);
        List<String> tokens = search.isEmpty() ? List.of()
                : List.of(search.split("\\s+"));
        Comparator<AuctionListing> comparator = auctionComparator(
                query.sort(), query.serverTimeMillis());
        return snapshot.listings().values().stream().filter(route)
                .filter(value -> query.category().isEmpty()
                        || query.category().equals(
                        value.itemLot().categoryId()))
                .filter(value -> tokens.isEmpty() || tokens.stream()
                        .allMatch(((value.itemLot().registryId() + " "
                                + value.itemLot().searchDocument())
                                .toLowerCase(Locale.ROOT))::contains))
                .filter(value -> withinPrice(auctionPrice(value), query))
                .sorted(comparator.thenComparing(value ->
                        value.listingId().toString())).toList();
    }

    private static Comparator<AuctionListing> auctionComparator(
            String sort,
            long nowMillis
    ) {
        return switch (sort) {
            case "newest" -> Comparator.comparingLong(
                    AuctionListing::createdAtMillis).reversed();
            case "lowest_price" -> Comparator.comparingLong(
                    MarketPageProjector::auctionPrice);
            case "highest_price" -> Comparator.comparingLong(
                    MarketPageProjector::auctionPrice).reversed();
            case "most_bids" -> Comparator.comparingLong(
                    AuctionListing::acceptedBidCount).reversed();
            case "seller" -> Comparator.comparing(value ->
                    value.sellerId().toString());
            default -> Comparator.comparingLong(value ->
                    auctionRemaining(value, nowMillis));
        };
    }

    private static MarketPageCard auctionCard(
            AuctionListing listing,
            boolean watched,
            long nowMillis,
            UUID viewerId
    ) {
        long minimum = listing.type().acceptsBids()
                ? listing.minimumNextBid() : listing.buyoutMinor();
        boolean own = listing.sellerId().equals(viewerId);
        boolean active = listing.state() == AuctionListingState.ACTIVE;
        return new MarketPageCard(MarketPageCardKind.AUCTION,
                listing.listingId().toString(),
                Optional.of(listing.sellerId()),
                listing.itemLot().registryId(), listing.itemLot().count(),
                listing.itemLot().registryId(),
                listing.itemLot().categoryId(), listing.state().name(),
                listing.revision(), auctionPrice(listing), minimum,
                listing.acceptedBidCount(),
                auctionRemaining(listing, nowMillis), watched,
                active && !own, own && active,
                // tertiary = buyout price of a BIDDING listing that also allows buy-now
                // (AUCTION_WITH_BUYOUT). Pure BUY_NOW keeps its price in primary/secondary and
                // signals type via the MAX_VALUE remaining sentinel; 0 = no buy-now on a bid lot.
                listing.type().acceptsBids() ? listing.buyoutMinor() : 0L);
    }

    private static long auctionPrice(AuctionListing listing) {
        return listing.type().acceptsBids()
                ? listing.highestBid().map(value -> value.amountMinor())
                .orElse(listing.startingBidMinor())
                : listing.buyoutMinor();
    }

    private static long auctionRemaining(
            AuctionListing listing,
            long nowMillis
    ) {
        if (!listing.type().acceptsBids()) {
            return Long.MAX_VALUE;
        }
        if (listing.state() == AuctionListingState.FROZEN) {
            return listing.frozenRemainingMillis();
        }
        return Math.max(0L, listing.deadlineMillis()
                - Math.max(nowMillis, listing.lastObservedTimeMillis()));
    }

    private static BazaarProductBrowseIndex.Page bazaarProducts(
            MarketPageQuery query,
            BazaarOrderBookSnapshot snapshot,
            Set<BazaarProductVersionKey> watched
    ) {
        BazaarProductBrowseIndex.Query browse =
                new BazaarProductBrowseIndex.Query(query.search(),
                        query.category(), EnumSet.of(
                        BazaarProductStatus.ACTIVE,
                        BazaarProductStatus.HALTED),
                        bazaarSort(query.sort()), query.pageIndex(),
                        query.pageSize(), query.serverTimeMillis(),
                        RECENT_WINDOW_MILLIS, 12);
        return BazaarProductBrowseIndex.query(snapshot, browse, watched);
    }

    private static BazaarProductBrowseIndex.Page bazaarWatchedProducts(
            MarketPageQuery query,
            BazaarOrderBookSnapshot snapshot,
            Set<BazaarProductVersionKey> watched
    ) {
        BazaarProductBrowseIndex.Query browse =
                new BazaarProductBrowseIndex.Query(query.search(),
                        query.category(), EnumSet.of(
                        BazaarProductStatus.ACTIVE,
                        BazaarProductStatus.HALTED),
                        bazaarSort(query.sort()), query.pageIndex(),
                        query.pageSize(), query.serverTimeMillis(),
                        RECENT_WINDOW_MILLIS, 12);
        return BazaarProductBrowseIndex.queryWatched(
                snapshot, browse, watched);
    }

    private static BazaarProductBrowseIndex.Sort bazaarSort(
            String sort
    ) {
        return switch (sort) {
            case "instant_buy_lowest" ->
                    BazaarProductBrowseIndex.Sort.INSTANT_BUY_LOWEST;
            case "instant_sell_highest" ->
                    BazaarProductBrowseIndex.Sort.INSTANT_SELL_HIGHEST;
            case "spread_lowest" ->
                    BazaarProductBrowseIndex.Sort.SPREAD_LOWEST;
            case "volume_highest" ->
                    BazaarProductBrowseIndex.Sort.VOLUME_HIGHEST;
            case "trend_highest" ->
                    BazaarProductBrowseIndex.Sort.TREND_HIGHEST;
            default -> BazaarProductBrowseIndex.Sort.NAME;
        };
    }

    private static MarketPageCard bazaarProductCard(
            BazaarProductBrowseIndex.Card card,
            UUID viewerId
    ) {
        BazaarMarketAnalytics.ProductSummary summary = card.summary();
        long ask = summary.bestAskMinor().orElse(0L);
        long bid = summary.bestBidMinor().orElse(0L);
        return new MarketPageCard(MarketPageCardKind.BAZAAR_PRODUCT,
                productIdentity(card.product()), Optional.of(viewerId),
                card.registryId(), card.lotSize(),
                card.product().productId(), card.categoryId(),
                card.status().name(), card.product().version(), ask, bid,
                summary.recentVolume(), 0L, card.watched(),
                card.status() == BazaarProductStatus.ACTIVE && ask > 0L,
                card.status() == BazaarProductStatus.ACTIVE && bid > 0L);
    }

    private static List<BazaarOrder> ownedOrders(
            MarketPageQuery query,
            UUID playerId,
            BazaarOrderBookSnapshot snapshot
    ) {
        BazaarOrderSide side = "buy_orders".equals(query.view())
                ? BazaarOrderSide.BUY
                : "sell_orders".equals(query.view())
                ? BazaarOrderSide.SELL : null;
        String search = query.search().toLowerCase(Locale.ROOT);
        return snapshot.orders().stream()
                .filter(value -> value.ownerId().equals(playerId))
                .filter(value -> side == null || value.side() == side)
                .filter(value -> search.isEmpty()
                        || value.productId().toLowerCase(Locale.ROOT)
                        .contains(search))
                .sorted(Comparator.comparingLong(
                                BazaarOrder::createdAtMillis).reversed()
                        .thenComparing(value -> value.orderId().toString()))
                .toList();
    }

    private static MarketPageCard bazaarOrderCard(BazaarOrder order) {
        return new MarketPageCard(MarketPageCardKind.BAZAAR_ORDER,
                order.orderId().toString(), Optional.of(order.ownerId()),
                "", 0, order.productId(), "", order.state().name(),
                order.revision(), order.limitPriceMinor(),
                order.reservedMoneyMinor(), order.remainingQuantity(),
                order.expiresAtMillis() == 0L ? 0L
                        : Math.max(0L, order.expiresAtMillis()
                        - order.createdAtMillis()), false,
                order.matchable(), false);
    }

    private static List<BazaarFill> ownedFills(
            BazaarOrderBookSnapshot snapshot,
            UUID playerId,
            String search
    ) {
        Map<UUID, BazaarOrder> orders = orders(snapshot);
        String needle = search.toLowerCase(Locale.ROOT);
        return snapshot.fills().stream()
                .filter(value -> owned(orders.get(value.buyOrderId()),
                        playerId)
                        || owned(orders.get(value.sellOrderId()),
                        playerId))
                .filter(value -> needle.isEmpty()
                        || value.productId().toLowerCase(Locale.ROOT)
                        .contains(needle))
                .sorted(Comparator.comparingLong(
                                BazaarFill::filledAtMillis).reversed()
                        .thenComparing(value -> value.fillId().toString()))
                .toList();
    }

    private static MarketPageCard bazaarFillCard(
            BazaarFill fill,
            Map<UUID, BazaarOrder> orders,
            UUID playerId
    ) {
        BazaarOrder buy = orders.get(fill.buyOrderId());
        boolean buyer = owned(buy, playerId);
        return new MarketPageCard(MarketPageCardKind.BAZAAR_FILL,
                fill.fillId().toString(), Optional.of(playerId), "", 0,
                fill.productId(), buyer ? "buy" : "sell", "FILLED",
                fill.sequence(), fill.priceMinor(), fill.grossMinor(),
                fill.quantity(), 0L, false, false, false);
    }

    private static List<PortfolioLine> portfolio(
            BazaarOrderBookSnapshot snapshot,
            UUID playerId
    ) {
        Map<UUID, BazaarOrder> orders = orders(snapshot);
        Map<BazaarProductVersionKey, Long> quantities =
                new LinkedHashMap<>();
        for (BazaarFill fill : snapshot.fills()) {
            BazaarProductVersionKey key = new BazaarProductVersionKey(
                    fill.productId(), fill.productVersion());
            long delta = owned(orders.get(fill.buyOrderId()), playerId)
                    ? fill.quantity() : 0L;
            if (owned(orders.get(fill.sellOrderId()), playerId)) {
                delta = Math.subtractExact(delta, fill.quantity());
            }
            if (delta != 0L) {
                quantities.merge(key, delta, Math::addExact);
            }
        }
        List<PortfolioLine> result = new ArrayList<>();
        Map<BazaarProductVersionKey, BazaarProduct> products =
                productVersions(snapshot);
        for (Map.Entry<BazaarProductVersionKey, Long> entry
                : quantities.entrySet()) {
            if (entry.getValue() <= 0L) {
                continue;
            }
            BazaarProduct product = products.get(entry.getKey());
            BazaarMarketAnalytics.ProductSummary summary =
                    BazaarMarketAnalytics.summarize(snapshot,
                            entry.getKey(), Long.MAX_VALUE,
                            Long.MAX_VALUE, 1);
            long price = summary.lastPriceMinor().orElseGet(() ->
                    summary.bestBidMinor().orElse(0L));
            result.add(new PortfolioLine(entry.getKey(),
                    product == null ? "" : product.registryId(),
                    entry.getValue(), Math.multiplyExact(
                    entry.getValue(), price)));
        }
        return result.stream().sorted(Comparator.comparing(value ->
                value.product().productId())).toList();
    }

    private static MarketPageCard portfolioCard(PortfolioLine line) {
        return new MarketPageCard(MarketPageCardKind.BAZAAR_PRODUCT,
                productIdentity(line.product()), Optional.empty(),
                line.registryId(), 0, line.product().productId(),
                "portfolio", "OWNED", line.product().version(),
                line.estimatedValueMinor(), 0L, line.quantity(), 0L,
                false, false, false);
    }

    private static MarketPageCard claimCard(EscrowClaim claim) {
        boolean money = claim.kind() == ClaimKind.MONEY
                || (claim.kind() == ClaimKind.REFUND
                && claim.payload().length == 0);
        return new MarketPageCard(MarketPageCardKind.CLAIM,
                claim.claimId().toString(), Optional.of(claim.ownerId()),
                "", 0, claim.label(), claim.sourceKey(),
                claim.status().name(), 0L,
                money ? claim.remainingUnits() : 0L, 0L,
                money ? 0L : claim.remainingUnits(), 0L, false,
                claim.status() == ClaimStatus.PENDING
                        || claim.status()
                        == ClaimStatus.PARTIALLY_DELIVERED,
                false);
    }

    private static MarketPageSnapshot claimPage(
            MarketPageQuery query,
            UUID playerId,
            MarketProfileSavedData.Snapshot profile,
            OpenClaimPage page,
            String sourcePrefix,
            long publicRevision,
            List<String> categories
    ) {
        UUID ownerId = Objects.requireNonNull(playerId, "playerId");
        OpenClaimPage claims = Objects.requireNonNull(page, "claims");
        if (!"claims".equals(query.view())
                || !claims.ownerId().equals(ownerId)
                || !claims.sourcePrefix().equals(sourcePrefix)
                || claims.pageIndex() != query.pageIndex()
                || claims.pageSize() != query.pageSize()) {
            throw new IllegalArgumentException(
                    "Market claim page does not match its query");
        }
        List<MarketPageCard> cards = claims.claims().stream()
                .map(MarketPageProjector::claimCard).toList();
        return new MarketPageSnapshot(query.requestId(),
                query.routeNonce(), query.module(), query.view(),
                query.pageIndex(), query.pageSize(),
                claims.totalResults(), claims.pageCount(),
                publicRevision, profile.revision(),
                profile.replayEpoch(), query.serverTimeMillis(),
                profile.unreadNotifications(), sumPrimary(cards),
                sumQuantity(cards), categories, cards);
    }

    private static List<EscrowClaim> ownedClaims(
            UUID playerId,
            List<EscrowClaim> claims,
            String prefix
    ) {
        return List.copyOf(Objects.requireNonNull(claims, "claims"))
                .stream().filter(value -> value.ownerId().equals(playerId))
                .filter(value -> value.kind().publiclyVisible())
                .filter(value -> value.status() == ClaimStatus.PENDING
                        || value.status()
                        == ClaimStatus.PARTIALLY_DELIVERED
                        || value.status() == ClaimStatus.QUARANTINED)
                .filter(value -> value.sourceKey().startsWith(prefix))
                .sorted(Comparator.comparing(EscrowClaim::createdAt)
                        .thenComparing(value -> value.claimId().toString()))
                .toList();
    }

    private static Map<String, BazaarProduct> latestProducts(
            BazaarOrderBookSnapshot snapshot
    ) {
        Map<String, BazaarProduct> latest = new LinkedHashMap<>();
        for (BazaarProduct product : snapshot.products()) {
            BazaarProduct previous = latest.get(product.productId());
            if (previous == null
                    || product.version() > previous.version()) {
                latest.put(product.productId(), product);
            }
        }
        return latest;
    }

    private static Map<BazaarProductVersionKey, BazaarProduct>
    productVersions(BazaarOrderBookSnapshot snapshot) {
        Map<BazaarProductVersionKey, BazaarProduct> result =
                new LinkedHashMap<>();
        for (BazaarProduct product : snapshot.products()) {
            result.put(BazaarProductVersionKey.of(product), product);
        }
        return result;
    }

    private static Map<UUID, BazaarOrder> orders(
            BazaarOrderBookSnapshot snapshot
    ) {
        Map<UUID, BazaarOrder> result = new LinkedHashMap<>();
        for (BazaarOrder order : snapshot.orders()) {
            result.put(order.orderId(), order);
        }
        return result;
    }

    private static boolean owned(BazaarOrder order, UUID playerId) {
        return order != null && order.ownerId().equals(playerId);
    }

    private static boolean withinPrice(
            long price,
            MarketPageQuery query
    ) {
        return (query.minimumPriceMinor().isEmpty()
                || price >= query.minimumPriceMinor().getAsLong())
                && (query.maximumPriceMinor().isEmpty()
                || price <= query.maximumPriceMinor().getAsLong());
    }

    private static String productIdentity(
            BazaarProductVersionKey product
    ) {
        return product.productId() + "@" + product.version();
    }

    private static long sumPrimary(List<MarketPageCard> cards) {
        long total = 0L;
        for (MarketPageCard card : cards) {
            total = saturatedAdd(total, card.primaryMinor());
        }
        return total;
    }

    private static long sumQuantity(List<MarketPageCard> cards) {
        long total = 0L;
        for (MarketPageCard card : cards) {
            total = saturatedAdd(total, card.quantity());
        }
        return total;
    }

    private static long saturatedAdd(long first, long second) {
        if (Long.MAX_VALUE - first < second) {
            return Long.MAX_VALUE;
        }
        return first + second;
    }

    private static <T> PageSlice<T> slice(
            List<T> values,
            MarketPageQuery query
    ) {
        int total = values.size();
        long firstLong = Math.multiplyExact(
                (long) query.pageIndex(), query.pageSize());
        int first = firstLong >= total ? total : (int) firstLong;
        int last = Math.min(total, Math.addExact(first,
                query.pageSize()));
        int pages = total == 0 ? 0 : Math.toIntExact(Math.floorDiv(
                Math.addExact((long) total, query.pageSize() - 1L),
                query.pageSize()));
        return new PageSlice<>(values.subList(first, last), total, pages);
    }

    private static void requireModule(
            MarketPageQuery query,
            String moduleId
    ) {
        Objects.requireNonNull(query, "query");
        if (!query.module().id().equals(moduleId)) {
            throw new IllegalArgumentException(
                    "Market page module is invalid");
        }
    }

    private record PageSlice<T>(
            List<T> values,
            int total,
            int pages
    ) {
        private PageSlice {
            values = List.copyOf(values);
        }
    }

    private record PortfolioLine(
            BazaarProductVersionKey product,
            String registryId,
            long quantity,
            long estimatedValueMinor
    ) {
    }
}
