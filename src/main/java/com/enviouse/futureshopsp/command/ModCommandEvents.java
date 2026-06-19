package com.enviouse.futureshopsp.command;

import com.enviouse.futureshopsp.Futureshops;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = Futureshops.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ModCommandEvents {
    private ModCommandEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ShopCommand.register(event.getDispatcher());
        BalanceCommand.register(event.getDispatcher());
        PayCommand.register(event.getDispatcher());
        BalTopCommand.register(event.getDispatcher());
        WithdrawCommand.register(event.getDispatcher());
        DepositCommand.register(event.getDispatcher());
        ShopAdminCommand.register(event.getDispatcher());
        AdminModeCommand.register(event.getDispatcher());
        LinkCommand.register(event.getDispatcher());
        FranchiseCommand.register(event.getDispatcher());
        DescCommand.register(event.getDispatcher());
    }
}
