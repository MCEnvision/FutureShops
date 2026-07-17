package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.ClientConfig;
import com.enviouse.futureshops.client.PlayerShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.SettlementHistoryRow;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SFetchSettlementHistoryPacket;
import net.minecraft.client.gui.GuiGraphics;
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
    private static final DateTimeFormatter TS_FORMAT_24 =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter TS_FORMAT_12 =
            DateTimeFormatter.ofPattern("MM-dd h:mm a").withZone(ZoneId.systemDefault());

    private final Screen parent;
    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;

    private SettlementHistoryRow.SettlementFilter filter = SettlementHistoryRow.SettlementFilter.ALL;
    private EditBox fromDateBox;
    private EditBox toDateBox;
    private boolean tightTopBar;

    /** Per-frame flat-button hit regions, populated in {@link #render}, consulted in mouseClicked. */
    private final java.util.List<ShopUiUtil.ClickZone> clickZones = new java.util.ArrayList<>();

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

        fromDateBox = new EditBox(this.font, dateStartX, dateRowY, dateBoxW, 14, Component.translatable("gui.futureshops.settlement.date.from"));
        fromDateBox.setMaxLength(10);
        fromDateBox.setHint(Component.translatable("gui.futureshops.settlement.date.hint"));
        addRenderableWidget(fromDateBox);

        toDateBox = new EditBox(this.font, dateStartX + dateBoxW + 4, dateRowY, dateBoxW, 14, Component.translatable("gui.futureshops.settlement.date.to"));
        toDateBox.setMaxLength(10);
        toDateBox.setHint(Component.translatable("gui.futureshops.settlement.date.hint"));
        addRenderableWidget(toDateBox);

        // Back / Filter / Apply / prev / next are flat Nocturne buttons drawn in render().
        request(PlayerShopClientState.settlementHistoryPage());
    }

    /** Draws every flat Nocturne button and registers its click zone for this frame. */
    private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + 8, guiTop + 8, 18, 14,
                Component.translatable("gui.futureshops.settlement.back"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, this::onClose);
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + 30, guiTop + 8, 70, 14, filterLabel(),
                ShopUiUtil.ButtonStyle.SECONDARY, true, () -> {
                    filter = switch (filter) {
                        case ALL -> SettlementHistoryRow.SettlementFilter.SALE;
                        case SALE -> SettlementHistoryRow.SettlementFilter.CLAIM;
                        case CLAIM -> SettlementHistoryRow.SettlementFilter.ROLLBACK;
                        case ROLLBACK -> SettlementHistoryRow.SettlementFilter.ALL;
                    };
                    request(1);
                });

        int dateRowY = tightTopBar ? guiTop + 24 : guiTop + 8;
        int applyW = 50;
        int applyX = guiLeft + guiW - applyW - 8;
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                applyX, dateRowY, applyW, 14,
                Component.translatable("gui.futureshops.settlement.apply"),
                ShopUiUtil.ButtonStyle.PRIMARY, true, () -> request(1));

        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + guiW / 2 - 46, guiTop + guiH - 18, 16, 14, Component.literal("<"),
                ShopUiUtil.ButtonStyle.SECONDARY, true,
                () -> request(PlayerShopClientState.settlementHistoryPage() - 1));
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + guiW / 2 + 30, guiTop + guiH - 18, 16, 14, Component.literal(">"),
                ShopUiUtil.ButtonStyle.SECONDARY, true,
                () -> request(PlayerShopClientState.settlementHistoryPage() + 1));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        clickZones.clear();
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

                // Item 12: Show item icon + quantity when available (NBT-aware: tagged
                // variants like enchanted books / TacZ guns render their real icon)
                int textStartX = guiLeft + 10;
                if (row.itemId() != null && !row.itemId().isBlank()) {
                    ShopUiUtil.renderItemIconWithNbt(graphics, this.font, row.itemId(), row.nbtJson(), guiLeft + 10, rowY);
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
                DateTimeFormatter timestampFormat = ClientConfig.use12HourTime() ? TS_FORMAT_12 : TS_FORMAT_24;
                String ts = timestampFormat.format(Instant.ofEpochSecond(row.timestampEpochSeconds()));
                // Item 12: Include item quantity in the row text
                String qtyStr = row.quantity() > 0 ? Component.translatable("gui.futureshops.settlement.qty_suffix", row.quantity()).getString() : "";
                String itemName = (row.itemId() != null && !row.itemId().isBlank())
                        ? ShopUiUtil.getItemDisplayNameWithNbt(row.itemId(), row.nbtJson()) : "";

                // Column layout — previously rendered as a single "type • amount • item • ts"
                // string. Long type labels like "MONEY_AND_BARTER" plus long mod item names
                // overflowed the row width and visually overlapped the timestamp.
                // Now: right-align the timestamp, then drop text from the middle columns
                // individually so each gets its own truncation budget.
                int rowTextY = rowY + 4;
                int rowEndX = guiLeft + guiW - 12;
                int tsW = this.font.width(ts);
                int tsX = rowEndX - tsW;
                graphics.drawString(this.font, ts, tsX, rowTextY, ShopColors.TEXT_SECONDARY, false);

                int middleAvailW = Math.max(0, tsX - textStartX - 6);
                String middleLine;
                if (!itemName.isBlank()) {
                    middleLine = left + " • " + amount + " • " + itemName + qtyStr;
                } else {
                    middleLine = left + " • " + amount;
                }
                graphics.drawString(this.font, this.font.plainSubstrByWidth(middleLine, middleAvailW),
                        textStartX, rowTextY, ShopColors.TEXT_PRIMARY, false);
            }
        }

        graphics.drawCenteredString(this.font,
                Component.translatable("gui.futureshops.history.page", PlayerShopClientState.settlementHistoryPage(), PlayerShopClientState.settlementHistoryTotalPages()),
                guiLeft + guiW / 2,
                guiTop + guiH - 14,
                ShopColors.TEXT_SECONDARY);

        renderButtons(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);

        // Item 12: Tooltip on hover over item icons in settlement history rows
        if (!rows.isEmpty()) {
            for (int i = 0; i < Math.min(maxRows, rows.size()); i++) {
                SettlementHistoryRow row = rows.get(i);
                if (row.itemId() == null || row.itemId().isBlank()) continue;
                int rowY = y + i * 18;
                int iconX = guiLeft + 10;
                if (mouseX >= iconX && mouseX < iconX + 16 && mouseY >= rowY && mouseY < rowY + 16) {
                    ShopUiUtil.renderItemTooltip(graphics, this.font, row.itemId(), row.nbtJson(), mouseX, mouseY);
                    break;
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ShopUiUtil.dispatchClicks(clickZones, mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private Component filterLabel() {
        String label = switch (filter) {
            case ALL -> net.minecraft.client.resources.language.I18n.get("gui.futureshops.settlement.filter.all");
            case SALE -> net.minecraft.client.resources.language.I18n.get("gui.futureshops.settlement.filter.sale");
            case CLAIM -> net.minecraft.client.resources.language.I18n.get("gui.futureshops.settlement.filter.claim");
            case ROLLBACK -> net.minecraft.client.resources.language.I18n.get("gui.futureshops.settlement.filter.rollback");
        };
        return Component.translatable("gui.futureshops.settlement.filter.label", label);
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
