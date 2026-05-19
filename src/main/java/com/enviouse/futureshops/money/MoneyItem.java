package com.enviouse.futureshops.money;

import com.enviouse.futureshops.command.EconomyCommandUtil;
import com.enviouse.futureshops.event.MoneyDepositEvent;
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

import java.util.List;

public class MoneyItem extends Item {
    private final long denominationMinorUnits;

    public MoneyItem(Properties properties, long denominationMinorUnits) {
        super(properties);
        this.denominationMinorUnits = denominationMinorUnits;
    }

    public long getDenominationMinorUnits() {
        return denominationMinorUnits;
    }

    /**
     * Right-click (use) a coin stack to instantly deposit it into your balance.
     * Validates checksum + authorized count + mint remaining-count atomically,
     * accepts up to the remaining balance and destroys any excess as counterfeit.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        int requested = stack.getCount();
        MoneyValidationService.ConsumeOutcome outcome =
                MoneyValidationService.validateAndConsume(serverPlayer.getServer(), stack);

        if (!outcome.success()) {
            // Full rejection: invalid checksum / unknown mint / already consumed.
            serverPlayer.sendSystemMessage(
                    EconomyCommandUtil.error(Component.translatable("command.futureshops.deposit.coin_invalid")));
            stack.setCount(0);
            return InteractionResultHolder.fail(stack);
        }

        long acceptedValue = outcome.acceptedValueMinor();
        EconomyProvider provider = BalanceManager.getProvider();
        TransactionResult result = provider.deposit(serverPlayer.getUUID(), acceptedValue);
        if (!result.success()) {
            // Balance credit failed after we already decremented the mint ledger —
            // we can't cleanly undo a partial consume, so reject the stack entirely.
            EconomyCommandUtil.sendProviderError(serverPlayer, result.errorCode());
            return InteractionResultHolder.fail(stack);
        }

        // Remove accepted + rejected coins from the held stack. Rejected coins
        // are destroyed as counterfeit (they never corresponded to a real ledger
        // entry).
        stack.shrink(outcome.accepted() + outcome.rejected());

        // Fire MoneyDepositEvent (spec §33) for the accepted portion only.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new MoneyDepositEvent(serverPlayer.getUUID(), acceptedValue, outcome.accepted()));

        String depositedText = EconomyCommandUtil.formatMinorUnits(acceptedValue, provider.getDecimalPlaces());
        String balanceText = EconomyCommandUtil.formatMinorUnits(result.resultingBalance(), provider.getDecimalPlaces());
        serverPlayer.sendSystemMessage(EconomyCommandUtil.success(
                Component.translatable("command.futureshops.deposit.right_click_success",
                        outcome.accepted(), depositedText, provider.getCurrencyName(), balanceText)));

        if (outcome.rejected() > 0) {
            serverPlayer.sendSystemMessage(EconomyCommandUtil.warning(Component.translatable(
                    "command.futureshops.deposit.invalid_destroyed", outcome.rejected())));
        }

        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        long denom = denominationMinorUnits;
        CompoundTag root = stack.getTag();
        if (root != null && root.contains(MoneyNbtKeys.ROOT, Tag.TAG_COMPOUND)) {
            CompoundTag moneyData = root.getCompound(MoneyNbtKeys.ROOT);
            if (moneyData.contains(MoneyNbtKeys.DENOMINATION, Tag.TAG_LONG)) {
                long nbtDenom = moneyData.getLong(MoneyNbtKeys.DENOMINATION);
                if (nbtDenom > 0L) {
                    denom = nbtDenom;
                }
            }
        }
        String formatted = EconomyCommandUtil.formatMinorUnits(denom, com.enviouse.futureshops.Config.economyCurrencyDecimals);
        tooltip.add(Component.translatable("tooltip.futureshops.money_value", formatted).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.futureshops.money_right_click").withStyle(ChatFormatting.GRAY));
    }
}

