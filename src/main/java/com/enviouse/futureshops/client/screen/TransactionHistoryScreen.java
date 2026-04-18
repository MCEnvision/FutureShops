package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.TransactionHistoryEntry;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SFetchHistoryPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class TransactionHistoryScreen extends Screen implements ShopScreenMarker {
    private static final int PAGE_SIZE = 10;
    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Screen parent;
    private final Map<TransactionHistoryEntry.HistoryFilter, Button> filterButtons =
            new EnumMap<>(TransactionHistoryEntry.HistoryFilter.class);

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

        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(guiLeft + 10, guiTop + guiH - 24, 48, 18)
                .build());
        addRenderableWidget(Button.builder(Component.literal("<"), button -> {
                    page = Math.max(1, page - 1);
                    requestPage();
                })
                .bounds(guiLeft + guiW - 120, guiTop + guiH - 24, 18, 18)
                .build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> {
                    page = Math.min(totalPages, page + 1);
                    requestPage();
                })
                .bounds(guiLeft + guiW - 66, guiTop + guiH - 24, 18, 18)
                .build());

        searchBox = new EditBox(this.font, guiLeft + 10, guiTop + 34, Math.max(120, guiW - 304), 18,
                Component.translatable("gui.futureshops.shop.search"));
        searchBox.setMaxLength(32);
        searchBox.setResponder(ignored -> {
            page = 1;
            requestPage();
        });
        addRenderableWidget(searchBox);

        addRenderableWidget(Button.builder(sortLabel(), button -> {
                    sortOrder = sortOrder == TransactionHistoryEntry.SortOrder.NEWEST
                            ? TransactionHistoryEntry.SortOrder.OLDEST
                            : TransactionHistoryEntry.SortOrder.NEWEST;
                    button.setMessage(sortLabel());
                    page = 1;
                    requestPage();
                })
                .bounds(guiLeft + guiW - 186, guiTop + 34, 84, 18)
                .build());
        addRenderableWidget(Button.builder(windowLabel(), button -> {
                    timeWindow = switch (timeWindow) {
                        case ALL -> TransactionHistoryEntry.TimeWindow.DAY;
                        case DAY -> TransactionHistoryEntry.TimeWindow.WEEK;
                        case WEEK -> TransactionHistoryEntry.TimeWindow.MONTH;
                        case MONTH -> TransactionHistoryEntry.TimeWindow.ALL;
                    };
                    button.setMessage(windowLabel());
                    page = 1;
                    requestPage();
                })
                .bounds(guiLeft + guiW - 96, guiTop + 34, 78, 18)
                .build());

        int buttonX = guiLeft + 10;
        int filterBtnW = Math.min(70, (guiW - 20 - 4 * 4) / 5);
        for (TransactionHistoryEntry.HistoryFilter filter : TransactionHistoryEntry.HistoryFilter.values()) {
            Button button = Button.builder(Component.translatable(labelKey(filter)), ignored -> {
                        activeFilter = filter;
                        page = 1;
                        refreshFilterButtons();
                        requestPage();
                    })
                    .bounds(buttonX, guiTop + 60, filterBtnW, 18)
                    .build();
            filterButtons.put(filter, button);
            addRenderableWidget(button);
            buttonX += filterBtnW + 4;
        }
        refreshFilterButtons();
        requestPage();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + 2, ShopColors.ACCENT_PRIMARY);
        renderHeader(graphics);
        renderRows(graphics);
        renderFooter(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics graphics) {
        graphics.drawString(this.font, this.title, guiLeft + 10, guiTop + 14, ShopColors.TEXT_STRONG, false);
        graphics.drawString(this.font, "§7Filter, search, and inspect every buy, sell, and barter event", guiLeft + 10, guiTop + 24, ShopColors.TEXT_MUTED, false);
    }

    private void renderRows(GuiGraphics graphics) {
        int tableX = guiLeft + 10;
        int tableY = guiTop + 90;
        int tableW = guiW - 20;
        int tableH = guiH - 126;
        ShopUiUtil.renderCard(graphics, tableX, tableY, tableW, tableH);
        graphics.fill(tableX, tableY, tableX + tableW, tableY + 2, ShopColors.ACCENT_PRIMARY);
        graphics.drawString(this.font, "§lType", tableX + 10, tableY + 8, ShopColors.TEXT_FAINT, false);
        graphics.drawString(this.font, "§lItem", tableX + 74, tableY + 8, ShopColors.TEXT_FAINT, false);
        graphics.drawString(this.font, "§lQty", tableX + tableW - 168, tableY + 8, ShopColors.TEXT_FAINT, false);
        graphics.drawString(this.font, "§lValue", tableX + tableW - 118, tableY + 8, ShopColors.TEXT_FAINT, false);
        graphics.drawString(this.font, "§lWhen", tableX + tableW - 58, tableY + 8, ShopColors.TEXT_FAINT, false);
        graphics.fill(tableX + 8, tableY + 20, tableX + tableW - 8, tableY + 21, ShopColors.BORDER_SUBTLE);

        List<TransactionHistoryEntry> entries = ShopClientState.getTransactionHistory();
        if (entries.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.history.empty"), tableX + tableW / 2, tableY + tableH / 2 - 8, ShopColors.TEXT_MUTED);
            return;
        }

        int rowY = tableY + 26;
        int rowH = 20;
        int maxRows = Math.min(10, (tableH - 34) / rowH);
        for (int i = 0; i < Math.min(entries.size(), maxRows); i++) {
            TransactionHistoryEntry entry = entries.get(i);
            int y = rowY + i * rowH;
            graphics.fill(tableX + 8, y, tableX + tableW - 8, y + 18, i % 2 == 0 ? ShopColors.SURFACE_RAISED : ShopColors.SURFACE_OVERLAY);
            graphics.drawString(this.font, entry.type(), tableX + 10, y + 5, colorForType(entry.type()), false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayName(entry.itemId()), tableW - 250), tableX + 74, y + 5, ShopColors.TEXT_STRONG, false);
            graphics.drawString(this.font, Integer.toString(entry.quantity()), tableX + tableW - 168, y + 5, ShopColors.TEXT_MUTED, false);
            graphics.drawString(this.font, entry.totalMinorUnits() > 0L ? ShopUiUtil.formatMinorUnits(entry.totalMinorUnits()) : "—", tableX + tableW - 118, y + 5, ShopColors.TEXT_CURRENCY, false);
            graphics.drawString(this.font, TS_FORMAT.format(Instant.ofEpochSecond(entry.timestampEpochSeconds())), tableX + tableW - 58, y + 5, ShopColors.TEXT_FAINT, false);
        }
    }

    private void renderFooter(GuiGraphics graphics) {
        int midX = guiLeft + guiW - 93;
        String pageText = "§7Page §f" + page + " §8/ §7" + totalPages;
        graphics.drawCenteredString(this.font, pageText, midX, guiTop + guiH - 20, ShopColors.TEXT_MUTED);
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
                PAGE_SIZE,
                activeFilter,
                searchBox == null ? "" : searchBox.getValue(),
                sortOrder,
                timeWindow));
    }

    private void refreshFilterButtons() {
        filterButtons.forEach((filter, button) -> button.active = filter != activeFilter);
    }

    private Component sortLabel() {
        return Component.literal(sortOrder == TransactionHistoryEntry.SortOrder.NEWEST ? "Newest" : "Oldest");
    }

    private Component windowLabel() {
        return Component.literal(switch (timeWindow) {
            case ALL -> "All time";
            case DAY -> "24h";
            case WEEK -> "7d";
            case MONTH -> "30d";
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
            case "CART_CLAIM" -> ShopColors.ACCENT_PROMO_HI;
            default -> ShopColors.TEXT_STRONG;
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
