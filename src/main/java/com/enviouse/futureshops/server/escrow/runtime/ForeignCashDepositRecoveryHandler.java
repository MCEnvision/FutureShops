package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.model.EscrowAssetLotType;
import com.enviouse.futureshops.server.escrow.model.EscrowError;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowPartyType;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashInventoryState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class ForeignCashDepositRecoveryHandler {
    private static final String ERROR_CODE =
            "foreign_cash_evidence_unknown";

    private final MinecraftServer server;
    private final EscrowRuntimeService runtime;
    private final ForeignCashDepositIntentStore intentStore;
    private final Clock clock;

    ForeignCashDepositRecoveryHandler(
            MinecraftServer server,
            EscrowRuntimeService runtime,
            ForeignCashDepositIntentStore intentStore,
            Clock clock
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.intentStore = Objects.requireNonNull(intentStore, "intentStore");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    EscrowRecoveryAttempt recover(EscrowTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        if (transaction.operation() != EscrowOperation.CURRENCY_DEPOSIT
                || transaction.assetLots().stream().noneMatch(lot ->
                lot.type()
                        == EscrowAssetLotType.FOREIGN_PHYSICAL_CURRENCY)) {
            return EscrowRecoveryAttempt.stable(
                    "Currency deposit is outside foreign cash recovery");
        }
        UUID playerId;
        try {
            playerId = playerId(transaction);
        } catch (RuntimeException exception) {
            return manualReview(transaction,
                    "Foreign cash player identity is invalid");
        }
        ForeignCashDepositIntentStore.Inspection inspection =
                intentStore.inspect(server, playerId,
                        transaction.transactionId().value());
        if (inspection.status()
                == ForeignCashDepositIntentStore.InspectionStatus.MISSING
                || inspection.status()
                == ForeignCashDepositIntentStore.InspectionStatus.UNKNOWN) {
            return manualReview(transaction, inspection.detail());
        }
        ForeignCashDepositEvidence evidence = inspection.evidence()
                .orElseThrow();
        ForeignCashDepositReservation reservation = evidence.reservation();
        if (transaction.state() != EscrowState.HELD
                || !transaction.equals(reservation.heldTransaction())) {
            return manualReview(transaction,
                    "Foreign cash transaction and evidence conflict");
        }
        try {
            return switch (inspection.status()) {
                case SETTLEMENT_PROVED -> {
                    runtime.commitForeignCashSettlement(
                            evidence.settlement().orElseThrow());
                    runtime.scheduleForeignCashCleanup(
                            reservation.playerId(),
                            reservation.transactionId());
                    yield EscrowRecoveryAttempt.resolved(
                            "Foreign cash settlement replayed from durable evidence");
                }
                case CANCELLATION_PROVED -> {
                    runtime.commitForeignCashCancellation(
                            evidence.cancellation().orElseThrow());
                    runtime.scheduleForeignCashCleanup(
                            reservation.playerId(),
                            reservation.transactionId());
                    yield EscrowRecoveryAttempt.resolved(
                            "Foreign cash cancellation replayed from durable evidence");
                }
                case INTENT_UNCHANGED -> cancelUnchanged(reservation,
                        evidence.inventoryState());
                case MISSING, UNKNOWN -> throw new IllegalStateException(
                        "Foreign cash inspection changed during recovery");
            };
        } catch (IOException | RuntimeException exception) {
            return manualReview(transaction, detail(exception));
        }
    }

    private EscrowRecoveryAttempt cancelUnchanged(
            ForeignCashDepositReservation reservation,
            ProtectedCashInventoryState unchanged
    ) throws IOException {
        Instant at = transitionTime(reservation.heldTransaction());
        ForeignCashDepositCancellation cancellation =
                ForeignCashDepositFactory.cancellation(reservation,
                        unchanged, at);
        ForeignCashDepositEvidence evidence =
                ForeignCashDepositEvidence.cancellation(cancellation,
                        unchanged);
        ServerPlayer player = server.getPlayerList().getPlayer(
                reservation.playerId());
        if (player == null) {
            intentStore.persistUpgradeOffline(server,
                    reservation.playerId(), evidence);
        } else {
            if (!unchanged.matches(player.getInventory())) {
                throw new IllegalStateException(
                        "Foreign cash live inventory differs from durable state");
            }
            intentStore.persistUpgrade(server, player, evidence);
        }
        runtime.commitForeignCashCancellation(cancellation);
        runtime.scheduleForeignCashCleanup(reservation.playerId(),
                reservation.transactionId());
        return EscrowRecoveryAttempt.resolved(
                "Foreign cash unchanged intent was cancelled");
    }

    private EscrowRecoveryAttempt manualReview(
            EscrowTransaction transaction,
            String reason
    ) {
        String limited = limited(reason);
        if (transaction.state() == EscrowState.MANUAL_REVIEW) {
            return EscrowRecoveryAttempt.manualReview(limited);
        }
        Instant at = transitionTime(transaction);
        EscrowTransaction current = transaction;
        try {
            if (current.state() != EscrowState.RECOVERY_REQUIRED) {
                EscrowError error = new EscrowError(ERROR_CODE, limited,
                        true, at, Map.of(
                        "operation", EscrowOperation.CURRENCY_DEPOSIT.name(),
                        "state", current.state().name()));
                current = current.requireRecovery(error, 1, at, at);
                runtime.commitTransaction(current);
            }
            EscrowTransaction manual = current.transitionTo(
                    EscrowState.MANUAL_REVIEW, at);
            runtime.commitTransaction(manual);
            return EscrowRecoveryAttempt.manualReview(
                    "Foreign cash deposit moved to manual review. "
                            + limited);
        } catch (RuntimeException exception) {
            return EscrowRecoveryAttempt.manualReview(
                    "Foreign cash deposit requires manual review. "
                            + limited);
        }
    }

    private Instant transitionTime(EscrowTransaction transaction) {
        Instant now = clock.instant();
        Instant updatedAt = transaction.timestamps().updatedAt();
        return now.isBefore(updatedAt) ? updatedAt : now;
    }

    private static UUID playerId(EscrowTransaction transaction) {
        List<UUID> players = transaction.participants().stream()
                .map(participant -> participant.party())
                .filter(party -> party.type() == EscrowPartyType.PLAYER)
                .map(party -> UUID.fromString(party.id())).toList();
        if (players.size() != 1) {
            throw new IllegalArgumentException(
                    "Foreign cash transaction must have one player");
        }
        return players.get(0);
    }

    private static String detail(Exception exception) {
        String message = exception.getMessage();
        return limited(message == null || message.isBlank()
                ? exception.getClass().getSimpleName() : message);
    }

    private static String limited(String value) {
        String normalized = Objects.requireNonNull(value, "value").strip();
        if (normalized.isEmpty()) {
            normalized = "Foreign cash evidence is unknown";
        }
        return normalized.length() <= 800 ? normalized
                : normalized.substring(0, 800);
    }
}
