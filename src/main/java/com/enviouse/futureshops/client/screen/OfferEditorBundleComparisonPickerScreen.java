package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

public final class OfferEditorBundleComparisonPickerScreen
        extends Screen implements ShopScreenMarker {
    private final Screen parent;
    private final OfferItemComponent output;
    private final String editedListingId;
    private final Consumer<Selection> selection;
    private final List<Button> rows = new ArrayList<>();
    private List<Entry> filtered = List.of();
    private EditBox search;
    private String query = "";
    private int selectedIndex;
    private int scroll;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int visibleRows;

    public OfferEditorBundleComparisonPickerScreen(
            Screen parent,
            OfferItemComponent output,
            String editedListingId,
            Consumer<Selection> selection
    ) {
        super(Component.translatable(
                "gui.futureshops.offer_editor.comparison_picker.title"));
        this.parent = Objects.requireNonNull(parent, "parent");
        this.output = Objects.requireNonNull(output, "output");
        this.editedListingId = Objects.requireNonNullElse(
                editedListingId, "");
        this.selection = Objects.requireNonNull(
                selection, "selection");
    }

    @Override
    protected void init() {
        panelWidth = Math.min(460, width - 24);
        panelHeight = Math.min(330, height - 24);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        visibleRows = Math.max(
                1, (panelHeight - 100) / 24);

        search = new EditBox(font, left + 12, top + 38,
                panelWidth - 24, 20,
                Component.translatable(
                        "gui.futureshops.offer_editor.picker.search"));
        search.setHint(Component.translatable(
                "gui.futureshops.offer_editor.comparison_picker.search_hint"));
        search.setValue(query);
        search.setMaxLength(96);
        search.setResponder(value -> {
            query = value;
            selectedIndex = 0;
            scroll = 0;
            rebuildRows();
        });
        search.setTooltip(Tooltip.create(Component.translatable(
                "gui.futureshops.offer_editor.help"
                        + ".comparison_search")));
        addRenderableWidget(search);

        Button cancel = FutureShopsButton.styled(Component.translatable(
                        "gui.futureshops.offer_editor.cancel"),
                ignored -> onClose())
                .bounds(left + panelWidth - 80,
                        top + panelHeight - 28, 68, 20)
                .build();
        cancel.setTooltip(Tooltip.create(Component.translatable(
                "gui.futureshops.offer_editor.help.cancel")));
        addRenderableWidget(cancel);
        rebuildRows();
    }

    private void rebuildRows() {
        for (Button row : rows) {
            removeWidget(row);
        }
        rows.clear();
        String normalized = query.strip()
                .toLowerCase(Locale.ROOT);
        filtered = eligibleEntries().stream().filter(entry ->
                normalized.isEmpty()
                        || entry.searchText()
                        .toLowerCase(Locale.ROOT)
                        .contains(normalized)).toList();
        int count = filtered.size() + 1;
        selectedIndex = Math.max(0, Math.min(
                selectedIndex, count - 1));
        scroll = Math.max(0, Math.min(scroll,
                Math.max(0, count - visibleRows)));
        for (int visibleIndex = 0;
             visibleIndex < visibleRows; visibleIndex++) {
            int index = scroll + visibleIndex;
            if (index >= count) {
                break;
            }
            Component label = index == 0
                    ? Component.translatable(
                    "gui.futureshops.offer_editor.comparison_picker.none")
                    : Component.literal(filtered.get(index - 1)
                    .display());
            Button button = FutureShopsButton.styled(label, ignored -> {
                        selectedIndex = index;
                        chooseSelected();
                    }).bounds(left + 12,
                            top + 64 + visibleIndex * 24,
                            panelWidth - 24, 20).build();
            button.setTooltip(Tooltip.create(index == 0
                    ? Component.translatable(
                    "gui.futureshops.offer_editor.help"
                            + ".comparison_none")
                    : Component.literal(filtered.get(index - 1)
                    .listingId() + "  "
                    + filtered.get(index - 1).optionId())));
            rows.add(button);
            addRenderableWidget(button);
        }
    }

    private List<Entry> eligibleEntries() {
        List<Entry> entries = new ArrayList<>();
        for (ServerShopOfferListing listing
                : ShopClientState.getCatalogOffers()) {
            if (listing.listingId().equals(editedListingId)
                    || listing.outputs().size() != 1) {
                continue;
            }
            OfferItemComponent standalone =
                    listing.outputs().get(0);
            if (!standalone.itemId().equals(output.itemId())
                    || !standalone.exactNbt()
                    .equals(output.exactNbt())) {
                continue;
            }
            for (AcquireOfferOption option
                    : listing.acquireOptions()) {
                if (option.free()
                        || !option.moneyCostPresent()
                        || option.hasItemCosts()) {
                    continue;
                }
                String optionName = option.label().isBlank()
                        ? option.optionId() : option.label();
                String display = listing.displayName()
                        + "  " + optionName + "  "
                        + ShopUiUtil.formatMinorUnits(
                        option.moneyCostMinorUnits())
                        + " " + ShopClientState.getCurrencyName();
                entries.add(new Entry(
                        listing.listingId(), option.optionId(),
                        display, display + " "
                        + listing.listingId() + " "
                        + option.optionId() + " "
                        + standalone.itemId()));
            }
        }
        entries.sort(java.util.Comparator.comparing(
                Entry::listingId).thenComparing(Entry::optionId));
        return List.copyOf(entries);
    }

    private void chooseSelected() {
        if (minecraft == null) {
            return;
        }
        if (selectedIndex == 0) {
            selection.accept(new Selection("", ""));
        } else if (selectedIndex - 1 < filtered.size()) {
            Entry entry = filtered.get(selectedIndex - 1);
            selection.accept(new Selection(
                    entry.listingId(), entry.optionId()));
        }
        minecraft.setScreen(parent);
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        ShopUiUtil.renderDimBackdrop(graphics, width, height);
        graphics.fill(left, top, left + panelWidth,
                top + panelHeight, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, left, top,
                panelWidth, panelHeight, ShopColors.BORDER_STRONG,
                ShopColors.BORDER_SUBTLE);
        ShopUiUtil.renderAccentLine(graphics, left + 2,
                top, panelWidth - 4);
        graphics.drawString(font, title, left + 12,
                top + 10, ShopColors.TEXT_STRONG, false);
        ShopUiUtil.renderItemIconWithNbt(
                graphics, font, output.itemId(),
                output.exactNbt(), left + 12, top + 18);
        graphics.drawString(font, output.itemId(),
                left + 34, top + 22,
                ShopColors.TEXT_MUTED, false);
        ShopUiUtil.renderScrollIndicators(
                graphics, font, left + 12, top + 64,
                panelWidth - 24, visibleRows * 24,
                scroll, visibleRows, filtered.size() + 1);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (mouseX >= left + 12 && mouseX < left + 30
                && mouseY >= top + 18 && mouseY < top + 36) {
            ShopUiUtil.renderItemTooltip(
                    graphics, font, output.itemId(),
                    output.exactNbt(), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double delta
    ) {
        scroll = Math.max(0, Math.min(
                scroll + (delta < 0 ? 1 : -1),
                Math.max(0,
                        filtered.size() + 1 - visibleRows)));
        rebuildRows();
        return true;
    }

    @Override
    public boolean keyPressed(
            int keyCode,
            int scanCode,
            int modifiers
    ) {
        if (Screen.hasControlDown()
                && keyCode == GLFW.GLFW_KEY_F) {
            setFocused(search);
            search.setFocused(true);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (!search.isFocused()
                && (keyCode == GLFW.GLFW_KEY_UP
                || keyCode == GLFW.GLFW_KEY_DOWN)) {
            int count = filtered.size() + 1;
            selectedIndex = Math.max(0, Math.min(
                    selectedIndex
                            + (keyCode == GLFW.GLFW_KEY_DOWN
                            ? 1 : -1),
                    count - 1));
            if (selectedIndex < scroll) {
                scroll = selectedIndex;
                rebuildRows();
            } else if (selectedIndex
                    >= scroll + visibleRows) {
                scroll = selectedIndex - visibleRows + 1;
                rebuildRows();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            chooseSelected();
            return true;
        }
        return super.keyPressed(
                keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public record Selection(
            String listingId,
            String optionId
    ) {
        public Selection {
            listingId = Objects.requireNonNullElse(
                    listingId, "");
            optionId = Objects.requireNonNullElse(
                    optionId, "");
        }
    }

    private record Entry(
            String listingId,
            String optionId,
            String display,
            String searchText
    ) {
    }
}
