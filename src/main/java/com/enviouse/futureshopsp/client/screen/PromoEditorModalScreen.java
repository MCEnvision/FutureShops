package com.enviouse.futureshopsp.client.screen;

import com.enviouse.futureshopsp.client.PlayerShopClientState;
import com.enviouse.futureshopsp.client.ShopColors;
import com.enviouse.futureshopsp.network.ShopPackets;
import com.enviouse.futureshopsp.network.packets.C2SPlayerShopPromoPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public class PromoEditorModalScreen extends Screen implements ShopScreenMarker {
    private final Screen parent;
    private int guiLeft;
    private int guiTop;
    private int modalW;
    private int modalH;

    // Item 36: Promo type is a cycling button, not a text field
    private static final String[] PROMO_TYPES = {"PERCENTAGE", "FLAT", "BUY_X_GET_Y", "FLASH"};
    private int selectedTypeIndex = 0;
    private Button typeButton;
    private EditBox valueBox;
    private EditBox buyXBox;
    private EditBox buyYBox;
    private EditBox startBox;
    private EditBox durationBox;
    private boolean flash;
    private Button flashButton;
    private ConfirmationModal clearConfirm;

    public PromoEditorModalScreen(Screen parent) {
        super(Component.translatable("gui.futureshops.promo_editor.title"));
        this.parent = parent;
    }

    private static Component typeLabel(String type) {
        return Component.translatable("gui.futureshops.promo_editor.type." + type.toLowerCase(Locale.ROOT));
    }

    @Override
    protected void init() {
        // Items 21-22: Responsive sizing
        modalW = Math.max(220, this.width - 4);
        modalH = Math.max(140, this.height - 4);
        guiLeft = (this.width - modalW) / 2;
        guiTop = (this.height - modalH) / 2;
        int fieldX = guiLeft + Math.min(98, modalW / 3);
        int fieldW = Math.max(80, modalW - fieldX + guiLeft - 10);

        // Item 36: Type is a button that cycles through options
        typeButton = addRenderableWidget(Button.builder(
                typeLabel(PROMO_TYPES[selectedTypeIndex]),
                button -> {
                    selectedTypeIndex = (selectedTypeIndex + 1) % PROMO_TYPES.length;
                    button.setMessage(typeLabel(PROMO_TYPES[selectedTypeIndex]));
                    // Item 37: Flash toggle binds to FLASH type
                    if ("FLASH".equals(PROMO_TYPES[selectedTypeIndex])) {
                        flash = true;
                        if (flashButton != null) flashButton.setMessage(Component.translatable("gui.futureshops.promo_editor.flash_on"));
                    }
                    updateFieldVisibility();
                })
                .bounds(fieldX, guiTop + 26, fieldW, 14)
                .build());

        valueBox = new EditBox(this.font, fieldX, guiTop + 44, fieldW, 14, Component.translatable("gui.futureshops.promo_editor.value"));
        valueBox.setValue("10.00");
        valueBox.setMaxLength(10);
        addRenderableWidget(valueBox);

        buyXBox = new EditBox(this.font, fieldX, guiTop + 62, fieldW / 2 - 4, 14, Component.translatable("gui.futureshops.promo_editor.buy_x"));
        buyXBox.setValue("1");
        buyXBox.setMaxLength(4);
        addRenderableWidget(buyXBox);

        buyYBox = new EditBox(this.font, fieldX + fieldW / 2 + 4, guiTop + 62, fieldW / 2 - 4, 14, Component.translatable("gui.futureshops.promo_editor.get_y"));
        buyYBox.setValue("1");
        buyYBox.setMaxLength(4);
        addRenderableWidget(buyYBox);

        startBox = new EditBox(this.font, fieldX, guiTop + 80, fieldW / 2 - 4, 14, Component.translatable("gui.futureshops.promo_editor.start"));
        startBox.setValue("0");
        startBox.setMaxLength(6);
        startBox.setHint(Component.translatable("gui.futureshops.promo_editor.start"));
        addRenderableWidget(startBox);

        durationBox = new EditBox(this.font, fieldX + fieldW / 2 + 4, guiTop + 80, fieldW / 2 - 4, 14, Component.translatable("gui.futureshops.promo_editor.length"));
        durationBox.setValue("0");
        durationBox.setMaxLength(6);
        durationBox.setHint(Component.translatable("gui.futureshops.promo_editor.length"));
        addRenderableWidget(durationBox);

        flashButton = addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.promo_editor.flash_off"), button -> {
                    flash = !flash;
                    button.setMessage(Component.translatable(flash
                            ? "gui.futureshops.promo_editor.flash_on"
                            : "gui.futureshops.promo_editor.flash_off"));
                })
                .bounds(fieldX, guiTop + 98, 96, 14)
                .build());

        int btnW = Math.max(50, (modalW - 20) / 3 - 4);
        addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.promo_editor.apply"), button -> applyPromo())
                .bounds(guiLeft + 10, guiTop + modalH - 30, btnW, 18)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.promo_editor.clear"), button -> openClearConfirm())
                .bounds(guiLeft + 14 + btnW, guiTop + modalH - 30, btnW, 18)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.futureshops.promo_editor.back"), button -> onClose())
                .bounds(guiLeft + 18 + btnW * 2, guiTop + modalH - 30, btnW, 18)
                .build());

        updateFieldVisibility();
    }

    /**
     * Item 37: Show/hide fields based on selected promo type.
     * BUY_X_GET_Y: show buyX/buyY, hide value.
     * PERCENTAGE/FLAT/FLASH: show value, hide buyX/buyY.
     */
    private void updateFieldVisibility() {
        boolean isBuyXGetY = "BUY_X_GET_Y".equals(PROMO_TYPES[selectedTypeIndex]);
        if (valueBox != null) valueBox.visible = !isBuyXGetY;
        if (buyXBox != null) buyXBox.visible = isBuyXGetY;
        if (buyYBox != null) buyYBox.visible = isBuyXGetY;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        graphics.fill(guiLeft, guiTop, guiLeft + modalW, guiTop + modalH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, modalW, modalH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        graphics.fill(guiLeft, guiTop, guiLeft + modalW, guiTop + 2, ShopColors.ACCENT_PROMO_HI);

        graphics.drawString(this.font, I18n.get("gui.futureshops.promo_editor.header"), guiLeft + 8, guiTop + 8, ShopColors.TEXT_STRONG, false);
        String itemId = PlayerShopClientState.selectedListing() == null ? "" : PlayerShopClientState.selectedListing().itemId();
        String itemName = itemId.isBlank()
                ? I18n.get("gui.futureshops.promo_editor.item_none")
                : ShopUiUtil.getItemDisplayName(itemId);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(
                I18n.get("gui.futureshops.promo_editor.item_prefix", itemName), modalW - 16), guiLeft + 8, guiTop + 16, ShopColors.TEXT_MUTED, false);

        drawLabel(graphics, I18n.get("gui.futureshops.promo_editor.label.type"), guiTop + 29);
        // Item 37: Conditionally show label based on type
        boolean isBuyXGetY = "BUY_X_GET_Y".equals(PROMO_TYPES[selectedTypeIndex]);
        if (!isBuyXGetY) {
            drawLabel(graphics, I18n.get("gui.futureshops.promo_editor.label.pct_flat"), guiTop + 47);
        } else {
            drawLabel(graphics, I18n.get("gui.futureshops.promo_editor.label.buy_x_get_y"), guiTop + 65);
        }
        drawLabel(graphics, I18n.get("gui.futureshops.promo_editor.label.schedule"), guiTop + 83);

        // Translate the minutes entered into human-readable start/length so owners don't have to do math.
        String scheduleHint = formatScheduleHint(parseInt(startBox.getValue(), 0), parseInt(durationBox.getValue(), 0));
        graphics.drawString(this.font, scheduleHint, guiLeft + 10, guiTop + 114, ShopColors.TEXT_FAINT, false);

        super.render(graphics, mouseX, mouseY, partialTick);

        if (clearConfirm != null) {
            clearConfirm.render(graphics, this.font, this.width, this.height, mouseX, mouseY);
            if (clearConfirm.shouldAutoDismiss()) {
                clearConfirm = null;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (clearConfirm != null) {
            return clearConfirm.mouseClicked(mouseX, mouseY, button, this.font);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private String formatScheduleHint(int startMin, int durationMin) {
        String duration = durationMin <= 0
                ? I18n.get("gui.futureshops.promo_editor.schedule.duration_until_cleared")
                : I18n.get("gui.futureshops.promo_editor.schedule.lasts", humanMinutes(durationMin));
        String start = startMin <= 0
                ? I18n.get("gui.futureshops.promo_editor.schedule.starts_now")
                : I18n.get("gui.futureshops.promo_editor.schedule.starts_in", humanMinutes(startMin));
        return start + I18n.get("gui.futureshops.promo_editor.schedule.separator") + duration;
    }

    private static String humanMinutes(int minutes) {
        if (minutes <= 0) return "0m";
        int days = minutes / 1440;
        int hours = (minutes % 1440) / 60;
        int mins = minutes % 60;
        StringBuilder out = new StringBuilder();
        if (days > 0) out.append(days).append("d ");
        if (hours > 0) out.append(hours).append("h ");
        if (mins > 0 || out.length() == 0) out.append(mins).append("m");
        return out.toString().trim();
    }

    private void drawLabel(GuiGraphics graphics, String text, int y) {
        graphics.drawString(this.font, text, guiLeft + 10, y, ShopColors.TEXT_FAINT, false);
    }

    private void applyPromo() {
        if (PlayerShopClientState.selectedListing() == null || PlayerShopClientState.selectedListing().itemId().isBlank()) {
            return;
        }
        String type = PROMO_TYPES[selectedTypeIndex];
        ShopPackets.sendToServer(new C2SPlayerShopPromoPacket(
                PlayerShopClientState.shopPos(),
                PlayerShopClientState.selectedListingIndex(),
                false,
                type,
                parseDouble(valueBox.getValue(), 0.0D),
                parseInt(buyXBox.getValue(), 0),
                parseInt(buyYBox.getValue(), 0),
                parseInt(startBox.getValue(), 0),
                parseInt(durationBox.getValue(), 0),
                flash));
        onClose();
    }

    private void openClearConfirm() {
        if (PlayerShopClientState.selectedListing() == null || PlayerShopClientState.selectedListing().itemId().isBlank()) {
            return;
        }
        String itemName = ShopUiUtil.getItemDisplayName(PlayerShopClientState.selectedListing().itemId());
        clearConfirm = new ConfirmationModal(
                I18n.get("gui.futureshops.promo_editor.clear_title"),
                java.util.List.of(
                        ConfirmationModal.SummaryLine.text(I18n.get("gui.futureshops.promo_editor.clear_line1", itemName)),
                        ConfirmationModal.SummaryLine.text(I18n.get("gui.futureshops.promo_editor.clear_line2"))),
                I18n.get("gui.futureshops.promo_editor.clear_warning"),
                modal -> {
                    modal.setProcessing();
                    clearPromo();
                    modal.setSuccess(I18n.get("gui.futureshops.promo_editor.clear_success"));
                },
                () -> clearConfirm = null);
    }

    private void clearPromo() {
        if (PlayerShopClientState.selectedListing() == null || PlayerShopClientState.selectedListing().itemId().isBlank()) {
            return;
        }
        ShopPackets.sendToServer(new C2SPlayerShopPromoPacket(
                PlayerShopClientState.shopPos(),
                PlayerShopClientState.selectedListingIndex(),
                true,
                "",
                0.0D,
                0,
                0,
                0,
                0,
                false));
        onClose();
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC (256) dismisses the clear-confirm overlay first if open, otherwise returns to parent
        if (keyCode == 256) {
            if (clearConfirm != null) {
                clearConfirm.keyPressed(keyCode);
                clearConfirm = null;
                return true;
            }
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
