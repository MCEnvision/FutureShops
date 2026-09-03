package com.enviouse.futureshopsp.compat.pixelmon;

import com.enviouse.futureshopsp.api.economy.BalanceSnapshot;
import com.enviouse.futureshopsp.api.economy.EconomyApi;
import com.enviouse.futureshopsp.api.economy.EconomyCapability;
import com.enviouse.futureshopsp.api.economy.MutationKind;
import com.enviouse.futureshopsp.api.economy.MutationRequest;
import com.enviouse.futureshopsp.api.economy.ProviderError;
import com.enviouse.futureshopsp.api.economy.ProviderLifecycle;
import com.enviouse.futureshopsp.api.economy.ProviderResult;
import com.enviouse.futureshopsp.api.economy.ProviderResultStatus;
import com.enviouse.futureshopsp.api.economy.RequestId;
import com.enviouse.futureshopsp.server.economy.EconomyLifecycleController;
import com.enviouse.futureshopsp.server.economy.EconomyTransactionCoordinator;
import com.enviouse.futureshopsp.server.economy.InMemoryEconomyTransactionJournal;
import com.pixelmonmod.pixelmon.api.economy.BankAccount;
import com.pixelmonmod.pixelmon.api.economy.BankAccountProxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PixelmonEconomyProviderTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000009");
    private TrackingAccount account;

    @BeforeEach
    void setUp() {
        account = new TrackingAccount(PLAYER, new BigDecimal("12500"));
        BankAccountProxy.setImplementation(true);
        BankAccountProxy.setAccount(account);
    }

    @AfterEach
    void tearDown() {
        BankAccountProxy.setAccount(null);
        BankAccountProxy.setImplementation(true);
    }

    @Test
    void exposesOnlyQueryAndPrecheckCapabilities() {
        PixelmonEconomyProvider provider = new PixelmonEconomyProvider();

        assertEquals(PixelmonEconomyProvider.PROVIDER_ID, provider.providerId());
        assertEquals(EconomyApi.COMPATIBILITY_VERSION, provider.compatibilityVersion());
        assertTrue(provider.capabilities().supports(EconomyCapability.BALANCE_QUERY));
        assertTrue(provider.capabilities().supports(EconomyCapability.PRECHECK));
        assertFalse(provider.capabilities().supports(EconomyCapability.WITHDRAW));
        assertFalse(provider.capabilities().supports(EconomyCapability.DEPOSIT));
        assertFalse(provider.capabilities().supports(EconomyCapability.RECEIPT_LOOKUP));
        assertFalse(provider.capabilities().supports(EconomyCapability.IDEMPOTENT_RETRY));
        assertEquals(ProviderLifecycle.READY, provider.readiness().lifecycle());
    }

    @Test
    void readsExactBalanceAndPrechecksFunds() {
        PixelmonEconomyProvider provider = new PixelmonEconomyProvider();
        ProviderResult<BalanceSnapshot> balance = provider.balance(PLAYER);
        assertTrue(balance.confirmed());
        assertEquals(12500L, balance.value().orElseThrow().balanceMinorUnits());

        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 12000L,
                MutationKind.WITHDRAW);
        ProviderResult<BalanceSnapshot> precheck = provider.precheck(request);
        assertTrue(precheck.confirmed());
        assertEquals(1, account.hasBalanceCalls.get());

        ProviderResult<BalanceSnapshot> insufficient = provider.precheck(
                MutationRequest.forPlayer(RequestId.random(), PLAYER, 13000L, MutationKind.FEE));
        assertEquals(ProviderResultStatus.REJECTED, insufficient.status());
        assertEquals(ProviderError.INSUFFICIENT_FUNDS, insufficient.error());
        assertEquals(2, account.hasBalanceCalls.get());
    }

    @Test
    void refusesEveryMutationBeforeCallingBooleanPixelmonMethods() {
        PixelmonEconomyProvider provider = new PixelmonEconomyProvider();
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 1L,
                MutationKind.WITHDRAW);

        assertEquals(ProviderError.CAPABILITY_MISSING, provider.withdraw(request).error());
        assertEquals(ProviderError.CAPABILITY_MISSING, provider.deposit(request).error());
        assertEquals(ProviderError.CAPABILITY_MISSING, provider.retry(request).error());
        assertEquals(ProviderError.CAPABILITY_MISSING, provider.lookup(request.requestId()).error());
        assertEquals(0, account.takeCalls.get());
        assertEquals(0, account.addCalls.get());
    }

    @Test
    void coordinatorRefusesBeforeJournalAndCustodyEffects() {
        PixelmonEconomyProvider provider = new PixelmonEconomyProvider();
        EconomyLifecycleController lifecycle = new EconomyLifecycleController(PixelmonEconomyProvider.PROVIDER_ID);
        lifecycle.resolve(ProviderLifecycle.READY, "", true, true, false);
        InMemoryEconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        EconomyTransactionCoordinator coordinator = new EconomyTransactionCoordinator(provider, lifecycle, journal);
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 10L,
                MutationKind.WITHDRAW);

        ProviderResult<?> result = coordinator.withdraw(request);

        assertEquals(ProviderResultStatus.UNAVAILABLE, result.status());
        assertEquals(ProviderError.CAPABILITY_MISSING, result.error());
        assertTrue(journal.snapshot().isEmpty());
        assertTrue(coordinator.custody(request.requestId()).isEmpty());
        assertEquals(0, account.hasBalanceCalls.get());
        assertEquals(0, account.takeCalls.get());
        assertEquals(0, account.addCalls.get());
    }

    @Test
    void rejectsFractionalBalancesAndUnavailableAccounts() {
        PixelmonEconomyProvider provider = new PixelmonEconomyProvider();
        account.balance = new BigDecimal("1.5");
        assertEquals(ProviderError.PROVIDER_EXCEPTION, provider.balance(PLAYER).error());

        BankAccountProxy.setAccount(null);
        assertEquals(ProviderResultStatus.UNAVAILABLE, provider.balance(PLAYER).status());
        assertEquals(ProviderError.PROVIDER_EXCEPTION, provider.balance(PLAYER).error());

        BankAccountProxy.setImplementation(false);
        assertEquals(ProviderLifecycle.MISSING, provider.readiness().lifecycle());
        assertEquals(ProviderError.NOT_READY, provider.balance(PLAYER).error());
    }

    @Test
    void requiresExactPixelmonVersion() {
        assertTrue(PixelmonEconomyProviderRegistration.isSupportedVersion("9.4.0"));
        assertFalse(PixelmonEconomyProviderRegistration.isSupportedVersion("9.4.0+build"));
        assertFalse(PixelmonEconomyProviderRegistration.isSupportedVersion("9.3.0"));
        assertFalse(PixelmonEconomyProviderRegistration.isSupportedVersion(null));
    }

    private static final class TrackingAccount implements BankAccount {
        private final UUID identifier;
        private BigDecimal balance;
        private final AtomicInteger hasBalanceCalls = new AtomicInteger();
        private final AtomicInteger takeCalls = new AtomicInteger();
        private final AtomicInteger addCalls = new AtomicInteger();

        private TrackingAccount(UUID identifier, BigDecimal balance) {
            this.identifier = identifier;
            this.balance = balance;
        }

        @Override
        public UUID getIdentifier() {
            return identifier;
        }

        @Override
        public BigDecimal getBalance() {
            return balance;
        }

        @Override
        public boolean hasBalance(BigDecimal amount) {
            hasBalanceCalls.incrementAndGet();
            return balance.compareTo(amount) >= 0;
        }

        @Override
        public boolean take(BigDecimal amount) {
            takeCalls.incrementAndGet();
            return false;
        }

        @Override
        public boolean add(BigDecimal amount) {
            addCalls.incrementAndGet();
            return false;
        }
    }
}
