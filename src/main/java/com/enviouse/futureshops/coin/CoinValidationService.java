package com.enviouse.futureshops.coin;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.init.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;

public final class CoinValidationService {
    private CoinValidationService() {
    }

    public static CoinValidationResult validate(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != ModItems.COIN_ITEM.get()) {
            return CoinValidationResult.error("NOT_COIN");
        }

        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(CoinNbtKeys.ROOT, Tag.TAG_COMPOUND)) {
            return CoinValidationResult.error("MISSING_COIN_DATA");
        }

        CompoundTag coinData = root.getCompound(CoinNbtKeys.ROOT);
        if (!coinData.contains(CoinNbtKeys.DENOMINATION, Tag.TAG_LONG)
            || !coinData.contains(CoinNbtKeys.MINT_ID, Tag.TAG_STRING)
            || !coinData.contains(CoinNbtKeys.MINT_TIMESTAMP, Tag.TAG_LONG)
            || !coinData.contains(CoinNbtKeys.MINT_PLAYER, Tag.TAG_STRING)
            || !coinData.contains(CoinNbtKeys.MINT_SERVER, Tag.TAG_STRING)
            || !coinData.contains(CoinNbtKeys.CHECKSUM, Tag.TAG_STRING)) {
            return CoinValidationResult.error("MISSING_FIELDS");
        }

        long denomination = coinData.getLong(CoinNbtKeys.DENOMINATION);
        if (denomination <= 0L || denomination != ModItems.COIN_DENOMINATION_MINOR_UNITS) {
            return CoinValidationResult.error("BAD_DENOMINATION");
        }

        long mintedAt = coinData.getLong(CoinNbtKeys.MINT_TIMESTAMP);
        long now = Instant.now().getEpochSecond();
        long maxAgeSeconds = (long) Config.coinMaxAgeDays * 24L * 60L * 60L;
        if (mintedAt > now || now - mintedAt > maxAgeSeconds) {
            return CoinValidationResult.error("EXPIRED");
        }

        String mintId = coinData.getString(CoinNbtKeys.MINT_ID);
        String mintPlayer = coinData.getString(CoinNbtKeys.MINT_PLAYER);
        String mintServer = coinData.getString(CoinNbtKeys.MINT_SERVER);
        String expected = CoinChecksumService.createChecksum(denomination, mintId, mintedAt, mintPlayer, mintServer);
        String actual = coinData.getString(CoinNbtKeys.CHECKSUM);
        if (!expected.equals(actual)) {
            return CoinValidationResult.error("BAD_CHECKSUM");
        }

        return CoinValidationResult.ok(denomination);
    }
}
