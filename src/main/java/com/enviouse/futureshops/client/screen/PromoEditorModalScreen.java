package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.PlayerShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SPlayerShopPromoPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class PromoEditorModalScreen extends Screen implements ShopScreenMarker {
    private final Screen parent;
    private int guiLeft;
    private int guiTop;

    private EditBox typeBox;
    private EditBox valueBox;
    private EditBox buyXBox;
    private EditBox buyYBox;
    private EditBox startBox;
    private EditBox durationBox;
    private boolean flash;
    private Button flashButton;

    public PromoEditorModalScreen(Screen parent) {
        super(Component.literal("Promo Editor"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        guiLeft = (this.width - 256) / 2;
        guiTop = (this.height - 166) / 2;

        typeBox = new EditBox(this.font, guiLeft + 98, guiTop + 26, 148, 14, Component.literal("Type"));
        typeBox.setValue("PERCENTAGE");
        typeBox.setMaxLength(20);
        addRenderableWidget(typeBox);

        valueBox = new EditBox(this.font, guiLeft + 98, guiTop + 44, 148, 14, Component.literal("Value"));
        valueBox.setValue("10");
        valueBox.setMaxLength(10);
        addRenderableWidget(valueBox);

        buyXBox = new EditBox(this.font, guiLeft + 98, guiTop + 62, 70, 14, Component.literal("Buy X"));
        buyXBox.setValue("1");
        buyXBox.setMaxLength(4);
        addRenderableWidget(buyXBox);

        buyYBox = new EditBox(this.font, guiLeft + 176, guiTop + 62, 70, 14, Component.literal("Get Y"));
        buyYBox.setValue("1");
        buyYBox.setMaxLength(4);
        addRenderableWidget(buyYBox);

        startBox = new EditBox(this.font, guiLeft + 98, guiTop + 80, 70, 14, Component.literal("Starts in min"));
        startBox.setValue("0");
        startBox.setMaxLength(6);
        addRenderableWidget(startBox);

        durationBox = new EditBox(this.font, guiLeft + 176, guiTop + 80, 70, 14, Component.literal("Duration min"));
        durationBox.setValue("0");
        durationBox.setMaxLength(6);
        addRenderableWidget(durationBox);

        flashButton = addRenderableWidget(Button.builder(Component.literal("Flash: OFF"), button -> {
                    flash = !flash;
                    button.setMessage(Component.literal(flash ? "Flash: ON" : "Flash: OFF"));
                })
                .bounds(guiLeft + 98, guiTop + 98, 96, 14)
                .build());

        addRenderableWidget(Button.builder(Component.literal("Apply"), button -> applyPromo())
                .bounds(guiLeft + 10, guiTop + 136, 74, 18)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Clear"), button -> clearPromo())
                .bounds(guiLeft + 90, guiTop + 136, 74, 18)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(guiLeft + 170, guiTop + 136, 74, 18)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, ShopColors.BG_PRIMARY);
        graphics.fill(guiLeft, guiTop, guiLeft + 256, guiTop + 166, ShopColors.BG_PANEL);
        ShopUiUtil.drawBorder(graphics, guiLeft, guiTop, 256, 166, ShopColors.BORDER_DEFAULT);

        graphics.drawString(this.font, "Promo Editor", guiLeft + 8, guiTop + 8, ShopColors.TEXT_PRIMARY, false);
        String itemName = PlayerShopClientState.listedItemId().isBlank() ? "(none)" : ShopUiUtil.getItemDisplayName(PlayerShopClientState.listedItemId());
        graphics.drawString(this.font, this.font.plainSubstrByWidth("Item: " + itemName, 244), guiLeft + 8, guiTop + 16, ShopColors.TEXT_SECONDARY, false);

        drawLabel(graphics, "Type", guiTop + 29);
        drawLabel(graphics, "Value", guiTop + 47);
        drawLabel(graphics, "BuyX/GetY", guiTop + 65);
        drawLabel(graphics, "Start/Duration", guiTop + 83);
        graphics.drawString(this.font, "Types: PERCENTAGE, FLAT, BUY_X_GET_Y, FLASH", guiLeft + 8, guiTop + 116, ShopColors.TEXT_SECONDARY, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawLabel(GuiGraphics graphics, String text, int y) {
        graphics.drawString(this.font, text, guiLeft + 10, y, ShopColors.TEXT_SECONDARY, false);
    }

    private void applyPromo() {
        if (PlayerShopClientState.listedItemId().isBlank()) {
            return;
        }
        ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopPromoPacket(
                PlayerShopClientState.shopPos(),
                false,
                typeBox.getValue().trim(),
                parseDouble(valueBox.getValue(), 0.0D),
                parseInt(buyXBox.getValue(), 0),
                parseInt(buyYBox.getValue(), 0),
                parseInt(startBox.getValue(), 0),
                parseInt(durationBox.getValue(), 0),
                flash));
    }

    private void clearPromo() {
        if (PlayerShopClientState.listedItemId().isBlank()) {
            return;
        }
        ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopPromoPacket(
                PlayerShopClientState.shopPos(),
                true,
                "",
                0.0D,
                0,
                0,
                0,
                0,
                false));
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
