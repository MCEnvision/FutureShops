package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.BalanceSnapshot;
import com.enviouse.futureshopsp.api.economy.CurrencyMetadata;
import com.enviouse.futureshopsp.api.economy.EconomyApi;
import com.enviouse.futureshopsp.api.economy.EconomyProvider;
import com.enviouse.futureshopsp.api.economy.MutationKind;
import com.enviouse.futureshopsp.api.economy.MutationReceipt;
import com.enviouse.futureshopsp.api.economy.MutationRequest;
import com.enviouse.futureshopsp.api.economy.ProviderCapabilities;
import com.enviouse.futureshopsp.api.economy.ProviderError;
import com.enviouse.futureshopsp.api.economy.ProviderLifecycle;
import com.enviouse.futureshopsp.api.economy.ProviderReadiness;
import com.enviouse.futureshopsp.api.economy.ProviderResult;
import com.enviouse.futureshopsp.api.economy.ProviderResultStatus;
import com.enviouse.futureshopsp.api.economy.RequestId;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyTransactionCoordinatorTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000011");

    @Test
    void writesIntentBeforeMutationAndReplaysCompletedRequest() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        EconomyLifecycleController lifecycle = readyLifecycle();
        InMemoryEconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, lifecycle, journal);
        MutationRequest request = MutationRequest.forPlayer(
                new RequestId(UUID.fromString("00000000-0000-0000-0000-000000000011")), PLAYER, 25L,
                MutationKind.WITHDRAW);

        var first = coordinator.withdraw(request);
        var duplicate = coordinator.withdraw(request);

        assertTrue(first.confirmed());
        assertTrue(duplicate.confirmed());
        assertEquals(first.receipt(), duplicate.receipt());
        assertEquals(1, provider.withdrawCalls);
        assertEquals(EconomyTransactionState.RESOLVED,
                journal.find(request.requestId()).orElseThrow().state());
    }

    @Test
    void duplicateRequestWithChangedPayloadIsRejectedBeforeReplay() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        EconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, readyLifecycle(), journal);
        RequestId requestId = RequestId.random();
        MutationRequest original = MutationRequest.forPlayer(requestId, PLAYER, 25L, MutationKind.WITHDRAW);
        MutationRequest conflicting = new MutationRequest(requestId, TARGET, Optional.of(PLAYER), 30L,
                MutationKind.WITHDRAW);

        assertTrue(coordinator.withdraw(original).confirmed());
        var result = coordinator.withdraw(conflicting);

        assertEquals(ProviderResultStatus.REJECTED, result.status());
        assertEquals(ProviderError.INVALID_REQUEST, result.error());
        assertEquals(1, provider.withdrawCalls);
    }

    @Test
    void confirmedTerminalRecordWithoutReceiptFreezesInsteadOfReplayingRejection() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        EconomyLifecycleController lifecycle = readyLifecycle();
        InMemoryEconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, lifecycle, journal);
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.WITHDRAW);
        journal.append(new EconomyJournalRecord(request, EconomyTransactionState.RESOLVED,
                Optional.empty(), ProviderResultStatus.CONFIRMED, "", provider.providerId()));

        var result = coordinator.withdraw(request);

        assertEquals(ProviderResultStatus.RECOVERY_REQUIRED, result.status());
        assertEquals(ProviderLifecycle.FROZEN, lifecycle.snapshot().lifecycle());
        assertEquals(0, provider.withdrawCalls);
    }

    @Test
    void terminalRecordWithMismatchedReceiptFreezesDuringRecovery() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        EconomyLifecycleController lifecycle = readyLifecycle();
        InMemoryEconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, lifecycle, journal);
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.WITHDRAW);
        MutationReceipt mismatched = new MutationReceipt(request.requestId(), MutationKind.DEPOSIT,
                request.amountMinorUnits(), "mismatched", OptionalLong.of(75L));
        journal.append(new EconomyJournalRecord(request, EconomyTransactionState.RESOLVED,
                Optional.of(mismatched), ProviderResultStatus.CONFIRMED, "", provider.providerId()));

        var result = coordinator.recover(request.requestId());

        assertEquals(ProviderResultStatus.RECOVERY_REQUIRED, result.status());
        assertEquals(ProviderLifecycle.FROZEN, lifecycle.snapshot().lifecycle());
        assertEquals(0, provider.withdrawCalls);
    }

    @Test
    void providerBoundRecordCannotBeReplayedByAnotherProvider() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        EconomyLifecycleController lifecycle = readyLifecycle();
        InMemoryEconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, lifecycle, journal);
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.WITHDRAW);
        MutationReceipt receipt = provider.receipt(request);
        journal.append(new EconomyJournalRecord(request, EconomyTransactionState.RESOLVED,
                Optional.of(receipt), ProviderResultStatus.CONFIRMED, "", "other-provider"));

        var result = coordinator.withdraw(request);

        assertEquals(ProviderResultStatus.RECOVERY_REQUIRED, result.status());
        assertEquals(ProviderLifecycle.FROZEN, lifecycle.snapshot().lifecycle());
        assertEquals(0, provider.withdrawCalls);
    }

    @Test
    void missingCapabilityRejectsBeforeJournalOrProviderCall() {
        FixtureProvider provider = new FixtureProvider(new ProviderCapabilities(true, true, true, true, false, false));
        EconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, readyLifecycle(), journal);
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.WITHDRAW);

        var result = coordinator.withdraw(request);

        assertEquals(ProviderResultStatus.UNAVAILABLE, result.status());
        assertEquals(ProviderError.CAPABILITY_MISSING, result.error());
        assertEquals(0, provider.withdrawCalls);
        assertTrue(journal.snapshot().isEmpty());
    }

    @Test
    void intentPersistenceFailureFreezesBeforeProviderMutation() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        FailingJournal journal = new FailingJournal();
        journal.failAppend = true;
        EconomyLifecycleController lifecycle = readyLifecycle();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, lifecycle, journal);
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.WITHDRAW);

        var result = coordinator.withdraw(request);

        assertEquals(ProviderResultStatus.RECOVERY_REQUIRED, result.status());
        assertEquals(ProviderLifecycle.FROZEN, lifecycle.snapshot().lifecycle());
        assertEquals(0, provider.withdrawCalls);
        assertTrue(journal.snapshot().isEmpty());
    }

    @Test
    void journalLookupFailureFreezesBeforeProviderMutation() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        FailingJournal journal = new FailingJournal();
        journal.failFind = true;
        EconomyLifecycleController lifecycle = readyLifecycle();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, lifecycle, journal);
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.WITHDRAW);

        var result = coordinator.withdraw(request);

        assertEquals(ProviderResultStatus.RECOVERY_REQUIRED, result.status());
        assertEquals(ProviderLifecycle.FROZEN, lifecycle.snapshot().lifecycle());
        assertEquals(0, provider.withdrawCalls);
        assertTrue(journal.snapshot().isEmpty());
    }

    @Test
    void capabilityLookupFailureMarksProviderFailedBeforeMutation() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        provider.throwCapabilities = true;
        EconomyLifecycleController lifecycle = readyLifecycle();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, lifecycle,
                new InMemoryEconomyTransactionJournal());
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.WITHDRAW);

        var result = coordinator.withdraw(request);

        assertEquals(ProviderResultStatus.UNAVAILABLE, result.status());
        assertEquals(ProviderError.CAPABILITY_MISSING, result.error());
        assertEquals(ProviderLifecycle.FAILED, lifecycle.snapshot().lifecycle());
        assertEquals(0, provider.withdrawCalls);
    }

    @Test
    void confirmedOutcomePersistenceFailureFreezesAndRetainsConfirmedRecord() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        FailingJournal journal = new FailingJournal();
        journal.failState = EconomyTransactionState.RESOLVED;
        EconomyLifecycleController lifecycle = readyLifecycle();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, lifecycle, journal);
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.WITHDRAW);

        var result = coordinator.withdraw(request);

        assertEquals(ProviderResultStatus.RECOVERY_REQUIRED, result.status());
        assertEquals(ProviderLifecycle.FROZEN, lifecycle.snapshot().lifecycle());
        assertEquals(1, provider.withdrawCalls);
        assertEquals(EconomyTransactionState.EXTERNAL_CONFIRMED,
                journal.find(request.requestId()).orElseThrow().state());
    }

    @Test
    void ambiguousOutcomePersistenceFailureStillFreezesWithoutGuessing() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        provider.ambiguous = true;
        FailingJournal journal = new FailingJournal();
        journal.failState = EconomyTransactionState.UNCERTAIN;
        EconomyLifecycleController lifecycle = readyLifecycle();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, lifecycle, journal);
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.WITHDRAW);

        var result = coordinator.withdraw(request);

        assertEquals(ProviderResultStatus.RECOVERY_REQUIRED, result.status());
        assertEquals(ProviderLifecycle.FROZEN, lifecycle.snapshot().lifecycle());
        assertEquals(EconomyTransactionState.EXTERNAL_PENDING,
                journal.find(request.requestId()).orElseThrow().state());
    }

    @Test
    void ambiguousProviderResultFreezesAndBlocksFurtherMutation() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        provider.ambiguous = true;
        EconomyLifecycleController lifecycle = readyLifecycle();
        InMemoryEconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, lifecycle, journal);
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.WITHDRAW);

        var result = coordinator.withdraw(request);
        var retry = coordinator.withdraw(MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.WITHDRAW));

        assertEquals(ProviderResultStatus.AMBIGUOUS, result.status());
        assertEquals(ProviderLifecycle.FROZEN, lifecycle.snapshot().lifecycle());
        assertEquals(ProviderResultStatus.RECOVERY_REQUIRED, retry.status());
        assertFalse(retry.confirmed());
    }

    @Test
    void providerMutationExceptionFreezesWithAnUncertainJournalRecord() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        provider.throwMutation = true;
        EconomyLifecycleController lifecycle = readyLifecycle();
        InMemoryEconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, lifecycle, journal);
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.WITHDRAW);

        var result = coordinator.withdraw(request);

        assertEquals(ProviderResultStatus.AMBIGUOUS, result.status());
        assertEquals(ProviderLifecycle.FROZEN, lifecycle.snapshot().lifecycle());
        assertEquals(EconomyTransactionState.UNCERTAIN,
                journal.find(request.requestId()).orElseThrow().state());
        assertEquals(1, provider.withdrawCalls);
    }

    @Test
    void mismatchedProviderReceiptFreezesWithoutPublishingSuccess() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        provider.malformedReceipt = true;
        EconomyLifecycleController lifecycle = readyLifecycle();
        InMemoryEconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, lifecycle, journal);
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.WITHDRAW);

        var result = coordinator.withdraw(request);

        assertEquals(ProviderResultStatus.AMBIGUOUS, result.status());
        assertEquals(ProviderLifecycle.FROZEN, lifecycle.snapshot().lifecycle());
        assertEquals(EconomyTransactionState.UNCERTAIN,
                journal.find(request.requestId()).orElseThrow().state());
        assertEquals(100L, provider.balances.get(PLAYER));
    }

    @Test
    void recoveryUsesDurableLookupBeforeRetry() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        EconomyLifecycleController lifecycle = readyLifecycle();
        InMemoryEconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, lifecycle, journal);
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.WITHDRAW);
        MutationReceipt receipt = provider.receipt(request);
        journal.append(new EconomyJournalRecord(request, EconomyTransactionState.EXTERNAL_PENDING,
                Optional.empty(), ProviderResultStatus.UNAVAILABLE, ""));
        provider.receipts.put(request.requestId(), receipt);
        lifecycle.markAmbiguous("test pending");
        lifecycle.markUncleanStart();

        var result = coordinator.recover(request.requestId());

        assertTrue(result.confirmed());
        assertEquals(EconomyTransactionState.RESOLVED,
                journal.find(request.requestId()).orElseThrow().state());
        assertEquals(ProviderLifecycle.READY, lifecycle.snapshot().lifecycle());
    }

    @Test
    void recoveryPersistsReceiptWhenLookupUsesValueOnly() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        provider.lookupValueOnly = true;
        EconomyLifecycleController lifecycle = readyLifecycle();
        InMemoryEconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, lifecycle, journal);
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.WITHDRAW);
        MutationReceipt receipt = provider.receipt(request);
        journal.append(new EconomyJournalRecord(request, EconomyTransactionState.EXTERNAL_PENDING,
                Optional.empty(), ProviderResultStatus.UNAVAILABLE, ""));
        provider.receipts.put(request.requestId(), receipt);
        lifecycle.markUncleanStart();

        var result = coordinator.recover(request.requestId());

        assertTrue(result.confirmed());
        assertEquals(Optional.of(receipt), journal.find(request.requestId()).orElseThrow().receipt());
    }

    @Test
    void missingReceiptDuringRecoveryFreezesInsteadOfGuessingRejection() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        EconomyLifecycleController lifecycle = readyLifecycle();
        InMemoryEconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, lifecycle, journal);
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.WITHDRAW);
        journal.append(new EconomyJournalRecord(request, EconomyTransactionState.EXTERNAL_PENDING,
                Optional.empty(), ProviderResultStatus.UNAVAILABLE, ""));
        lifecycle.markUncleanStart();

        var result = coordinator.recover(request.requestId());

        assertEquals(ProviderResultStatus.RECOVERY_REQUIRED, result.status());
        assertEquals(ProviderLifecycle.FROZEN, lifecycle.snapshot().lifecycle());
        assertEquals(EconomyTransactionState.UNCERTAIN,
                journal.find(request.requestId()).orElseThrow().state());
    }

    @Test
    void lifecycleWritesCleanMarkerOnlyAfterDrainFlushes() {
        EconomyLifecycleController lifecycle = readyLifecycle();
        lifecycle.beginDraining();
        assertFalse(lifecycle.writeCleanMarkerLast(false, true, true, true));
        assertEquals(ProviderLifecycle.DRAINING, lifecycle.snapshot().lifecycle());
        assertTrue(lifecycle.writeCleanMarkerLast(true, true, true, true));
        assertEquals(ProviderLifecycle.STOPPED, lifecycle.snapshot().lifecycle());
    }

    @Test
    void localFinalizationFailureFreezesAdmission() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        EconomyLifecycleController lifecycle = readyLifecycle();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(
                provider, lifecycle, new InMemoryEconomyTransactionJournal());

        coordinator.markRecoveryRequired("claim finalization failed");

        assertEquals(ProviderLifecycle.FROZEN, lifecycle.snapshot().lifecycle());
        assertEquals("claim finalization failed", lifecycle.snapshot().diagnostic());
    }

    @Test
    void custodyAndClaimsAreIdempotentAndConservative() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        EconomyLifecycleController lifecycle = readyLifecycle();
        InMemoryEconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        InMemoryEconomyCustodyStore custody = new InMemoryEconomyCustodyStore();
        InMemoryEconomyClaimStore claims = new InMemoryEconomyClaimStore();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(
                provider, lifecycle, journal, custody, claims);
        RequestId custodyId = RequestId.random();

        CustodyRecord held = coordinator.holdCustody(custodyId, PLAYER, "minecraft:diamond", 2L, "hash");
        assertEquals(held, coordinator.holdCustody(custodyId, PLAYER, "minecraft:diamond", 2L, "hash"));
        assertEquals(CustodyState.DELIVERED, coordinator.deliverCustody(custodyId).state());
        assertEquals(CustodyState.CLAIMED, coordinator.claimCustody(custodyId).state());
        assertEquals(CustodyState.CLAIMED, coordinator.claimCustody(custodyId).state());
        assertThrows(IllegalStateException.class,
                () -> coordinator.releaseCustody(custodyId));

        RequestId claimId = RequestId.random();
        ClaimRecord claim = coordinator.createClaim(claimId, PLAYER, 450L, "offline proceeds");
        assertEquals(claim, coordinator.createClaim(claimId, PLAYER, 450L, "offline proceeds"));
        assertEquals(ClaimState.DELIVERED, coordinator.deliverClaim(claimId).state());
        assertEquals(ClaimState.RESOLVED, coordinator.resolveClaim(claimId).state());
        assertEquals(ClaimState.RESOLVED, coordinator.resolveClaim(claimId).state());
    }

    @Test
    void newClaimIsRefusedDuringDrainBeforeStoreMutation() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        EconomyLifecycleController lifecycle = readyLifecycle();
        InMemoryEconomyClaimStore claims = new InMemoryEconomyClaimStore();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(
                provider, lifecycle, new InMemoryEconomyTransactionJournal(),
                new InMemoryEconomyCustodyStore(), claims);
        lifecycle.beginDraining();

        assertThrows(IllegalStateException.class,
                () -> coordinator.createClaim(RequestId.random(), PLAYER, 450L, "offline proceeds"));
        assertTrue(claims.snapshot().isEmpty());
    }

    @Test
    void executeWithCustodyRefusesMissingCapabilitiesBeforeHolding() {
        FixtureProvider provider = new FixtureProvider(new ProviderCapabilities(true, true, true, true, false, false));
        EconomyLifecycleController lifecycle = readyLifecycle();
        InMemoryEconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        InMemoryEconomyCustodyStore custody = new InMemoryEconomyCustodyStore();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(
                provider, lifecycle, journal, custody, new InMemoryEconomyClaimStore());
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.WITHDRAW);

        var result = coordinator.executeWithCustody(request, PLAYER, "shop:test", 1L, "hash", CustodyState.CLAIMED);

        assertEquals(ProviderError.CAPABILITY_MISSING, result.error());
        assertTrue(custody.snapshot().isEmpty());
        assertTrue(journal.snapshot().isEmpty());
    }

    @Test
    void executeWithCustodyClaimsOnlineDeliveryAfterConfirmation() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        EconomyLifecycleController lifecycle = readyLifecycle();
        InMemoryEconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        InMemoryEconomyCustodyStore custody = new InMemoryEconomyCustodyStore();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(
                provider, lifecycle, journal, custody, new InMemoryEconomyClaimStore());
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.WITHDRAW);

        var result = coordinator.executeWithCustody(request, PLAYER, "shop:test", 1L, "hash", CustodyState.CLAIMED);

        assertTrue(result.confirmed());
        assertEquals(CustodyState.CLAIMED, custody.find(request.requestId().child("custody")).orElseThrow().state());
        assertEquals(EconomyTransactionState.RESOLVED,
                journal.find(request.requestId()).orElseThrow().state());
    }

    @Test
    void custodyFinalizationFailureAfterProviderConfirmationFreezesRecovery() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        EconomyLifecycleController lifecycle = readyLifecycle();
        InMemoryEconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        FailingCustodyStore custody = new FailingCustodyStore();
        custody.failTransition = true;
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(
                provider, lifecycle, journal, custody, new InMemoryEconomyClaimStore());
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.WITHDRAW);

        var result = coordinator.executeWithCustody(request, PLAYER, "shop:test", 1L, "hash",
                CustodyState.DELIVERED);

        assertEquals(ProviderResultStatus.RECOVERY_REQUIRED, result.status());
        assertEquals(ProviderLifecycle.FROZEN, lifecycle.snapshot().lifecycle());
        assertEquals(1, provider.withdrawCalls);
        assertEquals(CustodyState.HELD,
                custody.find(request.requestId().child("custody")).orElseThrow().state());
        assertEquals(EconomyTransactionState.RESOLVED,
                journal.find(request.requestId()).orElseThrow().state());
    }

    @Test
    void custodiedReplayRequiresDurableCustodyBeforeReportingSuccess() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        EconomyLifecycleController lifecycle = readyLifecycle();
        InMemoryEconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, lifecycle, journal);
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.WITHDRAW);
        MutationReceipt receipt = provider.receipt(request);
        journal.append(new EconomyJournalRecord(request, EconomyTransactionState.RESOLVED,
                Optional.of(receipt), ProviderResultStatus.CONFIRMED, ""));

        var result = coordinator.executeWithCustody(request, PLAYER, "shop:test", 1L, "hash",
                CustodyState.CLAIMED);

        assertEquals(ProviderResultStatus.RECOVERY_REQUIRED, result.status());
        assertEquals(ProviderLifecycle.FROZEN, lifecycle.snapshot().lifecycle());
        assertEquals(0, provider.withdrawCalls);
    }

    @Test
    void custodiedReplayWithChangedCustodyPayloadIsRejectedWithoutFreezing() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        EconomyLifecycleController lifecycle = readyLifecycle();
        EconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        EconomyCustodyStore custody = new InMemoryEconomyCustodyStore();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(
                provider, lifecycle, journal, custody, new InMemoryEconomyClaimStore());
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.WITHDRAW);

        assertTrue(coordinator.executeWithCustody(request, PLAYER, "minecraft:diamond", 1L, "hash",
                CustodyState.HELD).confirmed());
        var result = coordinator.executeWithCustody(request, PLAYER, "minecraft:gold", 1L, "hash",
                CustodyState.HELD);

        assertEquals(ProviderResultStatus.REJECTED, result.status());
        assertEquals(ProviderError.INVALID_REQUEST, result.error());
        assertEquals(ProviderLifecycle.READY, lifecycle.snapshot().lifecycle());
        assertEquals(1, provider.withdrawCalls);
    }

    @Test
    void executeWithCustodyPersistsIntentBeforeCustody() {
        List<String> order = new ArrayList<>();
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        EconomyTransactionJournal journal = new RecordingJournal(order);
        EconomyCustodyStore custody = new RecordingCustodyStore(order);
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(
                provider, readyLifecycle(), journal, custody, new InMemoryEconomyClaimStore());
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.WITHDRAW);

        assertTrue(coordinator.executeWithCustody(request, PLAYER, "shop:test", 1L, "hash", CustodyState.DELIVERED).confirmed());
        assertEquals(List.of("journal:PREPARED", "custody:HELD", "journal:EXTERNAL_PENDING"), order.subList(0, 3));
    }

    @Test
    void retainedCustodySurvivesDefinitiveProviderRejection() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        provider.rejectAllDeposits = true;
        InMemoryEconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        InMemoryEconomyCustodyStore custody = new InMemoryEconomyCustodyStore();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(
                provider, readyLifecycle(), journal, custody, new InMemoryEconomyClaimStore());
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.DEPOSIT);

        var result = coordinator.executeWithCustody(request, PLAYER, "minecraft:diamond", 1L, "hash",
                CustodyState.HELD, false);

        assertFalse(result.confirmed());
        assertEquals(CustodyState.HELD, custody.find(request.requestId().child("custody")).orElseThrow().state());
    }

    @Test
    void custodyReleaseFailureAfterDefinitiveProviderRejectionFreezesRecovery() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        provider.rejectAllDeposits = true;
        EconomyLifecycleController lifecycle = readyLifecycle();
        InMemoryEconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        FailingCustodyStore custody = new FailingCustodyStore();
        custody.failTransition = true;
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(
                provider, lifecycle, journal, custody, new InMemoryEconomyClaimStore());
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.DEPOSIT);

        var result = coordinator.executeWithCustody(request, PLAYER, "minecraft:diamond", 1L, "hash",
                CustodyState.RELEASED);

        assertEquals(ProviderResultStatus.RECOVERY_REQUIRED, result.status());
        assertEquals(ProviderLifecycle.FROZEN, lifecycle.snapshot().lifecycle());
        assertEquals(CustodyState.HELD,
                custody.find(request.requestId().child("custody")).orElseThrow().state());
        assertEquals(EconomyTransactionState.RESOLVED,
                journal.find(request.requestId()).orElseThrow().state());
    }

    @Test
    void transferCompensationCreditsSenderAfterCreditRejection() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        provider.rejectFirstDeposit = true;
        EconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, readyLifecycle(), journal);

        var result = coordinator.transfer(PLAYER, TARGET, 25L);

        assertFalse(result.confirmed());
        assertEquals(2, provider.depositCalls);
        assertEquals(100L, provider.balances.get(PLAYER));
        assertEquals(0L, provider.balances.getOrDefault(TARGET, 0L));
    }

    @Test
    void successfulTransferReturnsTheDebitLegBalance() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        EconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, readyLifecycle(), journal);

        var result = coordinator.transfer(PLAYER, TARGET, 25L);

        assertTrue(result.confirmed());
        assertEquals(75L, result.receipt().orElseThrow().resultingBalanceMinorUnits().orElseThrow());
        assertEquals(75L, provider.balances.get(PLAYER));
        assertEquals(25L, provider.balances.getOrDefault(TARGET, 0L));
    }

    @Test
    void transferRefusesBeforeDebitWhenDepositCapabilityIsMissing() {
        FixtureProvider provider = new FixtureProvider(new ProviderCapabilities(true, true, true, false, true, true));
        EconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, readyLifecycle(), journal);

        var result = coordinator.transfer(PLAYER, TARGET, 25L);

        assertEquals(ProviderResultStatus.UNAVAILABLE, result.status());
        assertEquals(ProviderError.CAPABILITY_MISSING, result.error());
        assertEquals(0, provider.withdrawCalls);
        assertEquals(0, provider.depositCalls);
        assertTrue(journal.snapshot().isEmpty());
    }

    @Test
    void failedTransferCompensationFreezesInsteadOfReportingACompletedTransfer() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        provider.rejectAllDeposits = true;
        EconomyLifecycleController lifecycle = readyLifecycle();
        EconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, lifecycle, journal);

        var result = coordinator.transfer(PLAYER, TARGET, 25L);

        assertEquals(ProviderResultStatus.RECOVERY_REQUIRED, result.status());
        assertEquals(ProviderLifecycle.FROZEN, lifecycle.snapshot().lifecycle());
        assertEquals(75L, provider.balances.get(PLAYER));
        assertEquals(0L, provider.balances.getOrDefault(TARGET, 0L));
    }

    @Test
    void compensationIsExecutedAsAcreditedDeposit() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        EconomyLifecycleController lifecycle = readyLifecycle();
        EconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, lifecycle, journal);
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.COMPENSATION);

        var result = coordinator.compensate(request);

        assertTrue(result.confirmed());
        assertEquals(1, provider.depositCalls);
        assertEquals(0, provider.withdrawCalls);
        assertEquals(125L, provider.balances.get(PLAYER));
    }

    @Test
    void compensationRequiresDepositCapabilityRatherThanWithdrawCapability() {
        FixtureProvider provider = new FixtureProvider(new ProviderCapabilities(true, true, false, true, true, true));
        EconomyLifecycleController lifecycle = readyLifecycle();
        EconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, lifecycle, journal);
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.COMPENSATION);

        var result = coordinator.compensate(request);

        assertTrue(result.confirmed());
        assertEquals(1, provider.depositCalls);
        assertEquals(0, provider.withdrawCalls);
    }

    @Test
    void refundIsExecutedAsCreditedDeposit() {
        FixtureProvider provider = new FixtureProvider(ProviderCapabilities.all());
        EconomyLifecycleController lifecycle = readyLifecycle();
        EconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, lifecycle, journal);
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L, MutationKind.REFUND);

        var result = coordinator.refund(request);

        assertTrue(result.confirmed());
        assertEquals(1, provider.depositCalls);
        assertEquals(0, provider.withdrawCalls);
        assertEquals(125L, provider.balances.get(PLAYER));
    }

    private static EconomyLifecycleController readyLifecycle() {
        EconomyLifecycleController lifecycle = new EconomyLifecycleController(EconomyApi.INTERNAL_PROVIDER_ID);
        lifecycle.resolve(ProviderLifecycle.READY, "", true, true, false);
        return lifecycle;
    }

    private static final class FixtureProvider implements EconomyProvider {
        private final ProviderCapabilities capabilities;
        private final Map<UUID, Long> balances = new ConcurrentHashMap<>();
        private final Map<RequestId, MutationReceipt> receipts = new ConcurrentHashMap<>();
        private int withdrawCalls;
        private int depositCalls;
        private boolean ambiguous;
        private boolean rejectFirstDeposit;
        private boolean rejectAllDeposits;
        private boolean lookupValueOnly;
        private boolean throwCapabilities;
        private boolean throwMutation;
        private boolean malformedReceipt;

        private FixtureProvider(ProviderCapabilities capabilities) {
            this.capabilities = capabilities;
            balances.put(PLAYER, 100L);
        }

        @Override
        public String providerId() {
            return "fixture";
        }

        @Override
        public int compatibilityVersion() {
            return EconomyApi.COMPATIBILITY_VERSION;
        }

        @Override
        public CurrencyMetadata currency() {
            return new CurrencyMetadata("Coin", "Coins", 2);
        }

        @Override
        public ProviderCapabilities capabilities() {
            if (throwCapabilities) {
                throw new IllegalStateException("capability failure");
            }
            return capabilities;
        }

        @Override
        public ProviderReadiness readiness() {
            return new ProviderReadiness(ProviderLifecycle.READY, "");
        }

        @Override
        public ProviderResult<BalanceSnapshot> balance(UUID playerId) {
            return ProviderResult.confirmed(new BalanceSnapshot(playerId, balances.getOrDefault(playerId, 0L)));
        }

        @Override
        public ProviderResult<BalanceSnapshot> precheck(MutationRequest request) {
            long balance = balances.getOrDefault(request.actor(), 0L);
            boolean requiresFunds = request.kind() != MutationKind.DEPOSIT
                    && request.kind() != MutationKind.TRANSFER_CREDIT;
            return requiresFunds && balance < request.amountMinorUnits()
                    ? ProviderResult.rejected(ProviderError.INSUFFICIENT_FUNDS, "insufficient")
                    : ProviderResult.confirmed(new BalanceSnapshot(request.actor(), balance));
        }

        @Override
        public ProviderResult<MutationReceipt> withdraw(MutationRequest request) {
            withdrawCalls++;
            if (throwMutation) {
                throw new IllegalStateException("fixture mutation failure");
            }
            if (malformedReceipt) {
                return ProviderResult.confirmed(new MutationReceipt(RequestId.random(), request.kind(),
                        request.amountMinorUnits(), "malformed", OptionalLong.empty()));
            }
            if (ambiguous) {
                return ProviderResult.ambiguous("fixture ambiguity");
            }
            MutationReceipt receipt = receipts.computeIfAbsent(request.requestId(), ignored -> {
                long balance = balances.merge(request.actor(), -request.amountMinorUnits(), Long::sum);
                return new MutationReceipt(request.requestId(), request.kind(), request.amountMinorUnits(),
                        request.requestId().value().toString(), OptionalLong.of(balance));
            });
            return ProviderResult.confirmed(receipt);
        }

        @Override
        public ProviderResult<MutationReceipt> deposit(MutationRequest request) {
            depositCalls++;
            if (rejectAllDeposits || (rejectFirstDeposit && depositCalls == 1)) {
                return ProviderResult.rejected(ProviderError.PROVIDER_EXCEPTION, "fixture credit rejection");
            }
            long balance = balances.merge(request.actor(), request.amountMinorUnits(), Long::sum);
            MutationReceipt receipt = new MutationReceipt(request.requestId(), request.kind(), request.amountMinorUnits(),
                    request.requestId().value().toString(), OptionalLong.of(balance));
            receipts.put(request.requestId(), receipt);
            return ProviderResult.confirmed(receipt);
        }

        @Override
        public ProviderResult<MutationReceipt> lookup(RequestId requestId) {
            MutationReceipt receipt = receipts.get(requestId);
            if (receipt == null) {
                return ProviderResult.rejected(ProviderError.RECEIPT_NOT_FOUND, "missing");
            }
            return lookupValueOnly
                    ? new ProviderResult<>(ProviderResultStatus.CONFIRMED, ProviderError.NONE,
                    Optional.of(receipt), Optional.empty(), "")
                    : ProviderResult.confirmed(receipt);
        }

        @Override
        public ProviderResult<MutationReceipt> retry(MutationRequest request) {
            return withdraw(request);
        }

        private MutationReceipt receipt(MutationRequest request) {
            return new MutationReceipt(request.requestId(), request.kind(), request.amountMinorUnits(),
                    request.requestId().value().toString(), OptionalLong.of(75L));
        }
    }

    private static final class RecordingJournal implements EconomyTransactionJournal {
        private final InMemoryEconomyTransactionJournal delegate = new InMemoryEconomyTransactionJournal();
        private final List<String> order;

        private RecordingJournal(List<String> order) {
            this.order = order;
        }

        @Override
        public Optional<EconomyJournalRecord> find(RequestId requestId) {
            return delegate.find(requestId);
        }

        @Override
        public void append(EconomyJournalRecord record) {
            order.add("journal:" + record.state());
            delegate.append(record);
        }

        @Override
        public void replace(EconomyJournalRecord record) {
            order.add("journal:" + record.state());
            delegate.replace(record);
        }

        @Override
        public List<EconomyJournalRecord> snapshot() {
            return delegate.snapshot();
        }
    }

    private static final class FailingJournal implements EconomyTransactionJournal {
        private final InMemoryEconomyTransactionJournal delegate = new InMemoryEconomyTransactionJournal();
        private boolean failAppend;
        private boolean failFind;
        private EconomyTransactionState failState;

        @Override
        public Optional<EconomyJournalRecord> find(RequestId requestId) {
            if (failFind) {
                throw new IllegalStateException("find failure");
            }
            return delegate.find(requestId);
        }

        @Override
        public void append(EconomyJournalRecord record) {
            if (failAppend) {
                throw new IllegalStateException("append failure");
            }
            delegate.append(record);
        }

        @Override
        public void replace(EconomyJournalRecord record) {
            if (record.state() == failState) {
                throw new IllegalStateException("replace failure");
            }
            delegate.replace(record);
        }

        @Override
        public List<EconomyJournalRecord> snapshot() {
            return delegate.snapshot();
        }
    }

    private static final class RecordingCustodyStore implements EconomyCustodyStore {
        private final InMemoryEconomyCustodyStore delegate = new InMemoryEconomyCustodyStore();
        private final List<String> order;

        private RecordingCustodyStore(List<String> order) {
            this.order = order;
        }

        @Override
        public Optional<CustodyRecord> find(RequestId requestId) {
            return delegate.find(requestId);
        }

        @Override
        public CustodyRecord hold(RequestId requestId, UUID owner, String itemKey, long quantity, String contentHash) {
            CustodyRecord record = delegate.hold(requestId, owner, itemKey, quantity, contentHash);
            order.add("custody:" + record.state());
            return record;
        }

        @Override
        public CustodyRecord transition(RequestId requestId, CustodyState expected, CustodyState next) {
            return delegate.transition(requestId, expected, next);
        }

        @Override
        public List<CustodyRecord> snapshot() {
            return delegate.snapshot();
        }
    }

    private static final class FailingCustodyStore implements EconomyCustodyStore {
        private final InMemoryEconomyCustodyStore delegate = new InMemoryEconomyCustodyStore();
        private boolean failTransition;

        @Override
        public Optional<CustodyRecord> find(RequestId requestId) {
            return delegate.find(requestId);
        }

        @Override
        public CustodyRecord hold(RequestId requestId, UUID owner, String itemKey, long quantity, String contentHash) {
            return delegate.hold(requestId, owner, itemKey, quantity, contentHash);
        }

        @Override
        public CustodyRecord transition(RequestId requestId, CustodyState expected, CustodyState next) {
            if (failTransition) {
                throw new IllegalStateException("transition failure");
            }
            return delegate.transition(requestId, expected, next);
        }

        @Override
        public List<CustodyRecord> snapshot() {
            return delegate.snapshot();
        }
    }
}
