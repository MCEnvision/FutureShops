package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.block.ShopBlockEntity;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopAssetEndpoint;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopAtomicCommit;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopBackendException;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopClaimCreationEvidence;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopClaimPlan;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopEscrowBackend;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopEscrowIntent;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopEscrowOrchestrator;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopExecutionSnapshot;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopFundingEvidence;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopIdentity;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopMoneyMutationReceipt;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopMoneyTransfer;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopMutationPreparation;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopOperation;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopPaymentSource;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopPreparedExecution;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopRequestIdentity;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopSettlementImportEvidence;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopTradeMethod;
import com.enviouse.futureshops.server.shop.PlayerShopSettlementSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PlayerShopSettlementEscrowService {
    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    private static final String LEDGER_REASON =
            "PLAYER_SHOP_SETTLEMENT_IMPORT";

    private PlayerShopSettlementEscrowService() {
    }

    public static PlayerShopEscrowOrchestrator.Result collect(
            ServerPlayer player,
            ShopBlockEntity shop,
            BlockPos position,
            UUID requestId,
            int responseToken
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(shop, "shop");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(requestId, "requestId");
        if (ZERO_UUID.equals(requestId)) {
            throw new IllegalArgumentException(
                    "Player shop settlement request cannot be zero");
        }
        if (player.getServer() == null) {
            throw new PlayerShopBackendException(
                    PlayerShopBackendException.Kind.REJECTED,
                    "Player shop settlement server is unavailable");
        }
        EscrowRuntimeService runtime = EscrowRuntimeManager.requireReady();
        shop.reconcileRegistryIdentity();
        PlayerShopEscrowOrchestrator.Command command = command(
                runtime, player, shop, position, requestId, responseToken);
        SettlementDriver driver = new SettlementDriver(runtime, player,
                position, command.settlementImport());
        RuntimePlayerShopEscrowBackend backend =
                new RuntimePlayerShopEscrowBackend(runtime, driver);
        return new PlayerShopEscrowOrchestrator(backend,
                Clock.systemUTC()).execute(command);
    }

    private static PlayerShopEscrowOrchestrator.Command command(
            EscrowRuntimeService runtime,
            ServerPlayer player,
            ShopBlockEntity shop,
            BlockPos position,
            UUID requestId,
            int responseToken
    ) {
        Optional<PlayerShopEscrowSavedData.Entry> existing =
                runtime.playerShopEscrowEntry(requestId);
        if (existing.isPresent()) {
            PlayerShopExecutionSnapshot snapshot =
                    existing.orElseThrow().snapshot();
            PlayerShopSettlementImportEvidence settlement =
                    snapshot.settlementImport();
            if (settlement == null
                    || !snapshot.intent().actorId().equals(player.getUUID())
                    || !matchesCurrentShop(snapshot.intent(), shop,
                    position)) {
                throw new PlayerShopBackendException(
                        PlayerShopBackendException.Kind.CONFLICT,
                        "Player shop settlement request identity conflicts");
            }
            return PlayerShopEscrowOrchestrator.Command.settlement(
                    snapshot.intent(), responseToken, settlement);
        }
        UUID ownerId = requireOwner(player, shop);
        UUID registryShopId = Objects.requireNonNull(
                shop.getRegistryShopId(), "registryShopId");
        String legacySourceKey = legacyKey(identity(shop, position, ownerId,
                registryShopId));
        List<PlayerShopEscrowSavedData.Entry> pendingEntries = runtime
                .pendingPlayerShopRecovery(
                        PlayerShopEscrowSavedData.MAXIMUM_ENTRIES);
        Optional<PlayerShopSettlementImportEvidence> ownedImport =
                selectPendingImport(pendingEntries.stream()
                                .map(entry -> entry.snapshot()
                                        .settlementImport())
                                .filter(Objects::nonNull).toList(),
                        ownerId, registryShopId, legacySourceKey);
        Optional<PlayerShopEscrowSavedData.Entry> pendingImport = ownedImport
                .map(evidence -> pendingEntries.stream().filter(entry ->
                        entry.snapshot().intent().requestId().equals(
                                evidence.requestId())).findFirst().orElseThrow(
                        () -> new PlayerShopBackendException(
                                PlayerShopBackendException.Kind
                                        .RECOVERY_REQUIRED,
                                "Player shop settlement import owner is missing")));
        if (pendingImport.isPresent()) {
            PlayerShopExecutionSnapshot snapshot = pendingImport.orElseThrow()
                    .snapshot();
            if (!matchesCurrentShop(snapshot.intent(), shop, position)) {
                throw new PlayerShopBackendException(
                        PlayerShopBackendException.Kind.CONFLICT,
                        "Player shop settlement import moved shops");
            }
            return PlayerShopEscrowOrchestrator.Command.settlement(
                    snapshot.intent(), responseToken,
                    snapshot.settlementImport());
        }
        PlayerShopSettlementSavedData settlements =
                PlayerShopSettlementSavedData.get(player.getServer());
        PlayerShopSettlementSavedData.Snapshot legacy = settlements.snapshot(
                ownerId, position.asLong(), 1);
        long pending = legacy.pendingMinor();
        if (pending <= 0L) {
            throw new PlayerShopBackendException(
                    PlayerShopBackendException.Kind.REJECTED,
                    "Player shop settlement has no pending money");
        }
        PlayerShopIdentity identity = identity(shop, position, ownerId,
                registryShopId);
        PlayerShopClaimPlan claim = PlayerShopClaimPlan.money(requestId,
                "settlement", ownerId, pending,
                "Player shop settlement claim");
        LedgerAccountId claimAccount = claimAccount(claim.claimId());
        long claimBalance = runtime.ledgerBalance(claimAccount);
        if (claimBalance != 0L) {
            throw new PlayerShopBackendException(
                    PlayerShopBackendException.Kind.CONFLICT,
                    "Player shop settlement claim account is not empty");
        }
        PlayerShopMoneyTransfer transfer = new PlayerShopMoneyTransfer(
                deterministicId("settlement transfer", requestId),
                PlayerShopAssetEndpoint.participant(
                        PlayerShopAssetEndpoint.Kind.SETTLEMENT_BALANCE,
                        ownerId, legacyKey(identity)),
                PlayerShopAssetEndpoint.participant(
                        PlayerShopAssetEndpoint.Kind.MONEY_CLAIM,
                        ownerId, claim.claimId().toString()),
                pending, PlayerShopPaymentSource.NONE, pending,
                claimBalance);
        Instant quotedAt = Instant.now();
        PlayerShopEscrowIntent intent = PlayerShopEscrowIntent.prepared(
                requestId, ownerId, ownerId, identity,
                PlayerShopOperation.SETTLEMENT_CLAIM,
                PlayerShopTradeMethod.SETTLEMENT,
                PlayerShopPaymentSource.NONE, 1, quotedAt, null,
                List.of(transfer), List.of(), List.of(claim), List.of());
        PlayerShopSettlementImportEvidence evidence =
                PlayerShopSettlementImportEvidence.capture(requestId,
                        ownerId, registryShopId, legacyKey(identity),
                        Math.max(0L, legacy.lifetimeMinor()), pending);
        return PlayerShopEscrowOrchestrator.Command.settlement(intent,
                responseToken, evidence);
    }

    private static UUID requireOwner(
            ServerPlayer player,
            ShopBlockEntity shop
    ) {
        UUID ownerId = shop.getOwnerUuid();
        if (ownerId == null || !ownerId.equals(player.getUUID())) {
            throw new PlayerShopBackendException(
                    PlayerShopBackendException.Kind.REJECTED,
                    "Only the player shop owner can claim settlement money");
        }
        return ownerId;
    }

    private static PlayerShopIdentity identity(
            ShopBlockEntity shop,
            BlockPos position,
            UUID ownerId,
            UUID registryShopId
    ) {
        String shopId = shop.getShopId();
        if (shopId == null || shopId.isBlank()) {
            shopId = "player_shop." + registryShopId;
        }
        return new PlayerShopIdentity(registryShopId,
                shop.getRegistryIdentityRevision(), shopId,
                shop.getLevel().dimension().location().toString(),
                position.getX(), position.getY(), position.getZ(), ownerId);
    }

    private static boolean matchesCurrentShop(
            PlayerShopEscrowIntent intent,
            ShopBlockEntity shop,
            BlockPos position
    ) {
        PlayerShopIdentity identity = intent.shopIdentity();
        return intent.operation() == PlayerShopOperation.SETTLEMENT_CLAIM
                && Objects.equals(shop.getOwnerUuid(), intent.ownerId())
                && Objects.equals(shop.getRegistryShopId(),
                identity.registryShopId())
                && shop.getRegistryIdentityRevision()
                == identity.identityRevision()
                && shop.getLevel() != null
                && shop.getLevel().dimension().location().toString().equals(
                identity.dimensionId())
                && position.getX() == identity.blockX()
                && position.getY() == identity.blockY()
                && position.getZ() == identity.blockZ();
    }

    private static String legacyKey(PlayerShopIdentity identity) {
        return identity.dimensionId() + "." + identity.blockX() + "."
                + identity.blockY() + "." + identity.blockZ();
    }

    private static UUID deterministicId(String purpose, UUID requestId) {
        return UUID.nameUUIDFromBytes(("futureshops.player.shop."
                + purpose + "." + requestId).getBytes(
                StandardCharsets.UTF_8));
    }

    private static LedgerAccountId claimAccount(UUID claimId) {
        return new LedgerAccountId(LedgerAccountType.PLAYER_CLAIM,
                claimId.toString());
    }

    static Optional<PlayerShopSettlementImportEvidence> selectPendingImport(
            List<PlayerShopSettlementImportEvidence> candidates,
            UUID ownerId,
            UUID registryShopId,
            String legacySettlementKey
    ) {
        List<PlayerShopSettlementImportEvidence> matches = List.copyOf(
                Objects.requireNonNull(candidates, "candidates")).stream()
                .filter(value -> value.sameLegacySource(ownerId,
                        registryShopId, legacySettlementKey)).toList();
        if (matches.size() > 1) {
            throw new PlayerShopBackendException(
                    PlayerShopBackendException.Kind.CONFLICT,
                    "Player shop settlement has conflicting imports");
        }
        return matches.stream().findFirst();
    }

    static LegacyCleanupDisposition legacyCleanupDisposition(
            long pendingMinorUnits,
            long expectedMinorUnits
    ) {
        if (pendingMinorUnits < 0L || expectedMinorUnits <= 0L) {
            throw new IllegalArgumentException(
                    "Player shop settlement cleanup amount is invalid");
        }
        if (pendingMinorUnits == 0L) {
            return LegacyCleanupDisposition.ALREADY_CLEARED;
        }
        return pendingMinorUnits == expectedMinorUnits
                ? LegacyCleanupDisposition.CLEAR
                : LegacyCleanupDisposition.CONFLICT;
    }

    enum LegacyCleanupDisposition {
        CLEAR,
        ALREADY_CLEARED,
        CONFLICT
    }

    private static final class SettlementDriver
            implements RuntimePlayerShopEscrowBackend.MutationDriver {
        private final EscrowRuntimeService runtime;
        private final ServerPlayer player;
        private final BlockPos position;
        private final PlayerShopSettlementImportEvidence settlement;

        private SettlementDriver(
                EscrowRuntimeService runtime,
                ServerPlayer player,
                BlockPos position,
                PlayerShopSettlementImportEvidence settlement
        ) {
            this.runtime = Objects.requireNonNull(runtime, "runtime");
            this.player = Objects.requireNonNull(player, "player");
            this.position = Objects.requireNonNull(position, "position");
            this.settlement = Objects.requireNonNull(settlement,
                    "settlement");
        }

        @Override
        public PlayerShopPreparedExecution prepare(
                PlayerShopRequestIdentity requestIdentity,
                PlayerShopEscrowIntent intent
        ) {
            requireIntent(intent);
            PlayerShopMutationPreparation mutation =
                    PlayerShopMutationPreparation.money(
                            intent.moneyTransfers().get(0),
                            settlement.sourceFingerprint().getBytes(
                                    StandardCharsets.UTF_8));
            Instant preparedAt = Instant.now();
            if (preparedAt.isBefore(intent.quoteCreatedAt())) {
                preparedAt = intent.quoteCreatedAt();
            }
            return PlayerShopPreparedExecution.create(requestIdentity,
                    intent, preparedAt, List.of(mutation));
        }

        @Override
        public PlayerShopFundingEvidence commitFunding(
                PlayerShopPreparedExecution preparation,
                PlayerShopFundingEvidence existing,
                RuntimePlayerShopEscrowBackend.FundingProgress progress
        ) {
            PlayerShopEscrowIntent intent = preparation.intent();
            requireIntent(intent);
            PlayerShopMoneyTransfer transfer = intent.moneyTransfers().get(0);
            UUID seedId = seedId(intent.requestId());
            LedgerTransaction seed = seedTransaction(intent, transfer);
            long claimBalance = runtime.ledgerBalance(
                    claimAccount(intent.claims().get(0).claimId()));
            if (runtime.wasLedgerTransactionApplied(seedId)) {
                if (!runtime.ledgerTransaction(seedId).filter(
                        seed::equals).isPresent()
                        || claimBalance != transfer.amountMinorUnits()) {
                    throw recovery(
                            "Player shop settlement ledger evidence conflicts");
                }
                clearLegacySettlement();
                return completeFunding(intent, transfer, seed);
            }
            PlayerShopSettlementSavedData settlements =
                    PlayerShopSettlementSavedData.get(
                            Objects.requireNonNull(player.getServer()));
            PlayerShopSettlementSavedData.Snapshot current =
                    settlements.snapshot(settlement.ownerId(),
                            position.asLong(), 1);
            if (current.pendingMinor() != settlement.pendingMinorUnits()
                    || claimBalance != 0L) {
                throw recovery(
                        "Player shop legacy settlement evidence changed");
            }
            try {
                runtime.commitLedger(seed);
            } catch (RuntimeException exception) {
                throw recovery(
                        "Player shop settlement ledger import requires recovery");
            }
            if (!runtime.wasLedgerTransactionApplied(seedId)
                    || runtime.ledgerBalance(claimAccount(
                    intent.claims().get(0).claimId()))
                    != transfer.amountMinorUnits()) {
                throw recovery(
                        "Player shop settlement ledger import is incomplete");
            }
            clearLegacySettlement();
            return completeFunding(intent, transfer, seed);
        }

        @Override
        public PlayerShopClaimCreationEvidence createClaims(
                PlayerShopPreparedExecution preparation,
                PlayerShopFundingEvidence funding
        ) {
            PlayerShopEscrowIntent intent = preparation.intent();
            requireIntent(intent);
            PlayerShopClaimPlan plan = intent.claims().get(0);
            Instant createdAt = preparation.preparedAt();
            EscrowClaim expected = new EscrowClaim(plan.claimId(),
                    intent.requestId(), plan.beneficiaryId(),
                    "player.shop.settlement." + intent.requestId(),
                    ClaimKind.MONEY, plan.moneyAmountMinorUnits(),
                    plan.moneyAmountMinorUnits(), new byte[0],
                    ClaimStatus.PENDING, plan.purpose(), createdAt,
                    createdAt);
            Optional<EscrowClaim> existing = runtime.claim(plan.claimId());
            boolean replayed;
            if (existing.isPresent()) {
                if (!existing.orElseThrow().equals(expected)) {
                    throw new PlayerShopBackendException(
                            PlayerShopBackendException.Kind.CONFLICT,
                            "Player shop settlement claim conflicts");
                }
                replayed = true;
            } else {
                replayed = runtime.createClaim(expected).replayed();
            }
            return new PlayerShopClaimCreationEvidence(intent.requestId(),
                    replayed
                            ? PlayerShopClaimCreationEvidence.Status.IDEMPOTENT_REPLAY
                            : PlayerShopClaimCreationEvidence.Status.CREATED,
                    intent.claims(), settlement.sourceFingerprint(), "");
        }

        @Override
        public PlayerShopEscrowBackend.DeliveryResult deliverClaims(
                PlayerShopAtomicCommit commit,
                PlayerShopPreparedExecution preparation
        ) {
            PlayerShopClaimPlan plan = commit.createdClaims().get(0);
            UUID deliveryRequest = deterministicId(
                    "settlement delivery", commit.commitId());
            EscrowMoneyClaimService.CollectionResult result =
                    EscrowMoneyClaimService.collect(player, plan.claimId(),
                            deliveryRequest);
            return switch (result.status()) {
                case SUCCESS -> new PlayerShopEscrowBackend.DeliveryResult(
                        PlayerShopEscrowBackend.DeliveryStatus.DELIVERED, "");
                case ALREADY_COLLECTED -> completedClaim(plan.claimId())
                        ? new PlayerShopEscrowBackend.DeliveryResult(
                        PlayerShopEscrowBackend.DeliveryStatus.DELIVERED, "")
                        : pending("Settlement claim state changed");
                case WALLET_FULL, CANCELLED, CONFIG_CHANGED,
                        REENTRANT_REQUEST -> pending(
                        "Settlement money remains available as a claim");
                case NOT_FOUND, REQUEST_CONFLICT, RECOVERY_REQUIRED,
                        ESCROW_UNAVAILABLE -> recoveryDelivery(
                        "Settlement claim delivery requires recovery");
            };
        }

        @Override
        public PlayerShopEscrowBackend.RecoveryResult recover(
                PlayerShopExecutionSnapshot snapshot
        ) {
            PlayerShopEscrowIntent intent = snapshot.intent();
            requireIntent(intent);
            PlayerShopMoneyTransfer transfer = intent.moneyTransfers().get(0);
            boolean seeded = runtime.wasLedgerTransactionApplied(
                    seedId(intent.requestId()));
            long claimBalance = runtime.ledgerBalance(claimAccount(
                    intent.claims().get(0).claimId()));
            long pending = PlayerShopSettlementSavedData.get(
                    Objects.requireNonNull(player.getServer())).snapshot(
                    settlement.ownerId(), position.asLong(), 1).pendingMinor();
            if (seeded && claimBalance >= 0L
                    && claimBalance <= transfer.amountMinorUnits()) {
                return new PlayerShopEscrowBackend.RecoveryResult(
                        PlayerShopEscrowBackend.RecoveryStatus.RESUMABLE,
                        "Settlement ledger import can resume");
            }
            if (!seeded && pending == settlement.pendingMinorUnits()
                    && claimBalance == 0L) {
                return new PlayerShopEscrowBackend.RecoveryResult(
                        PlayerShopEscrowBackend.RecoveryStatus.RESUMABLE,
                        "Settlement legacy import can resume");
            }
            return new PlayerShopEscrowBackend.RecoveryResult(
                    PlayerShopEscrowBackend.RecoveryStatus.RECOVERY_REQUIRED,
                    "Settlement import evidence requires review");
        }

        @Override
        public void markSettlementImported(
                PlayerShopSettlementImportEvidence ignored,
                PlayerShopAtomicCommit commit
        ) {
            if (!runtime.wasLedgerTransactionApplied(seedId(
                    commit.commitId()))) {
                throw recovery(
                        "Player shop settlement import marker is premature");
            }
        }

        private boolean completedClaim(UUID claimId) {
            return runtime.claim(claimId).map(value ->
                    value.status() == ClaimStatus.COMPLETED
                            && value.remainingUnits() == 0L).orElse(false);
        }

        private static PlayerShopEscrowBackend.DeliveryResult pending(
                String detail
        ) {
            return new PlayerShopEscrowBackend.DeliveryResult(
                    PlayerShopEscrowBackend.DeliveryStatus.CLAIMS_PENDING,
                    detail);
        }

        private static PlayerShopEscrowBackend.DeliveryResult recoveryDelivery(
                String detail
        ) {
            return new PlayerShopEscrowBackend.DeliveryResult(
                    PlayerShopEscrowBackend.DeliveryStatus.RECOVERY_REQUIRED,
                    detail);
        }

        private PlayerShopFundingEvidence completeFunding(
                PlayerShopEscrowIntent intent,
                PlayerShopMoneyTransfer transfer,
                LedgerTransaction seed
        ) {
            PlayerShopMoneyMutationReceipt receipt =
                    PlayerShopMoneyMutationReceipt.applied(
                            intent.requestId(), transfer, 0L,
                            transfer.amountMinorUnits(), seed.fingerprint()
                                    .getBytes(StandardCharsets.UTF_8));
            return PlayerShopFundingEvidence.complete(intent.requestId(),
                    List.of(receipt), List.of(), List.of());
        }

        private void clearLegacySettlement() {
            PlayerShopSettlementSavedData settlements =
                    PlayerShopSettlementSavedData.get(
                            Objects.requireNonNull(player.getServer()));
            long pending = settlements.snapshot(settlement.ownerId(),
                    position.asLong(), 1).pendingMinor();
            LegacyCleanupDisposition disposition = legacyCleanupDisposition(
                    pending, settlement.pendingMinorUnits());
            if (disposition == LegacyCleanupDisposition.ALREADY_CLEARED) return;
            if (disposition == LegacyCleanupDisposition.CONFLICT) throw recovery(
                    "Player shop legacy settlement cleanup conflicts");
            long claimed = settlements.claim(settlement.ownerId(),
                    position.asLong());
            if (claimed != settlement.pendingMinorUnits()
                    || settlements.snapshot(settlement.ownerId(),
                    position.asLong(), 1).pendingMinor() != 0L) {
                throw recovery(
                        "Player shop legacy settlement cleanup is incomplete");
            }
        }

        private LedgerTransaction seedTransaction(
                PlayerShopEscrowIntent intent,
                PlayerShopMoneyTransfer transfer
        ) {
            return new LedgerTransaction(seedId(intent.requestId()),
                    "player.shop.settlement.import." + intent.requestId(),
                    LEDGER_REASON, List.of(
                    new LedgerLeg(new LedgerAccountId(
                            LedgerAccountType.ADMIN_SOURCE,
                            "player.shop.settlement"),
                            Math.negateExact(transfer.amountMinorUnits())),
                    new LedgerLeg(claimAccount(
                            intent.claims().get(0).claimId()),
                            transfer.amountMinorUnits())));
        }

        private static UUID seedId(UUID requestId) {
            return deterministicId("settlement ledger", requestId);
        }

        private void requireIntent(PlayerShopEscrowIntent intent) {
            if (intent.operation() != PlayerShopOperation.SETTLEMENT_CLAIM
                    || !settlement.matches(intent)
                    || intent.moneyTransfers().size() != 1
                    || intent.claims().size() != 1) {
                throw new PlayerShopBackendException(
                        PlayerShopBackendException.Kind.CONFLICT,
                        "Player shop settlement intent conflicts");
            }
        }

        private static PlayerShopBackendException recovery(String detail) {
            return new PlayerShopBackendException(
                    PlayerShopBackendException.Kind.RECOVERY_REQUIRED,
                    detail);
        }
    }
}
