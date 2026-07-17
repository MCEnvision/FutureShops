package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.ClientConfig;
import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.TransactionHistoryEntry;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SFetchHistoryPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.ZoneId;
import java.util.List;

public class TransactionHistoryScreen extends Screen implements ShopScreenMarker {
    /** Page size = rows actually shown (computed in init from the table height). A fixed size larger
     *  than the visible rows hid entries and reported too few pages ("Page 1/1" with plenty of history). */
    private int rowsPerPage = 7;
    private final Screen parent;

    /** Per-frame flat-button hit regions, populated in {@link #render}, consulted in mouseClicked. */
    private final java.util.List<ShopUiUtil.ClickZone> clickZones = new java.util.ArrayList<>();

    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;
    private int page = 1;
    private int totalPages = 1;
    private TransactionHistoryEntry.HistoryFilter activeFilter = TransactionHistoryEntry.HistoryFilter.ALL;
    private TransactionHistoryEntry.SortOrder sortOrder = TransactionHistoryEntry.SortOrder.NEWEST;
    private TransactionHistoryEntry.TimeWindow timeWindow = TransactionHistoryEntry.TimeWindow.ALL;
    private EditBox searchBox;

    public TransactionHistoryScreen(Screen parent) {
        super(Component.translatable("gui.futureshops.history.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        guiW = Math.max(360, this.width - 4);
        guiH = Math.max(240, this.height - 4);
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;
        // Page size = the rows this table can actually draw (28px rows, no scroll), so every entry
        // is reachable and totalPages reflects the real history volume.
        rowsPerPage = Math.max(1, (guiH - 126 - 34) / 28);

        searchBox = new EditBox(this.font, guiLeft + 10, guiTop + 34, Math.max(120, guiW - 304), 18,
                Component.translatable("gui.futureshops.shop.search"));
        searchBox.setMaxLength(32);
        searchBox.setResponder(ignored -> {
            page = 1;
            requestPage();
        });
        addRenderableWidget(searchBox);

        requestPage();
    }

    /** Draws every flat Nocturne button (nav, sort, window, filters) and registers its click zone. */
    private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + 10, guiTop + guiH - 24, 48, 18,
                Component.translatable("gui.futureshops.history.back"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, this::onClose);
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + guiW - 120, guiTop + guiH - 24, 18, 18, Component.literal("<"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, () -> {
                    page = Math.max(1, page - 1);
                    requestPage();
                });
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + guiW - 66, guiTop + guiH - 24, 18, 18, Component.literal(">"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, () -> {
                    // Advance optimistically; the server-authoritative response clamps back to
                    // min(page, totalPages) via applyHistoryResponse. (Matches SettlementHistoryScreen.)
                    page = page + 1;
                    requestPage();
                });

        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + guiW - 186, guiTop + 34, 84, 18, sortLabel(),
                ShopUiUtil.ButtonStyle.SECONDARY, true, () -> {
                    sortOrder = sortOrder == TransactionHistoryEntry.SortOrder.NEWEST
                            ? TransactionHistoryEntry.SortOrder.OLDEST
                            : TransactionHistoryEntry.SortOrder.NEWEST;
                    page = 1;
                    requestPage();
                });
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + guiW - 96, guiTop + 34, 78, 18, windowLabel(),
                ShopUiUtil.ButtonStyle.SECONDARY, true, () -> {
                    timeWindow = switch (timeWindow) {
                        case ALL -> TransactionHistoryEntry.TimeWindow.DAY;
                        case DAY -> TransactionHistoryEntry.TimeWindow.WEEK;
                        case WEEK -> TransactionHistoryEntry.TimeWindow.MONTH;
                        case MONTH -> TransactionHistoryEntry.TimeWindow.ALL;
                    };
                    page = 1;
                    requestPage();
                });

        int buttonX = guiLeft + 10;
        int filterBtnW = Math.min(70, (guiW - 20 - 4 * 4) / 5);
        for (TransactionHistoryEntry.HistoryFilter filter : TransactionHistoryEntry.HistoryFilter.values()) {
            // The active filter renders disabled (greyed, non-clickable) to signal the selection.
            ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                    buttonX, guiTop + 60, filterBtnW, 18,
                    Component.translatable(labelKey(filter)),
                    ShopUiUtil.ButtonStyle.SECONDARY, filter != activeFilter, () -> {
                        activeFilter = filter;
                        page = 1;
                        requestPage();
                    });
            buttonX += filterBtnW + 4;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        clickZones.clear();
        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + 2, ShopColors.ACCENT_PRIMARY);
        renderHeader(graphics);
        renderRows(graphics);
        renderFooter(graphics);
        renderButtons(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ShopUiUtil.dispatchClicks(clickZones, mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderHeader(GuiGraphics graphics) {
        graphics.drawString(this.font, this.title, guiLeft + 10, guiTop + 14, ShopColors.TEXT_STRONG, false);
        graphics.drawString(this.font, Component.translatable("gui.futureshops.history.subtitle"), guiLeft + 10, guiTop + 24, ShopColors.TEXT_MUTED, false);
    }

    private void renderRows(GuiGraphics graphics) {
        int tableX = guiLeft + 10;
        int tableY = guiTop + 90;
        int tableW = guiW - 20;
        int tableH = guiH - 126;
        ShopUiUtil.renderCard(graphics, tableX, tableY, tableW, tableH);
        graphics.fill(tableX, tableY, tableX + tableW, tableY + 2, ShopColors.ACCENT_PRIMARY);
        graphics.drawString(this.font, Component.translatable("gui.futureshops.history.col.type"), tableX + 10, tableY + 8, ShopColors.TEXT_FAINT, false);
        graphics.drawString(this.font, Component.translatable("gui.futureshops.history.col.item"), tableX + 74, tableY + 8, ShopColors.TEXT_FAINT, false);
        boolean twelveHourTime = ClientConfig.use12HourTime();
        int qtyOffset = twelveHourTime ? 192 : 168;
        int valueOffset = twelveHourTime ? 142 : 118;
        int timeOffset = twelveHourTime ? 82 : 58;
        graphics.drawString(this.font, Component.translatable("gui.futureshops.history.col.qty"), tableX + tableW - qtyOffset, tableY + 8, ShopColors.TEXT_FAINT, false);
        graphics.drawString(this.font, Component.translatable("gui.futureshops.history.col.value"), tableX + tableW - valueOffset, tableY + 8, ShopColors.TEXT_FAINT, false);
        graphics.drawString(this.font, Component.translatable("gui.futureshops.history.col.when"), tableX + tableW - timeOffset, tableY + 8, ShopColors.TEXT_FAINT, false);
        graphics.fill(tableX + 8, tableY + 20, tableX + tableW - 8, tableY + 21, ShopColors.BORDER_SUBTLE);

        List<TransactionHistoryEntry> entries = ShopClientState.getTransactionHistory();
        if (entries.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.history.empty"), tableX + tableW / 2, tableY + tableH / 2 - 8, ShopColors.TEXT_MUTED);
            return;
        }

        int rowY = tableY + 26;
        int rowH = 28;
        int maxRows = rowsPerPage;
        // Column geometry — Item starts at +74, so Type has ~60px before it. Raw types
        // like "MONEY_AND_BARTER" are far too wide and previously bled over the Item
        // column; show a short label instead and clip defensively.
        int typeColW = 60;
        int itemColW = (tableW - qtyOffset) - 74 - 4;
        for (int i = 0; i < Math.min(entries.size(), maxRows); i++) {
            TransactionHistoryEntry entry = entries.get(i);
            int y = rowY + i * rowH;
            graphics.fill(tableX + 8, y, tableX + tableW - 8, y + rowH - 2, i % 2 == 0 ? ShopColors.SURFACE_RAISED : ShopColors.SURFACE_OVERLAY);
            String typeLabel = this.font.plainSubstrByWidth(shortTypeLabel(entry.type()), typeColW);
            graphics.drawString(this.font, typeLabel, tableX + 10, y + 5, colorForType(entry.type()), false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayNameWithNbt(entry.itemId(), entry.nbtJson()), itemColW), tableX + 74, y + 5, ShopColors.TEXT_STRONG, false);
            graphics.drawString(this.font, Integer.toString(entry.quantity()), tableX + tableW - qtyOffset, y + 5, ShopColors.TEXT_MUTED, false);
            graphics.drawString(this.font, entry.totalMinorUnits() > 0L ? ShopUiUtil.formatMinorUnits(entry.totalMinorUnits()) : "—", tableX + tableW - valueOffset, y + 5, ShopColors.TEXT_CURRENCY, false);
            graphics.drawString(this.font, HistoryTimestampFormatter.format(
                            entry.timestampEpochSeconds(), twelveHourTime, ZoneId.systemDefault()),
                    tableX + tableW - timeOffset, y + 5, ShopColors.TEXT_FAINT, false);

            String detail = formatBarterDetail(entry);
            if (detail != null) {
                graphics.drawString(this.font, this.font.plainSubstrByWidth(detail, tableW - 30), tableX + 74, y + 16, ShopColors.TEXT_BARTER_SOFT, false);
            }
        }
    }

    private String formatBarterDetail(TransactionHistoryEntry entry) {
        String note = entry.note();
        if (note == null || !note.startsWith("paid=")) {
            return null;
        }
        String payload = note.substring(5);
        if (payload.isBlank()) {
            return null;
        }
        StringBuilder out = new StringBuilder(Component.translatable("gui.futureshops.history.paid_prefix").getString());
        String[] parts = payload.split(",");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) out.append("§8, ");
            String part = parts[i];
            int sep = part.indexOf('\u00d7');
            if (sep <= 0) continue;
            String itemId = part.substring(0, sep);
            String count = part.substring(sep + 1);
            out.append("§f").append(count).append("§8×§f").append(ShopUiUtil.getItemDisplayName(itemId));
        }
        return out.toString();
    }

    private void renderFooter(GuiGraphics graphics) {
        // Center the page counter in the panel rather than in the ~36px gap between the
        // </> nav buttons — the "Page X / Y" string is wider than that gap and used to
        // overlap the nav cluster on the right.
        int midX = guiLeft + guiW / 2;
        graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.history.page_footer", page, totalPages), midX, guiTop + guiH - 20, ShopColors.TEXT_MUTED);
    }

    public boolean applyHistoryResponse(int responsePage, int responseTotalPages,
                                        TransactionHistoryEntry.HistoryFilter responseFilter) {
        if (responseFilter != activeFilter) {
            return false;
        }
        this.page = Math.max(1, responsePage);
        this.totalPages = Math.max(1, responseTotalPages);
        return true;
    }

    private void requestPage() {
        ShopPackets.CHANNEL.sendToServer(new C2SFetchHistoryPacket(
                ShopClientState.getActiveShopId(),
                page,
                rowsPerPage,
                activeFilter,
                searchBox == null ? "" : searchBox.getValue(),
                sortOrder,
                timeWindow));
    }

    private Component sortLabel() {
        return Component.translatable(sortOrder == TransactionHistoryEntry.SortOrder.NEWEST
                ? "gui.futureshops.history.sort.newest"
                : "gui.futureshops.history.sort.oldest");
    }

    private Component windowLabel() {
        return Component.translatable(switch (timeWindow) {
            case ALL -> "gui.futureshops.history.window.all";
            case DAY -> "gui.futureshops.history.window.day";
            case WEEK -> "gui.futureshops.history.window.week";
            case MONTH -> "gui.futureshops.history.window.month";
        });
    }

    private String labelKey(TransactionHistoryEntry.HistoryFilter filter) {
        return switch (filter) {
            case ALL -> "gui.futureshops.history.filter.all";
            case BUY -> "gui.futureshops.history.filter.buy";
            case SELL -> "gui.futureshops.history.filter.sell";
            case BARTER -> "gui.futureshops.history.filter.barter";
            case CART_CLAIM -> "gui.futureshops.history.filter.cart_claim";
        };
    }

    private int colorForType(String type) {
        return switch (type.toUpperCase()) {
            case "BUY" -> ShopColors.TEXT_CURRENCY;
            case "SELL" -> ShopColors.STATUS_SUCCESS;
            case "BARTER" -> ShopColors.TEXT_BARTER_SOFT;
            case "CART_CLAIM" -> ShopColors.ACCENT_PRIMARY;
            case "MONEY_AND_BARTER" -> ShopColors.TEXT_BARTER_SOFT;
            default -> ShopColors.TEXT_STRONG;
        };
    }

    private String shortTypeLabel(String type) {
        if (type == null) return "";
        return switch (type.toUpperCase()) {
            case "MONEY_AND_BARTER" -> Component.translatable("gui.futureshops.history.type.money_and_barter").getString();
            case "CART_CLAIM" -> Component.translatable("gui.futureshops.history.filter.cart_claim").getString();
            default -> type;
        };
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
