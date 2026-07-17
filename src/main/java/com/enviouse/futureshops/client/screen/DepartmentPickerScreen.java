package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.DepartmentClientState;
import com.enviouse.futureshops.client.PlayerShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SFetchDepartmentsPacket;
import com.enviouse.futureshops.network.packets.C2SSetDepartmentPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Modal screen for choosing/creating a department for a listing.
 * Shows a search box + the existing departments as clickable chips. Clicking a chip
 * fills the search box with that department.
 * <p>Typing a name that is NOT already among the fetched departments turns the primary
 * button into "Create &amp; assign" (it registers the new category and assigns it);
 * typing/selecting an existing name shows "Assign". This makes it obvious that a brand-new
 * name creates a category, which users previously did not realise.
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
    // Flat Nocturne buttons: per-frame click zones populated by ShopUiUtil.button in render().
    private final java.util.List<ShopUiUtil.ClickZone> clickZones = new java.util.ArrayList<>();

    public DepartmentPickerScreen(Screen parent) {
        super(Component.translatable("gui.futureshops.department.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        modalW = Math.max(200, this.width - 4);
        modalH = Math.max(160, this.height - 4);
        guiLeft = (this.width - modalW) / 2;
        guiTop = (this.height - modalH) / 2;

        searchBox = new EditBox(this.font, guiLeft + 8, guiTop + 22, modalW - 16, 14, Component.translatable("gui.futureshops.department.search_hint"));
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

        // The bottom action buttons (Create&assign/Assign, Clear, Back) and the department
        // chips are flat Nocturne primitives drawn immediate-mode in render().

        // Trigger initial search
        ShopPackets.CHANNEL.sendToServer(new C2SFetchDepartmentsPacket(""));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        clickZones.clear();

        // Debounced search — sends query after 300ms of no typing
        if (lastSearchMs > 0 && System.currentTimeMillis() - lastSearchMs >= 300) {
            lastSearchMs = 0;
            ShopPackets.CHANNEL.sendToServer(new C2SFetchDepartmentsPacket(lastQuery));
        }

        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        graphics.fill(guiLeft, guiTop, guiLeft + modalW, guiTop + modalH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, modalW, modalH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        graphics.fill(guiLeft, guiTop, guiLeft + modalW, guiTop + 2, ShopColors.ACCENT_PRIMARY);

        graphics.drawString(this.font, Component.translatable("gui.futureshops.department.header"), guiLeft + 8, guiTop + 8, ShopColors.TEXT_STRONG, false);

        String current = searchBox.getValue().trim();

        // ── Existing departments as clickable chips (wrapping flow, scrollable by chip-row) ──
        List<String> results = DepartmentClientState.getSearchResults();
        int listX = guiLeft + 8;
        int listY = guiTop + 40;
        int listW = modalW - 16;
        int listBottom = guiTop + modalH - 30; // leave room above the action buttons
        int chipH = 16;
        int rowGap = 4;
        int chipGap = 4;

        if (results.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.department.none_found"),
                    guiLeft + modalW / 2, listY + 10, ShopColors.TEXT_FAINT);
        } else {
            // Wrap chips into rows.
            java.util.List<java.util.List<String>> rows = new java.util.ArrayList<>();
            java.util.List<String> row = new java.util.ArrayList<>();
            int rowW = 0;
            for (String dept : results) {
                int cw = Math.min(listW - 10, Math.max(28, this.font.width(dept) + 14));
                if (rowW > 0 && rowW + cw > listW - 10) {
                    rows.add(row);
                    row = new java.util.ArrayList<>();
                    rowW = 0;
                }
                row.add(dept);
                rowW += cw + chipGap;
            }
            if (!row.isEmpty()) rows.add(row);

            int visibleRows = Math.max(1, (listBottom - listY) / (chipH + rowGap));
            scrollIndex = Math.max(0, Math.min(scrollIndex, Math.max(0, rows.size() - visibleRows)));

            for (int r = scrollIndex; r < rows.size() && r < scrollIndex + visibleRows; r++) {
                int cy = listY + (r - scrollIndex) * (chipH + rowGap);
                int cx = listX;
                for (String dept : rows.get(r)) {
                    int cw = Math.min(listW - 10, Math.max(28, this.font.width(dept) + 14));
                    boolean selected = dept.equalsIgnoreCase(current);
                    ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                            cx, cy, cw, chipH, Component.literal(dept),
                            selected ? ShopUiUtil.ButtonStyle.PRIMARY : ShopUiUtil.ButtonStyle.SECONDARY,
                            true, () -> selectDepartment(dept));
                    cx += cw + chipGap;
                }
            }

            // Scroll indicators when chip rows overflow the visible area.
            ShopUiUtil.renderScrollIndicators(graphics, this.font, listX, listY, listW,
                    visibleRows * (chipH + rowGap), scrollIndex, visibleRows, rows.size());
        }

        // ── Bottom action buttons: [Create & assign / Assign] [Clear] [Back] ──
        boolean inList = false;
        for (String d : results) {
            if (d.equalsIgnoreCase(current)) { inList = true; break; }
        }
        // "Create & assign" when the typed name is new (non-empty + not in the fetched list),
        // otherwise "Assign" — surfaces that a brand-new name registers a category.
        Component setLabel = (!current.isBlank() && !inList)
                ? Component.translatable("gui.futureshops.department.create_assign")
                : Component.translatable("gui.futureshops.department.assign");
        int btnW = (modalW - 24) / 3;
        int btnY = guiTop + modalH - 22;
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + 4, btnY, btnW, 16, setLabel,
                ShopUiUtil.ButtonStyle.PRIMARY, !current.isBlank(), this::applyDepartment);
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + 8 + btnW, btnY, btnW, 16,
                Component.translatable("gui.futureshops.department.clear"),
                ShopUiUtil.ButtonStyle.DANGER, true, this::clearDepartment);
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + 12 + btnW * 2, btnY, btnW, 16,
                Component.translatable("gui.futureshops.department.back"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, this::onClose);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** Fills the search box with a clicked department chip (the confirm button then assigns it). */
    private void selectDepartment(String dept) {
        searchBox.setValue(dept);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Flat Nocturne buttons + department chips: run the top-most hit ClickZone first.
        if (ShopUiUtil.dispatchClicks(clickZones, mouseX, mouseY)) {
            return true;
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

