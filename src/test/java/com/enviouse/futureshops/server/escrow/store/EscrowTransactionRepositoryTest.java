package com.enviouse.futureshops.server.escrow.store;

import com.enviouse.futureshops.server.escrow.model.EscrowError;
import com.enviouse.futureshops.server.escrow.model.EscrowRetryMetadata;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTimestamps;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowTransactionRepositoryTest {
    @Test
    void applyAdvancesOneValidatedRevisionAndReplaysCurrentRevision() {
        EscrowTransactionRepository repository = new EscrowTransactionRepository(10);
        EscrowTransaction created = EscrowTransactionFixtures.created("repository current");
        EscrowTransaction validated = created.transitionTo(
                EscrowState.VALIDATED, EscrowTransactionFixtures.CREATED_AT.plusSeconds(1));

        assertTrue(repository.apply(created).applied());
        EscrowStoreApplyResult advanced = repository.apply(validated);
        EscrowStoreApplyResult replayed = repository.apply(validated);

        assertTrue(advanced.applied());
        assertFalse(advanced.replayed());
        assertFalse(replayed.applied());
        assertTrue(replayed.replayed());
        assertSame(validated, replayed.transaction());
    }

    @Test
    void olderAppliedRevisionIsHarmlessAndReturnsCurrentState() {
        EscrowTransactionRepository repository = new EscrowTransactionRepository(10);
        EscrowTransaction created = EscrowTransactionFixtures.created("repository older");
        EscrowTransaction validated = created.transitionTo(
                EscrowState.VALIDATED, EscrowTransactionFixtures.CREATED_AT.plusSeconds(1));
        repository.apply(created);
        repository.apply(validated);

        EscrowStoreApplyResult replay = repository.apply(created);

        assertTrue(replay.replayed());
        assertEquals(validated, replay.transaction());
        assertEquals(validated, repository.get(created.transactionId()));
    }

    @Test
    void duplicateRequestKeyCannotCreateAnotherTransaction() {
        EscrowTransactionRepository repository = new EscrowTransactionRepository(10);
        repository.apply(EscrowTransactionFixtures.created("repository duplicate request"));
        EscrowTransaction duplicate = EscrowTransactionFixtures.created(
                UUID.fromString("cf3c0261-70e9-412a-babb-6ace6e7e75b2"),
                "repository duplicate request");

        assertThrows(EscrowStoreConflictException.class, () -> repository.apply(duplicate));
    }

    @Test
    void skippedAndIllegalStateTransitionsFailClosed() {
        EscrowTransactionRepository repository = new EscrowTransactionRepository(10);
        EscrowTransaction created = EscrowTransactionFixtures.created("repository transition");
        repository.apply(created);
        EscrowTransaction skipped = created
                .transitionTo(EscrowState.VALIDATED, EscrowTransactionFixtures.CREATED_AT.plusSeconds(1))
                .transitionTo(EscrowState.HOLDING, EscrowTransactionFixtures.CREATED_AT.plusSeconds(2));
        EscrowTransaction illegal = copyWithState(
                created,
                EscrowState.HELD,
                EscrowTransactionFixtures.CREATED_AT.plusSeconds(1),
                1L,
                Optional.empty(),
                EscrowRetryMetadata.none());

        assertThrows(EscrowStoreConflictException.class, () -> repository.apply(skipped));
        assertThrows(EscrowStoreConflictException.class, () -> repository.apply(illegal));
    }

    @Test
    void conflictingDataAtSameRevisionFailsClosed() {
        EscrowTransactionRepository repository = new EscrowTransactionRepository(10);
        EscrowTransaction created = EscrowTransactionFixtures.created("repository same revision");
        repository.apply(created);
        EscrowError error = new EscrowError(
                "UNEXPECTED",
                "Unexpected data at the same revision",
                false,
                EscrowTransactionFixtures.CREATED_AT,
                Map.of("source", "test"));
        EscrowTransaction conflict = copyWithState(
                created,
                EscrowState.CREATED,
                EscrowTransactionFixtures.CREATED_AT,
                0L,
                Optional.of(error),
                EscrowRetryMetadata.none());

        assertThrows(EscrowStoreConflictException.class, () -> repository.apply(conflict));
    }

    @Test
    void repositoryEnforcesRecordLimit() {
        EscrowTransactionRepository repository = new EscrowTransactionRepository(1);
        repository.apply(EscrowTransactionFixtures.created("repository capacity one"));
        EscrowTransaction second = EscrowTransactionFixtures.created(
                UUID.fromString("61e2fe1a-3469-4100-a8a5-e92c3fbb3063"),
                "repository capacity two");

        assertThrows(IllegalStateException.class, () -> repository.apply(second));
    }

    @Test
    void preflightAndRecoveryIndexAreBoundedAndSideEffectFree() {
        EscrowTransactionRepository repository = new EscrowTransactionRepository(10);
        EscrowTransaction later = EscrowTransactionFixtures.created(
                UUID.fromString("f0000000-0000-0000-0000-000000000001"), "later");
        EscrowTransaction earlier = EscrowTransactionFixtures.created(
                UUID.fromString("10000000-0000-0000-0000-000000000001"), "earlier");

        repository.preflight(later);
        assertEquals(0, repository.size());
        repository.apply(later);
        repository.apply(earlier);

        List<EscrowTransaction> first = repository.recoveryCandidatesAfter(Optional.empty(), 1);
        assertEquals(List.of(earlier), first);
        assertEquals(List.of(later), repository.recoveryCandidatesAfter(
                Optional.of(earlier.transactionId()), 1));
    }

    private static EscrowTransaction copyWithState(
            EscrowTransaction source,
            EscrowState state,
            Instant updatedAt,
            long revision,
            Optional<EscrowError> error,
            EscrowRetryMetadata retry
    ) {
        return new EscrowTransaction(
                source.transactionId(),
                source.parentTransactionId(),
                source.requestKey(),
                source.operation(),
                state,
                source.participants(),
                source.assetLots(),
                new EscrowTimestamps(
                        source.timestamps().createdAt(),
                        updatedAt,
                        source.timestamps().commitDecidedAt(),
                        source.timestamps().terminalAt()),
                revision,
                source.configRevision(),
                error,
                retry,
                source.shopReference());
    }
}
