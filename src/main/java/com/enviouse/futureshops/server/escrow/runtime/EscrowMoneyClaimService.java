package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.event.BalanceChangeEvent;
import com.enviouse.futureshops.money.CurrencyManager;
import com.enviouse.futureshops.server.economy.WalletMutationGuard;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimAttemptResult;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class EscrowMoneyClaimService {
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    private EscrowMoneyClaimService() {
    }

    public static synchronized CollectionResult collect(
            ServerPlayer player,
            UUID claimId,
            UUID requestId
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(requestId, "requestId");
        if (ZERO_UUID.equals(claimId) || ZERO_UUID.equals(requestId)) {
            throw new IllegalArgumentException(
                    "Money claim collection identity cannot be zero");
        }
        MinecraftServer server = player.getServer();
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        if (server == null || runtime == null || !runtime.isReady()) {
            return result(Status.ESCROW_UNAVAILABLE, requestId, claimId,
                    0L, 0L, false);
        }
        Optional<CollectionResult> replay = replay(
                runtime, player.getUUID(), claimId, requestId);
        if (replay.isPresent()) {
            return replay.orElseThrow();
        }
        EscrowClaim claim = ClaimSavedData.get(server).getClaim(claimId);
        if (!publiclyCollectible(claim, player.getUUID())) {
            return result(Status.NOT_FOUND, requestId, claimId,
                    0L, balance(runtime, player.getUUID()), false);
        }
        if (claim.status() == ClaimStatus.COMPLETED) {
            return result(Status.ALREADY_COLLECTED, requestId, claimId,
                    0L, balance(runtime, player.getUUID()), false);
        }
        if (claim.status() == ClaimStatus.QUARANTINED) {
            return result(Status.RECOVERY_REQUIRED, requestId, claimId,
                    0L, balance(runtime, player.getUUID()), false);
        }
        Optional<WalletMutationGuard.Lease> optionalGuard =
                WalletMutationGuard.tryAcquire(List.of(player.getUUID()));
        if (optionalGuard.isEmpty()) {
            return result(Status.REENTRANT_REQUEST, requestId, claimId,
                    0L, balance(runtime, player.getUUID()), false);
        }
        try (WalletMutationGuard.Lease ignoredGuard =
                     optionalGuard.orElseThrow()) {
            Snapshot before;
            ClaimConfiguration firstConfiguration;
            try (CurrencyManager.ConfigurationReadLease ignored =
                         CurrencyManager.acquireConfigurationReadLease()) {
                firstConfiguration = new ClaimConfiguration(
                        ignored.generation(),
                        Config.economyMaxBalanceMinorUnits);
                before = snapshot(runtime, player.getUUID(),
                        firstConfiguration.limit());
            }
            long capacity = before.capacity();
            if (capacity <= 0L) {
                return result(Status.WALLET_FULL, requestId, claimId,
                        0L, before.balance(), false);
            }
            long units = Math.min(claim.remainingUnits(), capacity);
            if (postPre(player.getUUID(), units, before.balance())) {
                return result(Status.CANCELLED, requestId, claimId,
                        0L, before.balance(), false);
            }
            Instant deliveredAt = Instant.now();
            try (CurrencyManager.ConfigurationReadLease ignored =
                         CurrencyManager.acquireConfigurationReadLease()) {
                ClaimConfiguration currentConfiguration =
                        new ClaimConfiguration(ignored.generation(),
                                Config.economyMaxBalanceMinorUnits);
                if (!firstConfiguration.sameSemantics(
                        currentConfiguration)
                        || !snapshot(runtime, player.getUUID(),
                        currentConfiguration.limit())
                        .equals(before)) {
                    return result(Status.CONFIG_CHANGED, requestId,
                            claimId, 0L, before.balance(), false);
                }
                MoneyClaimSettlement settlement =
                        MoneyClaimSettlement.create(
                                requestId, player.getUUID(), claimId,
                                before.wallet(), before.debt(),
                                before.reserved(), claim.remainingUnits(),
                                currentConfiguration.limit(),
                                currentConfiguration.generation(),
                                deliveredAt);
                EscrowCommitResult commit = runtime.settleMoneyClaim(
                        settlement);
                long after = Math.addExact(before.balance(), units);
                if (!commit.replayed()) {
                    postAfter(player.getUUID(), units, after);
                }
                return result(Status.SUCCESS, requestId, claimId,
                        units, after, commit.replayed());
            } catch (RuntimeException exception) {
                return replay(runtime, player.getUUID(), claimId,
                        requestId).orElseGet(() -> result(
                        Status.RECOVERY_REQUIRED, requestId, claimId,
                        0L, before.balance(), false));
            }
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return result(Status.RECOVERY_REQUIRED, requestId, claimId,
                    0L, balance(runtime, player.getUUID()), false);
        }
    }

    public static List<EscrowClaim> pending(ServerPlayer player, int limit) {
        Objects.requireNonNull(player, "player");
        if (limit <= 0 || limit > 1024 || player.getServer() == null) {
            return List.of();
        }
        return ClaimSavedData.get(player.getServer())
                .pendingFor(player.getUUID(), limit).stream()
                .filter(EscrowMoneyClaimService::isMonetaryClaim)
                .toList();
    }

    static boolean publiclyCollectible(
            EscrowClaim claim,
            UUID ownerId
    ) {
        return claim != null
                && claim.ownerId().equals(
                Objects.requireNonNull(ownerId, "ownerId"))
                && isMonetaryClaim(claim);
    }

    static boolean isMonetaryClaim(EscrowClaim claim) {
        return claim != null
                && claim.kind().publiclyVisible()
                && (claim.kind() == ClaimKind.MONEY
                || claim.kind() == ClaimKind.REFUND
                && claim.payload().length == 0);
    }

    private static Optional<CollectionResult> replay(
            EscrowRuntimeService runtime,
            UUID playerId,
            UUID claimId,
            UUID requestId
    ) {
        return resolveReplay(new LiveEvidence(runtime), playerId,
                claimId, requestId);
    }

    static Optional<CollectionResult> resolveReplay(
            EvidenceBackend backend,
            UUID playerId,
            UUID claimId,
            UUID requestId
    ) {
        Objects.requireNonNull(backend, "backend");
        Optional<LedgerTransaction> receipt =
                backend.ledgerTransaction(requestId);
        String requestKey = MoneyClaimSettlement.requestKey(
                requestId, claimId);
        Optional<ClaimAttemptResult> attempt =
                backend.claimAttempt(requestKey);
        if (receipt.isPresent()) {
            LedgerTransaction existing = receipt.orElseThrow();
            if (!existing.transactionId().equals(requestId)
                    || !existing.reason().equals(
                    MoneyClaimSettlement.LEDGER_REASON)
                    || !existing.idempotencyKey().equals(requestKey)) {
                return Optional.of(result(Status.REQUEST_CONFLICT,
                        requestId, claimId, 0L,
                        balance(backend, playerId), false));
            }
        }
        if (receipt.isEmpty() && attempt.isEmpty()) {
            return Optional.empty();
        }
        if (receipt.isEmpty() || attempt.isEmpty()
                || backend.claim(claimId).isEmpty()) {
            return Optional.of(result(Status.RECOVERY_REQUIRED,
                    requestId, claimId, 0L,
                    balance(backend, playerId), false));
        }
        LedgerTransaction ledger = receipt.orElseThrow();
        ClaimAttemptResult claimAttempt = attempt.orElseThrow();
        EscrowClaim currentClaim = backend.claim(claimId).orElseThrow();
        long claimDebit = 0L;
        long walletCredit = 0L;
        long debtCredit = 0L;
        boolean invalid = !ledger.reason().equals(
                MoneyClaimSettlement.LEDGER_REASON)
                || !ledger.transactionId().equals(requestId)
                || !ledger.idempotencyKey().equals(requestKey)
                || !currentClaim.ownerId().equals(playerId)
                || !isMonetaryClaim(currentClaim);
        java.util.Set<LedgerAccountId> accounts =
                new java.util.HashSet<>();
        for (LedgerLeg leg : ledger.legs()) {
            if (!accounts.add(leg.account())) {
                invalid = true;
                continue;
            }
            if (leg.account().type() == LedgerAccountType.PLAYER_CLAIM
                    && leg.account().ownerKey().equals(
                    claimId.toString())) {
                claimDebit = leg.deltaMinor();
            } else if (leg.account().type()
                    == LedgerAccountType.PLAYER_WALLET
                    && leg.account().ownerKey().equals(
                    playerId.toString())) {
                walletCredit = leg.deltaMinor();
            } else if (leg.account().type()
                    == LedgerAccountType.PLAYER_DEBT
                    && leg.account().ownerKey().equals(
                    playerId.toString())) {
                debtCredit = leg.deltaMinor();
            } else {
                invalid = true;
            }
        }
        long totalCredit = Math.addExact(walletCredit, debtCredit);
        if (invalid || totalCredit <= 0L
                || walletCredit < 0L || debtCredit < 0L
                || claimDebit != Math.negateExact(totalCredit)
                || !claimAttempt.claimId().equals(claimId)
                || !claimAttempt.requestKey().equals(requestKey)
                || claimAttempt.deliveredUnits() != totalCredit
                || claimAttempt.remainingUnits()
                < currentClaim.remainingUnits()) {
            return Optional.of(result(Status.REQUEST_CONFLICT,
                    requestId, claimId, 0L,
                    balance(backend, playerId), false));
        }
        return Optional.of(result(Status.SUCCESS, requestId, claimId,
                totalCredit, balance(backend, playerId), true));
    }

    private static Snapshot snapshot(
            EscrowRuntimeService runtime,
            UUID playerId,
            long limit
    ) {
        long wallet = runtime.ledgerBalance(
                PlayerPaymentCommit.walletAccount(playerId));
        long debt = runtime.ledgerBalance(
                PlayerPaymentCommit.debtAccount(playerId));
        long reserved = runtime.ledgerBalance(
                PlayerPaymentCommit.reservedAccount(playerId));
        return new Snapshot(wallet, debt, reserved, limit);
    }

    private static long balance(
            EscrowRuntimeService runtime,
            UUID playerId
    ) {
        return Math.addExact(runtime.ledgerBalance(
                        PlayerPaymentCommit.walletAccount(playerId)),
                runtime.ledgerBalance(
                        PlayerPaymentCommit.debtAccount(playerId)));
    }

    private static long balance(
            EvidenceBackend backend,
            UUID playerId
    ) {
        return Math.addExact(backend.balance(
                        PlayerPaymentCommit.walletAccount(playerId)),
                backend.balance(
                        PlayerPaymentCommit.debtAccount(playerId)));
    }

    private static boolean postPre(
            UUID playerId,
            long units,
            long before
    ) {
        try {
            return MinecraftForge.EVENT_BUS.post(
                    new BalanceChangeEvent.Pre(
                            playerId, units, "CLAIM", before));
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private static void postAfter(
            UUID playerId,
            long units,
            long after
    ) {
        try {
            MinecraftForge.EVENT_BUS.post(new BalanceChangeEvent.Post(
                    playerId, units, "CLAIM", after));
        } catch (RuntimeException ignored) {
        }
    }

    private static CollectionResult result(
            Status status,
            UUID requestId,
            UUID claimId,
            long collectedMinorUnits,
            long resultingBalanceMinorUnits,
            boolean replayed
    ) {
        return new CollectionResult(status, requestId, claimId,
                collectedMinorUnits, resultingBalanceMinorUnits,
                replayed);
    }

    private record Snapshot(
            long wallet,
            long debt,
            long reserved,
            long limit
    ) {
        private Snapshot {
            if (wallet < 0L || debt > 0L || reserved < 0L
                    || wallet > 0L && debt < 0L || limit < 0L) {
                throw new IllegalArgumentException(
                        "Money claim wallet snapshot is invalid");
            }
        }

        private long balance() {
            return Math.addExact(wallet, debt);
        }

        private long capacity() {
            long exposure = Math.addExact(balance(), reserved);
            if (exposure >= limit) {
                return 0L;
            }
            try {
                return Math.subtractExact(limit, exposure);
            } catch (ArithmeticException exception) {
                return Long.MAX_VALUE;
            }
        }
    }

    record ClaimConfiguration(long generation, long limit) {
        ClaimConfiguration {
            if (generation < 0L || limit < 0L) {
                throw new IllegalArgumentException(
                        "Money claim configuration is invalid");
            }
        }

        boolean sameSemantics(ClaimConfiguration other) {
            Objects.requireNonNull(other, "other");
            return limit == other.limit;
        }
    }

    interface EvidenceBackend {
        Optional<LedgerTransaction> ledgerTransaction(UUID requestId);

        Optional<ClaimAttemptResult> claimAttempt(String requestKey);

        Optional<EscrowClaim> claim(UUID claimId);

        long balance(LedgerAccountId account);
    }

    private record LiveEvidence(EscrowRuntimeService runtime)
            implements EvidenceBackend {
        private LiveEvidence {
            Objects.requireNonNull(runtime, "runtime");
        }

        @Override
        public Optional<LedgerTransaction> ledgerTransaction(
                UUID requestId
        ) {
            return runtime.ledgerTransaction(requestId);
        }

        @Override
        public Optional<ClaimAttemptResult> claimAttempt(
                String requestKey
        ) {
            return runtime.claimAttempt(requestKey);
        }

        @Override
        public Optional<EscrowClaim> claim(UUID claimId) {
            return runtime.claim(claimId);
        }

        @Override
        public long balance(LedgerAccountId account) {
            return runtime.ledgerBalance(account);
        }
    }

    public record CollectionResult(
            Status status,
            UUID requestId,
            UUID claimId,
            long collectedMinorUnits,
            long resultingBalanceMinorUnits,
            boolean replayed
    ) {
        public CollectionResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(claimId, "claimId");
            if (ZERO_UUID.equals(requestId)
                    || ZERO_UUID.equals(claimId)
                    || collectedMinorUnits < 0L
                    || replayed && status != Status.SUCCESS
                    || (status == Status.SUCCESS)
                    != (collectedMinorUnits > 0L)
                    || status == Status.ESCROW_UNAVAILABLE
                    && resultingBalanceMinorUnits != 0L) {
                throw new IllegalArgumentException(
                        "Money claim collection result is invalid");
            }
        }

        public boolean successful() {
            return status == Status.SUCCESS;
        }
    }

    public enum Status {
        SUCCESS,
        NOT_FOUND,
        ALREADY_COLLECTED,
        WALLET_FULL,
        CANCELLED,
        CONFIG_CHANGED,
        REENTRANT_REQUEST,
        REQUEST_CONFLICT,
        RECOVERY_REQUIRED,
        ESCROW_UNAVAILABLE
    }
}
