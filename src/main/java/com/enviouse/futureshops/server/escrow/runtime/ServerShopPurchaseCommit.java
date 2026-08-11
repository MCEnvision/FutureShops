package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayload;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayloadCodec;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.DimensionAwareShopReference;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLot;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLotType;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipant;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipantRole;
import com.enviouse.futureshops.server.escrow.model.EscrowParty;
import com.enviouse.futureshops.server.escrow.model.EscrowProtectionLevel;
import com.enviouse.futureshops.server.escrow.model.EscrowRequestKey;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import com.enviouse.futureshops.server.escrow.model.MoneyAmount;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommand;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record ServerShopPurchaseCommit(
        UUID requestId,
        UUID playerId,
        String shopId,
        boolean cartCheckout,
        PaymentSource paymentSource,
        long walletBeforeMinorUnits,
        long debtBeforeMinorUnits,
        String currencyName,
        int currencyDecimals,
        List<Line> lines,
        EscrowTransaction completedTransaction,
        List<EscrowTransaction> completedLineTransactions,
        LedgerTransaction ledgerTransaction,
        StockMutationCommand.ReserveBatch stockReservation,
        StockMutationCommand.ResolveBatch stockCommit,
        List<EscrowClaim> itemClaims
) {
    public static final String CURRENCY_ID = "futureshops:wallet";
    public static final String CLAIM_LABEL = "Server shop purchase";
    public static final int MAX_LINES = 256;
    public static final int MAX_IDENTIFIER_LENGTH = 160;
    public static final int MAX_CURRENCY_NAME_LENGTH = 128;

    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public ServerShopPurchaseCommit {
        requestId = requireUuid(requestId, "requestId");
        playerId = requireUuid(playerId, "playerId");
        shopId = requireIdentifier(shopId, "shopId");
        Objects.requireNonNull(paymentSource, "paymentSource");
        if (walletBeforeMinorUnits < 0L || debtBeforeMinorUnits > 0L) {
            throw new IllegalArgumentException(
                    "Server shop wallet snapshot is invalid");
        }
        currencyName = Objects.requireNonNull(
                currencyName, "currencyName").strip();
        if (currencyName.isEmpty()
                || currencyName.length() > MAX_CURRENCY_NAME_LENGTH
                || currencyDecimals < 0 || currencyDecimals > 9) {
            throw new IllegalArgumentException(
                    "Server shop currency metadata is invalid");
        }
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        if (lines.isEmpty() || lines.size() > MAX_LINES) {
            throw new IllegalArgumentException(
                    "Server shop purchase line count is invalid");
        }
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).lineIndex() != index) {
                throw new IllegalArgumentException(
                        "Server shop purchase lines are not canonical");
            }
        }
        completedTransaction = Objects.requireNonNull(
                completedTransaction, "completedTransaction");
        completedLineTransactions = List.copyOf(Objects.requireNonNull(
                completedLineTransactions, "completedLineTransactions"));
        ledgerTransaction = Objects.requireNonNull(
                ledgerTransaction, "ledgerTransaction");
        stockReservation = Objects.requireNonNull(
                stockReservation, "stockReservation");
        stockCommit = Objects.requireNonNull(stockCommit, "stockCommit");
        itemClaims = List.copyOf(Objects.requireNonNull(
                itemClaims, "itemClaims"));
        validateCanonical(new CanonicalInput(requestId, playerId, shopId,
                cartCheckout, paymentSource, walletBeforeMinorUnits,
                debtBeforeMinorUnits, currencyName, currencyDecimals, lines,
                completedTransaction.timestamps().createdAt(),
                completedTransaction.shopReference().orElseThrow(),
                physicalFunding(completedTransaction),
                paymentSource == PaymentSource.PHYSICAL
                        && physicalFunding(completedTransaction).isEmpty()),
                completedTransaction, completedLineTransactions,
                ledgerTransaction, stockReservation, stockCommit,
                itemClaims);
    }

    public static ServerShopPurchaseCommit create(
            UUID requestId,
            UUID playerId,
            String shopId,
            boolean cartCheckout,
            PaymentSource paymentSource,
            long walletBeforeMinorUnits,
            long debtBeforeMinorUnits,
            String currencyName,
            int currencyDecimals,
            List<Line> lines,
            DimensionAwareShopReference shopReference,
            Instant now
    ) {
        return create(requestId, playerId, shopId, cartCheckout,
                paymentSource, walletBeforeMinorUnits, debtBeforeMinorUnits,
                currencyName, currencyDecimals, lines, shopReference, now,
                Optional.empty());
    }

    public static ServerShopPurchaseCommit create(
            UUID requestId,
            UUID playerId,
            String shopId,
            boolean cartCheckout,
            PaymentSource paymentSource,
            long walletBeforeMinorUnits,
            long debtBeforeMinorUnits,
            String currencyName,
            int currencyDecimals,
            List<Line> lines,
            DimensionAwareShopReference shopReference,
            Instant now,
            Optional<PhysicalFunding> physicalFunding
    ) {
        CanonicalInput input = new CanonicalInput(requestId, playerId,
                shopId, cartCheckout, paymentSource,
                walletBeforeMinorUnits, debtBeforeMinorUnits, currencyName,
                currencyDecimals, lines, now, shopReference,
                physicalFunding, false);
        CanonicalComponents components = canonical(input);
        return new ServerShopPurchaseCommit(input.requestId(),
                input.playerId(), input.shopId(), input.cartCheckout(),
                input.paymentSource(), input.walletBeforeMinorUnits(),
                input.debtBeforeMinorUnits(), input.currencyName(),
                input.currencyDecimals(), input.lines(),
                components.parent(), components.children(),
                components.ledger(), components.reserve(),
                components.commit(), components.claims());
    }

    public long totalCostMinorUnits() {
        long total = 0L;
        for (Line line : lines) {
            total = Math.addExact(total, line.lineCostMinorUnits());
        }
        return total;
    }

    public int totalQuantity() {
        int total = 0;
        for (Line line : lines) {
            total = Math.addExact(total, line.quantity());
        }
        return total;
    }

    public long resultingWalletMinorUnits() {
        return paymentSource == PaymentSource.WALLET
                || physicalFunding().isEmpty()
                ? Math.subtractExact(walletBeforeMinorUnits,
                totalCostMinorUnits()) : walletBeforeMinorUnits;
    }

    static ServerShopPurchaseCommit createLegacyPhysical(
            UUID requestId,
            UUID playerId,
            String shopId,
            boolean cartCheckout,
            long walletBeforeMinorUnits,
            long debtBeforeMinorUnits,
            String currencyName,
            int currencyDecimals,
            List<Line> lines,
            DimensionAwareShopReference shopReference,
            Instant now
    ) {
        CanonicalInput input = new CanonicalInput(requestId, playerId,
                shopId, cartCheckout, PaymentSource.PHYSICAL,
                walletBeforeMinorUnits, debtBeforeMinorUnits, currencyName,
                currencyDecimals, lines, now, shopReference,
                Optional.empty(), true);
        CanonicalComponents components = canonical(input);
        return new ServerShopPurchaseCommit(input.requestId(),
                input.playerId(), input.shopId(), input.cartCheckout(),
                input.paymentSource(), input.walletBeforeMinorUnits(),
                input.debtBeforeMinorUnits(), input.currencyName(),
                input.currencyDecimals(), input.lines(), components.parent(),
                components.children(), components.ledger(),
                components.reserve(), components.commit(),
                components.claims());
    }

    public Optional<PhysicalFunding> physicalFunding() {
        return physicalFunding(completedTransaction);
    }

    static Optional<PhysicalFunding> physicalFunding(
            EscrowTransaction transaction
    ) {
        Map<String, String> attributes = transaction.assetLots()
                .stream().filter(asset -> asset.type()
                        == EscrowAssetLotType.WALLET_MONEY)
                .findFirst().orElseThrow().attributes();
        boolean present = attributes.containsKey("funding_transaction_id")
                || attributes.containsKey("funding_claim_id")
                || attributes.containsKey("funding_amount");
        if (!present) {
            return Optional.empty();
        }
        try {
            return Optional.of(new PhysicalFunding(
                    transaction.transactionId().value(),
                    UUID.fromString(attributes.get("funding_transaction_id")),
                    UUID.fromString(attributes.get("funding_claim_id")),
                    Long.parseLong(attributes.get("funding_amount"))));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Server shop physical funding evidence is invalid",
                    exception);
        }
    }

    public String wireFingerprint() {
        return wireFingerprint(requestId, playerId, shopId, cartCheckout,
                paymentSource, lines.stream().map(line ->
                        new IdentityLine(line.listingId(), line.quantity()))
                        .toList());
    }

    public static String wireFingerprint(
            UUID requestId,
            UUID playerId,
            String shopId,
            boolean cartCheckout,
            PaymentSource paymentSource,
            List<IdentityLine> lines
    ) {
        requireUuid(requestId, "requestId");
        requireUuid(playerId, "playerId");
        String normalizedShop = requireIdentifier(shopId, "shopId");
        Objects.requireNonNull(paymentSource, "paymentSource");
        List<IdentityLine> canonicalLines = List.copyOf(
                Objects.requireNonNull(lines, "lines")).stream()
                .sorted(Comparator.comparing(IdentityLine::listingId))
                .toList();
        if (canonicalLines.isEmpty() || canonicalLines.size() > MAX_LINES
                || new HashSet<>(canonicalLines.stream().map(
                IdentityLine::listingId).toList()).size()
                != canonicalLines.size()) {
            throw new IllegalArgumentException(
                    "Server shop purchase identity lines are invalid");
        }
        StringBuilder material = new StringBuilder(
                "futureshops server shop wire v1\u0000")
                .append(requestId).append('\u0000')
                .append(playerId).append('\u0000')
                .append(normalizedShop).append('\u0000')
                .append(cartCheckout).append('\u0000')
                .append(paymentSource.name());
        for (IdentityLine line : canonicalLines) {
            material.append('\u0000').append(line.listingId())
                    .append('\u0000').append(line.quantity());
        }
        return sha256(material.toString());
    }

    public static UUID childTransactionId(
            UUID requestId,
            int lineIndex,
            String listingId
    ) {
        return deterministicUuid("line", requestId,
                lineIndex + "\u0000" + requireIdentifier(
                        listingId, "listingId"));
    }

    public static UUID stockReserveRequestId(UUID requestId) {
        return deterministicUuid("stock reserve", requestId, "");
    }

    public static UUID stockCommitRequestId(UUID requestId) {
        return deterministicUuid("stock commit", requestId, "");
    }

    public static LedgerAccountId walletAccount(UUID playerId) {
        return new LedgerAccountId(LedgerAccountType.PLAYER_WALLET,
                requireUuid(playerId, "playerId").toString());
    }

    public static LedgerAccountId debtAccount(UUID playerId) {
        return new LedgerAccountId(LedgerAccountType.PLAYER_DEBT,
                requireUuid(playerId, "playerId").toString());
    }

    public static LedgerAccountId sinkAccount(String shopId) {
        String normalized = requireIdentifier(shopId, "shopId");
        String owner = normalized.length() <= 128
                ? normalized : "shop." + sha256(normalized);
        return new LedgerAccountId(LedgerAccountType.SERVER_SHOP_SINK,
                owner);
    }

    public static LedgerAccountId claimAccount(UUID claimId) {
        return new LedgerAccountId(LedgerAccountType.PLAYER_CLAIM,
                requireUuid(claimId, "claimId").toString());
    }

    public static String physicalFundingDeliveryKey(
            UUID requestId,
            UUID claimId
    ) {
        return "server.shop.physical.custody."
                + requireUuid(requestId, "requestId") + "."
                + requireUuid(claimId, "claimId");
    }

    private static CanonicalComponents canonical(CanonicalInput input) {
        long total = totalCost(input.lines());
        boolean walletFunded = input.paymentSource() == PaymentSource.WALLET
                || input.legacyPhysicalWallet();
        if (walletFunded
                && input.walletBeforeMinorUnits() < total) {
            throw new IllegalArgumentException(
                    "Server shop wallet snapshot cannot fund purchase");
        }
        if (!input.legacyPhysicalWallet()
                && (input.paymentSource() == PaymentSource.PHYSICAL)
                != input.physicalFunding().isPresent()
                || input.physicalFunding().isPresent()
                && input.physicalFunding().orElseThrow().amountMinorUnits()
                != total
                || input.physicalFunding().isPresent()
                && !input.physicalFunding().orElseThrow().purchaseRequestId()
                .equals(input.requestId())) {
            throw new IllegalArgumentException(
                    "Server shop physical funding does not fund purchase");
        }
        List<EscrowClaim> claims = claims(input);
        EscrowTransaction parent = transaction(input,
                input.requestId(), Optional.empty(), input.cartCheckout()
                        ? EscrowOperation.SERVER_SHOP_CART
                        : EscrowOperation.SERVER_SHOP_BUY,
                input.lines(), true);
        List<EscrowTransaction> children = input.cartCheckout()
                ? input.lines().stream().map(line -> transaction(input,
                childTransactionId(input.requestId(), line.lineIndex(),
                        line.listingId()),
                Optional.of(new EscrowTransactionId(input.requestId())),
                EscrowOperation.SERVER_SHOP_BUY, List.of(line), false))
                .toList() : List.of();
        LedgerAccountId sourceAccount = input.physicalFunding()
                .map(value -> claimAccount(value.claimId()))
                .orElseGet(() -> walletAccount(input.playerId()));
        LedgerTransaction ledger = new LedgerTransaction(input.requestId(),
                "server.shop.purchase." + input.requestId(),
                "Server shop purchase", List.of(
                new LedgerLeg(sourceAccount, Math.negateExact(total)),
                new LedgerLeg(sinkAccount(input.shopId()), total)));
        List<StockReservationRequest> reservations = input.lines().stream()
                .map(line -> new StockReservationRequest(
                        new StockKey(input.shopId(), line.listingId()),
                        StockReservationDirection.OUTBOUND,
                        line.quantity(), line.expectedStockRevision()))
                .toList();
        StockMutationCommand.ReserveBatch reserve =
                new StockMutationCommand.ReserveBatch(
                        stockReserveRequestId(input.requestId()),
                        input.requestId(), reservations, input.now());
        List<StockReservationResolution> resolutions = reservations.stream()
                .map(value -> new StockReservationResolution(
                        StockReservationId.forTransaction(input.requestId(),
                                value.stockKey(), value.direction()), 0L))
                .toList();
        StockMutationCommand.ResolveBatch commit =
                new StockMutationCommand.ResolveBatch(
                        stockCommitRequestId(input.requestId()),
                        StockMutationType.COMMIT_BATCH, input.requestId(),
                        resolutions, input.now());
        return new CanonicalComponents(parent, children, ledger, reserve,
                commit, claims);
    }

    private static EscrowTransaction transaction(
            CanonicalInput input,
            UUID transactionId,
            Optional<EscrowTransactionId> parentId,
            EscrowOperation operation,
            List<Line> includedLines,
            boolean parent
    ) {
        EscrowParty player = EscrowParty.player(input.playerId());
        EscrowParty shop = EscrowParty.shop(input.shopId());
        Set<EscrowParticipant> participants = Set.of(
                new EscrowParticipant(player, Set.of(
                        EscrowParticipantRole.INITIATOR,
                        EscrowParticipantRole.PAYER,
                        EscrowParticipantRole.BUYER,
                        EscrowParticipantRole.RECIPIENT)),
                new EscrowParticipant(shop, Set.of(
                        EscrowParticipantRole.BENEFICIARY,
                        EscrowParticipantRole.SELLER)));
        List<EscrowAssetLot> assets = new ArrayList<>();
        long lineTotal = totalCost(includedLines);
        Map<String, String> moneyAttributes = new java.util.HashMap<>();
        moneyAttributes.put("wire_fingerprint", wireFingerprint(input.requestId(),
                        input.playerId(), input.shopId(),
                        input.cartCheckout(), input.paymentSource(),
                        input.lines().stream().map(line ->
                                new IdentityLine(line.listingId(),
                                        line.quantity())).toList()));
        moneyAttributes.put("payment_source", input.paymentSource().name());
        moneyAttributes.put("shop_id", input.shopId());
        moneyAttributes.put("cart_checkout", Boolean.toString(
                input.cartCheckout()));
        moneyAttributes.put("wallet_before", Long.toString(
                input.walletBeforeMinorUnits()));
        moneyAttributes.put("debt_before", Long.toString(
                input.debtBeforeMinorUnits()));
        moneyAttributes.put("wallet_after", Long.toString(
                input.paymentSource() == PaymentSource.WALLET
                        || input.legacyPhysicalWallet()
                        ? Math.subtractExact(input.walletBeforeMinorUnits(),
                        totalCost(input.lines()))
                        : input.walletBeforeMinorUnits()));
        moneyAttributes.put("currency_name", input.currencyName());
        moneyAttributes.put("currency_decimals", Integer.toString(
                input.currencyDecimals()));
        input.physicalFunding().ifPresent(value -> {
            moneyAttributes.put("funding_transaction_id",
                    value.transactionId().toString());
            moneyAttributes.put("funding_claim_id",
                    value.claimId().toString());
            moneyAttributes.put("funding_amount",
                    Long.toString(value.amountMinorUnits()));
        });
        assets.add(new EscrowAssetLot(
                deterministicUuid(parent ? "parent money" : "line money",
                        transactionId, ""),
                EscrowAssetLotType.WALLET_MONEY,
                EscrowProtectionLevel.PROTECTED, player, shop, 1L,
                Optional.of(new MoneyAmount(CURRENCY_ID, lineTotal)),
                new byte[0], moneyAttributes));
        for (Line line : includedLines) {
            for (ExactItemClaimPayload output : line.outputs()) {
                assets.add(new EscrowAssetLot(output.lotId(),
                        EscrowAssetLotType.ITEM_STACK,
                        EscrowProtectionLevel.PROTECTED, shop, player,
                        output.stackCount(), Optional.empty(),
                        ExactItemClaimPayloadCodec.encode(output), Map.of(
                        "line_index", Integer.toString(line.lineIndex()),
                        "listing_id", line.listingId(),
                        "item_id", line.itemId(),
                        "line_cost", Long.toString(
                                line.lineCostMinorUnits()),
                        "stock_revision", Long.toString(
                                line.expectedStockRevision()),
                        "portion_index", Integer.toString(
                                output.portionIndex()),
                        "portion_count", Integer.toString(
                                output.portionCount()))));
            }
        }
        long configRevision = configurationRevision(input);
        EscrowTransaction created = EscrowTransaction.create(
                new EscrowTransactionId(transactionId), parentId,
                new EscrowRequestKey((parent ? "server.shop.purchase."
                        : "server.shop.line.") + quoteFingerprint(input,
                        includedLines, transactionId)), operation,
                participants, assets, input.now(), configRevision,
                Optional.of(input.shopReference()));
        return complete(created, input.now());
    }

    private static EscrowTransaction complete(
            EscrowTransaction transaction,
            Instant now
    ) {
        return transaction.transitionTo(EscrowState.VALIDATED, now)
                .transitionTo(EscrowState.HOLDING, now)
                .transitionTo(EscrowState.HELD, now)
                .transitionTo(EscrowState.COMMIT_DECIDED, now)
                .transitionTo(EscrowState.COMMITTED, now)
                .transitionTo(EscrowState.CLAIMS_CREATED, now)
                .transitionTo(EscrowState.COMPLETED, now);
    }

    private static List<EscrowClaim> claims(CanonicalInput input) {
        List<EscrowClaim> claims = new ArrayList<>();
        for (Line line : input.lines()) {
            for (ExactItemClaimPayload output : line.outputs()) {
                claims.add(new EscrowClaim(output.lotId(),
                        input.requestId(), input.playerId(),
                        claimSourceKey(output), ClaimKind.ITEM,
                        output.stackCount(), output.stackCount(),
                        ExactItemClaimPayloadCodec.encode(output),
                        ClaimStatus.PENDING, CLAIM_LABEL, input.now(),
                        input.now()));
            }
        }
        return List.copyOf(claims);
    }

    private static void validateCanonical(
            CanonicalInput input,
            EscrowTransaction parent,
            List<EscrowTransaction> children,
            LedgerTransaction ledger,
            StockMutationCommand.ReserveBatch reserve,
            StockMutationCommand.ResolveBatch commit,
            List<EscrowClaim> claims
    ) {
        CanonicalComponents expected = canonical(input);
        if (!expected.parent().equals(parent)
                || !expected.children().equals(children)
                || !expected.ledger().equals(ledger)
                || !expected.reserve().equals(reserve)
                || !expected.commit().equals(commit)
                || !expected.claims().equals(claims)) {
            throw new IllegalArgumentException(
                    "Server shop purchase commit evidence conflicts");
        }
    }

    private static long totalCost(List<Line> lines) {
        long total = 0L;
        for (Line line : lines) {
            total = Math.addExact(total, line.lineCostMinorUnits());
        }
        if (total <= 0L) {
            throw new IllegalArgumentException(
                    "Server shop purchase total is invalid");
        }
        return total;
    }

    private static long configurationRevision(CanonicalInput input) {
        byte[] digest = HexFormat.of().parseHex(quoteFingerprint(input,
                input.lines(), input.requestId()));
        long value = 0L;
        for (int index = 0; index < Long.BYTES; index++) {
            value = value << 8 | digest[index] & 0xffL;
        }
        return value & Long.MAX_VALUE;
    }

    private static String quoteFingerprint(
            CanonicalInput input,
            List<Line> lines,
            UUID transactionId
    ) {
        StringBuilder value = new StringBuilder(
                "futureshops server shop quote v1\u0000")
                .append(input.requestId()).append('\u0000')
                .append(transactionId).append('\u0000')
                .append(input.playerId()).append('\u0000')
                .append(input.shopId()).append('\u0000')
                .append(input.cartCheckout()).append('\u0000')
                .append(input.paymentSource()).append('\u0000')
                .append(input.walletBeforeMinorUnits()).append('\u0000')
                .append(input.debtBeforeMinorUnits()).append('\u0000')
                .append(input.currencyName()).append('\u0000')
                .append(input.currencyDecimals());
        input.physicalFunding().ifPresent(funding -> value
                .append('\u0000').append(funding.purchaseRequestId())
                .append('\u0000').append(funding.transactionId())
                .append('\u0000').append(funding.claimId())
                .append('\u0000').append(funding.amountMinorUnits()));
        for (Line line : lines) {
            value.append('\u0000').append(line.lineIndex())
                    .append('\u0000').append(line.listingId())
                    .append('\u0000').append(line.itemId())
                    .append('\u0000').append(line.quantity())
                    .append('\u0000').append(line.lineCostMinorUnits())
                    .append('\u0000').append(line.expectedStockRevision());
            for (ExactItemClaimPayload output : line.outputs()) {
                value.append('\u0000').append(output.fingerprint());
            }
        }
        return sha256(value.toString());
    }

    private static UUID deterministicUuid(
            String purpose,
            UUID requestId,
            String suffix
    ) {
        return UUID.nameUUIDFromBytes((
                "futureshops server shop purchase v1\u0000" + purpose
                        + "\u0000" + requireUuid(requestId, "requestId")
                        + "\u0000" + suffix).getBytes(
                StandardCharsets.UTF_8));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(
                    "SHA-256").digest(value.getBytes(
                    StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable", exception);
        }
    }

    private static UUID requireUuid(UUID value, String name) {
        UUID safe = Objects.requireNonNull(value, name);
        if (safe.equals(ZERO_UUID)) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return safe;
    }

    private static String requireIdentifier(String value, String name) {
        String safe = Objects.requireNonNull(value, name).strip();
        if (safe.isEmpty() || safe.length() > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return safe;
    }

    public record IdentityLine(String listingId, int quantity) {
        public IdentityLine {
            listingId = requireIdentifier(listingId, "listingId");
            if (quantity <= 0) {
                throw new IllegalArgumentException(
                        "Server shop identity quantity is invalid");
            }
        }
    }

    public record PhysicalFunding(
            UUID purchaseRequestId,
            UUID transactionId,
            UUID claimId,
            long amountMinorUnits
    ) {
        public PhysicalFunding {
            purchaseRequestId = requireUuid(purchaseRequestId,
                    "purchaseRequestId");
            transactionId = requireUuid(transactionId, "transactionId");
            claimId = requireUuid(claimId, "claimId");
            if (purchaseRequestId.equals(transactionId)
                    || purchaseRequestId.equals(claimId)
                    || transactionId.equals(claimId)
                    || amountMinorUnits <= 0L) {
                throw new IllegalArgumentException(
                        "Server shop physical funding is invalid");
            }
        }
    }

    public record Line(
            int lineIndex,
            String listingId,
            String itemId,
            int quantity,
            long lineCostMinorUnits,
            long expectedStockRevision,
            List<ExactItemClaimPayload> outputs
    ) {
        public Line {
            listingId = requireIdentifier(listingId, "listingId");
            itemId = requireIdentifier(itemId, "itemId");
            outputs = List.copyOf(Objects.requireNonNull(
                    outputs, "outputs"));
            if (lineIndex < 0 || lineIndex >= MAX_LINES
                    || quantity <= 0 || lineCostMinorUnits <= 0L
                    || expectedStockRevision < 0L || outputs.isEmpty()
                    || outputs.size() > ExactItemClaimPayload.MAX_PORTIONS) {
                throw new IllegalArgumentException(
                        "Server shop purchase line is invalid");
            }
            int delivered = 0;
            for (int index = 0; index < outputs.size(); index++) {
                ExactItemClaimPayload output = Objects.requireNonNull(
                        outputs.get(index), "output");
                String sourceKey = outputSourceKey(
                        output.sourceTransactionId(), lineIndex);
                if (output.portionIndex() != index
                        || output.portionCount() != outputs.size()
                        || !output.sourceKey().equals(sourceKey)
                        || !output.registryItemId().equals(itemId)) {
                    throw new IllegalArgumentException(
                            "Server shop output identity is invalid");
                }
                delivered = Math.addExact(delivered, output.stackCount());
            }
            if (delivered != quantity) {
                throw new IllegalArgumentException(
                        "Server shop output quantity does not match line");
            }
        }
    }

    public static String outputSourceKey(
            UUID requestId,
            int lineIndex
    ) {
        requireUuid(requestId, "requestId");
        if (lineIndex < 0 || lineIndex >= MAX_LINES) {
            throw new IllegalArgumentException(
                    "Server shop output line is invalid");
        }
        return "server.shop.output." + requestId + "." + lineIndex;
    }

    public static String claimSourceKey(ExactItemClaimPayload output) {
        Objects.requireNonNull(output, "output");
        return output.sourceKey() + "." + output.portionIndex();
    }

    private record CanonicalInput(
            UUID requestId,
            UUID playerId,
            String shopId,
            boolean cartCheckout,
            PaymentSource paymentSource,
            long walletBeforeMinorUnits,
            long debtBeforeMinorUnits,
            String currencyName,
            int currencyDecimals,
            List<Line> lines,
            Instant now,
            DimensionAwareShopReference shopReference,
            Optional<PhysicalFunding> physicalFunding,
            boolean legacyPhysicalWallet
    ) {
        private CanonicalInput {
            requestId = requireUuid(requestId, "requestId");
            playerId = requireUuid(playerId, "playerId");
            shopId = requireIdentifier(shopId, "shopId");
            Objects.requireNonNull(paymentSource, "paymentSource");
            currencyName = Objects.requireNonNull(
                    currencyName, "currencyName").strip();
            lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
            now = Objects.requireNonNull(now, "now");
            shopReference = Objects.requireNonNull(
                    shopReference, "shopReference");
            physicalFunding = Objects.requireNonNull(
                    physicalFunding, "physicalFunding");
            if (legacyPhysicalWallet
                    && (paymentSource != PaymentSource.PHYSICAL
                    || physicalFunding.isPresent())) {
                throw new IllegalArgumentException(
                        "Legacy physical wallet funding is invalid");
            }
            if (!shopReference.shopId().equals(shopId)) {
                throw new IllegalArgumentException(
                        "Server shop reference conflicts");
            }
            for (Line line : lines) {
                for (ExactItemClaimPayload output : line.outputs()) {
                    if (!output.sourceTransactionId().equals(requestId)) {
                        throw new IllegalArgumentException(
                                "Server shop output transaction conflicts");
                    }
                }
            }
        }
    }

    private record CanonicalComponents(
            EscrowTransaction parent,
            List<EscrowTransaction> children,
            LedgerTransaction ledger,
            StockMutationCommand.ReserveBatch reserve,
            StockMutationCommand.ResolveBatch commit,
            List<EscrowClaim> claims
    ) {
    }
}
