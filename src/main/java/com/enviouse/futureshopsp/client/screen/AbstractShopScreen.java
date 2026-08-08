package com.enviouse.futureshopsp.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class AbstractShopScreen extends Screen {
    protected AbstractShopScreen(Component title) {
        super(title);
    }

    @Override
    public final void renderBackground(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
    }
}
