package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimAttemptResult;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayload;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayloadCodec;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.DimensionAwareShopReference;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLot;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLotType;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import com.enviouse.futureshops.server.escrow.stock.StockReservation;
import com.enviouse.futureshops.server.escrow.stock.StockReservationDirection;
import com.enviouse.futureshops.server.escrow.stock.StockReservationState;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

public final class ServerShopPurchaseService {
    private ServerShopPurchaseService() {
    }

    public static Optional<Result> resolveReplay(Identity identity) {
        Objects.requireNonNull(identity, "identity");
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (runtime == null) {
            return Optional.empty();
        }
        try {
            return resolveReplay(identity, new LiveBackend(runtime));
        } catch (RuntimeException exception) {
            return Optional.of(Result.failure(Status.ESCROW_UNAVAILABLE,
                    identity.requestId()));
        }
    }

    public static Result purchase(PreparedRequest request) {
        Objects.requireNonNull(request, "request");
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (runtime == null) {
            return Result.failure(Status.ESCROW_UNAVAILABLE,
                    request.identity().requestId());
        }
        Backend backend = new LiveBackend(runtime);
        try {
            Optional<Result> replay = resolveReplay(
                    request.identity(), backend);
            if (replay.isPresent()) {
                return replay.orElseThrow();
            }
            if (!runtime.isReady()) {
                return Result.failure(Status.ESCROW_UNAVAILABLE,
                        request.identity().requestId());
            }
            return executeFresh(request, backend);
        } catch (RuntimeException exception) {
            try {
                return resolveReplay(request.identity(), backend)
                        .orElseGet(() -> Result.failure(
                                Status.RECOVERY_REQUIRED,
                                request.identity().requestId()));
            } catch (RuntimeException replayFailure) {
                return Result.failure(Status.RECOVERY_REQUIRED,
                        request.identity().requestId());
            }
        }
    }

    static Result execute(
            PreparedRequest request,
            Backend backend
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(backend, "backend");
        try {
            return resolveReplay(request.identity(), backend)
                    .orElseGet(() -> executeFresh(request, backend));
        } catch (RuntimeException exception) {
            return Result.failure(Status.RECOVERY_REQUIRED,
                    request.identity().requestId());
        }
    }

    static Optional<Result> resolveReplay(
            Identity identity,
            Backend backend
    ) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(backend, "backend");
        Optional<EscrowTransaction> stored = backend.transaction(
                identity.requestId());
        Optional<LedgerTransaction> ledger = backend.ledgerTransaction(
                identity.requestId());
        List<EscrowClaim> claims = backend.claimsForTransaction(
                identity.requestId());
        List<StockReservation> reservations = backend.stockReservations(
                identity.requestId());
        if (stored.isEmpty()) {
            if (ledger.isPresent() || !claims.isEmpty()
                    || !reservations.isEmpty()) {
                return Optional.of(Result.failure(
                        Status.RECOVERY_REQUIRED, identity.requestId()));
            }
            return Optional.empty();
        }
        EscrowTransaction transaction = stored.orElseThrow();
        EscrowOperation expectedOperation = identity.cartCheckout()
                ? EscrowOperation.SERVER_SHOP_CART
                : EscrowOperation.SERVER_SHOP_BUY;
        if (transaction.operation() != expectedOperation
                || !transaction.transactionId().value().equals(
                identity.requestId())) {
            return Optional.of(Result.failure(Status.REQUEST_CONFLICT,
                    identity.requestId()));
        }
        Map<String, String> attributes;
        try {
            attributes = moneyAsset(transaction).attributes();
        } catch (RuntimeException exception) {
            return Optional.of(Result.failure(Status.RECOVERY_REQUIRED,
                    identity.requestId()));
        }
        if (!identity.wireFingerprint().equals(
                attributes.get("wire_fingerprint"))) {
            return Optional.of(Result.failure(Status.REQUEST_CONFLICT,
                    identity.requestId()));
        }
        if (transaction.state() != EscrowState.COMPLETED
                || ledger.isEmpty()) {
            return Optional.of(Result.failure(Status.RECOVERY_REQUIRED,
                    identity.requestId()));
        }
        try {
            List<ServerShopPurchaseCommit.Line> lines =
                    parseLines(transaction);
            long walletBefore = parseLong(attributes, "wallet_before");
            long debtBefore = parseLong(attributes, "debt_before");
            PaymentSource source = PaymentSource.valueOf(
                    requireAttribute(attributes, "payment_source"));
            String currencyName = requireAttribute(
                    attributes, "currency_name");
            int currencyDecimals = parseInt(attributes,
                    "currency_decimals");
            Optional<ServerShopPurchaseCommit.PhysicalFunding> funding =
                    ServerShopPurchaseCommit.physicalFunding(transaction);
            ServerShopPurchaseCommit expected = source
                    == PaymentSource.PHYSICAL && funding.isEmpty()
                    ? ServerShopPurchaseCommit.createLegacyPhysical(
                            identity.requestId(), identity.playerId(),
                            identity.shopId(), identity.cartCheckout(),
                            walletBefore, debtBefore, currencyName,
                            currencyDecimals, lines,
                            transaction.shopReference().orElseThrow(),
                            transaction.timestamps().createdAt())
                    : ServerShopPurchaseCommit.create(identity.requestId(),
                            identity.playerId(), identity.shopId(),
                            identity.cartCheckout(), source, walletBefore,
                            debtBefore, currencyName, currencyDecimals,
                            lines, transaction.shopReference().orElseThrow(),
                            transaction.timestamps().createdAt(),
                            funding);
            if (!expected.completedTransaction().equals(transaction)
                    || !expected.ledgerTransaction().equals(
                    ledger.orElseThrow())
                    || !childrenMatch(expected, backend)
                    || !physicalFundingMatches(expected, backend)
                    || !claimsMatch(expected.itemClaims(), claims)
                    || !reservationsMatch(expected, reservations)) {
                return Optional.of(Result.failure(
                        Status.RECOVERY_REQUIRED, identity.requestId()));
            }
            return Optional.of(Result.success(expected, claims, true));
        } catch (RuntimeException exception) {
            return Optional.of(Result.failure(Status.RECOVERY_REQUIRED,
                    identity.requestId()));
        }
    }

    private static Result executeFresh(
            PreparedRequest request,
            Backend backend
    ) {
        Identity identity = request.identity();
        if (!identity.lines().equals(request.lines().stream()
                .map(line -> new ServerShopPurchaseCommit.IdentityLine(
                        line.listingId(), line.quantity())).toList())) {
            return Result.failure(Status.INVALID_REQUEST,
                    identity.requestId());
        }
        long walletBefore = backend.ledgerBalance(
                ServerShopPurchaseCommit.walletAccount(
                        identity.playerId()));
        long debtBefore = backend.ledgerBalance(
                ServerShopPurchaseCommit.debtAccount(identity.playerId()));
        long total = 0L;
        try {
            for (ServerShopPurchaseCommit.Line line : request.lines()) {
                total = Math.addExact(total, line.lineCostMinorUnits());
            }
        } catch (ArithmeticException exception) {
            return Result.failure(Status.INVALID_REQUEST,
                    identity.requestId());
        }
        if (identity.paymentSource() == PaymentSource.WALLET
                && walletBefore < total) {
            return Result.failure(Status.INSUFFICIENT_FUNDS,
                    identity.requestId());
        }
        if (identity.paymentSource() == PaymentSource.PHYSICAL
                && !physicalFundingReady(request, backend, total)) {
            return Result.failure(Status.RECOVERY_REQUIRED,
                    identity.requestId());
        }
        ServerShopPurchaseCommit commit = ServerShopPurchaseCommit.create(
                identity.requestId(), identity.playerId(), identity.shopId(),
                identity.cartCheckout(), identity.paymentSource(),
                walletBefore, debtBefore, request.currencyName(),
                request.currencyDecimals(), request.lines(),
                request.shopReference(), request.now(),
                request.physicalFunding());
        try {
            EscrowCommitResult result = backend.commit(commit);
            return Result.success(commit, commit.itemClaims(),
                    result.replayed());
        } catch (RuntimeException exception) {
            return resolveReplay(identity, backend)
                    .orElseGet(() -> Result.failure(
                            Status.RECOVERY_REQUIRED,
                            identity.requestId()));
        }
    }

    private static EscrowAssetLot moneyAsset(
            EscrowTransaction transaction
    ) {
        List<EscrowAssetLot> money = transaction.assetLots().stream()
                .filter(asset -> asset.type()
                        == EscrowAssetLotType.WALLET_MONEY)
                .toList();
        if (money.size() != 1) {
            throw new IllegalArgumentException(
                    "Server shop money evidence is invalid");
        }
        return money.get(0);
    }

    private static List<ServerShopPurchaseCommit.Line> parseLines(
            EscrowTransaction transaction
    ) {
        Map<Integer, ParsedLine> parsed = new TreeMap<>();
        for (EscrowAssetLot asset : transaction.assetLots()) {
            if (asset.type() != EscrowAssetLotType.ITEM_STACK) {
                continue;
            }
            Map<String, String> attributes = asset.attributes();
            int lineIndex = parseInt(attributes, "line_index");
            ParsedLine line = parsed.computeIfAbsent(lineIndex,
                    ignored -> new ParsedLine(lineIndex,
                            requireAttribute(attributes, "listing_id"),
                            requireAttribute(attributes, "item_id"),
                            parseLong(attributes, "line_cost"),
                            parseLong(attributes, "stock_revision"),
                            parseInt(attributes, "portion_count")));
            line.add(parseInt(attributes, "portion_index"),
                    ExactItemClaimPayloadCodec.decode(
                            asset.serializedPayload()), asset.quantity());
        }
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException(
                    "Server shop item evidence is missing");
        }
        List<ServerShopPurchaseCommit.Line> lines = new ArrayList<>();
        for (ParsedLine value : parsed.values()) {
            lines.add(value.finish());
        }
        return List.copyOf(lines);
    }

    private static boolean childrenMatch(
            ServerShopPurchaseCommit expected,
            Backend backend
    ) {
        if (!expected.cartCheckout()) {
            return expected.completedLineTransactions().isEmpty();
        }
        for (EscrowTransaction child
                : expected.completedLineTransactions()) {
            Optional<EscrowTransaction> stored = backend.transaction(
                    child.transactionId().value());
            if (stored.isEmpty() || !stored.orElseThrow().equals(child)) {
                return false;
            }
        }
        return true;
    }

    private static boolean physicalFundingReady(
            PreparedRequest request,
            Backend backend,
            long total
    ) {
        Optional<ServerShopPurchaseCommit.PhysicalFunding> optional =
                request.physicalFunding();
        if (optional.isEmpty()) {
            return false;
        }
        ServerShopPurchaseCommit.PhysicalFunding funding =
                optional.orElseThrow();
        Optional<EscrowClaim> optionalClaim = backend.claim(
                funding.claimId());
        if (funding.amountMinorUnits() != total
                || !funding.purchaseRequestId().equals(
                request.identity().requestId())
                || optionalClaim.isEmpty()) {
            return false;
        }
        EscrowClaim claim = optionalClaim.orElseThrow();
        return claim.transactionId().equals(funding.transactionId())
                && claim.ownerId().equals(request.identity().playerId())
                && claim.kind() == ClaimKind.INTERNAL_ESCROW_MONEY
                && claim.status() == ClaimStatus.PENDING
                && claim.originalUnits() == total
                && claim.remainingUnits() == total
                && backend.ledgerBalance(
                ServerShopPurchaseCommit.claimAccount(funding.claimId()))
                == total;
    }

    private static boolean physicalFundingMatches(
            ServerShopPurchaseCommit commit,
            Backend backend
    ) {
        Optional<ServerShopPurchaseCommit.PhysicalFunding> optional =
                commit.physicalFunding();
        if (optional.isEmpty()) {
            return commit.paymentSource() == PaymentSource.WALLET
                    || commit.paymentSource() == PaymentSource.PHYSICAL;
        }
        ServerShopPurchaseCommit.PhysicalFunding funding =
                optional.orElseThrow();
        EscrowClaim claim = backend.claim(funding.claimId()).orElse(null);
        ClaimAttemptResult attempt = backend.claimAttempt(
                ServerShopPurchaseCommit.physicalFundingDeliveryKey(
                        commit.requestId(), funding.claimId())).orElse(null);
        return commit.paymentSource() == PaymentSource.PHYSICAL
                && funding.amountMinorUnits()
                == commit.totalCostMinorUnits()
                && claim != null
                && claim.transactionId().equals(funding.transactionId())
                && claim.ownerId().equals(commit.playerId())
                && claim.kind() == ClaimKind.INTERNAL_ESCROW_MONEY
                && claim.status() == ClaimStatus.COMPLETED
                && claim.originalUnits() == funding.amountMinorUnits()
                && claim.remainingUnits() == 0L
                && backend.ledgerBalance(
                ServerShopPurchaseCommit.claimAccount(funding.claimId()))
                == 0L
                && attempt != null
                && attempt.claimId().equals(funding.claimId())
                && attempt.deliveredUnits() == funding.amountMinorUnits()
                && attempt.remainingUnits() == 0L
                && attempt.status() == ClaimStatus.COMPLETED
                && attempt.deliveredAt().equals(commit.completedTransaction()
                .timestamps().createdAt());
    }

    private static boolean claimsMatch(
            List<EscrowClaim> expected,
            List<EscrowClaim> actual
    ) {
        if (expected.size() != actual.size()) {
            return false;
        }
        Map<UUID, EscrowClaim> actualById = new HashMap<>();
        for (EscrowClaim claim : actual) {
            if (actualById.put(claim.claimId(), claim) != null) {
                return false;
            }
        }
        for (EscrowClaim pending : expected) {
            EscrowClaim stored = actualById.get(pending.claimId());
            if (stored == null
                    || !stored.transactionId().equals(
                    pending.transactionId())
                    || !stored.ownerId().equals(pending.ownerId())
                    || !stored.sourceKey().equals(pending.sourceKey())
                    || stored.kind() != ClaimKind.ITEM
                    || stored.originalUnits() != pending.originalUnits()
                    || stored.remainingUnits() < 0L
                    || stored.remainingUnits() > stored.originalUnits()
                    || !Arrays.equals(stored.payload(), pending.payload())
                    || !stored.label().equals(pending.label())
                    || !stored.createdAt().equals(pending.createdAt())) {
                return false;
            }
        }
        return true;
    }

    private static boolean reservationsMatch(
            ServerShopPurchaseCommit expected,
            List<StockReservation> reservations
    ) {
        if (reservations.size() != expected.lines().size()) {
            return false;
        }
        Map<String, StockReservation> byListing = new HashMap<>();
        for (StockReservation reservation : reservations) {
            if (!reservation.transactionId().equals(expected.requestId())
                    || reservation.direction()
                    != StockReservationDirection.OUTBOUND
                    || reservation.state()
                    != StockReservationState.COMMITTED
                    || byListing.put(reservation.stockKey().listingId(),
                    reservation) != null) {
                return false;
            }
        }
        for (ServerShopPurchaseCommit.Line line : expected.lines()) {
            StockReservation reservation = byListing.get(line.listingId());
            if (reservation == null
                    || !reservation.stockKey().shopId().equals(
                    expected.shopId())
                    || reservation.quantity() != line.quantity()) {
                return false;
            }
        }
        return true;
    }

    private static String requireAttribute(
            Map<String, String> attributes,
            String key
    ) {
        String value = attributes.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Server shop evidence attribute is missing");
        }
        return value;
    }

    private static long parseLong(
            Map<String, String> attributes,
            String key
    ) {
        return Long.parseLong(requireAttribute(attributes, key));
    }

    private static int parseInt(
            Map<String, String> attributes,
            String key
    ) {
        return Integer.parseInt(requireAttribute(attributes, key));
    }

    public static ServerShopPurchaseCommit.Line captureLine(
            UUID requestId,
            int lineIndex,
            String listingId,
            String itemId,
            int quantity,
            long lineCostMinorUnits,
            long expectedStockRevision,
            List<ItemStack> exactStacks
    ) {
        List<ItemStack> stacks = List.copyOf(Objects.requireNonNull(
                exactStacks, "exactStacks"));
        String sourceKey = ServerShopPurchaseCommit.outputSourceKey(
                requestId, lineIndex);
        List<ExactItemClaimPayload> outputs = new ArrayList<>(stacks.size());
        for (int index = 0; index < stacks.size(); index++) {
            outputs.add(ExactItemClaimPayload.capture(requestId, sourceKey,
                    index, stacks.size(), stacks.get(index)));
        }
        return new ServerShopPurchaseCommit.Line(lineIndex, listingId,
                itemId, quantity, lineCostMinorUnits,
                expectedStockRevision, outputs);
    }

    public enum Status {
        SUCCESS,
        INVALID_REQUEST,
        REQUEST_CONFLICT,
        INSUFFICIENT_FUNDS,
        OUT_OF_STOCK,
        QUOTE_CHANGED,
        ESCROW_UNAVAILABLE,
        RECOVERY_REQUIRED
    }

    public record Identity(
            UUID requestId,
            UUID playerId,
            String shopId,
            boolean cartCheckout,
            PaymentSource paymentSource,
            List<ServerShopPurchaseCommit.IdentityLine> lines
    ) {
        public Identity {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(shopId, "shopId");
            Objects.requireNonNull(paymentSource, "paymentSource");
            lines = List.copyOf(Objects.requireNonNull(lines, "lines"))
                    .stream().sorted(Comparator.comparing(
                            ServerShopPurchaseCommit.IdentityLine::listingId))
                    .toList();
            ServerShopPurchaseCommit.wireFingerprint(requestId, playerId,
                    shopId, cartCheckout, paymentSource, lines);
        }

        public String wireFingerprint() {
            return ServerShopPurchaseCommit.wireFingerprint(requestId,
                    playerId, shopId, cartCheckout, paymentSource, lines);
        }
    }

    public record PreparedRequest(
            Identity identity,
            List<ServerShopPurchaseCommit.Line> lines,
            String currencyName,
            int currencyDecimals,
            DimensionAwareShopReference shopReference,
            Instant now,
            Optional<ServerShopPurchaseCommit.PhysicalFunding> physicalFunding
    ) {
        public PreparedRequest(
                Identity identity,
                List<ServerShopPurchaseCommit.Line> lines,
                String currencyName,
                int currencyDecimals,
                DimensionAwareShopReference shopReference,
                Instant now
        ) {
            this(identity, lines, currencyName, currencyDecimals,
                    shopReference, now, Optional.empty());
        }

        public PreparedRequest {
            Objects.requireNonNull(identity, "identity");
            lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
            Objects.requireNonNull(currencyName, "currencyName");
            Objects.requireNonNull(shopReference, "shopReference");
            Objects.requireNonNull(now, "now");
            physicalFunding = Objects.requireNonNull(
                    physicalFunding, "physicalFunding");
            if ((identity.paymentSource() == PaymentSource.PHYSICAL)
                    != physicalFunding.isPresent()) {
                throw new IllegalArgumentException(
                        "Server shop payment source funding conflicts");
            }
        }
    }

    public record Result(
            Status status,
            UUID requestId,
            Optional<UUID> transactionId,
            long resultingBalanceMinorUnits,
            long totalCostMinorUnits,
            int totalQuantity,
            List<ServerShopPurchaseCommit.Line> lines,
            List<EscrowClaim> itemClaims,
            Instant occurredAt,
            boolean replayed
    ) {
        public Result {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(requestId, "requestId");
            transactionId = Objects.requireNonNull(
                    transactionId, "transactionId");
            lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
            itemClaims = List.copyOf(Objects.requireNonNull(
                    itemClaims, "itemClaims"));
            Objects.requireNonNull(occurredAt, "occurredAt");
            if (status == Status.SUCCESS
                    && (transactionId.isEmpty()
                    || totalCostMinorUnits <= 0L || totalQuantity <= 0
                    || lines.isEmpty() || itemClaims.isEmpty()
                    || occurredAt.equals(Instant.EPOCH))
                    || status != Status.SUCCESS
                    && (transactionId.isPresent()
                    || totalCostMinorUnits != 0L || totalQuantity != 0
                    || !lines.isEmpty() || !itemClaims.isEmpty()
                    || !occurredAt.equals(Instant.EPOCH)
                    || replayed)) {
                throw new IllegalArgumentException(
                        "Server shop purchase result is invalid");
            }
        }

        static Result success(
                ServerShopPurchaseCommit commit,
                List<EscrowClaim> claims,
                boolean replayed
        ) {
            return new Result(Status.SUCCESS, commit.requestId(),
                    Optional.of(commit.requestId()),
                    commit.resultingWalletMinorUnits(),
                    commit.totalCostMinorUnits(), commit.totalQuantity(),
                    commit.lines(), claims,
                    commit.completedTransaction().timestamps().createdAt(),
                    replayed);
        }

        static Result failure(Status status, UUID requestId) {
            return new Result(status, requestId, Optional.empty(), 0L, 0L,
                    0, List.of(), List.of(), Instant.EPOCH, false);
        }

        public boolean successful() {
            return status == Status.SUCCESS;
        }
    }

    interface Backend {
        Optional<EscrowTransaction> transaction(UUID transactionId);

        Optional<LedgerTransaction> ledgerTransaction(UUID transactionId);

        List<EscrowClaim> claimsForTransaction(UUID transactionId);

        Optional<EscrowClaim> claim(UUID claimId);

        Optional<ClaimAttemptResult> claimAttempt(String requestKey);

        List<StockReservation> stockReservations(UUID transactionId);

        long ledgerBalance(
                com.enviouse.futureshops.server.escrow.ledger
                        .LedgerAccountId account
        );

        EscrowCommitResult commit(ServerShopPurchaseCommit commit);
    }

    private record LiveBackend(EscrowRuntimeService runtime)
            implements Backend {
        private LiveBackend {
            Objects.requireNonNull(runtime, "runtime");
        }

        @Override
        public Optional<EscrowTransaction> transaction(UUID transactionId) {
            return runtime.transaction(transactionId);
        }

        @Override
        public Optional<LedgerTransaction> ledgerTransaction(
                UUID transactionId
        ) {
            return runtime.ledgerTransaction(transactionId);
        }

        @Override
        public List<EscrowClaim> claimsForTransaction(UUID transactionId) {
            return runtime.claimsForTransaction(transactionId);
        }

        @Override
        public Optional<EscrowClaim> claim(UUID claimId) {
            return runtime.claim(claimId);
        }

        @Override
        public Optional<ClaimAttemptResult> claimAttempt(String requestKey) {
            return runtime.claimAttempt(requestKey);
        }

        @Override
        public List<StockReservation> stockReservations(
                UUID transactionId
        ) {
            return runtime.stockReservations(transactionId);
        }

        @Override
        public long ledgerBalance(
                com.enviouse.futureshops.server.escrow.ledger
                        .LedgerAccountId account
        ) {
            return runtime.ledgerBalance(account);
        }

        @Override
        public EscrowCommitResult commit(ServerShopPurchaseCommit commit) {
            return runtime.commitServerShopPurchase(commit);
        }
    }

    private static final class ParsedLine {
        private final int lineIndex;
        private final String listingId;
        private final String itemId;
        private final long lineCost;
        private final long stockRevision;
        private final ExactItemClaimPayload[] outputs;
        private int quantity;

        private ParsedLine(
                int lineIndex,
                String listingId,
                String itemId,
                long lineCost,
                long stockRevision,
                int portionCount
        ) {
            this.lineIndex = lineIndex;
            this.listingId = listingId;
            this.itemId = itemId;
            this.lineCost = lineCost;
            this.stockRevision = stockRevision;
            if (portionCount <= 0
                    || portionCount > ExactItemClaimPayload.MAX_PORTIONS) {
                throw new IllegalArgumentException(
                        "Server shop portion count is invalid");
            }
            outputs = new ExactItemClaimPayload[portionCount];
        }

        private void add(
                int portionIndex,
                ExactItemClaimPayload output,
                long assetQuantity
        ) {
            if (portionIndex < 0 || portionIndex >= outputs.length
                    || outputs[portionIndex] != null
                    || output.portionIndex() != portionIndex
                    || output.portionCount() != outputs.length
                    || output.stackCount() != assetQuantity) {
                throw new IllegalArgumentException(
                        "Server shop portion evidence conflicts");
            }
            outputs[portionIndex] = output;
            quantity = Math.addExact(quantity, output.stackCount());
        }

        private ServerShopPurchaseCommit.Line finish() {
            if (Arrays.stream(outputs).anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException(
                        "Server shop portion evidence is incomplete");
            }
            return new ServerShopPurchaseCommit.Line(lineIndex, listingId,
                    itemId, quantity, lineCost, stockRevision,
                    List.of(outputs));
        }
    }
}
