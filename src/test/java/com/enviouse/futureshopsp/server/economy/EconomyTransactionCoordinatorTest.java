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
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyTransactionCoordinatorTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000010");

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
    void lifecycleWritesCleanMarkerOnlyAfterDrainFlushes() {
        EconomyLifecycleController lifecycle = readyLifecycle();
        lifecycle.beginDraining();
        assertFalse(lifecycle.writeCleanMarkerLast(false, true, true, true));
        assertEquals(ProviderLifecycle.DRAINING, lifecycle.snapshot().lifecycle());
        assertTrue(lifecycle.writeCleanMarkerLast(true, true, true, true));
        assertEquals(ProviderLifecycle.STOPPED, lifecycle.snapshot().lifecycle());
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
        private boolean ambiguous;

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
            return balance < request.amountMinorUnits()
                    ? ProviderResult.rejected(ProviderError.INSUFFICIENT_FUNDS, "insufficient")
                    : ProviderResult.confirmed(new BalanceSnapshot(request.actor(), balance));
        }

        @Override
        public ProviderResult<MutationReceipt> withdraw(MutationRequest request) {
            withdrawCalls++;
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
            long balance = balances.merge(request.actor(), request.amountMinorUnits(), Long::sum);
            MutationReceipt receipt = new MutationReceipt(request.requestId(), request.kind(), request.amountMinorUnits(),
                    request.requestId().value().toString(), OptionalLong.of(balance));
            receipts.put(request.requestId(), receipt);
            return ProviderResult.confirmed(receipt);
        }

        @Override
        public ProviderResult<MutationReceipt> lookup(RequestId requestId) {
            MutationReceipt receipt = receipts.get(requestId);
            return receipt == null ? ProviderResult.rejected(ProviderError.RECEIPT_NOT_FOUND, "missing")
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
}
