package com.enviouse.futureshopsp.gametest;

import com.enviouse.futureshopsp.Futureshops;
import com.enviouse.futureshopsp.api.economy.ProviderLifecycle;
import com.enviouse.futureshopsp.init.ModItems;
import com.enviouse.futureshopsp.server.economy.BalanceManager;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Real server proof that the mod and its default economy are ready together. */
@GameTestHolder(Futureshops.MODID)
@PrefixGameTestTemplate(false)
public final class EconomyGameTests {
    private static final UUID FIXTURE_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000231");

    private EconomyGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void internalEconomyAndRegistration(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded(Futureshops.MODID),
                "FutureShops must be loaded in the GameTest server");
        ResourceLocation moneyId = BuiltInRegistries.ITEM.getKey(ModItems.MONEY_ITEM.get());
        helper.assertTrue(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "money").equals(moneyId),
                "the save compatible money item must be registered");

        var lifecycle = BalanceManager.getLifecycleSnapshotOrUnresolved();
        helper.assertTrue(lifecycle.providerId().equals("internal"),
                "a fresh GameTest server must select the internal provider");
        helper.assertTrue(lifecycle.lifecycle() == ProviderLifecycle.READY,
                "the internal provider must be ready before a GameTest mutation");

        var setResult = BalanceManager.setInternalBalance(FIXTURE_PLAYER, 231L);
        helper.assertTrue(setResult.confirmed(), "the internal provider must accept a server mutation");
        var queryResult = BalanceManager.queryBalance(FIXTURE_PLAYER);
        helper.assertTrue(queryResult.confirmed() && queryResult.value().orElseThrow().balanceMinorUnits() == 231L,
                "the server query must return the confirmed balance");
        helper.succeed();
    }
}
