package com.enviouse.futureshops.money;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.init.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.util.UUID;

public final class MoneyMintService {
    private MoneyMintService() {
    }

    /**
     * Mints a single ItemStack of {@code count} coins sharing one {@code mintId}
     * and {@code authorizedCount = count}. Because all coins in the returned stack
     * have byte-identical NBT, vanilla stack-merging will coalesce them with other
     * stacks that share the same mint ID (typically just this one).
     */
    public static ItemStack mintStack(ServerPlayer player, int count, long denominationMinorUnits) {
        ItemStack stack = new ItemStack(ModItems.MONEY_ITEM.get(), count);

        String mintId = UUID.randomUUID().toString();
        long mintTimestamp = Instant.now().getEpochSecond();
        String mintPlayer = player.getUUID().toString();
        String mintServer = Config.moneyMintServerId;

        CompoundTag moneyData = new CompoundTag();
        moneyData.putLong(MoneyNbtKeys.DENOMINATION, denominationMinorUnits);
        moneyData.putString(MoneyNbtKeys.MINT_ID, mintId);
        moneyData.putLong(MoneyNbtKeys.MINT_TIMESTAMP, mintTimestamp);
        moneyData.putString(MoneyNbtKeys.MINT_PLAYER, mintPlayer);
        moneyData.putString(MoneyNbtKeys.MINT_SERVER, mintServer);
        moneyData.putInt(MoneyNbtKeys.AUTHORIZED_COUNT, count);
        moneyData.putString(MoneyNbtKeys.CHECKSUM,
                MoneyChecksumService.createChecksum(denominationMinorUnits, mintId, mintTimestamp,
                        mintPlayer, mintServer, count));

        stack.getOrCreateTag().put(MoneyNbtKeys.ROOT, moneyData);
        return stack;
    }
}
