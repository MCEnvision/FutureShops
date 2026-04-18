package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.DepartmentClientState;
import com.enviouse.futureshops.client.PlayerShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SFetchDepartmentsPacket;
import com.enviouse.futureshops.network.packets.C2SSetDepartmentPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Modal screen for choosing/creating a department for a listing.
 * Shows a search box + scrollable list of existing departments.
 * Typing a new name and pressing "Create" registers it.
 */
public class DepartmentPickerScreen extends Screen implements ShopScreenMarker {
    private final Screen parent;
    private int guiLeft;
    private int guiTop;
    private int modalW;
    private int modalH;
    private EditBox searchBox;
    private int scrollIndex;
    private long lastSearchMs;
    private String lastQuery = "";

    public DepartmentPickerScreen(Screen parent) {
        super(Component.literal("Department Picker"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        modalW = Math.max(200, this.width - 4);
        modalH = Math.max(160, this.height - 4);
        guiLeft = (this.width - modalW) / 2;
        guiTop = (this.height - modalH) / 2;

        searchBox = new EditBox(this.font, guiLeft + 8, guiTop + 22, modalW - 16, 14, Component.literal("Search departments..."));
        searchBox.setMaxLength(48);
        searchBox.setResponder(query -> {
            lastSearchMs = System.currentTimeMillis();
            lastQuery = query;
        });
        // Pre-fill with current department if any
        if (PlayerShopClientState.selectedListing() != null) {
            String current = PlayerShopClientState.selectedListing().department();
            if (current != null && !current.isBlank()) {
                searchBox.setValue(current);
            }
        }
        addRenderableWidget(searchBox);

        // Buttons at the bottom
        int btnW = (modalW - 24) / 3;
        addRenderableWidget(Button.builder(Component.literal("§aSet"), button -> applyDepartment())
                .bounds(guiLeft + 4, guiTop + modalH - 22, btnW, 16).build());
        addRenderableWidget(Button.builder(Component.literal("§cClear"), button -> clearDepartment())
                .bounds(guiLeft + 8 + btnW, guiTop + modalH - 22, btnW, 16).build());
        addRenderableWidget(Button.builder(Component.literal("§7Back"), button -> onClose())
                .bounds(guiLeft + 12 + btnW * 2, guiTop + modalH - 22, btnW, 16).build());

        // Trigger initial search
        ShopPackets.CHANNEL.sendToServer(new C2SFetchDepartmentsPacket(""));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Debounced search — sends query after 300ms of no typing
        if (lastSearchMs > 0 && System.currentTimeMillis() - lastSearchMs >= 300) {
            lastSearchMs = 0;
            ShopPackets.CHANNEL.sendToServer(new C2SFetchDepartmentsPacket(lastQuery));
        }

        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        graphics.fill(guiLeft, guiTop, guiLeft + modalW, guiTop + modalH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, modalW, modalH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        graphics.fill(guiLeft, guiTop, guiLeft + modalW, guiTop + 2, ShopColors.ACCENT_PRIMARY);

        graphics.drawString(this.font, "§d📦 Department Picker", guiLeft + 8, guiTop + 8, ShopColors.TEXT_STRONG, false);

        // Render search results
        List<String> results = DepartmentClientState.getSearchResults();
        int listY = guiTop + 40;
        int rowH = 16;
        int maxVisible = Math.max(1, (modalH - 72) / rowH);
        scrollIndex = Math.max(0, Math.min(scrollIndex, Math.max(0, results.size() - maxVisible)));

        if (results.isEmpty()) {
            graphics.drawCenteredString(this.font, "§7No departments found — type to create one", guiLeft + modalW / 2, listY + 10, ShopColors.TEXT_FAINT);
        } else {
            for (int i = scrollIndex; i < results.size() && i < scrollIndex + maxVisible; i++) {
                String dept = results.get(i);
                int y = listY + (i - scrollIndex) * rowH;
                boolean hovered = mouseX >= guiLeft + 8 && mouseX <= guiLeft + modalW - 8 && mouseY >= y && mouseY <= y + rowH - 2;
                boolean selected = dept.equalsIgnoreCase(searchBox.getValue().trim());

                int bg = selected ? ShopColors.SURFACE_PRESSED : (hovered ? ShopColors.SURFACE_OVERLAY : ShopColors.SURFACE_RAISED);
                graphics.fill(guiLeft + 8, y, guiLeft + modalW - 8, y + rowH - 2, bg);
                if (selected) {
                    graphics.fill(guiLeft + 8, y, guiLeft + 11, y + rowH - 2, ShopColors.ACCENT_PRIMARY);
                }
                String label = this.font.plainSubstrByWidth(dept, modalW - 24);
                graphics.drawString(this.font, label, guiLeft + 14, y + 3, selected ? ShopColors.TEXT_STRONG : ShopColors.TEXT_MUTED, false);
            }
        }

        // Scroll indicators
        ShopUiUtil.renderScrollIndicators(graphics, this.font, guiLeft + 8, listY, modalW - 16, maxVisible * rowH, scrollIndex, maxVisible, results.size());

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Click on a department row to select it
        List<String> results = DepartmentClientState.getSearchResults();
        int listY = guiTop + 40;
        int rowH = 16;
        int maxVisible = Math.max(1, (modalH - 72) / rowH);
        for (int i = scrollIndex; i < results.size() && i < scrollIndex + maxVisible; i++) {
            int y = listY + (i - scrollIndex) * rowH;
            if (mouseX >= guiLeft + 8 && mouseX <= guiLeft + modalW - 8 && mouseY >= y && mouseY <= y + rowH - 2) {
                searchBox.setValue(results.get(i));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollIndex = Math.max(0, scrollIndex - (int) delta);
        return true;
    }

    private void applyDepartment() {
        String dept = searchBox.getValue().trim();
        if (dept.isBlank()) return;
        ShopPackets.CHANNEL.sendToServer(new C2SSetDepartmentPacket(
                PlayerShopClientState.shopPos(),
                PlayerShopClientState.selectedListingIndex(),
                dept));
        onClose();
    }

    private void clearDepartment() {
        ShopPackets.CHANNEL.sendToServer(new C2SSetDepartmentPacket(
                PlayerShopClientState.shopPos(),
                PlayerShopClientState.selectedListingIndex(),
                ""));
        onClose();
    }

    @Override
    public void onClose() {
        DepartmentClientState.clear();
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

