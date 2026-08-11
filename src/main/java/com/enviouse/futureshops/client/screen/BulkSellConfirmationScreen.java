package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.BulkSellQuote;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SBulkSellCancelPacket;
import com.enviouse.futureshops.network.packets.C2SBulkSellCommitPacket;
import com.enviouse.futureshops.network.packets.C2SBulkSellQuotePacket;
import com.enviouse.futureshops.network.packets.S2CBulkSellResultPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class BulkSellConfirmationScreen extends Screen
        implements ShopScreenMarker {
    private static final int ROW_HEIGHT = 44;
    @Nullable
    private final Screen parent;
    private final BulkSellQuote quote;
    private final Set<String> selected = new LinkedHashSet<>();
    private final List<ShopUiUtil.ClickZone> clickZones =
            new ArrayList<>();
    private final List<Button> lineButtons = new ArrayList<>();
    private Button selectAllButton;
    private Button clearAllButton;
    private Button cancelButton;
    private Button confirmButton;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listY;
    private int listH;
    private int scroll;
    private boolean processing;
    @Nullable
    private S2CBulkSellResultPacket result;

    public BulkSellConfirmationScreen(
            @Nullable Screen parent,
            BulkSellQuote quote
    ) {
        super(Component.translatable(
                "gui.futureshops.bulk_sell.title"));
        this.parent = parent;
        this.quote = quote;
        if (quote.selectEligibleByDefault()) {
            quote.lines().stream()
                    .filter(BulkSellQuote.Line::eligible)
                    .map(BulkSellQuote.Line::lineId)
                    .forEach(selected::add);
        }
    }

    @Override
    protected void init() {
        panelX = Math.max(8, this.width / 14);
        panelY = Math.max(8, this.height / 14);
        panelW = this.width - panelX * 2;
        panelH = this.height - panelY * 2;
        listY = panelY + 46;
        int footerHeight = compactFooter() ? 84 : 62;
        listH = Math.max(ROW_HEIGHT,
                panelH - 46 - footerHeight);
        clampScroll();
        createLineButtons();
        createFooterButtons();
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        clickZones.clear();
        ShopUiUtil.renderDimBackdrop(
                graphics, this.width, this.height);
        graphics.fill(panelX, panelY,
                panelX + panelW, panelY + panelH,
                ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(
                graphics, panelX, panelY, panelW, panelH,
                ShopColors.BORDER_STRONG,
                ShopColors.BORDER_SUBTLE);
        graphics.fill(panelX, panelY,
                panelX + panelW, panelY + 2,
                ShopColors.ACCENT_PRIMARY);

        graphics.drawString(this.font, this.title,
                panelX + 12, panelY + 11,
                ShopColors.TEXT_STRONG, false);
        Component target = Component.translatable(
                quote.target()
                        == com.enviouse.futureshops.data
                        .BulkSellTarget.ADMIN_SHOP
                        ? "gui.futureshops.bulk_sell.target.admin"
                        : "gui.futureshops.bulk_sell.target.players");
        graphics.drawString(this.font, target,
                panelX + 12, panelY + 25,
                ShopColors.TEXT_MUTED, false);
        long remainingSeconds = Math.max(0L,
                (quote.expiresAtEpochMillis()
                        - System.currentTimeMillis() + 999L)
                        / 1000L);
        Component expires = Component.translatable(
                "gui.futureshops.bulk_sell.expires",
                remainingSeconds);
        graphics.drawString(this.font, expires,
                panelX + panelW - 12
                        - this.font.width(expires),
                panelY + 11, ShopColors.TEXT_MUTED, false);

        renderRows(graphics, mouseX, mouseY);
        renderFooter(graphics, mouseX, mouseY);
        updateButtons();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void createLineButtons() {
        lineButtons.clear();
        int visible = Math.max(1, listH / ROW_HEIGHT);
        for (int slot = 0; slot < visible; slot++) {
            int exactSlot = slot;
            Button button = FutureShopsButton.styled(
                            Component.empty(),
                            ignored -> toggleVisibleRow(exactSlot))
                    .bounds(panelX + 17,
                            listY + slot * ROW_HEIGHT + 14,
                            14, 14)
                    .build();
            lineButtons.add(button);
            addRenderableWidget(button);
        }
    }

    private void createFooterButtons() {
        boolean compact = compactFooter();
        int bottom = panelY + panelH;
        if (compact) {
            int gap = 4;
            int buttonWidth = (panelW - 28) / 2;
            int left = panelX + 12;
            int right = left + buttonWidth + gap;
            int firstRow = bottom - 42;
            int secondRow = bottom - 22;
            selectAllButton = footerButton(
                    "gui.futureshops.bulk_sell.select_all",
                    left, firstRow, buttonWidth,
                    ShopUiUtil.ButtonStyle.SECONDARY,
                    ignored -> selectAll(),
                    "gui.futureshops.bulk_sell.help.select_all");
            clearAllButton = footerButton(
                    "gui.futureshops.bulk_sell.clear_all",
                    right, firstRow, buttonWidth,
                    ShopUiUtil.ButtonStyle.SECONDARY,
                    ignored -> selected.clear(),
                    "gui.futureshops.bulk_sell.help.clear_all");
            cancelButton = footerButton(
                    "gui.futureshops.bulk_sell.cancel",
                    left, secondRow, buttonWidth,
                    ShopUiUtil.ButtonStyle.SECONDARY,
                    ignored -> onClose(),
                    "gui.futureshops.bulk_sell.help.cancel");
            confirmButton = footerButton(
                    "gui.futureshops.bulk_sell.confirm",
                    right, secondRow, buttonWidth,
                    ShopUiUtil.ButtonStyle.PRIMARY,
                    ignored -> confirm(),
                    "gui.futureshops.bulk_sell.help.confirm");
            return;
        }
        int buttonY = bottom - 22;
        selectAllButton = footerButton(
                "gui.futureshops.bulk_sell.select_all",
                panelX + 12, buttonY, 70,
                ShopUiUtil.ButtonStyle.SECONDARY,
                ignored -> selectAll(),
                "gui.futureshops.bulk_sell.help.select_all");
        clearAllButton = footerButton(
                "gui.futureshops.bulk_sell.clear_all",
                panelX + 86, buttonY, 70,
                ShopUiUtil.ButtonStyle.SECONDARY,
                ignored -> selected.clear(),
                "gui.futureshops.bulk_sell.help.clear_all");
        cancelButton = footerButton(
                "gui.futureshops.bulk_sell.cancel",
                panelX + panelW - 202, buttonY, 70,
                ShopUiUtil.ButtonStyle.SECONDARY,
                ignored -> onClose(),
                "gui.futureshops.bulk_sell.help.cancel");
        confirmButton = footerButton(
                "gui.futureshops.bulk_sell.confirm",
                panelX + panelW - 128, buttonY, 116,
                ShopUiUtil.ButtonStyle.PRIMARY,
                ignored -> confirm(),
                "gui.futureshops.bulk_sell.help.confirm");
    }

    private Button footerButton(
            String labelKey,
            int x,
            int y,
            int width,
            ShopUiUtil.ButtonStyle style,
            Button.OnPress action,
            String helpKey
    ) {
        Button button = FutureShopsButton.styled(
                        Component.translatable(labelKey), action)
                .bounds(x, y, width, 16)
                .style(style)
                .build();
        button.setTooltip(Tooltip.create(
                Component.translatable(helpKey)));
        addRenderableWidget(button);
        return button;
    }

    private void updateButtons() {
        int first = visibleFirstLine();
        for (int slot = 0; slot < lineButtons.size(); slot++) {
            Button button = lineButtons.get(slot);
            int index = first + slot;
            if (index >= quote.lines().size()) {
                button.visible = false;
                continue;
            }
            BulkSellQuote.Line line = quote.lines().get(index);
            button.visible = true;
            button.active = line.eligible() && !processing
                    && result == null;
            boolean included = selected.contains(line.lineId());
            button.setMessage(Component.literal(included ? "✓" : ""));
            button.setTooltip(Tooltip.create(
                    lineHelp(line, included)));
        }
        selectAllButton.active = !processing && result == null;
        clearAllButton.active = !processing && result == null
                && !selected.isEmpty();
        cancelButton.active = !processing;
        confirmButton.active = canConfirm();
        confirmButton.setMessage(Component.translatable(
                processing
                        ? "gui.futureshops.bulk_sell.processing"
                        : "gui.futureshops.bulk_sell.confirm"));
    }

    private int visibleFirstLine() {
        return Math.min(scroll, Math.max(0,
                quote.lines().size() - lineButtons.size()));
    }

    private void toggleVisibleRow(int slot) {
        int index = visibleFirstLine() + slot;
        if (index < quote.lines().size()) {
            BulkSellQuote.Line line = quote.lines().get(index);
            if (line.eligible() && !processing && result == null) {
                toggle(line.lineId());
            }
        }
    }

    private void renderRows(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        if (quote.lines().isEmpty()) {
            graphics.drawCenteredString(
                    this.font,
                    Component.translatable(
                            "gui.futureshops.bulk_sell.empty"),
                    panelX + panelW / 2,
                    listY + Math.max(8, listH / 2),
                    ShopColors.TEXT_MUTED);
            return;
        }
        int visible = Math.max(1, listH / ROW_HEIGHT);
        int first = visibleFirstLine();
        ItemStack tooltip = ItemStack.EMPTY;
        graphics.enableScissor(
                panelX + 8, listY,
                panelX + panelW - 8, listY + listH);
        for (int index = first;
             index < quote.lines().size()
                     && index < first + visible;
             index++) {
            BulkSellQuote.Line line = quote.lines().get(index);
            int y = listY + (index - first) * ROW_HEIGHT;
            boolean hovered = mouseX >= panelX + 10
                    && mouseX < panelX + panelW - 10
                    && mouseY >= y + 2
                    && mouseY < y + ROW_HEIGHT - 2;
            int fill = hovered && line.eligible()
                    ? ShopColors.SURFACE_OVERLAY
                    : ShopColors.SURFACE_RAISED;
            graphics.fill(panelX + 10, y + 2,
                    panelX + panelW - 10,
                    y + ROW_HEIGHT - 2, fill);
            ShopUiUtil.drawSoftOutline(
                    graphics, panelX + 10, y + 2,
                    panelW - 20, ROW_HEIGHT - 4,
                    line.eligible()
                            ? ShopColors.BORDER_MUTED
                            : ShopColors.BORDER_SUBTLE,
                    ShopColors.BORDER_SUBTLE);

            BulkSellQuote.Component firstComponent =
                    line.inputs().get(0);
            ItemStack stack = ShopUiUtil.buildItemStack(
                    firstComponent.itemId(),
                    firstComponent.exactNbt()).copy();
            int totalCount = Math.multiplyExact(
                    firstComponent.count(), line.quantity());
            stack.setCount(Math.min(totalCount,
                    Math.max(1, stack.getMaxStackSize())));
            int iconX = panelX + 38;
            int iconY = y + 14;
            graphics.renderItem(stack, iconX, iconY);
            graphics.renderItemDecorations(
                    this.font, stack, iconX, iconY,
                    totalCount > 1
                            ? Integer.toString(totalCount) : null);
            if (mouseX >= iconX && mouseX < iconX + 16
                    && mouseY >= iconY && mouseY < iconY + 16) {
                tooltip = stack;
            }

            String name = lineName(line);
            int textX = panelX + 60;
            Component value = line.eligible()
                    ? Component.translatable(
                    "gui.futureshops.bulk_sell.line_value",
                    format(line.unitPayoutMinorUnits()),
                    format(line.totalPayoutMinorUnits()))
                    : Component.translatable(line.reasonKey());
            String valueText = this.font.plainSubstrByWidth(
                    value.getString(),
                    Math.max(52, panelW / 3));
            int valueX = panelX + panelW - 18
                    - this.font.width(valueText);
            graphics.drawString(this.font,
                    this.font.plainSubstrByWidth(
                            name, Math.max(
                                    24, valueX - textX - 8)),
                    textX, y + 8,
                    line.eligible()
                            ? ShopColors.TEXT_STRONG
                            : ShopColors.TEXT_MUTED,
                    false);
            Component destination = destination(line);
            String destinationText = Component.translatable(
                    "gui.futureshops.bulk_sell.destination",
                    destination).getString();
            graphics.drawString(this.font,
                    this.font.plainSubstrByWidth(
                            destinationText,
                            Math.max(24,
                                    panelX + panelW - 18 - textX)),
                    textX, y + 25,
                    ShopColors.TEXT_MUTED, false);

            graphics.drawString(this.font, valueText,
                    valueX, y + 8,
                    line.eligible()
                            ? ShopColors.STATUS_SUCCESS
                            : ShopColors.STATUS_DANGER,
                    false);
            ShopUiUtil.zone(clickZones,
                    panelX + 10, y + 2,
                    panelW - 20, ROW_HEIGHT - 4,
                    line.eligible() && !processing,
                    () -> toggle(line.lineId()));
        }
        graphics.disableScissor();
        if (!tooltip.isEmpty()) {
            graphics.renderTooltip(
                    this.font, tooltip, mouseX, mouseY);
        }
    }

    private void renderFooter(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        boolean compact = compactFooter();
        int bottom = panelY + panelH;
        int y = bottom - (compact ? 80 : 58);
        Component total = Component.translatable(
                "gui.futureshops.bulk_sell.selected_total",
                format(selectedTotal()));
        graphics.drawString(this.font, total,
                panelX + 12, y + 4,
                ShopColors.TEXT_STRONG, false);
        if (result != null) {
            Component status = Component.translatable(
                    "gui.futureshops.bulk_sell.result."
                            + result.status().name()
                            .toLowerCase(java.util.Locale.ROOT),
                    result.soldLines(), result.failedLines(),
                    result.recoveryLines(),
                    format(
                            result.paidMinorUnits(),
                            result.currencyDecimals(),
                            result.currencyName()));
            graphics.drawString(this.font,
                    this.font.plainSubstrByWidth(
                            status.getString(),
                            Math.max(40, panelW - 24)),
                    panelX + 12, y + 17,
                    resultColor(result),
                    false);
        }
    }

    private boolean canConfirm() {
        return !processing && !selected.isEmpty()
                && result == null;
    }

    private boolean compactFooter() {
        return panelW < 420;
    }

    private void toggle(String lineId) {
        if (!selected.remove(lineId)) {
            selected.add(lineId);
        }
    }

    private void selectAll() {
        quote.lines().stream()
                .filter(BulkSellQuote.Line::eligible)
                .map(BulkSellQuote.Line::lineId)
                .forEach(selected::add);
    }

    private String lineName(BulkSellQuote.Line line) {
        BulkSellQuote.Component first = line.inputs().get(0);
        String name = ShopUiUtil.getItemDisplayNameWithNbt(
                first.itemId(), first.exactNbt());
        return line.inputs().size() > 1
                ? Component.translatable(
                "gui.futureshops.bulk_sell.bundle_name",
                name, line.inputs().size() - 1).getString()
                : name;
    }

    private Component destination(BulkSellQuote.Line line) {
        return line.destination().startsWith("gui.futureshops.")
                ? Component.translatable(line.destination())
                : Component.literal(line.destination());
    }

    private Component lineHelp(
            BulkSellQuote.Line line,
            boolean included
    ) {
        Component value = line.eligible()
                ? Component.literal(format(
                line.totalPayoutMinorUnits()))
                : Component.translatable(line.reasonKey());
        return Component.translatable(
                "gui.futureshops.bulk_sell.help.line",
                lineName(line), destination(line), value,
                Component.translatable(included
                        ? "gui.futureshops.bulk_sell.included"
                        : "gui.futureshops.bulk_sell.excluded"));
    }

    private long selectedTotal() {
        long total = 0L;
        for (BulkSellQuote.Line line : quote.lines()) {
            if (selected.contains(line.lineId())) {
                total = Math.addExact(
                        total, line.totalPayoutMinorUnits());
            }
        }
        return total;
    }

    private String format(long minorUnits) {
        return format(
                minorUnits,
                quote.currencyDecimals(),
                quote.currencyName());
    }

    private static String format(
            long minorUnits,
            int decimals,
            String currencyName
    ) {
        return BigDecimal.valueOf(minorUnits, decimals)
                .toPlainString() + " " + currencyName;
    }

    private static int resultColor(
            S2CBulkSellResultPacket packet
    ) {
        return switch (packet.status()) {
            case SUCCESS -> ShopColors.STATUS_SUCCESS;
            case PARTIAL -> ShopColors.STATUS_WARNING;
            default -> ShopColors.STATUS_DANGER;
        };
    }

    private void confirm() {
        processing = true;
        ShopPackets.CHANNEL.sendToServer(
                new C2SBulkSellCommitPacket(
                        quote.quoteId(),
                        List.copyOf(selected)));
    }

    public void onResult(S2CBulkSellResultPacket packet) {
        if (!quote.quoteId().equals(packet.quoteId())) {
            return;
        }
        if (packet.status()
                == com.enviouse.futureshops.server.shop
                .BulkSellService.Status.QUOTE_EXPIRED) {
            processing = true;
            result = null;
            ShopPackets.CHANNEL.sendToServer(
                    new C2SBulkSellQuotePacket(
                            quote.target(), quote.shopId(),
                            quote.selectEligibleByDefault()));
            return;
        }
        processing = false;
        result = packet;
    }

    public void onQuoteRejected() {
        processing = false;
    }

    @Nullable
    public Screen returnScreen() {
        return parent;
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (ShopUiUtil.dispatchClicks(
                clickZones, mouseX, mouseY)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double delta
    ) {
        if (delta != 0.0D) {
            scroll += delta < 0.0D ? 1 : -1;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(
            int keyCode,
            int scanCode,
            int modifiers
    ) {
        int page = Math.max(1, lineButtons.size());
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            scroll += page;
            clampScroll();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            scroll -= page;
            clampScroll();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            scroll = 0;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            scroll = quote.lines().size();
            clampScroll();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void clampScroll() {
        int visible = Math.max(1, listH / ROW_HEIGHT);
        scroll = Math.max(0, Math.min(scroll,
                Math.max(0, quote.lines().size() - visible)));
    }

    @Override
    public void onClose() {
        if (!processing && result == null) {
            ShopPackets.CHANNEL.sendToServer(
                    new C2SBulkSellCancelPacket(quote.quoteId()));
        }
        Minecraft client = this.minecraft != null
                ? this.minecraft : Minecraft.getInstance();
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
