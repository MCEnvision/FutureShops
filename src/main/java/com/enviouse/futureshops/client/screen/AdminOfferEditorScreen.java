package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.catalog.AdminShopOfferConfigWriter;
import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferBundleComparison;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.OfferComponentNormalizer;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.catalog.offer.OfferSchedule;
import com.enviouse.futureshops.catalog.offer.OfferStockPolicy;
import com.enviouse.futureshops.catalog.offer.SellOfferOption;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.client.editor.AdminOfferSaveAcknowledgement;
import com.enviouse.futureshops.client.editor.OfferEditorControlRegistry;
import com.enviouse.futureshops.client.editor.OfferEditorDraft;
import com.enviouse.futureshops.client.editor.OfferEditorSimpleMode;
import com.enviouse.futureshops.client.editor.OfferEditorStaleReview;
import com.enviouse.futureshops.client.editor.OfferEditorTemplates;
import com.enviouse.futureshops.command.EconomyCommandUtil;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SAdminOfferSavePacket;
import com.enviouse.futureshops.network.packets
        .C2SPlayerShopOfferSavePacket;
import com.enviouse.futureshops.network.packets.S2CAdminOfferSaveResultPacket;
import com.enviouse.futureshops.network.packets
        .S2CPlayerShopOfferSaveResultPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.UnaryOperator;

public final class AdminOfferEditorScreen extends Screen
        implements ShopScreenMarker {
    private final Screen parent;
    private final OfferEditorDraft draft;
    private final PlayerShopTarget playerShopTarget;
    private boolean creating;
    private final List<EditBinding> bindings = new ArrayList<>();
    private UUID pendingRequestId;
    private AdminShopOfferConfigWriter.Operation pendingOperation;
    private boolean closeAfterSave;
    private Component resultMessage;
    private boolean resultSuccess;
    private ServerShopOfferListing staleSnapshot;
    private boolean staleReviewing;
    private ConfirmationModal confirmation;
    private boolean templateChosen;
    private int selectedOutputIndex;
    private int selectedAcquireIndex;
    private int selectedAcquireCostIndex;
    private int selectedSellIndex;
    private int selectedSellInputIndex;
    private ServerShopOfferPresentation.PreviewState previewState =
            ServerShopOfferPresentation.PreviewState.ACTIVE;
    private PreviewMode previewMode = PreviewMode.DETAIL;
    private int contentLeft;
    private int contentTop;
    private int contentWidth;
    private int summaryLeft;
    private int summaryWidth;
    private OfferItemComponent hoveredEditorComponent;
    private boolean addingContentWidgets;
    private final Map<EditBox, Component> simpleFieldLabels =
            new IdentityHashMap<>();
    private boolean advancedMode;
    private SimpleStep simpleStep = SimpleStep.BASICS;
    private int simpleScrollPosition;

    public AdminOfferEditorScreen(Screen parent, String listingId) {
        this(parent, ShopClientState.getCatalogOffer(listingId)
                .orElseThrow(), false, null);
    }

    public static AdminOfferEditorScreen create(Screen parent) {
        return new AdminOfferEditorScreen(
                parent, blankListing(), true, null);
    }

    public static AdminOfferEditorScreen create(
            Screen parent,
            OfferItemComponent component,
            String categoryId
    ) {
        ServerShopOfferListing base = withCategory(
                blankListing(), categoryId);
        ServerShopOfferListing listing = OfferEditorTemplates.apply(
                base, OfferEditorTemplates.Template.MONEY,
                Optional.of(java.util.Objects.requireNonNull(
                        component, "component")));
        AdminOfferEditorScreen editor = new AdminOfferEditorScreen(
                parent, listing, true, null);
        editor.templateChosen = true;
        editor.simpleStep = SimpleStep.TRADE;
        return editor;
    }

    public static AdminOfferEditorScreen createPlayerShop(
            Screen parent,
            BlockPos shopPos,
            int listingIndex,
            ServerShopOfferListing listing
    ) {
        return new AdminOfferEditorScreen(
                parent, listing, false,
                new PlayerShopTarget(shopPos, listingIndex));
    }

    private AdminOfferEditorScreen(
            Screen parent,
            ServerShopOfferListing listing,
            boolean creating,
            PlayerShopTarget playerShopTarget
    ) {
        super(Component.translatable(
                playerShopTarget == null
                        ? "gui.futureshops.offer_editor.title"
                        : "gui.futureshops.offer_editor.player_shop_title"));
        this.parent = parent;
        this.draft = new OfferEditorDraft(listing);
        this.creating = creating;
        this.templateChosen = !creating;
        this.playerShopTarget = playerShopTarget;
    }

    @Override
    protected void init() {
        clearWidgets();
        bindings.clear();
        simpleFieldLabels.clear();
        if (!advancedMode) {
            initSimpleEditor();
            return;
        }
        boolean narrow = width < 640;
        int margin = 10;
        int outlineWidth = narrow ? 0 : 118;
        summaryWidth = narrow ? 0 : 138;
        int sectionTop = narrow ? 42 : 28;
        contentLeft = margin + outlineWidth + (narrow ? 0 : 8);
        contentTop = sectionTop;
        summaryLeft = width - margin - summaryWidth;
        contentWidth = width - contentLeft - margin
                - summaryWidth - (summaryWidth == 0 ? 0 : 8);

        if (!templateChosen) {
            contentLeft = margin;
            contentTop = 34;
            contentWidth = width - margin * 2 - summaryWidth
                    - (summaryWidth == 0 ? 0 : 8);
            buildTemplateChooser();
            buildFooter();
            return;
        }

        int sectionX = narrow ? margin : margin;
        int sectionY = narrow ? 22 : 30;
        int sectionW = narrow
                ? Math.max(56, (width - margin * 2 - 8) / 4)
                : outlineWidth;
        OfferEditorDraft.Section[] sections =
                OfferEditorDraft.Section.values();
        for (int index = 0; index < sections.length; index++) {
            OfferEditorDraft.Section section = sections[index];
            int x = narrow
                    ? sectionX + index % 4 * (sectionW + 2)
                    : sectionX;
            int y = narrow
                    ? sectionY + index / 4 * 18
                    : sectionY + index * 20;
            Button button = FutureShopsButton.styled(
                    sectionButtonLabel(section),
                    ignored -> switchSection(section))
                    .bounds(x, y, sectionW, 18).build();
            button.setTooltip(Tooltip.create(
                    sectionHelp(section)));
            addRenderableWidget(button);
        }
        if (narrow) {
            contentTop = sectionY + 40;
        }
        if (staleReviewing && staleSnapshot != null) {
            buildContentWidgets(this::buildStaleReviewActions);
        } else {
            buildSectionResetButton();
            buildContentWidgets(this::buildSection);
        }
        buildFooter();
        buildValidationNavigation();
        bindings.stream().filter(binding ->
                binding.path().equals(draft.focusedPath()))
                .findFirst().ifPresent(binding ->
                setFocused(binding.field()));
        ShopClientPacketHandler.takeAdminOfferSaveResult(
                pendingRequestId).ifPresent(this::applySaveResult);
        buildEditorModeButton();
    }

    private void initSimpleEditor() {
        int margin = 14;
        summaryWidth = 0;
        summaryLeft = width;
        contentLeft = margin;
        contentTop = 66;
        contentWidth = Math.max(120, width - margin * 2);
        buildSimpleNavigation();
        buildContentWidgets(this::buildSimpleStep);
        buildFooter();
        buildEditorModeButton();
        bindings.stream().filter(binding ->
                        binding.path().equals(draft.focusedPath()))
                .findFirst().ifPresent(binding ->
                        setFocused(binding.field()));
        ShopClientPacketHandler.takeAdminOfferSaveResult(
                pendingRequestId).ifPresent(this::applySaveResult);
    }

    private void buildSimpleNavigation() {
        int gap = 4;
        int available = width - 28;
        int buttonWidth = Math.max(
                52, (available - gap * 3) / 4);
        SimpleStep[] steps = SimpleStep.values();
        for (int index = 0; index < steps.length; index++) {
            SimpleStep step = steps[index];
            Button button = FutureShopsButton.styled(
                    Component.translatable(
                            "gui.futureshops.offer_editor.simple.step."
                                    + step.key()),
                    ignored -> switchSimpleStep(step))
                    .bounds(14 + index * (buttonWidth + gap),
                            36, buttonWidth, 22).build();
            button.active = simpleStep != step
                    && (templateChosen || step == SimpleStep.BASICS);
            button.setTooltip(Tooltip.create(Component.translatable(
                    "gui.futureshops.offer_editor.simple.help.step."
                            + step.key())));
            addRenderableWidget(button);
        }
    }

    private void buildEditorModeButton() {
        String key = advancedMode
                ? "simple_mode" : "advanced_mode";
        int buttonWidth = Math.min(112,
                Math.max(82, width / 5));
        Button button = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor." + key),
                ignored -> toggleEditorMode())
                .bounds(width - buttonWidth - 10, 8,
                        buttonWidth, 20).build();
        button.setTooltip(Tooltip.create(Component.translatable(
                "gui.futureshops.offer_editor.help." + key)));
        addRenderableWidget(button);
    }

    private void toggleEditorMode() {
        flushFields();
        advancedMode = !advancedMode;
        simpleScrollPosition = 0;
        if (advancedMode) {
            templateChosen = true;
            draft.section(OfferEditorDraft.Section.GENERAL);
            draft.scrollPosition(0);
        }
        rebuildWidgets();
    }

    private void switchSimpleStep(SimpleStep step) {
        flushFields();
        simpleStep = step;
        simpleScrollPosition = 0;
        rebuildWidgets();
    }

    private void buildSimpleStep() {
        switch (simpleStep) {
            case BASICS -> buildSimpleBasics();
            case ITEMS -> buildSimpleItems();
            case TRADE -> buildSimpleTrade();
            case REVIEW -> buildSimpleReview();
        }
    }

    private void buildSimpleBasics() {
        if (!templateChosen) {
            buildSimpleTemplateChooser();
            return;
        }
        ServerShopOfferListing listing = draft.candidate();
        int y = simpleY(42);
        addSimpleTextField(
                "displayName", listing.displayName(), y,
                "name", value -> mutateGeneral(
                        "displayName", value,
                        null, null, null, null));
        addSimpleTextField(
                "description", listing.description(), y + 42,
                "description", value -> mutateGeneral(
                        "description", null,
                        value, null, null, null));
        Button category = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.simple.category",
                        categoryName(listing.categoryId())),
                ignored -> openCategoryPicker())
                .bounds(contentLeft, y + 84,
                        Math.min(260, contentWidth), 22).build();
        category.setTooltip(help("select_category"));
        addRenderableWidget(category);
        Button active = FutureShopsButton.styled(
                Component.translatable(listing.active()
                        ? "gui.futureshops.offer_editor.simple.active"
                        : "gui.futureshops.offer_editor.simple.inactive"),
                ignored -> mutateAndRebuild(() ->
                        mutateGeneral("active", null, null,
                                null, null, !draft.candidate().active())))
                .bounds(contentLeft, y + 114,
                        Math.min(180, contentWidth), 22).build();
        active.setTooltip(help("active"));
        addRenderableWidget(active);
        if (!creating && playerShopTarget == null) {
            int actionWidth = Math.min(
                    150, Math.max(90, (contentWidth - 6) / 2));
            Button duplicate = FutureShopsButton.styled(
                    Component.translatable(
                            "gui.futureshops.offer_editor.duplicate"),
                    ignored -> submitDuplicate())
                    .bounds(contentLeft, y + 150,
                            actionWidth, 22).build();
            duplicate.setTooltip(help("duplicate"));
            addRenderableWidget(duplicate);
            Button remove = FutureShopsButton.styled(
                    Component.translatable(
                            "gui.futureshops.offer_editor.remove_listing"),
                    ignored -> confirmRemove())
                    .bounds(contentLeft + actionWidth + 6,
                            y + 150, actionWidth, 22).build();
            remove.setTooltip(help("remove_listing"));
            addRenderableWidget(remove);
        }
    }

    private void buildSimpleTemplateChooser() {
        int columns = contentWidth < 300 ? 1 : 2;
        int gap = 6;
        int buttonWidth = Math.max(100,
                (Math.min(contentWidth, 600)
                        - gap * (columns - 1)) / columns);
        OfferEditorTemplates.Template[] templates = {
                OfferEditorTemplates.Template.MONEY,
                OfferEditorTemplates.Template.FREE,
                OfferEditorTemplates.Template.SELL,
                OfferEditorTemplates.Template.BUY_AND_SELL,
                OfferEditorTemplates.Template.BARTER,
                OfferEditorTemplates.Template.MONEY_OR_BARTER,
                OfferEditorTemplates.Template.MONEY_AND_BARTER,
                OfferEditorTemplates.Template.BUNDLE
        };
        for (int index = 0; index < templates.length; index++) {
            OfferEditorTemplates.Template template = templates[index];
            int x = contentLeft + index % columns
                    * (buttonWidth + gap);
            int y = simpleY(48 + index / columns * 30);
            Button button = FutureShopsButton.styled(
                    Component.translatable(
                            "gui.futureshops.offer_editor.template."
                                    + template.key()),
                    ignored -> applySimpleTemplate(template))
                    .bounds(x, y, buttonWidth, 24).build();
            button.setTooltip(Tooltip.create(Component.translatable(
                    "gui.futureshops.offer_editor.help.template."
                            + template.key())));
            addRenderableWidget(button);
        }
    }

    private void applySimpleTemplate(
            OfferEditorTemplates.Template template
    ) {
        flushFields();
        OfferItemComponent held = heldComponent("output");
        draft.replace("template." + template.key(),
                OfferEditorTemplates.apply(
                        draft.candidate(), template,
                        Optional.ofNullable(held)));
        templateChosen = true;
        simpleStep = SimpleStep.ITEMS;
        simpleScrollPosition = 0;
        synchronizeSimpleIdentity();
        rebuildWidgets();
    }

    private void buildSimpleItems() {
        boolean sellInputs = simpleEditsSellInputs();
        int y = simpleY(44);
        int pickerWidth = Math.max(
                72, (Math.min(contentWidth, 420) - 8) / 3);
        Button held = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.simple.add_held"),
                ignored -> mutateStructureAndRebuild(
                        sellInputs ? "sellOptions.0.itemInputs"
                                : "outputs",
                        this::addSimpleHeldItem))
                .bounds(contentLeft, y, pickerWidth, 22).build();
        held.setTooltip(help(sellInputs ? "sell_input" : "output"));
        addRenderableWidget(held);
        Button inventory = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.pick_inventory"),
                ignored -> openItemPicker(
                        OfferEditorItemPickerScreen.Source.INVENTORY,
                        sellInputs ? "sell_input" : "output",
                        sellInputs ? this::acceptSimpleSellInput
                                : this::acceptSimpleOutput))
                .bounds(contentLeft + pickerWidth + 4,
                        y, pickerWidth, 22).build();
        inventory.setTooltip(help("pick_inventory"));
        addRenderableWidget(inventory);
        Button registry = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.search_registry"),
                ignored -> openItemPicker(
                        OfferEditorItemPickerScreen.Source.REGISTRY,
                        sellInputs ? "sell_input" : "output",
                        sellInputs ? this::acceptSimpleSellInput
                                : this::acceptSimpleOutput))
                .bounds(contentLeft + (pickerWidth + 4) * 2,
                        y, pickerWidth, 22).build();
        registry.setTooltip(help("search_registry"));
        addRenderableWidget(registry);
        List<OfferItemComponent> components =
                simpleEditedComponents();
        for (int index = 0; index < components.size(); index++) {
            buildSimpleComponentControls(
                    index, simpleY(82 + index * 36),
                    sellInputs ? SimpleComponentList.SELL_INPUTS
                            : SimpleComponentList.OUTPUTS);
        }
    }

    private void buildSimpleTrade() {
        OfferEditorSimpleMode.Mode current =
                OfferEditorSimpleMode.detect(draft.candidate());
        OfferEditorSimpleMode.Mode[] modes = {
                OfferEditorSimpleMode.Mode.MONEY,
                OfferEditorSimpleMode.Mode.FREE,
                OfferEditorSimpleMode.Mode.BARTER,
                OfferEditorSimpleMode.Mode.MONEY_OR_BARTER,
                OfferEditorSimpleMode.Mode.MONEY_AND_BARTER,
                OfferEditorSimpleMode.Mode.SELL_ONLY,
                OfferEditorSimpleMode.Mode.BUY_AND_SELL
        };
        int columns = contentWidth < 430 ? 1 : 2;
        int gap = 6;
        int buttonWidth = Math.max(110,
                (Math.min(contentWidth, 560)
                        - gap * (columns - 1)) / columns);
        for (int index = 0; index < modes.length; index++) {
            OfferEditorSimpleMode.Mode mode = modes[index];
            int x = contentLeft + index % columns
                    * (buttonWidth + gap);
            int y = simpleY(42 + index / columns * 28);
            Button button = FutureShopsButton.styled(
                    Component.translatable(
                            "gui.futureshops.offer_editor.simple.mode."
                                    + mode.key()),
                    ignored -> applySimpleMode(mode))
                    .bounds(x, y, buttonWidth, 22).build();
            button.active = current != mode;
            button.setTooltip(Tooltip.create(Component.translatable(
                    "gui.futureshops.offer_editor.simple.help.mode."
                            + mode.key())));
            addRenderableWidget(button);
        }
        int rows = (modes.length + columns - 1) / columns;
        int detailY = 42 + rows * 28 + 18;
        if (current == OfferEditorSimpleMode.Mode.ADVANCED) {
            Button advanced = FutureShopsButton.styled(
                    Component.translatable(
                            "gui.futureshops.offer_editor.advanced_mode"),
                    ignored -> toggleEditorMode())
                    .bounds(contentLeft, simpleY(detailY),
                            Math.min(220, contentWidth), 22).build();
            addRenderableWidget(advanced);
            return;
        }
        int moneyIndex = simpleMoneyOptionIndex();
        if (moneyIndex >= 0) {
            AcquireOfferOption money = draft.candidate()
                    .acquireOptions().get(moneyIndex);
            selectedAcquireIndex = moneyIndex;
            addSimpleTextField(
                    "acquireOptions." + moneyIndex + ".moneyCost",
                    ShopUiUtil.formatMinorUnits(
                            money.moneyCostMinorUnits()),
                    simpleY(detailY), "price",
                    value -> {
                        selectedAcquireIndex = moneyIndex;
                        updateAcquireMoney(
                                parseMoneyMinor(value, -1L));
                    });
            detailY += 44;
        }
        if (!draft.candidate().sellOptions().isEmpty()) {
            selectedSellIndex = 0;
            SellOfferOption sell =
                    draft.candidate().sellOptions().get(0);
            addSimpleTextField(
                    "sellOptions.0.moneyPayout",
                    ShopUiUtil.formatMinorUnits(
                            sell.moneyPayoutMinorUnits()),
                    simpleY(detailY), "payout",
                    value -> {
                        selectedSellIndex = 0;
                        updateSellPayout(
                                parseMoneyMinor(value, -1L));
                    });
            detailY += 44;
        }
        int barterIndex = simpleBarterOptionIndex();
        if (barterIndex >= 0) {
            buildSimpleBarterEditor(barterIndex, detailY);
        }
    }

    private void buildSimpleBarterEditor(
            int optionIndex,
            int offset
    ) {
        selectedAcquireIndex = optionIndex;
        int y = simpleY(offset);
        int pickerWidth = Math.max(
                72, (Math.min(contentWidth, 420) - 8) / 3);
        Button held = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.simple.add_held_cost"),
                ignored -> mutateStructureAndRebuild(
                        "acquireOptions." + optionIndex + ".itemCosts",
                        () -> {
                            selectedAcquireIndex = optionIndex;
                            addHeldAcquireCost();
                        }))
                .bounds(contentLeft, y, pickerWidth, 22).build();
        held.setTooltip(help("item_cost"));
        addRenderableWidget(held);
        Button inventory = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.pick_inventory"),
                ignored -> {
                    selectedAcquireIndex = optionIndex;
                    openItemPicker(
                            OfferEditorItemPickerScreen.Source.INVENTORY,
                            "payment", this::acceptAcquireCost);
                }).bounds(contentLeft + pickerWidth + 4,
                        y, pickerWidth, 22).build();
        inventory.setTooltip(help("pick_inventory"));
        addRenderableWidget(inventory);
        Button registry = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.search_registry"),
                ignored -> {
                    selectedAcquireIndex = optionIndex;
                    openItemPicker(
                            OfferEditorItemPickerScreen.Source.REGISTRY,
                            "payment", this::acceptAcquireCost);
                }).bounds(contentLeft + (pickerWidth + 4) * 2,
                        y, pickerWidth, 22).build();
        registry.setTooltip(help("search_registry"));
        addRenderableWidget(registry);
        List<OfferItemComponent> costs = draft.candidate()
                .acquireOptions().get(optionIndex).itemCosts();
        for (int index = 0; index < costs.size(); index++) {
            buildSimpleComponentControls(
                    index, simpleY(offset + 38 + index * 36),
                    SimpleComponentList.BARTER_COSTS);
        }
    }

    private void buildSimpleReview() {
        if (draft.candidate().outputs().size() > 1) {
            Button compare = FutureShopsButton.styled(
                    Component.translatable(
                            "gui.futureshops.offer_editor.simple."
                                    + "find_bundle_prices"),
                    ignored -> mutateAndRebuild(
                            this::autoConfigureBundleComparisons))
                    .bounds(contentLeft, simpleY(42),
                            Math.min(230, contentWidth), 22).build();
            compare.setTooltip(help("bundle_comparison"));
            addRenderableWidget(compare);
        }
    }

    private void buildSimpleComponentControls(
            int index,
            int y,
            SimpleComponentList list
    ) {
        int right = contentLeft + contentWidth;
        Button decrease = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.decrease_count"),
                ignored -> mutateAndRebuild(() ->
                        changeSimpleComponentCount(
                                list, index, -1)))
                .bounds(right - 102, y + 3, 24, 22).build();
        decrease.active = simpleComponents(list)
                .get(index).count() > 1;
        decrease.setTooltip(help("decrease_component_count"));
        addRenderableWidget(decrease);
        Button increase = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.increase_count"),
                ignored -> mutateAndRebuild(() ->
                        changeSimpleComponentCount(
                                list, index, 1)))
                .bounds(right - 74, y + 3, 24, 22).build();
        increase.setTooltip(help("increase_component_count"));
        addRenderableWidget(increase);
        Button remove = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.simple.remove_item"),
                ignored -> mutateStructureAndRebuild(
                        simpleComponentPath(list),
                        () -> removeSimpleComponent(list, index)))
                .bounds(right - 46, y + 3, 36, 22).build();
        remove.setTooltip(help("remove_component"));
        addRenderableWidget(remove);
    }

    private void addSimpleTextField(
            String path,
            String value,
            int y,
            String labelKey,
            Consumer<String> save
    ) {
        addTextField(path, value, y, save);
        EditBinding binding = bindings.get(bindings.size() - 1);
        simpleFieldLabels.put(binding.field(),
                Component.translatable(
                        "gui.futureshops.offer_editor.simple.field."
                                + labelKey));
    }

    private void applySimpleMode(
            OfferEditorSimpleMode.Mode mode
    ) {
        flushFields();
        draft.replace("simple.mode." + mode.key(),
                OfferEditorSimpleMode.apply(
                        draft.candidate(), mode));
        templateChosen = true;
        selectedAcquireIndex = 0;
        selectedAcquireCostIndex = 0;
        selectedSellIndex = 0;
        selectedSellInputIndex = 0;
        synchronizeSimpleIdentity();
        rebuildWidgets();
    }

    private boolean simpleEditsSellInputs() {
        OfferEditorSimpleMode.Mode mode =
                OfferEditorSimpleMode.detect(draft.candidate());
        return mode == OfferEditorSimpleMode.Mode.SELL_ONLY
                || draft.candidate().outputs().isEmpty()
                && !draft.candidate().sellOptions().isEmpty();
    }

    private List<OfferItemComponent> simpleEditedComponents() {
        return simpleEditsSellInputs()
                ? simpleComponents(SimpleComponentList.SELL_INPUTS)
                : simpleComponents(SimpleComponentList.OUTPUTS);
    }

    private List<OfferItemComponent> simpleComponents(
            SimpleComponentList list
    ) {
        return switch (list) {
            case OUTPUTS -> draft.candidate().outputs();
            case SELL_INPUTS -> draft.candidate().sellOptions()
                    .stream().findFirst()
                    .map(SellOfferOption::itemInputs)
                    .orElse(List.of());
            case BARTER_COSTS -> {
                int index = simpleBarterOptionIndex();
                yield index < 0 ? List.of()
                        : draft.candidate().acquireOptions()
                        .get(index).itemCosts();
            }
        };
    }

    private static String simpleComponentPath(
            SimpleComponentList list
    ) {
        return switch (list) {
            case OUTPUTS -> "outputs";
            case SELL_INPUTS -> "sellOptions.0.itemInputs";
            case BARTER_COSTS -> "acquireOptions.itemCosts";
        };
    }

    private void addSimpleHeldItem() {
        if (simpleEditsSellInputs()) {
            selectedSellIndex = 0;
            addHeldSellInput();
        } else {
            addHeldOutput();
        }
        synchronizeSimpleIdentity();
    }

    private void acceptSimpleOutput(OfferItemComponent component) {
        acceptOutput(component);
        synchronizeSimpleIdentity();
    }

    private void acceptSimpleSellInput(OfferItemComponent component) {
        selectedSellIndex = 0;
        acceptSellInput(component);
        synchronizeSimpleIdentity();
    }

    private void changeSimpleComponentCount(
            SimpleComponentList list,
            int index,
            int delta
    ) {
        List<OfferItemComponent> values =
                new ArrayList<>(simpleComponents(list));
        if (index < 0 || index >= values.size()) {
            return;
        }
        OfferItemComponent old = values.get(index);
        int count;
        try {
            count = Math.addExact(old.count(), delta);
        } catch (ArithmeticException exception) {
            return;
        }
        if (count < 1) {
            return;
        }
        values.set(index, new OfferItemComponent(
                old.componentId(), old.itemId(),
                count, old.exactNbt()));
        replaceSimpleComponents(list, values);
    }

    private void removeSimpleComponent(
            SimpleComponentList list,
            int index
    ) {
        List<OfferItemComponent> values =
                new ArrayList<>(simpleComponents(list));
        if (index < 0 || index >= values.size()) {
            return;
        }
        values.remove(index);
        replaceSimpleComponents(list, values);
        synchronizeSimpleIdentity();
    }

    private void replaceSimpleComponents(
            SimpleComponentList list,
            List<OfferItemComponent> values
    ) {
        switch (list) {
            case OUTPUTS -> {
                draft.clearFieldValues("outputs");
                draft.update("outputs", current -> copy(
                        current, null, null, null, null, null,
                        values, null, null, null));
            }
            case SELL_INPUTS -> {
                List<SellOfferOption> options = new ArrayList<>(
                        draft.candidate().sellOptions());
                if (options.isEmpty()) {
                    return;
                }
                SellOfferOption old = options.get(0);
                options.set(0, new SellOfferOption(
                        old.optionId(), old.label(), values,
                        old.moneyPayoutMinorUnits(), old.capacity(),
                        old.limits(), old.schedule(),
                        old.permissionNode()));
                draft.clearFieldValues(
                        "sellOptions.0.itemInputs");
                draft.update("sellOptions.0.itemInputs",
                        current -> copy(current,
                                null, null, null, null, null,
                                null, null, options, null));
            }
            case BARTER_COSTS -> {
                int optionIndex = simpleBarterOptionIndex();
                if (optionIndex < 0) {
                    return;
                }
                List<AcquireOfferOption> options =
                        new ArrayList<>(
                                draft.candidate().acquireOptions());
                AcquireOfferOption old = options.get(optionIndex);
                options.set(optionIndex, new AcquireOfferOption(
                        old.optionId(), old.label(), old.free(),
                        old.moneyCostPresent(),
                        old.moneyCostMinorUnits(), values,
                        old.outputMultiplier(), old.limits(),
                        old.schedule(), old.permissionNode()));
                draft.clearFieldValues(
                        "acquireOptions." + optionIndex
                                + ".itemCosts");
                draft.update("acquireOptions." + optionIndex
                                + ".itemCosts",
                        current -> copy(current,
                                null, null, null, null, null,
                                null, options, null, null));
            }
        }
    }

    private int simpleMoneyOptionIndex() {
        List<AcquireOfferOption> options =
                draft.candidate().acquireOptions();
        for (int index = 0; index < options.size(); index++) {
            if (options.get(index).moneyCostPresent()) {
                return index;
            }
        }
        return -1;
    }

    private int simpleBarterOptionIndex() {
        List<AcquireOfferOption> options =
                draft.candidate().acquireOptions();
        for (int index = 0; index < options.size(); index++) {
            AcquireOfferOption option = options.get(index);
            if (!option.free()
                    && (option.hasItemCosts()
                    || !option.moneyCostPresent()
                    || option.optionId().contains("barter"))) {
                return index;
            }
        }
        return -1;
    }

    private void synchronizeSimpleIdentity() {
        ServerShopOfferListing listing = draft.candidate();
        OfferItemComponent primary = !listing.outputs().isEmpty()
                ? listing.outputs().get(0)
                : listing.sellOptions().stream()
                .flatMap(option -> option.itemInputs().stream())
                .findFirst().orElse(null);
        if (primary == null) {
            return;
        }
        String name = listing.displayName();
        if (name.isBlank()) {
            ResourceLocation identifier =
                    ResourceLocation.tryParse(primary.itemId());
            var item = identifier == null ? null
                    : ForgeRegistries.ITEMS.getValue(identifier);
            name = item == null ? primary.itemId()
                    : item.getDescription().getString();
        }
        String resolvedName = name;
        draft.update("simple.identity", current ->
                new ServerShopOfferListing(
                        current.listingId(), current.revision(),
                        resolvedName, current.description(),
                        current.categoryId(), primary.itemId(),
                        primary.exactNbt(), current.active(),
                        current.expiresAtEpoch(),
                        current.permissionNode(), current.outputs(),
                        current.acquireOptions(),
                        current.sellOptions(),
                        current.stockPolicy(), current.limits(),
                        current.schedule(),
                        current.bundleComparisons()));
    }

    private void autoConfigureBundleComparisons() {
        ServerShopOfferListing listing = draft.candidate();
        List<OfferBundleComparison> comparisons =
                new ArrayList<>();
        for (OfferItemComponent output : listing.outputs()) {
            OfferBundleComparison comparison =
                    ShopClientState.getCatalogOffers().stream()
                    .filter(candidate -> !candidate.listingId()
                            .equals(listing.listingId()))
                    .filter(candidate -> candidate.active()
                            && candidate.outputs().size() == 1)
                    .filter(candidate -> {
                        OfferItemComponent component =
                                candidate.outputs().get(0);
                        return component.itemId().equals(output.itemId())
                                && component.count() == output.count()
                                && component.exactNbt().equals(
                                output.exactNbt());
                    })
                    .flatMap(candidate ->
                            candidate.acquireOptions().stream()
                                    .filter(option ->
                                            option.moneyCostPresent()
                                                    && !option.free()
                                                    && !option
                                                    .hasItemCosts())
                                    .map(option ->
                                            new OfferBundleComparison(
                                                    output.componentId(),
                                                    candidate.listingId(),
                                                    option.optionId())))
                    .findFirst().orElse(null);
            if (comparison != null) {
                comparisons.add(comparison);
            }
        }
        draft.update("bundleComparisons", current -> copy(
                current, null, null, null, null, null,
                null, null, null, comparisons));
        boolean complete = comparisons.size()
                == listing.outputs().size();
        resultSuccess = complete;
        resultMessage = Component.translatable(
                complete
                        ? "gui.futureshops.offer_editor.simple."
                        + "bundle_prices_found"
                        : "gui.futureshops.offer_editor.simple."
                        + "bundle_prices_missing");
    }

    private int simpleY(int offset) {
        int statusOffset = resultMessage == null ? 0 : 14;
        return contentTop + offset + statusOffset
                - simpleScrollPosition;
    }

    private int simpleViewportTop() {
        int statusOffset = resultMessage == null ? 0 : 14;
        return contentTop + 30 + statusOffset;
    }

    private int maximumSimpleScroll() {
        int componentRows = switch (simpleStep) {
            case ITEMS -> simpleEditedComponents().size();
            case TRADE -> {
                int barter = simpleBarterOptionIndex();
                yield barter < 0 ? 0 : draft.candidate()
                        .acquireOptions().get(barter)
                        .itemCosts().size();
            }
            default -> 0;
        };
        int contentHeight = switch (simpleStep) {
            case BASICS -> templateChosen ? 240 : 310;
            case ITEMS -> 120 + componentRows * 36;
            case TRADE -> 360 + componentRows * 36;
            case REVIEW -> 420;
        };
        int statusOffset = resultMessage == null ? 0 : 14;
        return Math.max(0, contentHeight + statusOffset
                - Math.max(100, footerTop() - contentTop - 8));
    }

    @Override
    protected <T extends GuiEventListener & Renderable
            & NarratableEntry> T addRenderableWidget(T widget) {
        T added = super.addRenderableWidget(widget);
        if (addingContentWidgets
                && widget instanceof AbstractWidget abstractWidget) {
            abstractWidget.visible = contentWidgetVisible(
                    abstractWidget);
        }
        return added;
    }

    private void buildContentWidgets(Runnable builder) {
        addingContentWidgets = true;
        try {
            builder.run();
        } finally {
            addingContentWidgets = false;
        }
    }

    private boolean contentWidgetVisible(AbstractWidget widget) {
        int top = advancedMode
                ? contentTop + 22 : simpleViewportTop() + 9;
        int bottom = footerTop() - 4;
        return widget.getY() >= top
                && widget.getY() + widget.getHeight() <= bottom;
    }

    private Component sectionButtonLabel(
            OfferEditorDraft.Section section
    ) {
        long errors = draft.issues(section).stream()
                .filter(issue -> issue.severity()
                        == com.enviouse.futureshops.catalog.offer
                        .OfferValidationIssue.Severity.ERROR)
                .count();
        return errors == 0L ? sectionLabel(section)
                : Component.translatable(
                "gui.futureshops.offer_editor.section_error_badge",
                sectionLabel(section), errors);
    }

    private void buildValidationNavigation() {
        List<com.enviouse.futureshops.catalog.offer.OfferValidationIssue>
                issues = draft.issues().stream()
                .filter(issue -> issue.severity()
                        == com.enviouse.futureshops.catalog.offer
                        .OfferValidationIssue.Severity.ERROR)
                .limit(summaryWidth == 0 ? 1 : 6)
                .toList();
        if (issues.isEmpty()) {
            return;
        }
        int x = summaryWidth == 0 ? Math.max(10, width - 126)
                : summaryLeft + 6;
        int y = summaryWidth == 0 ? 6 : contentTop + 126;
        int buttonWidth = summaryWidth == 0 ? 116
                : summaryWidth - 12;
        for (int index = 0; index < issues.size(); index++) {
            var issue = issues.get(index);
            Component message = validationMessage(issue);
            Button button = FutureShopsButton.styled(
                    Component.literal(font.plainSubstrByWidth(
                            message.getString(),
                            buttonWidth - 8)),
                    ignored -> focusIssue(issue))
                    .bounds(x, y + index * 22,
                            buttonWidth, 20).build();
            button.setTooltip(Tooltip.create(message));
            addRenderableWidget(button);
        }
    }

    private void focusIssue(
            com.enviouse.futureshops.catalog.offer.OfferValidationIssue
                    issue
    ) {
        flushFields();
        draft.section(OfferEditorDraft.Section.forPath(issue.path()));
        draft.scrollPosition(scrollForPath(issue.path()));
        draft.focusedPath(issue.path());
        rebuild();
    }

    private static int scrollForPath(String path) {
        if (path.contains(".itemCosts.")
                || path.contains(".itemInputs.")) {
            return 360;
        }
        if (path.startsWith("acquireOptions.")
                || path.startsWith("sellOptions.")) {
            return 120;
        }
        return 0;
    }

    private void buildTemplateChooser() {
        int columns = width < 520 ? 1 : 2;
        int gap = 8;
        int buttonWidth = Math.min(250,
                (contentWidth - gap * (columns - 1)) / columns);
        OfferEditorTemplates.Template[] templates =
                OfferEditorTemplates.Template.values();
        for (int index = 0; index < templates.length; index++) {
            OfferEditorTemplates.Template template = templates[index];
            int x = contentLeft + index % columns
                    * (buttonWidth + gap);
            int y = contentTop + 26 + index / columns * 28;
            Button button = FutureShopsButton.styled(
                    Component.translatable(
                            "gui.futureshops.offer_editor.template."
                                    + template.key()),
                    ignored -> applyTemplate(template))
                    .bounds(x, y, buttonWidth, 22).build();
            button.setTooltip(Tooltip.create(Component.translatable(
                    "gui.futureshops.offer_editor.help.template."
                            + template.key())));
            addRenderableWidget(button);
        }
    }

    private void applyTemplate(
            OfferEditorTemplates.Template template
    ) {
        OfferItemComponent held = heldComponent("output_1");
        if (template != OfferEditorTemplates.Template.ADVANCED
                && held == null) {
            resultMessage = Component.translatable(
                    "gui.futureshops.offer_editor.empty_hand");
            resultSuccess = false;
            return;
        }
        draft.replace("template." + template.key(),
                OfferEditorTemplates.apply(
                        draft.candidate(), template,
                        Optional.ofNullable(held)));
        templateChosen = true;
        draft.section(OfferEditorDraft.Section.GENERAL);
        rebuild();
    }

    private void buildSection() {
        switch (draft.section()) {
            case GENERAL -> buildGeneral();
            case OUTPUTS -> buildOutputs();
            case GET_OPTIONS -> buildAcquireOptions();
            case SELL_OPTIONS -> buildSellOptions();
            case STOCK_AND_LIMITS -> buildStockAndLimits();
            case SCHEDULE_AND_PERMISSIONS -> buildSchedule();
            case BUNDLE_VALUE -> buildBundleValue();
            case PREVIEW -> buildPreviewControls();
        }
    }

    private void buildSectionResetButton() {
        int buttonWidth = Math.min(104, Math.max(72, contentWidth / 3));
        Button reset = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.reset_section"),
                ignored -> confirmResetSection())
                .bounds(contentLeft + contentWidth - buttonWidth,
                        contentTop, buttonWidth, 18).build();
        reset.active = pendingRequestId == null
                && draft.sectionDirty(draft.section());
        reset.setTooltip(help("reset_section"));
        addRenderableWidget(reset);
    }

    private void buildStaleReviewActions() {
        int gap = 4;
        int buttonWidth = Math.max(
                72, Math.min(160, (contentWidth - gap) / 2));
        Button local = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.back_to_local_draft"),
                ignored -> {
                    staleReviewing = false;
                    rebuild();
                }).bounds(contentLeft, contentTop + 24,
                buttonWidth, 20).build();
        local.setTooltip(help("back_to_local_draft"));
        addRenderableWidget(local);
        Button reload = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.reload_server"),
                ignored -> confirmReloadServer())
                .bounds(contentLeft + buttonWidth + gap,
                        contentTop + 24, buttonWidth, 20).build();
        reload.setTooltip(help("reload_server"));
        addRenderableWidget(reload);
    }

    private void buildPreviewControls() {
        int y = sectionY(28);
        int buttonWidth = Math.max(
                60, Math.min(140, (contentWidth - 8) / 3));
        PreviewMode[] modes = PreviewMode.values();
        for (int index = 0; index < modes.length; index++) {
            PreviewMode mode = modes[index];
            Button button = FutureShopsButton.styled(
                    Component.translatable(
                            "gui.futureshops.offer_editor.preview_mode."
                                    + mode.key()),
                    ignored -> mutateAndRebuild(
                            () -> previewMode = mode))
                    .bounds(contentLeft
                                    + index * (buttonWidth + 4),
                            y, buttonWidth, 20).build();
            button.active = previewMode != mode;
            button.setTooltip(help("preview_mode"));
            addRenderableWidget(button);
        }
        Button state = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.preview_state",
                        Component.translatable(
                                "gui.futureshops.offer.preview.state."
                                        + previewState.key())),
                ignored -> mutateAndRebuild(() -> {
                    previewState = previewState.next();
                })).bounds(contentLeft, y + 24,
                Math.min(220, contentWidth), 20).build();
        state.setTooltip(help("preview_state"));
        addRenderableWidget(state);
    }

    private void buildGeneral() {
        ServerShopOfferListing listing = draft.candidate();
        int y = sectionY(26);
        addTextField("displayName", listing.displayName(), y,
                value -> mutateGeneral("displayName",
                        value, null, null, null, null));
        addTextField("description", listing.description(), y + 28,
                value -> mutateGeneral("description",
                        null, value, null, null, null));
        Button category = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.category_value",
                        categoryName(listing.categoryId())),
                ignored -> openCategoryPicker())
                .bounds(contentLeft, y + 56,
                        Math.min(220, contentWidth), 20).build();
        category.setTooltip(help("select_category"));
        addRenderableWidget(category);
        addTextField("permission", listing.permissionNode(), y + 84,
                value -> mutateGeneral("permission",
                        null, null, null, value, null));
        addTextField("iconItemId", listing.iconItemId(), y + 112,
                value -> updateIcon(value, null));
        addTextField("iconNbt", listing.iconNbt(), y + 140,
                value -> updateIcon(null, value));
        int pickerWidth = Math.max(54,
                (Math.min(300, contentWidth) - 8) / 3);
        Button heldIcon = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.use_held_icon"),
                ignored -> mutateAndRebuild(this::useHeldIcon))
                .bounds(contentLeft, y + 168,
                        pickerWidth, 20).build();
        heldIcon.setTooltip(help("use_held_icon"));
        addRenderableWidget(heldIcon);
        Button inventoryIcon = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.pick_inventory"),
                ignored -> openItemPicker(
                        OfferEditorItemPickerScreen.Source.INVENTORY,
                        "icon", this::acceptIcon))
                .bounds(contentLeft + pickerWidth + 4,
                        y + 168, pickerWidth, 20).build();
        inventoryIcon.setTooltip(help("pick_inventory"));
        addRenderableWidget(inventoryIcon);
        Button registryIcon = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.search_registry"),
                ignored -> openItemPicker(
                        OfferEditorItemPickerScreen.Source.REGISTRY,
                        "icon", this::acceptIcon))
                .bounds(contentLeft + (pickerWidth + 4) * 2,
                        y + 168, pickerWidth, 20).build();
        registryIcon.setTooltip(help("search_registry"));
        addRenderableWidget(registryIcon);
        Button active = FutureShopsButton.styled(
                Component.translatable(listing.active()
                        ? "gui.futureshops.offer_editor.active"
                        : "gui.futureshops.offer_editor.inactive"),
                ignored -> mutateAndRebuild(() -> {
                    mutateGeneral("active",
                            null, null, null, null,
                            !draft.candidate().active());
                })).bounds(contentLeft, y + 196,
                Math.min(150, contentWidth), 20).build();
        active.setTooltip(help("active"));
        addRenderableWidget(active);
        if (!creating && playerShopTarget == null) {
            Button duplicate = FutureShopsButton.styled(
                    Component.translatable(
                            "gui.futureshops.offer_editor.duplicate"),
                    ignored -> submitDuplicate())
                    .bounds(contentLeft, y + 224,
                            Math.min(120, contentWidth), 20).build();
            duplicate.setTooltip(help("duplicate"));
            addRenderableWidget(duplicate);
            Button remove = FutureShopsButton.styled(
                    Component.translatable(
                            "gui.futureshops.offer_editor.remove_listing"),
                    ignored -> confirmRemove())
                    .bounds(contentLeft + Math.min(126,
                            Math.max(0, contentWidth - 120)), y + 224,
                            Math.min(118, contentWidth), 20).build();
            remove.setTooltip(help("remove_listing"));
            addRenderableWidget(remove);
        }
        if (staleSnapshot != null) {
            Button review = FutureShopsButton.styled(
                    Component.translatable(staleReviewing
                            ? "gui.futureshops.offer_editor.reviewing_changes"
                            : "gui.futureshops.offer_editor.review_changes"),
                    ignored -> reviewStaleChanges())
                    .bounds(contentLeft, y + 252,
                            Math.min(150, contentWidth), 20)
                    .build();
            review.active = !staleReviewing;
            review.setTooltip(help("review_changes"));
            addRenderableWidget(review);
            Button reload = FutureShopsButton.styled(
                    Component.translatable(
                            "gui.futureshops.offer_editor.reload_server"),
                    ignored -> confirmReloadServer())
                    .bounds(contentLeft, y + 276,
                            Math.min(150, contentWidth), 20).build();
            reload.setTooltip(help("reload_server"));
            addRenderableWidget(reload);
        }
    }

    private void buildOutputs() {
        int y = sectionY(28);
        int pickerWidth = Math.max(54,
                (Math.min(300, contentWidth) - 8) / 3);
        Button add = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.add_held_output"),
                ignored -> mutateStructureAndRebuild(
                        "outputs", this::addHeldOutput))
                .bounds(contentLeft, y, pickerWidth, 20).build();
        add.setTooltip(help("output"));
        addRenderableWidget(add);
        Button inventory = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.pick_inventory"),
                ignored -> openItemPicker(
                        OfferEditorItemPickerScreen.Source.INVENTORY,
                        "output", this::acceptOutput))
                .bounds(contentLeft + pickerWidth + 4,
                        y, pickerWidth, 20).build();
        inventory.setTooltip(help("pick_inventory"));
        addRenderableWidget(inventory);
        Button registry = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.search_registry"),
                ignored -> openItemPicker(
                        OfferEditorItemPickerScreen.Source.REGISTRY,
                        "output", this::acceptOutput))
                .bounds(contentLeft + (pickerWidth + 4) * 2,
                        y, pickerWidth, 20).build();
        registry.setTooltip(help("search_registry"));
        addRenderableWidget(registry);
        List<OfferItemComponent> outputs = draft.candidate().outputs();
        if (outputs.isEmpty()) {
            return;
        }
        selectedOutputIndex = Math.min(
                selectedOutputIndex, outputs.size() - 1);
        OfferItemComponent selected = outputs.get(selectedOutputIndex);
        Button previous = FutureShopsButton.styled(Component.literal("<"),
                ignored -> mutateAndRebuild(() -> {
                    selectedOutputIndex = Math.max(
                            0, selectedOutputIndex - 1);
                })).bounds(contentLeft, y + 28, 24, 20).build();
        previous.setTooltip(help("previous_output"));
        addRenderableWidget(previous);
        Button next = FutureShopsButton.styled(Component.literal(">"),
                ignored -> mutateAndRebuild(() -> {
                    selectedOutputIndex = Math.min(
                            outputs.size() - 1,
                            selectedOutputIndex + 1);
                })).bounds(contentLeft + 28, y + 28, 24, 20).build();
        next.setTooltip(help("next_output"));
        addRenderableWidget(next);
        Button duplicate = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.duplicate_output"),
                ignored -> mutateStructureAndRebuild(
                        "outputs", this::duplicateOutput))
                .bounds(contentLeft + 58, y + 28,
                        Math.min(105, Math.max(50,
                                contentWidth - 58)), 20).build();
        duplicate.setTooltip(help("duplicate_output"));
        addRenderableWidget(duplicate);
        Button remove = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.remove_output"),
                ignored -> mutateStructureAndRebuild(
                        "outputs",
                        this::confirmRemoveOutput))
                .bounds(contentLeft + Math.min(169,
                        Math.max(0, contentWidth - 110)), y + 28,
                        Math.min(108, contentWidth), 20).build();
        remove.setTooltip(help("remove_output"));
        addRenderableWidget(remove);
        addTextField("outputs." + selectedOutputIndex + ".itemId",
                selected.itemId(), y + 62,
                this::updateOutputItem);
        addTextField("outputs." + selectedOutputIndex + ".count",
                Integer.toString(selected.count()), y + 90,
                value -> updateOutputCount(parseInt(value, -1)));
        addTextField("outputs." + selectedOutputIndex + ".exactNbt",
                selected.exactNbt(), y + 118,
                this::updateOutputNbt);
        Button up = FutureShopsButton.styled(Component.literal("↑"),
                ignored -> mutateStructureAndRebuild(
                        "outputs",
                        () -> moveOutput(-1)))
                .bounds(contentLeft, y + 148, 28, 20).build();
        up.active = selectedOutputIndex > 0;
        up.setTooltip(help("move_output_up"));
        addRenderableWidget(up);
        Button down = FutureShopsButton.styled(Component.literal("↓"),
                ignored -> mutateStructureAndRebuild(
                        "outputs",
                        () -> moveOutput(1)))
                .bounds(contentLeft + 32, y + 148, 28, 20).build();
        down.active = selectedOutputIndex + 1 < outputs.size();
        down.setTooltip(help("move_output_down"));
        addRenderableWidget(down);
        buildCountStepper(contentLeft + 66, y + 148,
                this::selectedOutputCount,
                this::updateOutputCount);
    }

    private void buildAcquireOptions() {
        int y = sectionY(28);
        String[] types = {"free", "money", "items", "compound"};
        for (int index = 0; index < types.length; index++) {
            String type = types[index];
            Button button = FutureShopsButton.styled(
                    acquireOptionButtonLabel(type),
                    ignored -> mutateAndRebuild(
                            () -> addAcquireOption(type)))
                    .bounds(contentLeft + index
                            * Math.max(52, contentWidth / 4),
                    y, Math.max(48, contentWidth / 4 - 3), 20).build();
            button.setTooltip(help(type));
            addRenderableWidget(button);
        }
        List<AcquireOfferOption> options =
                draft.candidate().acquireOptions();
        if (options.isEmpty()) {
            return;
        }
        selectedAcquireIndex = Math.min(
                selectedAcquireIndex, options.size() - 1);
        AcquireOfferOption selected =
                options.get(selectedAcquireIndex);
        addTextField("acquireOptions." + selectedAcquireIndex + ".label",
                selected.label(), y + 34, this::updateAcquireLabel);
        if (selected.moneyCostPresent()) {
            addTextField("acquireOptions." + selectedAcquireIndex
                            + ".moneyCost",
                    ShopUiUtil.formatMinorUnits(
                            selected.moneyCostMinorUnits()), y + 62,
                    value -> updateAcquireMoney(
                            parseMoneyMinor(value, -1L)));
        }
        addTextField("acquireOptions." + selectedAcquireIndex
                        + ".outputMultiplier",
                Integer.toString(selected.outputMultiplier()), y + 90,
                value -> updateAcquireMultiplier(parseInt(value, -1)));
        addTextField("acquireOptions." + selectedAcquireIndex
                        + ".permission",
                selected.permissionNode(), y + 118,
                this::updateAcquirePermission);
        addTextField("acquireOptions." + selectedAcquireIndex
                        + ".startsAtEpoch",
                Long.toString(selected.schedule().startsAtEpoch()), y + 146,
                value -> updateAcquireSchedule(
                        parseLong(value, -1L), null));
        addTextField("acquireOptions." + selectedAcquireIndex
                        + ".endsAtEpoch",
                Long.toString(selected.schedule().endsAtEpoch()), y + 174,
                value -> updateAcquireSchedule(
                        null, parseLong(value, -1L)));
        addTextField("acquireOptions." + selectedAcquireIndex
                        + ".maximumPerRequest",
                Integer.toString(selected.limits().maximumPerRequest()),
                y + 202, value -> updateAcquireLimits(
                        parseInt(value, -1), null, null, null, null));
        addTextField("acquireOptions." + selectedAcquireIndex
                        + ".lifetime",
                Long.toString(selected.limits().lifetimeLimit()), y + 230,
                value -> updateAcquireLimits(
                        null, parseLong(value, -1L),
                        null, null, null));
        addTextField("acquireOptions." + selectedAcquireIndex
                        + ".periodQuantity",
                Long.toString(selected.limits().periodLimit()), y + 258,
                value -> updateAcquireLimits(
                        null, null, parseLong(value, -1L),
                        null, null));
        addTextField("acquireOptions." + selectedAcquireIndex
                        + ".periodSeconds",
                Long.toString(selected.limits().periodSeconds()), y + 286,
                value -> updateAcquireLimits(
                        null, null, null,
                        parseLong(value, -1L), null));
        addTextField("acquireOptions." + selectedAcquireIndex
                        + ".cooldownSeconds",
                Long.toString(selected.limits().cooldownSeconds()), y + 314,
                value -> updateAcquireLimits(
                        null, null, null, null,
                        parseLong(value, -1L)));
        Button previous = FutureShopsButton.styled(Component.literal("<"),
                ignored -> mutateAndRebuild(() -> {
                    selectedAcquireIndex = Math.max(
                            0, selectedAcquireIndex - 1);
                })).bounds(contentLeft, y + 344, 24, 20).build();
        previous.setTooltip(help("previous_option"));
        addRenderableWidget(previous);
        Button next = FutureShopsButton.styled(Component.literal(">"),
                ignored -> mutateAndRebuild(() -> {
                    selectedAcquireIndex = Math.min(
                            options.size() - 1,
                            selectedAcquireIndex + 1);
                })).bounds(contentLeft + 28, y + 344, 24, 20).build();
        next.setTooltip(help("next_option"));
        addRenderableWidget(next);
        Button duplicate = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.duplicate_option"),
                ignored -> mutateStructureAndRebuild(
                        "acquireOptions",
                        this::duplicateAcquireOption))
                .bounds(contentLeft + 58, y + 344,
                        Math.min(130, Math.max(50,
                                contentWidth - 58)), 20).build();
        duplicate.setTooltip(help("duplicate_option"));
        addRenderableWidget(duplicate);
        Button up = FutureShopsButton.styled(Component.literal("↑"),
                ignored -> mutateStructureAndRebuild(
                        "acquireOptions",
                        () -> moveAcquireOption(-1)))
                .bounds(contentLeft, y + 370, 28, 20).build();
        up.active = selectedAcquireIndex > 0;
        up.setTooltip(help("move_option_up"));
        addRenderableWidget(up);
        Button down = FutureShopsButton.styled(Component.literal("↓"),
                ignored -> mutateStructureAndRebuild(
                        "acquireOptions",
                        () -> moveAcquireOption(1)))
                .bounds(contentLeft + 32, y + 370, 28, 20).build();
        down.active = selectedAcquireIndex + 1 < options.size();
        down.setTooltip(help("move_option_down"));
        addRenderableWidget(down);
        Button remove = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.remove_option"),
                ignored -> mutateStructureAndRebuild(
                        "acquireOptions",
                        this::confirmRemoveAcquireOption))
                .bounds(contentLeft + 66, y + 370,
                        Math.min(130, Math.max(50,
                                contentWidth - 66)), 20).build();
        remove.setTooltip(help("remove_option"));
        addRenderableWidget(remove);
        buildAcquireCostEditor(selected, y + 400);
    }

    private void buildAcquireCostEditor(
            AcquireOfferOption option,
            int y
    ) {
        int pickerWidth = Math.max(54,
                (Math.min(300, contentWidth) - 8) / 3);
        Button held = FutureShopsButton.styled(Component.translatable(
                        "gui.futureshops.offer_editor.add_held_cost"),
                ignored -> mutateStructureAndRebuild(
                        "acquireOptions." + selectedAcquireIndex
                                + ".itemCosts",
                        this::addHeldAcquireCost))
                .bounds(contentLeft, y, pickerWidth, 20).build();
        held.active = !option.free();
        held.setTooltip(help("item_cost"));
        addRenderableWidget(held);
        Button inventory = FutureShopsButton.styled(Component.translatable(
                        "gui.futureshops.offer_editor.pick_inventory"),
                ignored -> openItemPicker(
                        OfferEditorItemPickerScreen.Source.INVENTORY,
                        "payment", this::acceptAcquireCost))
                .bounds(contentLeft + pickerWidth + 4,
                        y, pickerWidth, 20).build();
        inventory.active = !option.free();
        inventory.setTooltip(help("pick_inventory"));
        addRenderableWidget(inventory);
        Button registry = FutureShopsButton.styled(Component.translatable(
                        "gui.futureshops.offer_editor.search_registry"),
                ignored -> openItemPicker(
                        OfferEditorItemPickerScreen.Source.REGISTRY,
                        "payment", this::acceptAcquireCost))
                .bounds(contentLeft + (pickerWidth + 4) * 2,
                        y, pickerWidth, 20).build();
        registry.active = !option.free();
        registry.setTooltip(help("search_registry"));
        addRenderableWidget(registry);
        if (option.itemCosts().isEmpty()) {
            return;
        }
        selectedAcquireCostIndex = Math.min(
                selectedAcquireCostIndex,
                option.itemCosts().size() - 1);
        OfferItemComponent component =
                option.itemCosts().get(selectedAcquireCostIndex);
        addTextField("acquireOptions." + selectedAcquireIndex
                        + ".itemCosts." + selectedAcquireCostIndex
                        + ".itemId",
                component.itemId(), y + 34,
                this::updateAcquireCostItem);
        addTextField("acquireOptions." + selectedAcquireIndex
                        + ".itemCosts." + selectedAcquireCostIndex
                        + ".count",
                Integer.toString(component.count()), y + 62,
                value -> updateAcquireCostCount(
                        parseInt(value, -1)));
        addTextField("acquireOptions." + selectedAcquireIndex
                        + ".itemCosts." + selectedAcquireCostIndex
                        + ".exactNbt",
                component.exactNbt(), y + 90,
                this::updateAcquireCostNbt);
        buildComponentNavigation(
                y + 120, option.itemCosts().size(),
                "acquireOptions." + selectedAcquireIndex
                        + ".itemCosts",
                () -> {
                    selectedAcquireCostIndex = Math.max(
                            0, selectedAcquireCostIndex - 1);
                },
                () -> {
                    selectedAcquireCostIndex = Math.min(
                            option.itemCosts().size() - 1,
                            selectedAcquireCostIndex + 1);
                },
                () -> moveAcquireCost(-1),
                () -> moveAcquireCost(1),
                this::confirmRemoveAcquireCost,
                selectedAcquireCostIndex);
        buildCountStepper(contentLeft, y + 146,
                this::selectedAcquireCostCount,
                this::updateAcquireCostCount);
    }

    private static Component acquireOptionButtonLabel(String type) {
        return switch (type) {
            case "free" -> Component.translatable(
                    "gui.futureshops.offer_editor.add_free");
            case "money" -> Component.translatable(
                    "gui.futureshops.offer_editor.add_money");
            case "items" -> Component.translatable(
                    "gui.futureshops.offer_editor.add_items");
            case "compound" -> Component.translatable(
                    "gui.futureshops.offer_editor.add_compound");
            default -> throw new IllegalArgumentException(
                    "Unknown acquire option type");
        };
    }

    private void buildSellOptions() {
        int y = sectionY(28);
        Button add = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.add_sell_held"),
                ignored -> mutateAndRebuild(this::addSellOption))
                .bounds(contentLeft, y,
                Math.min(150, contentWidth), 20).build();
        add.setTooltip(help("sell_to_shop"));
        addRenderableWidget(add);
        List<SellOfferOption> options =
                draft.candidate().sellOptions();
        if (options.isEmpty()) {
            return;
        }
        selectedSellIndex = Math.min(
                selectedSellIndex, options.size() - 1);
        SellOfferOption selected = options.get(selectedSellIndex);
        addTextField("sellOptions." + selectedSellIndex + ".label",
                selected.label(), y + 34, this::updateSellLabel);
        addTextField("sellOptions." + selectedSellIndex + ".moneyPayout",
                ShopUiUtil.formatMinorUnits(
                        selected.moneyPayoutMinorUnits()),
                y + 62,
                value -> updateSellPayout(
                        parseMoneyMinor(value, -1L)));
        addTextField("sellOptions." + selectedSellIndex + ".capacity",
                Long.toString(selected.capacity()), y + 90,
                value -> updateSellCapacity(parseLong(value, -1L)));
        addTextField("sellOptions." + selectedSellIndex + ".permission",
                selected.permissionNode(), y + 118,
                this::updateSellPermission);
        addTextField("sellOptions." + selectedSellIndex
                        + ".startsAtEpoch",
                Long.toString(selected.schedule().startsAtEpoch()), y + 146,
                value -> updateSellSchedule(parseLong(value, -1L), null));
        addTextField("sellOptions." + selectedSellIndex
                        + ".endsAtEpoch",
                Long.toString(selected.schedule().endsAtEpoch()), y + 174,
                value -> updateSellSchedule(null, parseLong(value, -1L)));
        addTextField("sellOptions." + selectedSellIndex
                        + ".maximumPerRequest",
                Integer.toString(selected.limits().maximumPerRequest()),
                y + 202, value -> updateSellLimits(
                        parseInt(value, -1), null, null, null, null));
        addTextField("sellOptions." + selectedSellIndex + ".lifetime",
                Long.toString(selected.limits().lifetimeLimit()), y + 230,
                value -> updateSellLimits(null,
                        parseLong(value, -1L), null, null, null));
        addTextField("sellOptions." + selectedSellIndex
                        + ".periodQuantity",
                Long.toString(selected.limits().periodLimit()), y + 258,
                value -> updateSellLimits(null, null,
                        parseLong(value, -1L), null, null));
        addTextField("sellOptions." + selectedSellIndex
                        + ".periodSeconds",
                Long.toString(selected.limits().periodSeconds()), y + 286,
                value -> updateSellLimits(null, null, null,
                        parseLong(value, -1L), null));
        addTextField("sellOptions." + selectedSellIndex
                        + ".cooldownSeconds",
                Long.toString(selected.limits().cooldownSeconds()), y + 314,
                value -> updateSellLimits(null, null, null, null,
                        parseLong(value, -1L)));
        Button previous = FutureShopsButton.styled(Component.literal("<"),
                ignored -> mutateAndRebuild(() -> {
                    selectedSellIndex = Math.max(
                            0, selectedSellIndex - 1);
                })).bounds(contentLeft, y + 344, 24, 20).build();
        previous.setTooltip(help("previous_option"));
        addRenderableWidget(previous);
        Button next = FutureShopsButton.styled(Component.literal(">"),
                ignored -> mutateAndRebuild(() -> {
                    selectedSellIndex = Math.min(
                            options.size() - 1,
                            selectedSellIndex + 1);
                })).bounds(contentLeft + 28, y + 344, 24, 20).build();
        next.setTooltip(help("next_option"));
        addRenderableWidget(next);
        Button duplicate = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.duplicate_option"),
                ignored -> mutateStructureAndRebuild(
                        "sellOptions",
                        this::duplicateSellOption))
                .bounds(contentLeft + 58, y + 344,
                        Math.min(130, Math.max(50,
                                contentWidth - 58)), 20).build();
        duplicate.setTooltip(help("duplicate_option"));
        addRenderableWidget(duplicate);
        Button up = FutureShopsButton.styled(Component.literal("↑"),
                ignored -> mutateStructureAndRebuild(
                        "sellOptions",
                        () -> moveSellOption(-1)))
                .bounds(contentLeft, y + 370, 28, 20).build();
        up.active = selectedSellIndex > 0;
        up.setTooltip(help("move_option_up"));
        addRenderableWidget(up);
        Button down = FutureShopsButton.styled(Component.literal("↓"),
                ignored -> mutateStructureAndRebuild(
                        "sellOptions",
                        () -> moveSellOption(1)))
                .bounds(contentLeft + 32, y + 370, 28, 20).build();
        down.active = selectedSellIndex + 1 < options.size();
        down.setTooltip(help("move_option_down"));
        addRenderableWidget(down);
        Button remove = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.remove_option"),
                ignored -> mutateStructureAndRebuild(
                        "sellOptions",
                        this::confirmRemoveSellOption))
                .bounds(contentLeft + 66, y + 370,
                        Math.min(130, Math.max(50,
                                contentWidth - 66)), 20).build();
        remove.setTooltip(help("remove_option"));
        addRenderableWidget(remove);
        buildSellInputEditor(selected, y + 400);
    }

    private void buildSellInputEditor(
            SellOfferOption option,
            int y
    ) {
        int pickerWidth = Math.max(54,
                (Math.min(300, contentWidth) - 8) / 3);
        Button held = FutureShopsButton.styled(Component.translatable(
                        "gui.futureshops.offer_editor.add_held_input"),
                ignored -> mutateStructureAndRebuild(
                        "sellOptions." + selectedSellIndex
                                + ".itemInputs",
                        this::addHeldSellInput))
                .bounds(contentLeft, y, pickerWidth, 20).build();
        held.setTooltip(help("sell_input"));
        addRenderableWidget(held);
        Button inventory = FutureShopsButton.styled(Component.translatable(
                        "gui.futureshops.offer_editor.pick_inventory"),
                ignored -> openItemPicker(
                        OfferEditorItemPickerScreen.Source.INVENTORY,
                        "sell_input", this::acceptSellInput))
                .bounds(contentLeft + pickerWidth + 4,
                        y, pickerWidth, 20).build();
        inventory.setTooltip(help("pick_inventory"));
        addRenderableWidget(inventory);
        Button registry = FutureShopsButton.styled(Component.translatable(
                        "gui.futureshops.offer_editor.search_registry"),
                ignored -> openItemPicker(
                        OfferEditorItemPickerScreen.Source.REGISTRY,
                        "sell_input", this::acceptSellInput))
                .bounds(contentLeft + (pickerWidth + 4) * 2,
                        y, pickerWidth, 20).build();
        registry.setTooltip(help("search_registry"));
        addRenderableWidget(registry);
        if (option.itemInputs().isEmpty()) {
            return;
        }
        selectedSellInputIndex = Math.min(
                selectedSellInputIndex,
                option.itemInputs().size() - 1);
        OfferItemComponent component =
                option.itemInputs().get(selectedSellInputIndex);
        addTextField("sellOptions." + selectedSellIndex
                        + ".itemInputs." + selectedSellInputIndex
                        + ".itemId",
                component.itemId(), y + 34,
                this::updateSellInputItem);
        addTextField("sellOptions." + selectedSellIndex
                        + ".itemInputs." + selectedSellInputIndex
                        + ".count",
                Integer.toString(component.count()), y + 62,
                value -> updateSellInputCount(
                        parseInt(value, -1)));
        addTextField("sellOptions." + selectedSellIndex
                        + ".itemInputs." + selectedSellInputIndex
                        + ".exactNbt",
                component.exactNbt(), y + 90,
                this::updateSellInputNbt);
        buildComponentNavigation(
                y + 120, option.itemInputs().size(),
                "sellOptions." + selectedSellIndex
                        + ".itemInputs",
                () -> {
                    selectedSellInputIndex = Math.max(
                            0, selectedSellInputIndex - 1);
                },
                () -> {
                    selectedSellInputIndex = Math.min(
                            option.itemInputs().size() - 1,
                            selectedSellInputIndex + 1);
                },
                () -> moveSellInput(-1),
                () -> moveSellInput(1),
                this::confirmRemoveSellInput,
                selectedSellInputIndex);
        buildCountStepper(contentLeft, y + 146,
                this::selectedSellInputCount,
                this::updateSellInputCount);
    }

    private void buildComponentNavigation(
            int y,
            int size,
            String pathPrefix,
            Runnable previousAction,
            Runnable nextAction,
            Runnable upAction,
            Runnable downAction,
            Runnable removeAction,
            int index
    ) {
        Button previous = FutureShopsButton.styled(Component.literal("<"),
                ignored -> mutateAndRebuild(previousAction))
                .bounds(contentLeft, y, 24, 20).build();
        previous.active = index > 0;
        previous.setTooltip(help("previous_component"));
        addRenderableWidget(previous);
        Button next = FutureShopsButton.styled(Component.literal(">"),
                ignored -> mutateAndRebuild(nextAction))
                .bounds(contentLeft + 28, y, 24, 20).build();
        next.active = index + 1 < size;
        next.setTooltip(help("next_component"));
        addRenderableWidget(next);
        Button up = FutureShopsButton.styled(Component.literal("↑"),
                ignored -> mutateStructureAndRebuild(
                        pathPrefix, upAction))
                .bounds(contentLeft + 58, y, 28, 20).build();
        up.active = index > 0;
        up.setTooltip(help("move_component_up"));
        addRenderableWidget(up);
        Button down = FutureShopsButton.styled(Component.literal("↓"),
                ignored -> mutateStructureAndRebuild(
                        pathPrefix, downAction))
                .bounds(contentLeft + 90, y, 28, 20).build();
        down.active = index + 1 < size;
        down.setTooltip(help("move_component_down"));
        addRenderableWidget(down);
        Button remove = FutureShopsButton.styled(Component.translatable(
                        "gui.futureshops.offer_editor.remove_component"),
                ignored -> mutateStructureAndRebuild(
                        pathPrefix, removeAction))
                .bounds(contentLeft + 124, y,
                        Math.min(132, Math.max(60,
                                contentWidth - 124)), 20).build();
        remove.setTooltip(help("remove_component"));
        addRenderableWidget(remove);
    }

    private void buildCountStepper(
            int x,
            int y,
            IntSupplier currentCount,
            IntConsumer updateCount
    ) {
        Button decrease = FutureShopsButton.styled(Component.translatable(
                        "gui.futureshops.offer_editor.decrease_count"),
                ignored -> mutateAndRebuild(() ->
                        changeComponentCount(
                                currentCount, updateCount, -1)))
                .bounds(x, y, 28, 20).build();
        decrease.active = currentCount.getAsInt() > 1;
        decrease.setTooltip(help("decrease_component_count"));
        addRenderableWidget(decrease);
        Button increase = FutureShopsButton.styled(Component.translatable(
                        "gui.futureshops.offer_editor.increase_count"),
                ignored -> mutateAndRebuild(() ->
                        changeComponentCount(
                                currentCount, updateCount, 1)))
                .bounds(x + 32, y, 28, 20).build();
        increase.setTooltip(help("increase_component_count"));
        addRenderableWidget(increase);
    }

    private void changeComponentCount(
            IntSupplier currentCount,
            IntConsumer updateCount,
            int direction
    ) {
        int current = currentCount.getAsInt();
        if (direction < 0 && current <= 1) {
            return;
        }
        try {
            updateCount.accept(Math.addExact(current, direction));
        } catch (ArithmeticException exception) {
            resultMessage = Component.translatable(
                    "gui.futureshops.offer_editor.count_overflow");
            resultSuccess = false;
        }
    }

    private void buildStockAndLimits() {
        ServerShopOfferListing listing = draft.candidate();
        int y = sectionY(26);
        addTextField("stock.quantity",
                Long.toString(listing.stockPolicy().quantity()), y,
                value -> updateStock(parseLong(value, -1L), null));
        addTextField("stock.refreshSeconds",
                Long.toString(listing.stockPolicy().refreshSeconds()),
                y + 28,
                value -> updateStock(null, parseLong(value, -1L)));
        OfferLimitPolicy limits = listing.limits();
        addTextField("limits.maximumPerRequest",
                Integer.toString(limits.maximumPerRequest()), y + 56,
                value -> updateLimits(parseInt(value, -1),
                        null, null, null, null));
        addTextField("limits.lifetime",
                Long.toString(limits.lifetimeLimit()), y + 84,
                value -> updateLimits(null, parseLong(value, -1L),
                        null, null, null));
        addTextField("limits.periodQuantity",
                Long.toString(limits.periodLimit()), y + 112,
                value -> updateLimits(null, null,
                        parseLong(value, -1L), null, null));
        addTextField("limits.periodSeconds",
                Long.toString(limits.periodSeconds()), y + 140,
                value -> updateLimits(null, null, null,
                        parseLong(value, -1L), null));
        addTextField("limits.cooldownSeconds",
                Long.toString(limits.cooldownSeconds()), y + 168,
                value -> updateLimits(null, null, null, null,
                        parseLong(value, -1L)));
        Button unlimited = FutureShopsButton.styled(
                Component.translatable(listing.stockPolicy().type()
                        == OfferStockPolicy.Type.UNLIMITED
                        ? "gui.futureshops.offer_editor.unlimited"
                        : "gui.futureshops.offer_editor.limited"),
                ignored -> mutateAndRebuild(this::toggleUnlimited))
                .bounds(contentLeft, y + 196,
                Math.min(150, contentWidth), 20).build();
        unlimited.setTooltip(help("unlimited_stock"));
        addRenderableWidget(unlimited);
    }

    private void buildSchedule() {
        ServerShopOfferListing listing = draft.candidate();
        int y = sectionY(26);
        addTextField("schedule.startsAtEpoch",
                Long.toString(listing.schedule().startsAtEpoch()), y,
                value -> updateSchedule(parseLong(value, -1L), null));
        addTextField("schedule.endsAtEpoch",
                Long.toString(listing.schedule().endsAtEpoch()), y + 28,
                value -> updateSchedule(null, parseLong(value, -1L)));
        addTextField("expiresAtEpoch",
                Long.toString(listing.expiresAtEpoch()), y + 56,
                value -> updateExpiry(parseLong(value, -1L)));
    }

    private void buildBundleValue() {
        int y = sectionY(30);
        Button clear = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.clear_comparisons"),
                ignored -> mutateAndRebuild(() -> {
                    draft.clearFieldValues("bundleComparisons");
                    draft.update("bundleComparisons", current ->
                            copy(current, null, null, null, null, null,
                                    null, null, null, List.of()));
                })).bounds(contentLeft, y,
                Math.min(170, contentWidth), 20).build();
        clear.setTooltip(help("bundle_comparison"));
        addRenderableWidget(clear);
        int row = 0;
        for (OfferItemComponent output : draft.candidate().outputs()) {
            if (row >= 5) {
                break;
            }
            com.enviouse.futureshops.catalog.offer
                    .OfferBundleComparison comparison =
                    draft.candidate().bundleComparisons().stream()
                    .filter(value -> value.componentId().equals(
                            output.componentId()))
                    .findFirst().orElse(null);
            String value = comparison == null ? ""
                    : comparison.listingId() + " "
                    + comparison.optionId();
            String path = "bundleComparisons."
                    + output.componentId();
            int rowY = y + 34 + row * 54;
            addTextField(path, value, rowY,
                    input -> updateComparison(
                            output.componentId(), input));
            Button select = FutureShopsButton.styled(
                    Component.translatable(
                            "gui.futureshops.offer_editor.select_comparison"),
                    ignored -> openBundleComparisonPicker(output))
                    .bounds(contentLeft, rowY + 22,
                            Math.min(180, contentWidth), 20)
                    .build();
            select.setTooltip(help("select_comparison"));
            addRenderableWidget(select);
            row++;
        }
    }

    private void openBundleComparisonPicker(
            OfferItemComponent output
    ) {
        flushFields();
        if (minecraft != null) {
            minecraft.setScreen(
                    new OfferEditorBundleComparisonPickerScreen(
                            this, output,
                            draft.candidate().listingId(),
                            selected -> acceptBundleComparison(
                                    output, selected)));
        }
    }

    private void acceptBundleComparison(
            OfferItemComponent output,
            OfferEditorBundleComparisonPickerScreen.Selection selected
    ) {
        String value = selected.listingId().isBlank()
                ? "" : selected.listingId()
                + " " + selected.optionId();
        updateComparison(output.componentId(), value);
        String path = "bundleComparisons."
                + output.componentId();
        draft.acceptFieldValue(path, value);
        draft.focusedPath(path);
    }

    private void buildFooter() {
        if (width < 430) {
            buildNarrowFooter();
            return;
        }
        int y = footerTop();
        int x = 10;
        addFooterButton(x, y, 62, "revert",
                ignored -> confirmRevert(), "revert");
        x += 66;
        addFooterButton(x, y, 62, "apply", ignored ->
                submit(false), "apply");
        x += 66;
        addFooterButton(x, y, 92, "save_close", ignored ->
                submit(true), "save_close");
        x += 96;
        addFooterButton(x, y, 62, "cancel", ignored ->
                requestClose(), "cancel");
        Button help = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.help"),
                ignored -> mutateAndRebuild(() -> {
                    draft.helpVisible(!draft.helpVisible());
                })).bounds(width - 72, y, 62, 20).build();
        help.setTooltip(help("help"));
        addRenderableWidget(help);
    }

    private void buildNarrowFooter() {
        int margin = 8;
        int gap = 4;
        int firstWidth = Math.max(44,
                (width - margin * 2 - gap * 2) / 3);
        int y = footerTop();
        addFooterButton(margin, y, firstWidth, "revert",
                ignored -> confirmRevert(), "revert");
        addFooterButton(margin + firstWidth + gap, y,
                firstWidth, "apply", ignored -> submit(false), "apply");
        addFooterButton(margin + (firstWidth + gap) * 2, y,
                firstWidth, "save_close",
                ignored -> submit(true), "save_close");
        int secondWidth = Math.max(62,
                (width - margin * 2 - gap) / 2);
        addFooterButton(margin, height - 24, secondWidth,
                "cancel", ignored -> requestClose(), "cancel");
        Button help = FutureShopsButton.styled(
                Component.translatable(
                        "gui.futureshops.offer_editor.help"),
                ignored -> mutateAndRebuild(() -> {
                    draft.helpVisible(!draft.helpVisible());
                })).bounds(margin + secondWidth + gap, height - 24,
                        secondWidth, 20).build();
        help.setTooltip(help("help"));
        addRenderableWidget(help);
    }

    private void addFooterButton(
            int x,
            int y,
            int width,
            String key,
            Button.OnPress action,
            String helpKey
    ) {
        Button button = FutureShopsButton.styled(Component.translatable(
                "gui.futureshops.offer_editor." + key), action)
                .bounds(x, y, width, 20).build();
        boolean saveAction = "apply".equals(key)
                || "save_close".equals(key);
        button.active = pendingRequestId == null
                && (!saveAction
                || draft.valid() && staleSnapshot == null);
        if (!button.active
                && saveAction
                && pendingRequestId == null) {
            button.setTooltip(Tooltip.create(
                    staleSnapshot == null
                            ? disabledSaveHelp(helpKey)
                            : Component.translatable(
                            "gui.futureshops.offer_editor.save_stale_blocked")));
        } else {
            button.setTooltip(help(helpKey));
        }
        addRenderableWidget(button);
    }

    private void addTextField(
            String path,
            String value,
            int y,
            Consumer<String> save
    ) {
        String registeredPath = registeredFieldPath(path);
        int labelWidth = advancedMode
                ? advancedFieldLabelWidth() : 0;
        int fieldX = contentLeft + labelWidth;
        int fieldWidth = Math.max(1, Math.min(260,
                contentWidth - labelWidth));
        EditBox field = new EditBox(font, fieldX, y,
                fieldWidth, 18,
                Component.translatable(
                        OfferEditorControlRegistry.fieldLabelKey(
                                registeredPath)));
        field.setValue(draft.fieldValue(path, value));
        field.setMaxLength(path.contains("description") ? 512 : 160);
        field.setTooltip(Tooltip.create(fieldHelp(registeredPath)));
        addRenderableWidget(field);
        bindings.add(new EditBinding(path, registeredPath, field, save,
                numericField(registeredPath)
                        && !moneyField(registeredPath)));
    }

    private void switchSection(OfferEditorDraft.Section section) {
        flushFields();
        draft.section(section);
        rebuild();
    }

    private void rebuild() {
        flushFields();
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    private void mutateAndRebuild(Runnable mutation) {
        flushFields();
        mutation.run();
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    private void mutateStructureAndRebuild(
            String pathPrefix,
            Runnable mutation
    ) {
        flushFields();
        if (!blockStructuralEdit(pathPrefix)) {
            mutation.run();
        }
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    private boolean blockStructuralEdit(String pathPrefix) {
        Optional<String> invalid =
                draft.firstInvalidFieldPath(pathPrefix);
        if (invalid.isEmpty()) {
            return false;
        }
        String path = invalid.orElseThrow();
        draft.focusedPath(path);
        draft.section(OfferEditorDraft.Section.forPath(path));
        resultMessage = Component.translatable(
                "gui.futureshops.offer_editor.fix_invalid_before_structure");
        resultSuccess = false;
        return true;
    }

    private void flushFields() {
        for (EditBinding binding : bindings) {
            if (binding.field().isFocused()) {
                draft.focusedPath(binding.path());
            }
            draft.recordFieldValue(binding.path(),
                    binding.field().getValue(), binding.numeric());
            binding.save().accept(binding.field().getValue());
        }
        bindings.clear();
    }

    private void submit(boolean closeAfter) {
        flushFields();
        if (!draft.valid() || pendingRequestId != null) {
            rebuild();
            return;
        }
        pendingRequestId = UUID.randomUUID();
        closeAfterSave = closeAfter;
        AdminShopOfferConfigWriter.Operation operation = creating
                ? AdminShopOfferConfigWriter.Operation.CREATE
                : AdminShopOfferConfigWriter.Operation.UPDATE;
        pendingOperation = operation;
        if (playerShopTarget == null) {
            ShopPackets.CHANNEL.sendToServer(
                    new C2SAdminOfferSavePacket(
                            pendingRequestId,
                            ShopClientState.getActiveShopId(),
                            creating ? ""
                                    : draft.baseline().listingId(),
                            creating ? 0L
                                    : draft.baseline().revision(),
                            operation,
                            Optional.of(draft.candidate())));
        } else {
            ShopPackets.CHANNEL.sendToServer(
                    new C2SPlayerShopOfferSavePacket(
                            pendingRequestId,
                            playerShopTarget.shopPos(),
                            playerShopTarget.listingIndex(),
                            draft.baseline().listingId(),
                            draft.baseline().revision(),
                            draft.candidate()));
        }
        rebuild();
    }

    public void applyPlayerShopSaveResult(
            S2CPlayerShopOfferSaveResultPacket result
    ) {
        if (playerShopTarget != null) {
            applySaveResult(result.asAdminResult());
        }
    }

    public boolean isPlayerShopEditor() {
        return playerShopTarget != null;
    }

    public void applySaveResult(
            S2CAdminOfferSaveResultPacket result
    ) {
        AdminOfferSaveAcknowledgement.Decision decision =
                AdminOfferSaveAcknowledgement.decide(
                        pendingRequestId, pendingOperation,
                        closeAfterSave, result);
        if (decision
                == AdminOfferSaveAcknowledgement.Decision.IGNORED) {
            return;
        }
        pendingRequestId = null;
        resultSuccess = result.success();
        resultMessage = Component.translatable(
                "gui.futureshops.offer_editor.result."
                        + result.status().name()
                        .toLowerCase(java.util.Locale.ROOT));
        if (decision
                == AdminOfferSaveAcknowledgement.Decision.REMOVED) {
            pendingOperation = null;
            if (minecraft != null) {
                minecraft.setScreen(parent);
            }
            return;
        }
        if (decision
                == AdminOfferSaveAcknowledgement.Decision
                .ACKNOWLEDGED_KEEP_OPEN
                || decision
                == AdminOfferSaveAcknowledgement.Decision
                .ACKNOWLEDGED_CLOSE) {
            draft.acknowledge(result.snapshot().orElseThrow());
            creating = false;
            if (decision
                    == AdminOfferSaveAcknowledgement.Decision
                    .ACKNOWLEDGED_CLOSE && minecraft != null) {
                minecraft.setScreen(parent);
                return;
            }
        } else if (!result.issues().isEmpty()) {
            draft.reject(result.issues());
            resultMessage = validationMessage(
                    result.issues().get(0));
        }
        if (result.status()
                == AdminShopOfferConfigWriter.Status.STALE
                && result.snapshot().isPresent()) {
            staleSnapshot = result.snapshot().orElseThrow();
            staleReviewing = false;
        }
        closeAfterSave = false;
        pendingOperation = null;
        rebuild();
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        hoveredEditorComponent = null;
        if (!advancedMode) {
            renderSimpleEditor(
                    graphics, mouseX, mouseY, partialTick);
            return;
        }
        renderBackground(graphics);
        graphics.fill(6, 6, width - 6, height - 32,
                ShopColors.SURFACE_BASE);
        ShopUiUtil.renderAccentLine(
                graphics, 8, 6, width - 16);
        graphics.drawString(font, title, 12, 10,
                ShopColors.TEXT_STRONG, false);
        graphics.drawString(font,
                Component.literal(draft.candidate().listingId()),
                Math.max(12, width - 190), 10,
                ShopColors.TEXT_MUTED, false);
        renderSectionContent(graphics, mouseX, mouseY);
        renderSummary(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderFieldLabelsAndHelp(graphics, mouseX, mouseY);
        if (hoveredEditorComponent != null) {
            ShopUiUtil.renderItemTooltip(
                    graphics, font,
                    hoveredEditorComponent.itemId(),
                    hoveredEditorComponent.exactNbt(),
                    mouseX, mouseY);
        }
        if (confirmation != null) {
            confirmation.render(graphics, font, width, height,
                    mouseX, mouseY);
        }
    }

    private void renderSimpleEditor(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        renderBackground(graphics);
        graphics.fill(6, 6, width - 6, height - 32,
                ShopColors.SURFACE_BASE);
        ShopUiUtil.renderAccentLine(
                graphics, 8, 6, width - 16);
        graphics.drawString(font, title, 12, 12,
                ShopColors.TEXT_STRONG, false);
        renderSimpleContent(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderFieldLabelsAndHelp(graphics, mouseX, mouseY);
        if (hoveredEditorComponent != null) {
            ShopUiUtil.renderItemTooltip(
                    graphics, font,
                    hoveredEditorComponent.itemId(),
                    hoveredEditorComponent.exactNbt(),
                    mouseX, mouseY);
        }
        if (confirmation != null) {
            confirmation.render(graphics, font, width, height,
                    mouseX, mouseY);
        }
    }

    private void renderSimpleContent(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        int bottom = footerTop() - 4;
        if (bottom <= contentTop) {
            return;
        }
        graphics.drawString(font,
                Component.translatable(
                        "gui.futureshops.offer_editor.simple.title."
                                + simpleStep.key()),
                contentLeft, contentTop + 4,
                ShopColors.ACCENT_PRIMARY, false);
        graphics.drawString(font,
                font.plainSubstrByWidth(
                        Component.translatable(
                                "gui.futureshops.offer_editor."
                                        + "simple.subtitle."
                                        + simpleStep.key())
                                .getString(),
                        contentWidth),
                contentLeft, contentTop + 18,
                ShopColors.TEXT_MUTED, false);
        if (resultMessage != null) {
            graphics.drawString(font,
                    font.plainSubstrByWidth(
                            resultMessage.getString(),
                            contentWidth),
                    contentLeft, contentTop + 30,
                    resultSuccess ? ShopColors.SUCCESS
                            : ShopColors.ERROR, false);
        }
        int viewportTop = simpleViewportTop();
        if (bottom <= viewportTop) {
            return;
        }
        graphics.enableScissor(
                contentLeft, viewportTop,
                contentLeft + contentWidth, bottom);
        try {
            switch (simpleStep) {
                case BASICS -> renderSimpleBasics(
                        graphics, mouseX, mouseY);
                case ITEMS -> renderSimpleItems(
                        graphics, mouseX, mouseY);
                case TRADE -> renderSimpleTrade(
                        graphics, mouseX, mouseY);
                case REVIEW -> renderSimpleReview(
                        graphics, mouseX, mouseY);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private void renderSimpleBasics(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        if (!templateChosen) {
            return;
        }
        ServerShopOfferListing listing = draft.candidate();
        OfferEditorSimpleMode.Mode mode =
                OfferEditorSimpleMode.detect(listing);
        String text = Component.translatable(
                "gui.futureshops.offer_editor.simple.current_type",
                Component.translatable(
                        "gui.futureshops.offer_editor.simple.mode."
                                + mode.key())).getString();
        graphics.drawString(font,
                font.plainSubstrByWidth(text, contentWidth),
                contentLeft, simpleY(226),
                ShopColors.TEXT_MUTED, false);
        if (!listing.iconItemId().isBlank()
                && contentWidth >= 310) {
            int x = contentLeft + Math.min(
                    290, contentWidth - 34);
            int y = simpleY(42);
            ShopUiUtil.renderCard(graphics, x, y, 28, 28);
            OfferItemComponent icon =
                    new OfferItemComponent(
                            "simple_icon",
                            listing.iconItemId(), 1,
                            listing.iconNbt());
            renderComponentPreview(
                    graphics, icon, x + 6, y + 6,
                    mouseX, mouseY);
        }
    }

    private void renderSimpleItems(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        boolean sellInputs = simpleEditsSellInputs();
        List<OfferItemComponent> components =
                simpleEditedComponents();
        graphics.drawString(font,
                Component.translatable(sellInputs
                        ? "gui.futureshops.offer_editor.simple."
                        + "shop_buys_items"
                        : "gui.futureshops.offer_editor.simple."
                        + "player_gets_items"),
                contentLeft, simpleY(70),
                ShopColors.TEXT_STRONG, false);
        if (!sellInputs && components.size() > 1) {
            graphics.drawString(font,
                    Component.translatable(
                            "gui.futureshops.offer_editor.simple."
                                    + "bundle_badge",
                            components.size()),
                    contentLeft + Math.min(170,
                            Math.max(0, contentWidth - 130)),
                    simpleY(70),
                    ShopColors.ACCENT_PRIMARY, false);
        }
        if (components.isEmpty()) {
            graphics.drawString(font,
                    Component.translatable(
                            "gui.futureshops.offer_editor.simple."
                                    + "no_items"),
                    contentLeft, simpleY(90),
                    ShopColors.STATUS_WARNING, false);
            return;
        }
        for (int index = 0; index < components.size(); index++) {
            renderSimpleComponentRow(
                    graphics, components.get(index),
                    simpleY(82 + index * 36),
                    mouseX, mouseY);
        }
    }

    private void renderSimpleTrade(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        OfferEditorSimpleMode.Mode mode =
                OfferEditorSimpleMode.detect(draft.candidate());
        graphics.drawString(font,
                Component.translatable(
                        "gui.futureshops.offer_editor.simple."
                                + "selected_mode",
                        Component.translatable(
                                "gui.futureshops.offer_editor."
                                        + "simple.mode."
                                        + mode.key())),
                contentLeft, simpleY(30),
                ShopColors.TEXT_STRONG, false);
        int barterIndex = simpleBarterOptionIndex();
        if (barterIndex < 0) {
            return;
        }
        int columns = contentWidth < 430 ? 1 : 2;
        int modeRows = (7 + columns - 1) / columns;
        int offset = 42 + modeRows * 28 + 18;
        if (simpleMoneyOptionIndex() >= 0) {
            offset += 44;
        }
        if (!draft.candidate().sellOptions().isEmpty()) {
            offset += 44;
        }
        List<OfferItemComponent> costs = draft.candidate()
                .acquireOptions().get(barterIndex).itemCosts();
        graphics.drawString(font,
                Component.translatable(
                        "gui.futureshops.offer_editor.simple."
                                + "barter_costs"),
                contentLeft, simpleY(offset - 12),
                ShopColors.TEXT_STRONG, false);
        for (int index = 0; index < costs.size(); index++) {
            renderSimpleComponentRow(
                    graphics, costs.get(index),
                    simpleY(offset + 38 + index * 36),
                    mouseX, mouseY);
        }
    }

    private void renderSimpleReview(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        int offset = draft.candidate().outputs().size() > 1
                ? 68 : 34;
        renderPreview(
                graphics, simpleY(offset), mouseX, mouseY);
    }

    private void renderSimpleComponentRow(
            GuiGraphics graphics,
            OfferItemComponent component,
            int y,
            int mouseX,
            int mouseY
    ) {
        int rowWidth = Math.max(120, contentWidth);
        ShopUiUtil.renderCard(
                graphics, contentLeft, y,
                rowWidth, 30);
        ShopUiUtil.renderItemIconWithNbt(
                graphics, font, component.itemId(),
                component.exactNbt(),
                contentLeft + 6, y + 6);
        String text = component.count() + " x "
                + component.itemId();
        graphics.drawString(font,
                font.plainSubstrByWidth(
                        text, Math.max(40, contentWidth - 142)),
                contentLeft + 30, y + 11,
                ShopColors.TEXT_STRONG, false);
        if (mouseX >= contentLeft
                && mouseX < contentLeft + contentWidth - 108
                && mouseY >= y && mouseY < y + 30) {
            hoveredEditorComponent = component;
        }
    }

    private void renderSectionContent(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        int contentBottom = footerTop() - 4;
        if (contentBottom <= contentTop) {
            return;
        }
        graphics.enableScissor(contentLeft, contentTop,
                contentLeft + contentWidth, contentBottom);
        try {
            if (staleReviewing && staleSnapshot != null) {
                renderStaleReview(graphics);
                return;
            }
            graphics.drawString(font, sectionLabel(draft.section()),
                    contentLeft, contentTop + 4,
                    ShopColors.ACCENT_PRIMARY, false);
            if (draft.section() == OfferEditorDraft.Section.GENERAL) {
                ServerShopOfferListing listing = draft.candidate();
                int fieldX = contentLeft + advancedFieldLabelWidth();
                int fieldWidth = Math.max(1, Math.min(260,
                        contentWidth
                                - advancedFieldLabelWidth()));
                int previewSize = 24;
                int previewY = sectionY(138);
                if (!listing.iconItemId().isBlank()
                        && fieldX + fieldWidth + previewSize + 6
                        <= contentLeft + contentWidth
                        && previewY >= contentTop
                        && previewY + previewSize + 4 <= footerTop()) {
                    OfferItemComponent icon = new OfferItemComponent(
                            "icon", listing.iconItemId(), 1,
                            listing.iconNbt());
                    int previewX = fieldX + fieldWidth + 6;
                    ShopUiUtil.renderCard(graphics, previewX, previewY,
                            previewSize, previewSize);
                    renderComponentPreview(graphics, icon,
                            previewX + 4, previewY + 4,
                            mouseX, mouseY);
                }
            } else if (draft.section()
                    == OfferEditorDraft.Section.OUTPUTS) {
                renderOutputTotals(graphics, sectionY(180));
                renderComponents(graphics,
                        draft.candidate().outputs(),
                        sectionY(224), mouseX, mouseY);
            } else if (draft.section()
                    == OfferEditorDraft.Section.GET_OPTIONS) {
                renderAcquireSummary(
                        graphics, sectionY(456), mouseX, mouseY);
            } else if (draft.section()
                    == OfferEditorDraft.Section.SELL_OPTIONS) {
                renderSellSummary(
                        graphics, sectionY(426), mouseX, mouseY);
            } else if (draft.section()
                    == OfferEditorDraft.Section.BUNDLE_VALUE) {
                graphics.drawString(font, Component.translatable(
                                "gui.futureshops.offer_editor."
                                        + "comparison_help"),
                        contentLeft, contentTop + 58,
                        ShopColors.TEXT_MUTED, false);
            } else if (draft.section()
                    == OfferEditorDraft.Section.PREVIEW) {
                renderPreview(
                        graphics, contentTop + 58, mouseX, mouseY);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private void renderStaleReview(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable(
                        "gui.futureshops.offer_editor.stale_review_title"),
                contentLeft, contentTop + 4,
                ShopColors.STATUS_WARNING, false);
        List<OfferEditorStaleReview.Change> changes =
                OfferEditorStaleReview.compare(
                        draft.candidate(), staleSnapshot);
        int y = contentTop + 52;
        int bottom = height - (width < 430 ? 76 : 48);
        int visible = Math.max(1, (bottom - y) / 38);
        int shown = Math.min(visible, changes.size());
        for (int index = 0; index < shown; index++) {
            OfferEditorStaleReview.Change change = changes.get(index);
            graphics.drawString(font,
                    font.plainSubstrByWidth(change.path(), contentWidth),
                    contentLeft, y, ShopColors.ACCENT_PRIMARY, false);
            graphics.drawString(font,
                    font.plainSubstrByWidth(Component.translatable(
                                    "gui.futureshops.offer_editor.stale_review_local",
                                    change.localValue()).getString(),
                            contentWidth),
                    contentLeft, y + 11,
                    ShopColors.TEXT_STRONG, false);
            graphics.drawString(font,
                    font.plainSubstrByWidth(Component.translatable(
                                    "gui.futureshops.offer_editor.stale_review_server",
                                    change.serverValue()).getString(),
                            contentWidth),
                    contentLeft, y + 22,
                    ShopColors.TEXT_MUTED, false);
            y += 38;
        }
        if (changes.isEmpty()) {
            graphics.drawString(font, Component.translatable(
                            "gui.futureshops.offer_editor.stale_review_no_changes"),
                    contentLeft, y, ShopColors.TEXT_MUTED, false);
        } else if (shown < changes.size()) {
            graphics.drawString(font, Component.translatable(
                            "gui.futureshops.offer_editor.stale_review_more",
                            changes.size() - shown),
                    contentLeft, y, ShopColors.TEXT_MUTED, false);
        }
    }

    private void renderOutputTotals(
            GuiGraphics graphics,
            int y
    ) {
        List<OfferItemComponent> outputs =
                draft.candidate().outputs();
        long total = outputs.stream()
                .mapToLong(OfferItemComponent::count)
                .sum();
        graphics.drawString(font, Component.translatable(
                        "gui.futureshops.offer_editor.output_total",
                        total, outputs.size()),
                contentLeft, y, ShopColors.TEXT_MUTED, false);
        if (outputs.size()
                >= com.enviouse.futureshops.catalog.offer
                .ServerShopOfferValidator.MAX_COMPONENTS - 6) {
            graphics.drawString(font, Component.translatable(
                            "gui.futureshops.offer_editor.output_limit_warning",
                            outputs.size(),
                            com.enviouse.futureshops.catalog.offer
                                    .ServerShopOfferValidator
                                    .MAX_COMPONENTS),
                    contentLeft, y + 12,
                    ShopColors.STATUS_WARNING, false);
        }
    }

    private void renderComponents(
            GuiGraphics graphics,
            List<OfferItemComponent> components,
            int y,
            int mouseX,
            int mouseY
    ) {
        int index = 0;
        for (OfferItemComponent component : components) {
            if (index >= 8) {
                break;
            }
            ShopUiUtil.renderItemIconWithNbt(graphics, font,
                    component.itemId(), component.exactNbt(),
                    contentLeft, y + index * 22);
            graphics.drawString(font,
                    component.count() + "  " + component.itemId(),
                    contentLeft + 22, y + index * 22 + 4,
                    ShopColors.TEXT_STRONG, false);
            if (mouseX >= contentLeft
                    && mouseX < contentLeft + contentWidth
                    && mouseY >= y + index * 22
                    && mouseY < y + index * 22 + 20) {
                hoveredEditorComponent = component;
            }
            index++;
        }
    }

    private void renderAcquireSummary(
            GuiGraphics graphics,
            int y,
            int mouseX,
            int mouseY
    ) {
        List<AcquireOfferOption> options =
                draft.candidate().acquireOptions();
        if (options.isEmpty()) {
            return;
        }
        AcquireOfferOption option = options.get(Math.min(
                selectedAcquireIndex, options.size() - 1));
        graphics.drawString(font,
                Component.translatable(
                        "gui.futureshops.offer_editor.option_position",
                        selectedAcquireIndex + 1,
                        options.size(), option.label()),
                contentLeft, y, ShopColors.TEXT_STRONG, false);
        if (options.size() > 1) {
            graphics.drawString(font, Component.translatable(
                            "gui.futureshops.offer_editor.alternatives_use_or"),
                    contentLeft, y + 12,
                    ShopColors.ACCENT_PRIMARY, false);
        }
        if (option.itemCosts().size() > 1) {
            graphics.drawString(font, Component.translatable(
                            "gui.futureshops.offer_editor.all_required"),
                    contentLeft, y + 24,
                    ShopColors.TEXT_MUTED, false);
        }
        renderComponents(graphics, option.itemCosts(), y + 38,
                mouseX, mouseY);
    }

    private void renderSellSummary(
            GuiGraphics graphics,
            int y,
            int mouseX,
            int mouseY
    ) {
        List<SellOfferOption> options =
                draft.candidate().sellOptions();
        if (options.isEmpty()) {
            return;
        }
        SellOfferOption option = options.get(Math.min(
                selectedSellIndex, options.size() - 1));
        graphics.drawString(font,
                Component.translatable(
                        "gui.futureshops.offer_editor.option_position",
                        selectedSellIndex + 1,
                        options.size(), option.label()),
                contentLeft, y, ShopColors.TEXT_STRONG, false);
        if (options.size() > 1) {
            graphics.drawString(font, Component.translatable(
                            "gui.futureshops.offer_editor.alternatives_use_or"),
                    contentLeft, y + 12,
                    ShopColors.ACCENT_PRIMARY, false);
        }
        if (option.itemInputs().size() > 1) {
            graphics.drawString(font, Component.translatable(
                            "gui.futureshops.offer_editor.all_required"),
                    contentLeft, y + 24,
                    ShopColors.TEXT_MUTED, false);
        }
        renderComponents(graphics, option.itemInputs(), y + 38,
                mouseX, mouseY);
    }

    private void renderPreview(
            GuiGraphics graphics,
            int y,
            int mouseX,
            int mouseY
    ) {
        ServerShopOfferListing listing = draft.candidate();
        ServerShopOfferPresentation.Projection projection =
                ServerShopOfferPresentation.project(
                        listing, previewState,
                        ShopClientState.getCurrencyName(),
                        ShopClientState.getCatalogOffers().stream()
                                .collect(java.util.stream.Collectors
                                        .toMap(
                                                ServerShopOfferListing
                                                        ::listingId,
                                                value -> value,
                                                (left, right) -> left,
                                                java.util.LinkedHashMap
                                                        ::new)));
        int panelY = y + 24;
        int panelHeight = Math.max(150,
                height - panelY - (width < 430 ? 70 : 44));
        graphics.fill(contentLeft, panelY,
                contentLeft + contentWidth,
                panelY + panelHeight,
                ShopColors.SURFACE_RAISED);
        ShopUiUtil.drawBorder(graphics, contentLeft, panelY,
                contentWidth, panelHeight,
                ShopColors.BORDER_MUTED);
        if (!projection.iconItemId().isBlank()) {
            ShopUiUtil.renderItemIconWithNbt(
                    graphics, font,
                    projection.iconItemId(),
                    projection.iconNbt(),
                    contentLeft + 8, panelY + 8);
            if (mouseX >= contentLeft + 8
                    && mouseX < contentLeft + 26
                    && mouseY >= panelY + 8
                    && mouseY < panelY + 26) {
                hoveredEditorComponent = new OfferItemComponent(
                        "preview_icon", projection.iconItemId(),
                        1, projection.iconNbt());
            }
        }
        int textX = contentLeft + 32;
        graphics.drawString(font,
                projection.title(), textX, panelY + 7,
                ShopColors.TEXT_STRONG, false);
        graphics.drawString(font,
                font.plainSubstrByWidth(
                        projection.description().getString(),
                        Math.max(20, contentWidth - 42)),
                textX, panelY + 20,
                ShopColors.TEXT_MUTED, false);
        graphics.drawString(font, projection.status(),
                contentLeft + 8, panelY + 38,
                previewState == ServerShopOfferPresentation
                        .PreviewState.ACTIVE
                        ? ShopColors.SUCCESS : ShopColors.ERROR,
                false);
        int rowY = panelY + 54;
        if (previewMode == PreviewMode.BROWSE_CARD) {
            graphics.drawString(font, Component.translatable(
                            projection.sellOnly()
                                    ? "gui.futureshops.offer"
                                    + ".presentation.sell_only"
                                    : projection.bundle()
                                    ? "gui.futureshops.offer"
                                    + ".presentation.bundle"
                                    : "gui.futureshops.offer"
                                    + ".presentation.single"),
                    contentLeft + 8, rowY,
                    ShopColors.TEXT_MUTED, false);
            rowY += 14;
            List<Component> primaryRows =
                    projection.acquireRows().isEmpty()
                            ? projection.sellRows()
                            : projection.acquireRows();
            if (!primaryRows.isEmpty()) {
                graphics.drawString(font,
                        font.plainSubstrByWidth(
                                primaryRows.get(0).getString(),
                                contentWidth - 16),
                        contentLeft + 8, rowY,
                        ShopColors.TEXT_STRONG, false);
            }
            return;
        }
        if (previewMode == PreviewMode.OPTION_CHOOSER) {
            graphics.drawString(font, Component.translatable(
                            "gui.futureshops.offer.choose_option"),
                    contentLeft + 8, rowY,
                    ShopColors.ACCENT_PRIMARY, false);
            rowY += 14;
            renderPreviewOptions(
                    graphics, projection, rowY);
            return;
        }
        for (Component row : projection.detailRows()) {
            graphics.drawString(font,
                    font.plainSubstrByWidth(
                            row.getString(), contentWidth - 16),
                    contentLeft + 8, rowY,
                    ShopColors.TEXT_MUTED, false);
            rowY += 12;
        }
        renderPreviewOptions(graphics, projection, rowY);
    }

    private void renderPreviewOptions(
            GuiGraphics graphics,
            ServerShopOfferPresentation.Projection projection,
            int startY
    ) {
        int rowY = startY;
        for (int index = 0;
             index < projection.acquireRows().size(); index++) {
            if (index > 0) {
                graphics.drawString(font,
                        Component.translatable(
                                "gui.futureshops.offer.or"),
                        contentLeft + 8, rowY,
                        ShopColors.ACCENT_PRIMARY, false);
                rowY += 12;
            }
            graphics.drawString(font,
                    font.plainSubstrByWidth(
                            projection.acquireRows().get(index)
                                    .getString(),
                            contentWidth - 16),
                    contentLeft + 8, rowY,
                    ShopColors.TEXT_STRONG, false);
            rowY += 14;
        }
        for (Component row : projection.sellRows()) {
            graphics.drawString(font,
                    font.plainSubstrByWidth(
                            row.getString(), contentWidth - 16),
                    contentLeft + 8, rowY,
                    ShopColors.TEXT_CURRENCY, false);
            rowY += 14;
        }
    }

    private void renderComponentPreview(
            GuiGraphics graphics,
            OfferItemComponent component,
            int x,
            int y,
            int mouseX,
            int mouseY
    ) {
        ShopUiUtil.renderItemIconWithNbt(
                graphics, font, component.itemId(),
                component.exactNbt(), x, y);
        if (mouseX >= x && mouseX < x + 18
                && mouseY >= y && mouseY < y + 18) {
            hoveredEditorComponent = component;
        }
    }

    private void renderSummary(GuiGraphics graphics) {
        if (summaryWidth == 0) {
            renderNarrowPersistentHelp(graphics);
            return;
        }
        graphics.fill(summaryLeft, contentTop,
                summaryLeft + summaryWidth, height - 36,
                ShopColors.SURFACE_RAISED);
        graphics.drawString(font,
                Component.translatable(
                        "gui.futureshops.offer_editor.summary"),
                summaryLeft + 8, contentTop + 8,
                ShopColors.TEXT_STRONG, false);
        ServerShopOfferListing listing = draft.candidate();
        int y = contentTop + 28;
        String[] rows = {
                listing.outputs().size() + " outputs",
                listing.acquireOptions().size() + " get options",
                listing.sellOptions().size() + " sell options",
                listing.bundle() ? "bundle" : "single output",
                draft.dirty() ? "unsaved changes" : "saved baseline",
                draft.valid() ? "valid" : draft.issues().size()
                        + " issues"
        };
        for (String row : rows) {
            graphics.drawString(font,
                    font.plainSubstrByWidth(row, summaryWidth - 16),
                    summaryLeft + 8, y, draft.valid()
                            ? ShopColors.TEXT_MUTED : ShopColors.ERROR,
                    false);
            y += 14;
        }
        if (draft.helpVisible()) {
            y += 8;
            graphics.drawString(font, Component.translatable(
                            "gui.futureshops.offer_editor.context_help"),
                    summaryLeft + 8, y,
                    ShopColors.ACCENT_PRIMARY, false);
            y += 13;
            ShopUiUtil.drawWrappedString(graphics, font,
                    persistentHelp(), summaryLeft + 8, y,
                    summaryWidth - 16, ShopColors.TEXT_FAINT, 10);
        }
        if (resultMessage != null) {
            ShopUiUtil.drawWrappedString(graphics, font,
                    resultMessage, summaryLeft + 8, y + 8,
                    summaryWidth - 16,
                    resultSuccess ? ShopColors.SUCCESS
                            : ShopColors.ERROR, 10);
        }
    }

    private void renderNarrowPersistentHelp(GuiGraphics graphics) {
        if (!draft.helpVisible()) {
            return;
        }
        int footerHeight = width < 430 ? 52 : 30;
        int panelY = Math.max(contentTop + 22,
                height - footerHeight - 24);
        graphics.fill(8, panelY, width - 8, panelY + 22,
                ShopColors.SURFACE_RAISED);
        String text = Component.translatable(
                        "gui.futureshops.offer_editor.context_help_inline",
                        persistentHelp())
                .getString();
        graphics.drawString(font,
                font.plainSubstrByWidth(text, width - 24),
                12, panelY + 7, ShopColors.TEXT_FAINT, false);
    }

    private Component persistentHelp() {
        return bindings.stream()
                .filter(binding -> binding.field().isFocused())
                .map(binding -> fieldHelp(binding.registeredPath()))
                .findFirst()
                .orElseGet(() -> bindings.stream()
                        .filter(binding -> binding.path().equals(
                                draft.focusedPath()))
                        .map(binding -> fieldHelp(
                                binding.registeredPath()))
                        .findFirst()
                        .orElseGet(() -> sectionHelp(
                                draft.section())));
    }

    private void renderFieldLabelsAndHelp(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        for (EditBinding binding : bindings) {
            EditBox field = binding.field();
            if (!field.visible) {
                continue;
            }
            Component label = simpleFieldLabels.getOrDefault(
                    field,
                    Component.translatable(
                            OfferEditorControlRegistry.fieldLabelKey(
                                    binding.registeredPath())));
            graphics.drawString(font,
                    font.plainSubstrByWidth(label.getString(),
                            advancedMode
                                    ? Math.max(8,
                                    advancedFieldLabelWidth() - 6)
                                    : contentWidth),
                    advancedMode ? contentLeft : field.getX(),
                    advancedMode ? field.getY() + 5
                            : field.getY() - 9,
                    ShopColors.TEXT_MUTED, false);
            List<com.enviouse.futureshops.catalog.offer
                    .OfferValidationIssue> issues =
                    draft.issues(binding.path());
            if (!issues.isEmpty()) {
                ShopUiUtil.drawBorder(graphics,
                        field.getX() - 1, field.getY() - 1,
                        field.getWidth() + 2,
                        field.getHeight() + 2,
                        ShopColors.ERROR);
                if (!advancedMode) {
                    graphics.drawString(font,
                            font.plainSubstrByWidth(
                                    validationMessage(issues.get(0))
                                            .getString(),
                                    contentWidth),
                            field.getX(),
                            field.getY() + field.getHeight() + 1,
                            ShopColors.ERROR, false);
                }
            }
            if (field.isFocused()) {
                graphics.renderTooltip(font,
                        fieldHelp(binding.registeredPath()),
                        mouseX, mouseY);
            }
        }
    }

    private void requestClose() {
        flushFields();
        if (!draft.dirty()) {
            if (minecraft != null) {
                minecraft.setScreen(parent);
            }
            return;
        }
        confirmation = new ConfirmationModal(
                Component.translatable(
                        "gui.futureshops.offer_editor.discard_title")
                        .getString(),
                List.of(ConfirmationModal.SummaryLine.text(
                        Component.translatable(
                                "gui.futureshops.offer_editor.discard_help")
                                .getString())),
                "",
                modal -> {
                    confirmation = null;
                    if (minecraft != null) {
                        minecraft.setScreen(parent);
                    }
                },
                () -> confirmation = null);
    }

    private void confirmRevert() {
        flushFields();
        if (!draft.dirty()) {
            return;
        }
        confirmation = new ConfirmationModal(
                Component.translatable(
                        "gui.futureshops.offer_editor.revert_title")
                        .getString(),
                List.of(ConfirmationModal.SummaryLine.text(
                        Component.translatable(
                                "gui.futureshops.offer_editor.revert_help")
                                .getString())),
                "",
                modal -> {
                    confirmation = null;
                    draft.revert();
                    rebuild();
                },
                () -> confirmation = null);
    }

    private void confirmResetSection() {
        flushFields();
        OfferEditorDraft.Section section = draft.section();
        if (!draft.sectionDirty(section)) {
            return;
        }
        confirmation = new ConfirmationModal(
                Component.translatable(
                        "gui.futureshops.offer_editor.reset_section_title")
                        .getString(),
                List.of(ConfirmationModal.SummaryLine.text(
                        sectionLabel(section).getString())),
                Component.translatable(
                        "gui.futureshops.offer_editor.reset_section_help",
                        sectionLabel(section)).getString(),
                modal -> {
                    confirmation = null;
                    draft.resetSection(section);
                    rebuild();
                },
                () -> confirmation = null);
    }

    private void submitDuplicate() {
        flushFields();
        if (!draft.valid() || pendingRequestId != null) {
            rebuild();
            return;
        }
        ServerShopOfferListing duplicate = withListingId(
                draft.candidate(),
                draft.candidate().listingId() + "_copy_"
                        + Integer.toUnsignedString(
                        java.util.concurrent.ThreadLocalRandom
                                .current().nextInt(), 36));
        pendingRequestId = UUID.randomUUID();
        pendingOperation =
                AdminShopOfferConfigWriter.Operation.DUPLICATE;
        ShopPackets.CHANNEL.sendToServer(
                new C2SAdminOfferSavePacket(
                        pendingRequestId,
                        ShopClientState.getActiveShopId(),
                        draft.baseline().listingId(),
                        draft.baseline().revision(),
                        pendingOperation, Optional.of(duplicate)));
        rebuild();
    }

    private void confirmRemove() {
        confirmation = new ConfirmationModal(
                Component.translatable(
                        "gui.futureshops.offer_editor.remove_title")
                        .getString(),
                List.of(ConfirmationModal.SummaryLine.text(
                        draft.candidate().displayName())),
                Component.translatable(
                        "gui.futureshops.offer_editor.remove_help")
                        .getString(),
                modal -> {
                    confirmation = null;
                    submitRemove();
                },
                () -> confirmation = null);
    }

    private ConfirmationModal destructiveConfirmation(
            String titleKey,
            String helpKey,
            Runnable action
    ) {
        return new ConfirmationModal(
                Component.translatable(
                        "gui.futureshops.offer_editor." + titleKey)
                        .getString(),
                List.of(ConfirmationModal.SummaryLine.text(
                        draft.candidate().displayName())),
                Component.translatable(
                        "gui.futureshops.offer_editor." + helpKey)
                        .getString(),
                modal -> {
                    confirmation = null;
                    action.run();
                    rebuild();
                },
                () -> confirmation = null);
    }

    private void submitRemove() {
        if (pendingRequestId != null) {
            return;
        }
        pendingRequestId = UUID.randomUUID();
        pendingOperation = AdminShopOfferConfigWriter.Operation.REMOVE;
        ShopPackets.CHANNEL.sendToServer(
                new C2SAdminOfferSavePacket(
                        pendingRequestId,
                        ShopClientState.getActiveShopId(),
                        draft.baseline().listingId(),
                        draft.baseline().revision(),
                        pendingOperation, Optional.empty()));
        rebuild();
    }

    private void confirmReloadServer() {
        if (staleSnapshot == null) {
            return;
        }
        confirmation = new ConfirmationModal(
                Component.translatable(
                        "gui.futureshops.offer_editor.reload_title")
                        .getString(),
                List.of(ConfirmationModal.SummaryLine.text(
                        Component.translatable(
                                "gui.futureshops.offer_editor.reload_help")
                                .getString())),
                "",
                modal -> {
                    draft.acknowledge(staleSnapshot);
                    staleSnapshot = null;
                    staleReviewing = false;
                    confirmation = null;
                    rebuild();
                },
                () -> confirmation = null);
    }

    private void reviewStaleChanges() {
        staleReviewing = true;
        resultMessage = Component.translatable(
                "gui.futureshops.offer_editor.review_changes_help");
        resultSuccess = false;
        rebuild();
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (confirmation != null) {
            return confirmation.mouseClicked(
                    mouseX, mouseY, button, font);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
        double delta
    ) {
        if (confirmation != null) {
            return confirmation.mouseScrolled(
                    mouseX, mouseY, delta);
        }
        if (!advancedMode) {
            int previous = simpleScrollPosition;
            int next = Math.max(0,
                    Math.min(maximumSimpleScroll(),
                            previous - (int) Math.signum(delta) * 24));
            if (next == previous) {
                return super.mouseScrolled(mouseX, mouseY, delta);
            }
            flushFields();
            simpleScrollPosition = next;
            rebuildWidgets();
            return true;
        }
        if (!templateChosen) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int previous = draft.scrollPosition();
        int next = Math.max(0, Math.min(maximumScroll(),
                previous - (int) Math.signum(delta) * 24));
        if (next == previous) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        flushFields();
        draft.scrollPosition(next);
        rebuildWidgets();
        return true;
    }

    @Override
    public boolean keyPressed(
            int keyCode,
            int scanCode,
            int modifiers
    ) {
        if (confirmation != null) {
            return confirmation.keyPressed(keyCode);
        }
        if (Screen.hasControlDown()
                && keyCode == GLFW.GLFW_KEY_F) {
            if (advancedMode) {
                openSearchForSection();
            } else {
                openSimpleSearch();
            }
            return true;
        }
        if (Screen.hasControlDown()
                && keyCode == GLFW.GLFW_KEY_S) {
            submit(Screen.hasShiftDown());
            return true;
        }
        if (Screen.hasAltDown()
                && (keyCode == GLFW.GLFW_KEY_LEFT
                || keyCode == GLFW.GLFW_KEY_RIGHT)) {
            int direction = keyCode == GLFW.GLFW_KEY_RIGHT ? 1 : -1;
            if (advancedMode) {
                moveSection(direction);
            } else {
                moveSimpleStep(direction);
            }
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && getFocused() == null) {
            submit(false);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            requestClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void openSimpleSearch() {
        if (!templateChosen) {
            return;
        }
        switch (simpleStep) {
            case BASICS -> openCategoryPicker();
            case ITEMS -> {
                boolean sellInputs = simpleEditsSellInputs();
                openItemPicker(
                        OfferEditorItemPickerScreen.Source.REGISTRY,
                        sellInputs ? "sell_input" : "output",
                        sellInputs ? this::acceptSimpleSellInput
                                : this::acceptSimpleOutput);
            }
            case TRADE -> {
                int barterIndex = simpleBarterOptionIndex();
                if (barterIndex >= 0) {
                    selectedAcquireIndex = barterIndex;
                    openItemPicker(
                            OfferEditorItemPickerScreen.Source.REGISTRY,
                            "payment", this::acceptAcquireCost);
                }
            }
            case REVIEW -> {
            }
        }
    }

    private void moveSimpleStep(int direction) {
        SimpleStep[] steps = SimpleStep.values();
        int index = Math.floorMod(
                simpleStep.ordinal() + direction,
                steps.length);
        SimpleStep next = steps[index];
        if (templateChosen || next == SimpleStep.BASICS) {
            switchSimpleStep(next);
        }
    }

    private void openSearchForSection() {
        switch (draft.section()) {
            case GENERAL -> openCategoryPicker();
            case OUTPUTS -> openItemPicker(
                    OfferEditorItemPickerScreen.Source.REGISTRY,
                    "output", this::acceptOutput);
            case GET_OPTIONS -> openItemPicker(
                    OfferEditorItemPickerScreen.Source.REGISTRY,
                    "payment", this::acceptAcquireCost);
            case SELL_OPTIONS -> openItemPicker(
                    OfferEditorItemPickerScreen.Source.REGISTRY,
                    "sell_input", this::acceptSellInput);
            default -> {
                bindings.stream().findFirst()
                        .ifPresent(binding -> {
                            setFocused(binding.field());
                            binding.field().setFocused(true);
                        });
            }
        }
    }

    private void moveSection(int direction) {
        OfferEditorDraft.Section[] sections =
                OfferEditorDraft.Section.values();
        int index = Math.floorMod(
                draft.section().ordinal() + direction,
                sections.length);
        switchSection(sections[index]);
    }

    @Override
    public void onClose() {
        requestClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void resize(
            net.minecraft.client.Minecraft minecraft,
            int width,
            int height
    ) {
        flushFields();
        super.resize(minecraft, width, height);
    }

    private void mutateGeneral(
            String path,
            String displayName,
            String description,
            String category,
            String permission,
            Boolean active
    ) {
        draft.update(path, current -> copy(
                current,
                displayName == null ? current.displayName() : displayName,
                description == null ? current.description() : description,
                category == null ? current.categoryId() : category,
                permission == null ? current.permissionNode() : permission,
                active == null ? current.active() : active,
                null, null, null, null));
    }

    private void openCategoryPicker() {
        flushFields();
        if (minecraft != null) {
            minecraft.setScreen(
                    new OfferEditorCategoryPickerScreen(
                            this, draft.candidate().categoryId(),
                            this::acceptCategory));
        }
    }

    private void acceptCategory(String categoryId) {
        String normalized = categoryId == null
                || categoryId.isBlank() ? "all" : categoryId;
        mutateGeneral("categoryId", null, null,
                normalized, null, null);
        draft.acceptFieldValue("categoryId", normalized);
        draft.focusedPath("categoryId");
    }

    private String categoryName(String categoryId) {
        if (categoryId == null || categoryId.isBlank()
                || "all".equals(categoryId)) {
            return Component.translatable(
                    "gui.futureshops.offer_editor.no_category")
                    .getString();
        }
        return ShopClientState.getCatalogCategories().stream()
                .filter(category -> category.id().equals(categoryId))
                .map(com.enviouse.futureshops.data
                        .CatalogCategory::displayName)
                .findFirst().orElse(categoryId);
    }

    private void openItemPicker(
            OfferEditorItemPickerScreen.Source source,
            String destination,
            Consumer<OfferItemComponent> selection
    ) {
        flushFields();
        if (minecraft != null) {
            minecraft.setScreen(new OfferEditorItemPickerScreen(
                    this, source,
                    Component.translatable(
                            "gui.futureshops.offer_editor.picker.destination."
                                    + destination),
                    selection));
        }
    }

    private void acceptIcon(OfferItemComponent component) {
        updateIcon(component.itemId(), component.exactNbt());
        draft.acceptFieldValue("iconItemId", component.itemId());
        draft.acceptFieldValue("iconNbt", component.exactNbt());
        draft.focusedPath("iconItemId");
    }

    private void acceptOutput(OfferItemComponent picked) {
        if (blockStructuralEdit("outputs")) {
            return;
        }
        List<OfferItemComponent> outputs = new ArrayList<>(
                draft.candidate().outputs());
        outputs.add(new OfferItemComponent(
                uniqueComponentId(outputs, "output"),
                picked.itemId(), 1, picked.exactNbt()));
        selectedOutputIndex = outputs.size() - 1;
        draft.clearFieldValues("outputs");
        draft.update("outputs", current -> copy(
                current, null, null, null, null, null,
                outputs, null, null, null));
        draft.focusedPath("outputs." + selectedOutputIndex
                + ".count");
    }

    private void acceptAcquireCost(OfferItemComponent picked) {
        List<AcquireOfferOption> options = new ArrayList<>(
                draft.candidate().acquireOptions());
        if (options.isEmpty()) {
            return;
        }
        int optionIndex = Math.min(selectedAcquireIndex,
                options.size() - 1);
        if (blockStructuralEdit("acquireOptions." + optionIndex
                + ".itemCosts")) {
            return;
        }
        AcquireOfferOption old = options.get(optionIndex);
        if (old.free()) {
            resultMessage = Component.translatable(
                    "gui.futureshops.offer_editor.free_has_no_cost");
            resultSuccess = false;
            return;
        }
        List<OfferItemComponent> costs =
                new ArrayList<>(old.itemCosts());
        costs.add(new OfferItemComponent(
                uniqueComponentId(costs, "cost"),
                picked.itemId(), 1, picked.exactNbt()));
        selectedAcquireCostIndex = costs.size() - 1;
        options.set(optionIndex, new AcquireOfferOption(
                old.optionId(), old.label(), false,
                old.moneyCostPresent(),
                old.moneyCostMinorUnits(), costs,
                old.outputMultiplier(), old.limits(),
                old.schedule(), old.permissionNode()));
        draft.clearFieldValues("acquireOptions." + optionIndex
                + ".itemCosts");
        draft.update("acquireOptions." + optionIndex + ".itemCosts",
                current -> copy(current, null, null, null, null, null,
                        null, options, null, null));
        draft.focusedPath("acquireOptions." + optionIndex
                + ".itemCosts." + selectedAcquireCostIndex + ".count");
    }

    private void acceptSellInput(OfferItemComponent picked) {
        List<SellOfferOption> options = new ArrayList<>(
                draft.candidate().sellOptions());
        if (options.isEmpty()) {
            return;
        }
        int optionIndex = Math.min(selectedSellIndex,
                options.size() - 1);
        if (blockStructuralEdit("sellOptions." + optionIndex
                + ".itemInputs")) {
            return;
        }
        SellOfferOption old = options.get(optionIndex);
        List<OfferItemComponent> inputs =
                new ArrayList<>(old.itemInputs());
        inputs.add(new OfferItemComponent(
                uniqueComponentId(inputs, "input"),
                picked.itemId(), 1, picked.exactNbt()));
        selectedSellInputIndex = inputs.size() - 1;
        options.set(optionIndex, new SellOfferOption(
                old.optionId(), old.label(), inputs,
                old.moneyPayoutMinorUnits(), old.capacity(),
                old.limits(), old.schedule(),
                old.permissionNode()));
        draft.clearFieldValues("sellOptions." + optionIndex
                + ".itemInputs");
        draft.update("sellOptions." + optionIndex + ".itemInputs",
                current -> copy(current, null, null, null, null, null,
                        null, null, options, null));
        draft.focusedPath("sellOptions." + optionIndex
                + ".itemInputs." + selectedSellInputIndex + ".count");
    }

    private void updateIcon(String itemId, String nbt) {
        draft.update("icon", current ->
                new ServerShopOfferListing(
                        current.listingId(), current.revision(),
                        current.displayName(), current.description(),
                        current.categoryId(),
                        itemId == null
                                ? current.iconItemId() : itemId,
                        nbt == null ? current.iconNbt() : nbt,
                        current.active(), current.expiresAtEpoch(),
                        current.permissionNode(), current.outputs(),
                        current.acquireOptions(), current.sellOptions(),
                        current.stockPolicy(), current.limits(),
                        current.schedule(),
                        current.bundleComparisons()));
    }

    private void useHeldIcon() {
        OfferItemComponent held = heldComponent("icon");
        if (held == null) {
            resultMessage = Component.translatable(
                    "gui.futureshops.offer_editor.empty_hand");
            resultSuccess = false;
            return;
        }
        updateIcon(held.itemId(), held.exactNbt());
    }

    private void addHeldOutput() {
        OfferItemComponent component = heldComponent(
                "output_" + draft.candidate().outputs().size());
        if (component == null) {
            resultMessage = Component.translatable(
                    "gui.futureshops.offer_editor.empty_hand");
            resultSuccess = false;
            return;
        }
        List<OfferItemComponent> outputs = new ArrayList<>(
                draft.candidate().outputs());
        outputs.add(component);
        List<OfferItemComponent> normalizedOutputs =
                OfferComponentNormalizer.normalize(outputs);
        draft.clearFieldValues("outputs");
        draft.update("outputs", current -> copy(
                current, null, null, null, null, null,
                normalizedOutputs, null, null, null));
    }

    private void confirmRemoveOutput() {
        if (draft.candidate().outputs().isEmpty()) {
            return;
        }
        OfferItemComponent selected = draft.candidate().outputs().get(
                Math.min(selectedOutputIndex,
                        draft.candidate().outputs().size() - 1));
        boolean acknowledged = draft.baseline().outputs().stream()
                .anyMatch(component -> component.componentId().equals(
                        selected.componentId()));
        if (!acknowledged) {
            removeSelectedOutput();
            rebuild();
            return;
        }
        confirmation = destructiveConfirmation(
                "remove_output_title",
                "remove_output_help",
                this::removeSelectedOutput);
    }

    private void removeSelectedOutput() {
        List<OfferItemComponent> outputs = new ArrayList<>(
                draft.candidate().outputs());
        if (!outputs.isEmpty()) {
            outputs.remove(Math.min(selectedOutputIndex,
                    outputs.size() - 1));
            selectedOutputIndex = Math.max(0,
                    selectedOutputIndex - 1);
            draft.clearFieldValues("outputs");
            draft.update("outputs", current -> copy(
                    current, null, null, null, null, null,
                    outputs, null, null, null));
        }
    }

    private void duplicateOutput() {
        List<OfferItemComponent> outputs = new ArrayList<>(
                draft.candidate().outputs());
        if (outputs.isEmpty()) {
            return;
        }
        OfferItemComponent source = outputs.get(Math.min(
                selectedOutputIndex, outputs.size() - 1));
        OfferItemComponent duplicate = new OfferItemComponent(
                uniqueComponentId(outputs, source.componentId() + "_copy"),
                source.itemId(), source.count(), source.exactNbt());
        outputs.add(selectedOutputIndex + 1, duplicate);
        selectedOutputIndex++;
        draft.clearFieldValues("outputs");
        draft.update("outputs", current -> copy(
                current, null, null, null, null, null,
                outputs, null, null, null));
    }

    private void moveOutput(int direction) {
        List<OfferItemComponent> outputs = new ArrayList<>(
                draft.candidate().outputs());
        int target = selectedOutputIndex + direction;
        if (selectedOutputIndex < 0 || selectedOutputIndex >= outputs.size()
                || target < 0 || target >= outputs.size()) {
            return;
        }
        java.util.Collections.swap(outputs, selectedOutputIndex, target);
        selectedOutputIndex = target;
        draft.clearFieldValues("outputs");
        draft.update("outputs", current -> copy(
                current, null, null, null, null, null,
                outputs, null, null, null));
    }

    private void updateOutputItem(String itemId) {
        updateOutput(component -> new OfferItemComponent(
                component.componentId(), itemId,
                component.count(), component.exactNbt()), "itemId");
    }

    private void updateOutputCount(int count) {
        updateOutput(component -> new OfferItemComponent(
                component.componentId(), component.itemId(),
                count, component.exactNbt()), "count");
    }

    private int selectedOutputCount() {
        List<OfferItemComponent> outputs =
                draft.candidate().outputs();
        if (outputs.isEmpty()) {
            return 0;
        }
        return outputs.get(Math.min(
                selectedOutputIndex, outputs.size() - 1)).count();
    }

    private void updateOutputNbt(String nbt) {
        updateOutput(component -> new OfferItemComponent(
                component.componentId(), component.itemId(),
                component.count(), nbt), "exactNbt");
    }

    private void updateOutput(
            UnaryOperator<OfferItemComponent> mutation,
            String path
    ) {
        List<OfferItemComponent> outputs = new ArrayList<>(
                draft.candidate().outputs());
        if (outputs.isEmpty()) {
            return;
        }
        int index = Math.min(selectedOutputIndex, outputs.size() - 1);
        outputs.set(index, mutation.apply(outputs.get(index)));
        draft.update("outputs." + index + "." + path,
                current -> copy(current, null, null, null, null, null,
                        outputs, null, null, null));
    }

    private void addAcquireOption(String type) {
        List<AcquireOfferOption> options = new ArrayList<>(
                draft.candidate().acquireOptions());
        String id = "get_" + (options.size() + 1);
        OfferItemComponent held = "items".equals(type)
                || "compound".equals(type)
                ? heldComponent("cost_1") : null;
        if (("items".equals(type) || "compound".equals(type))
                && held == null) {
            resultMessage = Component.translatable(
                    "gui.futureshops.offer_editor.empty_hand");
            resultSuccess = false;
            return;
        }
        options.add(new AcquireOfferOption(
                id, type, "free".equals(type),
                "money".equals(type) || "compound".equals(type),
                "money".equals(type) || "compound".equals(type)
                        ? 1L : 0L,
                held == null ? List.of() : List.of(held), 1,
                OfferLimitPolicy.defaults(), OfferSchedule.always(), ""));
        selectedAcquireIndex = options.size() - 1;
        draft.update("acquireOptions", current -> copy(
                current, null, null, null, null, null,
                null, options, null, null));
    }

    private void updateAcquireMoney(long money) {
        updateAcquireOption(old -> new AcquireOfferOption(
                old.optionId(), old.label(), old.free(),
                old.moneyCostPresent(), money, old.itemCosts(),
                old.outputMultiplier(), old.limits(),
                old.schedule(), old.permissionNode()), "moneyCost");
    }

    private void updateAcquireLabel(String label) {
        updateAcquireOption(old -> new AcquireOfferOption(
                old.optionId(), label, old.free(),
                old.moneyCostPresent(), old.moneyCostMinorUnits(),
                old.itemCosts(), old.outputMultiplier(), old.limits(),
                old.schedule(), old.permissionNode()), "label");
    }

    private void updateAcquireMultiplier(int multiplier) {
        updateAcquireOption(old -> new AcquireOfferOption(
                old.optionId(), old.label(), old.free(),
                old.moneyCostPresent(), old.moneyCostMinorUnits(),
                old.itemCosts(), multiplier, old.limits(),
                old.schedule(), old.permissionNode()),
                "outputMultiplier");
    }

    private void updateAcquirePermission(String permission) {
        updateAcquireOption(old -> new AcquireOfferOption(
                old.optionId(), old.label(), old.free(),
                old.moneyCostPresent(), old.moneyCostMinorUnits(),
                old.itemCosts(), old.outputMultiplier(), old.limits(),
                old.schedule(), permission), "permission");
    }

    private void updateAcquireSchedule(Long starts, Long ends) {
        updateAcquireOption(old -> new AcquireOfferOption(
                old.optionId(), old.label(), old.free(),
                old.moneyCostPresent(), old.moneyCostMinorUnits(),
                old.itemCosts(), old.outputMultiplier(), old.limits(),
                new OfferSchedule(
                        starts == null
                                ? old.schedule().startsAtEpoch() : starts,
                        ends == null
                                ? old.schedule().endsAtEpoch() : ends),
                old.permissionNode()),
                starts == null ? "endsAtEpoch" : "startsAtEpoch");
    }

    private void updateAcquireLimits(
            Integer maximum,
            Long lifetime,
            Long period,
            Long periodSeconds,
            Long cooldown
    ) {
        updateAcquireOption(old -> new AcquireOfferOption(
                old.optionId(), old.label(), old.free(),
                old.moneyCostPresent(), old.moneyCostMinorUnits(),
                old.itemCosts(), old.outputMultiplier(),
                updatedLimits(old.limits(), maximum, lifetime,
                        period, periodSeconds, cooldown),
                old.schedule(), old.permissionNode()), "limits");
    }

    private void updateAcquireOption(
            UnaryOperator<AcquireOfferOption> mutation,
            String path
    ) {
        List<AcquireOfferOption> options = new ArrayList<>(
                draft.candidate().acquireOptions());
        if (options.isEmpty()) {
            return;
        }
        int index = Math.min(selectedAcquireIndex,
                options.size() - 1);
        options.set(index, mutation.apply(options.get(index)));
        draft.update("acquireOptions." + index + "." + path,
                current -> copy(current, null, null, null, null, null,
                        null, options, null, null));
    }

    private void updateAcquireCostItem(String itemId) {
        updateAcquireCost(component -> new OfferItemComponent(
                component.componentId(), itemId,
                component.count(), component.exactNbt()), "itemId");
    }

    private void updateAcquireCostCount(int count) {
        updateAcquireCost(component -> new OfferItemComponent(
                component.componentId(), component.itemId(),
                count, component.exactNbt()), "count");
    }

    private int selectedAcquireCostCount() {
        List<AcquireOfferOption> options =
                draft.candidate().acquireOptions();
        if (options.isEmpty()) {
            return 0;
        }
        AcquireOfferOption option = options.get(Math.min(
                selectedAcquireIndex, options.size() - 1));
        if (option.itemCosts().isEmpty()) {
            return 0;
        }
        return option.itemCosts().get(Math.min(
                selectedAcquireCostIndex,
                option.itemCosts().size() - 1)).count();
    }

    private void updateAcquireCostNbt(String nbt) {
        updateAcquireCost(component -> new OfferItemComponent(
                component.componentId(), component.itemId(),
                component.count(), nbt), "exactNbt");
    }

    private void updateAcquireCost(
            UnaryOperator<OfferItemComponent> mutation,
            String path
    ) {
        List<AcquireOfferOption> options = new ArrayList<>(
                draft.candidate().acquireOptions());
        if (options.isEmpty()) {
            return;
        }
        int optionIndex = Math.min(
                selectedAcquireIndex, options.size() - 1);
        AcquireOfferOption old = options.get(optionIndex);
        if (old.itemCosts().isEmpty()) {
            return;
        }
        int componentIndex = Math.min(
                selectedAcquireCostIndex,
                old.itemCosts().size() - 1);
        List<OfferItemComponent> costs =
                new ArrayList<>(old.itemCosts());
        costs.set(componentIndex,
                mutation.apply(costs.get(componentIndex)));
        options.set(optionIndex, new AcquireOfferOption(
                old.optionId(), old.label(), old.free(),
                old.moneyCostPresent(), old.moneyCostMinorUnits(),
                costs, old.outputMultiplier(), old.limits(),
                old.schedule(), old.permissionNode()));
        draft.update("acquireOptions." + optionIndex
                        + ".itemCosts." + componentIndex + "." + path,
                current -> copy(current, null, null, null, null, null,
                        null, options, null, null));
    }

    private void moveAcquireCost(int direction) {
        List<AcquireOfferOption> options = new ArrayList<>(
                draft.candidate().acquireOptions());
        if (options.isEmpty()) {
            return;
        }
        int optionIndex = Math.min(
                selectedAcquireIndex, options.size() - 1);
        AcquireOfferOption old = options.get(optionIndex);
        List<OfferItemComponent> costs =
                new ArrayList<>(old.itemCosts());
        int target = selectedAcquireCostIndex + direction;
        if (selectedAcquireCostIndex < 0
                || selectedAcquireCostIndex >= costs.size()
                || target < 0 || target >= costs.size()) {
            return;
        }
        java.util.Collections.swap(costs,
                selectedAcquireCostIndex, target);
        selectedAcquireCostIndex = target;
        options.set(optionIndex, new AcquireOfferOption(
                old.optionId(), old.label(), old.free(),
                old.moneyCostPresent(), old.moneyCostMinorUnits(),
                costs, old.outputMultiplier(), old.limits(),
                old.schedule(), old.permissionNode()));
        draft.clearFieldValues("acquireOptions." + optionIndex
                + ".itemCosts");
        draft.update("acquireOptions." + optionIndex + ".itemCosts",
                current -> copy(current, null, null, null, null, null,
                        null, options, null, null));
    }

    private void confirmRemoveAcquireCost() {
        List<AcquireOfferOption> options =
                draft.candidate().acquireOptions();
        if (options.isEmpty()) {
            return;
        }
        AcquireOfferOption option = options.get(Math.min(
                selectedAcquireIndex, options.size() - 1));
        if (option.itemCosts().isEmpty()) {
            return;
        }
        OfferItemComponent component =
                option.itemCosts().get(Math.min(
                        selectedAcquireCostIndex,
                        option.itemCosts().size() - 1));
        boolean acknowledged = draft.baseline().acquireOptions()
                .stream().filter(baseline ->
                        baseline.optionId().equals(option.optionId()))
                .flatMap(baseline -> baseline.itemCosts().stream())
                .anyMatch(value -> value.componentId().equals(
                        component.componentId()));
        if (!acknowledged) {
            removeSelectedAcquireCost();
            rebuild();
            return;
        }
        confirmation = destructiveConfirmation(
                "remove_component_title",
                "remove_component_help",
                this::removeSelectedAcquireCost);
    }

    private void removeSelectedAcquireCost() {
        List<AcquireOfferOption> options = new ArrayList<>(
                draft.candidate().acquireOptions());
        if (options.isEmpty()) {
            return;
        }
        int optionIndex = Math.min(
                selectedAcquireIndex, options.size() - 1);
        AcquireOfferOption old = options.get(optionIndex);
        List<OfferItemComponent> costs =
                new ArrayList<>(old.itemCosts());
        if (costs.isEmpty()) {
            return;
        }
        costs.remove(Math.min(selectedAcquireCostIndex,
                costs.size() - 1));
        selectedAcquireCostIndex = Math.max(0,
                selectedAcquireCostIndex - 1);
        options.set(optionIndex, new AcquireOfferOption(
                old.optionId(), old.label(), old.free(),
                old.moneyCostPresent(), old.moneyCostMinorUnits(),
                costs, old.outputMultiplier(), old.limits(),
                old.schedule(), old.permissionNode()));
        draft.clearFieldValues("acquireOptions." + optionIndex
                + ".itemCosts");
        draft.update("acquireOptions." + optionIndex + ".itemCosts",
                current -> copy(current, null, null, null, null, null,
                        null, options, null, null));
    }

    private void addHeldAcquireCost() {
        OfferItemComponent held = heldComponent("cost_"
                + System.nanoTime());
        List<AcquireOfferOption> options = new ArrayList<>(
                draft.candidate().acquireOptions());
        if (held == null || options.isEmpty()) {
            return;
        }
        int index = Math.min(selectedAcquireIndex,
                options.size() - 1);
        AcquireOfferOption old = options.get(index);
        if (old.free()) {
            resultMessage = Component.translatable(
                    "gui.futureshops.offer_editor.free_has_no_cost");
            resultSuccess = false;
            return;
        }
        List<OfferItemComponent> costs =
                new ArrayList<>(old.itemCosts());
        costs.add(held);
        List<OfferItemComponent> normalizedCosts =
                OfferComponentNormalizer.normalize(costs);
        options.set(index, new AcquireOfferOption(
                old.optionId(), old.label(), false,
                old.moneyCostPresent(), old.moneyCostMinorUnits(),
                normalizedCosts, old.outputMultiplier(), old.limits(),
                old.schedule(), old.permissionNode()));
        draft.clearFieldValues("acquireOptions." + index
                + ".itemCosts");
        draft.update("acquireOptions." + index + ".itemCosts",
                current -> copy(current, null, null, null, null, null,
                        null, options, null, null));
    }

    private void removeAcquireOption() {
        List<AcquireOfferOption> options = new ArrayList<>(
                draft.candidate().acquireOptions());
        if (!options.isEmpty()) {
            options.remove(Math.min(
                    selectedAcquireIndex, options.size() - 1));
            selectedAcquireIndex = Math.max(0,
                    selectedAcquireIndex - 1);
            draft.clearFieldValues("acquireOptions");
            draft.update("acquireOptions", current -> copy(
                    current, null, null, null, null, null,
                    null, options, null, null));
        }
    }

    private void confirmRemoveAcquireOption() {
        List<AcquireOfferOption> options =
                draft.candidate().acquireOptions();
        if (options.isEmpty()) {
            return;
        }
        String optionId = options.get(Math.min(
                selectedAcquireIndex, options.size() - 1)).optionId();
        boolean acknowledged = draft.baseline().acquireOptions().stream()
                .anyMatch(option -> option.optionId().equals(optionId));
        if (!acknowledged) {
            removeAcquireOption();
            rebuild();
            return;
        }
        confirmation = destructiveConfirmation(
                "remove_option_title", "remove_option_help",
                this::removeAcquireOption);
    }

    private void removeLastAcquireCost() {
        updateAcquireOption(old -> {
            List<OfferItemComponent> costs =
                    new ArrayList<>(old.itemCosts());
            if (!costs.isEmpty()) {
                costs.remove(costs.size() - 1);
            }
            return new AcquireOfferOption(
                    old.optionId(), old.label(), old.free(),
                    old.moneyCostPresent(), old.moneyCostMinorUnits(),
                    costs, old.outputMultiplier(), old.limits(),
                    old.schedule(), old.permissionNode());
        }, "itemCosts");
    }

    private void duplicateAcquireOption() {
        List<AcquireOfferOption> options = new ArrayList<>(
                draft.candidate().acquireOptions());
        if (options.isEmpty()) {
            return;
        }
        AcquireOfferOption source = options.get(Math.min(
                selectedAcquireIndex, options.size() - 1));
        AcquireOfferOption duplicate = new AcquireOfferOption(
                uniqueOptionId(source.optionId() + "_copy"),
                source.label(), source.free(),
                source.moneyCostPresent(),
                source.moneyCostMinorUnits(), source.itemCosts(),
                source.outputMultiplier(), source.limits(),
                source.schedule(), source.permissionNode());
        options.add(selectedAcquireIndex + 1, duplicate);
        selectedAcquireIndex++;
        draft.clearFieldValues("acquireOptions");
        draft.update("acquireOptions", current -> copy(
                current, null, null, null, null, null,
                null, options, null, null));
    }

    private void moveAcquireOption(int direction) {
        List<AcquireOfferOption> options = new ArrayList<>(
                draft.candidate().acquireOptions());
        int target = selectedAcquireIndex + direction;
        if (selectedAcquireIndex < 0
                || selectedAcquireIndex >= options.size()
                || target < 0 || target >= options.size()) {
            return;
        }
        java.util.Collections.swap(options, selectedAcquireIndex, target);
        selectedAcquireIndex = target;
        draft.clearFieldValues("acquireOptions");
        draft.update("acquireOptions", current -> copy(
                current, null, null, null, null, null,
                null, options, null, null));
    }

    private void addSellOption() {
        OfferItemComponent held = heldComponent("input_1");
        if (held == null) {
            return;
        }
        List<SellOfferOption> options = new ArrayList<>(
                draft.candidate().sellOptions());
        options.add(new SellOfferOption(
                "sell_" + (options.size() + 1),
                "Sell to Shop", List.of(held), 1L, 0L,
                OfferLimitPolicy.defaults(), OfferSchedule.always(), ""));
        selectedSellIndex = options.size() - 1;
        draft.update("sellOptions", current -> copy(
                current, null, null, null, null, null,
                null, null, options, null));
    }

    private void updateSellPayout(long payout) {
        updateSellOption(old -> new SellOfferOption(
                old.optionId(), old.label(), old.itemInputs(),
                payout, old.capacity(), old.limits(),
                old.schedule(), old.permissionNode()), "moneyPayout");
    }

    private void updateSellLabel(String label) {
        updateSellOption(old -> new SellOfferOption(
                old.optionId(), label, old.itemInputs(),
                old.moneyPayoutMinorUnits(), old.capacity(), old.limits(),
                old.schedule(), old.permissionNode()), "label");
    }

    private void updateSellCapacity(long capacity) {
        updateSellOption(old -> new SellOfferOption(
                old.optionId(), old.label(), old.itemInputs(),
                old.moneyPayoutMinorUnits(), capacity, old.limits(),
                old.schedule(), old.permissionNode()), "capacity");
    }

    private void updateSellPermission(String permission) {
        updateSellOption(old -> new SellOfferOption(
                old.optionId(), old.label(), old.itemInputs(),
                old.moneyPayoutMinorUnits(), old.capacity(), old.limits(),
                old.schedule(), permission), "permission");
    }

    private void updateSellSchedule(Long starts, Long ends) {
        updateSellOption(old -> new SellOfferOption(
                old.optionId(), old.label(), old.itemInputs(),
                old.moneyPayoutMinorUnits(), old.capacity(), old.limits(),
                new OfferSchedule(
                        starts == null
                                ? old.schedule().startsAtEpoch() : starts,
                        ends == null
                                ? old.schedule().endsAtEpoch() : ends),
                old.permissionNode()),
                starts == null ? "endsAtEpoch" : "startsAtEpoch");
    }

    private void updateSellLimits(
            Integer maximum,
            Long lifetime,
            Long period,
            Long periodSeconds,
            Long cooldown
    ) {
        updateSellOption(old -> new SellOfferOption(
                old.optionId(), old.label(), old.itemInputs(),
                old.moneyPayoutMinorUnits(), old.capacity(),
                updatedLimits(old.limits(), maximum, lifetime,
                        period, periodSeconds, cooldown),
                old.schedule(), old.permissionNode()), "limits");
    }

    private void updateSellOption(
            UnaryOperator<SellOfferOption> mutation,
            String path
    ) {
        List<SellOfferOption> options = new ArrayList<>(
                draft.candidate().sellOptions());
        if (options.isEmpty()) {
            return;
        }
        int index = Math.min(selectedSellIndex, options.size() - 1);
        options.set(index, mutation.apply(options.get(index)));
        draft.update("sellOptions." + index + "." + path,
                current -> copy(current, null, null, null, null, null,
                        null, null, options, null));
    }

    private void updateSellInputItem(String itemId) {
        updateSellInput(component -> new OfferItemComponent(
                component.componentId(), itemId,
                component.count(), component.exactNbt()), "itemId");
    }

    private void updateSellInputCount(int count) {
        updateSellInput(component -> new OfferItemComponent(
                component.componentId(), component.itemId(),
                count, component.exactNbt()), "count");
    }

    private int selectedSellInputCount() {
        List<SellOfferOption> options =
                draft.candidate().sellOptions();
        if (options.isEmpty()) {
            return 0;
        }
        SellOfferOption option = options.get(Math.min(
                selectedSellIndex, options.size() - 1));
        if (option.itemInputs().isEmpty()) {
            return 0;
        }
        return option.itemInputs().get(Math.min(
                selectedSellInputIndex,
                option.itemInputs().size() - 1)).count();
    }

    private void updateSellInputNbt(String nbt) {
        updateSellInput(component -> new OfferItemComponent(
                component.componentId(), component.itemId(),
                component.count(), nbt), "exactNbt");
    }

    private void updateSellInput(
            UnaryOperator<OfferItemComponent> mutation,
            String path
    ) {
        List<SellOfferOption> options = new ArrayList<>(
                draft.candidate().sellOptions());
        if (options.isEmpty()) {
            return;
        }
        int optionIndex = Math.min(
                selectedSellIndex, options.size() - 1);
        SellOfferOption old = options.get(optionIndex);
        if (old.itemInputs().isEmpty()) {
            return;
        }
        int componentIndex = Math.min(
                selectedSellInputIndex,
                old.itemInputs().size() - 1);
        List<OfferItemComponent> inputs =
                new ArrayList<>(old.itemInputs());
        inputs.set(componentIndex,
                mutation.apply(inputs.get(componentIndex)));
        options.set(optionIndex, new SellOfferOption(
                old.optionId(), old.label(), inputs,
                old.moneyPayoutMinorUnits(), old.capacity(),
                old.limits(), old.schedule(),
                old.permissionNode()));
        draft.update("sellOptions." + optionIndex
                        + ".itemInputs." + componentIndex
                        + "." + path,
                current -> copy(current, null, null, null, null, null,
                        null, null, options, null));
    }

    private void moveSellInput(int direction) {
        List<SellOfferOption> options = new ArrayList<>(
                draft.candidate().sellOptions());
        if (options.isEmpty()) {
            return;
        }
        int optionIndex = Math.min(
                selectedSellIndex, options.size() - 1);
        SellOfferOption old = options.get(optionIndex);
        List<OfferItemComponent> inputs =
                new ArrayList<>(old.itemInputs());
        int target = selectedSellInputIndex + direction;
        if (selectedSellInputIndex < 0
                || selectedSellInputIndex >= inputs.size()
                || target < 0 || target >= inputs.size()) {
            return;
        }
        java.util.Collections.swap(inputs,
                selectedSellInputIndex, target);
        selectedSellInputIndex = target;
        options.set(optionIndex, new SellOfferOption(
                old.optionId(), old.label(), inputs,
                old.moneyPayoutMinorUnits(), old.capacity(),
                old.limits(), old.schedule(),
                old.permissionNode()));
        draft.clearFieldValues("sellOptions." + optionIndex
                + ".itemInputs");
        draft.update("sellOptions." + optionIndex + ".itemInputs",
                current -> copy(current, null, null, null, null, null,
                        null, null, options, null));
    }

    private void confirmRemoveSellInput() {
        List<SellOfferOption> options =
                draft.candidate().sellOptions();
        if (options.isEmpty()) {
            return;
        }
        SellOfferOption option = options.get(Math.min(
                selectedSellIndex, options.size() - 1));
        if (option.itemInputs().isEmpty()) {
            return;
        }
        OfferItemComponent component =
                option.itemInputs().get(Math.min(
                        selectedSellInputIndex,
                        option.itemInputs().size() - 1));
        boolean acknowledged = draft.baseline().sellOptions()
                .stream().filter(baseline ->
                        baseline.optionId().equals(option.optionId()))
                .flatMap(baseline -> baseline.itemInputs().stream())
                .anyMatch(value -> value.componentId().equals(
                        component.componentId()));
        if (!acknowledged) {
            removeSelectedSellInput();
            rebuild();
            return;
        }
        confirmation = destructiveConfirmation(
                "remove_component_title",
                "remove_component_help",
                this::removeSelectedSellInput);
    }

    private void removeSelectedSellInput() {
        List<SellOfferOption> options = new ArrayList<>(
                draft.candidate().sellOptions());
        if (options.isEmpty()) {
            return;
        }
        int optionIndex = Math.min(
                selectedSellIndex, options.size() - 1);
        SellOfferOption old = options.get(optionIndex);
        List<OfferItemComponent> inputs =
                new ArrayList<>(old.itemInputs());
        if (inputs.isEmpty()) {
            return;
        }
        inputs.remove(Math.min(selectedSellInputIndex,
                inputs.size() - 1));
        selectedSellInputIndex = Math.max(0,
                selectedSellInputIndex - 1);
        options.set(optionIndex, new SellOfferOption(
                old.optionId(), old.label(), inputs,
                old.moneyPayoutMinorUnits(), old.capacity(),
                old.limits(), old.schedule(),
                old.permissionNode()));
        draft.clearFieldValues("sellOptions." + optionIndex
                + ".itemInputs");
        draft.update("sellOptions." + optionIndex + ".itemInputs",
                current -> copy(current, null, null, null, null, null,
                        null, null, options, null));
    }

    private void addHeldSellInput() {
        OfferItemComponent held = heldComponent(
                "input_" + System.nanoTime());
        List<SellOfferOption> options = new ArrayList<>(
                draft.candidate().sellOptions());
        if (held == null || options.isEmpty()) {
            return;
        }
        int index = Math.min(selectedSellIndex, options.size() - 1);
        SellOfferOption old = options.get(index);
        List<OfferItemComponent> inputs =
                new ArrayList<>(old.itemInputs());
        inputs.add(held);
        List<OfferItemComponent> normalizedInputs =
                OfferComponentNormalizer.normalize(inputs);
        options.set(index, new SellOfferOption(
                old.optionId(), old.label(), normalizedInputs,
                old.moneyPayoutMinorUnits(), old.capacity(),
                old.limits(), old.schedule(),
                old.permissionNode()));
        draft.clearFieldValues("sellOptions." + index
                + ".itemInputs");
        draft.update("sellOptions." + index + ".itemInputs",
                current -> copy(current, null, null, null, null, null,
                        null, null, options, null));
    }

    private void removeSellOption() {
        List<SellOfferOption> options = new ArrayList<>(
                draft.candidate().sellOptions());
        if (!options.isEmpty()) {
            options.remove(Math.min(
                    selectedSellIndex, options.size() - 1));
            selectedSellIndex = Math.max(0,
                    selectedSellIndex - 1);
            draft.clearFieldValues("sellOptions");
            draft.update("sellOptions", current -> copy(
                    current, null, null, null, null, null,
                    null, null, options, null));
        }
    }

    private void confirmRemoveSellOption() {
        List<SellOfferOption> options = draft.candidate().sellOptions();
        if (options.isEmpty()) {
            return;
        }
        String optionId = options.get(Math.min(
                selectedSellIndex, options.size() - 1)).optionId();
        boolean acknowledged = draft.baseline().sellOptions().stream()
                .anyMatch(option -> option.optionId().equals(optionId));
        if (!acknowledged) {
            removeSellOption();
            rebuild();
            return;
        }
        confirmation = destructiveConfirmation(
                "remove_option_title", "remove_option_help",
                this::removeSellOption);
    }

    private void removeLastSellInput() {
        updateSellOption(old -> {
            List<OfferItemComponent> inputs =
                    new ArrayList<>(old.itemInputs());
            if (!inputs.isEmpty()) {
                inputs.remove(inputs.size() - 1);
            }
            return new SellOfferOption(
                    old.optionId(), old.label(), inputs,
                    old.moneyPayoutMinorUnits(), old.capacity(),
                    old.limits(), old.schedule(),
                    old.permissionNode());
        }, "itemInputs");
    }

    private void duplicateSellOption() {
        List<SellOfferOption> options = new ArrayList<>(
                draft.candidate().sellOptions());
        if (options.isEmpty()) {
            return;
        }
        SellOfferOption source = options.get(Math.min(
                selectedSellIndex, options.size() - 1));
        SellOfferOption duplicate = new SellOfferOption(
                uniqueOptionId(source.optionId() + "_copy"),
                source.label(), source.itemInputs(),
                source.moneyPayoutMinorUnits(), source.capacity(),
                source.limits(), source.schedule(),
                source.permissionNode());
        options.add(selectedSellIndex + 1, duplicate);
        selectedSellIndex++;
        draft.clearFieldValues("sellOptions");
        draft.update("sellOptions", current -> copy(
                current, null, null, null, null, null,
                null, null, options, null));
    }

    private void moveSellOption(int direction) {
        List<SellOfferOption> options = new ArrayList<>(
                draft.candidate().sellOptions());
        int target = selectedSellIndex + direction;
        if (selectedSellIndex < 0 || selectedSellIndex >= options.size()
                || target < 0 || target >= options.size()) {
            return;
        }
        java.util.Collections.swap(options, selectedSellIndex, target);
        selectedSellIndex = target;
        draft.clearFieldValues("sellOptions");
        draft.update("sellOptions", current -> copy(
                current, null, null, null, null, null,
                null, null, options, null));
    }

    private void updateStock(Long quantity, Long refresh) {
        OfferStockPolicy old = draft.candidate().stockPolicy();
        OfferStockPolicy updated = new OfferStockPolicy(
                old.type(),
                quantity == null ? old.quantity() : quantity,
                refresh == null ? old.refreshSeconds() : refresh);
        draft.update("stock", current -> copy(
                current, null, null, null, null, null,
                null, null, null, null, updated, null, null));
    }

    private void toggleUnlimited() {
        OfferStockPolicy current = draft.candidate().stockPolicy();
        OfferStockPolicy updated = current.type()
                == OfferStockPolicy.Type.UNLIMITED
                ? OfferStockPolicy.limited(
                Math.max(1L, current.quantity()),
                current.refreshSeconds())
                : OfferStockPolicy.unlimited();
        draft.update("stock", listing -> copy(
                listing, null, null, null, null, null,
                null, null, null, null, updated, null, null));
    }

    private void updateLimits(
            Integer maximum,
            Long lifetime,
            Long period,
            Long periodSeconds,
            Long cooldown
    ) {
        OfferLimitPolicy old = draft.candidate().limits();
        OfferLimitPolicy updated = new OfferLimitPolicy(
                maximum == null ? old.maximumPerRequest() : maximum,
                lifetime == null ? old.lifetimeLimit() : lifetime,
                period == null ? old.periodLimit() : period,
                periodSeconds == null
                        ? old.periodSeconds() : periodSeconds,
                cooldown == null ? old.cooldownSeconds() : cooldown);
        draft.update("limits", current -> copy(
                current, null, null, null, null, null,
                null, null, null, null, null, updated, null));
    }

    private static OfferLimitPolicy updatedLimits(
            OfferLimitPolicy old,
            Integer maximum,
            Long lifetime,
            Long period,
            Long periodSeconds,
            Long cooldown
    ) {
        return new OfferLimitPolicy(
                maximum == null ? old.maximumPerRequest() : maximum,
                lifetime == null ? old.lifetimeLimit() : lifetime,
                period == null ? old.periodLimit() : period,
                periodSeconds == null
                        ? old.periodSeconds() : periodSeconds,
                cooldown == null ? old.cooldownSeconds() : cooldown);
    }

    private void updateSchedule(Long starts, Long ends) {
        OfferSchedule old = draft.candidate().schedule();
        OfferSchedule updated = new OfferSchedule(
                starts == null ? old.startsAtEpoch() : starts,
                ends == null ? old.endsAtEpoch() : ends);
        draft.update("schedule", current -> copy(
                current, null, null, null, null, null,
                null, null, null, null, null, null, updated));
    }

    private void updateExpiry(long expiry) {
        draft.update("expiresAtEpoch", current ->
                new ServerShopOfferListing(
                        current.listingId(), current.revision(),
                        current.displayName(), current.description(),
                        current.categoryId(), current.iconItemId(),
                        current.iconNbt(), current.active(), expiry,
                        current.permissionNode(), current.outputs(),
                        current.acquireOptions(), current.sellOptions(),
                        current.stockPolicy(), current.limits(),
                        current.schedule(),
                        current.bundleComparisons()));
    }

    private void updateComparison(
            String componentId,
            String input
    ) {
        String[] parts = input.strip().split("\\s+");
        List<com.enviouse.futureshops.catalog.offer
                .OfferBundleComparison> comparisons =
                new ArrayList<>(draft.candidate()
                        .bundleComparisons());
        comparisons.removeIf(value -> value.componentId()
                .equals(componentId));
        if (parts.length == 2
                && !parts[0].isBlank() && !parts[1].isBlank()) {
            comparisons.add(new com.enviouse.futureshops.catalog.offer
                    .OfferBundleComparison(
                    componentId, parts[0], parts[1]));
        }
        draft.update("bundleComparisons." + componentId,
                current -> copy(current, null, null, null, null, null,
                        null, null, null, comparisons));
    }

    private OfferItemComponent heldComponent(String componentId) {
        if (minecraft == null || minecraft.player == null) {
            return null;
        }
        ItemStack held = minecraft.player.getMainHandItem();
        if (held.isEmpty()) {
            return null;
        }
        ResourceLocation identifier =
                ForgeRegistries.ITEMS.getKey(held.getItem());
        if (identifier == null) {
            return null;
        }
        return new OfferItemComponent(
                componentId.replaceAll("[^a-z0-9_.:/-]", "_"),
                identifier.toString(), Math.max(1, held.getCount()),
                held.getTag() == null ? "" : held.getTag().toString());
    }

    private static String uniqueComponentId(
            List<OfferItemComponent> components,
            String requested
    ) {
        String candidate = requested;
        int suffix = 2;
        while (containsComponentId(components, candidate)) {
            candidate = requested + "_" + suffix++;
        }
        return candidate;
    }

    private static boolean containsComponentId(
            List<OfferItemComponent> components,
            String candidate
    ) {
        return components.stream().anyMatch(component ->
                component.componentId().equals(candidate));
    }

    private String uniqueOptionId(String requested) {
        String candidate = requested;
        int suffix = 2;
        while (containsOptionId(candidate)) {
            candidate = requested + "_" + suffix++;
        }
        return candidate;
    }

    private boolean containsOptionId(String candidate) {
        return draft.candidate().acquireOptions().stream()
                .anyMatch(option -> option.optionId().equals(candidate))
                || draft.candidate().sellOptions().stream()
                .anyMatch(option -> option.optionId().equals(candidate));
    }

    private static ServerShopOfferListing copy(
            ServerShopOfferListing current,
            String displayName,
            String description,
            String category,
            String permission,
            Boolean active,
            List<OfferItemComponent> outputs,
            List<AcquireOfferOption> acquire,
            List<SellOfferOption> sell,
            List<com.enviouse.futureshops.catalog.offer
                    .OfferBundleComparison> comparisons
    ) {
        return copy(current, displayName, description, category,
                permission, active, outputs, acquire, sell,
                comparisons, null, null, null);
    }

    private static ServerShopOfferListing copy(
            ServerShopOfferListing current,
            String displayName,
            String description,
            String category,
            String permission,
            Boolean active,
            List<OfferItemComponent> outputs,
            List<AcquireOfferOption> acquire,
            List<SellOfferOption> sell,
            List<com.enviouse.futureshops.catalog.offer
                    .OfferBundleComparison> comparisons,
            OfferStockPolicy stock,
            OfferLimitPolicy limits,
            OfferSchedule schedule
    ) {
        return new ServerShopOfferListing(
                current.listingId(), current.revision(),
                displayName == null
                        ? current.displayName() : displayName,
                description == null
                        ? current.description() : description,
                category == null ? current.categoryId() : category,
                current.iconItemId(), current.iconNbt(),
                active == null ? current.active() : active,
                current.expiresAtEpoch(),
                permission == null
                        ? current.permissionNode() : permission,
                outputs == null ? current.outputs() : outputs,
                acquire == null ? current.acquireOptions() : acquire,
                sell == null ? current.sellOptions() : sell,
                stock == null ? current.stockPolicy() : stock,
                limits == null ? current.limits() : limits,
                schedule == null ? current.schedule() : schedule,
                comparisons == null
                        ? current.bundleComparisons() : comparisons);
    }

    private static ServerShopOfferListing blankListing() {
        String id = "offer_" + UUID.randomUUID();
        return new ServerShopOfferListing(
                id, 0L, "", "", "all", "", "",
                true, 0L, "", List.of(), List.of(), List.of(),
                OfferStockPolicy.unlimited(),
                OfferLimitPolicy.defaults(), OfferSchedule.always(),
                List.of());
    }

    private static ServerShopOfferListing withListingId(
            ServerShopOfferListing current,
            String listingId
    ) {
        return new ServerShopOfferListing(
                listingId, 0L, current.displayName(),
                current.description(), current.categoryId(),
                current.iconItemId(), current.iconNbt(),
                current.active(), current.expiresAtEpoch(),
                current.permissionNode(), current.outputs(),
                current.acquireOptions(), current.sellOptions(),
                current.stockPolicy(), current.limits(),
                current.schedule(), current.bundleComparisons());
    }

    private static ServerShopOfferListing withCategory(
            ServerShopOfferListing current,
            String categoryId
    ) {
        String category = categoryId == null || categoryId.isBlank()
                ? current.categoryId() : categoryId;
        return copy(current, null, null, category,
                null, null, null, null, null, null);
    }

    private static long parseLong(String value, long invalid) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return invalid;
        }
    }

    private static long parseMoneyMinor(
            String value,
            long invalid
    ) {
        try {
            return EconomyCommandUtil.parseAmountToMinorUnits(
                    value, ShopClientState.getCurrencyDecimals());
        } catch (IllegalArgumentException exception) {
            return invalid;
        }
    }

    private int advancedFieldLabelWidth() {
        if (!advancedMode) {
            return 0;
        }
        int desired = Math.min(190,
                Math.max(92, contentWidth / 3));
        return Math.max(0,
                Math.min(desired, contentWidth - 48));
    }

    private static int parseInt(String value, int invalid) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return invalid;
        }
    }

    private int sectionY(int offset) {
        return contentTop + offset - draft.scrollPosition();
    }

    private int footerTop() {
        return height - (width < 430 ? 48 : 26);
    }

    private int maximumScroll() {
        int contentHeight = switch (draft.section()) {
            case GENERAL -> 340;
            case OUTPUTS -> 360;
            case GET_OPTIONS -> 690;
            case SELL_OPTIONS -> 690;
            case STOCK_AND_LIMITS -> 250;
            case SCHEDULE_AND_PERMISSIONS -> 120;
            case BUNDLE_VALUE -> 390;
            case PREVIEW -> 420;
        };
        return Math.max(0, contentHeight
                - Math.max(80, height - contentTop
                - (width < 430 ? 64 : 40)));
    }

    private static Component sectionLabel(
            OfferEditorDraft.Section section
    ) {
        return Component.translatable(
                "gui.futureshops.offer_editor.section."
                        + section.name()
                        .toLowerCase(java.util.Locale.ROOT));
    }

    private static Component sectionHelp(
            OfferEditorDraft.Section section
    ) {
        return Component.translatable(
                "gui.futureshops.offer_editor.help.section."
                        + section.name()
                        .toLowerCase(java.util.Locale.ROOT));
    }

    private static Tooltip help(String key) {
        return Tooltip.create(Component.translatable(
                OfferEditorControlRegistry.helpKey(key)));
    }

    private static Component fieldHelp(String path) {
        return Component.translatable(
                OfferEditorControlRegistry.fieldHelpKey(path));
    }

    private Component disabledSaveHelp(String action) {
        List<com.enviouse.futureshops.catalog.offer.OfferValidationIssue>
                issues = draft.issues().stream()
                .filter(issue -> issue.severity()
                        == com.enviouse.futureshops.catalog.offer
                        .OfferValidationIssue.Severity.ERROR)
                .toList();
        if (issues.isEmpty()) {
            return Component.translatable(
                    OfferEditorControlRegistry.helpKey(action));
        }
        return Component.translatable(
                "gui.futureshops.offer_editor.save_blocked",
                validationMessage(issues.get(0)), issues.size() - 1);
    }

    private static Component validationMessage(
            com.enviouse.futureshops.catalog.offer.OfferValidationIssue issue
    ) {
        String registered = registeredFieldPath(issue.path());
        return Component.translatable(
                "gui.futureshops.offer_editor.validation",
                Component.translatable(
                        OfferEditorControlRegistry.fieldLabelKey(registered)),
                Component.translatable(
                        "gui.futureshops.offer_editor.validation."
                                + issue.code()));
    }

    private static boolean numericField(String path) {
        return path.endsWith("moneyCost")
                || path.endsWith("moneyPayout")
                || path.endsWith("outputMultiplier")
                || path.endsWith("capacity")
                || path.endsWith("count")
                || path.endsWith("quantity")
                || path.endsWith("refreshSeconds")
                || path.endsWith("maximumPerRequest")
                || path.endsWith("lifetime")
                || path.endsWith("periodQuantity")
                || path.endsWith("periodSeconds")
                || path.endsWith("cooldownSeconds")
                || path.endsWith("startsAtEpoch")
                || path.endsWith("endsAtEpoch")
                || path.endsWith("expiresAtEpoch");
    }

    private static boolean moneyField(String path) {
        return path.endsWith("moneyCost")
                || path.endsWith("moneyPayout");
    }

    private static String registeredFieldPath(String path) {
        if (path.startsWith("outputs.")) {
            if (path.endsWith(".itemId")) {
                return "outputs.itemId";
            }
            if (path.endsWith(".count")) {
                return "outputs.count";
            }
            if (path.endsWith(".exactNbt")) {
                return "outputs.exactNbt";
            }
        }
        if (path.startsWith("acquireOptions.")) {
            if (path.contains(".itemCosts.")) {
                if (path.endsWith(".itemId")) {
                    return "acquireOptions.itemCosts.itemId";
                }
                if (path.endsWith(".count")) {
                    return "acquireOptions.itemCosts.count";
                }
                return "acquireOptions.itemCosts.exactNbt";
            }
            if (path.endsWith(".label")) {
                return "acquireOptions.label";
            }
            if (path.endsWith(".outputMultiplier")) {
                return "acquireOptions.outputMultiplier";
            }
            if (path.endsWith(".permission")) {
                return "acquireOptions.permission";
            }
            if (path.endsWith(".startsAtEpoch")) {
                return "acquireOptions.startsAtEpoch";
            }
            if (path.endsWith(".endsAtEpoch")) {
                return "acquireOptions.endsAtEpoch";
            }
            if (path.endsWith(".maximumPerRequest")) {
                return "acquireOptions.maximumPerRequest";
            }
            if (path.endsWith(".lifetime")) {
                return "acquireOptions.lifetime";
            }
            if (path.endsWith(".periodQuantity")) {
                return "acquireOptions.periodQuantity";
            }
            if (path.endsWith(".periodSeconds")) {
                return "acquireOptions.periodSeconds";
            }
            if (path.endsWith(".cooldownSeconds")) {
                return "acquireOptions.cooldownSeconds";
            }
            return "acquireOptions.moneyCost";
        }
        if (path.startsWith("sellOptions.")) {
            if (path.contains(".itemInputs.")) {
                if (path.endsWith(".itemId")) {
                    return "sellOptions.itemInputs.itemId";
                }
                if (path.endsWith(".count")) {
                    return "sellOptions.itemInputs.count";
                }
                return "sellOptions.itemInputs.exactNbt";
            }
            if (path.endsWith(".label")) {
                return "sellOptions.label";
            }
            if (path.endsWith(".capacity")) {
                return "sellOptions.capacity";
            }
            if (path.endsWith(".permission")) {
                return "sellOptions.permission";
            }
            if (path.endsWith(".startsAtEpoch")) {
                return "sellOptions.startsAtEpoch";
            }
            if (path.endsWith(".endsAtEpoch")) {
                return "sellOptions.endsAtEpoch";
            }
            if (path.endsWith(".maximumPerRequest")) {
                return "sellOptions.maximumPerRequest";
            }
            if (path.endsWith(".lifetime")) {
                return "sellOptions.lifetime";
            }
            if (path.endsWith(".periodQuantity")) {
                return "sellOptions.periodQuantity";
            }
            if (path.endsWith(".periodSeconds")) {
                return "sellOptions.periodSeconds";
            }
            if (path.endsWith(".cooldownSeconds")) {
                return "sellOptions.cooldownSeconds";
            }
            return "sellOptions.moneyPayout";
        }
        if (path.startsWith("bundleComparisons")) {
            return "bundleComparisons";
        }
        if (path.equals("general")) {
            return "displayName";
        }
        if (OfferEditorControlRegistry.fields().contains(path)) {
            return path;
        }
        if (path.startsWith("options")) {
            return "acquireOptions.label";
        }
        if (path.startsWith("revision")
                || path.startsWith("listing")) {
            return "displayName";
        }
        return "displayName";
    }

    private record EditBinding(
            String path,
            String registeredPath,
            EditBox field,
            Consumer<String> save,
            boolean numeric
    ) {
    }

    private record PlayerShopTarget(
            BlockPos shopPos,
            int listingIndex
    ) {
        private PlayerShopTarget {
            java.util.Objects.requireNonNull(shopPos, "shopPos");
            if (listingIndex < 0) {
                throw new IllegalArgumentException(
                        "Player shop listing index is invalid");
            }
        }
    }

    private enum SimpleStep {
        BASICS("basics"),
        ITEMS("items"),
        TRADE("trade"),
        REVIEW("review");

        private final String key;

        SimpleStep(String key) {
            this.key = key;
        }

        private String key() {
            return key;
        }
    }

    private enum SimpleComponentList {
        OUTPUTS,
        SELL_INPUTS,
        BARTER_COSTS
    }

    private enum PreviewMode {
        BROWSE_CARD("browse"),
        DETAIL("detail"),
        OPTION_CHOOSER("chooser");

        private final String key;

        PreviewMode(String key) {
            this.key = key;
        }

        private String key() {
            return key;
        }
    }
}
