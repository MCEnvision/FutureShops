package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.event.BalanceChangeEvent;
import com.enviouse.futureshops.money.CurrencyManager;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.economy.WalletMutationGuard;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.security.ServerRequestAction;
import com.enviouse.futureshops.server.security.ServerRequestSecurityManager;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PlayerPaymentService {
    public static final long MAX_RETRY_AFTER_MILLIS = 3_600_000_000L;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    private PlayerPaymentService() {
    }

    public static synchronized PlayerPaymentResult pay(
            ServerPlayer payer,
            UUID recipientId,
            UUID requestId,
            long amountMinorUnits
    ) {
        Objects.requireNonNull(payer, "payer");
        PlayerPaymentRequest request = new PlayerPaymentRequest(
                requestId, payer.getUUID(), recipientId,
                amountMinorUnits);
        MinecraftServer server = payer.getServer();
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (server == null || runtime == null) {
            return failure(Status.ESCROW_UNAVAILABLE, request, 0L,
                    Optional.empty());
        }
        Backend backend = new LiveBackend(runtime);
        Optional<PlayerPaymentResult> replay;
        try {
            replay = resolveReplay(request, backend);
        } catch (RuntimeException exception) {
            return failure(Status.ESCROW_UNAVAILABLE, request, 0L,
                    Optional.empty());
        }
        if (replay.isPresent()) {
            return replay.orElseThrow();
        }
        if (!runtime.isReady()) {
            return failure(Status.ESCROW_UNAVAILABLE, request, 0L,
                    Optional.empty());
        }
        ServerRequestSecurityManager.GateDecision gate =
                ServerRequestSecurityManager.tryAcquire(
                        payer, ServerRequestAction.PAY);
        if (!gate.allowed()) {
            return failure(gate.status()
                            == ServerRequestSecurityManager.GateStatus
                            .RATE_LIMITED
                            ? Status.RATE_LIMITED
                            : Status.ESCROW_UNAVAILABLE,
                    request,
                    retryAfterMillis(gate), Optional.empty());
        }
        Optional<WalletMutationGuard.Lease> optionalGuard =
                WalletMutationGuard.tryAcquire(List.of(
                        request.payerId(), request.recipientId()));
        if (optionalGuard.isEmpty()) {
            return failure(Status.REENTRANT_REQUEST, request, 0L,
                    Optional.empty());
        }
        try (WalletMutationGuard.Lease ignoredGuard =
                     optionalGuard.orElseThrow()) {
            PlayerPaymentResult basicFailure = basicFailure(request);
            if (basicFailure != null) {
                return basicFailure;
            }
            Instant now = Instant.now();
            PaymentConfiguration firstConfiguration;
            PaymentPlan firstPlan;
            try (CurrencyManager.ConfigurationReadLease ignored =
                         CurrencyManager.acquireConfigurationReadLease()) {
                EconomyProvider provider = BalanceManager.getProvider();
                BalanceManager.getBalance(request.payerId());
                BalanceManager.getBalance(request.recipientId());
                FreshSettings settings = new FreshSettings(
                        Config.economyMaxBalanceMinorUnits,
                        provider.getCurrencyName(),
                        provider.getDecimalPlaces());
                firstConfiguration = new PaymentConfiguration(
                        ignored.generation(), settings);
                firstPlan = planFresh(request, backend, settings, now);
            } catch (ArithmeticException | IllegalArgumentException exception) {
                return failure(Status.INVALID_AMOUNT, request, 0L,
                        Optional.empty());
            } catch (EscrowRuntimeException | IllegalStateException exception) {
                return resolveAfterFailure(
                        request, backend, Status.RECOVERY_REQUIRED,
                        Optional.of(request.requestId()));
            } catch (RuntimeException exception) {
                return resolveAfterFailure(
                        request, backend, Status.ESCROW_UNAVAILABLE,
                        Optional.empty());
            }
            if (firstPlan.failure().isPresent()) {
                return firstPlan.failure().orElseThrow();
            }
            PlayerPaymentCommit preview = firstPlan.commit()
                    .orElseThrow();
            if (postPreEvents(preview)) {
                return failure(Status.CANCELLED, request, 0L,
                        Optional.empty());
            }
            PlayerPaymentResult result;
            try (CurrencyManager.ConfigurationReadLease ignored =
                         CurrencyManager.acquireConfigurationReadLease()) {
                EconomyProvider provider = BalanceManager.getProvider();
                FreshSettings currentSettings = new FreshSettings(
                        Config.economyMaxBalanceMinorUnits,
                        provider.getCurrencyName(),
                        provider.getDecimalPlaces());
                PaymentConfiguration currentConfiguration =
                        new PaymentConfiguration(
                                ignored.generation(), currentSettings);
                if (!firstConfiguration.sameSemantics(
                        currentConfiguration)) {
                    return failure(Status.CONFIG_CHANGED, request, 0L,
                            Optional.empty());
                }
                PaymentPlan finalPlan = planFresh(
                        request, backend, currentSettings, now);
                if (finalPlan.failure().isPresent()) {
                    return finalPlan.failure().orElseThrow();
                }
                PlayerPaymentCommit commit = finalPlan.commit()
                        .orElseThrow();
                if (!commit.equals(preview)) {
                    return failure(Status.CANCELLED, request, 0L,
                            Optional.empty());
                }
                try {
                    EscrowCommitResult commitResult = backend.commit(commit);
                    result = success(commit, commitResult.replayed());
                } catch (RuntimeException exception) {
                    result = resolveAfterFailure(
                            request, backend, Status.RECOVERY_REQUIRED,
                            Optional.of(request.requestId()));
                }
            } catch (ArithmeticException | IllegalArgumentException exception) {
                result = failure(Status.INVALID_AMOUNT, request, 0L,
                        Optional.empty());
            } catch (RuntimeException exception) {
                result = resolveAfterFailure(
                        request, backend, Status.ESCROW_UNAVAILABLE,
                        Optional.empty());
            }
            if (result.successful()) {
                if (!result.replayed()) {
                    publishBalanceEvents(result);
                }
                reconcileHistory(server, backend, result);
            }
            return result;
        }
    }

    public static synchronized PaymentStatusResult status(
            ServerPlayer requester,
            UUID requestId
    ) {
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(requestId, "requestId");
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (requester.getServer() == null || runtime == null) {
            return paymentStatus(PaymentStatus.ESCROW_UNAVAILABLE,
                    requestId, Optional.empty());
        }
        Backend backend = new LiveBackend(runtime);
        Optional<EscrowTransaction> optionalTransaction;
        Optional<LedgerTransaction> optionalLedger;
        List<EscrowClaim> paymentClaims;
        try {
            optionalTransaction = backend.transaction(requestId);
            optionalLedger = backend.ledgerTransaction(requestId);
            paymentClaims = backend.claimsForTransaction(requestId);
        } catch (RuntimeException exception) {
            return paymentStatus(PaymentStatus.ESCROW_UNAVAILABLE,
                    requestId, Optional.empty());
        }
        if (optionalTransaction.isEmpty()) {
            return paymentStatus(optionalLedger.isPresent()
                            || !paymentClaims.isEmpty()
                            ? PaymentStatus.RECOVERY_REQUIRED
                            : PaymentStatus.NOT_FOUND,
                    requestId, Optional.empty());
        }
        EscrowTransaction transaction = optionalTransaction.orElseThrow();
        if (transaction.operation() != EscrowOperation.PLAYER_PAYMENT
                || !transaction.transactionId().value().equals(requestId)) {
            return paymentStatus(PaymentStatus.REQUEST_CONFLICT,
                    requestId, Optional.empty());
        }
        PlayerPaymentCommit.PlayerPaymentIdentity identity;
        try {
            identity = PlayerPaymentCommit.identityFromTransaction(
                    transaction);
        } catch (RuntimeException exception) {
            return paymentStatus(PaymentStatus.RECOVERY_REQUIRED,
                    requestId, Optional.empty());
        }
        if (!identity.payerId().equals(requester.getUUID())) {
            return paymentStatus(PaymentStatus.REQUEST_CONFLICT,
                    requestId, Optional.empty());
        }
        if (transaction.state() == EscrowState.REFUNDED) {
            return paymentStatus(optionalLedger.isEmpty()
                            && paymentClaims.isEmpty()
                            ? PaymentStatus.CANCELLED
                            : PaymentStatus.RECOVERY_REQUIRED,
                    requestId, Optional.empty());
        }
        if (transaction.state() == EscrowState.RECOVERY_REQUIRED
                || transaction.state() == EscrowState.MANUAL_REVIEW) {
            return paymentStatus(PaymentStatus.RECOVERY_REQUIRED,
                    requestId, Optional.empty());
        }
        if (transaction.state() != EscrowState.COMPLETED) {
            return paymentStatus(optionalLedger.isEmpty()
                            && paymentClaims.isEmpty()
                            ? PaymentStatus.PENDING
                            : PaymentStatus.RECOVERY_REQUIRED,
                    requestId, Optional.empty());
        }
        if (optionalLedger.isEmpty()) {
            return paymentStatus(PaymentStatus.RECOVERY_REQUIRED,
                    requestId, Optional.empty());
        }
        try {
            PlayerPaymentCommit commit = PlayerPaymentCommit.fromEvidence(
                    transaction, optionalLedger.orElseThrow(),
                    paymentClaims);
            return paymentStatus(PaymentStatus.COMPLETED, requestId,
                    Optional.of(success(commit, true)));
        } catch (RuntimeException exception) {
            return paymentStatus(PaymentStatus.RECOVERY_REQUIRED,
                    requestId, Optional.empty());
        }
    }

    static synchronized PlayerPaymentResult execute(
            PlayerPaymentRequest request,
            Backend backend,
            FreshSettings settings,
            Instant now
    ) {
        try {
            return resolveReplay(request, backend).orElseGet(() ->
                    executeFresh(request, backend, settings, now));
        } catch (RuntimeException exception) {
            return failure(Status.ESCROW_UNAVAILABLE, request, 0L,
                    Optional.empty());
        }
    }

    static Optional<PlayerPaymentResult> resolveReplay(
            PlayerPaymentRequest request,
            Backend backend
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(backend, "backend");
        Optional<EscrowTransaction> optionalTransaction =
                backend.transaction(request.requestId());
        Optional<LedgerTransaction> optionalLedger =
                backend.ledgerTransaction(request.requestId());
        List<EscrowClaim> paymentClaims = backend.claimsForTransaction(
                request.requestId());
        if (optionalTransaction.isEmpty()) {
            if (optionalLedger.isPresent() || !paymentClaims.isEmpty()) {
                return Optional.of(failure(
                        Status.RECOVERY_REQUIRED, request, 0L,
                        Optional.of(request.requestId())));
            }
            return Optional.empty();
        }
        EscrowTransaction transaction = optionalTransaction.orElseThrow();
        if (transaction.operation() != EscrowOperation.PLAYER_PAYMENT
                || !transaction.transactionId().value().equals(
                request.requestId())) {
            return Optional.of(failure(
                    Status.REQUEST_CONFLICT, request, 0L,
                    Optional.of(request.requestId())));
        }
        PlayerPaymentCommit.PlayerPaymentIdentity identity;
        try {
            identity = PlayerPaymentCommit.identityFromTransaction(
                    transaction);
        } catch (RuntimeException exception) {
            return Optional.of(failure(
                    Status.RECOVERY_REQUIRED, request, 0L,
                    Optional.of(request.requestId())));
        }
        if (!identity.matches(request.payerId(), request.recipientId(),
                request.amountMinorUnits())) {
            return Optional.of(failure(
                    Status.REQUEST_CONFLICT, request, 0L,
                    Optional.of(request.requestId())));
        }
        if (transaction.state() == EscrowState.REFUNDED) {
            if (optionalLedger.isPresent() || !paymentClaims.isEmpty()) {
                return Optional.of(failure(
                        Status.RECOVERY_REQUIRED, request, 0L,
                        Optional.of(request.requestId())));
            }
            return Optional.of(failure(Status.CANCELLED, request, 0L,
                    Optional.of(request.requestId())));
        }
        if (transaction.state() != EscrowState.COMPLETED
                || optionalLedger.isEmpty()) {
            return Optional.of(failure(
                    Status.RECOVERY_REQUIRED, request, 0L,
                    Optional.of(request.requestId())));
        }
        try {
            PlayerPaymentCommit commit = PlayerPaymentCommit.fromEvidence(
                    transaction, optionalLedger.orElseThrow(),
                    paymentClaims);
            return Optional.of(success(commit, true));
        } catch (RuntimeException exception) {
            return Optional.of(failure(
                    Status.RECOVERY_REQUIRED, request, 0L,
                    Optional.of(request.requestId())));
        }
    }

    private static PlayerPaymentResult executeFresh(
            PlayerPaymentRequest request,
            Backend backend,
            FreshSettings settings,
            Instant now
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(now, "now");
        PlayerPaymentResult basicFailure = basicFailure(request);
        if (basicFailure != null) {
            return basicFailure;
        }
        PaymentPlan plan = planFresh(request, backend, settings, now);
        if (plan.failure().isPresent()) {
            return plan.failure().orElseThrow();
        }
        PlayerPaymentCommit commit = plan.commit().orElseThrow();
        try {
            EscrowCommitResult result = backend.commit(commit);
            return success(commit, result.replayed());
        } catch (RuntimeException exception) {
            return resolveAfterFailure(
                    request, backend, Status.RECOVERY_REQUIRED,
                    Optional.of(request.requestId()));
        }
    }

    private static PlayerPaymentResult resolveAfterFailure(
            PlayerPaymentRequest request,
            Backend backend,
            Status fallbackStatus,
            Optional<UUID> transactionId
    ) {
        try {
            return resolveReplay(request, backend).orElseGet(() ->
                    failure(fallbackStatus, request, 0L,
                            transactionId));
        } catch (RuntimeException exception) {
            return failure(fallbackStatus, request, 0L,
                    transactionId);
        }
    }

    private static PlayerPaymentResult basicFailure(
            PlayerPaymentRequest request
    ) {
        if (ZERO_UUID.equals(request.requestId())
                || ZERO_UUID.equals(request.payerId())
                || ZERO_UUID.equals(request.recipientId())) {
            return failure(Status.REQUEST_CONFLICT, request, 0L,
                    Optional.empty());
        }
        if (request.payerId().equals(request.recipientId())) {
            return failure(Status.SELF_PAYMENT, request, 0L,
                    Optional.empty());
        }
        if (request.amountMinorUnits() <= 0L) {
            return failure(Status.INVALID_AMOUNT, request, 0L,
                    Optional.empty());
        }
        return null;
    }

    private static PaymentPlan planFresh(
            PlayerPaymentRequest request,
            Backend backend,
            FreshSettings settings,
            Instant now
    ) {
        if (settings.walletBalanceLimitMinorUnits() < 0L) {
            return PaymentPlan.failed(failure(
                    Status.INVALID_AMOUNT, request, 0L,
                    Optional.empty()));
        }
        long payerWallet = backend.balance(
                PlayerPaymentCommit.walletAccount(request.payerId()));
        long payerDebt = backend.balance(
                PlayerPaymentCommit.debtAccount(request.payerId()));
        long recipientWallet = backend.balance(
                PlayerPaymentCommit.walletAccount(request.recipientId()));
        long recipientDebt = backend.balance(
                PlayerPaymentCommit.debtAccount(request.recipientId()));
        long recipientReserved = backend.balance(
                PlayerPaymentCommit.reservedAccount(
                        request.recipientId()));
        if (payerWallet < request.amountMinorUnits()
                || payerDebt != 0L) {
            return PaymentPlan.failed(failure(
                    Status.INSUFFICIENT_FUNDS, request, 0L,
                    Optional.empty()));
        }
        PlayerPaymentCommit commit;
        try {
            commit = PlayerPaymentCommit.create(
                    request.requestId(), request.payerId(),
                    request.recipientId(), request.amountMinorUnits(),
                    payerWallet, payerDebt,
                    recipientWallet, recipientDebt, recipientReserved,
                    settings.walletBalanceLimitMinorUnits(),
                    settings.currencyName(), settings.currencyDecimals(),
                    now);
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return PaymentPlan.failed(failure(
                    Status.INVALID_AMOUNT, request, 0L,
                    Optional.empty()));
        }
        return PaymentPlan.ready(commit);
    }

    private static boolean postPreEvents(PlayerPaymentCommit commit) {
        boolean cancelled = postPre(new BalanceChangeEvent.Pre(
                commit.payerId(),
                Math.negateExact(commit.amountMinorUnits()),
                "PAY",
                Math.addExact(commit.payerWalletBeforeMinorUnits(),
                        commit.payerDebtBeforeMinorUnits())));
        if (commit.acceptedMinorUnits() > 0L) {
            cancelled = postPre(new BalanceChangeEvent.Pre(
                    commit.recipientId(),
                    commit.acceptedMinorUnits(),
                    "PAY",
                    commit.recipientBalanceBeforeMinorUnits()))
                    || cancelled;
        }
        return cancelled;
    }

    private static boolean postPre(BalanceChangeEvent.Pre event) {
        try {
            return MinecraftForge.EVENT_BUS.post(event);
        } catch (RuntimeException exception) {
            LOGGER.error("Player payment pre event failed", exception);
            return true;
        }
    }

    private static long retryAfterMillis(
            ServerRequestSecurityManager.GateDecision gate
    ) {
        try {
            return Math.min(MAX_RETRY_AFTER_MILLIS,
                    Math.max(1L, gate.retryAfter().toMillis()));
        } catch (ArithmeticException exception) {
            return MAX_RETRY_AFTER_MILLIS;
        }
    }

    private static PlayerPaymentResult success(
            PlayerPaymentCommit commit,
            boolean replayed
    ) {
        return new PlayerPaymentResult(
                Status.SUCCESS,
                commit.requestId(),
                commit.payerId(),
                commit.recipientId(),
                Optional.of(commit.transactionId()),
                commit.amountMinorUnits(),
                commit.acceptedMinorUnits(),
                commit.overflowClaimMinorUnits(),
                commit.payerBalanceAfterMinorUnits(),
                commit.recipientBalanceAfterMinorUnits(),
                commit.walletBalanceLimitMinorUnits(),
                commit.currencyName(),
                commit.currencyDecimals(),
                replayed,
                0L);
    }

    private static PlayerPaymentResult failure(
            Status status,
            PlayerPaymentRequest request,
            long retryAfterMillis,
            Optional<UUID> transactionId
    ) {
        return new PlayerPaymentResult(
                status,
                request.requestId(),
                request.payerId(),
                request.recipientId(),
                transactionId,
                request.amountMinorUnits(),
                0L,
                0L,
                0L,
                0L,
                0L,
                "",
                0,
                false,
                Math.max(0L, retryAfterMillis));
    }

    private static PaymentStatusResult paymentStatus(
            PaymentStatus status,
            UUID requestId,
            Optional<PlayerPaymentResult> payment
    ) {
        return new PaymentStatusResult(status, requestId, payment);
    }

    private static void publishBalanceEvents(
            PlayerPaymentResult result
    ) {
        try {
            MinecraftForge.EVENT_BUS.post(new BalanceChangeEvent.Post(
                    result.payerId(),
                    Math.negateExact(result.amountMinorUnits()),
                    "PAY",
                    result.payerBalanceMinorUnits()));
        } catch (RuntimeException exception) {
            LOGGER.error("Player payment payer post event failed", exception);
        }
        if (result.acceptedMinorUnits() > 0L) {
            try {
                MinecraftForge.EVENT_BUS.post(new BalanceChangeEvent.Post(
                        result.recipientId(),
                        result.acceptedMinorUnits(),
                        "PAY",
                        result.recipientBalanceMinorUnits()));
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Player payment recipient post event failed",
                        exception);
            }
        }
    }

    private static void reconcileHistory(
            MinecraftServer server,
            Backend backend,
            PlayerPaymentResult result
    ) {
        try {
            PlayerPaymentCommit commit = PlayerPaymentCommit.fromEvidence(
                    backend.transaction(result.transactionId()
                            .orElseThrow()).orElseThrow(),
                    backend.ledgerTransaction(result.transactionId()
                            .orElseThrow()).orElseThrow(),
                    backend.claimsForTransaction(result.transactionId()
                            .orElseThrow()));
            PlayerPaymentHistoryProjector.reconcile(server, commit);
        } catch (RuntimeException exception) {
            LOGGER.error("Player payment history write failed", exception);
        }
    }

    public record PlayerPaymentRequest(
            UUID requestId,
            UUID payerId,
            UUID recipientId,
            long amountMinorUnits
    ) {
        public PlayerPaymentRequest {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(payerId, "payerId");
            Objects.requireNonNull(recipientId, "recipientId");
        }
    }

    record FreshSettings(
            long walletBalanceLimitMinorUnits,
            String currencyName,
            int currencyDecimals
    ) {
        FreshSettings {
            currencyName = PlayerPaymentCommit.normalizeCurrencyName(
                    currencyName);
            if (currencyDecimals < 0 || currencyDecimals > 6) {
                throw new IllegalArgumentException(
                        "Player payment currency decimals are invalid");
            }
        }
    }

    record PaymentConfiguration(
            long generation,
            FreshSettings settings
    ) {
        PaymentConfiguration {
            if (generation < 0L) {
                throw new IllegalArgumentException(
                        "Player payment configuration generation is invalid");
            }
            Objects.requireNonNull(settings, "settings");
        }

        boolean sameSemantics(PaymentConfiguration other) {
            Objects.requireNonNull(other, "other");
            return settings.equals(other.settings);
        }
    }

    private record PaymentPlan(
            Optional<PlayerPaymentCommit> commit,
            Optional<PlayerPaymentResult> failure
    ) {
        private PaymentPlan {
            Objects.requireNonNull(commit, "commit");
            Objects.requireNonNull(failure, "failure");
            if (commit.isPresent() == failure.isPresent()) {
                throw new IllegalArgumentException(
                        "Player payment plan is invalid");
            }
        }

        private static PaymentPlan ready(PlayerPaymentCommit commit) {
            return new PaymentPlan(Optional.of(
                    Objects.requireNonNull(commit, "commit")),
                    Optional.empty());
        }

        private static PaymentPlan failed(PlayerPaymentResult failure) {
            return new PaymentPlan(Optional.empty(), Optional.of(
                    Objects.requireNonNull(failure, "failure")));
        }
    }

    interface Backend {
        Optional<EscrowTransaction> transaction(UUID transactionId);

        Optional<LedgerTransaction> ledgerTransaction(UUID transactionId);

        List<EscrowClaim> claimsForTransaction(UUID transactionId);

        long balance(LedgerAccountId account);

        EscrowCommitResult commit(PlayerPaymentCommit commit);
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
        public long balance(LedgerAccountId account) {
            return runtime.ledgerBalance(account);
        }

        @Override
        public EscrowCommitResult commit(PlayerPaymentCommit commit) {
            return runtime.commitPlayerPayment(commit);
        }
    }

    public record PlayerPaymentResult(
            Status status,
            UUID requestId,
            UUID payerId,
            UUID recipientId,
            Optional<UUID> transactionId,
            long amountMinorUnits,
            long acceptedMinorUnits,
            long overflowClaimMinorUnits,
            long payerBalanceMinorUnits,
            long recipientBalanceMinorUnits,
            long walletBalanceLimitMinorUnits,
            String currencyName,
            int currencyDecimals,
            boolean replayed,
            long retryAfterMillis
    ) {
        public PlayerPaymentResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(payerId, "payerId");
            Objects.requireNonNull(recipientId, "recipientId");
            Objects.requireNonNull(transactionId, "transactionId");
            Objects.requireNonNull(currencyName, "currencyName");
            boolean success = status == Status.SUCCESS;
            boolean conserved;
            try {
                conserved = Math.addExact(acceptedMinorUnits,
                        overflowClaimMinorUnits) == amountMinorUnits;
            } catch (ArithmeticException exception) {
                conserved = false;
            }
            if (acceptedMinorUnits < 0L
                    || overflowClaimMinorUnits < 0L
                    || retryAfterMillis < 0L
                    || retryAfterMillis > MAX_RETRY_AFTER_MILLIS
                    || replayed && !success
                    || success && transactionId.isEmpty()
                    || success && !transactionId.orElseThrow()
                    .equals(requestId)
                    || !success && transactionId.isPresent()
                    && !transactionId.orElseThrow().equals(requestId)
                    || success && (amountMinorUnits <= 0L
                    || !conserved
                    || ZERO_UUID.equals(requestId)
                    || ZERO_UUID.equals(payerId)
                    || ZERO_UUID.equals(recipientId)
                    || payerId.equals(recipientId)
                    || payerBalanceMinorUnits < 0L
                    || walletBalanceLimitMinorUnits < 0L
                    || currencyName.isBlank()
                    || !currencyName.equals(currencyName.trim())
                    || currencyDecimals < 0
                    || currencyDecimals > 6
                    || retryAfterMillis != 0L)
                    || !success && (acceptedMinorUnits != 0L
                    || overflowClaimMinorUnits != 0L
                    || payerBalanceMinorUnits != 0L
                    || recipientBalanceMinorUnits != 0L
                    || walletBalanceLimitMinorUnits != 0L
                    || !currencyName.isEmpty()
                    || currencyDecimals != 0
                    || replayed)
                    || (status == Status.RATE_LIMITED)
                    != (retryAfterMillis > 0L)) {
                throw new IllegalArgumentException(
                        "Player payment result is invalid");
            }
        }

        public boolean successful() {
            return status == Status.SUCCESS;
        }
    }

    public enum Status {
        SUCCESS,
        INVALID_AMOUNT,
        SELF_PAYMENT,
        INSUFFICIENT_FUNDS,
        RATE_LIMITED,
        REENTRANT_REQUEST,
        REQUEST_CONFLICT,
        RECOVERY_REQUIRED,
        CONFIG_CHANGED,
        CANCELLED,
        ESCROW_UNAVAILABLE
    }

    public record PaymentStatusResult(
            PaymentStatus status,
            UUID requestId,
            Optional<PlayerPaymentResult> payment
    ) {
        public PaymentStatusResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(payment, "payment");
            if ((status == PaymentStatus.COMPLETED)
                    != payment.isPresent()
                    || payment.isPresent()
                    && (!payment.orElseThrow().successful()
                    || !payment.orElseThrow().replayed()
                    || !payment.orElseThrow().requestId()
                    .equals(requestId))) {
                throw new IllegalArgumentException(
                        "Player payment status result is invalid");
            }
        }
    }

    public enum PaymentStatus {
        COMPLETED,
        PENDING,
        CANCELLED,
        NOT_FOUND,
        REQUEST_CONFLICT,
        RECOVERY_REQUIRED,
        ESCROW_UNAVAILABLE
    }
}
