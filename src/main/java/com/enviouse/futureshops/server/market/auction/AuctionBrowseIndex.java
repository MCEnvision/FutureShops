package com.enviouse.futureshops.server.market.auction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

public final class AuctionBrowseIndex {
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_PAGE_INDEX = 1000000;
    public static final int MAX_QUERY_LENGTH = 128;

    private AuctionBrowseIndex() {
    }

    public static Page query(
            AuctionHouseSnapshot snapshot,
            Query query,
            Set<UUID> watchedListings
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(query, "query");
        watchedListings = Set.copyOf(Objects.requireNonNull(
                watchedListings, "watchedListings"));
        List<AuctionListing> matches = snapshot.listings().values()
                .stream().filter(listing -> visible(listing, query))
                .sorted(comparator(query.sort(), query.nowMillis()))
                .toList();
        int total = matches.size();
        long firstLong = Math.multiplyExact(
                (long) query.pageIndex(), query.pageSize());
        int first = firstLong >= total ? total : (int) firstLong;
        int last = Math.min(total,
                Math.addExact(first, query.pageSize()));
        List<Card> cards = new ArrayList<>(last - first);
        for (AuctionListing listing : matches.subList(first, last)) {
            cards.add(Card.from(listing, query.nowMillis(),
                    watchedListings.contains(listing.listingId())));
        }
        int pageCount = total == 0 ? 0
                : Math.toIntExact(Math.floorDiv(
                Math.addExact((long) total, query.pageSize() - 1L),
                query.pageSize()));
        return new Page(query.pageIndex(), query.pageSize(), total,
                pageCount, List.copyOf(cards));
    }

    private static boolean visible(
            AuctionListing listing,
            Query query
    ) {
        if (!query.states().contains(listing.state())
                || query.sellerId().isPresent()
                && !query.sellerId().orElseThrow().equals(
                listing.sellerId())
                || !query.categoryId().isEmpty()
                && !query.categoryId().equals(
                listing.itemLot().categoryId())
                || !query.exactFingerprint().isEmpty()
                && !query.exactFingerprint().equals(
                listing.itemLot().fingerprint())) {
            return false;
        }
        long price = displayPrice(listing);
        if (query.minimumPriceMinor().isPresent()
                && price < query.minimumPriceMinor().getAsLong()
                || query.maximumPriceMinor().isPresent()
                && price > query.maximumPriceMinor().getAsLong()) {
            return false;
        }
        if (query.tokens().isEmpty()) {
            return true;
        }
        String document = (listing.itemLot().registryId() + " "
                + listing.itemLot().categoryId() + " "
                + listing.itemLot().searchDocument())
                .toLowerCase(Locale.ROOT);
        return query.tokens().stream().allMatch(document::contains);
    }

    private static Comparator<AuctionListing> comparator(
            Sort sort,
            long nowMillis
    ) {
        Comparator<AuctionListing> identity = Comparator.comparing(
                listing -> listing.listingId().toString());
        Comparator<AuctionListing> selected = switch (sort) {
            case ENDING_SOON -> Comparator.comparingLong(listing ->
                    remainingMillis(listing, nowMillis));
            case NEWEST -> Comparator.comparingLong(
                    AuctionListing::createdAtMillis).reversed();
            case LOWEST_PRICE -> Comparator.comparingLong(
                    AuctionBrowseIndex::displayPrice);
            case HIGHEST_PRICE -> Comparator.comparingLong(
                    AuctionBrowseIndex::displayPrice).reversed();
            case MOST_BIDS -> Comparator.comparingLong(
                    AuctionListing::acceptedBidCount).reversed();
            case SELLER -> Comparator.comparing(listing ->
                    listing.sellerId().toString());
        };
        return selected.thenComparing(identity);
    }

    private static long displayPrice(AuctionListing listing) {
        if (listing.type() == AuctionListingType.BUY_NOW) {
            return listing.buyoutMinor();
        }
        return listing.highestBid().map(
                AuctionBidStanding::amountMinor).orElse(
                listing.startingBidMinor());
    }

    private static long remainingMillis(
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

    public record Query(
            String search,
            String categoryId,
            String exactFingerprint,
            Optional<UUID> sellerId,
            OptionalLong minimumPriceMinor,
            OptionalLong maximumPriceMinor,
            Set<AuctionListingState> states,
            Sort sort,
            int pageIndex,
            int pageSize,
            long nowMillis
    ) {
        public Query {
            search = normalize(search, MAX_QUERY_LENGTH, "search");
            categoryId = normalize(categoryId, 128, "categoryId")
                    .toLowerCase(Locale.ROOT);
            exactFingerprint = normalize(
                    exactFingerprint, 64, "exactFingerprint")
                    .toLowerCase(Locale.ROOT);
            sellerId = Objects.requireNonNull(sellerId, "sellerId");
            minimumPriceMinor = Objects.requireNonNull(
                    minimumPriceMinor, "minimumPriceMinor");
            maximumPriceMinor = Objects.requireNonNull(
                    maximumPriceMinor, "maximumPriceMinor");
            states = Set.copyOf(Objects.requireNonNull(states, "states"));
            sort = Objects.requireNonNull(sort, "sort");
            if (pageIndex < 0 || pageIndex > MAX_PAGE_INDEX
                    || pageSize <= 0 || pageSize > MAX_PAGE_SIZE
                    || nowMillis < 0L || states.isEmpty()
                    || minimumPriceMinor.isPresent()
                    && minimumPriceMinor.getAsLong() < 0L
                    || maximumPriceMinor.isPresent()
                    && maximumPriceMinor.getAsLong() < 0L
                    || minimumPriceMinor.isPresent()
                    && maximumPriceMinor.isPresent()
                    && minimumPriceMinor.getAsLong()
                    > maximumPriceMinor.getAsLong()
                    || !exactFingerprint.isEmpty()
                    && !exactFingerprint.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "Auction browse query is invalid");
            }
        }

        public static Query browse(
                String search,
                Sort sort,
                int pageIndex,
                int pageSize,
                long nowMillis
        ) {
            return new Query(search, "", "", Optional.empty(),
                    OptionalLong.empty(), OptionalLong.empty(),
                    Set.of(AuctionListingState.ACTIVE), sort,
                    pageIndex, pageSize, nowMillis);
        }

        List<String> tokens() {
            return search.isEmpty() ? List.of()
                    : List.of(search.toLowerCase(Locale.ROOT)
                    .split("\\s+"));
        }
    }

    public record Card(
            UUID listingId,
            UUID sellerId,
            long revision,
            String registryId,
            String exactFingerprint,
            String categoryId,
            int count,
            AuctionListingType type,
            AuctionListingState state,
            long displayPriceMinor,
            long minimumNextBidMinor,
            long buyoutMinor,
            long acceptedBidCount,
            long remainingMillis,
            boolean watched
    ) {
        public Card {
            listingId = Objects.requireNonNull(listingId, "listingId");
            sellerId = Objects.requireNonNull(sellerId, "sellerId");
            registryId = normalize(registryId, 256, "registryId");
            exactFingerprint = normalize(
                    exactFingerprint, 64, "exactFingerprint")
                    .toLowerCase(Locale.ROOT);
            categoryId = normalize(categoryId, 128, "categoryId");
            type = Objects.requireNonNull(type, "type");
            state = Objects.requireNonNull(state, "state");
            if (listingId.equals(new UUID(0L, 0L))
                    || sellerId.equals(new UUID(0L, 0L))
                    || revision < 0L || count <= 0
                    || displayPriceMinor <= 0L
                    || minimumNextBidMinor < 0L || buyoutMinor < 0L
                    || acceptedBidCount < 0L || remainingMillis < 0L
                    || !exactFingerprint.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "Auction browse card is invalid");
            }
        }

        static Card from(
                AuctionListing listing,
                long nowMillis,
                boolean watched
        ) {
            return new Card(listing.listingId(), listing.sellerId(),
                    listing.revision(), listing.itemLot().registryId(),
                    listing.itemLot().fingerprint(),
                    listing.itemLot().categoryId(),
                    listing.itemLot().count(), listing.type(),
                    listing.state(), displayPrice(listing),
                    listing.type().acceptsBids()
                            ? listing.minimumNextBid() : 0L,
                    listing.buyoutMinor(),
                    listing.acceptedBidCount(),
                    AuctionBrowseIndex.remainingMillis(
                            listing, nowMillis), watched);
        }
    }

    public record Page(
            int pageIndex,
            int pageSize,
            int totalResults,
            int pageCount,
            List<Card> cards
    ) {
        public Page {
            cards = List.copyOf(cards);
            if (pageIndex < 0 || pageSize <= 0 || totalResults < 0
                    || pageCount < 0 || cards.size() > pageSize) {
                throw new IllegalArgumentException(
                        "Auction browse page is invalid");
            }
        }
    }

    public enum Sort {
        ENDING_SOON,
        NEWEST,
        LOWEST_PRICE,
        HIGHEST_PRICE,
        MOST_BIDS,
        SELLER
    }

    private static String normalize(
            String value,
            int maximum,
            String label
    ) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.length() > maximum) {
            throw new IllegalArgumentException(
                    "Auction browse " + label + " is invalid");
        }
        return normalized;
    }
}
