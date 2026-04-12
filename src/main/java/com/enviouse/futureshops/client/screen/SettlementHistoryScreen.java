package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.PlayerShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.SettlementHistoryRow;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SFetchSettlementHistoryPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SettlementHistoryScreen extends Screen implements ShopScreenMarker {
    private static final int PAGE_SIZE = 8;
    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Screen parent;
    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;

    private SettlementHistoryRow.SettlementFilter filter = SettlementHistoryRow.SettlementFilter.ALL;
    private EditBox fromDateBox;
    private EditBox toDateBox;

    public SettlementHistoryScreen(Screen parent) {
        super(Component.translatable("gui.futureshops.settlement.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        guiW = Math.min(320, this.width - 16);
        guiH = Math.min(210, this.height - 16);
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;

        addRenderableWidget(Button.builder(Component.literal("<-"), button -> onClose())
                .bounds(guiLeft + 8, guiTop + 8, 18, 14)
                .build());

        addRenderableWidget(Button.builder(filterLabel(), button -> {
                    filter = switch (filter) {
                        case ALL -> SettlementHistoryRow.SettlementFilter.SALE;
                        case SALE -> SettlementHistoryRow.SettlementFilter.CLAIM;
                        case CLAIM -> SettlementHistoryRow.SettlementFilter.ROLLBACK;
                        case ROLLBACK -> SettlementHistoryRow.SettlementFilter.ALL;
                    };
                    button.setMessage(filterLabel());
                    request(1);
                })
                .bounds(guiLeft + 30, guiTop + 8, 70, 14)
                .build());

        fromDateBox = new EditBox(this.font, guiLeft + 106, guiTop + 8, 92, 14, Component.literal("From"));
        fromDateBox.setMaxLength(10);
        fromDateBox.setHint(Component.literal("yyyy-mm-dd"));
        addRenderableWidget(fromDateBox);

        toDateBox = new EditBox(this.font, guiLeft + 202, guiTop + 8, 92, 14, Component.literal("To"));
        toDateBox.setMaxLength(10);
        toDateBox.setHint(Component.literal("yyyy-mm-dd"));
        addRenderableWidget(toDateBox);

        addRenderableWidget(Button.builder(Component.literal("Apply"), button -> request(1))
                .bounds(guiLeft + guiW - 58, guiTop + guiH - 20, 50, 14)
                .build());

        addRenderableWidget(Button.builder(Component.literal("<"), button -> request(PlayerShopClientState.settlementHistoryPage() - 1))
                .bounds(guiLeft + guiW / 2 - 22, guiTop + guiH - 20, 16, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> request(PlayerShopClientState.settlementHistoryPage() + 1))
                .bounds(guiLeft + guiW / 2 + 6, guiTop + guiH - 20, 16, 14)
                .build());

        request(PlayerShopClientState.settlementHistoryPage());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, ShopColors.BG_PRIMARY);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.BG_PANEL);
        ShopUiUtil.drawBorder(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_DEFAULT);

        graphics.drawCenteredString(this.font, this.title, guiLeft + guiW / 2, guiTop + 28, ShopColors.TEXT_PRIMARY);

        List<SettlementHistoryRow> rows = PlayerShopClientState.settlementHistoryRows();
        int y = guiTop + 46;
        int maxRows = Math.max(1, Math.min(PAGE_SIZE, (guiH - 72) / 18));
        if (rows.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.settlement.empty"),
                    guiLeft + guiW / 2, guiTop + guiH / 2, ShopColors.TEXT_SECONDARY);
        } else {
            for (int i = 0; i < Math.min(maxRows, rows.size()); i++) {
                SettlementHistoryRow row = rows.get(i);
                int rowY = y + i * 18;
                int bg = i % 2 == 0 ? ShopColors.BG_CARD : ShopColors.BG_PANEL;
                graphics.fill(guiLeft + 8, rowY, guiLeft + guiW - 8, rowY + 16, bg);
                String typeKey = switch (row.type()) {
                    case "SALE" -> "gui.futureshops.settlement.type.sale";
                    case "CLAIM" -> "gui.futureshops.settlement.type.claim";
                    case "ROLLBACK" -> "gui.futureshops.settlement.type.rollback";
                    default -> "gui.futureshops.settlement.type.other";
                };
                String left = Component.translatable(typeKey).getString();
                String amount = ShopUiUtil.formatMinorUnits(row.amountMinor());
                String ts = TS_FORMAT.format(Instant.ofEpochSecond(row.timestampEpochSeconds()));
                String line = Component.translatable("gui.futureshops.settlement.row", left, amount, ts).getString();
                graphics.drawString(this.font, this.font.plainSubstrByWidth(line, guiW - 20), guiLeft + 10, rowY + 4, ShopColors.TEXT_PRIMARY, false);
            }
        }

        graphics.drawCenteredString(this.font,
                Component.translatable("gui.futureshops.history.page", PlayerShopClientState.settlementHistoryPage(), PlayerShopClientState.settlementHistoryTotalPages()),
                guiLeft + guiW / 2,
                guiTop + guiH - 16,
                ShopColors.TEXT_SECONDARY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private Component filterLabel() {
        return Component.literal("Filter: " + filter.name());
    }

    private long parseDate(String raw, boolean endOfDay) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            LocalDate date = LocalDate.parse(raw.trim());
            long base = date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
            return endOfDay ? base + 86_399L : base;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private void request(int page) {
        int safePage = Math.max(1, page);
        long fromEpoch = parseDate(fromDateBox == null ? "" : fromDateBox.getValue(), false);
        long toEpoch = parseDate(toDateBox == null ? "" : toDateBox.getValue(), true);
        ShopPackets.CHANNEL.sendToServer(new C2SFetchSettlementHistoryPacket(
                PlayerShopClientState.shopPos(),
                safePage,
                PAGE_SIZE,
                filter,
                fromEpoch,
                toEpoch));
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
