package com.enviouse.futureshops.server.escrow.mint;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectedMintRepositoryTest {
    @Test
    void issueAtomicallyCreatesFullyAvailableConservedBatch() {
        ProtectedMintBatch issued = ProtectedMintBatch.issue(
                ProtectedMintTestFixtures.MINT_TRANSACTION, "mint.issue.atomic",
                25L, 64, ProtectedMintTestFixtures.SERVER,
                ProtectedMintTestFixtures.CREATED,
                ProtectedMintTestFixtures.EVIDENCE);
        ProtectedMintRepository repository = new ProtectedMintRepository();

        ProtectedMintApplyResult first = repository.issueCommitted(issued);

        assertFalse(first.replayed());
        assertEquals(0, repository.getBatch(issued.batchId()).authorizedQuantity());
        assertEquals(64, repository.getBatch(issued.batchId()).availableQuantity());
        assertEquals(1L, repository.getBatch(issued.batchId()).revision());
        assertTrue(repository.issueCommitted(issued).replayed());
        assertTrue(repository.conservation().conserved());
        assertEquals(1600L, repository.outstandingLiability()
                .outstandingValueMinorUnits());

        ProtectedMintBatch conflicting = ProtectedMintBatch.issue(
                ProtectedMintTestFixtures.MINT_TRANSACTION, "mint.issue.atomic",
                50L, 64, ProtectedMintTestFixtures.SERVER,
                ProtectedMintTestFixtures.CREATED,
                ProtectedMintTestFixtures.EVIDENCE);
        assertThrows(ProtectedMintConflictException.class,
                () -> repository.issueCommitted(conflicting));
    }

    @Test
    void planIsAuthorizedBeforeMaterializationAndReplayReusesBatchIdentity() {
        ProtectedMintBatch first = ProtectedMintTestFixtures.batch();
        ProtectedMintBatch second = ProtectedMintTestFixtures.batch();
        assertEquals(first, second);

        ProtectedMintRepository repository = new ProtectedMintRepository();
        ProtectedMintApplyResult authorized = repository.authorizeCommitted(first);
        assertFalse(authorized.replayed());
        assertTrue(repository.hasMaterializedState());
        assertEquals(10, repository.getBatch(first.batchId()).authorizedQuantity());

        ProtectedMintApplyResult replay = repository.authorizeCommitted(second);
        assertTrue(replay.replayed());
        assertEquals(first, replay.affectedBatches().get(0));

        repository.materializeCommitted(first.transactionId(), first.batchId(),
                "mint.materialize.1", 10, first.authorizedAt().plusSeconds(1));
        ProtectedMintBatch materialized = repository.getBatch(first.batchId());
        assertTrue(materialized.isFullyMaterialized());
        assertEquals(10, materialized.availableQuantity());
    }

    @Test
    void reserveAndCommitExactQuantitiesAreIdempotentAndRejectDoubleSpend() {
        ProtectedMintRepository repository = ProtectedMintTestFixtures.availableRepository();
        Instant heldAt = ProtectedMintTestFixtures.CREATED.plusSeconds(2);
        ProtectedMintApplyResult reserved = repository.reserveCommitted(
                ProtectedMintTestFixtures.HOLD_TRANSACTION,
                ProtectedMintTestFixtures.BATCH_ID, "mint.reserve.1", 4, heldAt);
        assertFalse(reserved.replayed());
        assertEquals(4, reserved.affectedBatches().get(0).reservedFor(
                ProtectedMintTestFixtures.HOLD_TRANSACTION));
        assertTrue(repository.reserveCommitted(ProtectedMintTestFixtures.HOLD_TRANSACTION,
                ProtectedMintTestFixtures.BATCH_ID, "mint.reserve.1", 4, heldAt).replayed());

        Instant spentAt = heldAt.plusSeconds(1);
        ProtectedMintApplyResult committed = repository.commitCommitted(
                ProtectedMintTestFixtures.HOLD_TRANSACTION,
                ProtectedMintTestFixtures.BATCH_ID, "mint.commit.1", 3, spentAt);
        ProtectedMintBatch afterCommit = committed.affectedBatches().get(0);
        assertEquals(6, afterCommit.availableQuantity());
        assertEquals(1, afterCommit.reservedFor(ProtectedMintTestFixtures.HOLD_TRANSACTION));
        assertEquals(3, afterCommit.spentFor(ProtectedMintTestFixtures.HOLD_TRANSACTION));
        assertTrue(repository.commitCommitted(ProtectedMintTestFixtures.HOLD_TRANSACTION,
                ProtectedMintTestFixtures.BATCH_ID, "mint.commit.1", 3, spentAt).replayed());
        assertThrows(ProtectedMintConflictException.class,
                () -> repository.commitCommitted(ProtectedMintTestFixtures.HOLD_TRANSACTION,
                        ProtectedMintTestFixtures.BATCH_ID, "mint.commit.too.much", 2,
                        spentAt.plusSeconds(1)));
        assertThrows(ProtectedMintConflictException.class,
                () -> repository.reserveCommitted(ProtectedMintTestFixtures.HOLD_TRANSACTION,
                        ProtectedMintTestFixtures.BATCH_ID, "mint.reserve.1", 3, heldAt));

        repository.reserveCommitted(ProtectedMintTestFixtures.HOLD_TRANSACTION,
                ProtectedMintTestFixtures.BATCH_ID, "mint.reserve.remaining", 6,
                spentAt.plusSeconds(1));
        repository.commitCommitted(ProtectedMintTestFixtures.HOLD_TRANSACTION,
                ProtectedMintTestFixtures.BATCH_ID, "mint.commit.remaining", 7,
                spentAt.plusSeconds(2));
        ProtectedMintBatch spent = repository.getBatch(ProtectedMintTestFixtures.BATCH_ID);
        assertEquals(ProtectedMintValidationCode.ALREADY_SPENT,
                repository.validate(spent.batchId(), spent.denominationMinorUnits(),
                        spent.authorizedCount(), spent.serverIdentityEvidence(),
                        spent.checksumEvidence(), 1, Optional.empty()).code());
        assertTrue(repository.conservation().conserved());
    }

    @Test
    void independentReservationsAreExactAndWrongTransactionsCannotClaimThem() {
        ProtectedMintRepository repository = ProtectedMintTestFixtures.availableRepository();
        repository.reserveCommitted(ProtectedMintTestFixtures.HOLD_TRANSACTION,
                ProtectedMintTestFixtures.BATCH_ID, "mint.reserve.first", 3,
                ProtectedMintTestFixtures.CREATED.plusSeconds(2));
        repository.reserveCommitted(ProtectedMintTestFixtures.SECOND_HOLD_TRANSACTION,
                ProtectedMintTestFixtures.BATCH_ID, "mint.reserve.second", 2,
                ProtectedMintTestFixtures.CREATED.plusSeconds(3));
        ProtectedMintBatch held = repository.getBatch(ProtectedMintTestFixtures.BATCH_ID);
        assertEquals(5, held.availableQuantity());
        assertEquals(3, held.reservedFor(ProtectedMintTestFixtures.HOLD_TRANSACTION));
        assertEquals(2, held.reservedFor(ProtectedMintTestFixtures.SECOND_HOLD_TRANSACTION));
        assertTrue(repository.validate(held.batchId(), held.denominationMinorUnits(),
                held.authorizedCount(), held.serverIdentityEvidence(), held.checksumEvidence(),
                3, Optional.of(ProtectedMintTestFixtures.HOLD_TRANSACTION)).valid());
        assertEquals(ProtectedMintValidationCode.NOT_AVAILABLE,
                repository.validate(held.batchId(), held.denominationMinorUnits(),
                        held.authorizedCount(), held.serverIdentityEvidence(),
                        held.checksumEvidence(), 3,
                        Optional.of(ProtectedMintTestFixtures.SECOND_HOLD_TRANSACTION)).code());
        assertEquals(ProtectedMintValidationCode.NOT_AVAILABLE,
                repository.validate(held.batchId(), held.denominationMinorUnits(),
                        held.authorizedCount(), held.serverIdentityEvidence(),
                        held.checksumEvidence(), 1, Optional.of(UUID.randomUUID())).code());
        assertThrows(ProtectedMintConflictException.class,
                () -> repository.reserveCommitted(UUID.randomUUID(), held.batchId(),
                        "mint.reserve.overdraw", 6,
                        ProtectedMintTestFixtures.CREATED.plusSeconds(4)));
        assertEquals(5, repository.getBatch(held.batchId()).availableQuantity());
        assertThrows(ProtectedMintConflictException.class,
                () -> repository.reserveCommitted(UUID.randomUUID(), UUID.randomUUID(),
                        "mint.reserve.unknown", 1,
                        ProtectedMintTestFixtures.CREATED.plusSeconds(4)));
    }

    @Test
    void refundCreatesAnExactReplacementWithoutRevivingOriginalQuantity() {
        ProtectedMintRepository repository = ProtectedMintTestFixtures.availableRepository();
        Instant heldAt = ProtectedMintTestFixtures.CREATED.plusSeconds(2);
        repository.reserveCommitted(ProtectedMintTestFixtures.HOLD_TRANSACTION,
                ProtectedMintTestFixtures.BATCH_ID, "mint.reserve.refund", 4, heldAt);
        ProtectedMintBatch source = repository.getBatch(ProtectedMintTestFixtures.BATCH_ID);
        UUID replacementBatchId =
                UUID.fromString("10000000-0000-0000-0000-000000000099");
        ProtectedMintBatch replacement = ProtectedMintBatch.replacement(replacementBatchId,
                ProtectedMintTestFixtures.HOLD_TRANSACTION, "mint.refund.1", source, 2,
                ProtectedMintTestFixtures.SERVER, heldAt.plusSeconds(1),
                ProtectedMintTestFixtures.EVIDENCE);
        ProtectedMintBatch recoveryPlan = ProtectedMintBatch.replacement(replacementBatchId,
                ProtectedMintTestFixtures.HOLD_TRANSACTION, "mint.refund.1", source, 2,
                ProtectedMintTestFixtures.SERVER, heldAt.plusSeconds(1),
                ProtectedMintTestFixtures.EVIDENCE);
        assertEquals(replacement, recoveryPlan);

        ProtectedMintApplyResult refund = repository.refundCommitted(
                ProtectedMintTestFixtures.HOLD_TRANSACTION,
                ProtectedMintTestFixtures.BATCH_ID, "mint.refund.1",
                ProtectedMintState.RESERVED, 2, replacement, heldAt.plusSeconds(1));
        assertEquals(2, refund.affectedBatches().get(0).refundedQuantity());
        assertEquals(2, refund.affectedBatches().get(0).reservedFor(
                ProtectedMintTestFixtures.HOLD_TRANSACTION));
        assertEquals(2, refund.replacementBatches().get(0).authorizedQuantity());
        assertTrue(repository.refundCommitted(ProtectedMintTestFixtures.HOLD_TRANSACTION,
                ProtectedMintTestFixtures.BATCH_ID, "mint.refund.1",
                ProtectedMintState.RESERVED, 2, replacement,
                heldAt.plusSeconds(1)).replayed());
        assertNotEquals(source.batchId(), replacement.batchId());

        ProtectedMintBatch oversizedReplacement = ProtectedMintBatch.replacement(
                UUID.fromString("10000000-0000-0000-0000-000000000098"),
                ProtectedMintTestFixtures.HOLD_TRANSACTION, "mint.refund.2", source, 3,
                ProtectedMintTestFixtures.SERVER, heldAt.plusSeconds(2),
                ProtectedMintTestFixtures.EVIDENCE);
        assertThrows(ProtectedMintConflictException.class,
                () -> repository.refundCommitted(ProtectedMintTestFixtures.HOLD_TRANSACTION,
                        ProtectedMintTestFixtures.BATCH_ID, "mint.refund.2",
                        ProtectedMintState.RESERVED, 3, oversizedReplacement,
                        heldAt.plusSeconds(2)));
        assertNull(repository.getBatch(oversizedReplacement.batchId()));
        assertTrue(repository.conservation().conserved());
        assertEquals(10L, repository.outstandingLiability().outstandingUnits());
    }

    @Test
    void spentRefundIsCompensatingAndNeverMakesSpentQuantityAvailableAgain() {
        ProtectedMintRepository repository = ProtectedMintTestFixtures.availableRepository();
        Instant heldAt = ProtectedMintTestFixtures.CREATED.plusSeconds(2);
        repository.reserveCommitted(ProtectedMintTestFixtures.HOLD_TRANSACTION,
                ProtectedMintTestFixtures.BATCH_ID, "mint.reserve.compensation", 3, heldAt);
        repository.commitCommitted(ProtectedMintTestFixtures.HOLD_TRANSACTION,
                ProtectedMintTestFixtures.BATCH_ID, "mint.commit.compensation", 3,
                heldAt.plusSeconds(1));
        ProtectedMintBatch source = repository.getBatch(ProtectedMintTestFixtures.BATCH_ID);
        ProtectedMintBatch replacement = ProtectedMintBatch.replacement(
                UUID.fromString("10000000-0000-0000-0000-000000000097"),
                ProtectedMintTestFixtures.HOLD_TRANSACTION, "mint.refund.compensation",
                source, 2, ProtectedMintTestFixtures.SERVER, heldAt.plusSeconds(2),
                ProtectedMintTestFixtures.EVIDENCE);
        repository.refundCommitted(ProtectedMintTestFixtures.HOLD_TRANSACTION,
                source.batchId(), "mint.refund.compensation", ProtectedMintState.SPENT,
                2, replacement, heldAt.plusSeconds(2));

        ProtectedMintBatch compensated = repository.getBatch(source.batchId());
        assertEquals(7, compensated.availableQuantity());
        assertEquals(1, compensated.spentFor(ProtectedMintTestFixtures.HOLD_TRANSACTION));
        assertEquals(2, compensated.refundedQuantity());
        assertEquals(ProtectedMintValidationCode.NOT_AVAILABLE,
                repository.validate(compensated.batchId(), compensated.denominationMinorUnits(),
                        compensated.authorizedCount(), compensated.serverIdentityEvidence(),
                        compensated.checksumEvidence(), 1,
                        Optional.of(ProtectedMintTestFixtures.HOLD_TRANSACTION)).code());
        assertEquals(2, repository.getBatch(replacement.batchId()).authorizedQuantity());
        assertTrue(repository.conservation().conserved());
    }

    @Test
    void unknownAndTamperedEvidenceAreRejectedWhileReservationsDoNotExpire() {
        ProtectedMintRepository repository = ProtectedMintTestFixtures.availableRepository();
        ProtectedMintBatch batch = repository.getBatch(ProtectedMintTestFixtures.BATCH_ID);
        assertEquals(ProtectedMintValidationCode.UNKNOWN_MINT,
                repository.validate(UUID.randomUUID(), batch.denominationMinorUnits(),
                        batch.authorizedCount(), batch.serverIdentityEvidence(),
                        batch.checksumEvidence(), 1, Optional.empty()).code());
        assertEquals(ProtectedMintValidationCode.DENOMINATION_MISMATCH,
                repository.validate(batch.batchId(), 99L, batch.authorizedCount(),
                        batch.serverIdentityEvidence(), batch.checksumEvidence(), 1,
                        Optional.empty()).code());
        assertEquals(ProtectedMintValidationCode.SERVER_IDENTITY_MISMATCH,
                repository.validate(batch.batchId(), batch.denominationMinorUnits(),
                        batch.authorizedCount(), "other-server", batch.checksumEvidence(),
                        1, Optional.empty()).code());
        assertEquals(ProtectedMintValidationCode.CHECKSUM_MISMATCH,
                repository.validate(batch.batchId(), batch.denominationMinorUnits(),
                        batch.authorizedCount(), batch.serverIdentityEvidence(), "forged",
                        1, Optional.empty()).code());
        repository.reserveCommitted(ProtectedMintTestFixtures.HOLD_TRANSACTION,
                batch.batchId(), "mint.reserve.long.held", 4,
                ProtectedMintTestFixtures.CREATED.plusSeconds(2));
        ProtectedMintBatch held = repository.getBatch(batch.batchId());
        assertTrue(repository.validate(held.batchId(), held.denominationMinorUnits(),
                held.authorizedCount(), held.serverIdentityEvidence(), held.checksumEvidence(),
                4, Optional.of(ProtectedMintTestFixtures.HOLD_TRANSACTION)).valid());
        assertEquals(ProtectedMintValidationCode.NOT_AVAILABLE,
                repository.validate(held.batchId(), held.denominationMinorUnits(),
                        held.authorizedCount(), held.serverIdentityEvidence(),
                        held.checksumEvidence(), 1,
                        Optional.of(ProtectedMintTestFixtures.SECOND_HOLD_TRANSACTION)).code());
    }

    @Test
    void quarantineSupportsAuthorizedAvailableAndReservedSourcesExactly() {
        ProtectedMintRepository repository = new ProtectedMintRepository();
        ProtectedMintBatch batch = ProtectedMintTestFixtures.batch();
        repository.authorizeCommitted(batch);
        repository.quarantineCommitted(UUID.randomUUID(), batch.batchId(),
                "mint.quarantine.authorized", ProtectedMintState.AUTHORIZED, 2,
                batch.authorizedAt().plusSeconds(1));
        repository.materializeCommitted(batch.transactionId(), batch.batchId(),
                "mint.materialize.after.quarantine", 8,
                batch.authorizedAt().plusSeconds(2));
        repository.quarantineCommitted(UUID.randomUUID(), batch.batchId(),
                "mint.quarantine.available", ProtectedMintState.AVAILABLE, 1,
                batch.authorizedAt().plusSeconds(3));
        repository.reserveCommitted(ProtectedMintTestFixtures.HOLD_TRANSACTION,
                batch.batchId(), "mint.reserve.quarantine", 3,
                batch.authorizedAt().plusSeconds(4));
        repository.quarantineCommitted(ProtectedMintTestFixtures.HOLD_TRANSACTION,
                batch.batchId(), "mint.quarantine.reserved", ProtectedMintState.RESERVED,
                1, batch.authorizedAt().plusSeconds(5));

        ProtectedMintBatch quarantined = repository.getBatch(batch.batchId());
        assertEquals(4, quarantined.availableQuantity());
        assertEquals(2, quarantined.reservedFor(ProtectedMintTestFixtures.HOLD_TRANSACTION));
        assertEquals(4, quarantined.quarantinedQuantity());
        assertTrue(repository.conservation().conserved());
    }

    @Test
    void preflightIsSideEffectFreeAndRequestKeyReuseFailsClosed() {
        ProtectedMintRepository repository = new ProtectedMintRepository();
        ProtectedMintBatch batch = ProtectedMintTestFixtures.batch();
        ProtectedMintJournalEvent authorize = ProtectedMintJournalEvent.authorize(batch);
        ProtectedMintApplyResult predicted = repository.preflightCommitted(authorize);
        assertFalse(predicted.replayed());
        assertNull(repository.getBatch(batch.batchId()));
        assertNull(repository.receiptForRequest(batch.authorizeRequestKey()));
        repository.applyCommitted(authorize);

        ProtectedMintJournalEvent materialize = ProtectedMintJournalEvent.materialize(
                batch.transactionId(), batch.batchId(), "mint.materialize.preflight", 10,
                batch.authorizedAt().plusSeconds(1));
        ProtectedMintBatch before = repository.getBatch(batch.batchId());
        repository.preflightCommitted(materialize);
        assertEquals(before, repository.getBatch(batch.batchId()));
        assertNull(repository.receiptForRequest(materialize.requestKey()));
        repository.applyCommitted(materialize);

        repository.reserveCommitted(ProtectedMintTestFixtures.HOLD_TRANSACTION,
                batch.batchId(), "mint.unique.request", 1,
                batch.authorizedAt().plusSeconds(2));
        assertThrows(ProtectedMintConflictException.class,
                () -> repository.commitCommitted(ProtectedMintTestFixtures.HOLD_TRANSACTION,
                        batch.batchId(), "mint.unique.request", 1,
                        batch.authorizedAt().plusSeconds(3)));
        assertThrows(IllegalStateException.class,
                () -> batch.commit(ProtectedMintTestFixtures.HOLD_TRANSACTION, 1,
                        batch.authorizedAt().plusSeconds(1)));
    }

    @Test
    void conservationAuditRemainsBoundedAtNontrivialScale() {
        ProtectedMintRepository repository = new ProtectedMintRepository();
        Instant created = ProtectedMintTestFixtures.CREATED;
        int batchCount = 2_000;
        for (int index = 0; index < batchCount; index++) {
            UUID batchId = stableUuid("batch." + index);
            UUID mintTransaction = stableUuid("mint." + index);
            UUID holdTransaction = stableUuid("hold." + index);
            ProtectedMintBatch batch = ProtectedMintBatch.plan(batchId, mintTransaction,
                    "mint.scale.authorize." + index, 25L, 4,
                    ProtectedMintTestFixtures.SERVER, created,
                    ProtectedMintTestFixtures.EVIDENCE);
            repository.authorizeCommitted(batch);
            repository.materializeCommitted(mintTransaction, batchId,
                    "mint.scale.materialize." + index, 4, created.plusSeconds(1));
            repository.reserveCommitted(holdTransaction, batchId,
                    "mint.scale.reserve." + index, 2, created.plusSeconds(2));
            repository.commitCommitted(holdTransaction, batchId,
                    "mint.scale.commit." + index, 1, created.plusSeconds(3));
        }

        ProtectedMintConservationReport report = assertTimeout(Duration.ofSeconds(5),
                repository::conservation);
        assertTrue(report.conserved(), report.violations().toString());
        assertEquals(8_000L, report.issuedUnits());
        assertEquals(4_000L,
                report.unitsByState().getOrDefault(ProtectedMintState.AVAILABLE, 0L));
        assertEquals(2_000L,
                report.unitsByState().getOrDefault(ProtectedMintState.RESERVED, 0L));
        assertEquals(2_000L,
                report.unitsByState().getOrDefault(ProtectedMintState.SPENT, 0L));
    }

    private static UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
