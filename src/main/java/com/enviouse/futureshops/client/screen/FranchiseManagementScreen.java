package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.FranchiseMemberEntry;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SFranchiseActionPacket;
import com.enviouse.futureshops.network.packets.C2SOpenBalanceUiPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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

    public FranchiseManagementScreen(boolean inFranchise, UUID franchiseId, String franchiseName,
                                     boolean isLeader, List<FranchiseMemberEntry> members,
                                     boolean hasPendingInvite, String pendingFranchiseName) {
        super(Component.literal("Franchise Management"));
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

        // Back button (always at the left)
        addRenderableWidget(Button.builder(Component.literal("§7← Back"), button ->
                        ShopPackets.CHANNEL.sendToServer(new C2SOpenBalanceUiPacket()))
                .bounds(guiLeft + 6, guiTop + guiH - 22, 50, 16)
                .build());

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
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(closeRightEdge - 50, guiTop + guiH - 22, 50, 16)
                .build());

        if (!inFranchise) {
            // Show pending invite or "no franchise" state
            if (hasPendingInvite) {
                addRenderableWidget(Button.builder(Component.literal("§a✓ Accept"), button ->
                                ShopPackets.CHANNEL.sendToServer(new C2SFranchiseActionPacket("ACCEPT", "")))
                        .bounds(guiLeft + guiW / 2 - 60, guiTop + 100, 55, 16)
                        .build());
                addRenderableWidget(Button.builder(Component.literal("§c✗ Decline"), button ->
                                ShopPackets.CHANNEL.sendToServer(new C2SFranchiseActionPacket("DECLINE", "")))
                        .bounds(guiLeft + guiW / 2 + 5, guiTop + 100, 55, 16)
                        .build());
            }
            // Create franchise — use an EditBox for name input
            inviteBox = new EditBox(this.font, guiLeft + guiW / 2 - 60, guiTop + 140, 120, 16,
                    Component.literal("Franchise name"));
            inviteBox.setMaxLength(32);
            inviteBox.setHint(Component.literal("Enter franchise name..."));
            addRenderableWidget(inviteBox);
            // Create button — handled in mouseClicked via custom logic (franchise create uses /franchise create)
            return;
        }

        // In franchise — show invite box for leader
        if (isLeader) {
            inviteBox = new EditBox(this.font, guiLeft + guiW - 180, guiTop + 56, 110, 14,
                    Component.literal("Invite player"));
            inviteBox.setMaxLength(16);
            inviteBox.setHint(Component.literal("Player name..."));
            addRenderableWidget(inviteBox);

            addRenderableWidget(Button.builder(Component.literal("§a+ Invite"), button -> {
                        if (inviteBox != null && !inviteBox.getValue().isBlank()) {
                            ShopPackets.CHANNEL.sendToServer(new C2SFranchiseActionPacket("INVITE", inviteBox.getValue().trim()));
                            inviteBox.setValue("");
                        }
                    })
                    .bounds(guiLeft + guiW - 66, guiTop + 56, 54, 14)
                    .build());
        }

        // Leave / Disband buttons
        if (isLeader) {
            addRenderableWidget(Button.builder(Component.literal("§c⚠ Disband"), button ->
                            ShopPackets.CHANNEL.sendToServer(new C2SFranchiseActionPacket("DISBAND", "")))
                    .bounds(guiLeft + guiW - 80, guiTop + guiH - 22, 70, 16)
                    .build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("§c↩ Leave"), button ->
                            ShopPackets.CHANNEL.sendToServer(new C2SFranchiseActionPacket("LEAVE", "")))
                    .bounds(guiLeft + guiW - 66, guiTop + guiH - 22, 58, 16)
                    .build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ShopUiUtil.renderDimBackdrop(graphics, this.width, this.height);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, guiW, guiH, ShopColors.BORDER_STRONG, ShopColors.BORDER_SUBTLE);
        graphics.fill(guiLeft, guiTop, guiLeft + guiW, guiTop + 2, ShopColors.ACCENT_PRIMARY);

        if (!inFranchise) {
            renderNoFranchise(graphics, mouseX, mouseY);
        } else {
            renderFranchiseView(graphics, mouseX, mouseY);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderNoFranchise(GuiGraphics graphics, int mouseX, int mouseY) {
        // Header
        ShopUiUtil.renderCard(graphics, guiLeft + 10, guiTop + 10, guiW - 20, 40);
        graphics.fill(guiLeft + 10, guiTop + 10, guiLeft + guiW - 10, guiTop + 12, ShopColors.ACCENT_PRIMARY);
        graphics.drawString(this.font, "⚑ Franchise", guiLeft + 18, guiTop + 18, ShopColors.TEXT_STRONG, true);
        graphics.drawString(this.font, "You are not in a franchise", guiLeft + 18, guiTop + 32, ShopColors.TEXT_MUTED, false);

        if (hasPendingInvite) {
            ShopUiUtil.renderCard(graphics, guiLeft + 10, guiTop + 60, guiW - 20, 50);
            graphics.fill(guiLeft + 10, guiTop + 60, guiLeft + guiW - 10, guiTop + 62, ShopColors.STATUS_WARNING);
            graphics.drawString(this.font, "📨 Pending Invite", guiLeft + 18, guiTop + 68, ShopColors.STATUS_WARNING, true);
            graphics.drawString(this.font, "You've been invited to \"" + pendingFranchiseName + "\"",
                    guiLeft + 18, guiTop + 82, ShopColors.TEXT_STRONG, false);
        }

        // Create franchise section
        graphics.drawString(this.font, "Create a franchise:", guiLeft + guiW / 2 - 50, guiTop + 128, ShopColors.TEXT_MUTED, false);
    }

    private void renderFranchiseView(GuiGraphics graphics, int mouseX, int mouseY) {
        // Header with franchise name
        ShopUiUtil.renderCard(graphics, guiLeft + 10, guiTop + 10, guiW - 20, 40);
        graphics.fill(guiLeft + 10, guiTop + 10, guiLeft + guiW - 10, guiTop + 12, ShopColors.ACCENT_PRIMARY);
        graphics.drawString(this.font, "⚑ " + franchiseName, guiLeft + 18, guiTop + 18, ShopColors.ACCENT_PRIMARY, true);
        String roleText = isLeader ? "§6Leader" : "§7Member";
        graphics.drawString(this.font, roleText + " • " + members.size() + " member" + (members.size() != 1 ? "s" : ""),
                guiLeft + 18, guiTop + 32, ShopColors.TEXT_MUTED, false);

        // Member list
        int listX = guiLeft + 10;
        int listY = guiTop + 76;
        int listW = guiW - 20;
        int listH = guiH - 110;
        ShopUiUtil.renderCard(graphics, listX, listY, listW, listH);
        graphics.fill(listX, listY, listX + listW, listY + 2, ShopColors.ACCENT_PRIMARY);
        graphics.drawString(this.font, "§l👥 MEMBERS", listX + 8, listY + 6, ShopColors.TEXT_STRONG, false);

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
            if (member.leader()) nameText += " §6[Leader]";
            graphics.drawString(this.font, this.font.plainSubstrByWidth(nameText, listW - 120),
                    listX + 38, y + 6, ShopColors.TEXT_STRONG, false);

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

