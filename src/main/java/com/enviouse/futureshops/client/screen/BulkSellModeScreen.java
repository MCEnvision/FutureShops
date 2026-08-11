package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.BulkSellTarget;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SBulkSellQuotePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class BulkSellModeScreen extends Screen
        implements ShopScreenMarker {
    private final Screen parent;
    private final BulkSellTarget target;
    private final String shopId;
    private Button chooseItemsButton;
    private Button sellAllButton;
    private boolean requestPending;

    public BulkSellModeScreen(
            Screen parent,
            BulkSellTarget target,
            String shopId
    ) {
        super(Component.translatable(
                "gui.futureshops.bulk_sell.choose_mode"));
        this.parent = parent;
        this.target = target;
        this.shopId = shopId;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(360, this.width - 24);
        int panelHeight = 130;
        int x = (this.width - panelWidth) / 2;
        int y = (this.height - panelHeight) / 2;
        chooseItemsButton = FutureShopsButton.styled(
                        Component.translatable(
                                "gui.futureshops.bulk_sell.choose_items"),
                        ignored -> request(false))
                .bounds(x + 18, y + 56,
                        panelWidth - 36, 20)
                .build();
        chooseItemsButton.setTooltip(Tooltip.create(
                Component.translatable(
                        "gui.futureshops.bulk_sell.help.choose_items")));
        addRenderableWidget(chooseItemsButton);

        sellAllButton = FutureShopsButton.styled(
                        Component.translatable(
                                "gui.futureshops.bulk_sell.start_all"),
                        ignored -> request(true))
                .bounds(x + 18, y + 80,
                        panelWidth - 36, 20)
                .style(ShopUiUtil.ButtonStyle.PRIMARY)
                .build();
        sellAllButton.setTooltip(Tooltip.create(
                Component.translatable(
                        "gui.futureshops.bulk_sell.help.sell_all")));
        addRenderableWidget(sellAllButton);

        Button cancel = FutureShopsButton.styled(
                        Component.translatable(
                                "gui.futureshops.bulk_sell.cancel"),
                        ignored -> onClose())
                .bounds(x + panelWidth - 78,
                        y + 106, 60, 16)
                .style(ShopUiUtil.ButtonStyle.GHOST)
                .build();
        cancel.setTooltip(Tooltip.create(Component.translatable(
                "gui.futureshops.bulk_sell.help.cancel")));
        addRenderableWidget(cancel);
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        ShopUiUtil.renderDimBackdrop(
                graphics, this.width, this.height);
        int width = Math.min(360, this.width - 24);
        int height = 130;
        int x = (this.width - width) / 2;
        int y = (this.height - height) / 2;
        graphics.fill(x, y, x + width, y + height,
                ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(
                graphics, x, y, width, height,
                ShopColors.BORDER_STRONG,
                ShopColors.BORDER_SUBTLE);
        graphics.fill(x, y, x + width, y + 2,
                ShopColors.ACCENT_PRIMARY);
        graphics.drawCenteredString(
                this.font, this.title,
                x + width / 2, y + 14,
                ShopColors.TEXT_STRONG);
        graphics.drawCenteredString(
                this.font,
                Component.translatable(
                        "gui.futureshops.bulk_sell.choose_mode_help"),
                x + width / 2, y + 32,
                ShopColors.TEXT_MUTED);
        chooseItemsButton.active = !requestPending;
        sellAllButton.active = !requestPending;
        sellAllButton.setMessage(Component.translatable(
                requestPending
                        ? "gui.futureshops.bulk_sell.loading"
                        : "gui.futureshops.bulk_sell.start_all"));
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void request(boolean selectAll) {
        requestPending = true;
        ShopPackets.CHANNEL.sendToServer(
                new C2SBulkSellQuotePacket(
                        target, shopId, selectAll));
    }

    public Screen returnScreen() {
        return parent;
    }

    public void onQuoteRejected() {
        requestPending = false;
    }

    @Override
    public void onClose() {
        Minecraft client = this.minecraft != null
                ? this.minecraft : Minecraft.getInstance();
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
