package com.enviouse.futureshops.coin;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.init.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.util.UUID;

public final class CoinMintService {
    private CoinMintService() {
    }

    public static ItemStack mintStack(ServerPlayer player, int count, long denominationMinorUnits) {
        ItemStack stack = new ItemStack(ModItems.COIN_ITEM.get(), count);

        String mintId = UUID.randomUUID().toString();
        long mintTimestamp = Instant.now().getEpochSecond();
        String mintPlayer = player.getUUID().toString();
        String mintServer = Config.coinMintServerId;

        CompoundTag coinData = new CompoundTag();
        coinData.putLong(CoinNbtKeys.DENOMINATION, denominationMinorUnits);
        coinData.putString(CoinNbtKeys.MINT_ID, mintId);
        coinData.putLong(CoinNbtKeys.MINT_TIMESTAMP, mintTimestamp);
        coinData.putString(CoinNbtKeys.MINT_PLAYER, mintPlayer);
        coinData.putString(CoinNbtKeys.MINT_SERVER, mintServer);
        coinData.putString(CoinNbtKeys.CHECKSUM, CoinChecksumService.createChecksum(denominationMinorUnits, mintId, mintTimestamp, mintPlayer, mintServer));

        stack.getOrCreateTag().put(CoinNbtKeys.ROOT, coinData);
        return stack;
    }
}

