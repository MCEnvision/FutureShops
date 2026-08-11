package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.command.EconomyCommandUtil;
import com.enviouse.futureshops.client.AtmWithdrawalTracker;
import com.enviouse.futureshops.client.AtmCashClaimCollectionTracker;
import com.enviouse.futureshops.client.AtmDepositTracker;
import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.AtmDenominationData;
import com.enviouse.futureshops.money.AtmCurrencyCatalog;
import com.enviouse.futureshops.money.CurrencyMath;
import com.enviouse.futureshops.money.CurrencyWithdrawalService;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SOpenBalanceUiPacket;
import com.enviouse.futureshops.network.packets.C2SAtmDepositPacket;
import com.enviouse.futureshops.network.packets.S2CAtmDataPacket;
import com.enviouse.futureshops.network.packets.S2CAtmResultPacket;
import com.enviouse.futureshops.network.packets.S2CAtmCollectCashResultPacket;
import com.enviouse.futureshops.network.packets.S2CAtmDepositResultPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Physical-currency ATM. Players may enter an amount for automatic change or
 * manually adjust every denomination before submitting the exact bill plan.
 */
public final class AtmScreen extends Screen implements ShopScreenMarker {
    private final Screen parent;
    private S2CAtmDataPacket data;
    private S2CAtmDataPacket deferredData;
    private long balanceMinor;
    private boolean balanceKnown;
    private boolean serviceAvailable;
    private String availabilityCode;
    private int[] counts;

    private int guiLeft;
    private int guiTop;
    private int guiW;
    private int guiH;
    private int listScroll;
    private int visibleRows;
    private EditBox amountBox;
    private Component status;
    private boolean statusSuccess;
    private boolean retryableResultReceived;
    private boolean cashRetryableResultReceived;
    private boolean depositRetryableResultReceived;
    private boolean depositMode;
    private C2SAtmDepositPacket.Source depositSource =
            C2SAtmDepositPacket.Source.INVENTORY;

    private final List<ShopUiUtil.ClickZone> clickZones = new ArrayList<>();

    public AtmScreen(Screen parent, S2CAtmDataPacket data) {
        super(Component.translatable("gui.futureshops.atm.title"));
        this.parent = parent;
        this.data = data;
        this.balanceKnown = data.balanceKnown();
        this.serviceAvailable = data.serviceAvailable();
        this.availabilityCode = data.availabilityCode();
        this.balanceMinor = data.balanceKnown() ? data.balanceMinor() : 0L;
        this.counts = new int[data.denominations().size()];
        restorePendingSelection();
        restorePendingDeposit();
    }

    public void applyData(S2CAtmDataPacket next) {
        serviceAvailable = next.serviceAvailable();
        availabilityCode = next.availabilityCode();
        Optional<AtmWithdrawalTracker.PendingRequest> pending =
                ShopClientPacketHandler.pendingAtmWithdrawal();
        Optional<AtmDepositTracker.PendingRequest> pendingDeposit =
                ShopClientPacketHandler.pendingAtmDeposit();
        boolean withdrawalCatalogChanged = pending.isPresent()
                && !pending.orElseThrow().currencySignature().equals(
                next.currencySignature());
        boolean depositCatalogChanged = pendingDeposit.isPresent()
                && !pendingDeposit.orElseThrow().currencySignature().equals(
                next.currencySignature());
        if (withdrawalCatalogChanged || depositCatalogChanged) {
            if (!next.balanceKnown()) {
                balanceKnown = false;
            }
            deferredData = next;
            return;
        }
        boolean changed = !next.currencySignature().equals(data.currencySignature());
        deferredData = null;
        this.data = next;
        this.balanceKnown = next.balanceKnown();
        if (next.balanceKnown()) {
            this.balanceMinor = next.balanceMinor();
        }
        if (changed || counts.length != next.denominations().size()) {
            this.counts = new int[next.denominations().size()];
            this.listScroll = 0;
            if (amountBox != null) {
                amountBox.setValue("");
            }
        }
        if (next.depositRecovery().isPresent()) {
            restorePendingDeposit();
        }
    }

    public void applyResult(
            S2CAtmResultPacket result,
            AtmWithdrawalTracker.ResultDecision decision
    ) {
        if (decision != AtmWithdrawalTracker.ResultDecision.ACCEPT_RETRYABLE
                && decision
                != AtmWithdrawalTracker.ResultDecision.ACCEPT_TERMINAL) {
            return;
        }
        if (result.balanceKnown()) {
            balanceMinor = result.balanceMinor();
            balanceKnown = true;
        }
        statusSuccess = result.success();
        status = resultMessage(result);
        retryableResultReceived = decision
                == AtmWithdrawalTracker.ResultDecision.ACCEPT_RETRYABLE;
        if (result.replayed()) {
            status = status.copy().append(Component.translatable(
                    "gui.futureshops.atm.result.replayed"));
        }
        if (decision == AtmWithdrawalTracker.ResultDecision.ACCEPT_TERMINAL
                && result.success()) {
            Arrays.fill(counts, 0);
            if (amountBox != null) {
                amountBox.setValue("");
            }
        }
        if (decision == AtmWithdrawalTracker.ResultDecision.ACCEPT_TERMINAL
                && deferredData != null) {
            S2CAtmDataPacket next = deferredData;
            deferredData = null;
            applyData(next);
        }
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(result.success()
                    ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.NOTE_BLOCK_BASS.value(),
                    0.7F, result.success() ? 1.25F : 0.7F);
        }
    }

    public void applyCashCollectionResult(
            S2CAtmCollectCashResultPacket result,
            AtmCashClaimCollectionTracker.ResultDecision decision,
            List<UUID> submittedClaimIds
    ) {
        if (decision
                != AtmCashClaimCollectionTracker.ResultDecision.ACCEPT_RETRYABLE
                && decision
                != AtmCashClaimCollectionTracker.ResultDecision.ACCEPT_TERMINAL) {
            return;
        }
        statusSuccess = result.deliveredBillCount() > 0
                && result.quarantinedClaimCount() == 0;
        status = cashCollectionMessage(result);
        cashRetryableResultReceived = decision
                == AtmCashClaimCollectionTracker.ResultDecision
                .ACCEPT_RETRYABLE;
        if (decision
                == AtmCashClaimCollectionTracker.ResultDecision
                .ACCEPT_TERMINAL) {
            data = reconcileTerminalCashClaims(
                    data, submittedClaimIds,
                    result.remainingPendingClaimCount());
        }
        if (!result.quarantinedClaimIds().isEmpty()
                && this.minecraft != null
                && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(
                    Component.translatable(
                            "gui.futureshops.atm.collect_result.recovery_handles",
                            result.requestId().toString(),
                            cashClaimRecoveryHandles(result)), false);
        }
        if (result.replayed()) {
            status = status.copy().append(Component.translatable(
                    "gui.futureshops.atm.result.replayed"));
        }
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(statusSuccess
                    ? SoundEvents.EXPERIENCE_ORB_PICKUP
                    : SoundEvents.NOTE_BLOCK_BASS.value(),
                    0.7F, statusSuccess ? 1.25F : 0.7F);
        }
    }

    public void applyDepositResult(
            S2CAtmDepositResultPacket result,
            AtmDepositTracker.ResultDecision decision
    ) {
        if (decision != AtmDepositTracker.ResultDecision.ACCEPT_RETRYABLE
                && decision
                != AtmDepositTracker.ResultDecision.ACCEPT_TERMINAL) {
            return;
        }
        if (result.balanceKnown()) {
            balanceMinor = result.resultingBalanceMinorUnits();
            balanceKnown = true;
        }
        statusSuccess = result.success();
        status = depositResultMessage(result);
        depositRetryableResultReceived = decision
                == AtmDepositTracker.ResultDecision.ACCEPT_RETRYABLE;
        if ((result.status().equals("RECOVERY_PENDING")
                || result.status().equals("MANUAL_REVIEW")
                || result.status().equals("REFUNDED"))
                && result.transactionId().isPresent()
                && this.minecraft != null
                && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(
                    Component.translatable(
                            "gui.futureshops.atm.deposit_result.recovery_handle",
                            result.transactionId().orElseThrow()
                                    .toString()), false);
        }
        if (result.replayed()) {
            status = status.copy().append(Component.translatable(
                    "gui.futureshops.atm.result.replayed"));
        }
        if (decision == AtmDepositTracker.ResultDecision.ACCEPT_TERMINAL
                && result.success() && amountBox != null) {
            amountBox.setValue("");
        }
        if (decision == AtmDepositTracker.ResultDecision.ACCEPT_TERMINAL
                && deferredData != null) {
            S2CAtmDataPacket next = deferredData;
            deferredData = null;
            applyData(next);
        }
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(result.success()
                    ? SoundEvents.EXPERIENCE_ORB_PICKUP
                    : SoundEvents.NOTE_BLOCK_BASS.value(),
                    0.7F, result.success() ? 1.25F : 0.7F);
        }
    }

    @Override
    protected void init() {
        guiW = Math.min(520, Math.max(340, this.width - 8));
        guiH = Math.min(350, Math.max(240, this.height - 8));
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;
        int listTop = guiTop + 156;
        int listBottom = guiTop + guiH - 50;
        visibleRows = Math.max(1, (listBottom - listTop) / 22);

        String previous = amountBox == null ? "" : amountBox.getValue();
        amountBox = new EditBox(this.font, guiLeft + 82, guiTop + 112, 82, 16,
                Component.translatable("gui.futureshops.atm.amount"));
        amountBox.setMaxLength(20);
        amountBox.setFilter(AtmScreen::isAmountText);
        amountBox.setValue(previous);
        amountBox.setHint(Component.translatable("gui.futureshops.atm.amount_hint"));
        addRenderableWidget(amountBox);
        ShopClientPacketHandler.pendingAtmWithdrawal().ifPresent(pending ->
                amountBox.setValue(format(pending.amountMinor())));
        ShopClientPacketHandler.pendingAtmDeposit().ifPresent(pending ->
                amountBox.setValue(pending.requestedMinorUnits().isPresent()
                        ? format(pending.requestedMinorUnits().getAsLong())
                        : ""));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updatePendingStatus();
        if (amountBox != null) {
            amountBox.setEditable(depositMode
                    ? depositSelectionEnabled() : selectionEnabled());
        }
        clickZones.clear();
        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        ShopUiUtil.renderShellWindow(graphics, guiLeft, guiTop, guiW, guiH);
        graphics.fill(guiLeft + 2, guiTop, guiLeft + guiW - 2, guiTop + 2, ShopColors.ACCENT_CURRENCY);

        renderHeader(graphics);
        renderSecurity(graphics);
        renderModeTabs(graphics, mouseX, mouseY);
        renderAmountControls(graphics, mouseX, mouseY);
        if (depositMode) {
            renderDepositPanel(graphics);
        } else {
            renderDenominations(graphics, mouseX, mouseY);
        }
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
        String balance = balanceKnown
                ? Component.translatable("gui.futureshops.atm.balance",
                format(balanceMinor), data.currencyName()).getString()
                : Component.translatable(
                "gui.futureshops.atm.balance_unavailable").getString();
        graphics.drawString(this.font, balance, x + w - 10 - this.font.width(balance),
                y + 15, ShopColors.TEXT_CURRENCY, false);
    }

    private void renderSecurity(GuiGraphics graphics) {
        int x = guiLeft + 10;
        int y = guiTop + 56;
        int w = guiW - 20;
        int accent = !serviceAvailable
                ? ShopColors.STATUS_DANGER
                : data.protectedMinting()
                ? ShopColors.STATUS_SUCCESS
                : ShopColors.STATUS_DANGER;
        ShopUiUtil.renderPanel(graphics, x, y, w, 32, ShopColors.SURFACE_RAISED, accent);
        graphics.fill(x, y, x + 3, y + 32, accent);
        Component heading = serviceAvailable
                ? Component.translatable(data.protectedMinting()
                ? "gui.futureshops.atm.security.protected"
                : "gui.futureshops.atm.security.unprotected")
                : Component.translatable(
                "gui.futureshops.atm.availability.title");
        graphics.drawString(this.font, heading,
                x + 9, y + 6, accent, false);
        String detail = serviceAvailable
                ? Component.translatable(data.protectedMinting()
                ? "gui.futureshops.atm.security.protected_detail"
                : "gui.futureshops.atm.security.unprotected_detail")
                .getString()
                : Component.translatable(availabilityKey(
                availabilityCode)).getString();
        graphics.drawString(this.font, this.font.plainSubstrByWidth(detail, w - 18),
                x + 9, y + 18, ShopColors.TEXT_MUTED, false);
    }

    private void renderModeTabs(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        int y = guiTop + 92;
        boolean enabled = modeNavigationEnabled(
                ShopClientPacketHandler.atmWithdrawalState(),
                ShopClientPacketHandler.atmCashCollectionState(),
                ShopClientPacketHandler.atmDepositState(),
                depositRecoveryHandle().isPresent());
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + 10, y, 72, 16,
                Component.translatable(
                        "gui.futureshops.atm.mode.withdraw"),
                depositMode ? ShopUiUtil.ButtonStyle.SECONDARY
                        : ShopUiUtil.ButtonStyle.PRIMARY,
                enabled, () -> setDepositMode(false));
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + 84, y, 72, 16,
                Component.translatable(
                        "gui.futureshops.atm.mode.deposit"),
                depositMode ? ShopUiUtil.ButtonStyle.PRIMARY
                        : ShopUiUtil.ButtonStyle.SECONDARY,
                enabled, () -> setDepositMode(true));
    }

    private void renderAmountControls(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = guiTop + 112;
        graphics.drawString(this.font, Component.translatable(depositMode
                        ? "gui.futureshops.atm.deposit_amount"
                        : "gui.futureshops.atm.amount"),
                guiLeft + 12, y + 4, ShopColors.TEXT_MUTED, false);
        int bx = guiLeft + 168;
        if (depositMode) {
            ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                    bx, y, 124, 16,
                    Component.translatable(depositSourceKey(depositSource)),
                    ShopUiUtil.ButtonStyle.SECONDARY,
                    depositSelectionEnabled(), this::cycleDepositSource);
            ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                    bx + 126, y, 40, 16,
                    Component.translatable("gui.futureshops.atm.deposit_all"),
                    ShopUiUtil.ButtonStyle.SECONDARY,
                    depositSelectionEnabled(), () -> amountBox.setValue(""));
            return;
        }
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                bx, y, 40, 16, Component.translatable("gui.futureshops.atm.auto"),
                ShopUiUtil.ButtonStyle.PRIMARY, selectionEnabled(), this::autoFromField);
        bx += 42;
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                bx, y, 36, 16, Component.literal("25%"),
                ShopUiUtil.ButtonStyle.SECONDARY, selectionEnabled(),
                () -> autoFromBalance(25));
        bx += 38;
        if (guiW >= 420) {
            ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                    bx, y, 36, 16, Component.literal("50%"),
                    ShopUiUtil.ButtonStyle.SECONDARY, selectionEnabled(),
                    () -> autoFromBalance(50));
            bx += 38;
        }
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                bx, y, 36, 16, Component.translatable("gui.futureshops.atm.max"),
                ShopUiUtil.ButtonStyle.SECONDARY, selectionEnabled(),
                () -> autoFromBalance(100));
        bx += 38;
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                bx, y, 42, 16, Component.translatable("gui.futureshops.atm.clear"),
                ShopUiUtil.ButtonStyle.SECONDARY, selectionEnabled(),
                this::clearSelection);
    }

    private void renderDenominations(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = guiLeft + 10;
        int y = guiTop + 136;
        int w = guiW - 20;
        int h = guiH - 186;
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
                    selectionEnabled() && counts[i] > 0,
                    () -> adjust(index, hasShiftDown() ? -10 : -1));
            ShopUiUtil.zone(clickZones, stepper.plusX(), rowY + 3, stepper.btn(), stepper.btn(),
                    selectionEnabled(),
                    () -> adjust(index, hasShiftDown() ? 10 : 1));
        }
        ShopUiUtil.renderScrollIndicators(graphics, this.font, x, listY, w,
                Math.max(1, visibleRows) * 22, listScroll, visibleRows, denominations.size());
    }

    private void renderDepositPanel(GuiGraphics graphics) {
        int x = guiLeft + 10;
        int y = guiTop + 136;
        int w = guiW - 20;
        int h = guiH - 186;
        ShopUiUtil.renderCard(graphics, x, y, w, h);
        int accent = data.protectedMinting()
                ? ShopColors.STATUS_SUCCESS : ShopColors.STATUS_DANGER;
        graphics.fill(x, y, x + w, y + 2, accent);
        graphics.drawString(this.font,
                Component.translatable("gui.futureshops.atm.deposit_source",
                        Component.translatable(
                                depositSourceKey(depositSource))),
                x + 8, y + 5, ShopColors.TEXT_STRONG, false);
        int textY = drawWrappedDepositText(graphics,
                Component.translatable(
                        depositSourceExactKey(depositSource)),
                x + 8, y + 18, w - 16, ShopColors.TEXT_MUTED,
                y + h - 6, 3);
        textY += 2;
        textY = drawWrappedDepositText(graphics,
                Component.translatable(data.protectedMinting()
                        ? "gui.futureshops.atm.deposit_protected"
                        : "gui.futureshops.atm.deposit_foreign"),
                x + 8, textY, w - 16, accent,
                y + h - 6, 3);
        if (data.depositRecovery().isPresent()) {
            S2CAtmDataPacket.DepositRecoverySummary recovery =
                    data.depositRecovery().orElseThrow();
            textY = drawWrappedDepositText(graphics,
                    Component.translatable(
                            "gui.futureshops.atm.deposit_recovery."
                                    + recovery.status()
                                    .toLowerCase(Locale.ROOT),
                            format(recovery.amountMinorUnits())),
                    x + 8, textY + 2, w - 16,
                    recovery.status().equals("RECOVERY_PENDING")
                            ? ShopColors.TEXT_CURRENCY
                            : ShopColors.STATUS_DANGER,
                    y + h - 6, 3);
            drawWrappedDepositText(graphics,
                    Component.literal(recovery.transactionId().toString()),
                    x + 8, textY, w - 16, ShopColors.TEXT_MUTED,
                    y + h - 6, 3);
        }
        if (this.minecraft != null && this.minecraft.player != null
                && this.minecraft.player.getAbilities().instabuild) {
            drawWrappedDepositText(graphics, Component.translatable(
                            "gui.futureshops.atm.deposit_creative_blocked"),
                    x + 8, textY + 2, w - 16,
                    ShopColors.STATUS_DANGER, y + h - 6, 3);
        }
    }

    private int drawWrappedDepositText(
            GuiGraphics graphics,
            Component text,
            int x,
            int y,
            int width,
            int color,
            int bottom,
            int maximumLines
    ) {
        int drawn = 0;
        for (var line : this.font.split(text, Math.max(1, width))) {
            if (drawn >= maximumLines || y + 8 > bottom) {
                break;
            }
            graphics.drawString(this.font, line, x, y, color, false);
            y += 10;
            drawn++;
        }
        return y;
    }

    private void renderFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = guiTop + guiH - 42;
        Optional<AtmWithdrawalTracker.PendingRequest> pending =
                ShopClientPacketHandler.pendingAtmWithdrawal();
        long selected = pending.map(
                AtmWithdrawalTracker.PendingRequest::amountMinor)
                .orElseGet(this::selectedTotal);
        int billCount = pending.map(value -> value.denominationCounts()
                        .stream().mapToInt(Integer::intValue).sum())
                .orElseGet(() -> Arrays.stream(counts).sum());
        int stacks = pending.map(this::estimatedPendingStacks)
                .orElseGet(this::estimatedStacks);
        String summary = depositMode
                ? depositSummary()
                : Component.translatable("gui.futureshops.atm.selected",
                format(selected), billCount, stacks).getString();
        graphics.drawString(this.font, this.font.plainSubstrByWidth(summary, guiW - 160),
                guiLeft + 12, y, depositMode || selected > 0
                        ? ShopColors.TEXT_CURRENCY
                        : ShopColors.TEXT_MUTED, false);
        if (status != null) {
            graphics.drawString(this.font, this.font.plainSubstrByWidth(status.getString(), guiW - 24),
                    guiLeft + 12, y + 11, statusSuccess ? ShopColors.STATUS_SUCCESS : ShopColors.STATUS_DANGER, false);
        }

        int buttonY = guiTop + guiH - 22;
        AtmWithdrawalTracker.PendingState pendingState =
                ShopClientPacketHandler.atmWithdrawalState();
        AtmCashClaimCollectionTracker.PendingState cashState =
                ShopClientPacketHandler.atmCashCollectionState();
        AtmDepositTracker.PendingState depositState =
                ShopClientPacketHandler.atmDepositState();
        Optional<UUID> recoveryHandle = depositRecoveryHandle();
        boolean recoveryActive = recoveryHandle.isPresent();
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + 10, buttonY, 54, 16, Component.translatable("gui.futureshops.local.back"),
                ShopUiUtil.ButtonStyle.SECONDARY,
                pendingState != AtmWithdrawalTracker.PendingState.AWAITING
                        && cashState
                        != AtmCashClaimCollectionTracker.PendingState.AWAITING
                        && (depositState
                        != AtmDepositTracker.PendingState.AWAITING
                        || recoveryActive),
                this::onClose);
        boolean cashRetry = cashState
                == AtmCashClaimCollectionTracker.PendingState.RETRYABLE;
        Component cashAction = Component.translatable(cashRetry
                ? "gui.futureshops.atm.collect_retry"
                : cashState
                == AtmCashClaimCollectionTracker.PendingState.AWAITING
                ? "gui.futureshops.atm.collecting"
                : "gui.futureshops.atm.collect_cash");
        if (!cashRetry && cashState
                == AtmCashClaimCollectionTracker.PendingState.NONE) {
            cashAction = Component.translatable(
                    "gui.futureshops.atm.collect_cash",
                    data.pendingCashClaimCount());
        }
        boolean cashEnabled = cashCollectionEnabled(
                pendingState, cashState, depositState,
                recoveryActive,
                serviceAvailable,
                !data.collectibleCashClaims().isEmpty());
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + 68, buttonY, 118, 16, cashAction,
                ShopUiUtil.ButtonStyle.SECONDARY, cashEnabled,
                cashRetry ? this::retryCashCollection : this::collectCash);
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + 188, buttonY, 38, 16,
                Component.translatable("gui.futureshops.atm.copy"),
                ShopUiUtil.ButtonStyle.SECONDARY,
                recoveryHandle.isPresent(), this::copyDepositRecoveryHandle);
        boolean depositRecovery =
                depositState == AtmDepositTracker.PendingState.RETRYABLE;
        boolean retry = depositRecovery || !depositMode
                && pendingState
                == AtmWithdrawalTracker.PendingState.RETRYABLE;
        boolean depositAwaiting =
                depositState == AtmDepositTracker.PendingState.AWAITING;
        boolean awaiting = depositAwaiting || !depositMode
                && pendingState == AtmWithdrawalTracker.PendingState.AWAITING;
        Component action = Component.translatable(retry
                ? depositRecovery
                ? recoveryHandle.isPresent()
                ? "gui.futureshops.atm.deposit_recovery_check"
                : "gui.futureshops.atm.deposit_retry"
                : "gui.futureshops.atm.retry"
                : awaiting
                ? depositAwaiting
                ? "gui.futureshops.atm.depositing"
                : "gui.futureshops.atm.processing"
                : depositMode
                ? "gui.futureshops.atm.deposit"
                : "gui.futureshops.atm.withdraw");
        boolean actionEnabled = depositRecovery
                ? serviceAvailable
                && pendingState == AtmWithdrawalTracker.PendingState.NONE
                && cashState
                != AtmCashClaimCollectionTracker.PendingState.AWAITING
                : depositMode
                ? retry ? serviceAvailable
                : depositState == AtmDepositTracker.PendingState.NONE
                && pendingState == AtmWithdrawalTracker.PendingState.NONE
                && cashState
                == AtmCashClaimCollectionTracker.PendingState.NONE
                && serviceAvailable && validDepositAmountField()
                && !creativeDepositBlocked()
                : retry
                ? serviceAvailable
                : pendingState == AtmWithdrawalTracker.PendingState.NONE
                && cashState
                == AtmCashClaimCollectionTracker.PendingState.NONE
                && depositState == AtmDepositTracker.PendingState.NONE
                && serviceAvailable
                && balanceKnown
                && selected > 0L
                && selected <= balanceMinor
                && stacks <= AtmCurrencyCatalog.MAXIMUM_CLAIM_STACKS;
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + guiW - 112, buttonY, 102, 16,
                action, ShopUiUtil.ButtonStyle.PRIMARY, actionEnabled,
                retry
                        ? depositRecovery ? this::retryDeposit
                        : this::retryWithdrawal
                        : depositMode ? this::submitDeposit
                        : this::withdrawSelected);
    }

    private void autoFromField() {
        if (!selectionEnabled()) {
            return;
        }
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
        if (!selectionEnabled()) {
            return;
        }
        long positiveBalance = Math.max(0L, balanceMinor);
        long target = percent >= 100 ? positiveBalance
                : positiveBalance / 100L * percent + (positiveBalance % 100L) * percent / 100L;
        amountBox.setValue(format(target));
        autoSelect(target, false);
    }

    private void autoSelect(long target, boolean exactRequested) {
        if (!selectionEnabled()) {
            return;
        }
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
        CurrencyMath.BreakResult plan = CurrencyMath.breakIntoDenominations(
                target, values, maxStacks,
                CurrencyWithdrawalService.MAX_SELECTED_ITEMS);
        Arrays.fill(counts, 0);
        if (plan.limitExceeded()) {
            statusSuccess = false;
            status = Component.translatable(
                    "gui.futureshops.atm.bill_limit",
                    CurrencyWithdrawalService.MAX_SELECTED_ITEMS);
            return;
        }
        int remainingBills = CurrencyWithdrawalService.MAX_SELECTED_ITEMS;
        boolean limited = false;
        for (CurrencyMath.Portion portion : plan.portions()) {
            int accepted = Math.min(remainingBills, portion.count());
            counts[portion.denominationIndex()] += accepted;
            remainingBills -= accepted;
            if (accepted < portion.count()) {
                limited = true;
                break;
            }
        }
        long selected = selectedTotal();
        if (selected <= 0L) {
            setError("gui.futureshops.atm.result.not_representable");
        } else if (limited) {
            statusSuccess = false;
            status = Component.translatable(
                    "gui.futureshops.atm.bill_limit",
                    CurrencyWithdrawalService.MAX_SELECTED_ITEMS);
        } else if (plan.remainderMinor() > 0L) {
            statusSuccess = false;
            status = Component.translatable(exactRequested
                    ? "gui.futureshops.atm.nearest_exact"
                    : "gui.futureshops.atm.nearest_quick", format(selected));
        } else {
            statusSuccess = true;
            status = Component.translatable("gui.futureshops.atm.plan_ready", format(selected));
        }
        if (estimatedStacks()
                > AtmCurrencyCatalog.MAXIMUM_CLAIM_STACKS) {
            statusSuccess = false;
            status = Component.translatable(
                    "gui.futureshops.atm.stack_limit",
                    AtmCurrencyCatalog.MAXIMUM_CLAIM_STACKS);
        }
    }

    private void adjust(int index, int delta) {
        if (!selectionEnabled()
                || index < 0 || index >= counts.length || delta == 0) {
            return;
        }
        int current = counts[index];
        int otherBills = Math.max(0, Arrays.stream(counts).sum() - current);
        int availableBills = Math.max(0,
                CurrencyWithdrawalService.MAX_SELECTED_ITEMS - otherBills);
        int next = Math.max(0, Math.min(availableBills, current + delta));
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
        if (estimatedStacks() > AtmCurrencyCatalog.MAXIMUM_CLAIM_STACKS) {
            statusSuccess = false;
            status = Component.translatable(
                    "gui.futureshops.atm.stack_limit",
                    AtmCurrencyCatalog.MAXIMUM_CLAIM_STACKS);
        }
    }

    private void clearSelection() {
        if (!selectionEnabled()) {
            return;
        }
        Arrays.fill(counts, 0);
        amountBox.setValue("");
        status = null;
    }

    private void withdrawSelected() {
        long selected = selectedTotal();
        if (!selectionEnabled()
                || selected <= 0L || selected > balanceMinor
                || estimatedStacks()
                > AtmCurrencyCatalog.MAXIMUM_CLAIM_STACKS) {
            return;
        }
        status = Component.translatable("gui.futureshops.atm.processing");
        statusSuccess = true;
        retryableResultReceived = false;
        List<Integer> selectedCounts = Arrays.stream(counts).boxed().toList();
        if (ShopClientPacketHandler.submitAtmWithdrawal(
                data.currencySignature(), selectedCounts, selected)
                .isEmpty()) {
            setError("gui.futureshops.atm.request_rejected");
        }
    }

    private void retryWithdrawal() {
        if (!serviceAvailable
                || ShopClientPacketHandler.atmWithdrawalState()
                != AtmWithdrawalTracker.PendingState.RETRYABLE
                || ShopClientPacketHandler.atmDepositState()
                != AtmDepositTracker.PendingState.NONE) {
            return;
        }
        if (ShopClientPacketHandler.retryAtmWithdrawal().isEmpty()) {
            setError("gui.futureshops.atm.request_rejected");
            return;
        }
        retryableResultReceived = false;
        statusSuccess = true;
        status = Component.translatable("gui.futureshops.atm.processing");
    }

    private void collectCash() {
        if (!cashCollectionEnabled(
                ShopClientPacketHandler.atmWithdrawalState(),
                ShopClientPacketHandler.atmCashCollectionState(),
                ShopClientPacketHandler.atmDepositState(),
                depositRecoveryHandle().isPresent(),
                serviceAvailable,
                !data.collectibleCashClaims().isEmpty())) {
            return;
        }
        List<java.util.UUID> claimIds = data.collectibleCashClaims().stream()
                .map(S2CAtmDataPacket.CashClaimSummary::claimId)
                .toList();
        if (ShopClientPacketHandler.submitAtmCashCollection(claimIds)
                .isEmpty()) {
            setError("gui.futureshops.atm.collect_result.stale");
            return;
        }
        cashRetryableResultReceived = false;
        statusSuccess = true;
        status = Component.translatable(
                "gui.futureshops.atm.collecting");
    }

    private void retryCashCollection() {
        if (!serviceAvailable
                || ShopClientPacketHandler.atmCashCollectionState()
                != AtmCashClaimCollectionTracker.PendingState.RETRYABLE
                || ShopClientPacketHandler.atmDepositState()
                == AtmDepositTracker.PendingState.AWAITING) {
            return;
        }
        if (ShopClientPacketHandler.retryAtmCashCollection().isEmpty()) {
            setError("gui.futureshops.atm.collect_result.stale");
            return;
        }
        cashRetryableResultReceived = false;
        statusSuccess = true;
        status = Component.translatable(
                "gui.futureshops.atm.collecting");
    }

    private void submitDeposit() {
        if (!depositMode || !depositSelectionEnabled()
                || !validDepositAmountField() || creativeDepositBlocked()) {
            return;
        }
        OptionalLong amount;
        if (amountBox.getValue().isBlank()) {
            amount = OptionalLong.empty();
        } else {
            try {
                amount = OptionalLong.of(
                        EconomyCommandUtil.parseAmountToMinorUnits(
                                amountBox.getValue(),
                                data.currencyDecimals()));
            } catch (IllegalArgumentException exception) {
                setError("gui.futureshops.atm.result.invalid_amount");
                return;
            }
        }
        if (ShopClientPacketHandler.submitAtmDeposit(
                data.currencySignature(), depositSource, amount).isEmpty()) {
            setError("gui.futureshops.atm.request_rejected");
            return;
        }
        depositRetryableResultReceived = false;
        statusSuccess = true;
        status = Component.translatable(
                "gui.futureshops.atm.depositing");
    }

    private void retryDeposit() {
        if (!serviceAvailable
                || ShopClientPacketHandler.atmDepositState()
                != AtmDepositTracker.PendingState.RETRYABLE
                || ShopClientPacketHandler.atmWithdrawalState()
                != AtmWithdrawalTracker.PendingState.NONE
                || ShopClientPacketHandler.atmCashCollectionState()
                != AtmCashClaimCollectionTracker.PendingState.NONE) {
            return;
        }
        if (ShopClientPacketHandler.retryAtmDeposit().isEmpty()) {
            setError("gui.futureshops.atm.request_rejected");
            return;
        }
        depositRetryableResultReceived = false;
        statusSuccess = true;
        status = Component.translatable(depositRecoveryHandle().isPresent()
                ? "gui.futureshops.atm.deposit_recovery_checking"
                : "gui.futureshops.atm.depositing");
    }

    private void setDepositMode(boolean value) {
        if (!modeNavigationEnabled(
                ShopClientPacketHandler.atmWithdrawalState(),
                ShopClientPacketHandler.atmCashCollectionState(),
                ShopClientPacketHandler.atmDepositState(),
                depositRecoveryHandle().isPresent())
                || depositMode == value) {
            return;
        }
        depositMode = value;
        status = null;
        if (amountBox != null) {
            amountBox.setValue("");
        }
    }

    private void cycleDepositSource() {
        if (!depositSelectionEnabled()) {
            return;
        }
        depositSource = switch (depositSource) {
            case INVENTORY -> C2SAtmDepositPacket.Source.MAIN_HAND;
            case MAIN_HAND -> C2SAtmDepositPacket.Source.OFF_HAND;
            case OFF_HAND -> C2SAtmDepositPacket.Source.INVENTORY;
        };
    }

    private void restorePendingSelection() {
        ShopClientPacketHandler.pendingAtmWithdrawal().ifPresent(pending -> {
            if (pending.currencySignature().equals(data.currencySignature())
                    && pending.denominationCounts().size() == counts.length) {
                for (int index = 0; index < counts.length; index++) {
                    counts[index] = pending.denominationCounts().get(index);
                }
            }
            Optional<S2CAtmResultPacket> retryable =
                    ShopClientPacketHandler.lastRetryableAtmResult()
                    .filter(result -> result.requestId().equals(
                            pending.requestId()));
            if (retryable.isPresent()) {
                S2CAtmResultPacket result = retryable.orElseThrow();
                retryableResultReceived = true;
                statusSuccess = false;
                status = resultMessage(result);
                if (result.replayed()) {
                    status = status.copy().append(Component.translatable(
                            "gui.futureshops.atm.result.replayed"));
                }
            } else {
                statusSuccess = true;
                status = Component.translatable(
                        ShopClientPacketHandler.atmWithdrawalState()
                        == AtmWithdrawalTracker.PendingState.RETRYABLE
                        ? "gui.futureshops.atm.timeout"
                        : "gui.futureshops.atm.processing");
            }
        });
    }

    private void restorePendingDeposit() {
        ShopClientPacketHandler.pendingAtmDeposit().ifPresent(pending -> {
            depositMode = pending.recoveryTransactionId().isEmpty()
                    || data.collectibleCashClaims().isEmpty();
            depositSource = pending.source();
            if (amountBox != null) {
                amountBox.setValue(pending.requestedMinorUnits().isPresent()
                        ? format(pending.requestedMinorUnits().getAsLong())
                        : "");
            }
            Optional<S2CAtmDepositResultPacket> retryable =
                    ShopClientPacketHandler.lastRetryableAtmDepositResult()
                            .filter(result -> result.requestId().equals(
                                    pending.requestId()));
            if (retryable.isPresent()) {
                depositRetryableResultReceived = true;
                statusSuccess = false;
                status = depositResultMessage(retryable.orElseThrow());
            } else {
                statusSuccess = true;
                status = pending.recoveryTransactionId().isPresent()
                        ? Component.translatable(
                        "gui.futureshops.atm.deposit_result.recovery_pending",
                        pending.recoveryTransactionId().orElseThrow()
                                .toString())
                        : Component.translatable(
                        ShopClientPacketHandler.atmDepositState()
                        == AtmDepositTracker.PendingState.RETRYABLE
                                ? "gui.futureshops.atm.deposit_timeout"
                                : "gui.futureshops.atm.depositing");
            }
        });
        if (ShopClientPacketHandler.pendingAtmDeposit().isEmpty()
                || data.depositRecovery().filter(recovery ->
                !recovery.status().equals("RECOVERY_PENDING")).isPresent()) {
            data.depositRecovery().ifPresent(recovery -> {
                statusSuccess = recovery.status().equals("COMPLETED")
                        || recovery.status().equals("REFUNDED");
                status = Component.translatable(
                        "gui.futureshops.atm.deposit_recovery."
                                + recovery.status().toLowerCase(Locale.ROOT),
                        format(recovery.amountMinorUnits())).copy()
                        .append(Component.literal(" "
                                + recovery.transactionId()));
            });
        }
    }

    private void updatePendingStatus() {
        if (ShopClientPacketHandler.atmWithdrawalState()
                == AtmWithdrawalTracker.PendingState.RETRYABLE
                && !retryableResultReceived) {
            statusSuccess = false;
            status = Component.translatable("gui.futureshops.atm.timeout");
        }
        if (ShopClientPacketHandler.atmCashCollectionState()
                == AtmCashClaimCollectionTracker.PendingState.RETRYABLE
                && !cashRetryableResultReceived) {
            statusSuccess = false;
            status = Component.translatable(
                    "gui.futureshops.atm.collect_timeout");
        }
        if (ShopClientPacketHandler.atmDepositState()
                == AtmDepositTracker.PendingState.RETRYABLE
                && !depositRetryableResultReceived) {
            statusSuccess = false;
            status = depositRecoveryHandle()
                    .map(handle -> Component.translatable(
                            "gui.futureshops.atm.deposit_result.recovery_pending",
                            handle.toString()))
                    .orElseGet(() -> Component.translatable(
                            "gui.futureshops.atm.deposit_timeout"));
        }
    }

    private boolean selectionEnabled() {
        return serviceAvailable
                && !depositMode
                && balanceKnown
                && ShopClientPacketHandler.atmWithdrawalState()
                == AtmWithdrawalTracker.PendingState.NONE
                && ShopClientPacketHandler.atmCashCollectionState()
                == AtmCashClaimCollectionTracker.PendingState.NONE
                && ShopClientPacketHandler.atmDepositState()
                == AtmDepositTracker.PendingState.NONE;
    }

    private boolean depositSelectionEnabled() {
        return depositMode && serviceAvailable
                && ShopClientPacketHandler.atmWithdrawalState()
                == AtmWithdrawalTracker.PendingState.NONE
                && ShopClientPacketHandler.atmCashCollectionState()
                == AtmCashClaimCollectionTracker.PendingState.NONE
                && ShopClientPacketHandler.atmDepositState()
                == AtmDepositTracker.PendingState.NONE;
    }

    private boolean noPendingRequest() {
        return ShopClientPacketHandler.atmWithdrawalState()
                == AtmWithdrawalTracker.PendingState.NONE
                && ShopClientPacketHandler.atmCashCollectionState()
                == AtmCashClaimCollectionTracker.PendingState.NONE
                && ShopClientPacketHandler.atmDepositState()
                == AtmDepositTracker.PendingState.NONE;
    }

    private Optional<UUID> depositRecoveryHandle() {
        return ShopClientPacketHandler.pendingAtmDeposit()
                .flatMap(AtmDepositTracker.PendingRequest
                        ::recoveryTransactionId)
                .or(() -> data.depositRecovery().map(
                        S2CAtmDataPacket.DepositRecoverySummary
                                ::transactionId));
    }

    private void copyDepositRecoveryHandle() {
        Optional<UUID> handle = depositRecoveryHandle();
        if (handle.isEmpty() || this.minecraft == null) {
            return;
        }
        this.minecraft.keyboardHandler.setClipboard(
                handle.orElseThrow().toString());
        statusSuccess = true;
        status = Component.translatable(
                "gui.futureshops.atm.deposit_recovery_copied");
    }

    static boolean cashCollectionEnabled(
            AtmWithdrawalTracker.PendingState withdrawalState,
            AtmCashClaimCollectionTracker.PendingState cashState,
            AtmDepositTracker.PendingState depositState,
            boolean depositRecovery,
            boolean serviceAvailable,
            boolean hasCollectibleClaims
    ) {
        boolean retry = cashState
                == AtmCashClaimCollectionTracker.PendingState.RETRYABLE;
        return withdrawalState == AtmWithdrawalTracker.PendingState.NONE
                && (depositState
                != AtmDepositTracker.PendingState.AWAITING
                || depositRecovery)
                && serviceAvailable
                && (retry || cashState
                == AtmCashClaimCollectionTracker.PendingState.NONE
                && hasCollectibleClaims);
    }

    static boolean modeNavigationEnabled(
            AtmWithdrawalTracker.PendingState withdrawalState,
            AtmCashClaimCollectionTracker.PendingState cashState,
            AtmDepositTracker.PendingState depositState,
            boolean depositRecovery
    ) {
        return withdrawalState
                != AtmWithdrawalTracker.PendingState.AWAITING
                && cashState
                != AtmCashClaimCollectionTracker.PendingState.AWAITING
                && (depositState
                != AtmDepositTracker.PendingState.AWAITING
                || depositRecovery);
    }

    private Component resultMessage(S2CAtmResultPacket result) {
        String key = resultKey(result.status());
        return switch (result.status()) {
            case "DELIVERED" -> Component.translatable(
                    key, format(result.amountMinor()),
                    result.deliveredBillCount());
            case "CLAIMED" -> Component.translatable(
                    key, format(result.amountMinor()),
                    result.claimedBillCount());
            case "PARTIALLY_DELIVERED" -> Component.translatable(
                    key, format(result.amountMinor()),
                    result.deliveredBillCount(),
                    result.claimedBillCount());
            case "RECOVERY_PENDING" -> Component.translatable(
                    key, result.claimedBillCount());
            case "MANUAL_REVIEW" -> Component.translatable(
                    key, result.requestId().toString());
            case "RATE_LIMITED" -> Component.translatable(
                    key, retrySeconds(result.retryAfterMillis()));
            default -> Component.translatable(key);
        };
    }

    private Component cashCollectionMessage(
            S2CAtmCollectCashResultPacket result
    ) {
        String key = cashCollectionResultKey(result.status());
        return switch (result.status()) {
            case "DELIVERED" ->
                    Component.translatable(key, result.deliveredBillCount(),
                            result.remainingPendingClaimCount(),
                            result.quarantinedClaimCount());
            case "PARTIALLY_DELIVERED" ->
                    result.quarantinedClaimIds().isEmpty()
                            ? Component.translatable(
                            "gui.futureshops.atm.collect_result.partially_delivered_pending",
                            result.deliveredBillCount(),
                            result.remainingPendingClaimCount())
                            : Component.translatable(key,
                            result.deliveredBillCount(),
                            result.remainingPendingClaimCount(),
                            result.quarantinedClaimCount(),
                            cashClaimRecoveryHandles(result));
            case "MANUAL_REVIEW" -> Component.translatable(key,
                    result.requestId().toString(),
                    cashClaimRecoveryHandles(result),
                    result.remainingPendingClaimCount());
            case "RATE_LIMITED" -> Component.translatable(key,
                    retrySeconds(result.retryAfterMillis()),
                    result.remainingPendingClaimCount());
            default -> Component.translatable(key,
                    result.remainingPendingClaimCount());
        };
    }

    private Component depositResultMessage(
            S2CAtmDepositResultPacket result
    ) {
        String key = depositResultKey(result.status());
        if (result.success()) {
            Component message = Component.translatable(key,
                    format(result.depositedMinorUnits()),
                    result.itemsConsumed(),
                    format(result.resultingBalanceMinorUnits()));
            if (result.overflowClaimMinorUnits() > 0L) {
                message = message.copy().append(Component.translatable(
                        "gui.futureshops.atm.deposit_result.overflow_claim",
                        format(result.overflowClaimMinorUnits())));
            }
            if (result.cleanupPending()) {
                message = message.copy().append(Component.translatable(
                        "gui.futureshops.atm.deposit_result.cleanup_pending"));
            }
            return message;
        }
        if (result.status().equals("RATE_LIMITED")) {
            return Component.translatable(key,
                    retrySeconds(result.retryAfterMillis()));
        }
        if (result.status().equals("TOO_MANY_ITEMS")) {
            return Component.translatable(key,
                    S2CAtmDepositResultPacket.MAX_ITEMS_CONSUMED);
        }
        if (result.status().equals("LEGACY_MIGRATION_REQUIRED")) {
            S2CAtmDepositResultPacket.LegacyMigrationSummary legacy =
                    result.legacyMigration().orElseThrow();
            return Component.translatable(key,
                    format(legacy.availableMinorUnits()),
                    legacy.billCount(), legacy.entryCount());
        }
        if (result.status().equals("RECOVERY_REQUIRED")
                || result.status().equals("RECOVERY_PENDING")
                || result.status().equals("MANUAL_REVIEW")) {
            return Component.translatable(key,
                    result.transactionId()
                            .orElse(result.requestId()).toString());
        }
        if (result.status().equals("REFUNDED")) {
            return Component.translatable(
                    key, format(result.returnedMinorUnits()),
                    result.transactionId()
                            .orElse(result.requestId()).toString());
        }
        if (result.status().equals("CONFIG_CHANGED")) {
            return Component.translatable(key);
        }
        if (result.status().equals("REQUEST_CONFLICT")) {
            return Component.translatable(key,
                    result.transactionId().orElseThrow().toString());
        }
        if (result.status().equals("CANCELLED")) {
            return Component.translatable(key,
                    result.transactionId().orElseThrow().toString());
        }
        return Component.translatable(key);
    }

    static String resultKey(String statusCode) {
        return switch (statusCode) {
            case "DELIVERED", "CLAIMED", "PARTIALLY_DELIVERED",
                    "INVALID_AMOUNT", "INVALID_PLAN", "CURRENCY_CHANGED",
                    "INSUFFICIENT_FUNDS", "CANCELLED", "CONFLICT",
                    "RATE_LIMITED",
                    "MIGRATION_PENDING", "ESCROW_UNAVAILABLE",
                    "RECOVERY_PENDING", "MANUAL_REVIEW",
                    "SERVER_ERROR" ->
                    "gui.futureshops.atm.result."
                            + statusCode.toLowerCase(Locale.ROOT);
            default -> "gui.futureshops.atm.result.server_error";
        };
    }

    static String cashCollectionResultKey(String statusCode) {
        return switch (statusCode) {
            case "DELIVERED", "PARTIALLY_DELIVERED", "MANUAL_REVIEW",
                    "RATE_LIMITED", "RETRYABLE", "CONFLICT",
                    "UNAVAILABLE" ->
                    "gui.futureshops.atm.collect_result."
                            + statusCode.toLowerCase(Locale.ROOT);
            default -> "gui.futureshops.atm.collect_result.unavailable";
        };
    }

    static String depositResultKey(String statusCode) {
        return switch (statusCode) {
            case "SUCCESS", "NO_CURRENCY", "INVALID_AMOUNT",
                    "NOT_ENOUGH_CURRENCY", "INVALID_DENOMINATION",
                    "TOO_MANY_ITEMS",
                    "WRONG_PROVIDER", "CREATIVE_BLOCKED",
                    "LEGACY_MIGRATION_REQUIRED", "INVALID_CURRENCY",
                    "CONFIG_CHANGED", "CANCELLED", "ESCROW_UNAVAILABLE",
                    "RECOVERY_REQUIRED", "RECOVERY_PENDING",
                    "MANUAL_REVIEW", "REFUNDED", "REQUEST_CONFLICT",
                    "RATE_LIMITED", "SERVER_ERROR" ->
                    "gui.futureshops.atm.deposit_result."
                            + statusCode.toLowerCase(Locale.ROOT);
            default ->
                    "gui.futureshops.atm.deposit_result.server_error";
        };
    }

    static String depositSourceKey(C2SAtmDepositPacket.Source source) {
        return switch (source) {
            case INVENTORY -> "gui.futureshops.atm.deposit_source.inventory";
            case MAIN_HAND -> "gui.futureshops.atm.deposit_source.main_hand";
            case OFF_HAND -> "gui.futureshops.atm.deposit_source.off_hand";
        };
    }

    static String depositSourceExactKey(
            C2SAtmDepositPacket.Source source
    ) {
        return switch (source) {
            case INVENTORY ->
                    "gui.futureshops.atm.deposit_source.inventory_exact";
            case MAIN_HAND ->
                    "gui.futureshops.atm.deposit_source.main_hand_exact";
            case OFF_HAND ->
                    "gui.futureshops.atm.deposit_source.off_hand_exact";
        };
    }

    static String cashClaimRecoveryHandles(
            S2CAtmCollectCashResultPacket result
    ) {
        return result.quarantinedClaimIds().stream()
                .map(java.util.UUID::toString)
                .collect(Collectors.joining(", "));
    }

    static long retrySeconds(long retryAfterMillis) {
        if (retryAfterMillis <= 0L) {
            return 0L;
        }
        return (retryAfterMillis - 1L) / 1_000L + 1L;
    }

    static S2CAtmDataPacket reconcileTerminalCashClaims(
            S2CAtmDataPacket current,
            List<UUID> submittedClaimIds,
            int remainingPendingClaimCount
    ) {
        S2CAtmDataPacket exactCurrent = java.util.Objects.requireNonNull(
                current, "current");
        Set<UUID> submitted = Set.copyOf(java.util.Objects.requireNonNull(
                submittedClaimIds, "submittedClaimIds"));
        if (submitted.isEmpty()
                || remainingPendingClaimCount < 0
                || remainingPendingClaimCount
                > S2CAtmDataPacket.MAX_PENDING_CASH_CLAIMS) {
            return exactCurrent;
        }
        List<S2CAtmDataPacket.CashClaimSummary> remaining =
                exactCurrent.collectibleCashClaims().stream()
                        .filter(claim -> !submitted.contains(claim.claimId()))
                        .limit(remainingPendingClaimCount)
                        .toList();
        return new S2CAtmDataPacket(
                exactCurrent.balanceMinor(), exactCurrent.balanceKnown(),
                exactCurrent.currencyName(), exactCurrent.currencyDecimals(),
                exactCurrent.providerId(), exactCurrent.route(),
                exactCurrent.protectedMinting(),
                exactCurrent.currencySignature(),
                exactCurrent.denominations(),
                exactCurrent.serviceAvailable(),
                exactCurrent.availabilityCode(),
                exactCurrent.openScreen(), remainingPendingClaimCount,
                remaining, exactCurrent.depositRecovery());
    }

    static String availabilityKey(String availabilityCode) {
        return switch (availabilityCode) {
            case "MIGRATION_FAILED", "MIGRATION_PENDING",
                    "RECOVERY_PENDING", "ESCROW_MAINTENANCE",
                    "ESCROW_UNAVAILABLE" ->
                    "gui.futureshops.atm.availability."
                            + availabilityCode.toLowerCase(Locale.ROOT);
            default -> "gui.futureshops.atm.availability.unavailable";
        };
    }

    private String depositSummary() {
        Optional<AtmDepositTracker.PendingRequest> pending =
                ShopClientPacketHandler.pendingAtmDeposit();
        C2SAtmDepositPacket.Source source = pending
                .map(AtmDepositTracker.PendingRequest::source)
                .orElse(depositSource);
        OptionalLong amount = pending
                .map(AtmDepositTracker.PendingRequest::requestedMinorUnits)
                .orElseGet(() -> {
                    if (amountBox == null || amountBox.getValue().isBlank()) {
                        return OptionalLong.empty();
                    }
                    try {
                        long parsed = EconomyCommandUtil
                                .parseAmountToMinorUnits(
                                        amountBox.getValue(),
                                        data.currencyDecimals());
                        return parsed > 0L
                                ? OptionalLong.of(parsed)
                                : OptionalLong.empty();
                    } catch (IllegalArgumentException exception) {
                        return OptionalLong.empty();
                    }
                });
        return amount.isPresent()
                ? Component.translatable(
                "gui.futureshops.atm.deposit_summary.exact",
                Component.translatable(depositSourceKey(source)),
                format(amount.getAsLong())).getString()
                : Component.translatable(
                "gui.futureshops.atm.deposit_summary.all",
                Component.translatable(depositSourceKey(source)))
                .getString();
    }

    private boolean validDepositAmountField() {
        if (amountBox == null || amountBox.getValue().isBlank()) {
            return true;
        }
        try {
            return EconomyCommandUtil.parseAmountToMinorUnits(
                    amountBox.getValue(), data.currencyDecimals()) > 0L;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean creativeDepositBlocked() {
        return this.minecraft != null
                && this.minecraft.player != null
                && this.minecraft.player.getAbilities().instabuild;
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

    private int estimatedPendingStacks(
            AtmWithdrawalTracker.PendingRequest pending
    ) {
        List<Integer> pendingCounts = pending.denominationCounts();
        if (!pending.currencySignature().equals(data.currencySignature())
                || pendingCounts.size() != data.denominations().size()) {
            return pendingCounts.stream().mapToInt(Integer::intValue).sum();
        }
        int total = 0;
        for (int index = 0; index < pendingCounts.size(); index++) {
            int maximum = Math.max(
                    1, data.denominations().get(index).maxStackSize());
            int count = pendingCounts.get(index);
            total += (count + maximum - 1) / maximum;
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
        try {
            if (ShopUiUtil.dispatchClicks(clickZones, mouseX, mouseY)) {
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        } catch (RuntimeException exception) {
            setError("gui.futureshops.atm.request_rejected");
            return true;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        listScroll = Math.max(0, listScroll - (int) delta);
        return true;
    }

    @Override
    public void onClose() {
        if (this.minecraft == null
                || ShopClientPacketHandler.atmWithdrawalState()
                == AtmWithdrawalTracker.PendingState.AWAITING
                || ShopClientPacketHandler.atmCashCollectionState()
                == AtmCashClaimCollectionTracker.PendingState.AWAITING
                || ShopClientPacketHandler.atmDepositState()
                == AtmDepositTracker.PendingState.AWAITING) {
            return;
        }
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
