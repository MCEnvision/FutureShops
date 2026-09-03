package com.enviouse.futureshopsp.api.economy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyProviderApiTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void validatesStableProviderIdentifiersAndReservedNames() {
        assertTrue(EconomyApi.isValidProviderId("pixelmon"));
        assertTrue(EconomyApi.isValidProviderId("provider_2"));
        assertFalse(EconomyApi.isValidProviderId("PokeDollars"));
        assertFalse(EconomyApi.isValidProviderId("p"));
        assertFalse(EconomyApi.isValidProviderId("provider-name"));
        assertTrue(EconomyApi.isReservedProviderId(EconomyApi.INTERNAL_PROVIDER_ID));
        assertTrue(EconomyApi.isReservedProviderId(EconomyApi.VAULT_PROVIDER_ID));
        assertFalse(EconomyApi.isReservedProviderId("pixelmon"));
    }

    @Test
    void validatesCurrencyMetadataAndMutationRequests() {
        CurrencyMetadata metadata = new CurrencyMetadata("Dollar", "Dollars", 2);
        assertEquals("Dollar", metadata.singularName());
        assertEquals(2, metadata.decimalPlaces());

        MutationRequest request = MutationRequest.forPlayer(
                RequestId.random(), PLAYER, 125L, MutationKind.WITHDRAW);
        assertEquals(125L, request.amountMinorUnits());
        assertTrue(request.counterparty().isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> MutationRequest.forPlayer(RequestId.random(), PLAYER, 0L, MutationKind.DEPOSIT));
        assertThrows(IllegalArgumentException.class, () -> new CurrencyMetadata("", "Dollars", 2));
        assertThrows(IllegalArgumentException.class, () -> new CurrencyMetadata("Dollar", "Dollars", 7));
    }

    @Test
    void preservesUnavailableAndAmbiguousOutcomesWithoutImplicitBalance() {
        ProviderResult<BalanceSnapshot> unavailable = ProviderResult.unavailable(
                ProviderError.CAPABILITY_MISSING, "receipt lookup is unavailable");
        assertEquals(ProviderResultStatus.UNAVAILABLE, unavailable.status());
        assertEquals(ProviderError.CAPABILITY_MISSING, unavailable.error());
        assertTrue(unavailable.value().isEmpty());
        assertTrue(unavailable.receipt().isEmpty());
        assertFalse(unavailable.confirmed());
        assertTrue(unavailable.safeToRetry());

        ProviderResult<BalanceSnapshot> ambiguous = ProviderResult.ambiguous("outcome not proven");
        assertEquals(ProviderResultStatus.AMBIGUOUS, ambiguous.status());
        assertFalse(ambiguous.safeToRetry());
        assertThrows(IllegalArgumentException.class, () -> new ProviderResult<>(
                ProviderResultStatus.CONFIRMED,
                ProviderError.NONE,
                Optional.empty(),
                Optional.empty(),
                ""));
    }

    @Test
    void confirmedMutationCarriesDurableReceiptIdentity() {
        RequestId requestId = RequestId.random();
        MutationReceipt receipt = new MutationReceipt(
                requestId,
                MutationKind.DEPOSIT,
                500L,
                "external-operation-1",
                OptionalLong.of(2500L));
        ProviderResult<MutationReceipt> result = ProviderResult.confirmed(receipt);

        assertTrue(result.confirmed());
        assertEquals(requestId, result.value().orElseThrow().requestId());
        assertEquals(receipt, result.receipt().orElseThrow());
    }

    @Test
    void providerContractCanBeImplementedWithoutInternalPackages() {
        EconomyProvider provider = new FixtureProvider();
        assertEquals("fixture", provider.providerId());
        assertEquals(EconomyApi.COMPATIBILITY_VERSION, provider.compatibilityVersion());
        assertTrue(provider.capabilities().supports(EconomyCapability.RECEIPT_LOOKUP));
        assertTrue(provider.readiness().ready());
        assertEquals(100L, provider.balance(PLAYER).value().orElseThrow().balanceMinorUnits());
    }

    @Test
    void publicApiSourcesDoNotImportImplementationOrOptionalPlatformPackages() throws IOException {
        Path sourceRoot = findSourceRoot();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String source = Files.readString(path);
                    assertFalse(source.contains("import com.enviouse.futureshopsp.server."), path.toString());
                    assertFalse(source.contains("import pixelmon"), path.toString());
                    assertFalse(source.contains("import org.bukkit"), path.toString());
                    assertFalse(source.contains("import net.milkbowl"), path.toString());
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
    }

    private static Path findSourceRoot() {
        Path current = Path.of("").toAbsolutePath();
        Path relative = Path.of("src/main/java/com/enviouse/futureshopsp/api/economy");
        while (current != null && !Files.isDirectory(current.resolve(relative))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("project source root was not found");
        }
        return current.resolve(relative);
    }

    private static final class FixtureProvider implements EconomyProvider {
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
            return ProviderCapabilities.all();
        }

        @Override
        public ProviderReadiness readiness() {
            return new ProviderReadiness(ProviderLifecycle.READY, "");
        }

        @Override
        public ProviderResult<BalanceSnapshot> balance(UUID playerId) {
            return ProviderResult.confirmed(new BalanceSnapshot(playerId, 100L));
        }

        @Override
        public ProviderResult<BalanceSnapshot> precheck(MutationRequest request) {
            return ProviderResult.confirmed(new BalanceSnapshot(request.actor(), 100L));
        }

        @Override
        public ProviderResult<MutationReceipt> withdraw(MutationRequest request) {
            return ProviderResult.recoveryRequired("fixture");
        }

        @Override
        public ProviderResult<MutationReceipt> deposit(MutationRequest request) {
            return ProviderResult.recoveryRequired("fixture");
        }

        @Override
        public ProviderResult<MutationReceipt> lookup(RequestId requestId) {
            return ProviderResult.rejected(ProviderError.RECEIPT_NOT_FOUND, "fixture");
        }

        @Override
        public ProviderResult<MutationReceipt> retry(MutationRequest request) {
            return ProviderResult.recoveryRequired("fixture");
        }
    }
}
