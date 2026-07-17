package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.money.InternalBillInventoryPlanner;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashInventoryState;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionCancellation;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionEvidence;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionFactory;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionIntentStore;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionReservation;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionSettlement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class ProtectedCashRedemptionWorkflow {
    private final MinecraftServer server;
    private final EscrowRuntimeService runtime;
    private final ProtectedCashRedemptionIntentStore intentStore;
    private final Set<UUID> activePlayers = new HashSet<>();

    ProtectedCashRedemptionWorkflow(
            MinecraftServer server,
            EscrowRuntimeService runtime,
            ProtectedCashRedemptionIntentStore intentStore
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.intentStore = Objects.requireNonNull(intentStore, "intentStore");
    }

    synchronized Outcome redeem(
            ServerPlayer player,
            InternalBillInventoryPlanner.ExactPlan plan,
            UUID transactionId,
            String requestKey,
            long configRevision,
            long walletBalanceLimitMinorUnits,
            Instant now
    ) {
        requireServerThread();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(requestKey, "requestKey");
        Objects.requireNonNull(now, "now");
        if (server.getPlayerList().getPlayer(player.getUUID()) != player) {
            throw new EscrowRuntimeException(
                    "Protected cash redemption requires an active player");
        }
        ProtectedCashInventoryState before = null;
        ProtectedCashRedemptionReservation reservation = null;
        ProtectedCashRedemptionEvidence intent = null;
        boolean intentPersistAttempted = false;
        boolean reservationCommitted = false;
        boolean inventoryRemoved = false;
        if (!activePlayers.add(player.getUUID())) {
            throw new EscrowRuntimeException(
                    "A protected cash redemption is already active for this player");
        }
        try {
            before = ProtectedCashInventoryState.capture(
                    player.getInventory());
            reservation = ProtectedCashRedemptionFactory.walletReservation(
                    player.getUUID(), transactionId, requestKey,
                    configRevision, walletBalanceLimitMinorUnits, plan,
                    before, now);
            intent = ProtectedCashRedemptionEvidence.intent(
                    reservation, before);
            intentPersistAttempted = true;
            intentStore.persistIntent(server, player, intent);
            runtime.commitProtectedCashReservation(reservation);
            reservationCommitted = true;
            ProtectedCashInventoryState.RemovalResult removal =
                    before.removeExact(player.getInventory(), reservation, now);
            inventoryRemoved = true;
            player.containerMenu.broadcastChanges();
            long walletBefore = runtime.ledgerBalance(
                    reservation.destinationAccount());
            long reservedBefore = runtime.ledgerBalance(
                    new LedgerAccountId(LedgerAccountType.PLAYER_RESERVED,
                            player.getUUID().toString()));
            ProtectedCashRedemptionSettlement settlement =
                    ProtectedCashRedemptionFactory.settlement(reservation,
                            removal.receipt(), walletBefore, reservedBefore,
                            now);
            intentStore.persistUpgrade(server, player,
                    ProtectedCashRedemptionEvidence.settlement(settlement,
                            removal.afterInventory()));
            runtime.commitProtectedCashSettlement(settlement);
            boolean cleanupPending = !cleanup(player, transactionId);
            return new Outcome(reservation, settlement, cleanupPending);
        } catch (IOException | RuntimeException exception) {
            boolean cancellationResolved = false;
            if (reservation != null && before != null
                    && reservationCommitted && !inventoryRemoved
                    && before.matches(player.getInventory())) {
                try {
                    ProtectedCashRedemptionCancellation cancellation =
                            ProtectedCashRedemptionFactory.cancellation(
                                    reservation, before, now);
                    intentStore.persistUpgrade(server, player,
                            ProtectedCashRedemptionEvidence.cancellation(
                                    cancellation, before));
                    runtime.commitProtectedCashCancellation(cancellation);
                    cleanup(player, transactionId);
                    cancellationResolved = true;
                } catch (IOException | RuntimeException cancellationFailure) {
                    exception.addSuppressed(cancellationFailure);
                    enqueueRecovery(reservation, exception);
                }
            } else if (reservation != null && reservationCommitted) {
                enqueueRecovery(reservation, exception);
            } else if (intentPersistAttempted && intent != null) {
                if (!enqueueIntentRecovery(intent, exception)) {
                    discardMatchingLiveIntent(player, intent);
                }
            }
            if (cancellationResolved) {
                throw new CashDepositCancellationCompletedException(
                        reservation.transactionId(), exception);
            }
            throw new EscrowRuntimeException(
                    "Protected cash redemption did not complete", exception);
        } finally {
            activePlayers.remove(player.getUUID());
        }
    }

    private boolean cleanup(ServerPlayer player, UUID transactionId) {
        try {
            intentStore.cleanup(server, player, transactionId);
            return true;
        } catch (IOException | RuntimeException exception) {
            runtime.scheduleProtectedCashCleanup(
                    player.getUUID(), transactionId);
            return false;
        }
    }

    private boolean enqueueIntentRecovery(
            ProtectedCashRedemptionEvidence evidence,
            Throwable failure
    ) {
        try {
            return runtime.enqueueProtectedCashIntentRecovery(evidence);
        } catch (RuntimeException enqueueFailure) {
            failure.addSuppressed(enqueueFailure);
            return true;
        }
    }

    private static void discardMatchingLiveIntent(
            ServerPlayer player,
            ProtectedCashRedemptionEvidence evidence
    ) {
        net.minecraft.nbt.Tag raw = player.getPersistentData().get(
                ProtectedCashRedemptionIntentStore.EVIDENCE_KEY);
        if (raw instanceof net.minecraft.nbt.ByteArrayTag bytes) {
            try {
                if (ProtectedCashRedemptionEvidence.decode(
                        bytes.getAsByteArray()).equals(evidence)) {
                    player.getPersistentData().remove(
                            ProtectedCashRedemptionIntentStore.EVIDENCE_KEY);
                }
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void enqueueRecovery(
            ProtectedCashRedemptionReservation reservation,
            Throwable failure
    ) {
        try {
            runtime.enqueueProtectedCashRecovery(
                    reservation.transactionId());
        } catch (RuntimeException enqueueFailure) {
            failure.addSuppressed(enqueueFailure);
        }
    }

    private void requireServerThread() {
        if (!server.isSameThread()) {
            throw new EscrowRuntimeException(
                    "Protected cash redemption must run on the server thread");
        }
    }

    record Outcome(
            ProtectedCashRedemptionReservation reservation,
            ProtectedCashRedemptionSettlement settlement,
            boolean cleanupPending
    ) {
        Outcome {
            Objects.requireNonNull(reservation, "reservation");
            Objects.requireNonNull(settlement, "settlement");
            if (!reservation.equals(settlement.reservation())) {
                throw new IllegalArgumentException(
                        "Protected cash outcome identity is invalid");
            }
        }
    }
}
