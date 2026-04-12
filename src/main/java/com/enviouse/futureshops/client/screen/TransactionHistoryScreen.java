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
        guiW = Math.min(520, Math.max(360, this.width - 24));
        guiH = Math.min(320, Math.max(240, this.height - 24));
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;

        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(guiLeft + 10, guiTop + guiH - 24, 48, 18)
                .build());
        addRenderableWidget(Button.builder(Component.literal("<"), button -> {
                    page = Math.max(1, page - 1);
                    requestPage();
                })
                .bounds(guiLeft + guiW - 104, guiTop + guiH - 24, 18, 18)
                .build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> {
                    page = Math.min(totalPages, page + 1);
                    requestPage();
                })
                .bounds(guiLeft + guiW - 82, guiTop + guiH - 24, 18, 18)
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
        for (TransactionHistoryEntry.HistoryFilter filter : TransactionHistoryEntry.HistoryFilter.values()) {
            Button button = Button.builder(Component.translatable(labelKey(filter)), ignored -> {
                        activeFilter = filter;
                        page = 1;
                        refreshFilterButtons();
                        requestPage();
                    })
                    .bounds(buttonX, guiTop + 60, 70, 18)
                    .build();
            filterButtons.put(filter, button);
            addRenderableWidget(button);
            buttonX += 74;
        }
        refreshFilterButtons();
        requestPage();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, ShopColors.BG_PRIMARY);
        ShopUiUtil.renderPanel(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BG_PANEL, ShopColors.BORDER_DEFAULT);
        renderHeader(graphics);
        renderRows(graphics);
        renderFooter(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics graphics) {
        graphics.drawString(this.font, this.title, guiLeft + 10, guiTop + 16, ShopColors.TEXT_PRIMARY, false);
        graphics.drawString(this.font, "Filter, search, and inspect every buy, sell, and barter event.", guiLeft + 10, guiTop + 26, ShopColors.TEXT_SECONDARY, false);
    }

    private void renderRows(GuiGraphics graphics) {
        int tableX = guiLeft + 10;
        int tableY = guiTop + 90;
        int tableW = guiW - 20;
        int tableH = guiH - 126;
        ShopUiUtil.renderPanel(graphics, tableX, tableY, tableW, tableH, ShopColors.BG_CARD, ShopColors.BORDER_DEFAULT);
        graphics.drawString(this.font, "Type", tableX + 10, tableY + 8, ShopColors.TEXT_SECONDARY, false);
        graphics.drawString(this.font, "Item", tableX + 74, tableY + 8, ShopColors.TEXT_SECONDARY, false);
        graphics.drawString(this.font, "Qty", tableX + tableW - 168, tableY + 8, ShopColors.TEXT_SECONDARY, false);
        graphics.drawString(this.font, "Value", tableX + tableW - 118, tableY + 8, ShopColors.TEXT_SECONDARY, false);
        graphics.drawString(this.font, "When", tableX + tableW - 58, tableY + 8, ShopColors.TEXT_SECONDARY, false);

        List<TransactionHistoryEntry> entries = ShopClientState.getTransactionHistory();
        if (entries.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.history.empty"), tableX + tableW / 2, tableY + tableH / 2 - 8, ShopColors.TEXT_SECONDARY);
            return;
        }

        int rowY = tableY + 26;
        int rowH = 20;
        int maxRows = Math.min(10, (tableH - 34) / rowH);
        for (int i = 0; i < Math.min(entries.size(), maxRows); i++) {
            TransactionHistoryEntry entry = entries.get(i);
            int y = rowY + i * rowH;
            graphics.fill(tableX + 8, y, tableX + tableW - 8, y + 18, i % 2 == 0 ? ShopColors.BG_PANEL : ShopColors.BG_CARD_HOVER);
            graphics.drawString(this.font, entry.type(), tableX + 10, y + 5, colorForType(entry.type()), false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(ShopUiUtil.getItemDisplayName(entry.itemId()), tableW - 250), tableX + 74, y + 5, ShopColors.TEXT_PRIMARY, false);
            graphics.drawString(this.font, Integer.toString(entry.quantity()), tableX + tableW - 168, y + 5, ShopColors.TEXT_SECONDARY, false);
            graphics.drawString(this.font, entry.totalMinorUnits() > 0L ? ShopUiUtil.formatMinorUnits(entry.totalMinorUnits()) : "—", tableX + tableW - 118, y + 5, ShopColors.TEXT_PRICE, false);
            graphics.drawString(this.font, TS_FORMAT.format(Instant.ofEpochSecond(entry.timestampEpochSeconds())), tableX + tableW - 58, y + 5, ShopColors.TEXT_SECONDARY, false);
        }
    }

    private void renderFooter(GuiGraphics graphics) {
        graphics.drawString(this.font, "Page " + page + " / " + totalPages, guiLeft + guiW - 168, guiTop + guiH - 20, ShopColors.TEXT_SECONDARY, false);
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
        };
    }

    private int colorForType(String type) {
        return switch (type.toUpperCase()) {
            case "BUY" -> ShopColors.TEXT_PRICE;
            case "SELL" -> ShopColors.SUCCESS;
            case "BARTER" -> ShopColors.TEXT_BARTER;
            default -> ShopColors.TEXT_PRIMARY;
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
