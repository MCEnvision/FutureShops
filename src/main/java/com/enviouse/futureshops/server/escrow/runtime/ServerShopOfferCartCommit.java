package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.catalog.offer.ServerShopBundleSavings;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.network.ServerShopOfferNetworkCodec;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopAssetEndpoint;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopAtomicCommit;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopEscrowIntent;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopOperation;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopPaymentSource;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommand;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommandCodec;
import com.enviouse.futureshops.server.escrow.stock.StockMutationType;
import com.enviouse.futureshops.server.escrow.stock.StockReservationDirection;
import com.enviouse.futureshops.server.escrow.stock.StockReservationId;
import com.enviouse.futureshops.server.escrow.stock.StockReservationRequest;
import com.enviouse.futureshops.server.escrow.stock.StockReservationResolution;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record ServerShopOfferCartCommit(
        int schemaVersion,
        UUID requestId,
        UUID playerId,
        String shopId,
        List<Line> lines,
        Optional<PaymentSource> paymentSource,
        Instant quotedAt,
        boolean claimsPending,
        PlayerShopAtomicCommit valueCommit,
        StockMutationCommand.ReserveBatch stockReservation,
        StockMutationCommand.ResolveBatch stockCommit,
        String configurationFingerprint
) {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAXIMUM_LINES = 256;
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public ServerShopOfferCartCommit {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(playerId, "playerId");
        shopId = identifier(shopId);
        lines = canonical(lines);
        paymentSource = Objects.requireNonNull(
                paymentSource, "paymentSource");
        Objects.requireNonNull(quotedAt, "quotedAt");
        Objects.requireNonNull(valueCommit, "valueCommit");
        Objects.requireNonNull(stockReservation, "stockReservation");
        Objects.requireNonNull(stockCommit, "stockCommit");
        Objects.requireNonNull(configurationFingerprint,
                "configurationFingerprint");
        if (schemaVersion != CURRENT_SCHEMA
                || requestId.equals(ZERO_UUID)
                || playerId.equals(ZERO_UUID)
                || !configurationFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "Server shop offer cart commit is invalid");
        }
        validateValue(requestId, playerId, shopId, paymentSource,
                quotedAt, valueCommit);
        validateStock(requestId, shopId, lines,
                stockReservation, stockCommit);
        if (!configurationFingerprint.equals(fingerprint(
                schemaVersion, requestId, playerId, shopId, lines,
                paymentSource, quotedAt, claimsPending, valueCommit,
                stockReservation, stockCommit))) {
            throw new IllegalArgumentException(
                    "Server shop offer cart fingerprint is invalid");
        }
    }

    public static ServerShopOfferCartCommit create(
            UUID requestId,
            UUID playerId,
            String shopId,
            List<Line> lines,
            Optional<PaymentSource> paymentSource,
            Instant quotedAt,
            boolean claimsPending,
            PlayerShopAtomicCommit valueCommit,
            StockMutationCommand.ReserveBatch stockReservation,
            StockMutationCommand.ResolveBatch stockCommit
    ) {
        List<Line> canonical = canonical(lines);
        String fingerprint = fingerprint(
                CURRENT_SCHEMA, requestId, playerId, shopId, canonical,
                paymentSource, quotedAt, claimsPending, valueCommit,
                stockReservation, stockCommit);
        return new ServerShopOfferCartCommit(
                CURRENT_SCHEMA, requestId, playerId, shopId, canonical,
                paymentSource, quotedAt, claimsPending, valueCommit,
                stockReservation, stockCommit, fingerprint);
    }

    public static ServerShopOfferCartCommit create(
            UUID requestId,
            UUID playerId,
            String shopId,
            List<Line> lines,
            Optional<PaymentSource> paymentSource,
            Instant quotedAt,
            PlayerShopAtomicCommit valueCommit,
            StockMutationCommand.ReserveBatch stockReservation,
            StockMutationCommand.ResolveBatch stockCommit
    ) {
        return create(requestId, playerId, shopId, lines, paymentSource,
                quotedAt, false, valueCommit, stockReservation,
                stockCommit);
    }

    public static Line captureLine(
            ServerShopOfferListing listing,
            String optionId,
            int quantity,
            Optional<ServerShopBundleSavings.Snapshot> savings
    ) {
        byte[] encoded =
                ServerShopOfferNetworkCodec.encodeListingBytes(listing);
        return new Line(listing.listingId(), optionId, quantity,
                listing.revision(), sha256(encoded), savings);
    }

    public static UUID stockReserveRequestId(UUID requestId) {
        return deterministic("cart stock reserve", requestId);
    }

    public static UUID stockCommitRequestId(UUID requestId) {
        return deterministic("cart stock commit", requestId);
    }

    public static UUID stockReleaseRequestId(UUID requestId) {
        return deterministic("cart stock release", requestId);
    }

    private static void validateValue(
            UUID requestId,
            UUID playerId,
            String shopId,
            Optional<PaymentSource> paymentSource,
            Instant quotedAt,
            PlayerShopAtomicCommit valueCommit
    ) {
        PlayerShopEscrowIntent intent = valueCommit.committedIntent();
        if (!valueCommit.commitId().equals(requestId)
                || !intent.requestId().equals(requestId)
                || !intent.actorId().equals(playerId)
                || !intent.shopIdentity().shopId().equals(shopId)
                || intent.shopIdentity().identityRevision() != 0L
                || intent.operation()
                != PlayerShopOperation.SERVER_SHOP_OFFER_ACQUIRE
                || intent.requestedUnits() != 1
                || !intent.listing().listingId().equals("offer_cart")
                || intent.quoteCreatedAt().compareTo(quotedAt) != 0
                || intent.itemTransfers().stream().noneMatch(value ->
                value.source().kind()
                        == PlayerShopAssetEndpoint.Kind.ADMIN_MINT)
                || intent.moneyTransfers().size() > 1) {
            throw new IllegalArgumentException(
                    "Server shop offer cart value evidence is invalid");
        }
        if (intent.moneyTransfers().isEmpty()) {
            if (paymentSource.isPresent()
                    || intent.paymentSource()
                    != PlayerShopPaymentSource.NONE) {
                throw new IllegalArgumentException(
                        "Server shop offer cart money evidence conflicts");
            }
            return;
        }
        PaymentSource expected = switch (intent.paymentSource()) {
            case WALLET -> PaymentSource.WALLET;
            case INVENTORY_CASH -> PaymentSource.PHYSICAL;
            case NONE -> throw new IllegalArgumentException(
                    "Server shop offer cart payment evidence is missing");
        };
        if (paymentSource.filter(expected::equals).isEmpty()) {
            throw new IllegalArgumentException(
                    "Server shop offer cart payment source conflicts");
        }
    }

    private static void validateStock(
            UUID requestId,
            String shopId,
            List<Line> lines,
            StockMutationCommand.ReserveBatch reserve,
            StockMutationCommand.ResolveBatch commit
    ) {
        Map<String, Integer> quantities = stockQuantities(lines);
        if (!reserve.requestId().equals(stockReserveRequestId(requestId))
                || !reserve.transactionId().equals(requestId)
                || reserve.reservations().size() != quantities.size()
                || !commit.requestId().equals(
                stockCommitRequestId(requestId))
                || commit.operation() != StockMutationType.COMMIT_BATCH
                || !commit.transactionId().equals(requestId)
                || commit.reservations().size() != quantities.size()) {
            throw new IllegalArgumentException(
                    "Server shop offer cart stock evidence is invalid");
        }
        Map<StockKey, StockReservationRequest> reservedByKey =
                reserve.reservations().stream().collect(
                        Collectors.toMap(
                                StockReservationRequest::stockKey,
                                value -> value));
        Set<StockReservationId> committedReservationIds =
                commit.reservations().stream()
                        .map(StockReservationResolution::reservationId)
                        .collect(Collectors.toSet());
        for (Map.Entry<String, Integer> expected
                : quantities.entrySet()) {
            StockKey key = new StockKey(shopId, expected.getKey());
            StockReservationRequest reserved = reservedByKey.get(key);
            if (reserved == null
                    || reserved.direction()
                    != StockReservationDirection.OUTBOUND
                    || reserved.quantity()
                    < expected.getValue()
                    || !committedReservationIds.contains(
                    StockReservationId.forTransaction(
                            requestId, key,
                            StockReservationDirection.OUTBOUND))) {
                throw new IllegalArgumentException(
                        "Server shop offer cart stock line conflicts");
            }
        }
    }

    public static Map<String, Integer> stockQuantities(List<Line> lines) {
        LinkedHashMap<String, Integer> quantities =
                new LinkedHashMap<>();
        for (Line line : canonical(lines)) {
            quantities.merge(line.listingId(), line.quantity(),
                    Math::addExact);
        }
        return Collections.unmodifiableMap(quantities);
    }

    private static List<Line> canonical(List<Line> lines) {
        List<Line> result = List.copyOf(
                Objects.requireNonNull(lines, "lines")).stream()
                .sorted(Comparator.comparing(Line::listingId)
                        .thenComparing(Line::optionId))
                .toList();
        if (result.isEmpty() || result.size() > MAXIMUM_LINES) {
            throw new IllegalArgumentException(
                    "Server shop offer cart line count is invalid");
        }
        for (int index = 1; index < result.size(); index++) {
            Line previous = result.get(index - 1);
            Line current = result.get(index);
            if (previous.listingId().equals(current.listingId())
                    && previous.optionId().equals(current.optionId())) {
                throw new IllegalArgumentException(
                        "Server shop offer cart contains duplicate lines");
            }
        }
        return result;
    }

    private static String fingerprint(
            int schemaVersion,
            UUID requestId,
            UUID playerId,
            String shopId,
            List<Line> lines,
            Optional<PaymentSource> paymentSource,
            Instant quotedAt,
            boolean claimsPending,
            PlayerShopAtomicCommit valueCommit,
            StockMutationCommand.ReserveBatch stockReservation,
            StockMutationCommand.ResolveBatch stockCommit
    ) {
        StringBuilder material = new StringBuilder(
                "futureshops server shop offer cart commit v1")
                .append('\u0000').append(schemaVersion)
                .append('\u0000').append(requestId)
                .append('\u0000').append(playerId)
                .append('\u0000').append(shopId)
                .append('\u0000').append(
                        paymentSource.map(Enum::name).orElse("NONE"))
                .append('\u0000').append(quotedAt)
                .append('\u0000').append(claimsPending)
                .append('\u0000').append(
                        valueCommit.commitFingerprint());
        for (Line line : lines) {
            material.append('\u0000').append(line.fingerprintMaterial());
        }
        material.append('\u0000').append(HexFormat.of().formatHex(
                StockMutationCommandCodec.encode(stockReservation)));
        material.append('\u0000').append(HexFormat.of().formatHex(
                StockMutationCommandCodec.encode(stockCommit)));
        return sha256(material.toString()
                .getBytes(StandardCharsets.UTF_8));
    }

    private static UUID deterministic(
            String namespace,
            UUID requestId
    ) {
        Objects.requireNonNull(requestId, "requestId");
        if (requestId.equals(ZERO_UUID)) {
            throw new IllegalArgumentException(
                    "Server shop offer cart request is invalid");
        }
        return UUID.nameUUIDFromBytes((
                "futureshops server shop offer " + namespace + " "
                        + requestId).getBytes(StandardCharsets.UTF_8));
    }

    private static String identifier(String value) {
        String normalized =
                Objects.requireNonNull(value, "identifier").strip();
        if (normalized.isEmpty() || normalized.length() > 160) {
            throw new IllegalArgumentException(
                    "Server shop offer cart identifier is invalid");
        }
        return normalized;
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA 256 is unavailable", exception);
        }
    }

    public record Line(
            String listingId,
            String optionId,
            int quantity,
            long offerRevision,
            String listingFingerprint,
            Optional<ServerShopBundleSavings.Snapshot> savings
    ) {
        public Line {
            listingId = identifier(listingId);
            optionId = identifier(optionId);
            listingFingerprint = Objects.requireNonNull(
                    listingFingerprint, "listingFingerprint");
            savings = Objects.requireNonNull(savings, "savings");
            if (quantity <= 0 || quantity > 2304
                    || offerRevision < 0L
                    || offerRevision > ServerShopOfferCommit.MAX_REVISION
                    || !listingFingerprint.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "Server shop offer cart line is invalid");
            }
        }

        private String fingerprintMaterial() {
            StringBuilder value = new StringBuilder()
                    .append(listingId).append('\u0000')
                    .append(optionId).append('\u0000')
                    .append(quantity).append('\u0000')
                    .append(offerRevision).append('\u0000')
                    .append(listingFingerprint);
            savings.ifPresent(snapshot -> value.append('\u0000')
                    .append(snapshot.individualTotalMinorUnits())
                    .append('\u0000')
                    .append(snapshot.bundleTotalMinorUnits())
                    .append('\u0000')
                    .append(snapshot.savingsMinorUnits())
                    .append('\u0000')
                    .append(snapshot.savingsBasisPoints())
                    .append('\u0000')
                    .append(snapshot.comparisonRevisions()));
            return value.toString();
        }
    }
}
