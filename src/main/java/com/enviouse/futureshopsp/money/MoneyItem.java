package com.enviouse.futureshopsp.money;

import com.enviouse.futureshopsp.command.EconomyCommandUtil;
import com.enviouse.futureshopsp.event.MoneyDepositEvent;
import com.enviouse.futureshopsp.api.economy.MutationKind;
import com.enviouse.futureshopsp.api.economy.MutationRequest;
import com.enviouse.futureshopsp.api.economy.ProviderResult;
import com.enviouse.futureshopsp.api.economy.RequestId;
import com.enviouse.futureshopsp.server.economy.BalanceManager;
import com.enviouse.futureshopsp.server.economy.CustodyState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;

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

        if (!BalanceManager.isInternalEconomyReady()) {
            serverPlayer.sendSystemMessage(EconomyCommandUtil.error(Component.translatable(
                    "command.futureshops.economy.internal_only")));
            return InteractionResultHolder.fail(stack);
        }

        SpentMintsSavedData mintData = SpentMintsSavedData.get(serverPlayer.getServer());
        synchronized (mintData) {
            MoneyValidationResult validation = MoneyValidationService.validate(stack);
            MoneyValidationService.ConsumeOutcome preview =
                    MoneyValidationService.preview(serverPlayer.getServer(), stack);

            if (!preview.success()) {
                // Full rejection: invalid checksum / unknown mint / already consumed.
                serverPlayer.sendSystemMessage(
                        EconomyCommandUtil.error(Component.translatable("command.futureshops.deposit.coin_invalid")));
                stack.setCount(0);
                return InteractionResultHolder.fail(stack);
            }

            long acceptedValue;
            try {
                acceptedValue = Math.multiplyExact(preview.denominationMinorUnits(), preview.accepted());
            } catch (ArithmeticException exception) {
                serverPlayer.sendSystemMessage(EconomyCommandUtil.error(
                        Component.translatable("command.futureshops.error.invalid_amount")));
                return InteractionResultHolder.fail(stack);
            }

            RequestId requestId = RequestId.random();
            MutationRequest request = MutationRequest.forPlayer(requestId, serverPlayer.getUUID(),
                    acceptedValue, MutationKind.DEPOSIT);
            CoinData coinData = stack.get(ModDataComponents.COIN_DATA.get());
            String contentHash = coinData == null ? "" : coinData.checksum();
            ProviderResult<com.enviouse.futureshopsp.api.economy.MutationReceipt> mutation =
                    BalanceManager.getCoordinator().executeWithCustody(request, serverPlayer.getUUID(),
                            "money:" + preview.denominationMinorUnits(), preview.accepted(), contentHash,
                            CustodyState.HELD);
            if (!mutation.confirmed()) {
                EconomyCommandUtil.sendProviderError(serverPlayer, mutation);
                return InteractionResultHolder.fail(stack);
            }

            MoneyValidationService.ConsumeOutcome outcome =
                    MoneyValidationService.validateAndConsume(serverPlayer.getServer(), stack);
            if (outcome.accepted() != preview.accepted() || outcome.rejected() != preview.rejected()) {
                ProviderResult<com.enviouse.futureshopsp.api.economy.MutationReceipt> compensation =
                        BalanceManager.getCoordinator().compensate(
                                MutationRequest.forPlayer(requestId.child("compensation"), serverPlayer.getUUID(),
                                        acceptedValue, MutationKind.COMPENSATION));
                if (compensation.confirmed()) {
                    mintData.restore(outcome.mintId(), outcome.accepted(), outcome.denominationMinorUnits(),
                            validation.authorizedCount());
                    try {
                        BalanceManager.getCoordinator().releaseCustody(requestId.child("custody"));
                    } catch (RuntimeException exception) {
                        BalanceManager.getCoordinator().markRecoveryRequired("money deposit custody release requires recovery");
                        serverPlayer.sendSystemMessage(EconomyCommandUtil.error(Component.translatable(
                                "command.futureshops.economy.recovery_required")));
                    }
                } else {
                    BalanceManager.getCoordinator().markRecoveryRequired("money deposit compensation requires recovery");
                    serverPlayer.sendSystemMessage(EconomyCommandUtil.error(Component.translatable(
                            "command.futureshops.economy.recovery_required")));
                }
                return InteractionResultHolder.fail(stack);
            }

            // Remove accepted + rejected coins from the held stack. Rejected coins
            // are destroyed as counterfeit (they never corresponded to a real ledger entry).
            stack.shrink(outcome.accepted() + outcome.rejected());

            try {
                BalanceManager.getCoordinator().deliverCustody(requestId.child("custody"));
                BalanceManager.getCoordinator().claimCustody(requestId.child("custody"));
            } catch (RuntimeException exception) {
                BalanceManager.getCoordinator().markRecoveryRequired("money deposit custody finalization requires recovery");
                serverPlayer.sendSystemMessage(EconomyCommandUtil.error(Component.translatable(
                        "command.futureshops.economy.recovery_required")));
                return InteractionResultHolder.fail(stack);
            }

            // Fire MoneyDepositEvent (spec §33) for the accepted portion only.
            NeoForge.EVENT_BUS.post(
                    new MoneyDepositEvent(serverPlayer.getUUID(), acceptedValue, outcome.accepted()));

            int decimalPlaces = BalanceManager.getDecimalPlaces();
            String currencyName = BalanceManager.getCurrencyName();
            String depositedText = EconomyCommandUtil.formatMinorUnits(acceptedValue, decimalPlaces);
            Component balanceText = EconomyCommandUtil.formatResultingBalance(mutation, serverPlayer.getUUID(),
                    decimalPlaces);
            serverPlayer.sendSystemMessage(EconomyCommandUtil.success(
                    Component.translatable("command.futureshops.deposit.right_click_success",
                            outcome.accepted(), depositedText, currencyName, balanceText)));

            if (outcome.rejected() > 0) {
                serverPlayer.sendSystemMessage(EconomyCommandUtil.warning(Component.translatable(
                        "command.futureshops.deposit.invalid_destroyed", outcome.rejected())));
            }

            return InteractionResultHolder.consume(stack);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        long denom = denominationMinorUnits;
        CoinData data = stack.get(ModDataComponents.COIN_DATA.get());
        if (data != null && data.denomination() > 0L) {
            denom = data.denomination();
        }
        String formatted = EconomyCommandUtil.formatMinorUnits(denom, com.enviouse.futureshopsp.Config.economyCurrencyDecimals);
        tooltip.add(Component.translatable("tooltip.futureshops.money_value", formatted).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.futureshops.money_right_click").withStyle(ChatFormatting.GRAY));
    }
}
