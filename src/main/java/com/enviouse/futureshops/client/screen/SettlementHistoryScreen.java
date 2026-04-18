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
    private boolean tightTopBar;

    public SettlementHistoryScreen(Screen parent) {
        super(Component.translatable("gui.futureshops.settlement.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        guiW = Math.max(280, this.width - 4);
        guiH = Math.max(180, this.height - 4);
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;

        // Top bar collapses to two rows when guiW < 400 to avoid the date boxes
        // colliding with the Apply button or overflowing the right edge.
        tightTopBar = guiW < 400;

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

        int dateRowY = tightTopBar ? guiTop + 24 : guiTop + 8;
        int applyW = 50;
        int applyX;
        int dateStartX;
        int dateAvailW;
        if (tightTopBar) {
            dateStartX = guiLeft + 8;
            applyX = guiLeft + guiW - applyW - 8;
            dateAvailW = applyX - dateStartX - 6;
        } else {
            dateStartX = guiLeft + 106;
            applyX = guiLeft + guiW - applyW - 8;
            dateAvailW = applyX - dateStartX - 6;
        }
        int dateBoxW = Math.max(60, (dateAvailW - 4) / 2);

        fromDateBox = new EditBox(this.font, dateStartX, dateRowY, dateBoxW, 14, Component.literal("From"));
        fromDateBox.setMaxLength(10);
        fromDateBox.setHint(Component.literal("yyyy-mm-dd"));
        addRenderableWidget(fromDateBox);

        toDateBox = new EditBox(this.font, dateStartX + dateBoxW + 4, dateRowY, dateBoxW, 14, Component.literal("To"));
        toDateBox.setMaxLength(10);
        toDateBox.setHint(Component.literal("yyyy-mm-dd"));
        addRenderableWidget(toDateBox);

        addRenderableWidget(Button.builder(Component.literal("Apply"), button -> request(1))
                .bounds(applyX, dateRowY, applyW, 14)
                .build());

        addRenderableWidget(Button.builder(Component.literal("<"), button -> request(PlayerShopClientState.settlementHistoryPage() - 1))
                .bounds(guiLeft + guiW / 2 - 46, guiTop + guiH - 18, 16, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> request(PlayerShopClientState.settlementHistoryPage() + 1))
                .bounds(guiLeft + guiW / 2 + 30, guiTop + guiH - 18, 16, 14)
                .build());

        request(PlayerShopClientState.settlementHistoryPage());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + 2, ShopColors.ACCENT_CURRENCY);

        int titleY = tightTopBar ? guiTop + 42 : guiTop + 28;
        graphics.drawCenteredString(this.font, this.title, guiLeft + guiW / 2, titleY, ShopColors.TEXT_STRONG);

        List<SettlementHistoryRow> rows = PlayerShopClientState.settlementHistoryRows();
        int y = titleY + 18;
        int maxRows = Math.max(1, Math.min(PAGE_SIZE, (guiH - (tightTopBar ? 88 : 72)) / 18));
        if (rows.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.futureshops.settlement.empty"),
                    guiLeft + guiW / 2, guiTop + guiH / 2, ShopColors.TEXT_MUTED);
        } else {
            for (int i = 0; i < Math.min(maxRows, rows.size()); i++) {
                SettlementHistoryRow row = rows.get(i);
                int rowY = y + i * 18;
                int bg = i % 2 == 0 ? ShopColors.SURFACE_RAISED : ShopColors.SURFACE_OVERLAY;
                graphics.fill(guiLeft + 8, rowY, guiLeft + guiW - 8, rowY + 16, bg);

                // Item 12: Show item icon + quantity when available
                int textStartX = guiLeft + 10;
                if (row.itemId() != null && !row.itemId().isBlank()) {
                    ShopUiUtil.renderItemIcon(graphics, this.font, row.itemId(), guiLeft + 10, rowY);
                    textStartX = guiLeft + 28;
                }

                String typeKey = switch (row.type()) {
                    case "SALE" -> "gui.futureshops.settlement.type.sale";
                    case "CLAIM" -> "gui.futureshops.settlement.type.claim";
                    case "ROLLBACK" -> "gui.futureshops.settlement.type.rollback";
                    default -> "gui.futureshops.settlement.type.other";
                };
                String left = Component.translatable(typeKey).getString();
                String amount = ShopUiUtil.formatMinorUnits(row.amountMinor());
                String ts = TS_FORMAT.format(Instant.ofEpochSecond(row.timestampEpochSeconds()));
                // Item 12: Include item quantity in the row text
                String qtyStr = row.quantity() > 0 ? " x" + row.quantity() : "";
                String itemName = (row.itemId() != null && !row.itemId().isBlank())
                        ? ShopUiUtil.getItemDisplayName(row.itemId()) : "";
                String line;
                if (!itemName.isBlank()) {
                    line = left + " • " + amount + " • " + itemName + qtyStr + " • " + ts;
                } else {
                    line = Component.translatable("gui.futureshops.settlement.row", left, amount, ts).getString();
                }
                int availTextW = guiW - (textStartX - guiLeft) - 12;
                graphics.drawString(this.font, this.font.plainSubstrByWidth(line, availTextW), textStartX, rowY + 4, ShopColors.TEXT_PRIMARY, false);
            }
        }

        graphics.drawCenteredString(this.font,
                Component.translatable("gui.futureshops.history.page", PlayerShopClientState.settlementHistoryPage(), PlayerShopClientState.settlementHistoryTotalPages()),
                guiLeft + guiW / 2,
                guiTop + guiH - 14,
                ShopColors.TEXT_SECONDARY);

        super.render(graphics, mouseX, mouseY, partialTick);

        // Item 12: Tooltip on hover over item icons in settlement history rows
        if (!rows.isEmpty()) {
            for (int i = 0; i < Math.min(maxRows, rows.size()); i++) {
                SettlementHistoryRow row = rows.get(i);
                if (row.itemId() == null || row.itemId().isBlank()) continue;
                int rowY = y + i * 18;
                int iconX = guiLeft + 10;
                if (mouseX >= iconX && mouseX < iconX + 16 && mouseY >= rowY && mouseY < rowY + 16) {
                    ShopUiUtil.renderItemTooltip(graphics, this.font, row.itemId(), "", mouseX, mouseY);
                    break;
                }
            }
        }
    }

    private Component filterLabel() {
        String label = switch (filter) {
            case ALL -> "All";
            case SALE -> "Sale";
            case CLAIM -> "Cart Claim";
            case ROLLBACK -> "Rollback";
        };
        return Component.literal("Filter: " + label);
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
