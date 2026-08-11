package com.enviouse.futureshops.server.escrow.playershop;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

class PlayerShopEscrowOrchestratorTest {
    private static final Instant NOW = Instant.parse("2026-07-17T13:00:00Z");

    @Test
    void productionStagesClaimsBeforeDeliveryAndPreservesCorrelation() {
        PlayerShopEscrowFoundationTest.Fixture fixture =
                PlayerShopEscrowFoundationTest.purchase(id("orchestrated"),
                        PlayerShopTradeMethod.MONEY_AND_BARTER,
                        PlayerShopPaymentSource.INVENTORY_CASH, false);
        FakeBackend backend = new FakeBackend();
        PlayerShopEscrowOrchestrator orchestrator = orchestrator(backend);
        PlayerShopEscrowOrchestrator.Command command =
                PlayerShopEscrowOrchestrator.Command.of(fixture.intent(), 7);

        PlayerShopEscrowOrchestrator.Result result =
                orchestrator.execute(command);

        checkEquals(PlayerShopEscrowOrchestrator.Status.COMMITTED,
                result.status(), "committed status");
        checkEquals(fixture.intent().requestId(),
                result.responseIdentity().requestId(), "response request id");
        checkEquals(7, result.responseIdentity().responseToken(),
                "response token");
        checkEquals(PlayerShopPaymentSource.INVENTORY_CASH,
                backend.lastPreparation.intent().paymentSource(),
                "payment source evidence");
        check(before(backend.calls, "persist claims", "persist commit"),
                "claims persist before commit");
        check(before(backend.calls, "persist claims", "deliver claims"),
                "claims persist before delivery");
        check(fixture.intent().claims().stream().anyMatch(value ->
                        value.kind() == PlayerShopClaimPlan.Kind.MONEY
                                && value.beneficiaryId().equals(
                                fixture.intent().ownerId())),
                "seller money claim");
        check(fixture.intent().claims().stream().anyMatch(value ->
                        value.kind() == PlayerShopClaimPlan.Kind.EXACT_ITEM
                                && value.beneficiaryId().equals(
                                fixture.intent().ownerId())),
                "owner barter claim");
    }

    @Test
    void replayDoesNotRepeatFundingAndReturnsTheSamePacketIdentity() {
        PlayerShopEscrowFoundationTest.Fixture fixture =
                PlayerShopEscrowFoundationTest.purchase(id("replayed"),
                        PlayerShopTradeMethod.MONEY,
                        PlayerShopPaymentSource.WALLET, false);
        FakeBackend backend = new FakeBackend();
        PlayerShopEscrowOrchestrator orchestrator = orchestrator(backend);
        PlayerShopEscrowOrchestrator.Command command =
                PlayerShopEscrowOrchestrator.Command.of(fixture.intent(), 3);

        PlayerShopEscrowOrchestrator.Result first = orchestrator.execute(command);
        int fundingCalls = occurrences(backend.calls, "commit funding");
        PlayerShopEscrowOrchestrator.Result replay = orchestrator.execute(command);

        checkEquals(PlayerShopEscrowOrchestrator.Status.COMMITTED,
                first.status(), "first status");
        checkEquals(PlayerShopEscrowOrchestrator.Status.REPLAYED,
                replay.status(), "replay status");
        checkEquals(fundingCalls,
                occurrences(backend.calls, "commit funding"),
                "funding count after replay");
        checkEquals(first.responseIdentity(), replay.responseIdentity(),
                "replay response identity");
        checkEquals(first.commit(), replay.commit(), "replay commit");
    }

    @Test
    void conflictingReuseIsRejectedBeforeAnyMutation() {
        UUID requestId = id("conflicting orchestration");
        PlayerShopEscrowFoundationTest.Fixture money =
                PlayerShopEscrowFoundationTest.purchase(requestId,
                        PlayerShopTradeMethod.MONEY,
                        PlayerShopPaymentSource.WALLET, false);
        PlayerShopEscrowFoundationTest.Fixture barter =
                PlayerShopEscrowFoundationTest.purchase(requestId,
                        PlayerShopTradeMethod.BARTER,
                        PlayerShopPaymentSource.NONE, false);
        FakeBackend backend = new FakeBackend();
        PlayerShopEscrowOrchestrator orchestrator = orchestrator(backend);
        orchestrator.execute(PlayerShopEscrowOrchestrator.Command.of(
                money.intent(), 11));
        int fundingCalls = occurrences(backend.calls, "commit funding");

        PlayerShopEscrowOrchestrator.Result conflict = orchestrator.execute(
                PlayerShopEscrowOrchestrator.Command.of(barter.intent(), 11));

        checkEquals(PlayerShopEscrowOrchestrator.Status.CONFLICT,
                conflict.status(), "conflict status");
        checkEquals(requestId, conflict.responseIdentity().requestId(),
                "conflict response request id");
        checkEquals(fundingCalls,
                occurrences(backend.calls, "commit funding"),
                "no conflicting funding");
    }

    @Test
    void partialFundingInvokesRecoveryWithoutCreatingClaims() {
        PlayerShopEscrowFoundationTest.Fixture fixture =
                PlayerShopEscrowFoundationTest.buyback(id("funding failure"),
                        false);
        FakeBackend backend = new FakeBackend();
        backend.fundingStatus =
                PlayerShopFundingEvidence.Status.RECOVERY_REQUIRED;

        PlayerShopEscrowOrchestrator.Result result = orchestrator(backend)
                .execute(PlayerShopEscrowOrchestrator.Command.of(
                        fixture.intent(), 4));

        checkEquals(PlayerShopEscrowOrchestrator.Status.RECOVERY_REQUIRED,
                result.status(), "recovery status");
        check(backend.calls.contains("recover"), "recovery called");
        check(!backend.calls.contains("create claims"),
                "claims not created from partial funding");
        check(!backend.calls.contains("deliver claims"),
                "partial funding not delivered");
    }

    @Test
    void claimFailureResumesWithoutRepeatingInventoryOrStorageMutation() {
        PlayerShopEscrowFoundationTest.Fixture fixture =
                PlayerShopEscrowFoundationTest.purchase(id("claim resume"),
                        PlayerShopTradeMethod.BARTER,
                        PlayerShopPaymentSource.NONE, false);
        FakeBackend backend = new FakeBackend();
        backend.failClaimCreationOnce = true;
        PlayerShopEscrowOrchestrator orchestrator = orchestrator(backend);
        PlayerShopEscrowOrchestrator.Command command =
                PlayerShopEscrowOrchestrator.Command.of(fixture.intent(), 2);

        PlayerShopEscrowOrchestrator.Result first = orchestrator.execute(command);
        int fundingCalls = occurrences(backend.calls, "commit funding");
        PlayerShopEscrowOrchestrator.Result resumed = orchestrator.execute(command);

        checkEquals(PlayerShopEscrowOrchestrator.Status.RECOVERY_REQUIRED,
                first.status(), "first recovery status");
        checkEquals(PlayerShopEscrowOrchestrator.Status.COMMITTED,
                resumed.status(), "resumed status");
        checkEquals(fundingCalls,
                occurrences(backend.calls, "commit funding"),
                "funding not repeated");
    }

    @Test
    void settlementImportIsMarkedOnlyAfterItsClaimCommit() {
        PlayerShopEscrowFoundationTest.Fixture fixture =
                PlayerShopEscrowFoundationTest.settlement(
                        id("legacy settlement"));
        PlayerShopSettlementImportEvidence settlement =
                PlayerShopSettlementImportEvidence.capture(
                        fixture.intent().requestId(), fixture.intent().ownerId(),
                        fixture.intent().shopIdentity().registryShopId(),
                        "legacy.owner.shop.position", 9L, 300L);
        FakeBackend backend = new FakeBackend();

        PlayerShopEscrowOrchestrator.Result result = orchestrator(backend)
                .execute(PlayerShopEscrowOrchestrator.Command.settlement(
                        fixture.intent(), 0, settlement));

        checkEquals(PlayerShopEscrowOrchestrator.Status.COMMITTED,
                result.status(), "settlement status");
        check(before(backend.calls, "persist commit", "mark settlement"),
                "settlement marked after commit");
        check(before(backend.calls, "persist claims", "mark settlement"),
                "settlement claim exists before import marker");
        checkEquals(300L, settlement.pendingMinorUnits(),
                "imported settlement amount");
    }

    @Test
    void pendingDeliveryKeepsTheDurableCommitAndExactClaims() {
        PlayerShopEscrowFoundationTest.Fixture fixture =
                PlayerShopEscrowFoundationTest.purchase(id("pending delivery"),
                        PlayerShopTradeMethod.MONEY,
                        PlayerShopPaymentSource.WALLET, false);
        FakeBackend backend = new FakeBackend();
        backend.deliveryStatus =
                PlayerShopEscrowBackend.DeliveryStatus.CLAIMS_PENDING;

        PlayerShopEscrowOrchestrator.Result result = orchestrator(backend)
                .execute(PlayerShopEscrowOrchestrator.Command.of(
                        fixture.intent(), 5));

        checkEquals(
                PlayerShopEscrowOrchestrator.Status.COMMITTED_WITH_PENDING_DELIVERY,
                result.status(), "pending delivery status");
        check(result.commit() != null, "pending delivery commit");
        checkEquals(fixture.intent().claims(), result.commit().createdClaims(),
                "pending exact claims");
    }

    @Test
    void deliveryRecoveryAndQuarantineKeepTheDurableCommit() {
        for (PlayerShopEscrowBackend.DeliveryStatus deliveryStatus
                : List.of(
                PlayerShopEscrowBackend.DeliveryStatus.RECOVERY_REQUIRED,
                PlayerShopEscrowBackend.DeliveryStatus.QUARANTINED)) {
            PlayerShopEscrowFoundationTest.Fixture fixture =
                    PlayerShopEscrowFoundationTest.purchase(
                            id("delivery " + deliveryStatus),
                            PlayerShopTradeMethod.MONEY,
                            PlayerShopPaymentSource.WALLET, false);
            FakeBackend backend = new FakeBackend();
            backend.deliveryStatus = deliveryStatus;

            PlayerShopEscrowOrchestrator.Result result =
                    orchestrator(backend).execute(
                            PlayerShopEscrowOrchestrator.Command.of(
                                    fixture.intent(), 6));

            PlayerShopEscrowOrchestrator.Status expected =
                    deliveryStatus
                            == PlayerShopEscrowBackend.DeliveryStatus
                            .RECOVERY_REQUIRED
                            ? PlayerShopEscrowOrchestrator.Status
                            .RECOVERY_REQUIRED
                            : PlayerShopEscrowOrchestrator.Status.QUARANTINED;
            checkEquals(expected, result.status(),
                    deliveryStatus + " status");
            check(result.commit() != null,
                    deliveryStatus + " durable commit");
            checkEquals(fixture.intent().requestId(),
                    result.commit().commitId(),
                    deliveryStatus + " commit identity");
            check(before(backend.calls, "persist commit", "deliver claims"),
                    deliveryStatus + " persisted before delivery");
        }
    }

    @Test
    void requestAndResponseIdentitiesRejectZeroAndOutOfRangeValues() {
        PlayerShopEscrowFoundationTest.Fixture fixture =
                PlayerShopEscrowFoundationTest.purchase(id("identity"),
                        PlayerShopTradeMethod.MONEY,
                        PlayerShopPaymentSource.WALLET, false);
        expectThrows(() -> PlayerShopRequestIdentity.from(fixture.intent(),
                PlayerShopRequestIdentity.MAX_RESPONSE_TOKEN + 1));
        expectThrows(() -> new PlayerShopPacketResponseIdentity(
                new UUID(0L, 0L), 0, PlayerShopOperation.PURCHASE));
    }

    private static PlayerShopEscrowOrchestrator orchestrator(
            FakeBackend backend
    ) {
        return new PlayerShopEscrowOrchestrator(backend,
                Clock.fixed(NOW.plusSeconds(10), ZoneOffset.UTC));
    }

    private static boolean before(List<String> calls, String first,
                                  String second) {
        return calls.indexOf(first) >= 0
                && calls.indexOf(first) < calls.indexOf(second);
    }

    private static int occurrences(List<String> values, String value) {
        return (int) values.stream().filter(value::equals).count();
    }

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    private static void checkEquals(Object expected, Object actual,
                                    String label) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(label + ". Expected " + expected
                    + " but got " + actual);
        }
    }

    private static void expectThrows(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError("Expected an exception");
    }

    private static final class FakeBackend implements PlayerShopEscrowBackend {
        private final Map<UUID, PlayerShopExecutionSnapshot> snapshots =
                new LinkedHashMap<>();
        private final List<String> calls = new ArrayList<>();
        private PlayerShopFundingEvidence.Status fundingStatus =
                PlayerShopFundingEvidence.Status.COMPLETE;
        private DeliveryStatus deliveryStatus = DeliveryStatus.DELIVERED;
        private boolean failClaimCreationOnce;
        private PlayerShopPreparedExecution lastPreparation;

        @Override
        public Optional<PlayerShopExecutionSnapshot> load(UUID requestId) {
            calls.add("load");
            return Optional.ofNullable(snapshots.get(requestId));
        }

        @Override
        public void persistIntent(PlayerShopExecutionSnapshot snapshot) {
            calls.add("persist intent");
            snapshots.put(snapshot.intent().requestId(), snapshot);
        }

        @Override
        public PlayerShopPreparedExecution prepare(
                PlayerShopRequestIdentity requestIdentity,
                PlayerShopEscrowIntent intent
        ) {
            calls.add("prepare");
            List<PlayerShopMutationPreparation> preparations =
                    new ArrayList<>();
            for (PlayerShopMoneyTransfer transfer : intent.moneyTransfers()) {
                preparations.add(PlayerShopMutationPreparation.money(transfer,
                        bytes("money token." + transfer.transferId())));
            }
            for (PlayerShopItemTransfer transfer : intent.itemTransfers()) {
                preparations.add(PlayerShopMutationPreparation.item(transfer,
                        bytes("item token." + transfer.transferId())));
            }
            for (PlayerShopStorageMutationPlan mutation
                    : intent.storageMutations()) {
                preparations.add(PlayerShopMutationPreparation.storage(mutation,
                        bytes("storage token." + mutation.mutationId())));
            }
            lastPreparation = PlayerShopPreparedExecution.create(
                    requestIdentity, intent, NOW.plusSeconds(1), preparations);
            return lastPreparation;
        }

        @Override
        public void persistPreparation(
                PlayerShopPreparedExecution preparation
        ) {
            calls.add("persist preparation");
            PlayerShopExecutionSnapshot current = snapshots.get(
                    preparation.intent().requestId());
            snapshots.put(preparation.intent().requestId(),
                    current.withPreparation(preparation));
        }

        @Override
        public PlayerShopFundingEvidence commitFunding(
                PlayerShopPreparedExecution preparation
        ) {
            calls.add("commit funding");
            if (fundingStatus != PlayerShopFundingEvidence.Status.COMPLETE) {
                return new PlayerShopFundingEvidence(
                        preparation.intent().requestId(), fundingStatus,
                        List.of(), List.of(), List.of(),
                        "Simulated custody uncertainty");
            }
            PlayerShopEscrowIntent intent = preparation.intent();
            List<PlayerShopMoneyMutationReceipt> money = new ArrayList<>();
            for (PlayerShopMoneyTransfer transfer : intent.moneyTransfers()) {
                money.add(PlayerShopMoneyMutationReceipt.applied(
                        intent.requestId(), transfer,
                        transfer.sourceBalanceAfterMinorUnits(),
                        transfer.destinationBalanceAfterMinorUnits(),
                        bytes("money receipt." + transfer.transferId())));
            }
            List<PlayerShopItemMutationReceipt> items = new ArrayList<>();
            for (PlayerShopItemTransfer transfer : intent.itemTransfers()) {
                PlayerShopItemMutationReceipt.FundingKind kind = switch (
                        transfer.source().kind()) {
                    case ACTOR_INVENTORY ->
                            PlayerShopItemMutationReceipt.FundingKind.INVENTORY_REMOVAL;
                    case LINKED_STOCK ->
                            PlayerShopItemMutationReceipt.FundingKind.STORAGE_EXTRACTION;
                    case ADMIN_MINT ->
                            PlayerShopItemMutationReceipt.FundingKind.ADMIN_MINT;
                    default -> throw new AssertionError("Unexpected item source");
                };
                items.add(PlayerShopItemMutationReceipt.funded(
                        intent.requestId(), transfer, kind,
                        bytes("item receipt." + transfer.transferId())));
            }
            List<PlayerShopStorageCustodyReceipt> storage = new ArrayList<>();
            for (PlayerShopStorageMutationPlan mutation
                    : intent.storageMutations()) {
                PlayerShopStorageCustodyReceipt receipt =
                        PlayerShopStorageCustodyReceipt.prepared(
                                intent.requestId(), mutation,
                                NOW.plusSeconds(1));
                if (mutation.direction()
                        == PlayerShopStorageMutationPlan.Direction.EXTRACT) {
                    receipt = receipt.applied("storage before",
                            "storage after", bytes("storage receipt."
                                    + mutation.mutationId()),
                            NOW.plusSeconds(2));
                }
                storage.add(receipt);
            }
            return PlayerShopFundingEvidence.complete(intent.requestId(),
                    money, items, storage);
        }

        @Override
        public void persistFunding(PlayerShopFundingEvidence funding) {
            calls.add("persist funding");
            PlayerShopExecutionSnapshot current = snapshots.get(
                    funding.requestId());
            snapshots.put(funding.requestId(), current.withFunding(funding));
        }

        @Override
        public PlayerShopClaimCreationEvidence createClaims(
                PlayerShopPreparedExecution preparation,
                PlayerShopFundingEvidence funding
        ) {
            calls.add("create claims");
            if (failClaimCreationOnce) {
                failClaimCreationOnce = false;
                throw new PlayerShopBackendException(
                        PlayerShopBackendException.Kind.RECOVERY_REQUIRED,
                        "Simulated claim persistence interruption");
            }
            return new PlayerShopClaimCreationEvidence(
                    preparation.intent().requestId(),
                    PlayerShopClaimCreationEvidence.Status.CREATED,
                    preparation.intent().claims(), "claim batch evidence", "");
        }

        @Override
        public void persistClaimCreation(
                PlayerShopClaimCreationEvidence claims
        ) {
            calls.add("persist claims");
            PlayerShopExecutionSnapshot current = snapshots.get(
                    claims.requestId());
            snapshots.put(claims.requestId(), current.withClaims(claims));
        }

        @Override
        public void persistCommit(PlayerShopAtomicCommit commit) {
            calls.add("persist commit");
            PlayerShopExecutionSnapshot current = snapshots.get(
                    commit.commitId());
            snapshots.put(commit.commitId(), current.withCommit(commit));
        }

        @Override
        public DeliveryResult deliverClaims(
                PlayerShopAtomicCommit commit,
                PlayerShopPreparedExecution preparation
        ) {
            calls.add("deliver claims");
            return new DeliveryResult(deliveryStatus,
                    deliveryStatus == DeliveryStatus.DELIVERED ? ""
                            : "Claims remain safely pending");
        }

        @Override
        public RecoveryResult recover(PlayerShopExecutionSnapshot snapshot) {
            calls.add("recover");
            return new RecoveryResult(RecoveryStatus.RECOVERY_REQUIRED,
                    "Durable custody evidence requires retry");
        }

        @Override
        public void markSettlementImported(
                PlayerShopSettlementImportEvidence settlement,
                PlayerShopAtomicCommit commit
        ) {
            calls.add("mark settlement");
        }
    }
}
