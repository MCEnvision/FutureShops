package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchEntry;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryExecutionResult;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryExecutionStatus;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommand;
import com.enviouse.futureshops.server.escrow.stock.StockReservation;
import com.enviouse.futureshops.server.escrow.stock.StockReservationState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerShopBarterServiceTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void freshTradePreparesBeforeCustodyAndCommitsAllOutputsAtomically() {
        List<String> events = new ArrayList<>();
        ServerShopBarterService.PreparedRequest request =
                ServerShopBarterTestFixtures.request();
        FakeBackend backend = new FakeBackend(events);
        FakeCustody custody = new FakeCustody(request, events);

        ServerShopBarterService.Result result =
                ServerShopBarterService.execute(
                        request, backend, custody);

        assertTrue(result.success());
        assertFalse(result.replayed());
        assertEquals(List.of("prepare", "extract", "commit"), events);
        assertEquals(ServerShopBarterIntent.Status.COMMITTED,
                backend.intent.orElseThrow().status());
        assertEquals(2, backend.reservations.size());
        assertTrue(backend.reservations.stream().allMatch(value ->
                value.state() == StockReservationState.COMMITTED));
        assertEquals(3, backend.claims.size());
        assertEquals(3, result.outputClaims().size());
    }

    @Test
    void completedReplayResolvesBeforeReadinessAndPerformsNoMutation() {
        List<String> events = new ArrayList<>();
        ServerShopBarterService.PreparedRequest request =
                ServerShopBarterTestFixtures.request();
        FakeBackend backend = new FakeBackend(events);
        FakeCustody custody = new FakeCustody(request, events);
        assertTrue(ServerShopBarterService.execute(
                request, backend, custody).success());
        events.clear();
        backend.ready = false;

        ServerShopBarterService.Result replay =
                ServerShopBarterService.execute(
                        request, backend, custody);

        assertTrue(replay.success());
        assertTrue(replay.replayed());
        assertTrue(events.isEmpty());
    }

    @Test
    void changedMultiplierAndRecipeRevisionConflictWithStoredIntent() {
        ServerShopBarterService.PreparedRequest request =
                ServerShopBarterTestFixtures.request();
        FakeBackend backend = new FakeBackend(new ArrayList<>());
        FakeCustody custody = new FakeCustody(request,
                new ArrayList<>());
        assertTrue(ServerShopBarterService.execute(
                request, backend, custody).success());
        ServerShopBarterService.PreparedRequest multiplierConflict =
                ServerShopBarterTestFixtures.request(
                        request.identity().requestId(),
                        request.identity().recipeId(), 3,
                        request.quoteRevision(), request.recipeRevision());
        ServerShopBarterService.PreparedRequest revisionConflict =
                ServerShopBarterTestFixtures.request(
                        request.identity().requestId(),
                        request.identity().recipeId(), 2,
                        request.quoteRevision(),
                        request.recipeRevision() + 1L);

        assertEquals(ServerShopBarterService.Status.REQUEST_CONFLICT,
                ServerShopBarterService.execute(multiplierConflict,
                        backend, custody).status());
        assertEquals(ServerShopBarterService.Status.REQUEST_CONFLICT,
                ServerShopBarterService.execute(revisionConflict,
                        backend, custody).status());
    }

    @Test
    void stockFailuresHappenBeforeIntentAndIngredientCustody() {
        ServerShopBarterService.PreparedRequest request =
                ServerShopBarterTestFixtures.request();
        FakeBackend unavailable = new FakeBackend(new ArrayList<>());
        unavailable.prepareDisposition =
                ServerShopBarterService.PrepareDisposition
                        .STOCK_UNAVAILABLE;
        FakeCustody firstCustody = new FakeCustody(request,
                new ArrayList<>());
        FakeBackend changed = new FakeBackend(new ArrayList<>());
        changed.prepareDisposition =
                ServerShopBarterService.PrepareDisposition.STOCK_CHANGED;
        FakeCustody secondCustody = new FakeCustody(request,
                new ArrayList<>());

        assertEquals(ServerShopBarterService.Status.STOCK_UNAVAILABLE,
                ServerShopBarterService.execute(request, unavailable,
                        firstCustody).status());
        assertEquals(ServerShopBarterService.Status.STOCK_CHANGED,
                ServerShopBarterService.execute(request, changed,
                        secondCustody).status());
        assertEquals(0, firstCustody.extractCalls);
        assertEquals(0, secondCustody.extractCalls);
        assertTrue(unavailable.intent.isEmpty());
        assertTrue(changed.intent.isEmpty());
    }

    @Test
    void committedCustodyResumesStoredIntentWithoutRepeatingStockGate() {
        List<String> events = new ArrayList<>();
        ServerShopBarterService.PreparedRequest request =
                ServerShopBarterTestFixtures.request();
        FakeBackend backend = new FakeBackend(events);
        ServerShopBarterIntent intent =
                ServerShopBarterIntent.prepared(request);
        backend.prepare(intent, intent.stockReservation());
        FakeCustody custody = new FakeCustody(request, events);
        custody.state = ServerShopBarterItemCustody.State.COMMITTED;
        backend.ready = false;
        events.clear();

        ServerShopBarterService.Result result =
                ServerShopBarterService.execute(
                        request, backend, custody);

        assertTrue(result.success());
        assertTrue(result.replayed());
        assertEquals(List.of("commit"), events);
        assertEquals(0, custody.extractCalls);
        assertEquals(1, backend.prepareCalls);
    }

    @Test
    void preparedCustodyCompletesThroughTheSameStoredIntent() {
        List<String> events = new ArrayList<>();
        ServerShopBarterService.PreparedRequest request =
                ServerShopBarterTestFixtures.request();
        FakeBackend backend = new FakeBackend(events);
        ServerShopBarterIntent intent =
                ServerShopBarterIntent.prepared(request);
        backend.prepare(intent, intent.stockReservation());
        FakeCustody custody = new FakeCustody(request, events);
        custody.state = ServerShopBarterItemCustody.State.PREPARED;
        custody.executionStatus = ItemInventoryExecutionStatus.REPLAYED;
        events.clear();

        ServerShopBarterService.Result result =
                ServerShopBarterService.execute(
                        request, backend, custody);

        assertTrue(result.success());
        assertTrue(result.replayed());
        assertEquals(List.of("extract", "commit"), events);
        assertEquals(1, custody.extractCalls);
    }

    @Test
    void postCustodyCommitConflictFailsClosedThenForwardsOnRetry() {
        List<String> events = new ArrayList<>();
        ServerShopBarterService.PreparedRequest request =
                ServerShopBarterTestFixtures.request();
        FakeBackend backend = new FakeBackend(events);
        backend.commitDisposition =
                ServerShopBarterService.CommitDisposition.CONFLICT;
        FakeCustody custody = new FakeCustody(request, events);

        ServerShopBarterService.Result failed =
                ServerShopBarterService.execute(
                        request, backend, custody);

        assertEquals(ServerShopBarterService.Status.RECOVERY_REQUIRED,
                failed.status());
        assertEquals(ServerShopBarterIntent.Status.PREPARED,
                backend.intent.orElseThrow().status());
        assertTrue(backend.claims.isEmpty());
        assertTrue(backend.transaction.isEmpty());
        assertTrue(backend.reservations.stream().allMatch(value ->
                value.state() == StockReservationState.HELD));
        assertEquals(ServerShopBarterItemCustody.State.COMMITTED,
                custody.state);
        backend.commitDisposition =
                ServerShopBarterService.CommitDisposition.APPLIED;
        backend.ready = false;
        events.clear();

        ServerShopBarterService.Result recovered =
                ServerShopBarterService.execute(
                        request, backend, custody);

        assertTrue(recovered.success());
        assertTrue(recovered.replayed());
        assertEquals(List.of("commit"), events);
        assertEquals(1, custody.extractCalls);
    }

    @Test
    void missingAndPartialMaterializationEvidenceRequiresRecovery() {
        ServerShopBarterService.PreparedRequest request =
                ServerShopBarterTestFixtures.request();
        FakeBackend missingClaim = new FakeBackend(new ArrayList<>());
        FakeCustody committedCustody = new FakeCustody(request,
                new ArrayList<>());
        assertTrue(ServerShopBarterService.execute(request, missingClaim,
                committedCustody).success());
        missingClaim.claims.remove(0);

        assertEquals(ServerShopBarterService.Status.RECOVERY_REQUIRED,
                ServerShopBarterService.resolveReplay(request,
                        missingClaim, committedCustody).orElseThrow()
                        .status());

        FakeBackend orphanClaim = new FakeBackend(new ArrayList<>());
        orphanClaim.claims.addAll(
                ServerShopBarterTestFixtures.commit().outputClaims());
        FakeCustody noCustody = new FakeCustody(request,
                new ArrayList<>());
        assertEquals(ServerShopBarterService.Status.RECOVERY_REQUIRED,
                ServerShopBarterService.resolveReplay(request,
                        orphanClaim, noCustody).orElseThrow().status());

        FakeBackend missingCustody = new FakeBackend(new ArrayList<>());
        FakeCustody removedCustody = new FakeCustody(request,
                new ArrayList<>());
        assertTrue(ServerShopBarterService.execute(request,
                missingCustody, removedCustody).success());
        removedCustody.state =
                ServerShopBarterItemCustody.State.NONE;
        assertEquals(ServerShopBarterService.Status.RECOVERY_REQUIRED,
                ServerShopBarterService.resolveReplay(request,
                        missingCustody, removedCustody).orElseThrow()
                        .status());
    }

    @Test
    void stockEvidenceConflictStopsBeforeCustody() {
        ServerShopBarterService.PreparedRequest request =
                ServerShopBarterTestFixtures.request();
        FakeBackend backend = new FakeBackend(new ArrayList<>());
        ServerShopBarterIntent intent =
                ServerShopBarterIntent.prepared(request);
        backend.prepare(intent, intent.stockReservation());
        backend.reservations.set(0,
                backend.reservations.get(0).resolve(
                        StockReservationState.RELEASED,
                        request.quoteCreatedAt()));
        FakeCustody custody = new FakeCustody(request,
                new ArrayList<>());

        assertEquals(ServerShopBarterService.Status.RECOVERY_REQUIRED,
                ServerShopBarterService.execute(
                        request, backend, custody).status());
        assertEquals(0, custody.extractCalls);
    }

    @Test
    void missingIngredientsAbortIntentAndReleaseWholeStockBatch() {
        List<String> events = new ArrayList<>();
        ServerShopBarterService.PreparedRequest request =
                ServerShopBarterTestFixtures.request();
        FakeBackend backend = new FakeBackend(events);
        FakeCustody custody = new FakeCustody(request, events);
        custody.executionStatus =
                ItemInventoryExecutionStatus.INSUFFICIENT_ITEMS;

        ServerShopBarterService.Result result =
                ServerShopBarterService.execute(
                        request, backend, custody);

        assertEquals(ServerShopBarterService.Status.MISSING_INGREDIENTS,
                result.status());
        assertEquals(List.of("prepare", "extract", "abort"), events);
        assertEquals(ServerShopBarterIntent.Status
                        .ABORTED_MISSING_INGREDIENTS,
                backend.intent.orElseThrow().status());
        assertTrue(backend.reservations.stream().allMatch(value ->
                value.state() == StockReservationState.RELEASED));
        assertTrue(backend.claims.isEmpty());
        assertTrue(backend.transaction.isEmpty());
        events.clear();
        assertEquals(ServerShopBarterService.Status.MISSING_INGREDIENTS,
                ServerShopBarterService.execute(
                        request, backend, custody).status());
        assertTrue(events.isEmpty());
    }

    @Test
    void unsupportedIngredientStackAbortsBeforeAnyOutputExists() {
        ServerShopBarterService.PreparedRequest request =
                ServerShopBarterTestFixtures.request();
        FakeBackend backend = new FakeBackend(new ArrayList<>());
        FakeCustody custody = new FakeCustody(request,
                new ArrayList<>());
        custody.executionStatus =
                ItemInventoryExecutionStatus.UNSUPPORTED_STACK;

        ServerShopBarterService.Result result =
                ServerShopBarterService.execute(
                        request, backend, custody);

        assertEquals(ServerShopBarterService.Status.UNSUPPORTED_ITEM,
                result.status());
        assertEquals(ServerShopBarterIntent.Status
                        .ABORTED_UNSUPPORTED_ITEM,
                backend.intent.orElseThrow().status());
        assertTrue(backend.reservations.stream().allMatch(value ->
                value.state() == StockReservationState.RELEASED));
        assertTrue(backend.claims.isEmpty());
        assertTrue(backend.transaction.isEmpty());
    }

    @Test
    void abortedAndQuarantinedCustodyNeverMaterializeOutputs() {
        ServerShopBarterService.PreparedRequest request =
                ServerShopBarterTestFixtures.request();
        FakeBackend abortedBackend = new FakeBackend(new ArrayList<>());
        ServerShopBarterIntent abortedIntent =
                ServerShopBarterIntent.prepared(request);
        abortedBackend.prepare(abortedIntent,
                abortedIntent.stockReservation());
        FakeCustody aborted = new FakeCustody(request,
                new ArrayList<>());
        aborted.state = ServerShopBarterItemCustody.State.ABORTED;

        assertEquals(ServerShopBarterService.Status.ITEM_CUSTODY_ABORTED,
                ServerShopBarterService.execute(request,
                        abortedBackend, aborted).status());
        assertTrue(abortedBackend.claims.isEmpty());

        FakeBackend quarantinedBackend = new FakeBackend(
                new ArrayList<>());
        ServerShopBarterIntent quarantinedIntent =
                ServerShopBarterIntent.prepared(request);
        quarantinedBackend.prepare(quarantinedIntent,
                quarantinedIntent.stockReservation());
        FakeCustody quarantined = new FakeCustody(request,
                new ArrayList<>());
        quarantined.state =
                ServerShopBarterItemCustody.State.QUARANTINED;

        assertEquals(ServerShopBarterService.Status.RECOVERY_REQUIRED,
                ServerShopBarterService.execute(request,
                        quarantinedBackend, quarantined).status());
        assertTrue(quarantinedBackend.claims.isEmpty());
        assertTrue(quarantinedBackend.reservations.stream().allMatch(
                value -> value.state() == StockReservationState.HELD));
    }

    private static final class FakeBackend
            implements ServerShopBarterService.Backend {
        private final List<String> events;
        private boolean ready = true;
        private Optional<ServerShopBarterIntent> intent = Optional.empty();
        private Optional<EscrowTransaction> transaction = Optional.empty();
        private final List<EscrowClaim> claims = new ArrayList<>();
        private final List<StockReservation> reservations =
                new ArrayList<>();
        private ServerShopBarterService.PrepareDisposition
                prepareDisposition =
                ServerShopBarterService.PrepareDisposition.APPLIED;
        private ServerShopBarterService.CommitDisposition
                commitDisposition =
                ServerShopBarterService.CommitDisposition.APPLIED;
        private ServerShopBarterService.TransitionDisposition
                abortDisposition =
                ServerShopBarterService.TransitionDisposition.APPLIED;
        private int prepareCalls;

        private FakeBackend(List<String> events) {
            this.events = events;
        }

        @Override
        public boolean ready() {
            return ready;
        }

        @Override
        public Optional<ServerShopBarterIntent> intent(UUID requestId) {
            return intent.filter(value -> value.requestId().equals(
                    requestId));
        }

        @Override
        public Optional<EscrowTransaction> transaction(
                UUID transactionId
        ) {
            return transaction.filter(value -> value.transactionId()
                    .value().equals(transactionId));
        }

        @Override
        public List<EscrowClaim> claimsForTransaction(
                UUID transactionId
        ) {
            return claims.stream().filter(value -> value.transactionId()
                    .equals(transactionId)).toList();
        }

        @Override
        public List<StockReservation> stockReservations(
                UUID transactionId
        ) {
            return reservations.stream().filter(value ->
                    value.transactionId().equals(transactionId)).toList();
        }

        @Override
        public ServerShopBarterService.PrepareDisposition prepare(
                ServerShopBarterIntent prepared,
                StockMutationCommand.ReserveBatch reservation
        ) {
            events.add("prepare");
            prepareCalls++;
            if (prepareDisposition
                    != ServerShopBarterService.PrepareDisposition.APPLIED
                    && prepareDisposition
                    != ServerShopBarterService.PrepareDisposition.REPLAYED) {
                return prepareDisposition;
            }
            if (intent.isPresent()) {
                return intent.orElseThrow().intentFingerprint().equals(
                        prepared.intentFingerprint())
                        ? ServerShopBarterService.PrepareDisposition.REPLAYED
                        : ServerShopBarterService.PrepareDisposition.CONFLICT;
            }
            intent = Optional.of(prepared);
            reservations.clear();
            reservation.reservations().forEach(value ->
                    reservations.add(StockReservation.held(
                            reservation.transactionId(), value.stockKey(),
                            value.direction(), value.quantity(), true,
                            reservation.appliedAt())));
            return prepareDisposition;
        }

        @Override
        public ServerShopBarterService.TransitionDisposition abort(
                ServerShopBarterIntent terminalIntent,
                StockMutationCommand.ResolveBatch release
        ) {
            events.add("abort");
            if (abortDisposition
                    != ServerShopBarterService.TransitionDisposition.APPLIED
                    && abortDisposition
                    != ServerShopBarterService.TransitionDisposition
                    .REPLAYED) {
                return abortDisposition;
            }
            intent = Optional.of(terminalIntent);
            for (int index = 0; index < reservations.size(); index++) {
                StockReservation reservation = reservations.get(index);
                if (reservation.state() == StockReservationState.HELD) {
                    reservations.set(index, reservation.resolve(
                            StockReservationState.RELEASED,
                            release.appliedAt()));
                }
            }
            return abortDisposition;
        }

        @Override
        public ServerShopBarterService.CommitDisposition commit(
                ServerShopBarterIntent completedIntent,
                ServerShopBarterCommit commit
        ) {
            events.add("commit");
            if (commitDisposition
                    != ServerShopBarterService.CommitDisposition.APPLIED
                    && commitDisposition
                    != ServerShopBarterService.CommitDisposition.REPLAYED) {
                return commitDisposition;
            }
            intent = Optional.of(completedIntent);
            transaction = Optional.of(commit.completedTransaction());
            claims.clear();
            claims.addAll(commit.outputClaims());
            for (int index = 0; index < reservations.size(); index++) {
                StockReservation reservation = reservations.get(index);
                if (reservation.state() == StockReservationState.HELD) {
                    reservations.set(index, reservation.resolve(
                            StockReservationState.COMMITTED,
                            commit.stockCommit().appliedAt()));
                }
            }
            return commitDisposition;
        }
    }

    private static final class FakeCustody
            implements ServerShopBarterItemCustody {
        private final ItemInventoryMutationReceipt receipt;
        private final List<String> events;
        private State state = State.NONE;
        private ItemInventoryExecutionStatus executionStatus =
                ItemInventoryExecutionStatus.APPLIED;
        private int extractCalls;

        private FakeCustody(
                ServerShopBarterService.PreparedRequest request,
                List<String> events
        ) {
            this.receipt = ServerShopBarterTestFixtures.receipt(request,
                    ServerShopBarterTestFixtures.APPLIED_AT);
            this.events = events;
        }

        @Override
        public Inspection inspect(UUID requestId) {
            return state == State.NONE ? Inspection.none()
                    : new Inspection(state, Optional.of(receipt));
        }

        @Override
        public ItemInventoryExecutionResult extract(
                UUID transactionId,
                UUID requestId,
                List<ItemInventoryBatchEntry> entries
        ) {
            events.add("extract");
            extractCalls++;
            if (executionStatus == ItemInventoryExecutionStatus.APPLIED) {
                state = State.COMMITTED;
                return ItemInventoryExecutionResult.applied(receipt);
            }
            if (executionStatus == ItemInventoryExecutionStatus.REPLAYED) {
                state = State.COMMITTED;
                return ItemInventoryExecutionResult.replayed(receipt);
            }
            if (executionStatus == ItemInventoryExecutionStatus.ABORTED) {
                state = State.ABORTED;
                return new ItemInventoryExecutionResult(executionStatus,
                        Optional.of(receipt.token()),
                        Optional.of(receipt), false);
            }
            return ItemInventoryExecutionResult.rejected(
                    executionStatus);
        }
    }
}
