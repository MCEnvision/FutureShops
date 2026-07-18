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
import com.enviouse.futureshops.client.market.MarketActionFeedback;
import com.enviouse.futureshops.client.market.MarketPendingActionTracker;
import com.enviouse.futureshops.command.EconomyCommandUtil;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SAuctionBidPacket;
import com.enviouse.futureshops.network.packets.C2SAuctionBuyNowPacket;
import com.enviouse.futureshops.network.packets.C2SAuctionCancelPacket;
import com.enviouse.futureshops.network.packets.C2SAuctionCreatePacket;
import com.enviouse.futureshops.network.packets.C2SBazaarCancelPacket;
import com.enviouse.futureshops.network.packets.C2SBazaarOrderPacket;
import com.enviouse.futureshops.network.packets.C2SOpenMarketModulePacket;
import com.enviouse.futureshops.network.packets.C2SCloseMarketSessionPacket;
import com.enviouse.futureshops.network.packets.C2SMarketPageQueryPacket;
import com.enviouse.futureshops.network.packets.C2SMarketProfileMutationPacket;
import com.enviouse.futureshops.network.packets.C2SOpenShopPacket;
import com.enviouse.futureshops.network.packets.S2CMarketActionResponsePacket;
import com.enviouse.futureshops.network.packets.S2COpenMarketModulePacket;
import com.enviouse.futureshops.network.packets.S2CMarketPagePacket;
import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.server.market.auction.AuctionListingType;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderSide;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderType;
import com.enviouse.futureshops.server.market.bazaar.BazaarTimeInForce;
import com.enviouse.futureshops.server.market.profile.MarketProfileMutation;
import com.enviouse.futureshops.server.market.profile.MarketProfileMutationCommand;
import com.enviouse.futureshops.server.market.profile.MarketProfileMutationResult;
import com.enviouse.futureshops.server.market.profile.MarketProfileMutationResultCode;
import com.enviouse.futureshops.server.market.profile.MarketProfileSavedData;
import com.enviouse.futureshops.server.market.query.MarketPageCard;
import com.enviouse.futureshops.server.market.query.MarketPageCardKind;
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
    private static final long ARMED_CONFIRM_WINDOW_MILLIS = 4_000L;
    private static final long ACTION_STATUS_VISIBLE_MILLIS = 8_000L;
    private static final int PLAYER_MAIN_INVENTORY_SLOTS = 36;
    private static final long DEFAULT_CREATE_DURATION_SECONDS = 86_400L;

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
    /**
     * Shared across screen instances on purpose: navigation replaces the screen object, but a
     * request that is still unanswered (including TIMED_OUT awaiting an explicit retry or
     * give-up) must keep blocking fresh sends for its family+subject wherever the user goes in
     * the market session — a new request UUID for the same intent would be a second economic
     * mutation. Cleared when the market session truly closes ({@link #closeNavigation}): the
     * server session (and with it the per-request replay state) dies there too.
     */
    private static final MarketPendingActionTracker PENDING_ACTIONS =
            new MarketPendingActionTracker();
    /**
     * Payment source each in-flight request was sent with. A source is only remembered on the
     * session ({@link #rememberPaymentSource}) once a request that used it comes back APPLIED —
     * never at prompt time — so a denied choice can never become the silent default.
     */
    private static final java.util.LinkedHashMap<UUID, PaymentSource>
            PENDING_PAYMENT_SOURCES = new java.util.LinkedHashMap<>();
    private static final int MAXIMUM_TRACKED_PAYMENT_SOURCES = 32;
    /**
     * Sources the server answered PAYMENT_SOURCE_DENIED for during this market session. A
     * denied source is filtered out of the remembered-source fast path (so the prompt reopens
     * on the next money action) and its prompt button renders disabled with an
     * "unavailable" label. Market-session scope: cleared in {@link #closeNavigation}.
     */
    private static final java.util.EnumSet<PaymentSource>
            DENIED_PAYMENT_SOURCES =
            java.util.EnumSet.noneOf(PaymentSource.class);
    /** Static so the status strip survives the screen handoff a detail refresh performs. */
    private static Component actionStatus;
    private static boolean actionStatusSuccess;
    private static long actionStatusAtMillis;
    /**
     * One-shot marker for the detail-refresh round trip (see {@link #refreshAfterAction}): the
     * server only serves list pages to a session whose view IS that list, so a detail screen
     * refreshes by navigating back to its source list and re-opening the matching card from
     * the fresh page. This survives the intermediate screen swap.
     */
    private static DetailRefresh pendingDetailRefresh;
    private static final long DETAIL_REFRESH_WINDOW_MILLIS = 15_000L;
    private static long profileRevision;
    private static long profileReplayEpoch;
    private String armedConfirmKey = "";
    private long armedConfirmAtMillis;
    private boolean bidEditorOpen;
    private EditBox bidAmountBox;
    private EditBox bazaarQuantityBox;
    private EditBox bazaarLimitPriceBox;
    private java.util.function.Consumer<PaymentSource> paymentPromptAction;
    private Component paymentPromptDetail;
    private boolean createWizardOpen;
    private int createSelectedSlot = -1;
    /**
     * SHA-256 identity of the stack captured when the player CLICKED the slot (plan §8 step
     * 7) — deliberately not recomputed at send time, so the server rejects the create when
     * the slot's live content changed between selection and processing.
     */
    private String createSelectedFingerprint = "";
    private AuctionListingType createType = AuctionListingType.TIMED_AUCTION;
    private long createDurationSeconds = DEFAULT_CREATE_DURATION_SECONDS;
    private List<Long> createDurationPresets = List.of(
            3_600L, 21_600L, 86_400L, 259_200L, 604_800L);
    private EditBox createStartBidBox;
    private EditBox createBuyoutBox;
    private boolean profileMutationPending;

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
        for (EditBox box : activeOverlayBoxes()) {
            box.tick();
        }
        if (!PENDING_ACTIONS.expire(Util.getMillis(),
                MarketPendingActionTracker.DEFAULT_TIMEOUT_MILLIS)
                .isEmpty()) {
            // The entry stays tracked (double-spend guard): the button surface now offers
            // an explicit same-request Retry and a give-up ✕ instead of a fresh send.
            showActionStatus(MarketActionFeedback.timeoutMessage(), false);
        }
        if (!normalizedSearch().equals(sentSearch)
                && Util.getMillis() - searchChangedAtMillis
                >= ClientConfig.settings().search().debounceMillis()) {
            sendPageQuery();
        }
    }

    /**
     * Main-thread entry for {@code S2CMarketActionResponsePacket} (plan §12). Clears the
     * matching pending request, localizes the result, and — for both success and the
     * stale-revision family — refreshes capabilities and the current page so revisions and
     * cards match the server again (plan §15: stale interfaces refresh rather than execute).
     * On a detail route the refresh goes through {@link #refreshAfterAction}: back to the
     * source list, then re-open the matching card from the fresh page, because the server
     * only answers page queries for the session's own view.
     */
    public void applyActionResponse(S2CMarketActionResponsePacket response) {
        Optional<MarketPendingActionTracker.PendingAction> pending =
                PENDING_ACTIONS.complete(response.requestId());
        String actionKey = pending
                .map(MarketPendingActionTracker.PendingAction::actionKey)
                .orElseGet(() -> MarketActionFeedback.actionKey(
                        response.moduleId(), response.action()));
        String subjectId = pending
                .map(MarketPendingActionTracker.PendingAction::subjectId)
                .orElse("");
        PaymentSource usedSource =
                PENDING_PAYMENT_SOURCES.remove(response.requestId());
        armedConfirmKey = "";
        if (response.applied()) {
            if (usedSource != null) {
                // The only moment a payment source becomes the remembered session
                // default: a request that actually used it was APPLIED.
                rememberPaymentSource(usedSource);
            }
            showActionStatus(
                    MarketActionFeedback.successMessage(actionKey), true);
            if ("auction_bid".equals(actionKey)) {
                bidEditorOpen = false;
            }
            if ("auction_create".equals(actionKey)) {
                closeCreateWizard();
            }
            refreshAfterAction(subjectId);
            return;
        }
        if ("PAYMENT_SOURCE_DENIED".equals(response.status())) {
            if (usedSource != null) {
                // Never offer this source silently again this session; the prompt
                // reopens on the next money action with it disabled.
                DENIED_PAYMENT_SOURCES.add(usedSource);
            }
            showActionStatus(
                    MarketActionFeedback.failureMessage(response), false);
            return;
        }
        if (MarketActionFeedback.stale(response.status())) {
            showActionStatus(
                    MarketActionFeedback.staleMessage(response.status()),
                    false);
            refreshAfterAction(subjectId);
            return;
        }
        showActionStatus(
                MarketActionFeedback.failureMessage(response), false);
    }

    /**
     * Post-action refresh (plan §15). On list routes this is a straight capabilities+page
     * reload. On a detail route the shown card is a click-time snapshot and the server serves
     * pages only for the session's own view — so when the answered action concerned the shown
     * card (tracked subject matches), refresh by navigating back to the source list and
     * re-opening the matching card from the fresh page ({@link #reopenRefreshedDetail}); the
     * remembered detail card is replaced with live revision/prices on the way. Responses the
     * tracker no longer knows (duplicate of an already-handled reply, or arriving after an
     * explicit give-up) fall through to the plain refresh — no extra route round trip.
     */
    private void refreshAfterAction(String subjectId) {
        if (isDetailView() && detailSelection != null
                && navigation.isOpen() && navigation.historyDepth() > 0
                && !subjectId.isEmpty()
                && sameDetailEntity(subjectId,
                detailSelection.identity())) {
            pendingDetailRefresh = new DetailRefresh(module,
                    detailSelection.identity(), Util.getMillis());
            navigateBack();
            return;
        }
        refreshMarketState();
    }

    /**
     * Whether two card identities name the same market entity. Bazaar identities embed the
     * product version ({@code productId@version}), which bumps on every rule change — the
     * entity is the product, so compare the id part only.
     */
    private boolean sameDetailEntity(String left, String right) {
        if (left.equals(right)) {
            return true;
        }
        if (module == MarketModule.BAZAAR) {
            String leftProduct = bazaarProductId(left);
            return leftProduct != null
                    && leftProduct.equals(bazaarProductId(right));
        }
        return false;
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
            profileRevision = incoming.profileRevision();
            profileReplayEpoch = incoming.profileReplayEpoch();
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
            synchronizeRememberedDetailSelections(incoming);
            DetailRefresh refresh = consumeDetailRefresh();
            if (refresh != null) {
                reopenRefreshedDetail(refresh, incoming);
            }
        }
    }

    /**
     * Every arriving list page overwrites matching remembered detail cards, so a detail route
     * entered (or re-entered) afterwards renders live revision/prices instead of the
     * click-time snapshot it was remembered with.
     */
    private void synchronizeRememberedDetailSelections(
            MarketPageSnapshot incoming
    ) {
        if (!navigation.isOpen()) {
            return;
        }
        for (MarketPageCard card : incoming.cards()) {
            if (navigation.detailSelection(module, card.identity())
                    .isPresent()) {
                navigation.rememberDetail(card);
            }
        }
    }

    private DetailRefresh consumeDetailRefresh() {
        DetailRefresh refresh = pendingDetailRefresh;
        if (refresh == null) {
            return null;
        }
        if (refresh.module() != module
                || Util.getMillis() - refresh.armedAtMillis()
                > DETAIL_REFRESH_WINDOW_MILLIS) {
            pendingDetailRefresh = null;
            return null;
        }
        pendingDetailRefresh = null;
        return refresh;
    }

    /**
     * Completion of the detail-refresh round trip: the fresh source-list page arrived — re-open
     * the detail card that matches the refreshed entity (updating the remembered card on the
     * way through {@code openDetail}). When no card matches, the entity is gone from this view:
     * show the module's not-found status and stay on the list.
     */
    private void reopenRefreshedDetail(
            DetailRefresh refresh,
            MarketPageSnapshot incoming
    ) {
        List<MarketPageCard> cards = incoming.cards();
        for (int index = 0; index < cards.size(); index++) {
            MarketPageCard card = cards.get(index);
            if (sameDetailEntity(refresh.identity(), card.identity())) {
                openDetail(index, card);
                return;
            }
        }
        showActionStatus(Component.translatable(
                "gui.futureshops.market.action.status."
                        + (module == MarketModule.BAZAAR
                        ? "product_missing" : "not_found")), false);
    }

    public void applyCapabilities(
            MarketCapabilitiesSnapshot snapshot
    ) {
        boolean previouslyOpen = canOpenView(packet.view());
        capabilities = java.util.Objects.requireNonNull(
                snapshot, "snapshot");
        createDurationPresets = snapshot.auctionDurationPresetSeconds();
        if (createDurationSeconds != 0L
                && !createDurationPresets.contains(
                createDurationSeconds)) {
            createDurationSeconds = defaultCreateDurationSeconds();
        }
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

    public void applyProfileMutationResult(
            MarketProfileMutationResult result
    ) {
        if (result.module() != module
                || !result.routeNonce().equals(packet.routeNonce())) {
            return;
        }
        profileMutationPending = false;
        profileRevision = result.profileRevision();
        profileReplayEpoch = result.replayEpoch();
        unreadNotifications = result.unreadNotificationCount();
        MarketProfileMutationResultCode code = result.resultCode();
        boolean success = code == MarketProfileMutationResultCode.SUCCESS
                || code == MarketProfileMutationResultCode.NO_CHANGE;
        String status = switch (code) {
            case SUCCESS -> "success";
            case NO_CHANGE -> "no_change";
            case STALE_PROFILE, STALE_REPLAY_EPOCH -> "stale";
            case PERMISSION_DENIED -> "permission_denied";
            case LIMIT_REACHED -> "limit_reached";
            case TARGET_NOT_FOUND -> "target_not_found";
            case RATE_LIMITED -> "rate_limited";
            default -> "failed";
        };
        showActionStatus(Component.translatable(
                "gui.futureshops.market.profile.status." + status),
                success);
        if (success || code == MarketProfileMutationResultCode.STALE_PROFILE
                || code
                == MarketProfileMutationResultCode.STALE_REPLAY_EPOCH) {
            refreshAfterAction(detailSelection == null
                    ? "" : detailSelection.identity());
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
        renderCreateWizard(graphics, mouseX, mouseY);
        renderActionStatus(graphics);
        renderPaymentPrompt(graphics, mouseX, mouseY);
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
            renderDetail(graphics, content, mouseX, mouseY);
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
            renderOrderCancelButton(graphics, card, data, mouseX,
                    mouseY);
        }
    }

    /**
     * Own-order cards on the Bazaar order views carry an inline armed-confirm [Cancel]
     * (plan §9: cancelling returns only unfilled value as claims). Registered after the card
     * hit so the button wins the click.
     */
    private void renderOrderCancelButton(
            GuiGraphics graphics,
            MarketRectangle card,
            MarketPageCard data,
            int mouseX,
            int mouseY
    ) {
        if (module != MarketModule.BAZAAR
                || data.kind() != MarketPageCardKind.BAZAAR_ORDER
                || !orderCancellable(data.state())
                || !viewerOwns(data)
                || parseUuid(data.identity()) == null
                || card.width() < 96 || card.height() < 40) {
            return;
        }
        String armKey = "bazaar_cancel:" + data.identity();
        String label = armed(armKey)
                ? Component.translatable(
                "gui.futureshops.market.action.order.cancel_confirm")
                .getString()
                : Component.translatable(
                "gui.futureshops.market.action.order.cancel")
                .getString();
        MarketRectangle cancel = new MarketRectangle(
                card.right() - 52, card.bottom() - 18, 46, 14);
        actionButton(graphics, mouseX, mouseY, cancel, label, true,
                true, "bazaar_cancel", data.identity(), true,
                () -> armOrRun(armKey, () -> sendBazaarCancel(data)));
    }

    private void renderDetail(
            GuiGraphics graphics,
            MarketRectangle content,
            int mouseX,
            int mouseY
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
        MarketRectangle info = new MarketRectangle(panel.x(), panel.y(),
                panel.width(), Math.max(60, panel.height()
                - detailActionReservedHeight(data)));
        y = detailRow(graphics, info, x, y,
                "gui.futureshops.market.detail.state", data.state());
        if (!data.category().isEmpty()) {
            y = detailRow(graphics, info, x, y,
                    "gui.futureshops.market.detail.category",
                    data.category());
        }
        if (!data.registryId().isEmpty()) {
            y = detailRow(graphics, info, x, y,
                    "gui.futureshops.market.detail.item",
                    data.registryId());
        }
        if (data.quantity() > 0L) {
            y = detailRow(graphics, info, x, y,
                    "gui.futureshops.market.detail.quantity",
                    Long.toString(data.quantity()));
        }
        if (data.primaryMinor() > 0L) {
            y = detailRow(graphics, info, x, y,
                    "gui.futureshops.market.detail.primary",
                    ShopUiUtil.formatMinorUnits(data.primaryMinor()));
        }
        if (data.secondaryMinor() > 0L) {
            y = detailRow(graphics, info, x, y,
                    "gui.futureshops.market.detail.secondary",
                    ShopUiUtil.formatMinorUnits(data.secondaryMinor()));
        }
        if (data.remainingMillis() > 0L
                && data.remainingMillis() != Long.MAX_VALUE) {
            y = detailRow(graphics, info, x, y,
                    "gui.futureshops.market.detail.remaining",
                    durationLabel(data.remainingMillis()));
        }
        detailRow(graphics, info, x, y,
                "gui.futureshops.market.detail.identity",
                data.identity());
        if (!renderDetailActions(graphics, panel, padding,
                mouseX, mouseY)) {
            graphics.drawString(font,
                    Component.translatable(
                            "gui.futureshops.market.detail.read_only"),
                    panel.x() + padding, panel.bottom() - padding - 8,
                    theme.textMuted(), false);
        }
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

    // ── Action surface (plan §8/§9/§12) ─────────────────────────────

    /** Bottom strip height reserved on the detail panel for the action rows. */
    private int detailActionReservedHeight(MarketPageCard card) {
        if (module == MarketModule.AUCTION_HOUSE
                && card.kind() == MarketPageCardKind.AUCTION
                && auctionDetailHasActions(card)) {
            return bidEditorOpen ? 84 : 62;
        }
        if (module == MarketModule.BAZAAR
                && card.kind() == MarketPageCardKind.BAZAAR_PRODUCT
                && bazaarDetailHasActions(card)) {
            return 102;
        }
        return 0;
    }

    private boolean renderDetailActions(
            GuiGraphics graphics,
            MarketRectangle panel,
            int padding,
            int mouseX,
            int mouseY
    ) {
        if (detailSelection == null || createWizardOpen) {
            return false;
        }
        MarketPageCard card = detailSelection.card();
        if (module == MarketModule.AUCTION_HOUSE
                && card.kind() == MarketPageCardKind.AUCTION
                && auctionDetailHasActions(card)) {
            renderAuctionDetailActions(graphics, panel, padding, card,
                    mouseX, mouseY);
            return true;
        }
        if (module == MarketModule.BAZAAR
                && card.kind() == MarketPageCardKind.BAZAAR_PRODUCT
                && bazaarDetailHasActions(card)) {
            renderBazaarProductActions(graphics, panel, padding, card,
                    mouseX, mouseY);
            return true;
        }
        return false;
    }

    /**
     * Auction listings only accept bids while the projector reports a finite deadline —
     * pure BUY_NOW lots carry {@code Long.MAX_VALUE} remaining time on the wire, which is
     * the only type signal the page card exposes.
     */
    private static boolean auctionAcceptsBids(MarketPageCard card) {
        return card.remainingMillis() != Long.MAX_VALUE;
    }

    private boolean viewerOwns(MarketPageCard card) {
        return minecraft != null && minecraft.player != null
                && card.ownerId()
                .filter(minecraft.player.getUUID()::equals)
                .isPresent();
    }

    /** Buy-now is offered on pure BUY_NOW lots AND on bidding lots whose card carries a buyout. */
    private static boolean auctionHasBuyNow(MarketPageCard card) {
        return !auctionAcceptsBids(card) ? card.primaryMinor() > 0L
                : card.tertiaryMinor() > 0L;
    }

    private boolean auctionDetailHasActions(MarketPageCard card) {
        return "ACTIVE".equals(card.state())
                && parseUuid(card.identity()) != null;
    }

    private boolean bazaarDetailHasActions(MarketPageCard card) {
        return "ACTIVE".equals(card.state())
                && bazaarProductId(card.identity()) != null;
    }

    private void renderAuctionDetailActions(
            GuiGraphics graphics,
            MarketRectangle panel,
            int padding,
            MarketPageCard card,
            int mouseX,
            int mouseY
    ) {
        boolean own = viewerOwns(card);
        boolean acceptsBids = auctionAcceptsBids(card);
        int rowHeight = 16;
        int y = panel.bottom() - padding - rowHeight + 2;
        int x = panel.x() + padding;
        int profileY = y - (bidEditorOpen ? 40 : 20);
        String watchLabel = Component.translatable(card.watched()
                ? "gui.futureshops.market.profile.unwatch"
                : "gui.futureshops.market.profile.watch").getString();
        button(graphics, mouseX, mouseY,
                new MarketRectangle(x, profileY,
                        Math.max(76, font.width(watchLabel) + 14),
                        rowHeight), watchLabel,
                !profileMutationPending,
                () -> submitAuctionWatch(card));
        if (!own && acceptsBids) {
            // Retry host for auction_bid: when the send timed out this control turns
            // into [Retry][✕] even while the editor is closed, so the resend of the
            // original request stays reachable.
            actionButton(graphics, mouseX, mouseY,
                    new MarketRectangle(x, y, 78, rowHeight),
                    Component.translatable(
                            "gui.futureshops.market.action.bid.button")
                            .getString(),
                    true, false, "auction_bid", card.identity(), true,
                    () -> toggleBidEditor(card));
            x += 84;
        }
        if (!own && auctionHasBuyNow(card)) {
            actionButton(graphics, mouseX, mouseY,
                    new MarketRectangle(x, y, 78, rowHeight),
                    Component.translatable(
                            "gui.futureshops.market.action.buy_now.button")
                            .getString(),
                    true, false, "auction_buy_now", card.identity(),
                    true, () -> sendAuctionBuyNow(card));
            x += 84;
        }
        if (own && "mine".equals(detailSelection.sourceView())
                && card.quantity() == 0L) {
            String armKey = "auction_cancel:" + card.identity();
            String label = armed(armKey)
                    ? Component.translatable(
                    "gui.futureshops.market.action.confirm_again")
                    .getString()
                    : Component.translatable(
                    "gui.futureshops.market.action.cancel.button")
                    .getString();
            int width = Math.max(90, font.width(label) + 14);
            actionButton(graphics, mouseX, mouseY,
                    new MarketRectangle(panel.right() - padding - width,
                            y, width, rowHeight),
                    label, true, true, "auction_cancel",
                    card.identity(), true,
                    () -> armOrRun(armKey,
                            () -> sendAuctionCancel(card)));
        }
        if (bidEditorOpen && !own && acceptsBids) {
            renderBidEditorRow(graphics, panel, padding, card,
                    y - rowHeight - 6, mouseX, mouseY);
        }
    }

    private void renderBidEditorRow(
            GuiGraphics graphics,
            MarketRectangle panel,
            int padding,
            MarketPageCard card,
            int y,
            int mouseX,
            int mouseY
    ) {
        int x = panel.x() + padding;
        bidAmountBox = ensureOverlayBox(bidAmountBox, true, 20);
        button(graphics, mouseX, mouseY,
                new MarketRectangle(x, y, 16, 16), "−", true,
                () -> adjustBidAmount(card, -1L));
        positionBox(bidAmountBox, x + 20, y + 1, 72);
        bidAmountBox.render(graphics, mouseX, mouseY, 0.0F);
        button(graphics, mouseX, mouseY,
                new MarketRectangle(x + 96, y, 16, 16), "+", true,
                () -> adjustBidAmount(card, 1L));
        // Not a retry host — the always-visible bid toggle button hosts the retry pair.
        actionButton(graphics, mouseX, mouseY,
                new MarketRectangle(x + 118, y, 62, 16),
                Component.translatable(
                        "gui.futureshops.market.action.bid.confirm")
                        .getString(),
                true, false, "auction_bid", card.identity(), false,
                () -> sendAuctionBid(card));
        String minimum = Component.translatable(
                "gui.futureshops.market.action.bid.minimum",
                ShopUiUtil.formatMinorUnits(card.secondaryMinor()))
                .getString();
        graphics.drawString(font,
                font.plainSubstrByWidth(minimum,
                        Math.max(8, panel.right() - padding - x - 186)),
                x + 186, y + 4, theme.textMuted(), false);
    }

    private void renderBazaarProductActions(
            GuiGraphics graphics,
            MarketRectangle panel,
            int padding,
            MarketPageCard card,
            int mouseX,
            int mouseY
    ) {
        String identity = card.identity();
        long ask = card.primaryMinor();
        long bid = card.secondaryMinor();
        int x = panel.x() + padding;
        int limitY = panel.bottom() - padding - 16 + 2;
        int instantY = limitY - 20;
        int quantityY = instantY - 20;
        int profileY = quantityY - 20;
        String favoriteLabel = Component.translatable(card.watched()
                ? "gui.futureshops.market.profile.unfavorite"
                : "gui.futureshops.market.profile.favorite").getString();
        button(graphics, mouseX, mouseY,
                new MarketRectangle(x, profileY,
                        Math.max(82, font.width(favoriteLabel) + 14),
                        16), favoriteLabel, !profileMutationPending,
                () -> submitBazaarFavorite(card));
        String quantityLabel = Component.translatable(
                "gui.futureshops.market.action.quantity").getString();
        graphics.drawString(font, quantityLabel, x, quantityY + 4,
                theme.textMuted(), false);
        int quantityX = x + Math.min(76, font.width(quantityLabel) + 8);
        bazaarQuantityBox = ensureOverlayBox(bazaarQuantityBox, false, 7);
        if (bazaarQuantityBox.getValue().isEmpty()) {
            bazaarQuantityBox.setValue("1");
        }
        button(graphics, mouseX, mouseY,
                new MarketRectangle(quantityX, quantityY, 16, 16), "−",
                true, () -> adjustBazaarQuantity(-1));
        positionBox(bazaarQuantityBox, quantityX + 20, quantityY + 1, 48);
        bazaarQuantityBox.render(graphics, mouseX, mouseY, 0.0F);
        button(graphics, mouseX, mouseY,
                new MarketRectangle(quantityX + 72, quantityY, 16, 16),
                "+", true, () -> adjustBazaarQuantity(1));
        int quantity = parseBazaarQuantity();
        if (quantity > 0 && ask > 0L) {
            try {
                String total = Component.translatable(
                        "gui.futureshops.market.action.total",
                        ShopUiUtil.formatMinorUnits(Math.multiplyExact(
                                ask, (long) quantity))).getString();
                graphics.drawString(font,
                        font.plainSubstrByWidth(total, Math.max(8,
                                panel.right() - padding - quantityX - 96)),
                        quantityX + 96, quantityY + 4,
                        theme.textMuted(), false);
            } catch (ArithmeticException ignored) {
                // Total would overflow — the server rejects it anyway.
            }
        }
        // All four order buttons share the bazaar_order family+subject; only the first one
        // hosts the timed-out [Retry][✕] pair (the retry resends whatever the original
        // order was), the siblings just render the shared busy state.
        actionButton(graphics, mouseX, mouseY,
                new MarketRectangle(x, instantY, 82, 16),
                Component.translatable(
                        "gui.futureshops.market.action.instant_buy")
                        .getString(),
                ask > 0L, false, "bazaar_order", identity, true,
                () -> submitBazaarInstant(card, BazaarOrderSide.BUY, ask));
        actionButton(graphics, mouseX, mouseY,
                new MarketRectangle(x + 88, instantY, 82, 16),
                Component.translatable(
                        "gui.futureshops.market.action.instant_sell")
                        .getString(),
                bid > 0L, false, "bazaar_order", identity, false,
                () -> submitBazaarInstant(card, BazaarOrderSide.SELL, bid));
        String limitLabel = Component.translatable(
                "gui.futureshops.market.action.limit_price").getString();
        graphics.drawString(font, limitLabel, x, limitY + 4,
                theme.textMuted(), false);
        int limitX = x + Math.min(76, font.width(limitLabel) + 8);
        bazaarLimitPriceBox = ensureOverlayBox(
                bazaarLimitPriceBox, true, 20);
        positionBox(bazaarLimitPriceBox, limitX, limitY + 1, 62);
        bazaarLimitPriceBox.render(graphics, mouseX, mouseY, 0.0F);
        actionButton(graphics, mouseX, mouseY,
                new MarketRectangle(limitX + 68, limitY, 66, 16),
                Component.translatable(
                        "gui.futureshops.market.action.limit_buy")
                        .getString(),
                true, false, "bazaar_order", identity, false,
                () -> submitBazaarLimit(card, BazaarOrderSide.BUY));
        actionButton(graphics, mouseX, mouseY,
                new MarketRectangle(limitX + 140, limitY, 66, 16),
                Component.translatable(
                        "gui.futureshops.market.action.limit_sell")
                        .getString(),
                true, false, "bazaar_order", identity, false,
                () -> submitBazaarLimit(card, BazaarOrderSide.SELL));
    }

    private void toggleBidEditor(MarketPageCard card) {
        bidEditorOpen = !bidEditorOpen;
        if (bidEditorOpen) {
            bidAmountBox = ensureOverlayBox(bidAmountBox, true, 20);
            bidAmountBox.setValue(EconomyCommandUtil.formatMinorUnits(
                    card.secondaryMinor(),
                    ShopClientState.getCurrencyDecimals()));
        }
    }

    private void submitAuctionWatch(MarketPageCard card) {
        UUID listingId = parseUuid(card.identity());
        if (listingId == null || profileMutationPending) {
            return;
        }
        submitProfileMutation(new MarketProfileMutation.AuctionWatch(
                listingId, !card.watched()));
    }

    private void submitBazaarFavorite(MarketPageCard card) {
        String productId = bazaarProductId(card.identity());
        if (productId == null || card.revision() <= 0L
                || profileMutationPending) {
            return;
        }
        submitProfileMutation(new MarketProfileMutation.BazaarFavorite(
                new MarketProfileSavedData.ProductKey(productId,
                        card.revision()), !card.watched()));
    }

    private void submitProfileMutation(MarketProfileMutation mutation) {
        MarketProfileMutationCommand command =
                new MarketProfileMutationCommand(UUID.randomUUID(),
                        packet.routeNonce(), module, packet.view(),
                        profileRevision, profileReplayEpoch, mutation);
        if (ShopClientPacketHandler.submitMarketProfileMutation(
                new C2SMarketProfileMutationPacket(command))) {
            profileMutationPending = true;
        } else {
            showActionStatus(Component.translatable(
                    "gui.futureshops.market.profile.status.failed"),
                    false);
        }
    }

    private void adjustBidAmount(MarketPageCard card, long sign) {
        if (bidAmountBox == null) {
            return;
        }
        long step = card.secondaryMinor() - card.primaryMinor();
        if (step <= 0L) {
            step = wholeCurrencyUnitMinor();
        }
        long current = parseMoneyBox(bidAmountBox);
        if (current < 0L) {
            current = card.secondaryMinor();
        }
        long next = Math.max(card.secondaryMinor(),
                Math.min(C2SAuctionCreatePacket.MAX_MINOR,
                        current + sign * step));
        bidAmountBox.setValue(EconomyCommandUtil.formatMinorUnits(
                next, ShopClientState.getCurrencyDecimals()));
    }

    private static long wholeCurrencyUnitMinor() {
        long unit = 1L;
        for (int index = 0;
             index < ShopClientState.getCurrencyDecimals(); index++) {
            unit = Math.multiplyExact(unit, 10L);
        }
        return unit;
    }

    private void adjustBazaarQuantity(int sign) {
        if (bazaarQuantityBox == null) {
            return;
        }
        int step = hasShiftDown() ? 16 : 1;
        int current = parseBazaarQuantity();
        if (current < 1) {
            current = 1;
        }
        int next = Math.max(1,
                Math.min(C2SBazaarOrderPacket.MAX_QUANTITY,
                        current + sign * step));
        bazaarQuantityBox.setValue(Integer.toString(next));
    }

    private int parseBazaarQuantity() {
        if (bazaarQuantityBox == null) {
            return -1;
        }
        try {
            int value = Integer.parseInt(
                    bazaarQuantityBox.getValue().trim());
            return value >= 1 && value <= C2SBazaarOrderPacket.MAX_QUANTITY
                    ? value : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static long parseMoneyBox(EditBox box) {
        if (box == null) {
            return -1L;
        }
        try {
            return EconomyCommandUtil.parseAmountToMinorUnits(
                    box.getValue(),
                    ShopClientState.getCurrencyDecimals());
        } catch (IllegalArgumentException ignored) {
            return -1L;
        }
    }

    private void sendAuctionBid(MarketPageCard card) {
        UUID listingId = parseUuid(card.identity());
        long amountMinor = parseMoneyBox(bidAmountBox);
        if (listingId == null || amountMinor <= 0L) {
            showActionStatus(Component.translatable(
                    "gui.futureshops.market.action.invalid_amount"),
                    false);
            return;
        }
        requirePaymentSource(paymentAmountDetail(amountMinor, 1),
                source -> sendMarketAction("auction_bid", card.identity(),
                        requestId -> new C2SAuctionBidPacket(requestId,
                                packet.routeNonce(), listingId,
                                card.revision(), amountMinor, source.wire()))
                        .ifPresent(requestId ->
                                trackPaymentSource(requestId, source)));
    }

    private void sendAuctionBuyNow(MarketPageCard card) {
        UUID listingId = parseUuid(card.identity());
        if (listingId == null) {
            return;
        }
        long priceMinor = auctionAcceptsBids(card)
                ? card.tertiaryMinor() : card.primaryMinor();
        requirePaymentSource(paymentAmountDetail(priceMinor, 1),
                source -> sendMarketAction("auction_buy_now", card.identity(),
                        requestId -> new C2SAuctionBuyNowPacket(requestId,
                                packet.routeNonce(), listingId,
                                card.revision(), source.wire()))
                        .ifPresent(requestId ->
                                trackPaymentSource(requestId, source)));
    }

    private void sendAuctionCancel(MarketPageCard card) {
        UUID listingId = parseUuid(card.identity());
        if (listingId == null) {
            return;
        }
        sendMarketAction("auction_cancel", card.identity(),
                requestId -> new C2SAuctionCancelPacket(requestId,
                        packet.routeNonce(), listingId,
                        card.revision()));
    }

    private void sendBazaarCancel(MarketPageCard card) {
        UUID orderId = parseUuid(card.identity());
        if (orderId == null) {
            return;
        }
        sendMarketAction("bazaar_cancel", card.identity(),
                requestId -> new C2SBazaarCancelPacket(requestId,
                        packet.routeNonce(), orderId, card.revision()));
    }

    /**
     * Instant orders use IMMEDIATE_OR_CANCEL with the currently displayed book price as the
     * worst allowed execution price — the plan §9 slippage bound: the player never pays more
     * (or receives less) per unit than the price shown when the button was pressed.
     */
    private void submitBazaarInstant(
            MarketPageCard card,
            BazaarOrderSide side,
            long boundMinor
    ) {
        int quantity = parseBazaarQuantity();
        if (quantity < 1) {
            showActionStatus(Component.translatable(
                    "gui.futureshops.market.action.invalid_quantity"),
                    false);
            return;
        }
        if (side == BazaarOrderSide.BUY) {
            requirePaymentSource(paymentAmountDetail(boundMinor, quantity),
                    source -> sendBazaarOrder(card, side,
                            BazaarOrderType.INSTANT,
                            BazaarTimeInForce.IMMEDIATE_OR_CANCEL,
                            boundMinor, quantity, source));
            return;
        }
        sendBazaarOrder(card, side, BazaarOrderType.INSTANT,
                BazaarTimeInForce.IMMEDIATE_OR_CANCEL, boundMinor,
                quantity, PaymentSource.WALLET);
    }

    private void submitBazaarLimit(
            MarketPageCard card,
            BazaarOrderSide side
    ) {
        int quantity = parseBazaarQuantity();
        if (quantity < 1) {
            showActionStatus(Component.translatable(
                    "gui.futureshops.market.action.invalid_quantity"),
                    false);
            return;
        }
        long priceMinor = parseMoneyBox(bazaarLimitPriceBox);
        if (priceMinor <= 0L) {
            showActionStatus(Component.translatable(
                    "gui.futureshops.market.action.invalid_amount"),
                    false);
            return;
        }
        if (side == BazaarOrderSide.BUY) {
            requirePaymentSource(paymentAmountDetail(priceMinor, quantity),
                    source -> sendBazaarOrder(card, side,
                            BazaarOrderType.LIMIT,
                            BazaarTimeInForce.GOOD_UNTIL_CANCELLED,
                            priceMinor, quantity, source));
            return;
        }
        sendBazaarOrder(card, side, BazaarOrderType.LIMIT,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, priceMinor,
                quantity, PaymentSource.WALLET);
    }

    private Component paymentAmountDetail(long priceMinor, int quantity) {
        try {
            return Component.translatable(
                    "gui.futureshops.market.action.payment.detail",
                    ShopUiUtil.formatMinorUnits(Math.multiplyExact(
                            priceMinor, (long) quantity)));
        } catch (ArithmeticException ignored) {
            return Component.translatable(
                    "gui.futureshops.market.action.payment.detail",
                    ShopUiUtil.formatMinorUnits(priceMinor));
        }
    }

    private void sendBazaarOrder(
            MarketPageCard card,
            BazaarOrderSide side,
            BazaarOrderType type,
            BazaarTimeInForce timeInForce,
            long priceMinor,
            int quantity,
            PaymentSource source
    ) {
        String productId = bazaarProductId(card.identity());
        long version = bazaarProductVersion(card.identity());
        if (productId == null || version < 0L) {
            showActionStatus(Component.translatable(
                    "gui.futureshops.market.action.invalid_input"),
                    false);
            return;
        }
        sendMarketAction("bazaar_order", card.identity(),
                requestId -> new C2SBazaarOrderPacket(requestId,
                        packet.routeNonce(), productId, version,
                        side.name(), type.name(), timeInForce.name(),
                        priceMinor, quantity, 0L, source.wire()))
                .ifPresent(requestId ->
                        trackPaymentSource(requestId, source));
    }

    /** Associates a sent request with the payment source it used (finding: promote on APPLIED). */
    private static void trackPaymentSource(
            UUID requestId,
            PaymentSource source
    ) {
        PENDING_PAYMENT_SOURCES.put(requestId, source);
        while (PENDING_PAYMENT_SOURCES.size()
                > MAXIMUM_TRACKED_PAYMENT_SOURCES) {
            PENDING_PAYMENT_SOURCES.remove(
                    PENDING_PAYMENT_SOURCES.keySet().iterator().next());
        }
    }

    /**
     * Shared send path for every market mutation (plan §12): fresh request UUID, the screen's
     * route nonce inside the factory-built packet, pending registration for the busy state,
     * and a local status line instead of a crash when client-side validation rejects input.
     * The built packet is kept on the pending entry as a resend closure so a timed-out request
     * can be retried with the SAME request UUID (the server replays the stored result) instead
     * of minting a second, economically distinct request. Returns the request UUID on send.
     */
    private Optional<UUID> sendMarketAction(
            String actionKey,
            String subjectId,
            java.util.function.Function<UUID, Object> factory
    ) {
        UUID requestId = UUID.randomUUID();
        final Object message;
        try {
            message = factory.apply(requestId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            showActionStatus(Component.translatable(
                    "gui.futureshops.market.action.invalid_input"),
                    false);
            return Optional.empty();
        }
        Runnable resend =
                () -> ShopPackets.CHANNEL.sendToServer(message);
        if (!PENDING_ACTIONS.begin(requestId, actionKey, subjectId,
                Util.getMillis(), resend)) {
            showActionStatus(Component.translatable(
                    "gui.futureshops.market.action.busy"), false);
            return Optional.empty();
        }
        resend.run();
        return Optional.of(requestId);
    }

    /**
     * Plan §6 payment-source gate for money-committing Bazaar buys: the first commitment per
     * session asks Wallet vs Inventory Cash. The choice only becomes the remembered session
     * default once a request that used it returns APPLIED (see
     * {@link #applyActionResponse}) — a source the server denied is filtered out here, so
     * The prompt reopens after the server denies a source.
     */
    private void requirePaymentSource(
            Component detail,
            java.util.function.Consumer<PaymentSource> action
    ) {
        Optional<PaymentSource> remembered = rememberedPaymentSource()
                .filter(source ->
                        !DENIED_PAYMENT_SOURCES.contains(source));
        if (remembered.isPresent()) {
            action.accept(remembered.orElseThrow());
            return;
        }
        paymentPromptAction = action;
        paymentPromptDetail = detail;
    }

    private void closePaymentPrompt() {
        paymentPromptAction = null;
        paymentPromptDetail = null;
    }

    private void choosePaymentSource(PaymentSource source) {
        if (DENIED_PAYMENT_SOURCES.contains(source)) {
            return;
        }
        java.util.function.Consumer<PaymentSource> action =
                paymentPromptAction;
        closePaymentPrompt();
        if (action != null) {
            // Deliberately NOT remembered here — only an APPLIED response promotes the
            // source to the session default.
            action.accept(source);
        }
    }

    private void renderPaymentPrompt(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        if (paymentPromptAction == null) {
            return;
        }
        MarketRectangle window = layout.window();
        registerHit(window, this::closePaymentPrompt);
        int width = Math.min(236, Math.max(120, window.width() - 8));
        int height = Math.min(92, Math.max(60, window.height() - 8));
        MarketRectangle panel = new MarketRectangle(
                window.x() + (window.width() - width) / 2,
                window.y() + (window.height() - height) / 2,
                width, height);
        graphics.fill(panel.x(), panel.y(), panel.right(),
                panel.bottom(), SURFACE_RAISED);
        border(graphics, panel, theme.activeBorder());
        graphics.fill(panel.x(), panel.y(), panel.right(),
                panel.y() + 2, theme.accent());
        registerHit(panel, () -> {
        });
        graphics.drawString(font,
                font.plainSubstrByWidth(Component.translatable(
                        "gui.futureshops.market.action.payment.title")
                        .getString(), panel.width() - 30),
                panel.x() + 8, panel.y() + 7, theme.textStrong(), false);
        button(graphics, mouseX, mouseY,
                new MarketRectangle(panel.right() - 18, panel.y() + 4,
                        14, 12), "✕", true, this::closePaymentPrompt);
        int y = panel.y() + 22;
        if (paymentPromptDetail != null) {
            graphics.drawString(font,
                    font.plainSubstrByWidth(
                            paymentPromptDetail.getString(),
                            panel.width() - 16),
                    panel.x() + 8, y, theme.textMuted(), false);
            y += 12;
        }
        if (ShopClientState.isCurrentBalanceKnown()) {
            graphics.drawString(font,
                    font.plainSubstrByWidth(Component.translatable(
                            "gui.futureshops.market.action.payment.balance",
                            ShopUiUtil.formatMinorUnits(ShopClientState
                                    .getCurrentBalanceMinorUnits()))
                            .getString(), panel.width() - 16),
                    panel.x() + 8, y, theme.textMuted(), false);
        }
        int buttonY = panel.bottom() - 22;
        int buttonWidth = (panel.width() - 24) / 2;
        button(graphics, mouseX, mouseY,
                new MarketRectangle(panel.x() + 8, buttonY,
                        buttonWidth, 16),
                Component.translatable(
                        "gui.futureshops.market.action.payment.wallet")
                        .getString(), true,
                () -> choosePaymentSource(PaymentSource.WALLET));
        boolean physicalDenied = DENIED_PAYMENT_SOURCES.contains(
                PaymentSource.PHYSICAL);
        button(graphics, mouseX, mouseY,
                new MarketRectangle(panel.x() + 16 + buttonWidth,
                        buttonY, buttonWidth, 16),
                Component.translatable(physicalDenied
                        ? "gui.futureshops.market.action.payment.physical_unavailable"
                        : "gui.futureshops.market.action.payment.physical")
                        .getString(), !physicalDenied,
                () -> choosePaymentSource(PaymentSource.PHYSICAL));
    }

    // ── Create-listing wizard (plan §8 listing creation) ────────────

    private boolean showCreateListingButton() {
        return module == MarketModule.AUCTION_HOUSE
                && ("browse".equals(packet.view())
                || "mine".equals(packet.view()))
                && canOpenView(packet.view())
                && minecraft != null && minecraft.player != null;
    }

    private void openCreateWizard() {
        createWizardOpen = true;
        createSelectedSlot = -1;
        createSelectedFingerprint = "";
        createType = AuctionListingType.TIMED_AUCTION;
        createDurationSeconds = defaultCreateDurationSeconds();
        createStartBidBox = ensureOverlayBox(createStartBidBox, true, 20);
        createStartBidBox.setValue("");
        createBuyoutBox = ensureOverlayBox(createBuyoutBox, true, 20);
        createBuyoutBox.setValue("");
        bidEditorOpen = false;
        closePaymentPrompt();
    }

    private void closeCreateWizard() {
        createWizardOpen = false;
        createSelectedSlot = -1;
        createSelectedFingerprint = "";
    }

    private void renderCreateWizard(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        if (!createWizardOpen) {
            return;
        }
        MarketRectangle content = layout.content();
        if (content.width() < 40 || content.height() < 40) {
            return;
        }
        registerHit(content, () -> {
        });
        graphics.fill(content.x(), content.y(), content.right(),
                content.bottom(), SURFACE);
        border(graphics, content, theme.activeBorder());
        graphics.fill(content.x(), content.y(), content.right(),
                content.y() + 2, theme.accent());
        int padding = Math.max(6, layout.padding());
        int x = content.x() + padding;
        int y = content.y() + padding + 2;
        graphics.drawString(font,
                font.plainSubstrByWidth(Component.translatable(
                        "gui.futureshops.market.action.create.title")
                        .getString(), content.width() - padding * 2 - 20),
                x, y, theme.textStrong(), false);
        button(graphics, mouseX, mouseY,
                new MarketRectangle(content.right() - padding - 14,
                        content.y() + 4, 14, 12), "✕", true,
                this::closeCreateWizard);
        y += 12;
        int cell = content.width() >= 176 + padding * 2 ? 18 : 16;
        int gridWidth = cell * 9;
        var player = minecraft == null ? null : minecraft.player;
        if (player != null) {
            graphics.drawString(font,
                    font.plainSubstrByWidth(Component.translatable(
                            "gui.futureshops.market.action.create.select_item")
                            .getString(), content.width() - padding * 2),
                    x, y, theme.textMuted(), false);
            y += 11;
            for (int slot = 0; slot < PLAYER_MAIN_INVENTORY_SLOTS;
                 slot++) {
                int column = slot % 9;
                int row = slot / 9;
                MarketRectangle cellRect = new MarketRectangle(
                        x + column * cell, y + row * cell,
                        cell - 1, cell - 1);
                boolean selected = createSelectedSlot == slot;
                graphics.fill(cellRect.x(), cellRect.y(),
                        cellRect.right(), cellRect.bottom(),
                        selected ? theme.selectedSurface()
                                : SURFACE_RAISED);
                if (selected) {
                    border(graphics, cellRect, theme.accent());
                }
                ItemStack stack =
                        player.getInventory().items.get(slot);
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, cellRect.x() + 1,
                            cellRect.y() + 1);
                    graphics.renderItemDecorations(font, stack,
                            cellRect.x() + 1, cellRect.y() + 1);
                    int slotIndex = slot;
                    registerHit(cellRect,
                            () -> selectCreateSlot(slotIndex));
                }
            }
            y += cell * 4 + 4;
            ItemStack selectedStack = createSelectedSlot >= 0
                    && createSelectedSlot < PLAYER_MAIN_INVENTORY_SLOTS
                    ? player.getInventory().items.get(createSelectedSlot)
                    : ItemStack.EMPTY;
            String selectedText = selectedStack.isEmpty()
                    ? Component.translatable(
                    "gui.futureshops.market.action.create.none_selected")
                    .getString()
                    : Component.translatable(
                    "gui.futureshops.market.action.create.selected",
                    selectedStack.getHoverName().getString(),
                    selectedStack.getCount()).getString();
            graphics.drawString(font,
                    font.plainSubstrByWidth(selectedText,
                            content.width() - padding * 2),
                    x, y, selectedStack.isEmpty()
                            ? theme.semanticWarning()
                            : theme.textStrong(), false);
            y += 13;
        }
        int formWidth = Math.max(gridWidth,
                content.width() - padding * 2);
        int typeWidth = Math.max(40, (formWidth - 8) / 3);
        AuctionListingType[] types = AuctionListingType.values();
        for (int index = 0; index < types.length; index++) {
            AuctionListingType type = types[index];
            AuctionListingType target = type;
            button(graphics, mouseX, mouseY,
                    new MarketRectangle(x + index * (typeWidth + 4), y,
                            typeWidth, 14),
                    listingTypeLabel(type),
                    createType != type,
                    () -> selectCreateType(target));
            if (createType == type) {
                graphics.fill(x + index * (typeWidth + 4),
                        y + 14, x + index * (typeWidth + 4) + typeWidth,
                        y + 16, theme.accent());
            }
        }
        y += 20;
        createStartBidBox = ensureOverlayBox(createStartBidBox, true, 20);
        createBuyoutBox = ensureOverlayBox(createBuyoutBox, true, 20);
        if (createType != AuctionListingType.BUY_NOW) {
            String label = Component.translatable(
                    "gui.futureshops.market.action.create.start_bid")
                    .getString();
            graphics.drawString(font, label, x, y + 4,
                    theme.textMuted(), false);
            positionBox(createStartBidBox,
                    x + Math.min(96, font.width(label) + 8), y + 1, 64);
            createStartBidBox.render(graphics, mouseX, mouseY, 0.0F);
            y += 18;
        }
        if (createType != AuctionListingType.TIMED_AUCTION) {
            String label = Component.translatable(
                    "gui.futureshops.market.action.create.buyout")
                    .getString();
            graphics.drawString(font, label, x, y + 4,
                    theme.textMuted(), false);
            positionBox(createBuyoutBox,
                    x + Math.min(96, font.width(label) + 8), y + 1, 64);
            createBuyoutBox.render(graphics, mouseX, mouseY, 0.0F);
            y += 18;
        }
        String durationLabel = Component.translatable(
                "gui.futureshops.market.action.create.duration")
                .getString();
        graphics.drawString(font, durationLabel, x, y + 4,
                theme.textMuted(), false);
        int durationX = x + Math.min(96, font.width(durationLabel) + 8);
        if (createType == AuctionListingType.BUY_NOW) {
            button(graphics, mouseX, mouseY,
                    new MarketRectangle(durationX, y, 22, 14),
                    Component.translatable(
                            "gui.futureshops.market.action.create.duration.none")
                            .getString(),
                    createDurationSeconds != 0L,
                    () -> createDurationSeconds = 0L);
            if (createDurationSeconds == 0L) {
                graphics.fill(durationX, y + 14, durationX + 22,
                        y + 16, theme.accent());
            }
            durationX += 26;
        }
        int presetWidth = Math.max(20, Math.min(36,
                Math.max(1, content.right() - padding - durationX)
                        / createDurationPresets.size() - 4));
        for (int index = 0; index < createDurationPresets.size();
             index++) {
            long seconds = createDurationPresets.get(index);
            int presetX = durationX + index * (presetWidth + 4);
            button(graphics, mouseX, mouseY,
                    new MarketRectangle(presetX, y, presetWidth, 14),
                    formatDuration(seconds),
                    createDurationSeconds != seconds,
                    () -> createDurationSeconds = seconds);
            if (createDurationSeconds == seconds) {
                graphics.fill(presetX, y + 14,
                        presetX + presetWidth, y + 16,
                        theme.accent());
            }
        }
        y += 20;
        graphics.drawString(font,
                font.plainSubstrByWidth(Component.translatable(
                        "gui.futureshops.market.action.create.fee_notice")
                        .getString(), content.width() - padding * 2),
                x, y, theme.textMuted(), false);
        actionButton(graphics, mouseX, mouseY,
                new MarketRectangle(content.right() - padding - 88,
                        content.bottom() - padding - 16, 88, 16),
                Component.translatable(
                        "gui.futureshops.market.action.create.submit")
                        .getString(),
                true, false, "auction_create", "", true,
                this::submitCreateListing);
    }

    /**
     * Slot selection for the create wizard captures the stack fingerprint AT CLICK TIME
     * (plan §8 step 7): the send site reuses this value untouched, so a slot whose content
     * shifts before submit is rejected server-side instead of listing the wrong item.
     */
    private void selectCreateSlot(int slot) {
        var player = minecraft == null ? null : minecraft.player;
        if (player == null || slot < 0
                || slot >= PLAYER_MAIN_INVENTORY_SLOTS) {
            return;
        }
        ItemStack stack = player.getInventory().items.get(slot);
        if (stack.isEmpty()) {
            return;
        }
        createSelectedSlot = slot;
        createSelectedFingerprint =
                C2SAuctionCreatePacket.fingerprintOf(stack);
    }

    private void selectCreateType(AuctionListingType type) {
        createType = type;
        if (type != AuctionListingType.BUY_NOW
                && createDurationSeconds == 0L) {
            createDurationSeconds = defaultCreateDurationSeconds();
        }
    }

    private static String listingTypeLabel(AuctionListingType type) {
        String suffix = switch (type) {
            case BUY_NOW -> "buy_now";
            case TIMED_AUCTION -> "timed_auction";
            case AUCTION_WITH_BUYOUT -> "auction_with_buyout";
        };
        return Component.translatable(
                "gui.futureshops.market.action.create.type." + suffix)
                .getString();
    }

    private long defaultCreateDurationSeconds() {
        return createDurationPresets.contains(
                DEFAULT_CREATE_DURATION_SECONDS)
                ? DEFAULT_CREATE_DURATION_SECONDS
                : createDurationPresets.get(0);
    }

    private static String formatDuration(long seconds) {
        if (seconds % 86_400L == 0L) {
            return seconds / 86_400L + "d";
        }
        if (seconds % 3_600L == 0L) {
            return seconds / 3_600L + "h";
        }
        if (seconds % 60L == 0L) {
            return seconds / 60L + "m";
        }
        return seconds + "s";
    }

    private void submitCreateListing() {
        var player = minecraft == null ? null : minecraft.player;
        if (player == null) {
            return;
        }
        if (createSelectedSlot < 0
                || createSelectedSlot >= PLAYER_MAIN_INVENTORY_SLOTS
                || createSelectedFingerprint.isEmpty()) {
            showActionStatus(Component.translatable(
                    "gui.futureshops.market.action.create.invalid_item"),
                    false);
            return;
        }
        ItemStack stack =
                player.getInventory().items.get(createSelectedSlot);
        if (stack.isEmpty()) {
            showActionStatus(Component.translatable(
                    "gui.futureshops.market.action.create.invalid_item"),
                    false);
            return;
        }
        long startingBidMinor = 0L;
        if (createType != AuctionListingType.BUY_NOW) {
            startingBidMinor = parseMoneyBox(createStartBidBox);
            if (startingBidMinor <= 0L) {
                showActionStatus(Component.translatable(
                        "gui.futureshops.market.action.create.invalid_price"),
                        false);
                return;
            }
        }
        long buyoutMinor = 0L;
        if (createType != AuctionListingType.TIMED_AUCTION) {
            buyoutMinor = parseMoneyBox(createBuyoutBox);
            if (buyoutMinor <= 0L) {
                showActionStatus(Component.translatable(
                        "gui.futureshops.market.action.create.invalid_price"),
                        false);
                return;
            }
        }
        long durationSeconds = createType == AuctionListingType.BUY_NOW
                ? createDurationSeconds
                : createDurationSeconds > 0L ? createDurationSeconds
                : defaultCreateDurationSeconds();
        int slot = createSelectedSlot;
        String listingType = createType.name();
        long startMinor = startingBidMinor;
        long buyout = buyoutMinor;
        int quantity = stack.getCount();
        // Selection-time fingerprint, NOT recomputed here — see selectCreateSlot.
        String fingerprint = createSelectedFingerprint;
        requirePaymentSource(Component.translatable(
                        "gui.futureshops.market.action.payment.auction_listing_fee"),
                source -> sendMarketAction("auction_create", "",
                        requestId -> new C2SAuctionCreatePacket(requestId,
                                packet.routeNonce(), slot, listingType,
                                startMinor, buyout, durationSeconds, quantity,
                                fingerprint, source.wire()))
                        .ifPresent(requestId ->
                                trackPaymentSource(requestId, source)));
    }

    // ── Action-surface plumbing ─────────────────────────────────────

    private void showActionStatus(Component message, boolean success) {
        actionStatus = message;
        actionStatusSuccess = success;
        actionStatusAtMillis = Util.getMillis();
    }

    private void renderActionStatus(GuiGraphics graphics) {
        if (actionStatus == null || Util.getMillis()
                - actionStatusAtMillis > ACTION_STATUS_VISIBLE_MILLIS) {
            return;
        }
        MarketRectangle content = layout.content();
        if (content.width() < 24 || content.height() < 24) {
            return;
        }
        MarketRectangle strip = new MarketRectangle(content.x(),
                content.bottom() - 14, content.width(), 14);
        graphics.fill(strip.x(), strip.y(), strip.right(),
                strip.bottom(), SURFACE_RAISED);
        graphics.fill(strip.x(), strip.y(), strip.right(),
                strip.y() + 1, BORDER);
        graphics.drawString(font,
                font.plainSubstrByWidth(actionStatus.getString(),
                        Math.max(8, strip.width() - 12)),
                strip.x() + 6, strip.y() + 3,
                actionStatusSuccess ? theme.semanticSuccess()
                        : theme.semanticDanger(), false);
    }

    private boolean armed(String key) {
        return key.equals(armedConfirmKey)
                && Util.getMillis() - armedConfirmAtMillis
                <= ARMED_CONFIRM_WINDOW_MILLIS;
    }

    /** Destructive actions require a second click on the same control within the window. */
    private void armOrRun(String key, Runnable action) {
        if (armed(key)) {
            armedConfirmKey = "";
            action.run();
            return;
        }
        armedConfirmKey = key;
        armedConfirmAtMillis = Util.getMillis();
    }

    private static String busyLabel(String label, boolean busy) {
        return busy ? label + "…" : label;
    }

    /**
     * Action-surface control honoring the pending tracker. While the family+subject request
     * is in flight the control renders disabled with a busy label; once it TIMES OUT, the
     * retry-host control turns into an explicit [Retry] that resends the SAME request UUID
     * (the server replays the stored result idempotently) plus a ✕ give-up affordance that
     * abandons the entry together with a fresh refresh. A fresh request UUID for this
     * family+subject only becomes possible after that explicit give-up — re-enabling the
     * normal send while the original request may still be executing would let a retry mint
     * a second, economically distinct request. After a retry the same rules re-apply.
     */
    private void actionButton(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            MarketRectangle rectangle,
            String label,
            boolean enabled,
            boolean danger,
            String actionKey,
            String subjectId,
            boolean retryHost,
            Runnable send
    ) {
        Optional<MarketPendingActionTracker.PendingAction> timedOut =
                retryHost ? PENDING_ACTIONS.timedOut(actionKey, subjectId)
                        : Optional.empty();
        if (timedOut.isPresent()) {
            UUID requestId = timedOut.orElseThrow().requestId();
            int giveUpWidth = Math.min(16,
                    Math.max(10, rectangle.width() / 4));
            MarketRectangle retry = new MarketRectangle(rectangle.x(),
                    rectangle.y(),
                    Math.max(1, rectangle.width() - giveUpWidth - 2),
                    rectangle.height());
            MarketRectangle giveUp = new MarketRectangle(
                    rectangle.right() - giveUpWidth, rectangle.y(),
                    giveUpWidth, rectangle.height());
            button(graphics, mouseX, mouseY, retry,
                    Component.translatable(
                            "gui.futureshops.market.action.retry")
                            .getString(), true,
                    () -> retryPendingAction(requestId));
            dangerButton(graphics, mouseX, mouseY, giveUp, "✕", true,
                    () -> giveUpPendingAction(requestId));
            return;
        }
        boolean busy = PENDING_ACTIONS.busy(actionKey, subjectId);
        String rendered = busyLabel(label, busy);
        if (danger) {
            dangerButton(graphics, mouseX, mouseY, rectangle, rendered,
                    enabled && !busy, send);
        } else {
            button(graphics, mouseX, mouseY, rectangle, rendered,
                    enabled && !busy, send);
        }
    }

    /** Resends the ORIGINAL timed-out request — same UUID, fresh timeout window. */
    private void retryPendingAction(UUID requestId) {
        PENDING_ACTIONS.retry(requestId, Util.getMillis())
                .ifPresent(resend -> {
                    showActionStatus(Component.translatable(
                            "gui.futureshops.market.action.retry_sent"),
                            true);
                    resend.run();
                });
    }

    /**
     * Explicit give-up on a timed-out request: forgets the entry (fresh sends for its
     * family+subject become possible again) ONLY together with a fresh refresh, because the
     * abandoned request may have been applied server-side and only fresh state shows what
     * actually happened. On a detail route the refresh runs through the back-and-reopen
     * round trip like every other detail refresh.
     */
    private void giveUpPendingAction(UUID requestId) {
        if (!PENDING_ACTIONS.abandon(requestId)) {
            return;
        }
        PENDING_PAYMENT_SOURCES.remove(requestId);
        showActionStatus(Component.translatable(
                "gui.futureshops.market.action.gave_up"), false);
        if (isDetailView() && detailSelection != null
                && navigation.isOpen()
                && navigation.historyDepth() > 0) {
            pendingDetailRefresh = new DetailRefresh(module,
                    detailSelection.identity(), Util.getMillis());
            navigateBack();
            return;
        }
        refreshMarketState();
    }

    private void dangerButton(GuiGraphics graphics, int mouseX,
                              int mouseY, MarketRectangle rectangle,
                              String label, boolean enabled,
                              Runnable action) {
        boolean hover = enabled && rectangle.contains(mouseX, mouseY);
        graphics.fill(rectangle.x(), rectangle.y(), rectangle.right(),
                rectangle.bottom(), hover ? theme.selectedSurface()
                        : SURFACE_RAISED);
        border(graphics, rectangle, enabled
                ? theme.semanticDanger() : BORDER);
        graphics.drawCenteredString(font,
                font.plainSubstrByWidth(label,
                        Math.max(1, rectangle.width() - 4)),
                rectangle.x() + rectangle.width() / 2,
                rectangle.y() + Math.max(3,
                        (rectangle.height() - 8) / 2),
                enabled ? theme.semanticDanger() : theme.textMuted());
        if (enabled) {
            registerHit(rectangle, action);
        }
    }

    private EditBox ensureOverlayBox(
            EditBox existing,
            boolean decimal,
            int maxLength
    ) {
        if (existing != null) {
            return existing;
        }
        EditBox box = new EditBox(font, 0, 0, 50, 14, Component.empty());
        box.setMaxLength(maxLength);
        box.setFilter(decimal
                ? MarketModuleScreen::isDecimalText
                : MarketModuleScreen::isIntegerText);
        return box;
    }

    private static boolean isDecimalText(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if ((character < '0' || character > '9')
                    && character != '.') {
                return false;
            }
        }
        return true;
    }

    private static boolean isIntegerText(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }

    private static void positionBox(EditBox box, int x, int y,
                                    int width) {
        box.setX(x);
        box.setY(y);
        box.setWidth(width);
    }

    /** The overlay edit boxes that are live for the current route + overlay state. */
    private List<EditBox> activeOverlayBoxes() {
        List<EditBox> boxes = new ArrayList<>(4);
        if (paymentPromptAction != null) {
            return boxes;
        }
        if (createWizardOpen) {
            if (createStartBidBox != null
                    && createType != AuctionListingType.BUY_NOW) {
                boxes.add(createStartBidBox);
            }
            if (createBuyoutBox != null
                    && createType != AuctionListingType.TIMED_AUCTION) {
                boxes.add(createBuyoutBox);
            }
            return boxes;
        }
        if (isDetailView() && detailSelection != null) {
            MarketPageCard card = detailSelection.card();
            if (module == MarketModule.AUCTION_HOUSE
                    && card.kind() == MarketPageCardKind.AUCTION
                    && bidEditorOpen && bidAmountBox != null) {
                boxes.add(bidAmountBox);
            }
            if (module == MarketModule.BAZAAR
                    && card.kind() == MarketPageCardKind.BAZAAR_PRODUCT
                    && bazaarDetailHasActions(card)) {
                if (bazaarQuantityBox != null) {
                    boxes.add(bazaarQuantityBox);
                }
                if (bazaarLimitPriceBox != null) {
                    boxes.add(bazaarLimitPriceBox);
                }
            }
        }
        return boxes;
    }

    private EditBox focusedOverlayBox() {
        for (EditBox box : activeOverlayBoxes()) {
            if (box.isFocused()) {
                return box;
            }
        }
        return null;
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** Bazaar card identities are {@code productId@version} (see MarketPageProjector). */
    private static String bazaarProductId(String identity) {
        int separator = identity.lastIndexOf('@');
        if (separator <= 0 || separator >= identity.length() - 1) {
            return null;
        }
        return identity.substring(0, separator);
    }

    private static long bazaarProductVersion(String identity) {
        int separator = identity.lastIndexOf('@');
        if (separator <= 0 || separator >= identity.length() - 1) {
            return -1L;
        }
        try {
            return Long.parseLong(identity.substring(separator + 1));
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private static boolean orderCancellable(String state) {
        return "OPEN".equals(state) || "PARTIALLY_FILLED".equals(state);
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
        boolean showCreate = showCreateListingButton();
        int createWidth = showCreate
                ? Math.min(96, Math.max(56, footer.width() / 5)) : 0;
        if (showNavigation() && layout.categoryDrawer()) {
            int gap = 4;
            int reserved = createWidth > 0 ? createWidth + gap : 0;
            int buttonWidth = Math.max(54,
                    (footer.width() - layout.padding() * 2 - gap * 2
                            - reserved) / 3);
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
            String hint = Component.translatable(
                    "gui.futureshops.market.footer").getString();
            graphics.drawString(font,
                    font.plainSubstrByWidth(hint, Math.max(8,
                            footer.width() - layout.padding() * 2
                                    - createWidth - 8)),
                    footer.x() + layout.padding(), footer.y() + 8,
                    theme.textMuted(), false);
        }
        if (showCreate) {
            MarketRectangle create = new MarketRectangle(
                    footer.right() - layout.padding() - createWidth,
                    footer.y() + 4, createWidth,
                    Math.max(14, footer.height() - 8));
            // Retry host only while the wizard is closed — otherwise the wizard's own
            // submit button hosts the timed-out [Retry][✕] pair.
            actionButton(graphics, mouseX, mouseY, create,
                    "+ " + Component.translatable(
                            "gui.futureshops.market.action.create.button")
                            .getString(),
                    !createWizardOpen, false, "auction_create", "",
                    !createWizardOpen, this::openCreateWizard);
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
        String querySearch = normalizedSearch();
        UUID requestId = UUID.randomUUID();
        synchronizeRoute();
        navigation.beginResponseRequest(requestId,
                MarketResponseFamily.CONTENT);
        int pageSize = queryPageSize();
        C2SMarketPageQueryPacket query =
                new C2SMarketPageQueryPacket(requestId,
                        navigation.current().routeNonce(), module.id(),
                        packet.view(),
                        querySearch, selectedCategory, selectedSort,
                        requestedPage,
                        pageSize, OptionalLong.empty(),
                        OptionalLong.empty());
        sentSearch = querySearch;
        pageResult = "LOADING";
        page = null;
        ShopPackets.CHANNEL.sendToServer(query);
    }

    private String normalizedSearch() {
        return observedSearch.strip();
    }

    private void synchronizeRoute() {
        MarketRoute current = navigation.current();
        navigation.updateCurrent(new MarketRoute(module, packet.view(),
                selectedCategory, normalizedSearch(), current.filterId(),
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
            boolean boxConsumed = false;
            for (EditBox box : activeOverlayBoxes()) {
                boolean inside = box.mouseClicked(mouseX, mouseY, button);
                box.setFocused(inside);
                boxConsumed |= inside;
            }
            if (boxConsumed) {
                if (search != null) {
                    search.setFocused(false);
                }
                setFocused(null);
                return true;
            }
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
    public boolean charTyped(char character, int modifiers) {
        EditBox focused = focusedOverlayBox();
        if (focused != null && focused.charTyped(character, modifiers)) {
            return true;
        }
        return super.charTyped(character, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        EditBox focusedBox = focusedOverlayBox();
        if (focusedBox != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE
                    || keyCode == GLFW.GLFW_KEY_ENTER
                    || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                focusedBox.setFocused(false);
                return true;
            }
            if (focusedBox.keyPressed(keyCode, scanCode, modifiers)
                    || focusedBox.canConsumeInput()) {
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_F5) {
            refreshMarketState();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                && paymentPromptAction != null) {
            closePaymentPrompt();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && createWizardOpen) {
            closeCreateWizard();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && bidEditorOpen) {
            bidEditorOpen = false;
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
        // The market session is over: the server session (and its per-request replay
        // state) dies with it, so the cross-screen action bookkeeping resets too.
        PENDING_ACTIONS.clear();
        PENDING_PAYMENT_SOURCES.clear();
        DENIED_PAYMENT_SOURCES.clear();
        pendingDetailRefresh = null;
        actionStatus = null;
        profileRevision = 0L;
        profileReplayEpoch = 0L;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Hit(MarketRectangle rectangle, Runnable action) {
    }

    /** One-shot cross-screen marker for the detail back-and-reopen refresh round trip. */
    private record DetailRefresh(
            MarketModule module,
            String identity,
            long armedAtMillis
    ) {
    }
}
