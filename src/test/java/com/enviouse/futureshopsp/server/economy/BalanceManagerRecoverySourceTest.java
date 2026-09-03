package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.EconomyApi;
import com.enviouse.futureshopsp.api.economy.ProviderLifecycle;
import com.enviouse.futureshopsp.api.economy.RequestId;
import com.enviouse.futureshopsp.server.shop.PlayerShopBarterEscrowSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class BalanceManagerRecoverySourceTest {
    @Test
    void heldCustodyFreezesAfterJournalRecovery() {
        EconomyLifecycleController lifecycle = new EconomyLifecycleController(EconomyApi.INTERNAL_PROVIDER_ID);
        lifecycle.resolve(ProviderLifecycle.READY, "", true, true, false);
        InMemoryEconomyCustodyStore custody = new InMemoryEconomyCustodyStore();
        custody.hold(RequestId.random(), UUID.randomUUID(), "player-shop:test", 1L, "hash");

        assertTrue(BalanceManager.freezeIfUnresolvedItemState(lifecycle, custody, null, null));
        assertTrue(lifecycle.snapshot().lifecycle() == ProviderLifecycle.FROZEN);
    }

    @Test
    void incompleteClaimsFreezeAfterJournalRecovery() {
        EconomyLifecycleController lifecycle = new EconomyLifecycleController(EconomyApi.INTERNAL_PROVIDER_ID);
        lifecycle.resolve(ProviderLifecycle.READY, "", true, true, false);
        InMemoryEconomyClaimStore claims = new InMemoryEconomyClaimStore();
        claims.create(RequestId.random(), UUID.randomUUID(), 25L, "player shop settlement");

        assertTrue(BalanceManager.freezeIfUnresolvedItemState(lifecycle, null, claims, null));
        assertTrue(lifecycle.snapshot().lifecycle() == ProviderLifecycle.FROZEN);
    }

    @Test
    void incompleteBarterEscrowFreezesAfterJournalRecovery(MinecraftServer server) {
        PlayerShopBarterEscrowSavedData escrow = new PlayerShopBarterEscrowSavedData();
        UUID request = UUID.randomUUID();
        assertTrue(escrow.prepare(request, UUID.randomUUID(), 1L, "minecraft:overworld", "minecraft:diamond", 1,
                List.of(new ItemStack(Items.DIAMOND)), server.registryAccess()));
        EconomyLifecycleController lifecycle = new EconomyLifecycleController(EconomyApi.INTERNAL_PROVIDER_ID);
        lifecycle.resolve(ProviderLifecycle.READY, "", true, true, false);

        assertTrue(BalanceManager.freezeIfUnresolvedItemState(lifecycle, null, null, escrow));
        assertTrue(lifecycle.snapshot().lifecycle() == ProviderLifecycle.FROZEN);
    }

    @Test
    void startupReconcilesJournalRecordsBeforeExposingProvider() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp",
                "server", "economy", "BalanceManager.java")));

        assertTrue(source.contains("recoverIncompleteJournalRecords();\n            provider = new CoordinatedEconomyProvider"));
        assertTrue(source.contains("recoverIncompleteJournalRecords();\n            provider = new ExternalLegacyEconomyProvider"));
        assertTrue(source.contains("for (EconomyJournalRecord record : journal.snapshot())"));
        assertTrue(source.contains("coordinator.recover(record.request().requestId())"));
        assertTrue(source.contains("ProviderLifecycle.FROZEN"));
        assertTrue(source.contains("custody.hasIncompleteRecords()"));
        assertTrue(source.contains("freezeIfUnresolvedItemState(lifecycleController, custody, claims, barterEscrow)"));
        assertTrue(source.contains("lifecycle.markAmbiguous(\"item custody requires operator recovery\")"));
        assertTrue(source.contains("barterEscrow.hasIncompleteRecords()"));
        assertTrue(source.contains("lifecycle.markAmbiguous(\"player shop barter escrow requires operator recovery\")"));
        assertTrue(source.contains("claims.hasIncompleteRecords()"));
        assertTrue(source.contains("lifecycle.markAmbiguous(\"economy claims require operator recovery\")"));
    }

    private static Path projectDirectory() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isDirectory(candidate.resolve(Path.of("src", "main", "java")))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("FutureShops source directory is unavailable");
    }
}
