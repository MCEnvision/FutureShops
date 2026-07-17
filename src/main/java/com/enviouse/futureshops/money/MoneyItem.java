package com.enviouse.futureshops.money;

import com.enviouse.futureshops.command.EconomyCommandUtil;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.escrow.runtime.EscrowCashDepositService;
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
import java.util.OptionalLong;

public class MoneyItem extends Item {
    private final long denominationMinorUnits;

    public MoneyItem(Properties properties, long denominationMinorUnits) {
        super(properties);
        this.denominationMinorUnits = denominationMinorUnits;
    }

    public long getDenominationMinorUnits() {
        return denominationMinorUnits;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        int decimalPlaces;
        String currencyName;
        String providerId;
        EscrowCashDepositService.DepositRequest request;
        try (CurrencyManager.ConfigurationReadLease ignored =
                     CurrencyManager.acquireConfigurationReadLease()) {
            EconomyProvider provider = BalanceManager.getProvider();
            decimalPlaces = provider.getDecimalPlaces();
            currencyName = provider.getCurrencyName();
            PhysicalCurrencyAdapter currency = CurrencyManager.getOrNull();
            providerId = currency == null ? "unknown" : currency.id();
            EscrowCashDepositService.Source source = hand
                    == InteractionHand.MAIN_HAND
                    ? EscrowCashDepositService.Source.MAIN_HAND
                    : EscrowCashDepositService.Source.OFF_HAND;
            OptionalLong all = OptionalLong.empty();
            request = EscrowCashDepositService.requestForCurrentState(
                    serverPlayer, source, all);
        }
        var result = EscrowCashDepositService.deposit(
                serverPlayer, request);
        if (result.successful()) {
                String depositedText = EconomyCommandUtil.formatMinorUnits(
                        result.depositedMinorUnits(), decimalPlaces);
                String balanceText = EconomyCommandUtil.formatMinorUnits(
                        result.resultingBalanceMinorUnits(),
                        decimalPlaces);
                serverPlayer.sendSystemMessage(EconomyCommandUtil.success(
                        Component.translatable(
                                "command.futureshops.deposit.right_click_success",
                                result.itemsConsumed(), depositedText,
                                currencyName, balanceText)));
                return InteractionResultHolder.consume(
                        serverPlayer.getItemInHand(hand));
            }
        Component message = switch (result.status()) {
                case WRONG_PROVIDER -> Component.translatable(
                        "command.futureshops.deposit.wrong_currency",
                        providerId);
                case CREATIVE_BLOCKED -> Component.translatable(
                        "command.futureshops.deposit.creative_blocked");
                case LEGACY_MIGRATION_REQUIRED -> Component.translatable(
                        "command.futureshops.deposit.legacy_migration_required");
                case CONFIG_CHANGED -> Component.translatable(
                        "command.futureshops.deposit.config_changed");
                case REQUEST_CONFLICT -> Component.translatable(
                        "command.futureshops.deposit.request_conflict");
                case CANCELLED -> Component.translatable(
                        "command.futureshops.deposit.cancelled");
                case RATE_LIMITED -> Component.translatable(
                        "command.futureshops.deposit.rate_limited",
                        result.retryAfterSeconds());
                case ESCROW_UNAVAILABLE, RECOVERY_REQUIRED ->
                        Component.translatable(
                                "command.futureshops.deposit.recovery_required");
                case NO_CURRENCY, INVALID_AMOUNT, NOT_ENOUGH_CURRENCY,
                        INVALID_DENOMINATION, INVALID_CURRENCY ->
                        Component.translatable(
                                "command.futureshops.deposit.money_invalid");
                case TOO_MANY_ITEMS -> Component.translatable(
                        "command.futureshops.deposit.too_many_items",
                        EscrowCashDepositService.MAX_ITEMS_CONSUMED);
                case SUCCESS -> throw new IllegalStateException(
                        "Successful cash deposit was handled earlier");
            };
        serverPlayer.sendSystemMessage(
                EconomyCommandUtil.warning(message));
        return InteractionResultHolder.fail(
                serverPlayer.getItemInHand(hand));
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
