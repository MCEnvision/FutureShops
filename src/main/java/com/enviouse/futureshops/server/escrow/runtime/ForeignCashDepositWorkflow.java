package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.money.CurrencyManager;
import com.enviouse.futureshops.money.PhysicalCurrencyAdapter;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.model.CashDepositMode;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashInventoryState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class ForeignCashDepositWorkflow {
    private final MinecraftServer server;
    private final EscrowRuntimeService runtime;
    private final ForeignCashDepositIntentStore intentStore;
    private final Set<UUID> activePlayers = new HashSet<>();

    ForeignCashDepositWorkflow(
            MinecraftServer server,
            EscrowRuntimeService runtime,
            ForeignCashDepositIntentStore intentStore
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.intentStore = Objects.requireNonNull(intentStore, "intentStore");
    }

    synchronized Outcome deposit(
            ServerPlayer player,
            ForeignCashDepositPlan plan,
            UUID requestId,
            UUID transactionId,
            String requestKey,
            long walletBalanceLimitMinorUnits,
            CashDepositMode depositMode,
            Instant now
    ) {
        requireServerThread();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(requestKey, "requestKey");
        Objects.requireNonNull(depositMode, "depositMode");
        Objects.requireNonNull(now, "now");
        if (server.getPlayerList().getPlayer(player.getUUID()) != player) {
            throw new EscrowRuntimeException(
                    "Foreign cash deposit requires an active player");
        }
        ProtectedCashInventoryState before = null;
        ForeignCashDepositReservation reservation = null;
        ForeignCashDepositEvidence intent = null;
        boolean intentPersistAttempted = false;
        boolean reservationCommitted = false;
        boolean inventoryRemoved = false;
        if (!activePlayers.add(player.getUUID())) {
            throw new EscrowRuntimeException(
                    "A foreign cash deposit is already active for this player");
        }
        try {
            before = ProtectedCashInventoryState.capture(
                    player.getInventory());
            reservation = ForeignCashDepositFactory.reservation(requestId,
                    player.getUUID(), transactionId, requestKey,
                    walletBalanceLimitMinorUnits, depositMode, plan,
                    before, now);
            intent = ForeignCashDepositEvidence.intent(reservation, before);
            intentPersistAttempted = true;
            intentStore.persistIntent(server, player, intent);
            runtime.commitForeignCashReservation(reservation);
            reservationCommitted = true;
            requireCurrentProvider(reservation);
            ProtectedCashInventoryState.RemovalResult removal =
                    before.removeExact(player.getInventory(),
                            reservation.playerId(),
                            reservation.transactionId(),
                            reservation.reservationId(),
                            reservation.inventoryBeforeHash(),
                            ForeignCashDepositSettlement
                                    .inventoryMutationRequestKey(
                                            reservation.transactionId()),
                            reservation.plan().portions().stream()
                                    .map(portion -> new
                                            ProtectedCashInventoryState
                                                    .RemovalPortion(
                                            portion.slot(),
                                            portion.originalStackCount(),
                                            portion.selectedCount(),
                                            portion.exactStackSnapshot()))
                                    .toList(), now);
            inventoryRemoved = true;
            player.containerMenu.broadcastChanges();
            long walletBefore = runtime.ledgerBalance(
                    reservation.destinationAccount());
            long reservedBefore = runtime.ledgerBalance(
                    new LedgerAccountId(LedgerAccountType.PLAYER_RESERVED,
                            player.getUUID().toString()));
            ForeignCashDepositSettlement settlement =
                    ForeignCashDepositFactory.settlement(reservation,
                            removal.receipt(), walletBefore,
                            reservedBefore, now);
            intentStore.persistUpgrade(server, player,
                    ForeignCashDepositEvidence.settlement(settlement,
                            removal.afterInventory()));
            runtime.commitForeignCashSettlement(settlement);
            boolean cleanupPending = !cleanup(player, transactionId);
            return new Outcome(reservation, settlement, cleanupPending);
        } catch (IOException | RuntimeException exception) {
            boolean cancellationResolved = false;
            if (reservation != null && before != null
                    && reservationCommitted && !inventoryRemoved
                    && before.matches(player.getInventory())) {
                try {
                    ForeignCashDepositCancellation cancellation =
                            ForeignCashDepositFactory.cancellation(
                                    reservation, before, now);
                    intentStore.persistUpgrade(server, player,
                            ForeignCashDepositEvidence.cancellation(
                                    cancellation, before));
                    runtime.commitForeignCashCancellation(cancellation);
                    cleanup(player, transactionId);
                    cancellationResolved = true;
                } catch (IOException | RuntimeException cancellationFailure) {
                    exception.addSuppressed(cancellationFailure);
                    enqueueRecovery(reservation, exception);
                }
            } else if (reservation != null && reservationCommitted) {
                enqueueRecovery(reservation, exception);
            } else if (intentPersistAttempted && intent != null) {
                CashDepositRecoveryEnqueueResult enqueueResult =
                        enqueueIntentRecovery(intent, exception);
                switch (enqueueResult) {
                    case NO_DURABLE_EVIDENCE -> discardMatchingIntent(
                            player, intent, exception);
                    case QUEUED -> {
                    }
                    case FAILED -> {
                    }
                }
            }
            if (cancellationResolved) {
                throw new CashDepositCancellationCompletedException(
                        reservation.transactionId(), exception);
            }
            throw new EscrowRuntimeException(
                    "Foreign cash deposit did not complete", exception);
        } finally {
            activePlayers.remove(player.getUUID());
        }
    }

    private static void requireCurrentProvider(
            ForeignCashDepositReservation reservation
    ) {
        PhysicalCurrencyAdapter current = CurrencyManager.getOrNull();
        if (current == null || current.isInternal()
                || !current.id().equals(reservation.plan().providerId())
                || !current.depositConfigurationSignature().equals(
                reservation.plan().providerSignature())) {
            throw new ConfigurationChangedException();
        }
        for (ForeignCashDepositPlan.Portion portion :
                reservation.plan().portions()) {
            net.minecraft.world.item.ItemStack snapshot =
                    com.enviouse.futureshops.money.ItemStackSnapshotCodec
                            .decode(portion.exactStackSnapshot());
            if (current.unitValueMinor(snapshot)
                    != portion.unitValueMinorUnits()) {
                throw new ConfigurationChangedException();
            }
        }
    }

    private boolean cleanup(ServerPlayer player, UUID transactionId) {
        try {
            intentStore.cleanup(server, player, transactionId);
            return true;
        } catch (IOException | RuntimeException exception) {
            runtime.scheduleForeignCashCleanup(
                    player.getUUID(), transactionId);
            return false;
        }
    }

    private CashDepositRecoveryEnqueueResult enqueueIntentRecovery(
            ForeignCashDepositEvidence evidence,
            Throwable failure
    ) {
        try {
            return runtime.enqueueForeignCashIntentRecovery(evidence);
        } catch (RuntimeException enqueueFailure) {
            failure.addSuppressed(enqueueFailure);
            return CashDepositRecoveryEnqueueResult.FAILED;
        }
    }

    private void discardMatchingIntent(
            ServerPlayer player,
            ForeignCashDepositEvidence evidence,
            Throwable failure
    ) {
        try {
            intentStore.discardIntent(server, player, evidence);
        } catch (IOException | RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private void enqueueRecovery(
            ForeignCashDepositReservation reservation,
            Throwable failure
    ) {
        try {
            runtime.enqueueForeignCashRecovery(
                    reservation.transactionId());
        } catch (RuntimeException enqueueFailure) {
            failure.addSuppressed(enqueueFailure);
        }
    }

    private void requireServerThread() {
        if (!server.isSameThread()) {
            throw new EscrowRuntimeException(
                    "Foreign cash deposit must run on the server thread");
        }
    }

    record Outcome(
            ForeignCashDepositReservation reservation,
            ForeignCashDepositSettlement settlement,
            boolean cleanupPending
    ) {
        Outcome {
            Objects.requireNonNull(reservation, "reservation");
            Objects.requireNonNull(settlement, "settlement");
            if (!reservation.equals(settlement.reservation())) {
                throw new IllegalArgumentException(
                        "Foreign cash deposit outcome identity is invalid");
            }
        }
    }

    static final class ConfigurationChangedException
            extends RuntimeException {
        private ConfigurationChangedException() {
            super("Foreign currency configuration changed before removal");
        }
    }
}
