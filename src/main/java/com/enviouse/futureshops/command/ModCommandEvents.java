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
        BalanceCommand.register(event.getDispatcher());
        WithdrawCommand.register(event.getDispatcher());
        DepositCommand.register(event.getDispatcher());
    }
}

