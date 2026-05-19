package com.enviouse.futureshops.money;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.init.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;

public final class MoneyValidationService {
    private MoneyValidationService() {
    }

    public static MoneyValidationResult validate(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != ModItems.MONEY_ITEM.get()) {
            return MoneyValidationResult.error("NOT_COIN");
        }

        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(MoneyNbtKeys.ROOT, Tag.TAG_COMPOUND)) {
            return MoneyValidationResult.error("MISSING_COIN_DATA");
        }

        CompoundTag moneyData = root.getCompound(MoneyNbtKeys.ROOT);
        if (!moneyData.contains(MoneyNbtKeys.DENOMINATION, Tag.TAG_LONG)
            || !moneyData.contains(MoneyNbtKeys.MINT_ID, Tag.TAG_STRING)
            || !moneyData.contains(MoneyNbtKeys.MINT_TIMESTAMP, Tag.TAG_LONG)
            || !moneyData.contains(MoneyNbtKeys.MINT_PLAYER, Tag.TAG_STRING)
            || !moneyData.contains(MoneyNbtKeys.MINT_SERVER, Tag.TAG_STRING)
            || !moneyData.contains(MoneyNbtKeys.AUTHORIZED_COUNT, Tag.TAG_INT)
            || !moneyData.contains(MoneyNbtKeys.CHECKSUM, Tag.TAG_STRING)) {
            return MoneyValidationResult.error("MISSING_FIELDS");
        }

        long denomination = moneyData.getLong(MoneyNbtKeys.DENOMINATION);
        if (denomination <= 0L) {
            return MoneyValidationResult.error("BAD_DENOMINATION");
        }

        int authorizedCount = moneyData.getInt(MoneyNbtKeys.AUTHORIZED_COUNT);
        if (authorizedCount <= 0) {
            return MoneyValidationResult.error("BAD_AUTHORIZED_COUNT");
        }

        // Anti-dupe: the physical stack size can never exceed what the mint record
        // authorizes. If someone cloned NBT onto a larger stack, reject up front.
        if (stack.getCount() > authorizedCount) {
            return MoneyValidationResult.error("OVER_AUTHORIZED");
        }

        long mintedAt = moneyData.getLong(MoneyNbtKeys.MINT_TIMESTAMP);
        long now = Instant.now().getEpochSecond();
        long maxAgeSeconds = (long) Config.moneyMaxAgeDays * 24L * 60L * 60L;
        if (mintedAt > now || now - mintedAt > maxAgeSeconds) {
            return MoneyValidationResult.error("EXPIRED");
        }

        String mintId = moneyData.getString(MoneyNbtKeys.MINT_ID);
        String mintPlayer = moneyData.getString(MoneyNbtKeys.MINT_PLAYER);
        String mintServer = moneyData.getString(MoneyNbtKeys.MINT_SERVER);
        String expected = MoneyChecksumService.createChecksum(denomination, mintId, mintedAt,
                mintPlayer, mintServer, authorizedCount);
        String actual = moneyData.getString(MoneyNbtKeys.CHECKSUM);
        if (!expected.equals(actual)) {
            return MoneyValidationResult.error("BAD_CHECKSUM");
        }

        return MoneyValidationResult.ok(denomination, authorizedCount, mintId);
    }

    /**
     * Validates a coin stack and, if valid, consumes up to {@code stack.getCount()}
     * coins against its mint record. Returns an outcome describing how many coins
     * the server accepted versus rejected (counterfeit/overflow excess).
     *
     * <p>Callers are responsible for crediting balance for {@code accepted} and
     * shrinking the stack by {@code accepted + rejected} (both are removed from
     * the player's inventory — rejected coins are destroyed as counterfeit).</p>
     */
    public static ConsumeOutcome validateAndConsume(MinecraftServer server, ItemStack stack) {
        MoneyValidationResult validation = validate(stack);
        if (!validation.valid()) {
            return ConsumeOutcome.reject(stack.getCount(), validation.errorCode());
        }

        SpentMintsSavedData mintData = SpentMintsSavedData.get(server);
        int requested = stack.getCount();
        SpentMintsSavedData.ConsumeResult r = mintData.consume(validation.mintId(), requested,
                validation.denominationMinorUnits(), validation.authorizedCount());
        return new ConsumeOutcome(r.accepted(), r.rejected(), validation.denominationMinorUnits(),
                validation.mintId(), r.errorCode());
    }

    public record ConsumeOutcome(int accepted, int rejected, long denominationMinorUnits,
                                 String mintId, String errorCode) {
        public boolean success() {
            return accepted > 0;
        }

        public long acceptedValueMinor() {
            return denominationMinorUnits * (long) accepted;
        }

        public static ConsumeOutcome reject(int count, String errorCode) {
            return new ConsumeOutcome(0, count, 0L, "", errorCode);
        }
    }
}
