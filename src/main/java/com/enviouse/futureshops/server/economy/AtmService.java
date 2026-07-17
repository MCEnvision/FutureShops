package com.enviouse.futureshops.server.economy;

import com.enviouse.futureshops.data.AtmDenominationData;
import com.enviouse.futureshops.money.CurrencyManager;
import com.enviouse.futureshops.money.CurrencyWithdrawalService;
import com.enviouse.futureshops.money.PhysicalCurrencyAdapter;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.S2CAtmDataPacket;
import com.enviouse.futureshops.network.packets.S2CAtmResultPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** Server-owned ATM catalog and withdrawal request entry point. */
public final class AtmService {
    private AtmService() {
    }

    public static void sendData(ServerPlayer player, boolean openScreen) {
        PhysicalCurrencyAdapter currency = CurrencyManager.get();
        EconomyProvider economy = BalanceManager.getProvider();
        List<AtmDenominationData> denominations = new ArrayList<>();
        List<PhysicalCurrencyAdapter.Denomination> configured = currency.denominations();
        int advertisedCount = Math.min(configured.size(), CurrencyWithdrawalService.MAX_DENOMINATIONS);
        for (int i = 0; i < advertisedCount; i++) {
            PhysicalCurrencyAdapter.Denomination denomination = configured.get(i);
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(denomination.item());
            if (key == null) continue;
            denominations.add(new AtmDenominationData(
                    key.toString(), denomination.valueMinor(),
                    Math.max(1, new ItemStack(denomination.item()).getMaxStackSize())));
        }
        ShopPackets.sendToPlayer(player, new S2CAtmDataPacket(
                economy.getBalance(player.getUUID()), economy.getCurrencyName(), economy.getDecimalPlaces(),
                currency.id(), currency.isInternal(), CurrencyWithdrawalService.signature(currency),
                denominations, openScreen));
    }

    public static void withdraw(ServerPlayer player, String signature, List<Integer> counts) {
        CurrencyWithdrawalService.Result result =
                CurrencyWithdrawalService.withdrawSelected(player, signature, counts);
        ShopPackets.sendToPlayer(player, new S2CAtmResultPacket(
                result.success(), result.code().name(), result.resultingBalance(), result.amountMinor()));
        if (result.code() == CurrencyWithdrawalService.Code.CURRENCY_CHANGED) {
            sendData(player, false);
        }
    }
}
