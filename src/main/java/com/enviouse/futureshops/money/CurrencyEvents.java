package com.enviouse.futureshops.money;

import com.enviouse.futureshops.Futureshops;
import com.enviouse.futureshops.command.EconomyCommandUtil;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.escrow.runtime.EscrowCashDepositService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Right-click depositing for FOREIGN currency. The built-in money item handles
 * this in MoneyItem.use, but a foreign mod's item can't inherit that behavior,
 * so this mirrors it via the interact event. Like MoneyItem.use it only
 * triggers when the click didn't interact with a block (RightClickItem fires
 * for use-in-air), so e.g. placing an accept-only money BLOCK still works by
 * clicking the ground.
 */
@Mod.EventBusSubscriber(modid = Futureshops.MODID)
public final class CurrencyEvents {

    /**
     * Game time of the last MAIN_HAND currency click handled per player. The
     * client can't cancel the hand loop for foreign items (their Item.use
     * returns PASS), so one right-click sends use packets for BOTH hands —
     * without this guard a main-hand deposit would silently drag the offhand
     * stack along in the same tick.
     */
    private static final Map<UUID, Long> LAST_MAIN_HAND_HANDLED = new ConcurrentHashMap<>();

    private CurrencyEvents() {
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int decimalPlaces;
        String currencyName;
        EscrowCashDepositService.DepositRequest request;
        try (CurrencyManager.ConfigurationReadLease ignored =
                     CurrencyManager.acquireConfigurationReadLease()) {
        PhysicalCurrencyAdapter currency = CurrencyManager.getOrNull();
        if (currency == null || currency.isInternal()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        long unitValue = currency.unitValueMinor(stack);
        if (unitValue <= 0L) {
            return;
        }

        long gameTime = player.serverLevel().getGameTime();
        if (event.getHand() == InteractionHand.OFF_HAND
                && Long.valueOf(gameTime).equals(LAST_MAIN_HAND_HANDLED.get(player.getUUID()))) {
            return; // main hand already handled this click's deposit
        }
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            LAST_MAIN_HAND_HANDLED.put(player.getUUID(), gameTime);
        }

        EconomyProvider provider = BalanceManager.getProvider();
        decimalPlaces = provider.getDecimalPlaces();
        currencyName = provider.getCurrencyName();
        EscrowCashDepositService.Source source = event.getHand()
                == InteractionHand.MAIN_HAND
                ? EscrowCashDepositService.Source.MAIN_HAND
                : EscrowCashDepositService.Source.OFF_HAND;
        OptionalLong all = OptionalLong.empty();
        request = EscrowCashDepositService.requestForCurrentState(
                player, source, all);
        }
        var result = EscrowCashDepositService.deposit(player, request);
        event.setCanceled(true);
        if (result.successful()) {
            String depositedText = EconomyCommandUtil.formatMinorUnits(
                    result.depositedMinorUnits(),
                    decimalPlaces);
            String balanceText = EconomyCommandUtil.formatMinorUnits(
                    result.resultingBalanceMinorUnits(),
                    decimalPlaces);
            player.sendSystemMessage(EconomyCommandUtil.success(
                    Component.translatable(
                            "command.futureshops.deposit.right_click_success",
                            result.itemsConsumed(), depositedText,
                            currencyName, balanceText)));
            event.setCancellationResult(InteractionResult.CONSUME);
            return;
        }
        Component message = switch (result.status()) {
            case CREATIVE_BLOCKED -> Component.translatable(
                    "command.futureshops.deposit.creative_blocked");
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
                    INVALID_DENOMINATION, WRONG_PROVIDER,
                    LEGACY_MIGRATION_REQUIRED, INVALID_CURRENCY ->
                    Component.translatable(
                            "command.futureshops.deposit.money_invalid");
            case TOO_MANY_ITEMS -> Component.translatable(
                    "command.futureshops.deposit.too_many_items",
                    EscrowCashDepositService.MAX_ITEMS_CONSUMED);
            case SUCCESS -> throw new IllegalStateException(
                    "Successful cash deposit was handled earlier");
        };
        player.sendSystemMessage(EconomyCommandUtil.warning(message));
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_MAIN_HAND_HANDLED.remove(event.getEntity().getUUID());
    }
}
