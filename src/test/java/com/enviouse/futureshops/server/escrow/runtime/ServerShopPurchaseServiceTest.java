package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.claim.ClaimAttemptResult;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.journal.JournalRecord;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.DimensionAwareShopReference;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.stock.StockReservation;
import com.enviouse.futureshops.server.escrow.stock.StockReservationState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerShopPurchaseServiceTest {
    private static final Instant NOW = Instant.parse(
            "2026-07-17T19:00:00Z");

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void freshSuccessMaterializesThenExactReplaySkipsCommit() {
        Fixture fixture = fixture(false);
        FakeBackend backend = new FakeBackend(fixture.playerId(), 500L,
                -20L);

        ServerShopPurchaseService.Result fresh =
                ServerShopPurchaseService.execute(fixture.request(), backend);
        ServerShopPurchaseService.Result replay =
                ServerShopPurchaseService.execute(fixture.request(), backend);

        assertTrue(fresh.successful());
        assertFalse(fresh.replayed());
        assertEquals(425L, fresh.resultingBalanceMinorUnits());
        assertTrue(replay.successful());
        assertTrue(replay.replayed());
        assertEquals(fresh.transactionId(), replay.transactionId());
        assertEquals(1, backend.commitCalls());
    }

    @Test
    void sameRequestWithDifferentPayloadIsAConflict() {
        Fixture fixture = fixture(false);
        FakeBackend backend = new FakeBackend(fixture.playerId(), 500L, 0L);
        assertTrue(ServerShopPurchaseService.execute(
                fixture.request(), backend).successful());
        ServerShopPurchaseService.PreparedRequest conflict = request(
                fixture.requestId(), fixture.playerId(), false,
                PaymentSource.WALLET,
                List.of(line(fixture.requestId(), 0, "diamond_offer",
                        "minecraft:diamond", 2, 50L, 3L,
                        new ItemStack(Items.DIAMOND, 2))));

        ServerShopPurchaseService.Result result =
                ServerShopPurchaseService.execute(conflict, backend);

        assertEquals(ServerShopPurchaseService.Status.REQUEST_CONFLICT,
                result.status());
        assertEquals(1, backend.commitCalls());
    }

    @Test
    void partialMaterializationRequiresRecoveryWithoutAnotherCommit() {
        Fixture fixture = fixture(false);
        FakeBackend backend = new FakeBackend(fixture.playerId(), 500L, 0L);
        ServerShopPurchaseCommit commit = commit(fixture, 500L, 0L);
        backend.putTransaction(commit.completedTransaction());

        ServerShopPurchaseService.Result result =
                ServerShopPurchaseService.execute(fixture.request(), backend);

        assertEquals(ServerShopPurchaseService.Status.RECOVERY_REQUIRED,
                result.status());
        assertEquals(0, backend.commitCalls());
    }

    @Test
    void insufficientFundsNeverCallsTheCommitBoundary() {
        Fixture fixture = fixture(false);
        FakeBackend backend = new FakeBackend(fixture.playerId(), 74L, 0L);

        ServerShopPurchaseService.Result result =
                ServerShopPurchaseService.execute(fixture.request(), backend);

        assertEquals(ServerShopPurchaseService.Status.INSUFFICIENT_FUNDS,
                result.status());
        assertEquals(0, backend.commitCalls());
    }

    @Test
    void physicalFundingUsesInternalClaimWithFullWalletAndReplays() {
        UUID requestId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID fundingTransactionId = UUID.randomUUID();
        UUID fundingClaimId = UUID.randomUUID();
        List<ServerShopPurchaseCommit.Line> lines = List.of(line(requestId,
                0, "diamond_offer", "minecraft:diamond", 3, 75L, 3L,
                new ItemStack(Items.DIAMOND, 3)));
        ServerShopPurchaseCommit.PhysicalFunding funding =
                new ServerShopPurchaseCommit.PhysicalFunding(
                        requestId, fundingTransactionId, fundingClaimId, 75L);
        ServerShopPurchaseService.PreparedRequest request = request(
                requestId, playerId, false, PaymentSource.PHYSICAL, lines,
                Optional.of(funding));
        FakeBackend backend = new FakeBackend(playerId, 500L, 0L);
        backend.putFundingClaim(new EscrowClaim(fundingClaimId,
                fundingTransactionId, playerId, "physical funding",
                ClaimKind.INTERNAL_ESCROW_MONEY, 75L, 75L, new byte[0],
                ClaimStatus.PENDING, "Physical funding", NOW, NOW), 75L);

        ServerShopPurchaseService.Result fresh =
                ServerShopPurchaseService.execute(request, backend);
        ServerShopPurchaseService.Result replay =
                ServerShopPurchaseService.execute(request, backend);

        assertTrue(fresh.successful());
        assertEquals(500L, fresh.resultingBalanceMinorUnits());
        assertEquals(0L, backend.ledgerBalance(
                ServerShopPurchaseCommit.claimAccount(fundingClaimId)));
        assertEquals(ClaimStatus.COMPLETED,
                backend.claim(fundingClaimId).orElseThrow().status());
        assertTrue(replay.successful());
        assertTrue(replay.replayed());
        assertEquals(1, backend.commitCalls());
    }

    @Test
    void physicalFundingRetryAfterCommitResponseLossIsDeterministic() {
        UUID requestId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID fundingTransactionId = UUID.randomUUID();
        UUID fundingClaimId = UUID.randomUUID();
        List<ServerShopPurchaseCommit.Line> lines = List.of(line(requestId,
                0, "diamond_offer", "minecraft:diamond", 3, 75L, 3L,
                new ItemStack(Items.DIAMOND, 3)));
        ServerShopPurchaseCommit.PhysicalFunding funding =
                new ServerShopPurchaseCommit.PhysicalFunding(
                        requestId, fundingTransactionId, fundingClaimId, 75L);
        ServerShopPurchaseService.PreparedRequest request = request(
                requestId, playerId, false, PaymentSource.PHYSICAL, lines,
                Optional.of(funding));
        FakeBackend backend = new FakeBackend(playerId, 0L, 0L);
        backend.putFundingClaim(new EscrowClaim(fundingClaimId,
                fundingTransactionId, playerId, "physical funding",
                ClaimKind.INTERNAL_ESCROW_MONEY, 75L, 75L, new byte[0],
                ClaimStatus.PENDING, "Physical funding", NOW, NOW), 75L);
        backend.failAfterMaterialize();

        ServerShopPurchaseService.Result result =
                ServerShopPurchaseService.execute(request, backend);

        assertTrue(result.successful());
        assertTrue(result.replayed());
        assertEquals(1, backend.commitCalls());
    }

    @Test
    void completedPhysicalClaimCannotFundAnotherPurchaseRequest() {
        UUID firstRequestId = UUID.randomUUID();
        UUID secondRequestId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID fundingTransactionId = UUID.randomUUID();
        UUID fundingClaimId = UUID.randomUUID();
        ServerShopPurchaseCommit.PhysicalFunding funding =
                new ServerShopPurchaseCommit.PhysicalFunding(
                        firstRequestId, fundingTransactionId, fundingClaimId,
                        75L);
        List<ServerShopPurchaseCommit.Line> firstLines = List.of(line(
                firstRequestId, 0, "diamond_offer", "minecraft:diamond",
                3, 75L, 3L, new ItemStack(Items.DIAMOND, 3)));
        FakeBackend backend = new FakeBackend(playerId, 0L, 0L);
        backend.putFundingClaim(new EscrowClaim(fundingClaimId,
                fundingTransactionId, playerId, "physical funding",
                ClaimKind.INTERNAL_ESCROW_MONEY, 75L, 75L, new byte[0],
                ClaimStatus.PENDING, "Physical funding", NOW, NOW), 75L);
        assertTrue(ServerShopPurchaseService.execute(request(firstRequestId,
                playerId, false, PaymentSource.PHYSICAL, firstLines,
                Optional.of(funding)), backend).successful());
        List<ServerShopPurchaseCommit.Line> secondLines = List.of(line(
                secondRequestId, 0, "diamond_offer", "minecraft:diamond",
                3, 75L, 3L, new ItemStack(Items.DIAMOND, 3)));

        ServerShopPurchaseService.Result result =
                ServerShopPurchaseService.execute(request(secondRequestId,
                        playerId, false, PaymentSource.PHYSICAL, secondLines,
                        Optional.of(new ServerShopPurchaseCommit
                                .PhysicalFunding(secondRequestId,
                                fundingTransactionId, fundingClaimId, 75L))),
                        backend);

        assertEquals(ServerShopPurchaseService.Status.RECOVERY_REQUIRED,
                result.status());
        assertEquals(1, backend.commitCalls());
    }

    @Test
    void commitExceptionAfterMaterializationResolvesThroughReplay() {
        Fixture fixture = fixture(false);
        FakeBackend backend = new FakeBackend(fixture.playerId(), 500L, 0L);
        backend.failAfterMaterialize();

        ServerShopPurchaseService.Result result =
                ServerShopPurchaseService.execute(fixture.request(), backend);

        assertTrue(result.successful());
        assertTrue(result.replayed());
        assertEquals(1, backend.commitCalls());
    }

    @Test
    void cartParentHasOneCanonicalChildPerLine() {
        Fixture fixture = fixture(true);
        FakeBackend backend = new FakeBackend(fixture.playerId(), 500L, 0L);

        ServerShopPurchaseService.Result result =
                ServerShopPurchaseService.execute(fixture.request(), backend);
        EscrowTransaction parent = backend.transaction(
                fixture.requestId()).orElseThrow();

        assertTrue(result.successful());
        assertEquals(2, fixture.request().lines().size());
        for (ServerShopPurchaseCommit.Line line
                : fixture.request().lines()) {
            UUID childId = ServerShopPurchaseCommit.childTransactionId(
                    fixture.requestId(), line.lineIndex(), line.listingId());
            EscrowTransaction child = backend.transaction(childId)
                    .orElseThrow();
            assertEquals(Optional.of(parent.transactionId()),
                    child.parentTransactionId());
        }
    }

    @Test
    void claimLedgerAndReservationMismatchesFailClosed() {
        Fixture fixture = fixture(false);

        FakeBackend missingClaim = materialized(fixture);
        missingClaim.clearClaims();
        assertRecovery(fixture, missingClaim);

        FakeBackend missingLedger = materialized(fixture);
        missingLedger.clearLedger(fixture.requestId());
        assertRecovery(fixture, missingLedger);

        FakeBackend wrongReservation = materialized(fixture);
        wrongReservation.holdReservations(fixture.requestId());
        assertRecovery(fixture, wrongReservation);
    }

    private static void assertRecovery(
            Fixture fixture,
            FakeBackend backend
    ) {
        ServerShopPurchaseService.Result result =
                ServerShopPurchaseService.execute(fixture.request(), backend);
        assertEquals(ServerShopPurchaseService.Status.RECOVERY_REQUIRED,
                result.status());
        assertEquals(1, backend.commitCalls());
    }

    private static FakeBackend materialized(Fixture fixture) {
        FakeBackend backend = new FakeBackend(fixture.playerId(), 500L, 0L);
        assertTrue(ServerShopPurchaseService.execute(
                fixture.request(), backend).successful());
        return backend;
    }

    private static ServerShopPurchaseCommit commit(
            Fixture fixture,
            long wallet,
            long debt
    ) {
        return ServerShopPurchaseCommit.create(fixture.requestId(),
                fixture.playerId(), "default",
                fixture.request().identity().cartCheckout(),
                fixture.request().identity().paymentSource(), wallet, debt,
                "Funds", 2, fixture.request().lines(),
                fixture.request().shopReference(), NOW);
    }

    private static Fixture fixture(boolean cart) {
        UUID requestId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        List<ServerShopPurchaseCommit.Line> lines = new ArrayList<>();
        lines.add(line(requestId, 0, "diamond_offer",
                "minecraft:diamond", 3, 75L, 3L,
                tagged(new ItemStack(Items.DIAMOND, 3), "diamond")));
        if (cart) {
            lines.add(line(requestId, 1, "emerald_offer",
                    "minecraft:emerald", 2, 40L, 7L,
                    tagged(new ItemStack(Items.EMERALD, 2), "emerald")));
        }
        return new Fixture(requestId, playerId,
                request(requestId, playerId, cart, PaymentSource.WALLET,
                        lines));
    }

    private static ItemStack tagged(ItemStack stack, String value) {
        stack.getOrCreateTag().putString("quote", value);
        return stack;
    }

    private static ServerShopPurchaseCommit.Line line(
            UUID requestId,
            int lineIndex,
            String listingId,
            String itemId,
            int quantity,
            long cost,
            long revision,
            ItemStack stack
    ) {
        return ServerShopPurchaseService.captureLine(requestId, lineIndex,
                listingId, itemId, quantity, cost, revision,
                List.of(stack));
    }

    private static ServerShopPurchaseService.PreparedRequest request(
            UUID requestId,
            UUID playerId,
            boolean cart,
            PaymentSource source,
            List<ServerShopPurchaseCommit.Line> lines
    ) {
        return request(requestId, playerId, cart, source, lines,
                Optional.empty());
    }

    private static ServerShopPurchaseService.PreparedRequest request(
            UUID requestId,
            UUID playerId,
            boolean cart,
            PaymentSource source,
            List<ServerShopPurchaseCommit.Line> lines,
            Optional<ServerShopPurchaseCommit.PhysicalFunding> funding
    ) {
        ServerShopPurchaseService.Identity identity =
                new ServerShopPurchaseService.Identity(requestId, playerId,
                        "default", cart, source, lines.stream().map(line ->
                        new ServerShopPurchaseCommit.IdentityLine(
                                line.listingId(), line.quantity())).toList());
        return new ServerShopPurchaseService.PreparedRequest(identity, lines,
                "Funds", 2, new DimensionAwareShopReference("default",
                "minecraft:overworld", 1, 64, 1), NOW, funding);
    }

    private record Fixture(
            UUID requestId,
            UUID playerId,
            ServerShopPurchaseService.PreparedRequest request
    ) {
    }

    private static final class FakeBackend
            implements ServerShopPurchaseService.Backend {
        private final UUID playerId;
        private final Map<LedgerAccountId, Long> balances = new HashMap<>();
        private final Map<UUID, EscrowTransaction> transactions =
                new HashMap<>();
        private final Map<UUID, LedgerTransaction> ledgers = new HashMap<>();
        private final Map<UUID, List<EscrowClaim>> claims = new HashMap<>();
        private final Map<UUID, EscrowClaim> claimsById = new HashMap<>();
        private final Map<String, ClaimAttemptResult> claimAttempts =
                new HashMap<>();
        private final Map<UUID, List<StockReservation>> reservations =
                new HashMap<>();
        private int commitCalls;
        private boolean failAfterMaterialize;

        private FakeBackend(UUID playerId, long wallet, long debt) {
            this.playerId = playerId;
            balances.put(ServerShopPurchaseCommit.walletAccount(playerId),
                    wallet);
            balances.put(ServerShopPurchaseCommit.debtAccount(playerId),
                    debt);
        }

        @Override
        public Optional<EscrowTransaction> transaction(UUID transactionId) {
            return Optional.ofNullable(transactions.get(transactionId));
        }

        @Override
        public Optional<LedgerTransaction> ledgerTransaction(
                UUID transactionId
        ) {
            return Optional.ofNullable(ledgers.get(transactionId));
        }

        @Override
        public List<EscrowClaim> claimsForTransaction(UUID transactionId) {
            return claims.getOrDefault(transactionId, List.of());
        }

        @Override
        public Optional<EscrowClaim> claim(UUID claimId) {
            return Optional.ofNullable(claimsById.get(claimId));
        }

        @Override
        public Optional<ClaimAttemptResult> claimAttempt(String requestKey) {
            return Optional.ofNullable(claimAttempts.get(requestKey));
        }

        @Override
        public List<StockReservation> stockReservations(
                UUID transactionId
        ) {
            return reservations.getOrDefault(transactionId, List.of());
        }

        @Override
        public long ledgerBalance(LedgerAccountId account) {
            return balances.getOrDefault(account, 0L);
        }

        @Override
        public EscrowCommitResult commit(ServerShopPurchaseCommit commit) {
            commitCalls++;
            materialize(commit);
            if (failAfterMaterialize) {
                throw new EscrowRuntimeException(
                        "Simulated post commit response loss");
            }
            return EscrowCommitResult.applied(new JournalRecord(
                    commitCalls, commit.requestId(), UUID.randomUUID(),
                    new byte[]{1}));
        }

        private void materialize(ServerShopPurchaseCommit commit) {
            transactions.put(commit.requestId(),
                    commit.completedTransaction());
            for (EscrowTransaction child
                    : commit.completedLineTransactions()) {
                transactions.put(child.transactionId().value(), child);
            }
            ledgers.put(commit.requestId(), commit.ledgerTransaction());
            claims.put(commit.requestId(), commit.itemClaims());
            for (EscrowClaim claim : commit.itemClaims()) {
                claimsById.put(claim.claimId(), claim);
            }
            List<StockReservation> committed = commit.stockReservation()
                    .reservations().stream().map(value ->
                            StockReservation.held(commit.requestId(),
                                    value.stockKey(), value.direction(),
                                    value.quantity(), true, NOW)
                                    .resolve(StockReservationState.COMMITTED,
                                            NOW)).toList();
            reservations.put(commit.requestId(), committed);
            for (var leg : commit.ledgerTransaction().legs()) {
                balances.merge(leg.account(), leg.deltaMinor(),
                        Math::addExact);
            }
            commit.physicalFunding().ifPresent(funding -> {
                EscrowClaim pending = claimsById.get(funding.claimId());
                EscrowClaim completed = pending.deliver(
                        funding.amountMinorUnits(), NOW);
                claimsById.put(funding.claimId(), completed);
                String key = ServerShopPurchaseCommit
                        .physicalFundingDeliveryKey(commit.requestId(),
                                funding.claimId());
                claimAttempts.put(key, new ClaimAttemptResult(
                        funding.claimId(), key, funding.amountMinorUnits(),
                        0L, ClaimStatus.COMPLETED, NOW, false));
            });
        }

        private void putFundingClaim(EscrowClaim claim, long balance) {
            claimsById.put(claim.claimId(), claim);
            claims.put(claim.transactionId(), List.of(claim));
            balances.put(ServerShopPurchaseCommit.claimAccount(
                    claim.claimId()), balance);
        }

        private void putTransaction(EscrowTransaction transaction) {
            transactions.put(transaction.transactionId().value(),
                    transaction);
        }

        private void failAfterMaterialize() {
            failAfterMaterialize = true;
        }

        private void clearClaims() {
            claims.clear();
            claimsById.clear();
        }

        private void clearLedger(UUID transactionId) {
            ledgers.remove(transactionId);
        }

        private void holdReservations(UUID transactionId) {
            List<StockReservation> held = reservations.get(transactionId)
                    .stream().map(value -> StockReservation.held(
                            value.transactionId(), value.stockKey(),
                            value.direction(), value.quantity(),
                            value.inventoryBacked(), value.createdAt()))
                    .toList();
            reservations.put(transactionId, held);
        }

        private int commitCalls() {
            return commitCalls;
        }
    }
}
