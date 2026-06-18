package com.enviouse.futureshopsp.money;

import com.enviouse.futureshopsp.Config;
import com.enviouse.futureshopsp.init.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.util.UUID;

public final class MoneyMintService {
    private MoneyMintService() {
    }

    /**
     * Mints a single ItemStack of {@code count} coins sharing one {@code mintId}
     * and {@code authorizedCount = count}. All coins in the returned stack carry an
     * identical {@link CoinData} component value, so vanilla stack-merging coalesces
     * them (component equality is by value) — same behaviour as the 1.20.1 byte-identical
     * NBT version.
     */
    public static ItemStack mintStack(ServerPlayer player, int count, long denominationMinorUnits) {
        ItemStack stack = new ItemStack(ModItems.MONEY_ITEM.get(), count);

        String mintId = UUID.randomUUID().toString();
        long mintTimestamp = Instant.now().getEpochSecond();
        String mintPlayer = player.getUUID().toString();
        String mintServer = Config.moneyMintServerId;
        String checksum = MoneyChecksumService.createChecksum(denominationMinorUnits, mintId, mintTimestamp,
                mintPlayer, mintServer, count);

        stack.set(ModDataComponents.COIN_DATA.get(),
                new CoinData(denominationMinorUnits, mintId, mintTimestamp, mintPlayer, mintServer, count, checksum));
        return stack;
    }
}
