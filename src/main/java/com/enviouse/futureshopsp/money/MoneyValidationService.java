package com.enviouse.futureshopsp.money;

import com.enviouse.futureshopsp.Config;
import com.enviouse.futureshopsp.init.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.time.Instant;

public final class MoneyValidationService {
    private MoneyValidationService() {
    }

    public static MoneyValidationResult validate(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != ModItems.MONEY_ITEM.get()) {
            return MoneyValidationResult.error("NOT_COIN");
        }

        CoinData data = stack.get(ModDataComponents.COIN_DATA.get());
        if (data == null) {
            // Legacy rescue (Decision B). Verified empirically via the DataFixer probe: on a
            // 1.20.1 -> 1.21.1 world upgrade the old `futureshops:coin_data` NBT compound lands in
            // the vanilla `minecraft:custom_data` component under the identical key, all 7 fields
            // byte-intact. Promote it to the typed component on first touch.
            data = rescueLegacy(stack);
            if (data == null) {
                return MoneyValidationResult.error("MISSING_COIN_DATA");
            }
        }

        long denomination = data.denomination();
        if (denomination <= 0L) {
            return MoneyValidationResult.error("BAD_DENOMINATION");
        }

        int authorizedCount = data.authorizedCount();
        if (authorizedCount <= 0) {
            return MoneyValidationResult.error("BAD_AUTHORIZED_COUNT");
        }

        // Anti-dupe: the physical stack size can never exceed what the mint record authorizes.
        if (stack.getCount() > authorizedCount) {
            return MoneyValidationResult.error("OVER_AUTHORIZED");
        }

        long mintedAt = data.mintTimestamp();
        long now = Instant.now().getEpochSecond();
        long maxAgeSeconds = (long) Config.moneyMaxAgeDays * 24L * 60L * 60L;
        if (mintedAt > now || now - mintedAt > maxAgeSeconds) {
            return MoneyValidationResult.error("EXPIRED");
        }

        // Integrity gate — recomputed identically for fresh and rescued coins, so a tampered
        // legacy coin (someone edited custom_data in NBT) fails here exactly like a tampered fresh one.
        String expected = MoneyChecksumService.createChecksum(denomination, data.mintId(), mintedAt,
                data.mintPlayer(), data.mintServer(), authorizedCount);
        if (!expected.equals(data.checksum())) {
            return MoneyValidationResult.error("BAD_CHECKSUM");
        }

        return MoneyValidationResult.ok(denomination, authorizedCount, data.mintId());
    }

    /**
     * Reconstructs {@link CoinData} from a legacy coin's {@code minecraft:custom_data} and promotes
     * it to the typed {@code futureshops:coin_data} component, leaving the stack byte-for-byte
     * component-identical to a freshly minted coin of the same batch (so the two stack together):
     * the legacy sub-key is removed, and if {@code custom_data} becomes empty it is removed entirely
     * rather than left as a residual empty component (component equality includes presence).
     *
     * @return the reconstructed {@link CoinData}, or {@code null} if the stack carries no valid
     *         legacy coin data (missing component or any missing/mistyped field → treated invalid).
     */
    private static CoinData rescueLegacy(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) {
            return null;
        }
        CompoundTag root = custom.copyTag();
        if (!root.contains(MoneyNbtKeys.ROOT, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag md = root.getCompound(MoneyNbtKeys.ROOT);
        // Mirror the old MISSING_FIELDS contract: every typed field must be present.
        if (!md.contains(MoneyNbtKeys.DENOMINATION, Tag.TAG_LONG)
                || !md.contains(MoneyNbtKeys.MINT_ID, Tag.TAG_STRING)
                || !md.contains(MoneyNbtKeys.MINT_TIMESTAMP, Tag.TAG_LONG)
                || !md.contains(MoneyNbtKeys.MINT_PLAYER, Tag.TAG_STRING)
                || !md.contains(MoneyNbtKeys.MINT_SERVER, Tag.TAG_STRING)
                || !md.contains(MoneyNbtKeys.AUTHORIZED_COUNT, Tag.TAG_INT)
                || !md.contains(MoneyNbtKeys.CHECKSUM, Tag.TAG_STRING)) {
            return null;
        }

        CoinData data = new CoinData(
                md.getLong(MoneyNbtKeys.DENOMINATION),
                md.getString(MoneyNbtKeys.MINT_ID),
                md.getLong(MoneyNbtKeys.MINT_TIMESTAMP),
                md.getString(MoneyNbtKeys.MINT_PLAYER),
                md.getString(MoneyNbtKeys.MINT_SERVER),
                md.getInt(MoneyNbtKeys.AUTHORIZED_COUNT),
                md.getString(MoneyNbtKeys.CHECKSUM));

        // Promote to the typed component.
        stack.set(ModDataComponents.COIN_DATA.get(), data);

        // Strip the legacy remnant; remove custom_data entirely if it is now empty so the rescued
        // stack is component-identical to a fresh mint (no residual empty CustomData).
        root.remove(MoneyNbtKeys.ROOT);
        if (root.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        }

        return data;
    }

    /**
     * Validates a coin stack and, if valid, consumes up to {@code stack.getCount()}
     * coins against its mint record. Returns an outcome describing how many coins
     * the server accepted versus rejected (counterfeit/overflow excess).
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

    /** Plans a deposit without changing the item stack or mint ledger. */
    public static ConsumeOutcome preview(MinecraftServer server, ItemStack stack) {
        MoneyValidationResult validation = validate(stack);
        if (!validation.valid()) {
            return ConsumeOutcome.reject(stack.getCount(), validation.errorCode());
        }
        SpentMintsSavedData mintData = SpentMintsSavedData.get(server);
        int accepted = Math.min(stack.getCount(), mintData.remainingCount(validation.mintId()));
        int rejected = stack.getCount() - accepted;
        if (accepted <= 0) {
            return ConsumeOutcome.reject(stack.getCount(), "ALREADY_CONSUMED");
        }
        return new ConsumeOutcome(accepted, rejected, validation.denominationMinorUnits(),
                validation.mintId(), rejected > 0 ? "EXCESS" : "");
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
