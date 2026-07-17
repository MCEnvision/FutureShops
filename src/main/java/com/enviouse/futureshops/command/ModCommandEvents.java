package com.enviouse.futureshops.command;

import com.enviouse.futureshops.Futureshops;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Futureshops.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ModCommandEvents {
    private ModCommandEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ShopCommand.register(event.getDispatcher());
        MarketCommand.register(event.getDispatcher());
        BalanceCommand.register(event.getDispatcher());
        PayCommand.register(event.getDispatcher());
        BalTopCommand.register(event.getDispatcher());
        WithdrawCommand.register(event.getDispatcher());
        AtmCommand.register(event.getDispatcher());
        DepositCommand.register(event.getDispatcher());
        ShopAdminCommand.register(event.getDispatcher());
        AdminModeCommand.register(event.getDispatcher());
        LinkCommand.register(event.getDispatcher());
        FranchiseCommand.register(event.getDispatcher());
        DescCommand.register(event.getDispatcher());
    }
}
