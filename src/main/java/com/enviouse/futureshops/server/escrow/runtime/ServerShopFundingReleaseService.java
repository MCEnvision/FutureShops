package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimAttemptResult;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipantRole;
import com.enviouse.futureshops.server.escrow.model.EscrowParty;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.stock.StockReservation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;

public final class ServerShopFundingReleaseService {
    private ServerShopFundingReleaseService() {
    }

    public static Result release(
            UUID playerId,
            ServerShopPurchaseCommit.PhysicalFunding funding
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(funding, "funding");
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (runtime == null) {
            return Result.failure(Status.ESCROW_UNAVAILABLE, funding);
        }
        return release(playerId, funding, new LiveBackend(runtime));
    }

    static Result release(
            EscrowRuntimeService runtime,
            UUID playerId,
            ServerShopPurchaseCommit.PhysicalFunding funding
    ) {
        return release(playerId, funding, new LiveBackend(
                Objects.requireNonNull(runtime, "runtime")));
    }

    public static Optional<Result> resolve(
            UUID playerId,
            ServerShopPurchaseCommit.PhysicalFunding funding
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(funding, "funding");
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (runtime == null) {
            return Optional.empty();
        }
        try {
            return resolve(playerId, funding, new LiveBackend(runtime));
        } catch (RuntimeException exception) {
            return Optional.of(Result.failure(Status.RECOVERY_REQUIRED,
                    funding));
        }
    }

    public static Optional<Result> resolvePurchase(
            UUID playerId,
            UUID purchaseRequestId
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(purchaseRequestId, "purchaseRequestId");
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (runtime == null) {
            return Optional.empty();
        }
        try {
            UUID fundingTransactionId =
                    EscrowCashDepositService.transactionIdForRequest(
                            playerId,
                            ServerShopFundingRelease.fundingRequestId(
                                    purchaseRequestId));
            Optional<EscrowTransaction> deposit = runtime.transaction(
                    fundingTransactionId);
            if (deposit.isEmpty()
                    || EscrowCashDepositService.serverShopPurchaseBinding(
                    deposit.orElseThrow()).filter(
                    purchaseRequestId::equals).isEmpty()) {
                return Optional.empty();
            }
            List<EscrowClaim> fundingClaims = runtime
                    .claimsForTransaction(fundingTransactionId).stream()
                    .filter(claim -> claim.ownerId().equals(playerId))
                    .filter(claim -> claim.kind()
                            == ClaimKind.INTERNAL_ESCROW_MONEY)
                    .toList();
            if (fundingClaims.size() != 1) {
                return Optional.empty();
            }
            EscrowClaim claim = fundingClaims.get(0);
            ServerShopPurchaseCommit.PhysicalFunding funding =
                    new ServerShopPurchaseCommit.PhysicalFunding(
                            purchaseRequestId, fundingTransactionId,
                            claim.claimId(), claim.originalUnits());
            return resolve(playerId, funding,
                    new LiveBackend(runtime));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    static Result release(
            UUID playerId,
            ServerShopPurchaseCommit.PhysicalFunding funding,
            Backend backend
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(funding, "funding");
        Objects.requireNonNull(backend, "backend");
        try {
            Optional<Result> existing = resolve(playerId, funding, backend);
            if (existing.isPresent()) {
                return existing.orElseThrow();
            }
            EscrowClaim claim = backend.claim(funding.claimId())
                    .orElse(null);
            EscrowTransaction deposit = backend.transaction(
                    funding.transactionId()).orElse(null);
            if (!freshFundingMatches(playerId, funding, claim, deposit,
                    backend)) {
                return Result.failure(Status.REQUEST_CONFLICT, funding);
            }
            Instant now = Instant.now();
            if (now.isBefore(claim.updatedAt())) {
                now = claim.updatedAt();
            }
            ServerShopFundingRelease release =
                    ServerShopFundingRelease.create(playerId, funding, now);
            EscrowCommitResult commit = backend.commit(release);
            Optional<Result> resolved = resolve(playerId, funding, backend);
            if (resolved.isEmpty()
                    || resolved.orElseThrow().status() != Status.RELEASED) {
                return Result.failure(Status.RECOVERY_REQUIRED, funding);
            }
            return Result.released(release, commit.replayed());
        } catch (RuntimeException exception) {
            try {
                return resolve(playerId, funding, backend)
                        .orElseGet(() -> Result.failure(
                                Status.RECOVERY_REQUIRED, funding));
            } catch (RuntimeException replayFailure) {
                return Result.failure(Status.RECOVERY_REQUIRED, funding);
            }
        }
    }

    static Optional<Result> resolve(
            UUID playerId,
            ServerShopPurchaseCommit.PhysicalFunding funding,
            Backend backend
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(funding, "funding");
        Objects.requireNonNull(backend, "backend");
        Optional<EscrowTransaction> purchase = backend.transaction(
                funding.purchaseRequestId());
        boolean purchaseLedger = backend.ledgerTransaction(
                funding.purchaseRequestId()).isPresent();
        boolean purchaseClaims = !backend.claimsForTransaction(
                funding.purchaseRequestId()).isEmpty();
        boolean purchaseStock = !backend.stockReservations(
                funding.purchaseRequestId()).isEmpty();
        if (purchase.isPresent() || purchaseLedger || purchaseClaims
                || purchaseStock) {
            Status status = purchase.isPresent()
                    ? Status.PURCHASE_COMMITTED
                    : Status.RECOVERY_REQUIRED;
            return Optional.of(Result.failure(status, funding));
        }
        Optional<EscrowTransaction> deposit = backend.transaction(
                funding.transactionId());
        if (deposit.isEmpty()
                || EscrowCashDepositService.serverShopPurchaseBinding(
                deposit.orElseThrow()).filter(
                funding.purchaseRequestId()::equals).isEmpty()
                || !depositHasOwner(deposit.orElseThrow(), playerId)) {
            return Optional.of(Result.failure(Status.REQUEST_CONFLICT,
                    funding));
        }

        UUID releaseId = ServerShopFundingRelease.releaseId(
                funding.purchaseRequestId(), funding.claimId());
        UUID refundClaimId = ServerShopFundingRelease.refundClaimId(
                funding.purchaseRequestId(), funding.claimId());
        Optional<EscrowTransaction> transaction = backend.transaction(
                releaseId);
        Optional<LedgerTransaction> ledger = backend.ledgerTransaction(
                releaseId);
        Optional<EscrowClaim> refund = backend.claim(refundClaimId);
        Optional<ClaimAttemptResult> attempt = backend.claimAttempt(
                ServerShopFundingRelease.deliveryKey(
                        funding.purchaseRequestId(), funding.claimId()));
        EscrowClaim fundingClaim = backend.claim(funding.claimId())
                .orElse(null);
        boolean releaseEvidence = transaction.isPresent()
                || ledger.isPresent() || refund.isPresent()
                || attempt.isPresent();
        if (!releaseEvidence) {
            if (fundingClaim != null
                    && fundingClaim.status() == ClaimStatus.COMPLETED) {
                return Optional.of(Result.failure(
                        Status.RECOVERY_REQUIRED, funding));
            }
            return Optional.empty();
        }
        if (transaction.isEmpty()) {
            return Optional.of(Result.failure(Status.RECOVERY_REQUIRED,
                    funding));
        }
        try {
            ServerShopFundingRelease expected =
                    ServerShopFundingRelease.create(playerId, funding,
                            transaction.orElseThrow().timestamps()
                                    .createdAt());
            if (!expected.completedTransaction().equals(
                    transaction.orElseThrow())
                    || ledger.filter(expected.ledgerTransaction()::equals)
                    .isEmpty()
                    || attempt.filter(value -> deliveryMatches(
                    expected, value)).isEmpty()
                    || !fundingClaimCompleted(expected, fundingClaim)
                    || refund.filter(value -> refundMatches(
                    expected.refundClaim(), value)).isEmpty()
                    || backend.claimsForTransaction(releaseId).stream()
                    .anyMatch(value -> !value.claimId().equals(
                            refundClaimId))) {
                return Optional.of(Result.failure(
                        Status.RECOVERY_REQUIRED, funding));
            }
            return Optional.of(Result.released(expected, true));
        } catch (RuntimeException exception) {
            return Optional.of(Result.failure(Status.RECOVERY_REQUIRED,
                    funding));
        }
    }

    static Optional<ServerShopPurchaseCommit.PhysicalFunding>
    startupCandidate(
            EscrowTransaction deposit,
            List<EscrowClaim> transactionClaims,
            boolean purchaseEvidencePresent,
            BiPredicate<UUID, ServerShopPurchaseCommit.PhysicalFunding>
                    completedEvidence
    ) {
        Objects.requireNonNull(deposit, "deposit");
        List<EscrowClaim> claims = List.copyOf(Objects.requireNonNull(
                transactionClaims, "transactionClaims"));
        Objects.requireNonNull(completedEvidence, "completedEvidence");
        Optional<UUID> binding = EscrowCashDepositService
                .serverShopPurchaseBinding(deposit);
        if (binding.isEmpty()) {
            return Optional.empty();
        }
        List<EscrowClaim> fundingClaims = claims.stream()
                .filter(claim -> claim.kind()
                        == ClaimKind.INTERNAL_ESCROW_MONEY)
                .toList();
        if (fundingClaims.size() != 1) {
            throw new EscrowRuntimeException(
                    "Bound server shop funding is not safely releasable");
        }
        EscrowClaim claim = fundingClaims.get(0);
        if (!claim.transactionId().equals(
                deposit.transactionId().value())
                || !depositHasOwner(deposit, claim.ownerId())
                || claim.payload().length != 0) {
            throw new EscrowRuntimeException(
                    "Bound server shop funding claim conflicts");
        }
        ServerShopPurchaseCommit.PhysicalFunding funding =
                new ServerShopPurchaseCommit.PhysicalFunding(
                binding.orElseThrow(), deposit.transactionId().value(),
                claim.claimId(), claim.originalUnits());
        if (claim.status() == ClaimStatus.PENDING
                && claim.remainingUnits() == claim.originalUnits()) {
            if (purchaseEvidencePresent) {
                throw new EscrowRuntimeException(
                        "Pending server shop funding has purchase evidence");
            }
            return Optional.of(funding);
        }
        if (claim.status() == ClaimStatus.COMPLETED
                && claim.remainingUnits() == 0L
                && completedEvidence.test(claim.ownerId(), funding)) {
            return Optional.empty();
        }
        throw new EscrowRuntimeException(
                "Bound server shop funding status is unexplained");
    }

    static boolean startupCompletionExplained(
            EscrowRuntimeService runtime,
            UUID playerId,
            ServerShopPurchaseCommit.PhysicalFunding funding
    ) {
        Backend backend = new LiveBackend(Objects.requireNonNull(
                runtime, "runtime"));
        if (purchaseExplainsFunding(playerId, funding, backend)) {
            return true;
        }
        return resolve(playerId, funding, backend)
                .filter(result -> result.status() == Status.RELEASED)
                .isPresent();
    }

    private static boolean purchaseExplainsFunding(
            UUID playerId,
            ServerShopPurchaseCommit.PhysicalFunding funding,
            Backend backend
    ) {
        EscrowTransaction purchase = backend.transaction(
                funding.purchaseRequestId()).orElse(null);
        LedgerTransaction ledger = backend.ledgerTransaction(
                funding.purchaseRequestId()).orElse(null);
        EscrowClaim claim = backend.claim(funding.claimId()).orElse(null);
        ClaimAttemptResult attempt = backend.claimAttempt(
                ServerShopPurchaseCommit.physicalFundingDeliveryKey(
                        funding.purchaseRequestId(), funding.claimId()))
                .orElse(null);
        if (purchase == null || ledger == null || claim == null
                || attempt == null || purchase.state() != EscrowState.COMPLETED
                || purchase.operation() != EscrowOperation.SERVER_SHOP_BUY
                && purchase.operation() != EscrowOperation.SERVER_SHOP_CART
                || ServerShopPurchaseCommit.physicalFunding(purchase)
                .filter(funding::equals).isEmpty()
                || !claim.transactionId().equals(funding.transactionId())
                || !claim.ownerId().equals(playerId)
                || claim.kind() != ClaimKind.INTERNAL_ESCROW_MONEY
                || claim.status() != ClaimStatus.COMPLETED
                || claim.originalUnits() != funding.amountMinorUnits()
                || claim.remainingUnits() != 0L
                || claim.payload().length != 0
                || !attempt.claimId().equals(funding.claimId())
                || !attempt.requestKey().equals(
                ServerShopPurchaseCommit.physicalFundingDeliveryKey(
                        funding.purchaseRequestId(), funding.claimId()))
                || attempt.deliveredUnits()
                != funding.amountMinorUnits()
                || attempt.remainingUnits() != 0L
                || attempt.status() != ClaimStatus.COMPLETED
                || !attempt.deliveredAt().equals(
                purchase.timestamps().createdAt())
                || !ledger.transactionId().equals(
                funding.purchaseRequestId())
                || !ledger.idempotencyKey().equals(
                "server.shop.purchase." + funding.purchaseRequestId())
                || !ledger.reason().equals("Server shop purchase")
                || ledger.legs().size() != 2) {
            return false;
        }
        boolean claimDebit = ledger.legs().stream().anyMatch(leg ->
                leg.account().equals(ServerShopPurchaseCommit.claimAccount(
                        funding.claimId()))
                        && leg.deltaMinor()
                        == Math.negateExact(funding.amountMinorUnits()));
        boolean shopCredit = ledger.legs().stream().anyMatch(leg ->
                leg.account().type() == LedgerAccountType.SERVER_SHOP_SINK
                        && leg.deltaMinor() == funding.amountMinorUnits());
        return claimDebit && shopCredit;
    }

    private static boolean freshFundingMatches(
            UUID playerId,
            ServerShopPurchaseCommit.PhysicalFunding funding,
            EscrowClaim claim,
            EscrowTransaction deposit,
            Backend backend
    ) {
        return claim != null && deposit != null
                && EscrowCashDepositService.serverShopPurchaseBinding(
                deposit).filter(funding.purchaseRequestId()::equals)
                .isPresent()
                && depositHasOwner(deposit, playerId)
                && claim.claimId().equals(funding.claimId())
                && claim.transactionId().equals(funding.transactionId())
                && claim.ownerId().equals(playerId)
                && claim.kind() == ClaimKind.INTERNAL_ESCROW_MONEY
                && claim.status() == ClaimStatus.PENDING
                && claim.originalUnits() == funding.amountMinorUnits()
                && claim.remainingUnits() == funding.amountMinorUnits()
                && claim.payload().length == 0
                && backend.ledgerBalance(
                ServerShopPurchaseCommit.claimAccount(funding.claimId()))
                == funding.amountMinorUnits()
                && backend.ledgerBalance(
                ServerShopPurchaseCommit.claimAccount(
                        ServerShopFundingRelease.refundClaimId(
                                funding.purchaseRequestId(),
                                funding.claimId()))) == 0L;
    }

    private static boolean fundingClaimCompleted(
            ServerShopFundingRelease release,
            EscrowClaim claim
    ) {
        return claim != null
                && claim.claimId().equals(release.fundingClaimId())
                && claim.transactionId().equals(
                release.fundingTransactionId())
                && claim.ownerId().equals(release.playerId())
                && claim.kind() == ClaimKind.INTERNAL_ESCROW_MONEY
                && claim.status() == ClaimStatus.COMPLETED
                && claim.originalUnits() == release.amountMinorUnits()
                && claim.remainingUnits() == 0L
                && claim.payload().length == 0;
    }

    static boolean depositHasOwner(
            EscrowTransaction deposit,
            UUID playerId
    ) {
        EscrowParty player = EscrowParty.player(playerId);
        return deposit.participants().stream().anyMatch(participant ->
                participant.party().equals(player)
                        && participant.hasRole(
                        EscrowParticipantRole.INITIATOR));
    }

    private static boolean deliveryMatches(
            ServerShopFundingRelease release,
            ClaimAttemptResult attempt
    ) {
        return attempt.claimId().equals(release.fundingClaimId())
                && attempt.requestKey().equals(
                release.fundingClaimDelivery().requestKey())
                && attempt.deliveredUnits() == release.amountMinorUnits()
                && attempt.remainingUnits() == 0L
                && attempt.status() == ClaimStatus.COMPLETED
                && attempt.deliveredAt().equals(release.releasedAt());
    }

    private static boolean refundMatches(
            EscrowClaim expected,
            EscrowClaim actual
    ) {
        return actual.claimId().equals(expected.claimId())
                && actual.transactionId().equals(expected.transactionId())
                && actual.ownerId().equals(expected.ownerId())
                && actual.sourceKey().equals(expected.sourceKey())
                && actual.kind() == ClaimKind.MONEY
                && actual.originalUnits() == expected.originalUnits()
                && actual.remainingUnits() >= 0L
                && actual.remainingUnits() <= actual.originalUnits()
                && actual.payload().length == 0
                && actual.label().equals(expected.label())
                && actual.createdAt().equals(expected.createdAt());
    }

    public enum Status {
        RELEASED,
        PURCHASE_COMMITTED,
        REQUEST_CONFLICT,
        ESCROW_UNAVAILABLE,
        RECOVERY_REQUIRED
    }

    public record Result(
            Status status,
            UUID purchaseRequestId,
            UUID releaseId,
            Optional<UUID> refundClaimId,
            boolean replayed
    ) {
        public Result {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(purchaseRequestId,
                    "purchaseRequestId");
            Objects.requireNonNull(releaseId, "releaseId");
            refundClaimId = Objects.requireNonNull(
                    refundClaimId, "refundClaimId");
            if ((status == Status.RELEASED)
                    != refundClaimId.isPresent()
                    || replayed && status != Status.RELEASED) {
                throw new IllegalArgumentException(
                        "Server shop funding release result is invalid");
            }
        }

        static Result released(
                ServerShopFundingRelease release,
                boolean replayed
        ) {
            return new Result(Status.RELEASED,
                    release.purchaseRequestId(), release.releaseId(),
                    Optional.of(release.refundClaim().claimId()), replayed);
        }

        static Result failure(
                Status status,
                ServerShopPurchaseCommit.PhysicalFunding funding
        ) {
            if (status == Status.RELEASED) {
                throw new IllegalArgumentException(
                        "Released funding requires release evidence");
            }
            return new Result(status, funding.purchaseRequestId(),
                    ServerShopFundingRelease.releaseId(
                            funding.purchaseRequestId(),
                            funding.claimId()), Optional.empty(), false);
        }

        public boolean successful() {
            return status == Status.RELEASED;
        }
    }

    interface Backend {
        Optional<EscrowTransaction> transaction(UUID transactionId);

        Optional<LedgerTransaction> ledgerTransaction(UUID transactionId);

        Optional<EscrowClaim> claim(UUID claimId);

        List<EscrowClaim> claimsForTransaction(UUID transactionId);

        Optional<ClaimAttemptResult> claimAttempt(String requestKey);

        List<StockReservation> stockReservations(UUID transactionId);

        long ledgerBalance(LedgerAccountId account);

        EscrowCommitResult commit(ServerShopFundingRelease release);
    }

    private record LiveBackend(EscrowRuntimeService runtime)
            implements Backend {
        private LiveBackend {
            Objects.requireNonNull(runtime, "runtime");
        }

        @Override
        public Optional<EscrowTransaction> transaction(
                UUID transactionId
        ) {
            return runtime.transaction(transactionId);
        }

        @Override
        public Optional<LedgerTransaction> ledgerTransaction(
                UUID transactionId
        ) {
            return runtime.ledgerTransaction(transactionId);
        }

        @Override
        public Optional<EscrowClaim> claim(UUID claimId) {
            return runtime.claim(claimId);
        }

        @Override
        public List<EscrowClaim> claimsForTransaction(
                UUID transactionId
        ) {
            return runtime.claimsForTransaction(transactionId);
        }

        @Override
        public Optional<ClaimAttemptResult> claimAttempt(
                String requestKey
        ) {
            return runtime.claimAttempt(requestKey);
        }

        @Override
        public List<StockReservation> stockReservations(
                UUID transactionId
        ) {
            return runtime.stockReservations(transactionId);
        }

        @Override
        public long ledgerBalance(LedgerAccountId account) {
            return runtime.ledgerBalance(account);
        }

        @Override
        public EscrowCommitResult commit(
                ServerShopFundingRelease release
        ) {
            return runtime.commitServerShopFundingRelease(release);
        }
    }
}
