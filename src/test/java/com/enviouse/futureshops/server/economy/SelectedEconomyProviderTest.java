package com.enviouse.futureshops.server.economy;

import com.enviouse.futureshops.api.economy.BalanceSnapshot;
import com.enviouse.futureshops.api.economy.CurrencyMetadata;
import com.enviouse.futureshops.api.economy.EconomyProvider;
import com.enviouse.futureshops.api.economy.MutationKind;
import com.enviouse.futureshops.api.economy.MutationReceipt;
import com.enviouse.futureshops.api.economy.MutationRequest;
import com.enviouse.futureshops.api.economy.ProviderCapabilities;
import com.enviouse.futureshops.api.economy.ProviderError;
import com.enviouse.futureshops.api.economy.ProviderLifecycle;
import com.enviouse.futureshops.api.economy.ProviderReadiness;
import com.enviouse.futureshops.api.economy.ProviderResult;
import com.enviouse.futureshops.api.economy.ProviderResultStatus;
import com.enviouse.futureshops.api.economy.RequestId;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SelectedEconomyProviderTest {
    @Test
    void resolvedProviderOwnsQueriesAndMutations() {
        UUID player = UUID.randomUUID();
        FixtureProvider fixture = new FixtureProvider(player, 10L);
        SelectedEconomyProvider selected = new SelectedEconomyProvider(
                fixture, new CurrencyMetadata("Coin", "Coins", 0));

        assertEquals(10L, selected.getBalance(player));
        assertEquals(7L, selected.withdraw(player, 3L).resultingBalance());
        assertEquals(12L, selected.deposit(player, 5L).resultingBalance());
        assertEquals(12L, selected.getBalance(player));
    }

    @Test
    void crossAccountTransferRefusesWithoutAProviderTransferContract() {
        UUID player = UUID.randomUUID();
        SelectedEconomyProvider selected = new SelectedEconomyProvider(
                new FixtureProvider(player, 10L),
                new CurrencyMetadata("Coin", "Coins", 0));

        TransactionResult result = selected.transfer(player, UUID.randomUUID(), 1L);

        assertFalse(result.success());
        assertEquals(com.enviouse.futureshops.server.shop.ShopResultCode.SERVER_ERROR,
                result.errorCode());
    }

    private static final class FixtureProvider implements EconomyProvider {
        private final Map<UUID, Long> balances = new HashMap<>();
        private final Map<RequestId, MutationReceipt> receipts = new HashMap<>();

        private FixtureProvider(UUID player, long balance) {
            balances.put(player, balance);
        }

        @Override
        public String providerId() {
            return "fixture";
        }

        @Override
        public int compatibilityVersion() {
            return 1;
        }

        @Override
        public com.enviouse.futureshops.api.economy.CurrencyMetadata currency() {
            return new CurrencyMetadata("Coin", "Coins", 0);
        }

        @Override
        public ProviderCapabilities capabilities() {
            return ProviderCapabilities.all();
        }

        @Override
        public ProviderReadiness readiness() {
            return new ProviderReadiness(ProviderLifecycle.READY, "");
        }

        @Override
        public ProviderResult<BalanceSnapshot> balance(UUID playerId) {
            return ProviderResult.confirmed(new BalanceSnapshot(playerId,
                    balances.getOrDefault(playerId, 0L)));
        }

        @Override
        public ProviderResult<BalanceSnapshot> precheck(MutationRequest request) {
            return balance(request.actor());
        }

        @Override
        public ProviderResult<MutationReceipt> withdraw(MutationRequest request) {
            return mutate(request, -1L);
        }

        @Override
        public ProviderResult<MutationReceipt> deposit(MutationRequest request) {
            return mutate(request, 1L);
        }

        @Override
        public ProviderResult<MutationReceipt> lookup(RequestId requestId) {
            MutationReceipt receipt = receipts.get(requestId);
            return receipt == null
                    ? ProviderResult.rejected(ProviderError.RECEIPT_NOT_FOUND, "missing")
                    : ProviderResult.confirmed(receipt);
        }

        @Override
        public ProviderResult<MutationReceipt> retry(MutationRequest request) {
            return lookup(request.requestId());
        }

        private ProviderResult<MutationReceipt> mutate(MutationRequest request, long direction) {
            long before = balances.getOrDefault(request.actor(), 0L);
            long after = before + direction * request.amountMinorUnits();
            if (after < 0L) {
                return ProviderResult.rejected(ProviderError.INSUFFICIENT_FUNDS, "insufficient");
            }
            balances.put(request.actor(), after);
            MutationReceipt receipt = new MutationReceipt(request.requestId(), request.kind(),
                    request.amountMinorUnits(), request.requestId().value().toString(),
                    OptionalLong.of(after));
            receipts.put(request.requestId(), receipt);
            return ProviderResult.confirmed(receipt);
        }
    }
}
