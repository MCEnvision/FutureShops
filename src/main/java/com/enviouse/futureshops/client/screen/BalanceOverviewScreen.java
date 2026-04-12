package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopColors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class BalanceOverviewScreen extends Screen implements ShopScreenMarker {
    private final long balanceMinorUnits;
    private final String currencyName;
    private final int currencyDecimals;

    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;

    public BalanceOverviewScreen(long balanceMinorUnits, String currencyName, int currencyDecimals) {
        super(Component.literal("Balance"));
        this.balanceMinorUnits = balanceMinorUnits;
        this.currencyName = currencyName;
        this.currencyDecimals = currencyDecimals;
    }

    @Override
    protected void init() {
        guiW = Math.min(220, this.width - 16);
        guiH = Math.min(110, this.height - 16);
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(guiLeft + (guiW - 56) / 2, guiTop + guiH - 26, 56, 16)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, ShopColors.BG_PRIMARY);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.BG_PANEL);
        ShopUiUtil.drawBorder(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_DEFAULT);

        graphics.drawCenteredString(this.font, "Account Balance", guiLeft + guiW / 2, guiTop + 12, ShopColors.TEXT_PRIMARY);
        String value = formatMinorUnits(balanceMinorUnits, currencyDecimals) + " " + currencyName;
        graphics.drawCenteredString(this.font, this.font.plainSubstrByWidth(value, guiW - 12), guiLeft + guiW / 2, guiTop + 40, ShopColors.TEXT_PRICE);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static String formatMinorUnits(long minorUnits, int decimals) {
        if (decimals <= 0) {
            return Long.toString(minorUnits);
        }
        long divisor = (long) Math.pow(10, decimals);
        long whole = minorUnits / divisor;
        long fractional = Math.abs(minorUnits % divisor);
        return whole + "." + String.format("%0" + decimals + "d", fractional);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

