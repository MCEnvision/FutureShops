package com.enviouse.futureshopsp.gametest;

import com.enviouse.futureshopsp.Futureshops;
import com.enviouse.futureshopsp.api.economy.ProviderLifecycle;
import com.enviouse.futureshopsp.init.ModItems;
import com.enviouse.futureshopsp.server.economy.BalanceManager;
import com.enviouse.futureshopsp.server.shop.PlayerShopBarterEscrowSavedData;
import com.enviouse.futureshopsp.server.shop.PlayerShopSaleEscrowSavedData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
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

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void publicMutationRoutesUseDurableCoordinator(GameTestHelper helper) {
        UUID payer = UUID.fromString("00000000-0000-0000-0000-000000000238");
        UUID recipient = UUID.fromString("00000000-0000-0000-0000-000000000239");
        helper.assertTrue(BalanceManager.setInternalBalance(payer, 1_000L).confirmed(),
                "the fixture payer balance must be initialized");
        helper.assertTrue(BalanceManager.setInternalBalance(recipient, 0L).confirmed(),
                "the fixture recipient balance must be initialized");

        var withdrawal = BalanceManager.withdraw(payer, 250L);
        helper.assertTrue(withdrawal.success() && withdrawal.resultingBalance() == 750L,
                "public withdrawals must use the durable coordinator balance");

        var deposit = BalanceManager.deposit(recipient, 250L);
        helper.assertTrue(deposit.success() && deposit.resultingBalance() == 250L,
                "public deposits must use the durable coordinator balance");

        var transfer = BalanceManager.transfer(payer, recipient, 100L);
        helper.assertTrue(transfer.success() && transfer.resultingBalance() == 650L,
                "public transfers must persist both coordinator legs");
        helper.assertTrue(BalanceManager.queryBalance(recipient).value().orElseThrow().balanceMinorUnits() == 350L,
                "the recipient balance must include the confirmed transfer credit");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void playerShopSaleEscrowLifecycle(GameTestHelper helper) {
        PlayerShopSaleEscrowSavedData escrow = new PlayerShopSaleEscrowSavedData();
        UUID buyer = UUID.fromString("00000000-0000-0000-0000-000000000232");
        UUID request = UUID.fromString("00000000-0000-0000-0000-000000000233");
        ItemStack reward = new ItemStack(Items.DIAMOND, 2);
        var registryAccess = helper.getLevel().registryAccess();

        helper.assertTrue(escrow.prepare(request, buyer, 7L, "minecraft:overworld",
                        "minecraft:diamond", 2L, List.of(reward), registryAccess),
                "sale escrow must persist the exact reward before removal");
        CompoundTag saved = escrow.save(new CompoundTag(), registryAccess);
        PlayerShopSaleEscrowSavedData restored = PlayerShopSaleEscrowSavedData.load(saved, registryAccess);
        helper.assertTrue(restored.integrityValid(), "sale escrow checksum must survive a world save");
        helper.assertTrue(restored.markRemoved(request, List.of(reward), registryAccess),
                "sale escrow must verify the removed reward");
        helper.assertTrue(restored.markDelivered(request), "sale escrow must record delivery");
        helper.assertTrue(restored.markClaimed(request), "sale escrow must record the final claim");
        helper.assertTrue(!restored.hasIncompleteRecords(), "completed sale escrow must not remain pending");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void playerShopSaleEscrowUncleanRestartPreservesRecoveryState(GameTestHelper helper) {
        PlayerShopSaleEscrowSavedData escrow = new PlayerShopSaleEscrowSavedData();
        UUID buyer = UUID.fromString("00000000-0000-0000-0000-000000000234");
        UUID request = UUID.fromString("00000000-0000-0000-0000-000000000235");
        ItemStack reward = new ItemStack(Items.DIAMOND, 1);
        var registryAccess = helper.getLevel().registryAccess();

        helper.assertTrue(escrow.prepare(request, buyer, 8L, "minecraft:overworld",
                        "minecraft:diamond", 1L, List.of(reward), registryAccess),
                "sale escrow must persist intent before an admitted effect");
        escrow.markUnclean();

        PlayerShopSaleEscrowSavedData recovered = PlayerShopSaleEscrowSavedData.load(
                escrow.save(new CompoundTag(), registryAccess), registryAccess);
        helper.assertTrue(recovered.integrityValid(), "unclean sale escrow must retain valid checksums");
        helper.assertTrue(!recovered.cleanMarkerValid(), "unclean save must require recovery");
        helper.assertTrue(recovered.hasIncompleteRecords(), "unresolved sale custody must remain pending");
        helper.assertTrue(recovered.markRecoveryRequired(request),
                "recovery must classify the interrupted sale escrow explicitly");
        helper.assertTrue(recovered.find(request).orElseThrow().state()
                        == PlayerShopSaleEscrowSavedData.State.RECOVERY_REQUIRED,
                "interrupted sale escrow must not be retried as prepared work");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void playerShopBarterEscrowUncleanRestartPreservesRecoveryState(GameTestHelper helper) {
        PlayerShopBarterEscrowSavedData escrow = new PlayerShopBarterEscrowSavedData();
        UUID buyer = UUID.fromString("00000000-0000-0000-0000-000000000236");
        UUID request = UUID.fromString("00000000-0000-0000-0000-000000000237");
        ItemStack payment = new ItemStack(Items.EMERALD, 3);
        var registryAccess = helper.getLevel().registryAccess();

        helper.assertTrue(escrow.prepare(request, buyer, 9L, "minecraft:overworld",
                        "minecraft:emerald", 3, List.of(payment), registryAccess),
                "barter escrow must persist the exact payment before removal");
        helper.assertTrue(escrow.markRemoved(request, List.of(payment), registryAccess),
                "barter escrow must verify the removed payment");
        helper.assertTrue(escrow.markStored(request),
                "barter escrow must record stored custody before the dependent leg");
        escrow.markUnclean();

        PlayerShopBarterEscrowSavedData recovered = PlayerShopBarterEscrowSavedData.load(
                escrow.save(new CompoundTag(), registryAccess), registryAccess);
        helper.assertTrue(recovered.integrityValid(), "unclean barter escrow must retain valid checksums");
        helper.assertTrue(!recovered.cleanMarkerValid(), "unclean barter save must require recovery");
        helper.assertTrue(recovered.hasIncompleteRecords(), "stored barter custody must remain pending");
        helper.assertTrue(recovered.markRecoveryRequired(request),
                "recovery must classify the interrupted barter escrow explicitly");
        helper.assertTrue(recovered.find(request).state()
                        == PlayerShopBarterEscrowSavedData.State.RECOVERY_REQUIRED,
                "interrupted barter escrow must not be retried as stored work");
        helper.succeed();
    }
}
