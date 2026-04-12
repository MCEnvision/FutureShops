package com.enviouse.futureshops.coin;

import com.enviouse.futureshops.command.EconomyCommandUtil;
import com.enviouse.futureshops.init.ModItems;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.economy.TransactionResult;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CoinItem extends Item {
    private final long denominationMinorUnits;

    public CoinItem(Properties properties, long denominationMinorUnits) {
        super(properties);
        this.denominationMinorUnits = denominationMinorUnits;
    }

    public long getDenominationMinorUnits() {
        return denominationMinorUnits;
    }

    /**
     * Right-click (use) a coin to instantly deposit it into your balance.
     * Validates checksum + mint uniqueness before crediting, identical to /deposit logic.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Only process on the server side
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        // --- Validate the coin ---
        CoinValidationResult validation = CoinValidationService.validate(stack);
        if (!validation.valid()) {
            serverPlayer.sendSystemMessage(
                    EconomyCommandUtil.error(Component.translatable("command.futureshops.deposit.coin_invalid")));
            // Destroy the invalid coin
            stack.setCount(0);
            return InteractionResultHolder.fail(stack);
        }

        CompoundTag coinData = stack.getTag().getCompound(CoinNbtKeys.ROOT);
        String mintId = coinData.getString(CoinNbtKeys.MINT_ID);

        SpentMintsSavedData mintData = SpentMintsSavedData.get(serverPlayer.getServer());
        if (!mintData.isKnownAndUnconsumed(mintId)) {
            serverPlayer.sendSystemMessage(
                    EconomyCommandUtil.error(Component.translatable("command.futureshops.deposit.coin_spent")));
            // Destroy the duplicate/already-spent coin
            stack.setCount(0);
            return InteractionResultHolder.fail(stack);
        }

        // --- Calculate value ---
        long denom = coinData.getLong(CoinNbtKeys.DENOMINATION);
        if (denom <= 0L) denom = denominationMinorUnits;
        long totalValue = denom * stack.getCount();

        // --- Credit balance ---
        EconomyProvider provider = BalanceManager.getProvider();
        TransactionResult result = provider.deposit(serverPlayer.getUUID(), totalValue);
        if (!result.success()) {
            EconomyCommandUtil.sendProviderError(serverPlayer, result.errorCode());
            return InteractionResultHolder.fail(stack);
        }

        // --- Consume mints and remove coins ---
        List<String> consumed = new ArrayList<>();
        consumed.add(mintId);
        mintData.consumeMints(consumed);

        // Remove the entire stack from hand
        int count = stack.getCount();
        stack.setCount(0);

        // --- Feedback ---
        String depositedText = EconomyCommandUtil.formatMinorUnits(totalValue, provider.getDecimalPlaces());
        String balanceText = EconomyCommandUtil.formatMinorUnits(result.resultingBalance(), provider.getDecimalPlaces());
        serverPlayer.sendSystemMessage(EconomyCommandUtil.success(
                Component.translatable("command.futureshops.deposit.right_click_success",
                        count, depositedText, provider.getCurrencyName(), balanceText)));

        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        long denom = denominationMinorUnits;
        CompoundTag root = stack.getTag();
        if (root != null && root.contains(CoinNbtKeys.ROOT, Tag.TAG_COMPOUND)) {
            CompoundTag coinData = root.getCompound(CoinNbtKeys.ROOT);
            if (coinData.contains(CoinNbtKeys.DENOMINATION, Tag.TAG_LONG)) {
                long nbtDenom = coinData.getLong(CoinNbtKeys.DENOMINATION);
                if (nbtDenom > 0L) {
                    denom = nbtDenom;
                }
            }
        }
        long whole = denom / 100L;
        long fraction = Math.abs(denom % 100L);
        tooltip.add(Component.translatable("tooltip.futureshops.coin_value", whole, String.format("%02d", fraction)).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.futureshops.coin_right_click").withStyle(ChatFormatting.GRAY));
    }
}

