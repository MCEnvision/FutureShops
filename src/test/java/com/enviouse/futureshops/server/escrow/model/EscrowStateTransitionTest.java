package com.enviouse.futureshops.server.escrow.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowStateTransitionTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void transitionTableIsExhaustive() {
        Map<EscrowState, Set<EscrowState>> expected = expectedTransitions();

        assertEquals(EnumSet.allOf(EscrowState.class), expected.keySet());
        for (EscrowState source : EscrowState.values()) {
            for (EscrowState target : EscrowState.values()) {
                boolean legal = expected.get(source).contains(target);
                assertEquals(legal, source.canTransitionTo(target), source + " to " + target);
                if (legal) {
                    assertDoesNotThrow(() -> source.requireTransitionTo(target), source + " to " + target);
                } else {
                    assertThrows(
                            IllegalStateException.class,
                            () -> source.requireTransitionTo(target),
                            source + " to " + target
                    );
                }
            }
        }
    }

    @Test
    void normalCommitPathSetsDecisionAndTerminalMetadata() {
        EscrowTransaction transaction = newTransaction();

        transaction = transaction.transitionTo(EscrowState.VALIDATED, START.plusSeconds(1));
        transaction = transaction.transitionTo(EscrowState.HOLDING, START.plusSeconds(2));
        transaction = transaction.transitionTo(EscrowState.HELD, START.plusSeconds(3));
        transaction = transaction.transitionTo(EscrowState.COMMIT_DECIDED, START.plusSeconds(4));
        transaction = transaction.transitionTo(EscrowState.COMMITTED, START.plusSeconds(5));
        transaction = transaction.transitionTo(EscrowState.CLAIMS_CREATED, START.plusSeconds(6));
        transaction = transaction.transitionTo(EscrowState.COMPLETED, START.plusSeconds(7));

        assertEquals(EscrowState.COMPLETED, transaction.state());
        assertEquals(7L, transaction.revision());
        assertEquals(Optional.of(START.plusSeconds(4)), transaction.timestamps().commitDecidedAt());
        assertEquals(Optional.of(START.plusSeconds(7)), transaction.timestamps().terminalAt());
        assertTrue(transaction.state().isTerminal());
        EscrowTransaction completed = transaction;
        assertThrows(
                IllegalStateException.class,
                () -> completed.transitionTo(EscrowState.REFUND_PENDING, START.plusSeconds(8))
        );
    }

    @Test
    void normalRefundPathDoesNotCreateCommitDecision() {
        EscrowTransaction transaction = newTransaction()
                .transitionTo(EscrowState.ABORTING, START.plusSeconds(1))
                .transitionTo(EscrowState.REFUND_PENDING, START.plusSeconds(2))
                .transitionTo(EscrowState.REFUNDED, START.plusSeconds(3));

        assertEquals(EscrowState.REFUNDED, transaction.state());
        assertTrue(transaction.timestamps().commitDecidedAt().isEmpty());
        assertEquals(Optional.of(START.plusSeconds(3)), transaction.timestamps().terminalAt());
        assertTrue(transaction.state().isTerminal());
    }

    @Test
    void recoveryCanOnlyResumeItsRecordedState() {
        EscrowTransaction held = newTransaction()
                .transitionTo(EscrowState.VALIDATED, START.plusSeconds(1))
                .transitionTo(EscrowState.HOLDING, START.plusSeconds(2))
                .transitionTo(EscrowState.HELD, START.plusSeconds(3));
        EscrowError error = new EscrowError(
                "DELIVERY_FAILED",
                "Delivery failed",
                true,
                START.plusSeconds(4),
                Map.of("adapter", "inventory")
        );

        EscrowTransaction recovery = held.requireRecovery(
                error,
                3,
                START.plusSeconds(10),
                START.plusSeconds(4)
        );

        assertEquals(EscrowState.RECOVERY_REQUIRED, recovery.state());
        assertEquals(Optional.of(EscrowState.HELD), recovery.retryMetadata().resumeState());
        assertThrows(
                IllegalStateException.class,
                () -> recovery.transitionTo(EscrowState.HOLDING, START.plusSeconds(10))
        );

        EscrowTransaction resumed = recovery.transitionTo(EscrowState.HELD, START.plusSeconds(10));
        assertEquals(EscrowState.HELD, resumed.state());
        assertFalse(resumed.retryMetadata().isScheduled());
        assertEquals(1, resumed.retryMetadata().attemptCount());
        assertEquals(Optional.of(error), resumed.lastError());
    }

    @Test
    void recoveryRequiresTheDedicatedTransition() {
        EscrowTransaction held = newTransaction()
                .transitionTo(EscrowState.VALIDATED, START.plusSeconds(1))
                .transitionTo(EscrowState.HOLDING, START.plusSeconds(2));

        assertThrows(
                IllegalStateException.class,
                () -> held.transitionTo(EscrowState.RECOVERY_REQUIRED, START.plusSeconds(3))
        );
    }

    @Test
    void abortRecoveryResumesAtRefundPending() {
        EscrowTransaction aborting = newTransaction()
                .transitionTo(EscrowState.ABORTING, START.plusSeconds(1));
        EscrowError error = new EscrowError(
                "REFUND_FAILED",
                "Refund failed",
                true,
                START.plusSeconds(2),
                Map.of()
        );

        EscrowTransaction recovery = aborting.requireRecovery(
                error,
                3,
                START.plusSeconds(5),
                START.plusSeconds(2)
        );
        EscrowTransaction refund = recovery.transitionTo(
                EscrowState.REFUND_PENDING,
                START.plusSeconds(5)
        );

        assertEquals(EscrowState.REFUND_PENDING, refund.state());
        assertFalse(refund.retryMetadata().isScheduled());
    }

    @Test
    void recoveryCannotRefundAfterCommitDecision() {
        EscrowTransaction decided = newTransaction()
                .transitionTo(EscrowState.VALIDATED, START.plusSeconds(1))
                .transitionTo(EscrowState.HOLDING, START.plusSeconds(2))
                .transitionTo(EscrowState.HELD, START.plusSeconds(3))
                .transitionTo(EscrowState.COMMIT_DECIDED, START.plusSeconds(4));
        EscrowError error = new EscrowError(
                "COMMIT_FAILED",
                "Commit failed",
                true,
                START.plusSeconds(5),
                Map.of()
        );
        EscrowTransaction recovery = decided.requireRecovery(
                error,
                3,
                START.plusSeconds(10),
                START.plusSeconds(5)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> recovery.transitionTo(EscrowState.REFUND_PENDING, START.plusSeconds(10))
        );
    }

    private static Map<EscrowState, Set<EscrowState>> expectedTransitions() {
        Map<EscrowState, Set<EscrowState>> expected = new EnumMap<>(EscrowState.class);
        expected.put(EscrowState.CREATED, EnumSet.of(EscrowState.VALIDATED, EscrowState.ABORTING));
        expected.put(EscrowState.VALIDATED, EnumSet.of(EscrowState.HOLDING, EscrowState.ABORTING));
        expected.put(EscrowState.HOLDING, EnumSet.of(
                EscrowState.HELD,
                EscrowState.ABORTING,
                EscrowState.RECOVERY_REQUIRED
        ));
        expected.put(EscrowState.HELD, EnumSet.of(
                EscrowState.COMMIT_DECIDED,
                EscrowState.ABORTING,
                EscrowState.RECOVERY_REQUIRED
        ));
        expected.put(EscrowState.COMMIT_DECIDED, EnumSet.of(
                EscrowState.COMMITTED,
                EscrowState.RECOVERY_REQUIRED
        ));
        expected.put(EscrowState.COMMITTED, EnumSet.of(
                EscrowState.CLAIMS_CREATED,
                EscrowState.RECOVERY_REQUIRED
        ));
        expected.put(EscrowState.CLAIMS_CREATED, EnumSet.of(
                EscrowState.COMPLETED,
                EscrowState.RECOVERY_REQUIRED
        ));
        expected.put(EscrowState.COMPLETED, EnumSet.noneOf(EscrowState.class));
        expected.put(EscrowState.ABORTING, EnumSet.of(
                EscrowState.REFUND_PENDING,
                EscrowState.RECOVERY_REQUIRED
        ));
        expected.put(EscrowState.REFUND_PENDING, EnumSet.of(
                EscrowState.REFUNDED,
                EscrowState.RECOVERY_REQUIRED
        ));
        expected.put(EscrowState.REFUNDED, EnumSet.noneOf(EscrowState.class));
        expected.put(EscrowState.RECOVERY_REQUIRED, EnumSet.of(
                EscrowState.HOLDING,
                EscrowState.HELD,
                EscrowState.COMMIT_DECIDED,
                EscrowState.COMMITTED,
                EscrowState.CLAIMS_CREATED,
                EscrowState.REFUND_PENDING,
                EscrowState.MANUAL_REVIEW
        ));
        expected.put(EscrowState.MANUAL_REVIEW, EnumSet.of(
                EscrowState.RECOVERY_REQUIRED,
                EscrowState.REFUND_PENDING,
                EscrowState.CLAIMS_CREATED
        ));
        return expected;
    }

    private static EscrowTransaction newTransaction() {
        EscrowParty player = EscrowParty.player(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        EscrowParty recipient = EscrowParty.system("recipient");
        Set<EscrowParticipant> participants = Set.of(
                new EscrowParticipant(
                        player,
                        EnumSet.of(EscrowParticipantRole.INITIATOR, EscrowParticipantRole.PAYER)
                ),
                new EscrowParticipant(recipient, EnumSet.of(EscrowParticipantRole.BENEFICIARY))
        );
        EscrowAssetLot asset = new EscrowAssetLot(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                EscrowAssetLotType.WALLET_MONEY,
                EscrowProtectionLevel.PROTECTED,
                player,
                recipient,
                1,
                Optional.of(new MoneyAmount("futureshops:credits", 100)),
                new byte[0],
                Map.of()
        );
        return EscrowTransaction.create(
                new EscrowTransactionId(UUID.fromString("20000000-0000-0000-0000-000000000001")),
                Optional.empty(),
                new EscrowRequestKey("request-one"),
                EscrowOperation.PLAYER_PAYMENT,
                participants,
                List.of(asset),
                START,
                7,
                Optional.empty()
        );
    }
}
