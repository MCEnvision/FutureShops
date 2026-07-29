package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchEntry;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryExecutionResult;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryExecutionStatus;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
import com.enviouse.futureshops.server.escrow.stock.StockReservation;
import com.enviouse.futureshops.server.escrow.stock.StockReservationDirection;
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

class ServerShopSellServiceTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void freshCommitMaterializesOnceAndReplayRunsBeforeReadyGate() {
        FakeBackend backend = new FakeBackend();
        FakeCustody custody = new FakeCustody(receipt());

        ServerShopSellService.Result first = ServerShopSellService.execute(
                ServerShopSellTestFixtures.request(), backend, custody);

        assertTrue(first.success());
        assertFalse(first.replayed());
        assertEquals(1, backend.preflightCalls);
        assertEquals(1, custody.extractCalls);
        assertEquals(50L, first.overflowClaim().orElseThrow()
                .remainingUnits());

        backend.ready = false;
        ServerShopSellService.Result replay = ServerShopSellService.execute(
                ServerShopSellTestFixtures.request(), backend, custody);

        assertTrue(replay.success());
        assertTrue(replay.replayed());
        assertEquals(1, backend.preflightCalls);
        assertEquals(1, custody.extractCalls);
        assertEquals(1, backend.readyCalls);
    }

    @Test
    void committedCustodyWithoutParentResumesTheAtomicCommit() {
        FakeBackend backend = new FakeBackend();
        FakeCustody custody = new FakeCustody(receipt());
        backend.intent = Optional.of(ServerShopSellIntent.prepared(
                ServerShopSellTestFixtures.request(),
                ServerShopSellTestFixtures.wallet()));
        custody.state = ServerShopSellItemCustody.State.COMMITTED;

        ServerShopSellService.Result result = ServerShopSellService.execute(
                ServerShopSellTestFixtures.request(), backend, custody);

        assertTrue(result.success());
        assertTrue(backend.transaction.isPresent());
        assertEquals(0, custody.extractCalls);
        assertEquals(StockReservationState.COMMITTED,
                backend.reservations.get(0).state());
    }

    @Test
    void preparedIntentResumesWithoutRepeatingMutablePreflight() {
        FakeBackend backend = new FakeBackend();
        backend.intent = Optional.of(ServerShopSellIntent.prepared(
                ServerShopSellTestFixtures.request(),
                ServerShopSellTestFixtures.wallet()));
        FakeCustody custody = new FakeCustody(receipt());

        ServerShopSellService.Result result = ServerShopSellService.execute(
                ServerShopSellTestFixtures.request(), backend, custody);

        assertTrue(result.success());
        assertEquals(0, backend.preflightCalls);
        assertEquals(1, custody.extractCalls);
    }

    @Test
    void partialParentMaterializationFailsClosedBeforeGates() {
        FakeBackend backend = new FakeBackend();
        backend.ready = false;
        backend.ledger = Optional.of(
                ServerShopSellTestFixtures.commit().ledgerTransaction());
        FakeCustody custody = new FakeCustody(receipt());

        ServerShopSellService.Result result = ServerShopSellService.execute(
                ServerShopSellTestFixtures.request(), backend, custody);

        assertEquals(ServerShopSellService.Status.RECOVERY_REQUIRED,
                result.status());
        assertEquals(0, backend.readyCalls);
        assertEquals(0, backend.preflightCalls);
        assertEquals(0, custody.extractCalls);
    }

    @Test
    void stockPreflightRejectsBeforeItemCustody() {
        FakeBackend backend = new FakeBackend();
        backend.preflight = ServerShopSellService.PreflightResult.failure(
                ServerShopSellService.PreflightDisposition
                        .STOCK_UNAVAILABLE);
        FakeCustody custody = new FakeCustody(receipt());

        ServerShopSellService.Result result = ServerShopSellService.execute(
                ServerShopSellTestFixtures.request(), backend, custody);

        assertEquals(ServerShopSellService.Status.STOCK_UNAVAILABLE,
                result.status());
        assertEquals(0, custody.extractCalls);
        assertTrue(backend.transaction.isEmpty());
    }

    @Test
    void changedWireIdentityConflictsEvenWhenRuntimeIsUnavailable() {
        FakeBackend backend = new FakeBackend();
        FakeCustody custody = new FakeCustody(receipt());
        custody.state = ServerShopSellItemCustody.State.COMMITTED;
        backend.intent = Optional.of(ServerShopSellIntent.prepared(
                ServerShopSellTestFixtures.request(),
                ServerShopSellTestFixtures.wallet()));
        backend.materialize(ServerShopSellTestFixtures.commit());
        backend.ready = false;
        ServerShopSellService.Identity changed =
                new ServerShopSellService.Identity(
                        ServerShopSellTestFixtures.REQUEST_ID,
                        ServerShopSellTestFixtures.PLAYER_ID,
                        "default", "emerald.offer", 2);

        ServerShopSellService.Result result =
                ServerShopSellService.resolveReplay(
                        changed, backend, custody).orElseThrow();

        assertEquals(ServerShopSellService.Status.REQUEST_CONFLICT,
                result.status());
        assertEquals(0, backend.readyCalls);
    }

    @Test
    void conflictAfterCustodyNeverReportsARecoverableUserFailure() {
        FakeBackend backend = new FakeBackend();
        backend.commitDisposition =
                ServerShopSellService.CommitDisposition.CONFLICT;
        FakeCustody custody = new FakeCustody(receipt());

        ServerShopSellService.Result result = ServerShopSellService.execute(
                ServerShopSellTestFixtures.request(), backend, custody);

        assertEquals(ServerShopSellService.Status.RECOVERY_REQUIRED,
                result.status());
        assertEquals(ServerShopSellItemCustody.State.COMMITTED,
                custody.state);
        assertTrue(backend.transaction.isEmpty());
    }

    @Test
    void missingItemsAbortIsDurableAndReplaysBeforeReadyGate() {
        FakeBackend backend = new FakeBackend();
        FakeCustody custody = new FakeCustody(receipt());
        custody.nextResult = ItemInventoryExecutionResult.rejected(
                ItemInventoryExecutionStatus.INSUFFICIENT_ITEMS);

        ServerShopSellService.Result first = ServerShopSellService.execute(
                ServerShopSellTestFixtures.request(), backend, custody);

        assertEquals(ServerShopSellService.Status.MISSING_ITEMS,
                first.status());
        assertEquals(ServerShopSellIntent.Status.ABORTED_MISSING_ITEMS,
                backend.intent.orElseThrow().status());

        backend.ready = false;
        ServerShopSellService.Result replay = ServerShopSellService.execute(
                ServerShopSellTestFixtures.request(), backend, custody);

        assertEquals(ServerShopSellService.Status.MISSING_ITEMS,
                replay.status());
        assertEquals(1, backend.preflightCalls);
        assertEquals(1, custody.extractCalls);
    }

    private static ItemInventoryMutationReceipt receipt() {
        return ServerShopSellTestFixtures.receipt(
                ServerShopSellTestFixtures.REQUEST_ID,
                ServerShopSellTestFixtures.PLAYER_ID, 3,
                ServerShopSellTestFixtures.template(),
                ServerShopSellTestFixtures.APPLIED_AT);
    }

    private static final class FakeCustody
            implements ServerShopSellItemCustody {
        private final ItemInventoryMutationReceipt receipt;
        private State state = State.NONE;
        private int extractCalls;
        private ItemInventoryExecutionResult nextResult;

        private FakeCustody(ItemInventoryMutationReceipt receipt) {
            this.receipt = receipt;
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
            extractCalls++;
            if (nextResult != null) {
                ItemInventoryExecutionResult result = nextResult;
                nextResult = null;
                return result;
            }
            if (state == State.COMMITTED) {
                return ItemInventoryExecutionResult.replayed(receipt);
            }
            state = State.COMMITTED;
            return ItemInventoryExecutionResult.applied(receipt);
        }
    }

    private static final class FakeBackend
            implements ServerShopSellService.Backend {
        private boolean ready = true;
        private int readyCalls;
        private int preflightCalls;
        private ServerShopSellService.PreflightResult preflight =
                ServerShopSellService.PreflightResult.ready(
                        ServerShopSellTestFixtures.wallet());
        private ServerShopSellService.CommitDisposition commitDisposition =
                ServerShopSellService.CommitDisposition.APPLIED;
        private Optional<EscrowTransaction> transaction = Optional.empty();
        private Optional<LedgerTransaction> ledger = Optional.empty();
        private Optional<ServerShopSellIntent> intent = Optional.empty();
        private final List<EscrowClaim> claims = new ArrayList<>();
        private final List<StockReservation> reservations =
                new ArrayList<>();

        @Override
        public boolean ready() {
            readyCalls++;
            return ready;
        }

        @Override
        public Optional<ServerShopSellIntent> intent(UUID requestId) {
            return intent;
        }

        @Override
        public ServerShopSellService.IntentDisposition prepareIntent(
                ServerShopSellIntent prepared
        ) {
            if (intent.isEmpty()) {
                intent = Optional.of(prepared);
                return ServerShopSellService.IntentDisposition.APPLIED;
            }
            return intent.orElseThrow().equals(prepared)
                    ? ServerShopSellService.IntentDisposition.REPLAYED
                    : ServerShopSellService.IntentDisposition.CONFLICT;
        }

        @Override
        public ServerShopSellService.IntentDisposition abortIntent(
                ServerShopSellIntent expected,
                ServerShopSellIntent replacement
        ) {
            if (intent.filter(expected::equals).isEmpty()) {
                return intent.filter(replacement::equals).isPresent()
                        ? ServerShopSellService.IntentDisposition.REPLAYED
                        : ServerShopSellService.IntentDisposition.CONFLICT;
            }
            intent = Optional.of(replacement);
            return ServerShopSellService.IntentDisposition.APPLIED;
        }

        @Override
        public Optional<EscrowTransaction> transaction(
                UUID transactionId
        ) {
            return transaction;
        }

        @Override
        public Optional<LedgerTransaction> ledgerTransaction(
                UUID transactionId
        ) {
            return ledger;
        }

        @Override
        public List<EscrowClaim> claimsForTransaction(
                UUID transactionId
        ) {
            return List.copyOf(claims);
        }

        @Override
        public List<StockReservation> stockReservations(
                UUID transactionId
        ) {
            return List.copyOf(reservations);
        }

        @Override
        public ServerShopSellService.PreflightResult preflight(
                UUID playerId,
                StockKey stockKey,
                int quantity,
                long expectedStockRevision
        ) {
            preflightCalls++;
            return preflight;
        }

        @Override
        public ServerShopSellService.CommitDisposition commit(
                ServerShopSellCommit commit
        ) {
            if (commitDisposition
                    == ServerShopSellService.CommitDisposition.APPLIED
                    || commitDisposition
                    == ServerShopSellService.CommitDisposition.REPLAYED) {
                intent = intent.map(ServerShopSellIntent::complete);
                materialize(commit);
            }
            return commitDisposition;
        }

        private void materialize(ServerShopSellCommit commit) {
            transaction = Optional.of(commit.completedTransaction());
            ledger = Optional.of(commit.ledgerTransaction());
            claims.clear();
            commit.overflowClaim().ifPresent(claims::add);
            reservations.clear();
            StockKey key = new StockKey(commit.shopId(),
                    commit.listingId());
            reservations.add(StockReservation.held(commit.requestId(), key,
                    StockReservationDirection.INBOUND, commit.quantity(),
                    true, commit.itemCustodyReceipt().appliedAt()).resolve(
                    StockReservationState.COMMITTED,
                    commit.itemCustodyReceipt().appliedAt()));
        }
    }
}
