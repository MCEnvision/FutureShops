package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.command.EconomyCommandUtil;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.AtmDenominationData;
import com.enviouse.futureshops.money.CurrencyMath;
import com.enviouse.futureshops.money.CurrencyWithdrawalService;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SAtmWithdrawPacket;
import com.enviouse.futureshops.network.packets.C2SOpenBalanceUiPacket;
import com.enviouse.futureshops.network.packets.S2CAtmDataPacket;
import com.enviouse.futureshops.network.packets.S2CAtmResultPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Physical-currency ATM. Players may enter an amount for automatic change or
 * manually adjust every denomination before submitting the exact bill plan.
 */
public final class AtmScreen extends Screen {
    private final Screen parent;
    private S2CAtmDataPacket data;
    private long balanceMinor;
    private int[] counts;

    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;
    private int listScroll;
    private int visibleRows;
    private EditBox amountBox;
    private boolean awaiting;
    private Component status;
    private boolean statusSuccess;

    private final List<ShopUiUtil.ClickZone> clickZones = new ArrayList<>();

    public AtmScreen(Screen parent, S2CAtmDataPacket data) {
        super(Component.translatable("gui.futureshops.atm.title"));
        this.parent = parent;
        this.data = data;
        this.balanceMinor = data.balanceMinor();
        this.counts = new int[data.denominations().size()];
    }

    public void applyData(S2CAtmDataPacket next) {
        boolean changed = !next.currencySignature().equals(data.currencySignature());
        this.data = next;
        this.balanceMinor = next.balanceMinor();
        if (changed || counts.length != next.denominations().size()) {
            this.counts = new int[next.denominations().size()];
            this.listScroll = 0;
            if (amountBox != null) amountBox.setValue("");
        }
    }

    public void applyResult(S2CAtmResultPacket result) {
        awaiting = false;
        balanceMinor = result.balanceMinor();
        statusSuccess = result.success();
        String key = "gui.futureshops.atm.result." + result.code().toLowerCase(Locale.ROOT);
        status = result.success()
                ? Component.translatable(key, format(result.amountMinor()))
                : Component.translatable(key);
        if (result.success()) {
            Arrays.fill(counts, 0);
            if (amountBox != null) amountBox.setValue("");
        }
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(result.success()
                    ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.NOTE_BLOCK_BASS.value(),
                    0.7F, result.success() ? 1.25F : 0.7F);
        }
    }

    @Override
    protected void init() {
        guiW = Math.min(520, Math.max(340, this.width - 8));
        guiH = Math.min(350, Math.max(240, this.height - 8));
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;
        int listTop = guiTop + 126;
        int listBottom = guiTop + guiH - 54;
        visibleRows = Math.max(1, (listBottom - listTop) / 22);

        String previous = amountBox == null ? "" : amountBox.getValue();
        amountBox = new EditBox(this.font, guiLeft + 82, guiTop + 98, 82, 16,
                Component.translatable("gui.futureshops.atm.amount"));
        amountBox.setMaxLength(20);
        amountBox.setFilter(AtmScreen::isAmountText);
        amountBox.setValue(previous);
        amountBox.setHint(Component.translatable("gui.futureshops.atm.amount_hint"));
        addRenderableWidget(amountBox);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        clickZones.clear();
        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        ShopUiUtil.renderShellWindow(graphics, guiLeft, guiTop, guiW, guiH);
        graphics.fill(guiLeft + 2, guiTop, guiLeft + guiW - 2, guiTop + 2, ShopColors.ACCENT_CURRENCY);

        renderHeader(graphics);
        renderSecurity(graphics);
        renderAmountControls(graphics, mouseX, mouseY);
        renderDenominations(graphics, mouseX, mouseY);
        renderFooter(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics graphics) {
        int x = guiLeft + 10;
        int y = guiTop + 8;
        int w = guiW - 20;
        ShopUiUtil.renderElevatedCard(graphics, x, y, w, 42);
        graphics.drawString(this.font, this.title, x + 10, y + 8, ShopColors.TEXT_STRONG, false);
        graphics.drawString(this.font,
                Component.translatable("gui.futureshops.atm.provider", data.providerId()),
                x + 10, y + 23, ShopColors.TEXT_MUTED, false);
        String balance = Component.translatable("gui.futureshops.atm.balance",
                format(balanceMinor), data.currencyName()).getString();
        graphics.drawString(this.font, balance, x + w - 10 - this.font.width(balance),
                y + 15, ShopColors.TEXT_CURRENCY, false);
    }

    private void renderSecurity(GuiGraphics graphics) {
        int x = guiLeft + 10;
        int y = guiTop + 56;
        int w = guiW - 20;
        int accent = data.protectedMinting() ? ShopColors.STATUS_SUCCESS : ShopColors.STATUS_DANGER;
        ShopUiUtil.renderPanel(graphics, x, y, w, 32, ShopColors.SURFACE_RAISED, accent);
        graphics.fill(x, y, x + 3, y + 32, accent);
        graphics.drawString(this.font, Component.translatable(data.protectedMinting()
                        ? "gui.futureshops.atm.security.protected"
                        : "gui.futureshops.atm.security.unprotected"),
                x + 9, y + 6, accent, false);
        String detail = Component.translatable(data.protectedMinting()
                ? "gui.futureshops.atm.security.protected_detail"
                : "gui.futureshops.atm.security.unprotected_detail").getString();
        graphics.drawString(this.font, this.font.plainSubstrByWidth(detail, w - 18),
                x + 9, y + 18, ShopColors.TEXT_MUTED, false);
    }

    private void renderAmountControls(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = guiTop + 98;
        graphics.drawString(this.font, Component.translatable("gui.futureshops.atm.amount"),
                guiLeft + 12, y + 4, ShopColors.TEXT_MUTED, false);
        int bx = guiLeft + 168;
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                bx, y, 40, 16, Component.translatable("gui.futureshops.atm.auto"),
                ShopUiUtil.ButtonStyle.PRIMARY, !awaiting, this::autoFromField);
        bx += 42;
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                bx, y, 36, 16, Component.literal("25%"),
                ShopUiUtil.ButtonStyle.SECONDARY, !awaiting, () -> autoFromBalance(25));
        bx += 38;
        if (guiW >= 420) {
            ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                    bx, y, 36, 16, Component.literal("50%"),
                    ShopUiUtil.ButtonStyle.SECONDARY, !awaiting, () -> autoFromBalance(50));
            bx += 38;
        }
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                bx, y, 36, 16, Component.translatable("gui.futureshops.atm.max"),
                ShopUiUtil.ButtonStyle.SECONDARY, !awaiting, () -> autoFromBalance(100));
        bx += 38;
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                bx, y, 42, 16, Component.translatable("gui.futureshops.atm.clear"),
                ShopUiUtil.ButtonStyle.SECONDARY, !awaiting, this::clearSelection);
    }

    private void renderDenominations(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = guiLeft + 10;
        int y = guiTop + 122;
        int w = guiW - 20;
        int h = guiH - 172;
        ShopUiUtil.renderCard(graphics, x, y, w, h);
        graphics.fill(x, y, x + w, y + 2, ShopColors.ACCENT_CURRENCY);
        graphics.drawString(this.font, Component.translatable("gui.futureshops.atm.denominations"),
                x + 8, y + 7, ShopColors.TEXT_STRONG, false);
        graphics.drawString(this.font, Component.translatable("gui.futureshops.atm.step_hint"),
                x + w - 8 - this.font.width(Component.translatable("gui.futureshops.atm.step_hint")),
                y + 7, ShopColors.TEXT_FAINT, false);

        List<AtmDenominationData> denominations = data.denominations();
        int maxScroll = Math.max(0, denominations.size() - visibleRows);
        listScroll = Math.max(0, Math.min(listScroll, maxScroll));
        int listY = y + 20;
        for (int i = listScroll; i < denominations.size() && i < listScroll + visibleRows; i++) {
            AtmDenominationData denomination = denominations.get(i);
            int rowY = listY + (i - listScroll) * 22;
            graphics.fill(x + 6, rowY, x + w - 6, rowY + 20,
                    (i & 1) == 0 ? ShopColors.SURFACE_RAISED : ShopColors.SURFACE_OVERLAY);
            ShopUiUtil.renderItemIconWithNbt(graphics, this.font, denomination.itemId(), "", x + 9, rowY + 2);
            String itemName = ShopUiUtil.getItemDisplayNameWithNbt(denomination.itemId(), "");
            graphics.drawString(this.font, this.font.plainSubstrByWidth(itemName, Math.max(40, w - 230)),
                    x + 30, rowY + 3, ShopColors.TEXT_STRONG, false);
            graphics.drawString(this.font, format(denomination.valueMinor()),
                    x + 30, rowY + 12, ShopColors.TEXT_CURRENCY, false);

            final int index = i;
            ShopUiUtil.Stepper stepper = ShopUiUtil.renderStepper(graphics, this.font,
                    x + w - 100, rowY + 3, Integer.toString(counts[i]), 40, 14);
            ShopUiUtil.zone(clickZones, stepper.minusX(), rowY + 3, stepper.btn(), stepper.btn(),
                    !awaiting && counts[i] > 0, () -> adjust(index, hasShiftDown() ? -10 : -1));
            ShopUiUtil.zone(clickZones, stepper.plusX(), rowY + 3, stepper.btn(), stepper.btn(),
                    !awaiting, () -> adjust(index, hasShiftDown() ? 10 : 1));
        }
        ShopUiUtil.renderScrollIndicators(graphics, this.font, x, listY, w,
                Math.max(1, visibleRows) * 22, listScroll, visibleRows, denominations.size());
    }

    private void renderFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = guiTop + guiH - 42;
        long selected = selectedTotal();
        int billCount = Arrays.stream(counts).sum();
        int stacks = estimatedStacks();
        String summary = Component.translatable("gui.futureshops.atm.selected",
                format(selected), billCount, stacks).getString();
        graphics.drawString(this.font, this.font.plainSubstrByWidth(summary, guiW - 160),
                guiLeft + 12, y, selected > 0 ? ShopColors.TEXT_CURRENCY : ShopColors.TEXT_MUTED, false);
        if (status != null) {
            graphics.drawString(this.font, this.font.plainSubstrByWidth(status.getString(), guiW - 24),
                    guiLeft + 12, y + 11, statusSuccess ? ShopColors.STATUS_SUCCESS : ShopColors.STATUS_DANGER, false);
        }

        int buttonY = guiTop + guiH - 22;
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + 10, buttonY, 54, 16, Component.translatable("gui.futureshops.local.back"),
                ShopUiUtil.ButtonStyle.SECONDARY, !awaiting, this::onClose);
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + guiW - 112, buttonY, 102, 16,
                Component.translatable(awaiting ? "gui.futureshops.atm.processing" : "gui.futureshops.atm.withdraw"),
                ShopUiUtil.ButtonStyle.PRIMARY, !awaiting && selected > 0L && selected <= balanceMinor,
                this::withdrawSelected);
    }

    private void autoFromField() {
        long amount;
        try {
            amount = EconomyCommandUtil.parseAmountToMinorUnits(amountBox.getValue(), data.currencyDecimals());
        } catch (IllegalArgumentException ex) {
            setError("gui.futureshops.atm.result.invalid_amount");
            return;
        }
        autoSelect(amount, true);
    }

    private void autoFromBalance(int percent) {
        long positiveBalance = Math.max(0L, balanceMinor);
        long target = percent >= 100 ? positiveBalance
                : positiveBalance / 100L * percent + (positiveBalance % 100L) * percent / 100L;
        amountBox.setValue(format(target));
        autoSelect(target, false);
    }

    private void autoSelect(long target, boolean exactRequested) {
        if (target <= 0L || target > balanceMinor || data.denominations().isEmpty()) {
            setError(target > balanceMinor
                    ? "gui.futureshops.atm.result.insufficient_funds"
                    : "gui.futureshops.atm.result.invalid_amount");
            return;
        }
        long[] values = new long[data.denominations().size()];
        int[] maxStacks = new int[data.denominations().size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = data.denominations().get(i).valueMinor();
            maxStacks[i] = Math.max(1, data.denominations().get(i).maxStackSize());
        }
        CurrencyMath.BreakResult plan = CurrencyMath.breakIntoDenominations(target, values, maxStacks);
        Arrays.fill(counts, 0);
        for (CurrencyMath.Portion portion : plan.portions()) {
            counts[portion.denominationIndex()] += portion.count();
        }
        long selected = selectedTotal();
        if (selected <= 0L) {
            setError("gui.futureshops.atm.result.not_representable");
        } else if (plan.remainderMinor() > 0L) {
            statusSuccess = false;
            status = Component.translatable(exactRequested
                    ? "gui.futureshops.atm.nearest_exact"
                    : "gui.futureshops.atm.nearest_quick", format(selected));
        } else {
            statusSuccess = true;
            status = Component.translatable("gui.futureshops.atm.plan_ready", format(selected));
        }
    }

    private void adjust(int index, int delta) {
        if (index < 0 || index >= counts.length || delta == 0) return;
        int current = counts[index];
        int next = Math.max(0, Math.min(CurrencyWithdrawalService.MAX_SELECTED_ITEMS, current + delta));
        long total = selectedTotal();
        long value = data.denominations().get(index).valueMinor();
        try {
            long without = Math.subtractExact(total, Math.multiplyExact(value, (long) current));
            long affordable = Math.max(0L, (balanceMinor - without) / value);
            counts[index] = (int) Math.min(next, Math.min(affordable, CurrencyWithdrawalService.MAX_SELECTED_ITEMS));
        } catch (ArithmeticException ex) {
            setError("gui.futureshops.atm.result.invalid_plan");
            return;
        }
        status = null;
    }

    private void clearSelection() {
        Arrays.fill(counts, 0);
        amountBox.setValue("");
        status = null;
    }

    private void withdrawSelected() {
        if (selectedTotal() <= 0L || selectedTotal() > balanceMinor) return;
        awaiting = true;
        status = Component.translatable("gui.futureshops.atm.processing");
        statusSuccess = true;
        List<Integer> selectedCounts = Arrays.stream(counts).boxed().toList();
        ShopPackets.CHANNEL.sendToServer(new C2SAtmWithdrawPacket(data.currencySignature(), selectedCounts));
    }

    private long selectedTotal() {
        long total = 0L;
        try {
            for (int i = 0; i < counts.length; i++) {
                total = Math.addExact(total,
                        Math.multiplyExact(data.denominations().get(i).valueMinor(), (long) counts[i]));
            }
            return total;
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }

    private int estimatedStacks() {
        int total = 0;
        for (int i = 0; i < counts.length; i++) {
            int max = Math.max(1, data.denominations().get(i).maxStackSize());
            total += (counts[i] + max - 1) / max;
        }
        return total;
    }

    private String format(long minor) {
        return EconomyCommandUtil.formatMinorUnits(minor, data.currencyDecimals());
    }

    private void setError(String key) {
        status = Component.translatable(key);
        statusSuccess = false;
    }

    private static boolean isAmountText(String text) {
        return text.isEmpty() || text.matches("\\d*\\.?\\d*");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ShopUiUtil.dispatchClicks(clickZones, mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        listScroll = Math.max(0, listScroll - (int) delta);
        return true;
    }

    @Override
    public void onClose() {
        if (this.minecraft == null || awaiting) return;
        this.minecraft.setScreen(parent);
        // The dashboard stores a balance snapshot in final fields. Refresh it
        // after returning so an ATM withdrawal never leaves stale profile data.
        if (parent instanceof BalanceOverviewScreen) {
            ShopPackets.CHANNEL.sendToServer(new C2SOpenBalanceUiPacket());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
