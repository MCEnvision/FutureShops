package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.catalog.offer.OfferAction;
import com.enviouse.futureshops.catalog.offer.ServerShopBundleSavings;
import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopAssetEndpoint;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopAtomicCommit;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopEscrowIntent;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopOperation;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopPaymentSource;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommand;
import com.enviouse.futureshops.server.escrow.stock.StockMutationType;
import com.enviouse.futureshops.server.escrow.stock.StockReservationDirection;
import com.enviouse.futureshops.server.escrow.stock.StockReservationId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.regex.Pattern;

public record ServerShopOfferCommit(
        int schemaVersion,
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
        boolean claimsPending,
        PlayerShopAtomicCommit valueCommit,
        StockMutationCommand.ReserveBatch stockReservation,
        StockMutationCommand.ResolveBatch stockCommit,
        Optional<ServerShopBundleSavings.Snapshot> bundleSavings,
        String configurationFingerprint
) {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_IDENTIFIER_LENGTH = 160;
    public static final long MAX_REVISION = 1_000_000_000_000L;

    private static final Pattern FINGERPRINT =
            Pattern.compile("[0-9a-f]{64}");
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public ServerShopOfferCommit {
        requestId = requireUuid(requestId, "requestId");
        playerId = requireUuid(playerId, "playerId");
        shopId = requireIdentifier(shopId, "shopId");
        listingId = requireIdentifier(listingId, "listingId");
        optionId = requireIdentifier(optionId, "optionId");
        action = Objects.requireNonNull(action, "action");
        paymentSource = Objects.requireNonNull(
                paymentSource, "paymentSource");
        quotedAt = Objects.requireNonNull(quotedAt, "quotedAt");
        valueCommit = Objects.requireNonNull(valueCommit, "valueCommit");
        stockReservation = Objects.requireNonNull(
                stockReservation, "stockReservation");
        stockCommit = Objects.requireNonNull(stockCommit, "stockCommit");
        bundleSavings = Objects.requireNonNull(
                bundleSavings, "bundleSavings");
        configurationFingerprint = Objects.requireNonNull(
                configurationFingerprint, "configurationFingerprint");
        if (schemaVersion != CURRENT_SCHEMA || quantity <= 0
                || offerRevision < 0L || offerRevision > MAX_REVISION
                || !FINGERPRINT.matcher(configurationFingerprint).matches()) {
            throw new IllegalArgumentException(
                    "Server shop offer commit values are invalid");
        }
        validateValueEvidence(requestId, playerId, shopId, listingId,
                action, quantity, offerRevision, paymentSource, quotedAt,
                valueCommit);
        validateStockEvidence(requestId, shopId, listingId, action,
                quantity, stockReservation, stockCommit);
        if (!configurationFingerprint.equals(
                computeFingerprint(schemaVersion, requestId, playerId,
                        shopId, listingId, optionId, action, quantity,
                        offerRevision, paymentSource, quotedAt, claimsPending,
                        valueCommit,
                        stockReservation, stockCommit, bundleSavings))) {
            throw new IllegalArgumentException(
                    "Server shop offer commit fingerprint is invalid");
        }
    }

    public static ServerShopOfferCommit create(
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
            PlayerShopAtomicCommit valueCommit,
            StockMutationCommand.ReserveBatch stockReservation,
            StockMutationCommand.ResolveBatch stockCommit
    ) {
        return create(requestId, playerId, shopId, listingId, optionId,
                action, quantity, offerRevision, paymentSource, quotedAt,
                false, valueCommit, stockReservation, stockCommit,
                Optional.empty());
    }

    public static ServerShopOfferCommit create(
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
            boolean claimsPending,
            PlayerShopAtomicCommit valueCommit,
            StockMutationCommand.ReserveBatch stockReservation,
            StockMutationCommand.ResolveBatch stockCommit,
            Optional<ServerShopBundleSavings.Snapshot> bundleSavings
    ) {
        String fingerprint = computeFingerprint(CURRENT_SCHEMA,
                requestId, playerId, shopId, listingId, optionId, action,
                quantity, offerRevision, paymentSource, quotedAt,
                claimsPending, valueCommit, stockReservation, stockCommit,
                bundleSavings);
        return new ServerShopOfferCommit(CURRENT_SCHEMA, requestId,
                playerId, shopId, listingId, optionId, action, quantity,
                offerRevision, paymentSource, quotedAt, claimsPending,
                valueCommit,
                stockReservation, stockCommit, bundleSavings, fingerprint);
    }

    public static ServerShopOfferCommit create(
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
            PlayerShopAtomicCommit valueCommit,
            StockMutationCommand.ReserveBatch stockReservation,
            StockMutationCommand.ResolveBatch stockCommit,
            Optional<ServerShopBundleSavings.Snapshot> bundleSavings
    ) {
        return create(requestId, playerId, shopId, listingId, optionId,
                action, quantity, offerRevision, paymentSource, quotedAt,
                false, valueCommit, stockReservation, stockCommit,
                bundleSavings);
    }

    public OptionalLong moneyDebitMinorUnits() {
        if (action != OfferAction.ACQUIRE_FROM_SHOP
                || valueCommit.committedIntent().moneyTransfers()
                .isEmpty()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(valueCommit.committedIntent()
                .moneyTransfers().get(0).amountMinorUnits());
    }

    public OptionalLong moneyPayoutMinorUnits() {
        if (action != OfferAction.SELL_TO_SHOP) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(valueCommit.committedIntent()
                .moneyTransfers().get(0).amountMinorUnits());
    }

    public static UUID stockReserveRequestId(UUID requestId) {
        return deterministic("stock reserve", requestId);
    }

    public static UUID stockCommitRequestId(UUID requestId) {
        return deterministic("stock commit", requestId);
    }

    public static UUID stockReleaseRequestId(UUID requestId) {
        return deterministic("stock release", requestId);
    }

    private static void validateValueEvidence(
            UUID requestId,
            UUID playerId,
            String shopId,
            String listingId,
            OfferAction action,
            int quantity,
            long offerRevision,
            Optional<PaymentSource> paymentSource,
            Instant quotedAt,
            PlayerShopAtomicCommit valueCommit
    ) {
        PlayerShopEscrowIntent intent = valueCommit.committedIntent();
        if (!valueCommit.commitId().equals(requestId)
                || !intent.requestId().equals(requestId)
                || !intent.actorId().equals(playerId)
                || !intent.shopIdentity().shopId().equals(shopId)
                || !intent.listing().listingId().equals(listingId)
                || intent.shopIdentity().identityRevision() != offerRevision
                || intent.requestedUnits() != quantity
                || intent.quoteCreatedAt().compareTo(quotedAt) != 0) {
            throw new IllegalArgumentException(
                    "Server shop offer value identity is invalid");
        }
        if (action == OfferAction.ACQUIRE_FROM_SHOP) {
            validateAcquireValue(intent, paymentSource);
        } else {
            validateSellValue(intent, paymentSource);
        }
    }

    private static void validateAcquireValue(
            PlayerShopEscrowIntent intent,
            Optional<PaymentSource> paymentSource
    ) {
        if (intent.operation()
                != PlayerShopOperation.SERVER_SHOP_OFFER_ACQUIRE
                || intent.itemTransfers().stream().noneMatch(value ->
                value.source().kind()
                        == PlayerShopAssetEndpoint.Kind.ADMIN_MINT)
                || intent.moneyTransfers().size() > 1) {
            throw new IllegalArgumentException(
                    "Server shop acquire evidence is invalid");
        }
        if (intent.moneyTransfers().isEmpty()) {
            if (paymentSource.isPresent()
                    || intent.paymentSource()
                    != PlayerShopPaymentSource.NONE) {
                throw new IllegalArgumentException(
                        "Server shop acquire absent money evidence conflicts");
            }
            return;
        }
        PaymentSource expected = switch (intent.paymentSource()) {
            case WALLET -> PaymentSource.WALLET;
            case INVENTORY_CASH -> PaymentSource.PHYSICAL;
            case NONE -> throw new IllegalArgumentException(
                    "Server shop acquire payment evidence is missing");
        };
        if (paymentSource.filter(expected::equals).isEmpty()) {
            throw new IllegalArgumentException(
                    "Server shop acquire payment source conflicts");
        }
    }

    private static void validateSellValue(
            PlayerShopEscrowIntent intent,
            Optional<PaymentSource> paymentSource
    ) {
        if (intent.operation()
                != PlayerShopOperation.SERVER_SHOP_OFFER_SELL
                || paymentSource.isPresent()
                || intent.paymentSource() != PlayerShopPaymentSource.NONE
                || intent.moneyTransfers().size() != 1
                || intent.moneyTransfers().get(0).source().kind()
                != PlayerShopAssetEndpoint.Kind.ADMIN_MINT
                || intent.itemTransfers().stream().noneMatch(value ->
                value.source().kind()
                        == PlayerShopAssetEndpoint.Kind.ACTOR_INVENTORY)) {
            throw new IllegalArgumentException(
                    "Server shop sell evidence is invalid");
        }
    }

    private static void validateStockEvidence(
            UUID requestId,
            String shopId,
            String listingId,
            OfferAction action,
            int quantity,
            StockMutationCommand.ReserveBatch stockReservation,
            StockMutationCommand.ResolveBatch stockCommit
    ) {
        StockKey key = new StockKey(shopId, listingId);
        StockReservationDirection direction =
                action == OfferAction.ACQUIRE_FROM_SHOP
                        ? StockReservationDirection.OUTBOUND
                        : StockReservationDirection.INBOUND;
        if (!stockReservation.requestId().equals(
                stockReserveRequestId(requestId))
                || !stockReservation.transactionId().equals(requestId)
                || stockReservation.reservations().size() != 1
                || !stockReservation.reservations().get(0)
                .stockKey().equals(key)
                || stockReservation.reservations().get(0).direction()
                != direction
                || stockReservation.reservations().get(0).quantity()
                < quantity
                || action == OfferAction.SELL_TO_SHOP
                && stockReservation.reservations().get(0).quantity()
                != quantity
                || !stockCommit.requestId().equals(
                stockCommitRequestId(requestId))
                || stockCommit.operation()
                != StockMutationType.COMMIT_BATCH
                || !stockCommit.transactionId().equals(requestId)
                || stockCommit.reservations().size() != 1
                || !stockCommit.reservations().get(0).reservationId()
                .equals(StockReservationId.forTransaction(
                        requestId, key, direction))) {
            throw new IllegalArgumentException(
                    "Server shop offer stock evidence is invalid");
        }
    }

    private static String computeFingerprint(
            int schemaVersion,
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
            boolean claimsPending,
            PlayerShopAtomicCommit valueCommit,
            StockMutationCommand.ReserveBatch stockReservation,
            StockMutationCommand.ResolveBatch stockCommit,
            Optional<ServerShopBundleSavings.Snapshot> bundleSavings
    ) {
        Objects.requireNonNull(paymentSource, "paymentSource");
        String material = "futureshops server shop offer commit v1\u0000"
                + schemaVersion + "\u0000" + requestId + "\u0000"
                + playerId + "\u0000" + shopId + "\u0000"
                + listingId + "\u0000" + optionId + "\u0000"
                + action.name() + "\u0000" + quantity + "\u0000"
                + offerRevision + "\u0000"
                + paymentSource.map(Enum::name).orElse("NONE") + "\u0000"
                + quotedAt + "\u0000" + claimsPending + "\u0000"
                + valueCommit.commitFingerprint() + "\u0000"
                + HexFormat.of().formatHex(
                com.enviouse.futureshops.server.escrow.stock
                        .StockMutationCommandCodec.encode(stockReservation))
                + "\u0000" + HexFormat.of().formatHex(
                com.enviouse.futureshops.server.escrow.stock
                        .StockMutationCommandCodec.encode(stockCommit))
                + "\u0000" + savingsFingerprint(bundleSavings);
        return sha256(material.getBytes(StandardCharsets.UTF_8));
    }

    private static String savingsFingerprint(
            Optional<ServerShopBundleSavings.Snapshot> savings
    ) {
        if (savings.isEmpty()) {
            return "NONE";
        }
        ServerShopBundleSavings.Snapshot snapshot = savings.orElseThrow();
        StringBuilder material = new StringBuilder()
                .append(snapshot.individualTotalMinorUnits()).append('\u0000')
                .append(snapshot.bundleTotalMinorUnits()).append('\u0000')
                .append(snapshot.savingsMinorUnits()).append('\u0000')
                .append(snapshot.savingsBasisPoints());
        for (ServerShopBundleSavings.ComparisonRevision revision
                : snapshot.comparisonRevisions()) {
            material.append('\u0000').append(revision.componentId())
                    .append('\u0000').append(revision.listingId())
                    .append('\u0000').append(revision.optionId())
                    .append('\u0000').append(revision.revision());
        }
        return material.toString();
    }

    private static UUID deterministic(String namespace, UUID requestId) {
        requireUuid(requestId, "requestId");
        return UUID.nameUUIDFromBytes((
                "futureshops server shop offer " + namespace + " "
                        + requestId).getBytes(StandardCharsets.UTF_8));
    }

    private static UUID requireUuid(UUID value, String label) {
        Objects.requireNonNull(value, label);
        if (ZERO_UUID.equals(value)) {
            throw new IllegalArgumentException(
                    "Server shop offer UUID is invalid");
        }
        return value;
    }

    private static String requireIdentifier(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty()
                || normalized.length() > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException(
                    "Server shop offer identifier is invalid");
        }
        return normalized;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA 256 is unavailable", exception);
        }
    }
}
