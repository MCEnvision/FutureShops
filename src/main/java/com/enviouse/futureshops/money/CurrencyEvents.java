package com.enviouse.futureshops.money;

import com.enviouse.futureshops.Futureshops;
import com.enviouse.futureshops.command.EconomyCommandUtil;
import com.enviouse.futureshops.event.MoneyDepositEvent;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.economy.TransactionResult;
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

        // Foreign items have no checksum/ledger, so creative mode would be an
        // unlimited money printer — refuse the deposit outright.
        if (player.getAbilities().instabuild) {
            player.sendSystemMessage(EconomyCommandUtil.warning(
                    Component.translatable("command.futureshops.deposit.creative_blocked")));
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        int count = stack.getCount();
        long value = unitValue * count;
        EconomyProvider provider = BalanceManager.getProvider();
        TransactionResult result = provider.deposit(player.getUUID(), value);
        if (!result.success()) {
            // Nothing was consumed — foreign currency has no ledger, so a failed
            // credit (e.g. MAX_BALANCE_EXCEEDED) simply leaves the items in hand.
            EconomyCommandUtil.sendProviderError(player, result.errorCode());
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        stack.shrink(count);

        // Fire MoneyDepositEvent (spec §33)
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new MoneyDepositEvent(player.getUUID(), value, count));

        String depositedText = EconomyCommandUtil.formatMinorUnits(value, provider.getDecimalPlaces());
        String balanceText = EconomyCommandUtil.formatMinorUnits(result.resultingBalance(), provider.getDecimalPlaces());
        player.sendSystemMessage(EconomyCommandUtil.success(
                Component.translatable("command.futureshops.deposit.right_click_success",
                        count, depositedText, provider.getCurrencyName(), balanceText)));

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_MAIN_HAND_HANDLED.remove(event.getEntity().getUUID());
    }
}
