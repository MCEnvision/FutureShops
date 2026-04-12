package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.BalanceTopEntry;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SOpenBalTopUiPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class BalTopOverviewScreen extends Screen implements ShopScreenMarker {
    private int page;
    private int totalPages;
    private List<BalanceTopEntry> entries;
    private final String currencyName;
    private final int currencyDecimals;

    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;

    public BalTopOverviewScreen(int page, int totalPages, List<BalanceTopEntry> entries, String currencyName, int currencyDecimals) {
        super(Component.literal("BalTop"));
        this.page = page;
        this.totalPages = Math.max(1, totalPages);
        this.entries = entries;
        this.currencyName = currencyName;
        this.currencyDecimals = currencyDecimals;
    }

    @Override
    protected void init() {
        guiW = Math.min(260, this.width - 16);
        guiH = Math.min(200, this.height - 16);
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;

        addRenderableWidget(Button.builder(Component.literal("<-"), button -> onClose())
                .bounds(guiLeft + 6, guiTop + 6, 18, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("<"), button -> {
                    page = Math.max(1, page - 1);
                    request();
                })
                .bounds(guiLeft + guiW / 2 - 24, guiTop + guiH - 22, 16, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> {
                    page = Math.min(totalPages, page + 1);
                    request();
                })
                .bounds(guiLeft + guiW / 2 + 8, guiTop + guiH - 22, 16, 14)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, ShopColors.BG_PRIMARY);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.BG_PANEL);
        ShopUiUtil.drawBorder(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_DEFAULT);

        graphics.drawCenteredString(this.font, "Top Balances", guiLeft + guiW / 2, guiTop + 10, ShopColors.TEXT_PRIMARY);

        int y = guiTop + 28;
        for (int i = 0; i < Math.min(10, entries.size()); i++) {
            BalanceTopEntry entry = entries.get(i);
            String row = "#" + (((page - 1) * 10) + i + 1) + " " + entry.playerName();
            String bal = formatMinorUnits(entry.balanceMinorUnits(), currencyDecimals) + " " + currencyName;
            graphics.drawString(this.font, this.font.plainSubstrByWidth(row, guiW - 130), guiLeft + 10, y, ShopColors.TEXT_PRIMARY, false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(bal, 110), guiLeft + guiW - 116, y, ShopColors.TEXT_PRICE, false);
            y += 14;
        }

        graphics.drawCenteredString(this.font, "Page " + page + " / " + totalPages, guiLeft + guiW / 2, guiTop + guiH - 18, ShopColors.TEXT_SECONDARY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    public void updatePage(int page, int totalPages, List<BalanceTopEntry> entries) {
        this.page = page;
        this.totalPages = Math.max(1, totalPages);
        this.entries = entries;
    }

    private void request() {
        ShopPackets.CHANNEL.sendToServer(new C2SOpenBalTopUiPacket(page));
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

