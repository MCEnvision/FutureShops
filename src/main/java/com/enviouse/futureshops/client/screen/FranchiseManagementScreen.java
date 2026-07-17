package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.FranchiseMemberEntry;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SFranchiseActionPacket;
import com.enviouse.futureshops.network.packets.C2SOpenBalanceUiPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/**
 * Franchise management GUI — view members, invite, kick, promote, leave, disband.
 * Opened via /franchise manage or from BalanceOverviewScreen.
 */
public class FranchiseManagementScreen extends Screen implements ShopScreenMarker {

    private boolean inFranchise;
    private UUID franchiseId;
    private String franchiseName;
    private boolean isLeader;
    private List<FranchiseMemberEntry> members;
    private boolean hasPendingInvite;
    private String pendingFranchiseName;

    private int guiLeft, guiTop, guiW, guiH;
    private int memberScroll;
    private int visibleMembers;
    private EditBox inviteBox;
    private ConfirmationModal disbandConfirm;
    private ConfirmationModal leaveConfirm;

    /** Per-frame flat-button hit regions, populated in {@link #render}, consulted in mouseClicked. */
    private final java.util.List<ShopUiUtil.ClickZone> clickZones = new java.util.ArrayList<>();

    public FranchiseManagementScreen(boolean inFranchise, UUID franchiseId, String franchiseName,
                                     boolean isLeader, List<FranchiseMemberEntry> members,
                                     boolean hasPendingInvite, String pendingFranchiseName) {
        super(Component.translatable("gui.futureshops.franchise_mgmt.title"));
        this.inFranchise = inFranchise;
        this.franchiseId = franchiseId;
        this.franchiseName = franchiseName;
        this.isLeader = isLeader;
        this.members = List.copyOf(members);
        this.hasPendingInvite = hasPendingInvite;
        this.pendingFranchiseName = pendingFranchiseName;
    }

    /**
     * Called when updated data arrives from the server.
     */
    public void updateData(boolean inFranchise, UUID franchiseId, String franchiseName,
                           boolean isLeader, List<FranchiseMemberEntry> members,
                           boolean hasPendingInvite, String pendingFranchiseName) {
        this.inFranchise = inFranchise;
        this.franchiseId = franchiseId;
        this.franchiseName = franchiseName;
        this.isLeader = isLeader;
        this.members = List.copyOf(members);
        this.hasPendingInvite = hasPendingInvite;
        this.pendingFranchiseName = pendingFranchiseName;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        guiW = Math.max(300, this.width - 4);
        guiH = Math.max(200, this.height - 4);
        guiLeft = (this.width - guiW) / 2;
        guiTop = (this.height - guiH) / 2;
        visibleMembers = Math.max(2, (guiH - 140) / 22);

        // Only EditBoxes remain real widgets; every button is drawn immediate-mode in render().
        if (!inFranchise) {
            // Create franchise — use an EditBox for name input
            inviteBox = new EditBox(this.font, guiLeft + guiW / 2 - 60, guiTop + 140, 120, 16,
                    Component.translatable("gui.futureshops.franchise_mgmt.name_narration"));
            inviteBox.setMaxLength(32);
            inviteBox.setHint(Component.translatable("gui.futureshops.franchise_mgmt.name_hint"));
            addRenderableWidget(inviteBox);
            return;
        }

        // In franchise — show invite box for leader
        if (isLeader) {
            inviteBox = new EditBox(this.font, guiLeft + guiW - 180, guiTop + 56, 110, 14,
                    Component.translatable("gui.futureshops.franchise_mgmt.invite_narration"));
            inviteBox.setMaxLength(16);
            inviteBox.setHint(Component.translatable("gui.futureshops.franchise_mgmt.invite_hint"));
            addRenderableWidget(inviteBox);
        }
    }

    /** Draws every flat Nocturne button and registers its click zone for this frame. */
    private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        // Back button (always at the left)
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                guiLeft + 6, guiTop + guiH - 22, 50, 16,
                Component.translatable("gui.futureshops.cart.back"),
                ShopUiUtil.ButtonStyle.SECONDARY, true,
                () -> ShopPackets.CHANNEL.sendToServer(new C2SOpenBalanceUiPacket()));

        // Close button sits to the LEFT of Disband/Leave to avoid overlap.
        // Reserve right-edge slots for destructive actions when in a franchise.
        int closeRightEdge;
        if (inFranchise && isLeader) {
            closeRightEdge = guiLeft + guiW - 84; // Disband occupies guiW-80..guiW-10
        } else if (inFranchise) {
            closeRightEdge = guiLeft + guiW - 70; // Leave occupies guiW-66..guiW-8
        } else {
            closeRightEdge = guiLeft + guiW - 6;
        }
        ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                closeRightEdge - 50, guiTop + guiH - 22, 50, 16,
                Component.translatable("gui.futureshops.baltop.close"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, this::onClose);

        if (!inFranchise) {
            if (hasPendingInvite) {
                ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                        guiLeft + guiW / 2 - 60, guiTop + 100, 55, 16,
                        Component.translatable("gui.futureshops.franchise_mgmt.accept"),
                        ShopUiUtil.ButtonStyle.PRIMARY, true,
                        () -> ShopPackets.CHANNEL.sendToServer(new C2SFranchiseActionPacket("ACCEPT", "")));
                ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                        guiLeft + guiW / 2 + 5, guiTop + 100, 55, 16,
                        Component.translatable("gui.futureshops.franchise_mgmt.decline"),
                        ShopUiUtil.ButtonStyle.SECONDARY, true,
                        () -> ShopPackets.CHANNEL.sendToServer(new C2SFranchiseActionPacket("DECLINE", "")));
            }
            return;
        }

        if (isLeader) {
            ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                    guiLeft + guiW - 66, guiTop + 56, 54, 14,
                    Component.translatable("gui.futureshops.franchise_mgmt.invite_btn"),
                    ShopUiUtil.ButtonStyle.PRIMARY, true, () -> {
                        if (inviteBox != null && !inviteBox.getValue().isBlank()) {
                            ShopPackets.CHANNEL.sendToServer(new C2SFranchiseActionPacket("INVITE", inviteBox.getValue().trim()));
                            inviteBox.setValue("");
                        }
                    });
            ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                    guiLeft + guiW - 80, guiTop + guiH - 22, 70, 16,
                    Component.translatable("gui.futureshops.franchise_mgmt.disband_btn"),
                    ShopUiUtil.ButtonStyle.DANGER, true, this::openDisbandConfirm);
        } else {
            ShopUiUtil.button(graphics, this.font, clickZones, mouseX, mouseY,
                    guiLeft + guiW - 66, guiTop + guiH - 22, 58, 16,
                    Component.translatable("gui.futureshops.franchise_mgmt.leave_btn"),
                    ShopUiUtil.ButtonStyle.DANGER, true, this::openLeaveConfirm);
        }
    }

    private void openLeaveConfirm() {
        String name = franchiseName == null || franchiseName.isBlank()
                ? Component.translatable("gui.futureshops.franchise_mgmt.this_franchise").getString() : franchiseName;
        leaveConfirm = new ConfirmationModal(
                Component.translatable("gui.futureshops.franchise_mgmt.leave.title").getString(),
                java.util.List.of(
                        ConfirmationModal.SummaryLine.text(Component.translatable("gui.futureshops.franchise_mgmt.leave.line1", name).getString()),
                        ConfirmationModal.SummaryLine.text(Component.translatable("gui.futureshops.franchise_mgmt.leave.line2").getString())),
                Component.translatable("gui.futureshops.franchise_mgmt.leave.total").getString(),
                modal -> {
                    modal.setProcessing();
                    ShopPackets.CHANNEL.sendToServer(new C2SFranchiseActionPacket("LEAVE", ""));
                    modal.setSuccess(Component.translatable("gui.futureshops.franchise_mgmt.leave.success").getString());
                },
                () -> leaveConfirm = null);
    }

    private void openDisbandConfirm() {
        String name = franchiseName == null || franchiseName.isBlank()
                ? Component.translatable("gui.futureshops.franchise_mgmt.this_franchise").getString() : franchiseName;
        disbandConfirm = new ConfirmationModal(
                Component.translatable("gui.futureshops.franchise_mgmt.disband.title").getString(),
                java.util.List.of(
                        ConfirmationModal.SummaryLine.text(Component.translatable("gui.futureshops.franchise_mgmt.disband.line1", name).getString()),
                        ConfirmationModal.SummaryLine.text(Component.translatable("gui.futureshops.franchise_mgmt.disband.line2").getString()),
                        ConfirmationModal.SummaryLine.text(Component.translatable("gui.futureshops.franchise_mgmt.disband.line3").getString())),
                Component.translatable("gui.futureshops.franchise_mgmt.disband.total").getString(),
                modal -> {
                    modal.setProcessing();
                    ShopPackets.CHANNEL.sendToServer(new C2SFranchiseActionPacket("DISBAND", ""));
                    modal.setSuccess(Component.translatable("gui.futureshops.franchise_mgmt.disband.success").getString());
                },
                () -> disbandConfirm = null);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        clickZones.clear();
        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + 2, ShopColors.ACCENT_PRIMARY);

        if (!inFranchise) {
            renderNoFranchise(graphics, mouseX, mouseY);
        } else {
            renderFranchiseView(graphics, mouseX, mouseY);
        }

        renderButtons(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);

        if (disbandConfirm != null) {
            disbandConfirm.render(graphics, this.font, this.width, this.height, mouseX, mouseY);
            if (disbandConfirm.shouldAutoDismiss()) {
                disbandConfirm = null;
            }
        }
        if (leaveConfirm != null) {
            leaveConfirm.render(graphics, this.font, this.width, this.height, mouseX, mouseY);
            if (leaveConfirm.shouldAutoDismiss()) {
                leaveConfirm = null;
            }
        }
    }

    private void renderNoFranchise(GuiGraphics graphics, int mouseX, int mouseY) {
        // Header
        ShopUiUtil.renderCard(graphics, guiLeft + 10, guiTop + 10, guiW - 20, 40);
        graphics.fill(guiLeft + 10, guiTop + 10, guiLeft + guiW - 10, guiTop + 12, ShopColors.ACCENT_PRIMARY);
        graphics.drawString(this.font, Component.translatable("gui.futureshops.balance.franchise"), guiLeft + 18, guiTop + 18, ShopColors.TEXT_STRONG, true);
        graphics.drawString(this.font, Component.translatable("gui.futureshops.franchise_mgmt.not_in"), guiLeft + 18, guiTop + 32, ShopColors.TEXT_MUTED, false);

        if (hasPendingInvite) {
            ShopUiUtil.renderCard(graphics, guiLeft + 10, guiTop + 60, guiW - 20, 50);
            graphics.fill(guiLeft + 10, guiTop + 60, guiLeft + guiW - 10, guiTop + 62, ShopColors.STATUS_WARNING);
            graphics.drawString(this.font, Component.translatable("gui.futureshops.franchise_mgmt.pending_invite"), guiLeft + 18, guiTop + 68, ShopColors.STATUS_WARNING, true);
            graphics.drawString(this.font, Component.translatable("gui.futureshops.franchise_mgmt.invited_to", pendingFranchiseName),
                    guiLeft + 18, guiTop + 82, ShopColors.TEXT_STRONG, false);
        }

        // Create franchise section
        graphics.drawString(this.font, Component.translatable("gui.futureshops.franchise_mgmt.create_prompt"), guiLeft + guiW / 2 - 50, guiTop + 128, ShopColors.TEXT_MUTED, false);
    }

    private void renderFranchiseView(GuiGraphics graphics, int mouseX, int mouseY) {
        // Header with franchise name
        ShopUiUtil.renderCard(graphics, guiLeft + 10, guiTop + 10, guiW - 20, 40);
        graphics.fill(guiLeft + 10, guiTop + 10, guiLeft + guiW - 10, guiTop + 12, ShopColors.ACCENT_PRIMARY);
        graphics.drawString(this.font, Component.translatable("gui.futureshops.franchise_mgmt.name_header", franchiseName), guiLeft + 18, guiTop + 18, ShopColors.ACCENT_PRIMARY, true);
        String roleText = isLeader
                ? Component.translatable("gui.futureshops.franchise_mgmt.role.leader").getString()
                : Component.translatable("gui.futureshops.franchise_mgmt.role.member").getString();
        graphics.drawString(this.font, Component.translatable(
                        members.size() != 1 ? "gui.futureshops.franchise_mgmt.role_summary.many" : "gui.futureshops.franchise_mgmt.role_summary.one",
                        roleText, members.size()),
                guiLeft + 18, guiTop + 32, ShopColors.TEXT_MUTED, false);

        // Member list
        int listX = guiLeft + 10;
        int listY = guiTop + 76;
        int listW = guiW - 20;
        int listH = guiH - 110;
        ShopUiUtil.renderCard(graphics, listX, listY, listW, listH);
        graphics.fill(listX, listY, listX + listW, listY + 2, ShopColors.ACCENT_PRIMARY);
        graphics.drawString(this.font, Component.translatable("gui.futureshops.franchise_mgmt.members_header"), listX + 8, listY + 6, ShopColors.TEXT_STRONG, false);

        int maxScroll = Math.max(0, members.size() - visibleMembers);
        memberScroll = Math.max(0, Math.min(memberScroll, maxScroll));

        int entryY = listY + 22;
        graphics.enableScissor(listX, listY + 20, listX + listW, listY + listH - 2);
        for (int i = 0; i < visibleMembers && i + memberScroll < members.size(); i++) {
            FranchiseMemberEntry member = members.get(i + memberScroll);
            int y = entryY + i * 22;
            boolean hovered = mouseX >= listX + 4 && mouseX <= listX + listW - 4 && mouseY >= y && mouseY <= y + 20;

            graphics.fill(listX + 4, y, listX + listW - 4, y + 20,
                    hovered ? ShopColors.SURFACE_OVERLAY : (i % 2 == 0 ? ShopColors.SURFACE_RAISED : ShopColors.SURFACE_BASE));

            // Online indicator
            int dotColor = member.online() ? ShopColors.STATUS_SUCCESS : ShopColors.TEXT_FAINT;
            graphics.fill(listX + 8, y + 7, listX + 12, y + 13, dotColor);

            // Player head
            ShopUiUtil.renderPlayerFace(graphics, member.uuid(), listX + 16, y + 2, 16);

            // Name + role
            String nameText = member.name();
            if (member.leader()) nameText += " " + Component.translatable("gui.futureshops.franchise_mgmt.leader_suffix").getString();
            ShopUiUtil.renderScrollingString(graphics, this.font, nameText,
                    listX + 38, y + 6, listW - 120, ShopColors.TEXT_STRONG);

            // Action buttons (leader only, can't kick/promote self)
            if (isLeader && !member.leader()) {
                // Kick button
                int kickX = listX + listW - 50;
                boolean kickHover = mouseX >= kickX && mouseX <= kickX + 22 && mouseY >= y + 2 && mouseY <= y + 16;
                graphics.fill(kickX, y + 2, kickX + 22, y + 16, kickHover ? ShopColors.STATUS_DANGER : ShopColors.SURFACE_PRESSED);
                graphics.drawString(this.font, "§c✗", kickX + 7, y + 5, ShopColors.STATUS_DANGER, false);

                // Promote button
                int promX = listX + listW - 80;
                boolean promHover = mouseX >= promX && mouseX <= promX + 26 && mouseY >= y + 2 && mouseY <= y + 16;
                graphics.fill(promX, y + 2, promX + 26, y + 16, promHover ? ShopColors.ACCENT_CURRENCY : ShopColors.SURFACE_PRESSED);
                graphics.drawString(this.font, "§6👑", promX + 7, y + 5, ShopColors.ACCENT_CURRENCY, false);
            }
        }
        graphics.disableScissor();

        ShopUiUtil.renderScrollIndicators(graphics, this.font, listX, listY + 20, listW, listH - 22,
                memberScroll, visibleMembers, members.size());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (disbandConfirm != null) {
            return disbandConfirm.mouseClicked(mouseX, mouseY, button, this.font);
        }
        if (leaveConfirm != null) {
            return leaveConfirm.mouseClicked(mouseX, mouseY, button, this.font);
        }
        // Flat Nocturne buttons: run the top-most hit ClickZone before member/EditBox handling.
        if (ShopUiUtil.dispatchClicks(clickZones, mouseX, mouseY)) {
            return true;
        }
        // Handle create franchise (when not in a franchise and inviteBox has text)
        if (!inFranchise && !hasPendingInvite && inviteBox != null && !inviteBox.getValue().isBlank()) {
            // Check if we clicked near the create area — use Enter key instead
        }

        // Handle member action clicks (kick/promote)
        if (inFranchise && isLeader) {
            int listX = guiLeft + 10;
            int listY = guiTop + 76;
            int listW = guiW - 20;
            int entryY = listY + 22;

            for (int i = 0; i < visibleMembers && i + memberScroll < members.size(); i++) {
                FranchiseMemberEntry member = members.get(i + memberScroll);
                if (member.leader()) continue;
                int y = entryY + i * 22;

                // Kick button
                int kickX = listX + listW - 50;
                if (mouseX >= kickX && mouseX <= kickX + 22 && mouseY >= y + 2 && mouseY <= y + 16) {
                    ShopPackets.CHANNEL.sendToServer(new C2SFranchiseActionPacket("KICK", member.name()));
                    return true;
                }

                // Promote button
                int promX = listX + listW - 80;
                if (mouseX >= promX && mouseX <= promX + 26 && mouseY >= y + 2 && mouseY <= y + 16) {
                    ShopPackets.CHANNEL.sendToServer(new C2SFranchiseActionPacket("PROMOTE", member.name()));
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (disbandConfirm != null) {
            if (disbandConfirm.keyPressed(keyCode)) {
                return true;
            }
        }
        if (leaveConfirm != null) {
            if (leaveConfirm.keyPressed(keyCode)) {
                return true;
            }
        }
        // Enter key in inviteBox: if not in franchise → create; if in franchise → invite
        if ((keyCode == 257 || keyCode == 335) && inviteBox != null && inviteBox.isFocused() && !inviteBox.getValue().isBlank()) {
            if (!inFranchise) {
                // Create franchise (send as chat command since franchise create isn't a packet action yet)
                if (this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.connection.sendCommand("franchise create " + inviteBox.getValue().trim());
                    // Re-request data after a tick
                    this.minecraft.tell(() -> ShopPackets.CHANNEL.sendToServer(new C2SFranchiseActionPacket("OPEN", "")));
                }
            } else if (isLeader) {
                ShopPackets.CHANNEL.sendToServer(new C2SFranchiseActionPacket("INVITE", inviteBox.getValue().trim()));
                inviteBox.setValue("");
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (inFranchise) {
            int listX = guiLeft + 10;
            int listY = guiTop + 76;
            int listW = guiW - 20;
            int listH = guiH - 110;
            if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
                memberScroll = Math.max(0, Math.min(Math.max(0, members.size() - visibleMembers), memberScroll - (int) delta));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

