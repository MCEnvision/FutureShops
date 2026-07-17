package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.ClientConfig;
import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.market.MarketCapabilitiesSnapshot;
import com.enviouse.futureshops.client.market.MarketCardLayout;
import com.enviouse.futureshops.client.market.MarketCapabilityClientState;
import com.enviouse.futureshops.client.market.MarketClientNavigationCoordinator;
import com.enviouse.futureshops.client.market.MarketCompactPager;
import com.enviouse.futureshops.client.market.MarketDetailSelection;
import com.enviouse.futureshops.client.market.MarketHeaderControls;
import com.enviouse.futureshops.client.market.MarketLayout;
import com.enviouse.futureshops.client.market.MarketLayoutEngine;
import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.client.market.MarketModuleAvailability;
import com.enviouse.futureshops.client.market.MarketModuleCapability;
import com.enviouse.futureshops.client.market.MarketRectangle;
import com.enviouse.futureshops.client.market.MarketRequestGate;
import com.enviouse.futureshops.client.market.MarketResponseFamily;
import com.enviouse.futureshops.client.market.MarketRoute;
import com.enviouse.futureshops.client.market.MarketTheme;
import com.enviouse.futureshops.client.market.MarketThemeResolver;
import com.enviouse.futureshops.client.market.MarketViewport;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SOpenMarketModulePacket;
import com.enviouse.futureshops.network.packets.C2SCloseMarketSessionPacket;
import com.enviouse.futureshops.network.packets.C2SMarketPageQueryPacket;
import com.enviouse.futureshops.network.packets.C2SOpenShopPacket;
import com.enviouse.futureshops.network.packets.S2COpenMarketModulePacket;
import com.enviouse.futureshops.network.packets.S2CMarketPagePacket;
import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.server.market.query.MarketPageCard;
import com.enviouse.futureshops.server.market.query.MarketPageSnapshot;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Optional;
import java.util.UUID;

public final class MarketModuleScreen extends Screen
        implements ShopScreenMarker {
    private static final int SURFACE = 0xFF161826;
    private static final int SURFACE_RAISED = 0xFF232532;
    private static final int SURFACE_HOVER = 0xFF2D3040;
    private static final int BORDER = 0xFF3B3E50;
    private static final int BACKDROP = 0xC0080910;
    private static final int MAXIMUM_HIT_TARGETS = 128;

    private final S2COpenMarketModulePacket packet;
    private final MarketModule module;
    private final MarketClientNavigationCoordinator navigation;
    private final List<Hit> hits = new ArrayList<>();
    private MarketLayout layout;
    private MarketTheme theme;
    private MarketHeaderControls headerControls;
    private EditBox search;
    private MarketPageSnapshot page;
    private String pageResult = "LOADING";
    private String observedSearch = "";
    private String sentSearch = "";
    private String selectedCategory = "";
    private String selectedSort;
    private long searchChangedAtMillis;
    private UUID pendingOpenRequest;
    private int requestedPage;
    private int scrollOffset;
    private String selectionId = "";
    private int focusedCardIndex = -1;
    private MarketDetailSelection detailSelection;
    private boolean categoryDrawerOpen;
    private boolean narrowViewInitialized;
    private int narrowViewOffset;
    private int narrowCategoryOffset;
    private int focusedCategoryIndex = -1;
    private int unreadNotifications;
    private MarketCapabilitiesSnapshot capabilities;
    private java.util.Map<MarketModule, MarketModuleCapability>
            capabilitiesByModule = java.util.Map.of();
    private boolean explicitClose;
    private boolean closeSent;
    private boolean navigationHandoff;

    public MarketModuleScreen(
            S2COpenMarketModulePacket packet,
            MarketClientNavigationCoordinator navigation
    ) {
        super(Component.literal(packet.displayName()));
        this.packet = packet;
        this.module = MarketModule.fromId(packet.moduleId());
        this.navigation = java.util.Objects.requireNonNull(
                navigation, "navigation");
        MarketRoute route = navigation.current();
        if (route.module() != module
                || !route.viewId().equals(packet.view())
                || !route.routeNonce().equals(packet.routeNonce())) {
            throw new IllegalArgumentException(
                    "Market screen route does not match navigation state");
        }
        observedSearch = route.query();
        selectedCategory = route.categoryId();
        selectedSort = route.sortId().isEmpty()
                ? defaultSort(module) : route.sortId();
        requestedPage = route.page();
        scrollOffset = route.scrollOffset();
        selectionId = route.selectionId();
        if (route.isDetail()) {
            detailSelection = navigation.detailSelection(module,
                    selectionId).orElse(null);
        }
        capabilities = MarketCapabilityClientState.latest().orElse(null);
        if (capabilities != null) {
            capabilitiesByModule = capabilities.byModule();
        }
    }

    @Override
    protected void init() {
        String previousSearch = search == null ? observedSearch
                : search.getValue();
        ClientConfig.Settings settings = ClientConfig.settings();
        navigation.preferences().applySettings(settings);
        layout = MarketLayoutEngine.compute(
                new MarketViewport(width, height, 1, width, height),
                settings.presentation().density(),
                settings.presentation().cardSize());
        theme = MarketThemeResolver.resolve(module, packet.accentColor(),
                settings);
        headerControls = MarketHeaderControls.compute(layout.header(),
                layout.mode(), showBackButton());
        MarketRectangle searchBounds = headerControls.search();
        search = new EditBox(font, searchBounds.x(), searchBounds.y(),
                Math.max(1, searchBounds.width()),
                Math.max(1, searchBounds.height()),
                Component.translatable("gui.futureshops.market.search"));
        search.setMaxLength(128);
        search.setHint(Component.translatable(
                "gui.futureshops.market.search"));
        search.setValue(previousSearch);
        search.setEditable(!isDetailView());
        observedSearch = previousSearch;
        search.setResponder(value -> {
            observedSearch = value;
            requestedPage = 0;
            scrollOffset = 0;
            selectionId = "";
            searchChangedAtMillis = Util.getMillis();
        });
        addRenderableWidget(search);
        initializeNarrowViewWindow();
        requestCapabilities();
        if (isDetailView()) {
            pageResult = detailSelection == null
                    ? "DETAIL_UNAVAILABLE" : "OK";
        } else {
            sendPageQuery();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (search != null) {
            search.tick();
        }
        if (!observedSearch.equals(sentSearch)
                && Util.getMillis() - searchChangedAtMillis
                >= ClientConfig.settings().search().debounceMillis()) {
            sendPageQuery();
        }
    }

    public void applyPage(S2CMarketPagePacket response) {
        if (isDetailView()) {
            return;
        }
        MarketPageSnapshot incoming = response.page();
        if (incoming.module() != module
                || !incoming.routeNonce().equals(packet.routeNonce())) {
            return;
        }
        MarketRequestGate.Decision decision = navigation.acceptResponse(
                incoming.requestId(), incoming.module(),
                incoming.routeNonce(), MarketResponseFamily.CONTENT);
        if (decision != MarketRequestGate.Decision.ACCEPT) {
            return;
        }
        pageResult = response.resultCode();
        if ("OK".equals(pageResult)) {
            page = incoming;
            requestedPage = incoming.pageIndex();
            unreadNotifications = incoming.unreadNotifications();
            synchronizeFocusedCard();
            if (!selectedCategory.isEmpty()
                    && !incoming.categories().contains(selectedCategory)) {
                selectedCategory = "";
                requestedPage = 0;
                sendPageQuery();
            }
            synchronizeCategoryDrawer();
        }
    }

    public void applyCapabilities(
            MarketCapabilitiesSnapshot snapshot
    ) {
        boolean previouslyOpen = canOpenView(packet.view());
        capabilities = java.util.Objects.requireNonNull(
                snapshot, "snapshot");
        capabilitiesByModule = capabilities.byModule();
        if (layout != null) {
            theme = MarketThemeResolver.resolve(module,
                    currentAccent(), ClientConfig.settings());
        }
        boolean currentlyOpen = canOpenView(packet.view());
        if (!currentlyOpen) {
            page = null;
            pageResult = "CAPABILITY_BLOCKED";
        } else if (!previouslyOpen && layout != null
                && !isDetailView()) {
            sendPageQuery();
        }
    }

    public void refreshMarketState() {
        requestCapabilities();
        if (!isDetailView()) {
            sendPageQuery();
        }
    }

    public void prepareNavigationHandoff() {
        navigationHandoff = true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY,
                       float partialTick) {
        hits.clear();
        graphics.fill(0, 0, width, height, BACKDROP);
        MarketRectangle window = layout.window();
        graphics.fill(window.x(), window.y(), window.right(),
                window.bottom(), SURFACE);
        border(graphics, window, theme.accentDim());
        renderHeader(graphics, mouseX, mouseY);
        renderBreadcrumb(graphics);
        renderSecondaryTabs(graphics, mouseX, mouseY);
        renderRail(graphics, mouseX, mouseY);
        renderToolbar(graphics, mouseX, mouseY);
        renderContent(graphics, mouseX, mouseY);
        renderCategoryDrawer(graphics, mouseX, mouseY);
        renderFooter(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        MarketRectangle header = layout.header();
        graphics.fillGradient(header.x(), header.y(), header.right(),
                header.bottom(), theme.headerStart(), theme.headerEnd());
        graphics.fill(header.x(), header.bottom() - 1, header.right(),
                header.bottom(), theme.accentDim());
        int x = header.x() + layout.padding();
        int titleRight = layout.categoryDrawer()
                ? headerControls.balance().x() - 4
                : headerControls.search().x() - 4;
        String title = font.plainSubstrByWidth(currentDisplayName(),
                Math.max(8, titleRight - x));
        graphics.drawString(font, title, x,
                header.y() + 7, theme.textStrong(), false);
        if (layout.fullBrand()) {
            graphics.drawString(font,
                    font.plainSubstrByWidth(Component.translatable(
                            "gui.futureshops.market.brand").getString(),
                            Math.max(8, titleRight - x)),
                    x, header.y() + 20, theme.textMuted(), false);
        }
        if (showNavigation() && !layout.categoryDrawer()) {
            int tabsWidth = 164;
            int desiredX = x + Math.max(92,
                    Math.min(140, font.width(currentDisplayName()) + 18));
            int tabX = Math.min(desiredX,
                    headerControls.search().x() - tabsWidth - 4);
            if (tabX >= x + 56) {
                renderModuleTab(graphics, mouseX, mouseY, tabX,
                        MarketModule.SHOP, true);
                tabX += 52;
                renderModuleTab(graphics, mouseX, mouseY, tabX,
                        MarketModule.BAZAAR,
                        packet.bazaarEnabled());
                tabX += 62;
                renderModuleTab(graphics, mouseX, mouseY, tabX,
                        MarketModule.AUCTION_HOUSE,
                        packet.auctionHouseEnabled());
            }
        }
        renderAccountPills(graphics, mouseX, mouseY);
        button(graphics, mouseX, mouseY, headerControls.close(), "X", true,
                this::onClose);
        if (showBackButton()) {
            button(graphics, mouseX, mouseY, headerControls.back(),
                    "←", true,
                    this::navigateBack);
        }
    }

    private void renderAccountPills(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        boolean compact = layout.mode()
                != com.enviouse.futureshops.client.market.MarketLayoutMode.WIDE;
        String balance = ShopClientState.isCurrentBalanceKnown()
                ? ShopUiUtil.formatMinorUnits(
                ShopClientState.getCurrentBalanceMinorUnits())
                : Component.translatable(
                "gui.futureshops.market.balance_unknown").getString();
        if (!compact && ShopClientState.isCurrentBalanceKnown()) {
            balance = balance + " " + ShopClientState.getCurrencyName();
        }
        String playerName = minecraft != null && minecraft.player != null
                ? minecraft.player.getGameProfile().getName() : "";
        String profile = compact
                ? playerName.isEmpty() ? Component.translatable(
                "gui.futureshops.market.profile_compact").getString()
                : playerName.substring(0, 1).toUpperCase(
                        java.util.Locale.ROOT)
                : playerName;
        renderPill(graphics, mouseX, mouseY, headerControls.balance(),
                balance, false, null);
        renderPill(graphics, mouseX, mouseY, headerControls.profile(),
                profile, false, null);
        String notifications = compact ? Component.translatable(
                "gui.futureshops.market.notifications_compact",
                unreadNotifications).getString()
                : Component.translatable(
                "gui.futureshops.market.notifications",
                unreadNotifications).getString();
        renderPill(graphics, mouseX, mouseY,
                headerControls.notifications(), notifications,
                false, null);
        long claims = openClaimCount(module);
        String claimText = compact ? Component.translatable(
                "gui.futureshops.market.claim_count_compact",
                compactCount(claims)).getString()
                : Component.translatable(
                "gui.futureshops.market.claim_count", claims).getString();
        boolean claimsAllowed = moduleCapability(module)
                .map(capability -> capability.canOpenView("claims"))
                .orElse(true);
        boolean claimsRoute = claimsAllowed
                && !"claims".equals(packet.view());
        renderPill(graphics, mouseX, mouseY, headerControls.claims(),
                claimText, claimsRoute,
                claimsRoute ? () -> openView("claims") : null);
    }

    private void renderPill(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            MarketRectangle rectangle,
            String label,
            boolean interactive,
            Runnable action
    ) {
        if (rectangle.width() == 0 || rectangle.height() == 0) {
            return;
        }
        boolean hover = interactive && rectangle.contains(mouseX, mouseY);
        graphics.fill(rectangle.x(), rectangle.y(), rectangle.right(),
                rectangle.bottom(), hover ? theme.selectedSurface()
                        : SURFACE_RAISED);
        border(graphics, rectangle, hover ? theme.accent() : BORDER);
        graphics.drawCenteredString(font,
                font.plainSubstrByWidth(label,
                        Math.max(1, rectangle.width() - 6)),
                rectangle.x() + rectangle.width() / 2,
                rectangle.y() + Math.max(3,
                        (rectangle.height() - 8) / 2),
                interactive ? theme.textStrong() : theme.textMuted());
        if (interactive && action != null) {
            registerHit(rectangle, action);
        }
    }

    private void renderModuleTab(GuiGraphics graphics, int mouseX,
                                 int mouseY, int x, MarketModule target,
                                 boolean enabled) {
        String label = moduleLabel(target, true);
        label = label + claimBadge(target);
        boolean visible = moduleVisible(target);
        enabled = moduleOpenable(target, enabled);
        if (!visible) {
            return;
        }
        int tabWidth = target == MarketModule.BAZAAR ? 56 : 46;
        MarketRectangle rectangle = new MarketRectangle(x,
                layout.header().y() + 5, tabWidth, 20);
        boolean selected = target == module;
        int fill = selected ? theme.selectedSurface() : SURFACE_RAISED;
        graphics.fill(rectangle.x(), rectangle.y(), rectangle.right(),
                rectangle.bottom(), enabled ? fill : SURFACE);
        border(graphics, rectangle, selected ? theme.activeBorder() : BORDER);
        int color = enabled ? theme.textStrong() : theme.textMuted();
        graphics.drawCenteredString(font,
                font.plainSubstrByWidth(label,
                        Math.max(1, rectangle.width() - 4)),
                rectangle.x() + rectangle.width() / 2,
                rectangle.y() + 6, color);
        if (enabled && !selected) {
            registerHit(rectangle, () -> switchModule(target));
        }
    }

    private void renderBreadcrumb(GuiGraphics graphics) {
        MarketRectangle breadcrumb = layout.breadcrumb();
        if (breadcrumb.height() == 0) {
            return;
        }
        String viewName = viewLabel(packet.view());
        graphics.fill(breadcrumb.x(), breadcrumb.y(), breadcrumb.right(),
                breadcrumb.bottom(), SURFACE_RAISED);
        graphics.drawString(font, font.plainSubstrByWidth(
                currentDisplayName() + "  >  " + viewName,
                Math.max(1, breadcrumb.width()
                        - layout.padding() * 2)),
                breadcrumb.x() + layout.padding(), breadcrumb.y() + 4,
                theme.textMuted(), false);
    }

    private void renderSecondaryTabs(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        MarketRectangle row = layout.secondaryTabs();
        if (row.height() == 0) {
            return;
        }
        graphics.fill(row.x(), row.y(), row.right(), row.bottom(),
                SURFACE_RAISED);
        graphics.fill(row.x(), row.bottom() - 1, row.right(),
                row.bottom(), BORDER);
        List<String> views = localViews();
        int capacity = Math.min(3, Math.max(1, views.size()));
        MarketCompactPager.Window window = MarketCompactPager.window(
                views.size(), narrowViewOffset, capacity);
        int arrowWidth = Math.min(18, row.width() / 6);
        MarketRectangle previous = new MarketRectangle(row.x(), row.y(),
                arrowWidth, row.height());
        MarketRectangle next = new MarketRectangle(
                row.right() - arrowWidth, row.y(), arrowWidth,
                row.height());
        button(graphics, mouseX, mouseY, previous, "<",
                window.hasPrevious(), () -> narrowViewOffset =
                MarketCompactPager.previousOffset(views.size(),
                        narrowViewOffset, capacity));
        button(graphics, mouseX, mouseY, next, ">",
                window.hasNext(), () -> narrowViewOffset =
                MarketCompactPager.nextOffset(views.size(),
                        narrowViewOffset, capacity));
        int available = Math.max(1, row.width() - arrowWidth * 2);
        int tabWidth = Math.max(1, available / capacity);
        String activeView = activeRailView();
        for (int slot = 0; slot < window.size(); slot++) {
            int index = window.offset() + slot;
            String view = views.get(index);
            int x = row.x() + arrowWidth + slot * tabWidth;
            int width = slot == capacity - 1
                    ? row.right() - arrowWidth - x : tabWidth;
            MarketRectangle tab = new MarketRectangle(x, row.y(),
                    Math.max(0, width), row.height());
            boolean selected = activeView.equals(view);
            boolean allowed = moduleCapability(module)
                    .map(capability -> capability.canOpenView(view))
                    .orElse(true);
            boolean hover = allowed && !selected
                    && tab.contains(mouseX, mouseY);
            graphics.fill(tab.x(), tab.y(), tab.right(), tab.bottom(),
                    selected || hover ? theme.selectedSurface()
                            : SURFACE_RAISED);
            if (selected) {
                graphics.fill(tab.x(), tab.bottom() - 2, tab.right(),
                        tab.bottom(), theme.accent());
            }
            graphics.drawCenteredString(font,
                    font.plainSubstrByWidth(viewLabel(view),
                            Math.max(1, tab.width() - 8)),
                    tab.x() + tab.width() / 2,
                    tab.y() + Math.max(3, (tab.height() - 8) / 2),
                    allowed ? theme.textStrong() : theme.textMuted());
            if (allowed && !selected) {
                registerHit(tab, () -> openView(view));
            }
        }
    }

    private void initializeNarrowViewWindow() {
        if (!layout.secondaryTabRow() || narrowViewInitialized) {
            return;
        }
        List<String> views = localViews();
        int active = views.indexOf(activeRailView());
        if (active >= 0) {
            narrowViewOffset = MarketCompactPager.ensureVisible(
                    views.size(), narrowViewOffset,
                    Math.min(3, Math.max(1, views.size())), active);
        }
        narrowViewInitialized = true;
    }

    private void cycleLocalView(boolean reverse) {
        if (!navigation.isOpen()) {
            return;
        }
        List<String> views = localViews();
        int current = views.indexOf(activeRailView());
        if (current < 0) {
            current = 0;
        }
        for (int step = 1; step <= views.size(); step++) {
            int index = Math.floorMod(current
                    + (reverse ? -step : step), views.size());
            String view = views.get(index);
            boolean allowed = moduleCapability(module)
                    .map(capability -> capability.canOpenView(view))
                    .orElse(true);
            if (allowed) {
                openView(view);
                return;
            }
        }
    }

    private void renderRail(GuiGraphics graphics, int mouseX, int mouseY) {
        MarketRectangle rail = layout.categoryRail();
        if (rail.width() == 0) {
            return;
        }
        graphics.fill(rail.x(), rail.y(), rail.right(), rail.bottom(),
                SURFACE_RAISED);
        border(graphics, rail, BORDER);
        List<String> views = localViews();
        int y = rail.y() + 8;
        for (String view : views) {
            MarketRectangle row = new MarketRectangle(rail.x() + 6, y,
                    Math.max(1, rail.width() - 12), 20);
            boolean selected = activeRailView().equals(view);
            int fill = selected ? theme.selectedSurface() : SURFACE_RAISED;
            graphics.fill(row.x(), row.y(), row.right(), row.bottom(), fill);
            if (selected) {
                graphics.fill(row.x(), row.y(), row.x() + 2,
                        row.bottom(), theme.accent());
            }
            String label = viewLabel(view);
            if ("claims".equals(view)) {
                label = label + claimBadge(module);
            }
            boolean allowed = moduleCapability(module)
                    .map(capability -> capability.canOpenView(view))
                    .orElse(true);
            graphics.drawString(font, label, row.x() + 8,
                    row.y() + 6,
                    selected ? theme.textStrong()
                            : allowed ? theme.textMuted() : BORDER,
                    false);
            if (!selected && allowed) {
                registerHit(row, () -> openView(view));
            }
            y += 24;
        }
        if (page != null && supportsFilters()
                && y + 34 < rail.bottom()) {
            graphics.drawString(font, Component.translatable(
                            "gui.futureshops.market.categories"),
                    rail.x() + 8,
                    y + 3, theme.textMuted(), false);
            y += 16;
            List<String> categories = new ArrayList<>();
            categories.add("");
            categories.addAll(page.categories());
            for (String category : categories) {
                if (y + 18 > rail.bottom() - 4) {
                    break;
                }
                MarketRectangle row = new MarketRectangle(rail.x() + 6,
                        y, Math.max(1, rail.width() - 12), 18);
                boolean selected = selectedCategory.equals(category);
                graphics.fill(row.x(), row.y(), row.right(), row.bottom(),
                        selected ? theme.selectedSurface()
                                : SURFACE_RAISED);
                String label = category.isEmpty()
                        ? Component.translatable(
                        "gui.futureshops.market.all").getString()
                        : category;
                graphics.drawString(font,
                        font.plainSubstrByWidth(label,
                                Math.max(8, row.width() - 12)),
                        row.x() + 6, row.y() + 5,
                        selected ? theme.textStrong()
                                : theme.textMuted(), false);
                if (!selected) {
                    registerHit(row, () -> selectCategory(category));
                }
                y += 20;
            }
        }
    }

    private void renderToolbar(GuiGraphics graphics, int mouseX,
                               int mouseY) {
        MarketRectangle toolbar = layout.toolbar();
        graphics.fill(toolbar.x(), toolbar.y(), toolbar.right(),
                toolbar.bottom(), SURFACE_RAISED);
        border(graphics, toolbar, BORDER);
        MarketModuleAvailability availability = currentAvailability();
        boolean recovering = (availability
                == MarketModuleAvailability.CLAIMS_ONLY
                || availability == MarketModuleAvailability.DISABLED)
                && packet.enabled() && !packet.escrowReady();
        String statusKey = recovering
                ? "gui.futureshops.market.status.recovering"
                : switch (availability) {
                    case ENABLED ->
                            "gui.futureshops.market.status.ready";
                    case FROZEN ->
                            "gui.futureshops.market.status.frozen";
                    case DRAINING ->
                            "gui.futureshops.market.status.draining";
                    case CANCEL_AND_REFUND ->
                            "gui.futureshops.market.status.cancelling";
                    case CLAIMS_ONLY ->
                            "gui.futureshops.market.status.claims_only";
                    case DISABLED, HIDDEN ->
                            "gui.futureshops.market.status.disabled";
                };
        String status = Component.translatable(statusKey).getString();
        int color = recovering
                || availability
                == MarketModuleAvailability.CANCEL_AND_REFUND
                ? theme.semanticDanger()
                : availability == MarketModuleAvailability.ENABLED
                ? theme.semanticSuccess()
                : theme.semanticWarning();
        if (layout.categoryDrawer()) {
            renderNarrowToolbar(graphics, toolbar, mouseX, mouseY,
                    status, color);
            return;
        }
        graphics.drawString(font, status, toolbar.x() + 8,
                toolbar.y() + Math.max(5, (toolbar.height() - 8) / 2),
                color, false);
        long claims = openClaimCount(module);
        int contentStart = toolbar.x()
                + Math.max(72, font.width(status) + 18);
        if (claims > 0L) {
            String claimText = Component.translatable(
                    "gui.futureshops.market.claim_count", claims)
                    .getString();
            graphics.drawString(font, claimText, contentStart,
                    toolbar.y() + Math.max(5,
                            (toolbar.height() - 8) / 2),
                    theme.textMuted(), false);
            contentStart += font.width(claimText) + 10;
        }
        if (isDetailView()) {
            graphics.drawString(font,
                    Component.translatable(
                            "gui.futureshops.market.detail.toolbar"),
                    contentStart,
                    toolbar.y() + Math.max(5,
                            (toolbar.height() - 8) / 2),
                    theme.textMuted(), false);
            return;
        }
        if (page != null) {
            String count = Component.translatable(
                    "gui.futureshops.market.results",
                    page.totalResults()).getString();
            graphics.drawString(font, count,
                    Math.max(contentStart,
                            toolbar.x() + toolbar.width() / 3),
                    toolbar.y() + Math.max(5,
                            (toolbar.height() - 8) / 2),
                    theme.textMuted(), false);
            renderFilterControls(graphics, toolbar, mouseX, mouseY);
            renderPageControls(graphics, toolbar, mouseX, mouseY);
        } else if (!"LOADING".equals(pageResult)) {
            graphics.drawString(font, pageResultLabel(pageResult),
                    contentStart,
                    toolbar.y() + Math.max(5,
                            (toolbar.height() - 8) / 2),
                    theme.semanticDanger(), false);
        }
    }

    private void renderNarrowToolbar(
            GuiGraphics graphics,
            MarketRectangle toolbar,
            int mouseX,
            int mouseY,
            String status,
            int statusColor
    ) {
        String right = isDetailView()
                ? Component.translatable(
                "gui.futureshops.market.detail.toolbar").getString()
                : page == null ? pageResultLabel(pageResult)
                : Component.translatable(
                "gui.futureshops.market.results",
                page.totalResults()).getString();
        int rightWidth = Math.min(toolbar.width() / 3,
                font.width(right));
        graphics.drawString(font,
                font.plainSubstrByWidth(status,
                        Math.max(8, toolbar.width() - rightWidth - 24)),
                toolbar.x() + 6, toolbar.y() + 5,
                statusColor, false);
        if (!right.isEmpty()) {
            String clipped = font.plainSubstrByWidth(right,
                    Math.max(8, toolbar.width() / 3));
            graphics.drawString(font, clipped,
                    toolbar.right() - font.width(clipped) - 6,
                    toolbar.y() + 5, theme.textMuted(), false);
        }
        if (toolbar.height() < 28 || isDetailView()) {
            return;
        }
        int y = toolbar.bottom() - 17;
        int height = 14;
        int x = toolbar.x() + 5;
        boolean filterable = page != null && supportsFilters();
        int pageControlsWidth = 42;
        int available = Math.max(2,
                toolbar.width() - 14 - pageControlsWidth);
        int categoryWidth = Math.max(44, available * 3 / 5);
        int sortWidth = Math.max(38, available - categoryWidth - 4);
        MarketRectangle category = new MarketRectangle(x, y,
                categoryWidth, height);
        String categoryLabel = selectedCategory.isEmpty()
                ? Component.translatable(
                "gui.futureshops.market.categories").getString()
                : selectedCategory;
        button(graphics, mouseX, mouseY, category, categoryLabel,
                filterable, this::toggleCategoryDrawer);
        MarketRectangle sort = new MarketRectangle(
                category.right() + 4, y, sortWidth, height);
        button(graphics, mouseX, mouseY, sort,
                sortLabel(selectedSort), filterable, this::cycleSort);
        if (page != null) {
            MarketRectangle previous = new MarketRectangle(
                    toolbar.right() - 41, y, 18, height);
            MarketRectangle next = new MarketRectangle(
                    toolbar.right() - 20, y, 18, height);
            button(graphics, mouseX, mouseY, previous, "<",
                    page.pageIndex() > 0,
                    () -> requestPage(page.pageIndex() - 1));
            button(graphics, mouseX, mouseY, next, ">",
                    page.pageIndex() + 1 < page.pageCount(),
                    () -> requestPage(page.pageIndex() + 1));
        }
    }

    private void renderContent(GuiGraphics graphics, int mouseX,
                               int mouseY) {
        MarketRectangle content = layout.content();
        graphics.fill(content.x(), content.y(), content.right(),
                content.bottom(), SURFACE);
        if (!canOpenView(packet.view())) {
            boolean claimsAvailable = currentAllowsClaims();
            boolean recovering = packet.enabled()
                    && !packet.escrowReady();
            renderEmptyState(graphics, content,
                    Component.translatable(
                            recovering
                                    ? "gui.futureshops.market.recovering_title"
                                    : "gui.futureshops.market.disabled_title"),
                    Component.translatable(
                            recovering
                                    ? "gui.futureshops.market.recovering_detail"
                                    : "gui.futureshops.market.disabled_detail"),
                    claimsAvailable ? "claims" : "",
                    mouseX, mouseY);
            return;
        }
        if (isDetailView()) {
            renderDetail(graphics, content);
            return;
        }
        if (page == null) {
            renderLoading(graphics, content);
            return;
        }
        if (page.cards().isEmpty()) {
            renderNoResults(graphics, content, mouseX, mouseY);
            return;
        }
        renderPageCards(graphics, content, mouseX, mouseY);
    }

    private void renderCategoryDrawer(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        if (!layout.categoryDrawer() || !categoryDrawerOpen
                || page == null || !supportsFilters()) {
            return;
        }
        MarketRectangle content = layout.content();
        if (content.width() == 0 || content.height() == 0) {
            return;
        }
        registerHit(content, () -> categoryDrawerOpen = false);
        int width = Math.min(220, content.width());
        MarketRectangle drawer = new MarketRectangle(content.x(),
                content.y(), width, content.height());
        graphics.fill(drawer.x(), drawer.y(), drawer.right(),
                drawer.bottom(), SURFACE_RAISED);
        border(graphics, drawer, theme.activeBorder());
        registerHit(drawer, () -> {
        });
        int titleHeight = drawer.height() >= 80 ? 18 : 0;
        if (titleHeight > 0) {
            graphics.drawString(font,
                    Component.translatable(
                            "gui.futureshops.market.categories"),
                    drawer.x() + 7, drawer.y() + 6,
                    theme.textStrong(), false);
        }
        List<String> categories = categoryValues();
        int capacity = categoryDrawerCapacity();
        MarketCompactPager.Window window = MarketCompactPager.window(
                categories.size(), narrowCategoryOffset, capacity);
        narrowCategoryOffset = window.offset();
        int y = drawer.y() + titleHeight + 3;
        int navHeight = drawer.height() >= 28 ? 18 : 0;
        int rowsBottom = drawer.bottom() - navHeight - 3;
        for (int index = window.offset(); index < window.end(); index++) {
            int height = Math.min(18, rowsBottom - y);
            if (height <= 0) {
                break;
            }
            String category = categories.get(index);
            MarketRectangle row = new MarketRectangle(drawer.x() + 5, y,
                    Math.max(1, drawer.width() - 10), height);
            boolean selected = selectedCategory.equals(category);
            boolean focused = focusedCategoryIndex == index;
            boolean hover = row.contains(mouseX, mouseY);
            graphics.fill(row.x(), row.y(), row.right(), row.bottom(),
                    selected || focused || hover
                            ? theme.selectedSurface() : SURFACE_RAISED);
            if (selected) {
                graphics.fill(row.x(), row.y(), row.x() + 2,
                        row.bottom(), theme.accent());
            }
            String label = category.isEmpty()
                    ? Component.translatable(
                    "gui.futureshops.market.all_categories").getString()
                    : category;
            graphics.drawString(font,
                    font.plainSubstrByWidth(label,
                            Math.max(1, row.width() - 12)),
                    row.x() + 6,
                    row.y() + Math.max(3, (row.height() - 8) / 2),
                    selected || focused ? theme.textStrong()
                            : theme.textMuted(), false);
            int categoryIndex = index;
            registerHit(row, () -> {
                focusedCategoryIndex = categoryIndex;
                selectCategory(category);
                categoryDrawerOpen = false;
            });
            y += 20;
        }
        if (navHeight > 0) {
            int navY = drawer.bottom() - navHeight - 1;
            MarketRectangle previous = new MarketRectangle(
                    drawer.x() + 5, navY,
                    Math.max(1, (drawer.width() - 14) / 2),
                    navHeight);
            MarketRectangle next = new MarketRectangle(previous.right() + 4,
                    navY, Math.max(1, drawer.right() - 5
                    - previous.right() - 4), navHeight);
            button(graphics, mouseX, mouseY, previous, "<",
                    window.hasPrevious(), () -> narrowCategoryOffset =
                    MarketCompactPager.previousOffset(categories.size(),
                            narrowCategoryOffset, capacity));
            button(graphics, mouseX, mouseY, next, ">",
                    window.hasNext(), () -> narrowCategoryOffset =
                    MarketCompactPager.nextOffset(categories.size(),
                            narrowCategoryOffset, capacity));
        }
    }

    private void toggleCategoryDrawer() {
        if (page == null || !supportsFilters()) {
            return;
        }
        categoryDrawerOpen = !categoryDrawerOpen;
        if (categoryDrawerOpen) {
            List<String> categories = categoryValues();
            focusedCategoryIndex = Math.max(0,
                    categories.indexOf(selectedCategory));
            narrowCategoryOffset = MarketCompactPager.ensureVisible(
                    categories.size(), narrowCategoryOffset,
                    categoryDrawerCapacity(), focusedCategoryIndex);
        }
    }

    private void synchronizeCategoryDrawer() {
        if (page == null || !supportsFilters()) {
            categoryDrawerOpen = false;
            focusedCategoryIndex = -1;
            narrowCategoryOffset = 0;
            return;
        }
        List<String> categories = categoryValues();
        if (focusedCategoryIndex < 0
                || focusedCategoryIndex >= categories.size()) {
            focusedCategoryIndex = Math.max(0,
                    categories.indexOf(selectedCategory));
        }
        narrowCategoryOffset = MarketCompactPager.window(
                categories.size(), narrowCategoryOffset,
                categoryDrawerCapacity()).offset();
    }

    private boolean handleCategoryDrawerKey(int keyCode) {
        if (!categoryDrawerOpen || page == null || !supportsFilters()) {
            return false;
        }
        List<String> categories = categoryValues();
        int capacity = categoryDrawerCapacity();
        int next = focusedCategoryIndex < 0 ? 0
                : focusedCategoryIndex;
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> next--;
            case GLFW.GLFW_KEY_DOWN -> next++;
            case GLFW.GLFW_KEY_PAGE_UP -> next -= capacity;
            case GLFW.GLFW_KEY_PAGE_DOWN -> next += capacity;
            case GLFW.GLFW_KEY_HOME -> next = 0;
            case GLFW.GLFW_KEY_END -> next = categories.size() - 1;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER,
                    GLFW.GLFW_KEY_SPACE -> {
                int selected = Math.max(0, Math.min(
                        categories.size() - 1, next));
                selectCategory(categories.get(selected));
                categoryDrawerOpen = false;
                return true;
            }
            default -> {
                return false;
            }
        }
        focusedCategoryIndex = Math.max(0,
                Math.min(categories.size() - 1, next));
        narrowCategoryOffset = MarketCompactPager.ensureVisible(
                categories.size(), narrowCategoryOffset, capacity,
                focusedCategoryIndex);
        return true;
    }

    private int categoryDrawerCapacity() {
        int height = layout.content().height();
        int title = height >= 80 ? 18 : 0;
        int navigation = height >= 28 ? 18 : 0;
        int available = Math.max(1, height - title - navigation - 6);
        return Math.max(1, Math.min(256, available / 20));
    }

    private List<String> categoryValues() {
        List<String> categories = new ArrayList<>();
        categories.add("");
        if (page != null) {
            categories.addAll(page.categories());
        }
        return List.copyOf(categories);
    }

    private void renderPageCards(GuiGraphics graphics,
                                 MarketRectangle content,
                                 int mouseX, int mouseY) {
        List<MarketPageCard> cards = page.cards();
        MarketCardLayout.Placement placement = cardPlacement();
        for (int index = 0; index < placement.cards().size(); index++) {
            MarketPageCard data = cards.get(index);
            MarketRectangle card = placement.cards().get(index);
            boolean hover = card.contains(mouseX, mouseY);
            boolean focused = focusedCardIndex == index;
            graphics.fill(card.x(), card.y(), card.right(), card.bottom(),
                    hover || focused ? SURFACE_HOVER : SURFACE_RAISED);
            border(graphics, card, hover || focused
                    ? theme.accent() : BORDER);
            graphics.fill(card.x(), card.y(), card.right(), card.y() + 2,
                    theme.accent());
            int textX = card.x() + 8;
            if (!data.registryId().isEmpty()) {
                ItemStack stack = displayStack(data);
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, card.x() + 7,
                            card.y() + 8);
                    textX += 20;
                }
            }
            String title = font.plainSubstrByWidth(data.title(),
                    Math.max(20, card.right() - textX - 6));
            graphics.drawString(font, title, textX,
                    card.y() + 10, theme.textStrong(), false);
            String primary = data.primaryMinor() > 0L
                    ? ShopUiUtil.formatMinorUnits(data.primaryMinor())
                    : data.quantity() > 0L
                    ? Long.toString(data.quantity()) : data.state();
            graphics.drawString(font, primary, textX,
                    card.y() + 24, theme.callToAction(), false);
            String detail = data.secondaryMinor() > 0L
                    ? ShopUiUtil.formatMinorUnits(data.secondaryMinor())
                    : data.state();
            graphics.drawString(font,
                    font.plainSubstrByWidth(detail,
                            Math.max(20, card.right() - textX - 6)),
                    textX, card.y() + 36, theme.textMuted(), false);
            if (data.watched()) {
                graphics.drawString(font, "*", card.right() - 10,
                        card.y() + 8, theme.semanticWarning(), false);
            }
            int cardIndex = index;
            registerHit(card, () -> openDetail(cardIndex, data));
        }
    }

    private void renderDetail(
            GuiGraphics graphics,
            MarketRectangle content
    ) {
        if (content.width() < 4 || content.height() < 4) {
            return;
        }
        if (detailSelection == null) {
            renderEmptyState(graphics, content,
                    Component.translatable(
                            "gui.futureshops.market.detail.unavailable"),
                    Component.translatable(
                            "gui.futureshops.market.detail.unavailable_detail"),
                    "", 0, 0);
            return;
        }
        MarketPageCard data = detailSelection.card();
        int padding = Math.max(8, layout.padding());
        MarketRectangle panel = new MarketRectangle(content.x(),
                content.y(), content.width(), content.height());
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(),
                SURFACE_RAISED);
        border(graphics, panel, theme.activeBorder());
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.y() + 3,
                theme.accent());
        int x = panel.x() + padding;
        int y = panel.y() + padding + 3;
        ItemStack stack = displayStack(data);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x, y);
            x += 24;
        }
        graphics.drawString(font,
                font.plainSubstrByWidth(data.title(),
                        Math.max(20, panel.right() - x - padding)),
                x, y + 4, theme.textStrong(), false);
        x = panel.x() + padding;
        y += 28;
        y = detailRow(graphics, panel, x, y,
                "gui.futureshops.market.detail.state", data.state());
        if (!data.category().isEmpty()) {
            y = detailRow(graphics, panel, x, y,
                    "gui.futureshops.market.detail.category",
                    data.category());
        }
        if (!data.registryId().isEmpty()) {
            y = detailRow(graphics, panel, x, y,
                    "gui.futureshops.market.detail.item",
                    data.registryId());
        }
        if (data.quantity() > 0L) {
            y = detailRow(graphics, panel, x, y,
                    "gui.futureshops.market.detail.quantity",
                    Long.toString(data.quantity()));
        }
        if (data.primaryMinor() > 0L) {
            y = detailRow(graphics, panel, x, y,
                    "gui.futureshops.market.detail.primary",
                    ShopUiUtil.formatMinorUnits(data.primaryMinor()));
        }
        if (data.secondaryMinor() > 0L) {
            y = detailRow(graphics, panel, x, y,
                    "gui.futureshops.market.detail.secondary",
                    ShopUiUtil.formatMinorUnits(data.secondaryMinor()));
        }
        if (data.remainingMillis() > 0L) {
            y = detailRow(graphics, panel, x, y,
                    "gui.futureshops.market.detail.remaining",
                    durationLabel(data.remainingMillis()));
        }
        detailRow(graphics, panel, x, y,
                "gui.futureshops.market.detail.identity",
                data.identity());
        graphics.drawString(font,
                Component.translatable(
                        "gui.futureshops.market.detail.read_only"),
                panel.x() + padding, panel.bottom() - padding - 8,
                theme.textMuted(), false);
    }

    private int detailRow(
            GuiGraphics graphics,
            MarketRectangle panel,
            int x,
            int y,
            String labelKey,
            String value
    ) {
        if (y + 10 >= panel.bottom() - 24) {
            return y;
        }
        String label = Component.translatable(labelKey).getString();
        graphics.drawString(font, label, x, y,
                theme.textMuted(), false);
        int valueX = x + Math.min(96, font.width(label) + 8);
        graphics.drawString(font,
                font.plainSubstrByWidth(value,
                        Math.max(12, panel.right() - valueX
                                - layout.padding())),
                valueX, y, theme.textStrong(), false);
        return y + 13;
    }

    private void renderLoading(GuiGraphics graphics,
                               MarketRectangle content) {
        graphics.drawCenteredString(font,
                Component.translatable(
                        "gui.futureshops.market.loading"),
                content.x() + content.width() / 2,
                content.y() + content.height() / 2,
                theme.textMuted());
    }

    private void renderNoResults(GuiGraphics graphics,
                                 MarketRectangle content,
                                 int mouseX, int mouseY) {
        Component detail = Component.translatable(
                "create".equals(packet.view())
                        ? "gui.futureshops.market.empty.create"
                        : "gui.futureshops.market.empty.no_results");
        graphics.drawCenteredString(font, detail,
                content.x() + content.width() / 2,
                content.y() + content.height() / 2,
                theme.textMuted());
    }

    private void renderPageControls(GuiGraphics graphics,
                                    MarketRectangle toolbar,
                                    int mouseX, int mouseY) {
        int right = toolbar.right() - 6;
        MarketRectangle next = new MarketRectangle(right - 18,
                toolbar.y() + 4, 18,
                Math.max(14, toolbar.height() - 8));
        MarketRectangle previous = new MarketRectangle(right - 40,
                toolbar.y() + 4, 18,
                Math.max(14, toolbar.height() - 8));
        boolean hasPrevious = page.pageIndex() > 0;
        boolean hasNext = page.pageIndex() + 1 < page.pageCount();
        button(graphics, mouseX, mouseY, previous, "<", hasPrevious,
                () -> requestPage(page.pageIndex() - 1));
        button(graphics, mouseX, mouseY, next, ">", hasNext,
                () -> requestPage(page.pageIndex() + 1));
    }

    private void renderFilterControls(GuiGraphics graphics,
                                      MarketRectangle toolbar,
                                      int mouseX, int mouseY) {
        if (!supportsFilters() || toolbar.width() < 260) {
            return;
        }
        int right = toolbar.right() - 52;
        int height = Math.max(14, toolbar.height() - 8);
        int sortWidth = toolbar.width() >= 390 ? 104 : 82;
        MarketRectangle sort = new MarketRectangle(right - sortWidth,
                toolbar.y() + 4, sortWidth, height);
        button(graphics, mouseX, mouseY, sort,
                sortLabel(selectedSort), true, this::cycleSort);
        if (toolbar.width() >= 390) {
            int categoryWidth = 104;
            MarketRectangle category = new MarketRectangle(
                    sort.x() - categoryWidth - 4, toolbar.y() + 4,
                    categoryWidth, height);
            String label = selectedCategory.isEmpty()
                    ? Component.translatable(
                    "gui.futureshops.market.all_categories").getString()
                    : selectedCategory;
            button(graphics, mouseX, mouseY, category,
                    font.plainSubstrByWidth(label,
                            categoryWidth - 8), true,
                    this::cycleCategory);
        }
    }

    private boolean supportsFilters() {
        return module == MarketModule.BAZAAR
                && ("products".equals(packet.view())
                || "watched".equals(packet.view()))
                || module == MarketModule.AUCTION_HOUSE
                && ("browse".equals(packet.view())
                || "watched".equals(packet.view())
                || "history".equals(packet.view()));
    }

    private void cycleCategory() {
        if (page == null || page.categories().isEmpty()) {
            return;
        }
        int current = selectedCategory.isEmpty() ? -1
                : page.categories().indexOf(selectedCategory);
        int next = current + 1;
        String category = next >= page.categories().size()
                ? "" : page.categories().get(next);
        selectCategory(category);
    }

    private void selectCategory(String category) {
        selectedCategory = category;
        requestedPage = 0;
        scrollOffset = 0;
        selectionId = "";
        categoryDrawerOpen = false;
        sendPageQuery();
    }

    private void cycleSort() {
        List<String> sorts = module == MarketModule.BAZAAR
                ? List.of("name", "instant_buy_lowest",
                "instant_sell_highest", "spread_lowest",
                "volume_highest", "trend_highest")
                : List.of("ending_soon", "newest", "lowest_price",
                "highest_price", "most_bids", "seller");
        int current = sorts.indexOf(selectedSort);
        selectedSort = sorts.get((Math.max(0, current) + 1)
                % sorts.size());
        requestedPage = 0;
        scrollOffset = 0;
        selectionId = "";
        categoryDrawerOpen = false;
        sendPageQuery();
    }

    private void renderEmptyState(GuiGraphics graphics,
                                  MarketRectangle content,
                                  Component title, Component detail,
                                  String actionView, int mouseX,
                                  int mouseY) {
        int centerX = content.x() + content.width() / 2;
        int y = content.y() + Math.max(8, content.height() / 3);
        graphics.drawCenteredString(font, title, centerX, y,
                theme.textStrong());
        graphics.drawCenteredString(font, detail, centerX, y + 16,
                theme.textMuted());
        if (!actionView.isEmpty()) {
            MarketRectangle action = new MarketRectangle(
                    centerX - 48, y + 34, 96, 20);
            button(graphics, mouseX, mouseY, action,
                    viewLabel(actionView), true,
                    () -> openView(actionView));
        }
    }

    private void renderFooter(GuiGraphics graphics, int mouseX,
                              int mouseY) {
        MarketRectangle footer = layout.footer();
        graphics.fill(footer.x(), footer.y(), footer.right(),
                footer.bottom(), SURFACE_RAISED);
        graphics.fill(footer.x(), footer.y(), footer.right(),
                footer.y() + 1, BORDER);
        if (showNavigation() && layout.categoryDrawer()) {
            int gap = 4;
            int buttonWidth = Math.max(54,
                    (footer.width() - layout.padding() * 2 - gap * 2) / 3);
            int x = footer.x() + layout.padding();
            renderFooterModuleButton(graphics, mouseX, mouseY, x,
                    footer.y() + 4, buttonWidth, MarketModule.SHOP, true);
            x += buttonWidth + gap;
            renderFooterModuleButton(graphics, mouseX, mouseY, x,
                    footer.y() + 4, buttonWidth, MarketModule.BAZAAR,
                    packet.bazaarEnabled());
            x += buttonWidth + gap;
            renderFooterModuleButton(graphics, mouseX, mouseY, x,
                    footer.y() + 4, buttonWidth,
                    MarketModule.AUCTION_HOUSE,
                    packet.auctionHouseEnabled());
        } else {
            graphics.drawString(font,
                    Component.translatable("gui.futureshops.market.footer"),
                    footer.x() + layout.padding(), footer.y() + 8,
                    theme.textMuted(), false);
        }
    }

    private void renderFooterModuleButton(GuiGraphics graphics,
                                          int mouseX, int mouseY, int x,
                                          int y, int width,
                                          MarketModule target,
                                          boolean enabled) {
        if (!moduleVisible(target)) {
            return;
        }
        String label = moduleLabel(target, true);
        label = label + claimBadge(target);
        enabled = moduleOpenable(target, enabled);
        MarketRectangle rectangle = new MarketRectangle(x, y, width,
                Math.max(14, layout.footer().height() - 8));
        boolean selected = target == module;
        button(graphics, mouseX, mouseY, rectangle, label,
                enabled && !selected, () -> switchModule(target));
        if (selected) {
            graphics.fill(rectangle.x(), rectangle.bottom() - 2,
                    rectangle.right(), rectangle.bottom(), theme.accent());
        }
    }

    private void button(GuiGraphics graphics, int mouseX, int mouseY,
                        MarketRectangle rectangle, String label,
                        boolean enabled, Runnable action) {
        boolean hover = enabled && rectangle.contains(mouseX, mouseY);
        graphics.fill(rectangle.x(), rectangle.y(), rectangle.right(),
                rectangle.bottom(), hover ? theme.selectedSurface()
                        : SURFACE_RAISED);
        border(graphics, rectangle, enabled
                ? hover ? theme.accent() : BORDER : BORDER);
        graphics.drawCenteredString(font,
                font.plainSubstrByWidth(label,
                        Math.max(1, rectangle.width() - 4)),
                rectangle.x() + rectangle.width() / 2,
                rectangle.y() + Math.max(3,
                        (rectangle.height() - 8) / 2),
                enabled ? theme.textStrong() : theme.textMuted());
        if (enabled) {
            registerHit(rectangle, action);
        }
    }

    private void registerHit(
            MarketRectangle rectangle,
            Runnable action
    ) {
        if (hits.size() >= MAXIMUM_HIT_TARGETS || layout == null) {
            return;
        }
        MarketRectangle window = layout.window();
        if (rectangle.width() == 0 || rectangle.height() == 0
                || rectangle.x() < window.x()
                || rectangle.y() < window.y()
                || rectangle.right() > window.right()
                || rectangle.bottom() > window.bottom()) {
            return;
        }
        hits.add(new Hit(rectangle,
                java.util.Objects.requireNonNull(action, "action")));
    }

    private void switchModule(MarketModule target) {
        if (!navigation.isOpen() || !moduleOpenable(target, true)) {
            return;
        }
        if (target == MarketModule.SHOP) {
            closeNavigation(false);
            ShopPackets.CHANNEL.sendToServer(new C2SOpenShopPacket("default"));
            return;
        }
        synchronizeRoute();
        sendOpen(navigation.beginSwitchModule(
                UUID.randomUUID(), target));
    }

    private void openView(String view) {
        if (!navigation.isOpen() || moduleCapability(module)
                .map(capability -> !capability.canOpenView(view))
                .orElse(false)) {
            return;
        }
        synchronizeRoute();
        sendOpen(navigation.beginTab(UUID.randomUUID(), view));
    }

    private void openDetail(
            int cardIndex,
            MarketPageCard card
    ) {
        if (!navigation.isOpen() || isDetailView()
                || moduleCapability(module).map(capability ->
                !capability.canOpenView(
                        MarketRoute.detailView(module))).orElse(false)) {
            return;
        }
        focusedCardIndex = cardIndex;
        selectionId = card.identity();
        if (search != null) {
            search.setFocused(false);
        }
        synchronizeRoute();
        navigation.rememberDetail(card);
        UUID requestId = UUID.randomUUID();
        MarketRoute detailRoute = navigation.current().toDetail(
                card.identity(), requestId);
        sendOpen(navigation.beginDetail(requestId, detailRoute));
    }

    private void sendOpen(
            MarketClientNavigationCoordinator.OpenRequest request
    ) {
        if (pendingOpenRequest != null
                && !pendingOpenRequest.equals(request.requestId())) {
            ShopClientPacketHandler.cancelMarketOpen(pendingOpenRequest);
            navigation.cancelOpen(pendingOpenRequest);
        }
        pendingOpenRequest = request.requestId();
        ShopPackets.CHANNEL.sendToServer(new C2SOpenMarketModulePacket(
                request.requestId(), request.module().id(),
                request.viewId()));
    }

    private void navigateBack() {
        if (!navigation.isOpen()) {
            return;
        }
        synchronizeRoute();
        MarketClientNavigationCoordinator.Command command =
                navigation.back(UUID.randomUUID());
        if (command.openRequest().isPresent()) {
            sendOpen(command.openRequest().orElseThrow());
        } else {
            closeNavigation(false);
            super.onClose();
        }
    }

    private void requestPage(int pageIndex) {
        requestedPage = Math.max(0, pageIndex);
        scrollOffset = 0;
        selectionId = "";
        sendPageQuery();
    }

    private void sendPageQuery() {
        if (!navigation.isOpen() || layout == null
                || isDetailView() || !canOpenView(packet.view())) {
            return;
        }
        UUID requestId = UUID.randomUUID();
        synchronizeRoute();
        navigation.beginResponseRequest(requestId,
                MarketResponseFamily.CONTENT);
        int pageSize = queryPageSize();
        C2SMarketPageQueryPacket query =
                new C2SMarketPageQueryPacket(requestId,
                        navigation.current().routeNonce(), module.id(),
                        packet.view(),
                        observedSearch, selectedCategory, selectedSort,
                        requestedPage,
                        pageSize, OptionalLong.empty(),
                        OptionalLong.empty());
        sentSearch = observedSearch;
        pageResult = "LOADING";
        page = null;
        ShopPackets.CHANNEL.sendToServer(query);
    }

    private void synchronizeRoute() {
        MarketRoute current = navigation.current();
        navigation.updateCurrent(new MarketRoute(module, packet.view(),
                selectedCategory, observedSearch, current.filterId(),
                selectedSort, requestedPage, scrollOffset, selectionId,
                current.routeNonce()));
    }

    private int queryPageSize() {
        MarketRectangle content = layout.content();
        int rows = Math.max(1,
                (content.height() + layout.padding())
                        / (46 + layout.padding()));
        return Math.max(4, Math.min(28,
                Math.multiplyExact(layout.cardColumns(), rows)));
    }

    private MarketCardLayout.Placement cardPlacement() {
        if (layout == null || page == null) {
            return new MarketCardLayout.Placement(List.of(), 0);
        }
        return MarketCardLayout.place(layout.content(),
                layout.cardColumns(), layout.padding(),
                Math.min(page.cards().size(),
                        MarketCardLayout.MAXIMUM_CARDS));
    }

    private void synchronizeFocusedCard() {
        if (page == null || page.cards().isEmpty()) {
            focusedCardIndex = -1;
            return;
        }
        if (!selectionId.isEmpty()) {
            for (int index = 0; index < page.cards().size(); index++) {
                if (selectionId.equals(page.cards().get(index).identity())) {
                    focusedCardIndex = index;
                    return;
                }
            }
        }
        if (focusedCardIndex >= page.cards().size()) {
            focusedCardIndex = page.cards().size() - 1;
        }
    }

    private boolean moveCardFocus(int keyCode, boolean reverse) {
        if (isDetailView() || page == null || search != null
                && search.isFocused()) {
            return false;
        }
        MarketCardLayout.Placement placement = cardPlacement();
        int count = placement.cards().size();
        if (count == 0) {
            return false;
        }
        int current = focusedCardIndex < 0
                || focusedCardIndex >= count ? -1 : focusedCardIndex;
        int next;
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            next = current < 0 ? reverse ? count - 1 : 0
                    : Math.floorMod(current + (reverse ? -1 : 1), count);
        } else if (keyCode == GLFW.GLFW_KEY_HOME) {
            next = 0;
        } else if (keyCode == GLFW.GLFW_KEY_END) {
            next = count - 1;
        } else {
            int delta = switch (keyCode) {
                case GLFW.GLFW_KEY_LEFT -> -1;
                case GLFW.GLFW_KEY_RIGHT -> 1;
                case GLFW.GLFW_KEY_UP -> -placement.columns();
                case GLFW.GLFW_KEY_DOWN -> placement.columns();
                default -> 0;
            };
            if (delta == 0) {
                return false;
            }
            next = current < 0 ? 0
                    : Math.max(0, Math.min(count - 1, current + delta));
        }
        focusedCardIndex = next;
        selectionId = page.cards().get(next).identity();
        synchronizeRoute();
        return true;
    }

    private boolean activateFocusedCard() {
        if (isDetailView() || page == null || search != null
                && search.isFocused()) {
            return false;
        }
        MarketCardLayout.Placement placement = cardPlacement();
        if (placement.cards().isEmpty()) {
            return false;
        }
        int index = focusedCardIndex < 0
                || focusedCardIndex >= placement.cards().size()
                ? 0 : focusedCardIndex;
        openDetail(index, page.cards().get(index));
        return true;
    }

    private static ItemStack displayStack(MarketPageCard card) {
        ResourceLocation key = ResourceLocation.tryParse(
                card.registryId());
        if (key == null) {
            return ItemStack.EMPTY;
        }
        var item = BuiltInRegistries.ITEM.get(key);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        int count = Math.max(1, Math.min(card.itemCount(),
                item.getMaxStackSize()));
        return new ItemStack(item, count);
    }

    private static String viewLabel(String view) {
        String key = switch (view) {
            case "products" -> "products";
            case "buy_orders" -> "buy_orders";
            case "sell_orders" -> "sell_orders";
            case "orders" -> "orders";
            case "portfolio" -> "portfolio";
            case "browse" -> "browse";
            case "create" -> "create";
            case "mine" -> "mine";
            case "bids" -> "bids";
            case "watched" -> "watched";
            case "claims" -> "claims";
            case "history" -> "history";
            case "product_detail", "listing_detail", "item_detail"
                    -> "details";
            default -> "unknown";
        };
        return Component.translatable(
                "gui.futureshops.market.view." + key).getString();
    }

    private List<String> localViews() {
        return module == MarketModule.BAZAAR
                ? List.of("products", "orders", "portfolio", "watched",
                "claims", "history")
                : List.of("browse", "create", "mine", "bids",
                "watched", "claims", "history");
    }

    private boolean showBackButton() {
        return navigation.historyDepth() > 0
                || !packet.view().equals(module.rootView());
    }

    private String activeRailView() {
        return isDetailView() && detailSelection != null
                ? detailSelection.sourceView() : packet.view();
    }

    private boolean isDetailView() {
        return MarketRoute.isDetailView(module, packet.view());
    }

    private static String durationLabel(long remainingMillis) {
        long seconds = Math.max(0L, remainingMillis / 1000L);
        long days = seconds / 86400L;
        long hours = seconds % 86400L / 3600L;
        long minutes = seconds % 3600L / 60L;
        if (days > 0L) {
            return Component.translatable(
                    "gui.futureshops.market.duration.days",
                    days, hours).getString();
        }
        if (hours > 0L) {
            return Component.translatable(
                    "gui.futureshops.market.duration.hours",
                    hours, minutes).getString();
        }
        return Component.translatable(
                "gui.futureshops.market.duration.minutes",
                minutes).getString();
    }

    private static String compactCount(long value) {
        if (value < 1000L) {
            return Long.toString(value);
        }
        if (value < 1000000L) {
            return value / 1000L + "K+";
        }
        return value / 1000000L + "M+";
    }

    private static String sortLabel(String sort) {
        String key = switch (sort) {
            case "instant_buy_lowest" -> "instant_buy_lowest";
            case "instant_sell_highest" -> "instant_sell_highest";
            case "spread_lowest" -> "spread_lowest";
            case "volume_highest" -> "volume_highest";
            case "trend_highest" -> "trend_highest";
            case "ending_soon" -> "ending_soon";
            case "newest" -> "newest";
            case "lowest_price" -> "lowest_price";
            case "highest_price" -> "highest_price";
            case "most_bids" -> "most_bids";
            case "seller" -> "seller";
            default -> "name";
        };
        return Component.translatable(
                "gui.futureshops.market.sort." + key).getString();
    }

    private String moduleLabel(
            MarketModule target,
            boolean compact
    ) {
        return moduleCapability(target)
                .map(MarketModuleCapability::displayName)
                .orElseGet(() -> {
                    if (target == module && !packet.displayName().isBlank()) {
                        return packet.displayName();
                    }
                    String suffix = switch (target) {
                        case SHOP -> "shop";
                        case BAZAAR -> "bazaar";
                        case AUCTION_HOUSE -> "auction_house";
                    };
                    return Component.translatable(
                            "gui.futureshops.market.module."
                                    + (compact ? "short." : "")
                                    + suffix).getString();
                });
    }

    private static String pageResultLabel(String resultCode) {
        String key = switch (resultCode) {
            case "LOADING" -> "loading";
            case "DETAIL_UNAVAILABLE" -> "detail_unavailable";
            case "CAPABILITY_BLOCKED" -> "capability_blocked";
            case "MODULE_CONTROL_UNAVAILABLE" -> "control_unavailable";
            case "ESCROW_NOT_READY" -> "escrow_not_ready";
            case "MODULE_FROZEN" -> "frozen";
            case "MODULE_DRAINING" -> "draining";
            case "MODULE_CANCEL_AND_REFUND" -> "cancelling";
            case "CLAIMS_ONLY" -> "claims_only";
            case "MODULE_DISABLED" -> "disabled";
            case "VIEW_DISABLED" -> "view_disabled";
            default -> "unavailable";
        };
        return Component.translatable(
                "gui.futureshops.market.result." + key).getString();
    }

    private static String defaultSort(MarketModule module) {
        return module == MarketModule.BAZAAR ? "name" : "ending_soon";
    }

    private static void border(GuiGraphics graphics,
                               MarketRectangle rectangle, int color) {
        graphics.fill(rectangle.x(), rectangle.y(), rectangle.right(),
                rectangle.y() + 1, color);
        graphics.fill(rectangle.x(), rectangle.bottom() - 1,
                rectangle.right(), rectangle.bottom(), color);
        graphics.fill(rectangle.x(), rectangle.y(), rectangle.x() + 1,
                rectangle.bottom(), color);
        graphics.fill(rectangle.right() - 1, rectangle.y(),
                rectangle.right(), rectangle.bottom(), color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int index = hits.size() - 1; index >= 0; index--) {
                Hit hit = hits.get(index);
                if (hit.rectangle().contains((int) mouseX, (int) mouseY)) {
                    hit.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_F5) {
            refreshMarketState();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && categoryDrawerOpen) {
            categoryDrawerOpen = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_G && hasControlDown()
                && layout.categoryDrawer()) {
            toggleCategoryDrawer();
            return true;
        }
        if (handleCategoryDrawerKey(keyCode)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB && hasControlDown()) {
            cycleLocalView(hasShiftDown());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (!navigation.isOpen()) {
                super.onClose();
                return true;
            }
            synchronizeRoute();
            MarketClientNavigationCoordinator.Command command =
                    navigation.escape(UUID.randomUUID());
            if (command.openRequest().isPresent()) {
                sendOpen(command.openRequest().orElseThrow());
            } else {
                closeNavigation(true);
                super.onClose();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F && hasControlDown()
                && search != null && !isDetailView()) {
            setFocused(search);
            search.setFocused(true);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB && page != null) {
            if (search != null && search.isFocused()) {
                search.setFocused(false);
                setFocused(null);
            }
            if (moveCardFocus(keyCode, hasShiftDown())) {
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT
                || keyCode == GLFW.GLFW_KEY_RIGHT
                || keyCode == GLFW.GLFW_KEY_UP
                || keyCode == GLFW.GLFW_KEY_DOWN
                || keyCode == GLFW.GLFW_KEY_HOME
                || keyCode == GLFW.GLFW_KEY_END) {
            if (moveCardFocus(keyCode, false)) {
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_SPACE) {
            if (activateFocusedCard()) {
                return true;
            }
        }
        if (page != null && keyCode == GLFW.GLFW_KEY_PAGE_UP
                && page.pageIndex() > 0) {
            requestPage(page.pageIndex() - 1);
            return true;
        }
        if (page != null && keyCode == GLFW.GLFW_KEY_PAGE_DOWN
                && page.pageIndex() + 1 < page.pageCount()) {
            requestPage(page.pageIndex() + 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        closeNavigation(true);
        super.onClose();
    }

    @Override
    public void removed() {
        if (!navigationHandoff) {
            closeNavigation(explicitClose);
        }
        if (!navigationHandoff && !closeSent && minecraft != null
                && minecraft.getConnection() != null) {
            closeSent = true;
            ShopPackets.CHANNEL.sendToServer(
                    new C2SCloseMarketSessionPacket(
                            packet.routeNonce(), explicitClose));
        }
        super.removed();
    }

    public Optional<PaymentSource> rememberedPaymentSource() {
        return navigation.paymentSource();
    }

    public void rememberPaymentSource(PaymentSource source) {
        navigation.rememberPaymentSource(source);
    }

    public Optional<MarketModuleCapability> moduleCapability(
            MarketModule target
    ) {
        if (capabilities == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(capabilitiesByModule.get(target));
    }

    public long openClaimCount(MarketModule target) {
        return moduleCapability(target)
                .map(MarketModuleCapability::openClaims)
                .orElse(0L);
    }

    private void requestCapabilities() {
        if (minecraft != null && minecraft.getConnection() != null) {
            ShopClientPacketHandler.requestMarketCapabilities();
        }
    }

    private boolean canOpenView(String view) {
        if (MarketRoute.isDetailView(module, view)
                && detailSelection != null) {
            return true;
        }
        return moduleCapability(module)
                .map(capability -> capability.canOpenView(view))
                .orElseGet(() -> "claims".equals(view)
                        || packet.enabled() && packet.escrowReady());
    }

    private boolean currentAllowsClaims() {
        return moduleCapability(module)
                .map(capability -> capability.availability()
                        .allowsClaims())
                .orElse(true);
    }

    private MarketModuleAvailability currentAvailability() {
        return moduleCapability(module)
                .map(MarketModuleCapability::availability)
                .orElseGet(() -> packet.enabled()
                        && packet.escrowReady()
                        ? MarketModuleAvailability.ENABLED
                        : MarketModuleAvailability.DISABLED);
    }

    private boolean moduleVisible(MarketModule target) {
        return moduleCapability(target)
                .map(capability -> capability.availability().visible())
                .orElse(true);
    }

    private boolean moduleOpenable(
            MarketModule target,
            boolean fallback
    ) {
        return moduleCapability(target)
                .map(capability -> capability.availability().allowsBrowse()
                        || capability.availability().allowsClaims())
                .orElse(fallback);
    }

    private boolean showNavigation() {
        return capabilities == null
                ? packet.showNavigation()
                : capabilities.showNavigation();
    }

    private String currentDisplayName() {
        return moduleCapability(module)
                .map(MarketModuleCapability::displayName)
                .orElse(packet.displayName());
    }

    private String currentAccent() {
        return moduleCapability(module)
                .map(MarketModuleCapability::accentHex)
                .orElse(packet.accentColor());
    }

    private String claimBadge(MarketModule target) {
        long claims = openClaimCount(target);
        if (claims <= 0L) {
            return "";
        }
        if (claims < 1000L) {
            return " " + claims;
        }
        if (claims < 1000000L) {
            return " " + claims / 1000L + "K+";
        }
        return " " + claims / 1000000L + "M+";
    }

    private void closeNavigation(boolean explicit) {
        explicitClose = explicitClose || explicit;
        if (pendingOpenRequest != null) {
            ShopClientPacketHandler.cancelMarketOpen(pendingOpenRequest);
            navigation.cancelOpen(pendingOpenRequest);
            pendingOpenRequest = null;
        }
        if (navigation.isOpen()) {
            navigation.close();
        }
        ShopClientPacketHandler.releaseMarketNavigation(navigation);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Hit(MarketRectangle rectangle, Runnable action) {
    }
}
