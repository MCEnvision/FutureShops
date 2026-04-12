package com.enviouse.futureshops.client;

import com.enviouse.futureshops.network.packets.S2CShopDataPacket;
import net.minecraft.client.Minecraft;

public final class ShopClientPacketHandler {
    private ShopClientPacketHandler() {
    }

    public static void handleShopData(S2CShopDataPacket packet) {
        Minecraft.getInstance().execute(() ->
            ShopClientState.applyShopData(packet.shopId(), packet.balanceMinorUnits(), packet.currencyName(), packet.currencyDecimals())
        );
    }
}

