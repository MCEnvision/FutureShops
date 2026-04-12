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
    private static final int GUI_W = 260;
    private static final int GUI_H = 200;
    private static final int PAGE_SIZE = 10;

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Screen parent;

    private int guiLeft;
    private int guiTop;
    private int page = 1;
    private int totalPages = 1;
    private TransactionHistoryEntry.HistoryFilter activeFilter = TransactionHistoryEntry.HistoryFilter.ALL;
    private TransactionHistoryEntry.SortOrder sortOrder = TransactionHistoryEntry.SortOrder.NEWEST;
    private TransactionHistoryEntry.TimeWindow timeWindow = TransactionHistoryEntry.TimeWindow.ALL;

    private final Map<TransactionHistoryEntry.HistoryFilter, Button> filterButtons =
            new EnumMap<>(TransactionHistoryEntry.HistoryFilter.class);

    private EditBox searchBox;

    public TransactionHistoryScreen(Screen parent) {
        super(Component.translatable("gui.futureshops.history.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        guiLeft = (this.width - GUI_W) / 2;
        guiTop = (this.height - GUI_H) / 2;

        addRenderableWidget(Button.builder(Component.literal("<-"), button -> onClose())
                .bounds(guiLeft + 6, guiTop + 6, 18, 16)
                .build());

        searchBox = new EditBox(this.font, guiLeft + 30, guiTop + 8, 110, 14,
                Component.translatable("gui.futureshops.shop.search"));
        searchBox.setMaxLength(30);
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
                .bounds(guiLeft + 144, guiTop + 8, 54, 14)
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
                .bounds(guiLeft + 202, guiTop + 8, 52, 14)
                .build());

        addRenderableWidget(Button.builder(Component.literal("<"), button -> {
                    page = Math.max(1, page - 1);
                    requestPage();
                })
                .bounds(guiLeft + 80, guiTop + GUI_H - 20, 16, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> {
                    page = Math.min(totalPages, page + 1);
                    requestPage();
                })
                .bounds(guiLeft + GUI_W - 96, guiTop + GUI_H - 20, 16, 14)
                .build());

        addFilterButton(TransactionHistoryEntry.HistoryFilter.ALL, "gui.futureshops.history.filter.all", guiLeft + 30);
        addFilterButton(TransactionHistoryEntry.HistoryFilter.BUY, "gui.futureshops.history.filter.buy", guiLeft + 88);
        addFilterButton(TransactionHistoryEntry.HistoryFilter.SELL, "gui.futureshops.history.filter.sell", guiLeft + 146);
        addFilterButton(TransactionHistoryEntry.HistoryFilter.BARTER, "gui.futureshops.history.filter.barter", guiLeft + 204);
        refreshFilterButtons();

        requestPage();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x88000000);
        graphics.fill(guiLeft, guiTop, guiLeft + GUI_W, guiTop + GUI_H, ShopColors.BG_PANEL);
        ShopUiUtil.drawBorder(graphics, guiLeft, guiTop, GUI_W, GUI_H, ShopColors.BORDER_DEFAULT);

        graphics.drawCenteredString(this.font, this.title, guiLeft + GUI_W / 2, guiTop + 28, ShopColors.TEXT_PRIMARY);
        graphics.drawString(this.font, Component.translatable("gui.futureshops.history.filter.label"),
                guiLeft + 30, guiTop + 46, ShopColors.TEXT_SECONDARY, false);

        List<TransactionHistoryEntry> entries = ShopClientState.getTransactionHistory();
        if (entries.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.history.empty"),
                    guiLeft + GUI_W / 2, guiTop + 105, ShopColors.TEXT_SECONDARY);
        } else {
            int rowY = guiTop + 62;
            for (int i = 0; i < Math.min(entries.size(), 6); i++) {
                TransactionHistoryEntry entry = entries.get(i);
                int y = rowY + i * 20;
                int bg = i % 2 == 0 ? ShopColors.BG_CARD : ShopColors.BG_PANEL;
                graphics.fill(guiLeft + 6, y, guiLeft + GUI_W - 6, y + 18, bg);
                ShopUiUtil.drawBorder(graphics, guiLeft + 6, y, GUI_W - 12, 18, ShopColors.BORDER_DEFAULT);

                String left = entry.type() + " " + ShopUiUtil.getItemDisplayName(entry.itemId()) + " x" + entry.quantity();
                String right = entry.totalMinorUnits() > 0L ? ShopUiUtil.formatMinorUnits(entry.totalMinorUnits()) : "-";
                graphics.drawString(this.font, this.font.plainSubstrByWidth(left, 152), guiLeft + 10, y + 5, ShopColors.TEXT_PRIMARY, false);
                graphics.drawString(this.font, right, guiLeft + 168, y + 5,
                        entry.type().equals("SELL") ? ShopColors.SUCCESS : ShopColors.TEXT_PRICE, false);

                String ts = TS_FORMAT.format(Instant.ofEpochSecond(entry.timestampEpochSeconds()));
                graphics.drawString(this.font, ts, guiLeft + GUI_W - 52, y + 5, ShopColors.TEXT_SECONDARY, false);
            }
        }

        graphics.drawCenteredString(this.font,
                Component.translatable("gui.futureshops.history.page", page, totalPages),
                guiLeft + GUI_W / 2, guiTop + GUI_H - 16, ShopColors.TEXT_SECONDARY);

        super.render(graphics, mouseX, mouseY, partialTick);
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

    private void addFilterButton(TransactionHistoryEntry.HistoryFilter filter, String labelKey, int x) {
        Button button = Button.builder(Component.translatable(labelKey), ignored -> {
                    if (activeFilter == filter) {
                        return;
                    }
                    activeFilter = filter;
                    page = 1;
                    refreshFilterButtons();
                    requestPage();
                })
                .bounds(x, guiTop + 44, 54, 14)
                .build();
        filterButtons.put(filter, button);
        addRenderableWidget(button);
    }

    private void refreshFilterButtons() {
        filterButtons.forEach((filter, button) -> button.active = filter != activeFilter);
    }

    private Component sortLabel() {
        return Component.literal(sortOrder == TransactionHistoryEntry.SortOrder.NEWEST ? "Newest" : "Oldest");
    }

    private Component windowLabel() {
        return Component.literal(switch (timeWindow) {
            case ALL -> "All";
            case DAY -> "24h";
            case WEEK -> "7d";
            case MONTH -> "30d";
        });
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
