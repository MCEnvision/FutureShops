package com.enviouse.futureshops.client;

import com.enviouse.futureshops.ClientConfig;
import com.enviouse.futureshops.Futureshops;
import com.enviouse.futureshops.client.market.MarketClientNavigationCoordinator;
import com.enviouse.futureshops.client.market.MarketCapabilityClientState;
import com.enviouse.futureshops.client.market.MarketCapabilityResponseTracker;
import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.client.market.MarketProfileMutationClientState;
import com.enviouse.futureshops.client.market.MarketProfileMutationResponseTracker;
import com.enviouse.futureshops.client.market.MarketRoute;
import com.enviouse.futureshops.client.market.MarketSessionPreferences;
import com.enviouse.futureshops.client.screen.BarterScreen;
import com.enviouse.futureshops.client.screen.CartScreen;
import com.enviouse.futureshops.client.screen.FranchiseManagementScreen;
import com.enviouse.futureshops.client.screen.ItemDetailScreen;
import com.enviouse.futureshops.client.screen.PlayerShopBarterScreen;
import com.enviouse.futureshops.client.screen.PlayerShopBlockScreen;
import com.enviouse.futureshops.client.screen.PlayerShopCartScreen;
import com.enviouse.futureshops.client.screen.PlayerStorefrontScreen;
import com.enviouse.futureshops.client.screen.BalTopOverviewScreen;
import com.enviouse.futureshops.client.screen.AtmScreen;
import com.enviouse.futureshops.client.screen.AdminItemPickerScreen;
import com.enviouse.futureshops.client.screen.AdminOfferEditorScreen;
import com.enviouse.futureshops.client.screen.BalanceOverviewScreen;
import com.enviouse.futureshops.client.screen.BulkSellConfirmationScreen;
import com.enviouse.futureshops.client.screen.BulkSellModeScreen;
import com.enviouse.futureshops.client.screen.ShopMainScreen;
import com.enviouse.futureshops.client.screen.MarketModuleScreen;
import com.enviouse.futureshops.client.screen.ShopScreenMarker;
import com.enviouse.futureshops.client.screen.ShopUiUtil;
import com.enviouse.futureshops.client.screen.TransactionHistoryScreen;
import com.enviouse.futureshops.network.packets.S2CAdminEditAckPacket;
import com.enviouse.futureshops.network.packets.S2CAdminOfferSaveResultPacket;
import com.enviouse.futureshops.network.packets.C2SAtmWithdrawPacket;
import com.enviouse.futureshops.network.packets.C2SAtmCollectCashPacket;
import com.enviouse.futureshops.network.packets.C2SAtmDepositPacket;
import com.enviouse.futureshops.network.packets.C2SAtmDepositRecoveryPacket;
import com.enviouse.futureshops.network.packets.C2SCloseMarketSessionPacket;
import com.enviouse.futureshops.network.packets.C2SMarketCapabilitiesPacket;
import com.enviouse.futureshops.network.packets.C2SMarketProfileMutationPacket;
import com.enviouse.futureshops.network.packets.C2SOpenMarketModulePacket;
import com.enviouse.futureshops.network.packets.C2SServerShopOfferPacket;
import com.enviouse.futureshops.network.packets.S2CAtmDataPacket;
import com.enviouse.futureshops.network.packets.S2CAtmResultPacket;
import com.enviouse.futureshops.network.packets.S2CAtmCollectCashResultPacket;
import com.enviouse.futureshops.network.packets.S2CAtmDepositResultPacket;
import com.enviouse.futureshops.network.packets.S2CBalTopUiPacket;
import com.enviouse.futureshops.network.packets.S2CBalanceUiPacket;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.S2CBarterResponsePacket;
import com.enviouse.futureshops.network.packets.S2CBulkSellQuotePacket;
import com.enviouse.futureshops.network.packets.S2CBulkSellResultPacket;
import com.enviouse.futureshops.network.packets.S2CBuyResponsePacket;
import com.enviouse.futureshops.network.packets.S2CForceClosePacket;
import com.enviouse.futureshops.network.packets.S2CFranchiseDataPacket;
import com.enviouse.futureshops.network.packets.S2CHistoryResponsePacket;
import com.enviouse.futureshops.network.packets.S2CInventorySyncPacket;
import com.enviouse.futureshops.network.packets.S2CLocalShopsPacket;
import com.enviouse.futureshops.network.packets.S2CMarketActionResponsePacket;
import com.enviouse.futureshops.network.packets.S2COpenMarketModulePacket;
import com.enviouse.futureshops.network.packets.S2CMarketPagePacket;
import com.enviouse.futureshops.network.packets.S2CMarketCapabilitiesPacket;
import com.enviouse.futureshops.network.packets.S2CMarketProfileMutationPacket;
import com.enviouse.futureshops.network.packets.S2CPlayerShopDataPacket;
import com.enviouse.futureshops.network.packets.S2CPlayerShopResultPacket;
import com.enviouse.futureshops.network.packets.S2CSettlementHistoryPacket;
import com.enviouse.futureshops.network.packets.S2CServerShopOfferResultPacket;
import com.enviouse.futureshops.network.packets.S2CServerShopOfferCartResultPacket;
import com.enviouse.futureshops.network.packets.S2CSellResponsePacket;
import com.enviouse.futureshops.network.packets.S2CShopDataPacket;
import com.enviouse.futureshops.network.packets.S2CVerifyCartResponsePacket;
import com.enviouse.futureshops.server.shop.ShopResultCode;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Futureshops.MODID, value = Dist.CLIENT)
public final class ShopClientPacketHandler {
    private static final AtmWithdrawalTracker ATM_WITHDRAWALS =
            new AtmWithdrawalTracker();
    private static final AtmCashClaimCollectionTracker ATM_CASH_COLLECTIONS =
            new AtmCashClaimCollectionTracker();
    private static final AtmDepositTracker ATM_DEPOSITS =
            new AtmDepositTracker();
    private static final PlayerShopResponseTracker PLAYER_SHOP_RESPONSES =
            new PlayerShopResponseTracker();
    private static final ServerShopOfferResponseTracker
            SERVER_SHOP_OFFER_RESPONSES =
            new ServerShopOfferResponseTracker();
    private static final com.enviouse.futureshops.client.editor
            .AdminOfferSaveResultTracker ADMIN_OFFER_SAVE_RESULTS =
            new com.enviouse.futureshops.client.editor
                    .AdminOfferSaveResultTracker();
    private static volatile S2CAtmResultPacket lastRetryableAtmResult;
    private static volatile S2CAtmDepositResultPacket
            lastRetryableAtmDepositResult;
    private static final LinkedHashSet<UUID> CANCELLED_MARKET_OPENS =
            new LinkedHashSet<>();
    private static final MarketSessionPreferences MARKET_PREFERENCES =
            MarketSessionPreferences.from(ClientConfig.settings());
    private static MarketClientNavigationCoordinator marketNavigation;

    private ShopClientPacketHandler() {
    }

    public static Optional<ServerShopOfferResponseTracker.PendingRequest>
    submitServerShopOffer(
            String shopId,
            String listingId,
            String optionId,
            com.enviouse.futureshops.catalog.offer.OfferAction action,
            int quantity,
            long revision,
            Optional<com.enviouse.futureshops.money.PaymentSource> source
    ) {
        try {
            C2SServerShopOfferPacket packet =
                    SERVER_SHOP_OFFER_RESPONSES.begin(
                            shopId, listingId, optionId, action,
                            quantity, revision, source);
            ShopPackets.CHANNEL.sendToServer(packet);
            return SERVER_SHOP_OFFER_RESPONSES.pending();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public static Optional<ServerShopOfferResponseTracker.PendingRequest>
    pendingServerShopOffer() {
        return SERVER_SHOP_OFFER_RESPONSES.pending();
    }

    public static PlayerShopResponseTracker.PendingRequest
    beginPlayerShopRequest(
            PlayerShopResponseTracker.Operation operation,
            int responseToken
    ) {
        return PLAYER_SHOP_RESPONSES.begin(operation, responseToken);
    }

    public static Optional<AtmWithdrawalTracker.PendingRequest>
    submitAtmWithdrawal(
            String currencySignature,
            List<Integer> denominationCounts,
            long amountMinor
    ) {
        try {
            AtmWithdrawalTracker.PendingRequest request =
                    ATM_WITHDRAWALS.begin(currencySignature,
                            denominationCounts, amountMinor);
            lastRetryableAtmResult = null;
            sendAtmWithdrawal(request);
            return Optional.of(request);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public static Optional<AtmWithdrawalTracker.PendingRequest>
    retryAtmWithdrawal() {
        try {
            AtmWithdrawalTracker.PendingRequest request =
                    ATM_WITHDRAWALS.retry();
            lastRetryableAtmResult = null;
            sendAtmWithdrawal(request);
            return Optional.of(request);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public static Optional<AtmWithdrawalTracker.PendingRequest>
    pendingAtmWithdrawal() {
        return ATM_WITHDRAWALS.pending();
    }

    public static AtmWithdrawalTracker.PendingState atmWithdrawalState() {
        return ATM_WITHDRAWALS.state();
    }

    public static Optional<S2CAtmResultPacket> lastRetryableAtmResult() {
        return Optional.ofNullable(lastRetryableAtmResult);
    }

    public static void clearAtmWithdrawalState() {
        ATM_WITHDRAWALS.clear();
        ATM_CASH_COLLECTIONS.clear();
        ATM_DEPOSITS.clear();
        lastRetryableAtmResult = null;
        lastRetryableAtmDepositResult = null;
    }

    public static Optional<AtmCashClaimCollectionTracker.PendingRequest>
    submitAtmCashCollection(List<java.util.UUID> claimIds) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return Optional.empty();
        }
        try {
            Optional<AtmCashClaimCollectionTracker.PendingRequest> request =
                    ATM_CASH_COLLECTIONS.beginIfFresh(
                            minecraft.player.getUUID(), claimIds);
            request.ifPresent(ShopClientPacketHandler::sendAtmCashCollection);
            return request;
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public static Optional<AtmCashClaimCollectionTracker.PendingRequest>
    retryAtmCashCollection() {
        try {
            AtmCashClaimCollectionTracker.PendingRequest request =
                    ATM_CASH_COLLECTIONS.retry();
            sendAtmCashCollection(request);
            return Optional.of(request);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public static Optional<AtmCashClaimCollectionTracker.PendingRequest>
    pendingAtmCashCollection() {
        return ATM_CASH_COLLECTIONS.pending();
    }

    public static AtmCashClaimCollectionTracker.PendingState
    atmCashCollectionState() {
        return ATM_CASH_COLLECTIONS.state();
    }

    public static Optional<AtmDepositTracker.PendingRequest>
    submitAtmDeposit(
            String currencySignature,
            C2SAtmDepositPacket.Source source,
            OptionalLong requestedMinorUnits
    ) {
        try {
            AtmDepositTracker.PendingRequest request = ATM_DEPOSITS.begin(
                    currencySignature, source, requestedMinorUnits);
            lastRetryableAtmDepositResult = null;
            sendAtmDeposit(request);
            return Optional.of(request);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public static Optional<AtmDepositTracker.PendingRequest>
    retryAtmDeposit() {
        try {
            AtmDepositTracker.PendingRequest request = ATM_DEPOSITS.retry();
            lastRetryableAtmDepositResult = null;
            sendAtmDeposit(request);
            return Optional.of(request);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public static Optional<AtmDepositTracker.PendingRequest>
    pendingAtmDeposit() {
        return ATM_DEPOSITS.pending();
    }

    public static AtmDepositTracker.PendingState atmDepositState() {
        return ATM_DEPOSITS.state();
    }

    public static Optional<S2CAtmDepositResultPacket>
    lastRetryableAtmDepositResult() {
        return Optional.ofNullable(lastRetryableAtmDepositResult);
    }

    private static void sendAtmDeposit(
            AtmDepositTracker.PendingRequest request
    ) {
        if (request.recoveryTransactionId().isPresent()) {
            ShopPackets.CHANNEL.sendToServer(
                    new C2SAtmDepositRecoveryPacket(
                            request.requestId(),
                            request.recoveryTransactionId()
                                    .orElseThrow()));
            return;
        }
        ShopPackets.CHANNEL.sendToServer(atmDepositPacket(request));
    }

    static C2SAtmDepositPacket atmDepositPacket(
            AtmDepositTracker.PendingRequest request
    ) {
        return new C2SAtmDepositPacket(request.requestId(),
                request.currencySignature(), request.source(),
                request.requestedMinorUnits());
    }

    private static void sendAtmCashCollection(
            AtmCashClaimCollectionTracker.PendingRequest request
    ) {
        ShopPackets.CHANNEL.sendToServer(atmCashCollectionPacket(request));
    }

    static C2SAtmCollectCashPacket atmCashCollectionPacket(
            AtmCashClaimCollectionTracker.PendingRequest request
    ) {
        return new C2SAtmCollectCashPacket(
                request.requestId(), request.claimIds());
    }

    private static void sendAtmWithdrawal(
            AtmWithdrawalTracker.PendingRequest request
    ) {
        ShopPackets.CHANNEL.sendToServer(atmPacket(request));
    }

    static C2SAtmWithdrawPacket atmPacket(
            AtmWithdrawalTracker.PendingRequest request
    ) {
        return new C2SAtmWithdrawPacket(
                request.requestId(), request.currencySignature(),
                request.denominationCounts());
    }

    static boolean acceptedAtmResult(
            AtmWithdrawalTracker.ResultDecision decision
    ) {
        return decision
                == AtmWithdrawalTracker.ResultDecision.ACCEPT_RETRYABLE
                || decision
                == AtmWithdrawalTracker.ResultDecision.ACCEPT_TERMINAL;
    }

    static boolean shouldApplyAtmBalance(
            AtmWithdrawalTracker.ResultDecision decision,
            boolean balanceKnown
    ) {
        return balanceKnown && acceptedAtmResult(decision);
    }

    public static void handleAtmData(S2CAtmDataPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            packet.depositRecovery().ifPresent(recovery -> {
                if (recovery.status().equals("RECOVERY_PENDING")) {
                    ATM_DEPOSITS.adoptRecovery(
                            recovery.requestId(),
                            packet.currencySignature(),
                            recovery.transactionId());
                } else if (recovery.status().equals("MANUAL_REVIEW")) {
                    ATM_DEPOSITS.adoptBlockedRecovery(
                            recovery.requestId(),
                            packet.currencySignature(),
                            recovery.transactionId());
                } else {
                    ATM_DEPOSITS.reconcileTerminalRecovery(
                            recovery.requestId(),
                            recovery.transactionId());
                }
                lastRetryableAtmDepositResult = null;
            });
            if (packet.serviceAvailable()
                    && packet.depositRecovery().isEmpty()
                    && ATM_DEPOSITS.reconcileNoRecovery()) {
                lastRetryableAtmDepositResult = null;
            }
            if (mc.screen instanceof AtmScreen atm) {
                atm.applyData(packet);
            } else {
                ClientRouteGuard.ResponseDecision decision =
                        ClientRouteGuard.acceptAtmResponse(mc.screen);
                if (!ClientRouteGuard.allowsAtmOpen(
                        decision, packet.openScreen(), mc.screen == null)) {
                    return;
                }
                mc.setScreen(new AtmScreen(mc.screen, packet));
            }
        });
    }

    public static void handleOpenMarket(
            S2COpenMarketModulePacket packet
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (consumeCancelledMarketOpen(packet.requestId())) {
                ShopPackets.CHANNEL.sendToServer(
                        new C2SCloseMarketSessionPacket(
                                packet.routeNonce(), false));
                return;
            }
            MarketModule module;
            try {
                module = MarketModule.fromId(packet.moduleId());
            } catch (IllegalArgumentException exception) {
                return;
            }
            MarketClientNavigationCoordinator coordinator =
                    activeMarketNavigation();
            if (coordinator != null) {
                if (!coordinator.isOpen()) {
                    return;
                }
                MarketClientNavigationCoordinator.OpenResponse response =
                        coordinator.acceptOpenResponse(packet.requestId(),
                                module, packet.view(), packet.routeNonce());
                if (response.decision()
                        != MarketClientNavigationCoordinator.OpenDecision
                        .ACCEPT) {
                    return;
                }
                if (minecraft.screen instanceof MarketModuleScreen current) {
                    current.prepareNavigationHandoff();
                }
                minecraft.setScreen(new MarketModuleScreen(packet,
                        coordinator));
                return;
            }
            MARKET_PREFERENCES.applySettings(ClientConfig.settings());
            boolean rememberedTab =
                    MARKET_PREFERENCES.hasRememberedTab(module);
            if (!rememberedTab) {
                MARKET_PREFERENCES.rememberTab(module, packet.view());
            }
            MarketSessionPreferences.ModulePreference preference =
                    MARKET_PREFERENCES.preference(module);
            String sort = preference.sortId().isEmpty()
                    ? defaultMarketSort(module) : preference.sortId();
            MarketRoute initial = new MarketRoute(module, packet.view(),
                    preference.categoryId(), "", preference.filterId(),
                    sort, 0, 0, "", packet.routeNonce());
            coordinator = new MarketClientNavigationCoordinator(initial,
                    64, 128, false, MARKET_PREFERENCES);
            setActiveMarketNavigation(coordinator);
            if (rememberedTab
                    && !preference.viewId().equals(packet.view())) {
                MarketClientNavigationCoordinator.OpenRequest request =
                        coordinator.beginTab(UUID.randomUUID(),
                                preference.viewId());
                ShopPackets.CHANNEL.sendToServer(
                        new C2SOpenMarketModulePacket(request.requestId(),
                                request.module().id(), request.viewId()));
                return;
            }
            minecraft.setScreen(new MarketModuleScreen(packet,
                    coordinator));
        });
    }

    public static synchronized MarketClientNavigationCoordinator
    activeMarketNavigation() {
        return marketNavigation;
    }

    public static synchronized void releaseMarketNavigation(
            MarketClientNavigationCoordinator coordinator
    ) {
        if (marketNavigation == coordinator) {
            marketNavigation = null;
        }
    }

    private static synchronized void setActiveMarketNavigation(
            MarketClientNavigationCoordinator coordinator
    ) {
        marketNavigation = coordinator;
    }

    private static String defaultMarketSort(MarketModule module) {
        return module == MarketModule.BAZAAR ? "name" : "ending_soon";
    }

    public static synchronized void cancelMarketOpen(UUID requestId) {
        if (requestId == null) {
            return;
        }
        CANCELLED_MARKET_OPENS.add(requestId);
        while (CANCELLED_MARKET_OPENS.size() > 128) {
            var iterator = CANCELLED_MARKET_OPENS.iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private static synchronized boolean consumeCancelledMarketOpen(
            UUID requestId
    ) {
        return CANCELLED_MARKET_OPENS.remove(requestId);
    }

    public static void handleMarketPage(S2CMarketPagePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof MarketModuleScreen market) {
                market.applyPage(packet);
            }
        });
    }

    /**
     * Result of an Auction House / Bazaar mutation (plan §12). On the main thread the open
     * market screen clears its pending request, localizes the status, and refreshes the page
     * on success or on the stale-revision family (plan §15: stale interfaces refresh rather
     * than execute). Without an open market screen the response is dropped: there is no
     * market-wide status surface outside the screen, and durable outcomes (claims, wallet
     * movement, page contents) all arrive through their own packets.
     */
    public static void handleMarketActionResponse(
            S2CMarketActionResponsePacket packet
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof MarketModuleScreen market) {
                market.applyActionResponse(packet);
            }
        });
    }

    public static UUID requestMarketCapabilities() {
        UUID requestId = MarketCapabilityClientState.beginRequest();
        ShopPackets.CHANNEL.sendToServer(
                marketCapabilitiesPacket(requestId));
        return requestId;
    }

    static C2SMarketCapabilitiesPacket marketCapabilitiesPacket(
            UUID requestId
    ) {
        return new C2SMarketCapabilitiesPacket(requestId);
    }

    public static void handleMarketCapabilities(
            S2CMarketCapabilitiesPacket packet
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            MarketCapabilityResponseTracker.Decision decision =
                    MarketCapabilityClientState.accept(
                            packet.snapshot());
            if (decision != MarketCapabilityResponseTracker.Decision
                    .ACCEPT) {
                return;
            }
            ShopClientState.applyMarketWalletSnapshot(
                    packet.snapshot().walletBalanceMinorUnits(),
                    packet.snapshot().walletBalanceKnown(),
                    packet.snapshot().currencyName(),
                    packet.snapshot().currencyDecimals());
            if (minecraft.screen instanceof MarketModuleScreen market) {
                market.applyCapabilities(packet.snapshot());
            }
        });
    }

    public static boolean submitMarketProfileMutation(
            C2SMarketProfileMutationPacket packet
    ) {
        try {
            MarketProfileMutationClientState.begin(packet.command());
            ShopPackets.CHANNEL.sendToServer(packet);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static void handleMarketProfileMutation(
            S2CMarketProfileMutationPacket packet
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            MarketProfileMutationResponseTracker.Decision decision =
                    MarketProfileMutationClientState.accept(
                            packet.result());
            if (decision
                    != MarketProfileMutationResponseTracker.Decision
                    .ACCEPT) {
                return;
            }
            if (minecraft.screen instanceof MarketModuleScreen market) {
                market.applyProfileMutationResult(packet.result());
            }
        });
    }

    public static void handleAtmResult(S2CAtmResultPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            AtmWithdrawalTracker.ResultDecision decision =
                    ATM_WITHDRAWALS.evaluateResult(
                            packet.requestId(), packet.currencySignature(),
                            packet.retryable(), packet.retryAfterMillis(),
                            packet.deduplicationKey());
            if (!acceptedAtmResult(decision)) {
                return;
            }
            lastRetryableAtmResult = decision
                    == AtmWithdrawalTracker.ResultDecision.ACCEPT_RETRYABLE
                    ? packet : null;
            if (shouldApplyAtmBalance(decision, packet.balanceKnown())) {
                ShopClientState.setCurrentBalanceMinorUnits(
                        packet.balanceMinor());
            }
            if (mc.screen instanceof AtmScreen atm) {
                atm.applyResult(packet, decision);
            }
        });
    }

    public static void handleAtmCashCollectionResult(
            S2CAtmCollectCashResultPacket packet
    ) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            Optional<AtmCashClaimCollectionTracker.PendingRequest>
                    pendingBeforeResult = ATM_CASH_COLLECTIONS.pending();
            AtmCashClaimCollectionTracker.ResultDecision decision =
                    ATM_CASH_COLLECTIONS.evaluateResult(packet.requestId(),
                            packet.retryable(), packet.retryAfterMillis(),
                            packet.deduplicationKey());
            if (decision
                    != AtmCashClaimCollectionTracker.ResultDecision
                    .ACCEPT_RETRYABLE
                    && decision
                    != AtmCashClaimCollectionTracker.ResultDecision
                    .ACCEPT_TERMINAL) {
                return;
            }
            if (mc.screen instanceof AtmScreen atm) {
                List<java.util.UUID> submittedClaimIds =
                        pendingBeforeResult
                                .filter(request -> request.requestId().equals(
                                        packet.requestId()))
                                .map(AtmCashClaimCollectionTracker
                                        .PendingRequest::claimIds)
                                .orElse(List.of());
                atm.applyCashCollectionResult(
                        packet, decision, submittedClaimIds);
            }
        });
    }

    public static void handleAtmDepositResult(
            S2CAtmDepositResultPacket packet
    ) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            AtmDepositTracker.ResultDecision decision =
                    ATM_DEPOSITS.evaluateResult(packet.requestId(),
                            packet.retryable(), packet.retryAfterMillis(),
                            packet.deduplicationKey());
            if (decision != AtmDepositTracker.ResultDecision.ACCEPT_RETRYABLE
                    && decision
                    != AtmDepositTracker.ResultDecision.ACCEPT_TERMINAL) {
                return;
            }
            lastRetryableAtmDepositResult = decision
                    == AtmDepositTracker.ResultDecision.ACCEPT_RETRYABLE
                    ? packet : null;
            if (packet.balanceKnown()) {
                ShopClientState.setCurrentBalanceMinorUnits(
                        packet.resultingBalanceMinorUnits());
            }
            if (mc.screen instanceof AtmScreen atm) {
                atm.applyDepositResult(packet, decision);
            }
        });
    }

    @SubscribeEvent
    public static void onClientLoggingOut(
            ClientPlayerNetworkEvent.LoggingOut ignoredEvent
    ) {
        clearAtmWithdrawalState();
        ClientRouteGuard.clear();
        PLAYER_SHOP_RESPONSES.clear();
        SERVER_SHOP_OFFER_RESPONSES.clear();
        MarketCapabilityClientState.clear();
        MarketProfileMutationClientState.clear();
        ShopClientState.clearMarketWalletSnapshot();
        MarketClientNavigationCoordinator coordinator =
                activeMarketNavigation();
        if (coordinator != null) {
            coordinator.close();
            releaseMarketNavigation(coordinator);
        }
        MARKET_PREFERENCES.reset();
        synchronized (ShopClientPacketHandler.class) {
            CANCELLED_MARKET_OPENS.clear();
        }
    }

    /**
     * Applies the full shop catalog + balance state received from the server.
     *
     * <p>When {@code packet.forceOpen()} is true (the explicit open path), the GUI is opened if
     * it is not already showing. When false (stock refresh, post-transaction sync, admin reload),
     * the packet only updates an already-open shop-flow screen and is otherwise discarded — so a
     * player who closed the shop but still has a live server-side session does not get the GUI
     * forced back open by background server activity.
     */
    public static void handleShopData(S2CShopDataPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            boolean shopMainOpen = mc.screen instanceof ShopMainScreen;
            boolean shopFlowOpen = mc.screen instanceof ShopScreenMarker;
            if (!packet.forceOpen() && !shopFlowOpen) {
                // Silent refresh and the player isn't viewing the shop — nothing to do.
                return;
            }

            ShopClientState.applyShopData(
                    packet.shopId(),
                    packet.balanceMinorUnits(),
                    packet.currencyName(),
                    packet.currencyDecimals(),
                    packet.categories(),
                    packet.items(),
                    packet.promos(),
                    packet.barterRecipes(),
                    packet.adminShopEnabled(),
                    packet.nearbyShops(),
                    packet.canEdit(),
                    packet.offers());
            ShopPackets.CHANNEL.sendToServer(new com.enviouse.futureshops.network.packets.C2SInventorySyncPacket(packet.shopId()));
            if (shopMainOpen) {
                // Update in-place — preserves nearbyMode, scroll, tabs.
                ((ShopMainScreen) mc.screen).refreshAfterDataUpdate();
            } else if (packet.forceOpen()) {
                mc.setScreen(new ShopMainScreen());
            }
        });
    }

    public static void handleInventorySync(S2CInventorySyncPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (!packet.shopId().equals(ShopClientState.getActiveShopId())) {
                return;
            }
            ShopClientState.applyOwnedCounts(packet.itemCounts());
        });
    }

    public static void handleBalanceUi(S2CBalanceUiPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            // Resolve the parent to the SHOP ROOT, never a sibling overview — otherwise a sibling
            // (Leaders/Franchise) re-opening this dashboard via a packet would set its parent to
            // that sibling, and Back would ping-pong Profile↔Leaders forever (only Close escaped).
            net.minecraft.client.gui.screens.Screen current = mc.screen;
            net.minecraft.client.gui.screens.Screen parent =
                    current instanceof BalanceOverviewScreen b ? b.getParent()
                    : (current instanceof BalTopOverviewScreen || current instanceof FranchiseManagementScreen)
                            ? new ShopMainScreen()
                    : current;
            if (current instanceof MarketModuleScreen market) {
                market.prepareNavigationHandoff();
            }
            mc.setScreen(new BalanceOverviewScreen(
                parent,
                packet.playerUuid(),
                packet.playerName(),
                packet.balanceMinorUnits(),
                packet.currencyName(),
                packet.currencyDecimals(),
                packet.totalRevenueMinor(),
                packet.pendingSettlementMinor(),
                packet.shopCount(),
                packet.listingCount(),
                packet.totalStock(),
                packet.lowSupplyCount(),
                packet.shopSummaries(),
                packet.alerts()));
        });
    }

    public static void handleBalTopUi(S2CBalTopUiPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof BalTopOverviewScreen screen) {
                screen.updatePage(
                        packet.page(),
                        packet.totalPages(),
                        packet.entries(),
                        packet.activityLeaderUuid(),
                        packet.activityLeaderName(),
                        packet.activityLeaderCount(),
                        packet.topSellerUuid(),
                        packet.topSellerName(),
                        packet.topSellerCount(),
                        packet.popularItemId(),
                        packet.popularItemTrades(),
                        packet.popularItemQuantity(),
                        packet.popularItemNbtJson(),
                        packet.franchises());
                return;
            }
            mc.setScreen(new BalTopOverviewScreen(
                    packet.page(),
                    packet.totalPages(),
                    packet.entries(),
                    packet.currencyName(),
                    packet.currencyDecimals(),
                    packet.activityLeaderUuid(),
                    packet.activityLeaderName(),
                    packet.activityLeaderCount(),
                    packet.topSellerUuid(),
                    packet.topSellerName(),
                    packet.topSellerCount(),
                    packet.popularItemId(),
                    packet.popularItemTrades(),
                    packet.popularItemQuantity(),
                    packet.popularItemNbtJson(),
                    packet.franchises()));
        });
    }

    public static void handleHistoryResponse(S2CHistoryResponsePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (!packet.shopId().equals(ShopClientState.getActiveShopId())) {
                return;
            }
            if (mc.screen instanceof TransactionHistoryScreen historyScreen) {
                if (!historyScreen.applyHistoryResponse(packet.page(), packet.totalPages(), packet.filter())) {
                    return;
                }
            }
            ShopClientState.applyHistoryPage(packet.entries());
        });
    }

    public static void handlePlayerShopData(S2CPlayerShopDataPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (PlayerShopCartState.hasTrackedCheckout()) {
                return;
            }
            boolean storefrontOpen = mc.screen instanceof PlayerStorefrontScreen;
            boolean storefrontChild = mc.screen instanceof com.enviouse.futureshops.client.screen.PlayerShopBarterScreen
                    || mc.screen instanceof com.enviouse.futureshops.client.screen.PlayerShopSellScreen;
            if (!packet.owner()) {
                if (storefrontOpen || storefrontChild) {
                    if (!packet.shopPos().equals(PlayerShopClientState.shopPos())) {
                        return;
                    }
                } else {
                    ClientRouteGuard.ResponseDecision decision =
                            ClientRouteGuard.acceptStorefrontResponse(
                                    mc.screen, packet.shopPos().asLong());
                    if (decision == ClientRouteGuard.ResponseDecision.REJECT) {
                        return;
                    }
                }
            }
            PlayerShopClientState.apply(
                    packet.shopPos(),
                    packet.owner(),
                    packet.ownerUuid(),
                    packet.ownerName(),
                    packet.listings(),
                    packet.linked(),
                    packet.pendingSettlementMinor(),
                    packet.lifetimeRevenueMinor(),
                    packet.recentRevenueRows(),
                    packet.shopName(),
                    packet.singleItemMode(),
                    packet.barterStorageSame(),
                    packet.description(),
                    packet.franchiseName(),
                    packet.placedByCreative(),
                    packet.adminShopMode(),
                    packet.floatingIconMode(),
                    packet.floatingIconItem(),
                    packet.linkedStorages(),
                    packet.savedConfigNames(),
                    packet.normalizedOffers());
            if (packet.owner()) {
                // Owner MANAGE view is unchanged (Phase 5 rebuild). Data resends (buy/config acks)
                // update an already-open block screen in place; it reads live client state.
                if (!(mc.screen instanceof PlayerShopBlockScreen)
                        && !(mc.screen
                        instanceof AdminOfferEditorScreen editor
                        && editor.isPlayerShopEditor())) {
                    // Pass current screen as parent for back-button navigation (Items 4, 9)
                    mc.setScreen(new PlayerShopBlockScreen(mc.screen));
                }
            } else {
                // Visitor STOREFRONT view. A resend while the storefront is already open (e.g. a
                // buy ack re-sending the shop payload) refreshes it in place — preserving the
                // browse/detail state and any open confirmation modal — instead of popping a fresh
                // screen over it.
                if (mc.screen instanceof PlayerStorefrontScreen storefront) {
                    storefront.refreshAfterDataUpdate();
                } else if (mc.screen instanceof com.enviouse.futureshops.client.screen.PlayerShopBarterScreen
                        || mc.screen instanceof com.enviouse.futureshops.client.screen.PlayerShopSellScreen) {
                    // A barter / sell-to-shop just completed and re-sent the payload. That child
                    // screen will return to its parent storefront (which reads live client state)
                    // when the player backs out — don't stack a fresh storefront over it.
                } else {
                    mc.setScreen(new PlayerStorefrontScreen(mc.screen));
                }
            }
        });
    }

    public static void handlePlayerShopResult(S2CPlayerShopResultPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            PlayerShopClientState.setResultCode(packet.code());

            // Update ConfirmationModal on open screens
            boolean isSuccess = packet.success();
            // chatMessage is legacy/optional (the server now sends localized chat via
            // sendSystemMessage); when absent, localize the result CODE on the client
            // instead of showing the raw wire code (e.g. "INSUFFICIENT_FUNDS").
            String msg = packet.chatMessage() != null && !packet.chatMessage().isBlank()
                    ? packet.chatMessage()
                    : Component.translatable("gui.futureshops.player_shop.result."
                            + packet.code().toLowerCase(java.util.Locale.ROOT)).getString();
            boolean cartResponse = !CartResponsePolicy.UNCORRELATED_REQUEST_ID.equals(packet.requestId());
            if (cartResponse) {
                CartResponsePolicy.ResponseResult result =
                        PlayerShopCartState.applyCheckoutResponse(
                                packet.requestId(), packet.responseToken(), packet.success());
                if (result.matched()) {
                    if (result.checkoutComplete() && mc.screen instanceof PlayerShopCartScreen cartScreen) {
                        String completionMessage = result.checkoutSuccessful()
                                ? Component.translatable(
                                        "gui.futureshops.player_shop_cart.checkout_success").getString()
                                : Component.translatable(
                                        "gui.futureshops.player_shop_cart.checkout_partial_failure").getString();
                        cartScreen.onTransactionResult(result.checkoutSuccessful(), completionMessage);
                    }
                    return;
                }
                if (PLAYER_SHOP_RESPONSES.consume(packet.requestId(),
                        packet.responseToken())
                        != PlayerShopResponseTracker.Match.MATCHED) {
                    return;
                }
            }
            if (mc.screen instanceof PlayerShopBlockScreen psScreen) {
                psScreen.onTransactionResult(isSuccess, msg);
            } else if (mc.screen instanceof PlayerShopBarterScreen barterScreen) {
                barterScreen.onTransactionResult(isSuccess, msg);
            } else if (mc.screen instanceof PlayerStorefrontScreen storefront) {
                storefront.onTransactionResult(isSuccess, msg);
            }

            // Item 18: On barter/storage failure, close the UI and show a chat message
            String code = packet.code();
            if (!packet.success() && ("STORAGE_FULL".equals(code) || "ROLLBACK".equals(code) || "MISSING_BARTER_ITEMS".equals(code))) {
                if (mc.screen instanceof ShopScreenMarker) {
                    mc.setScreen(null);
                }
                if (mc.player != null && packet.chatMessage() != null && !packet.chatMessage().isBlank()) {
                    mc.player.sendSystemMessage(Component.literal(packet.chatMessage()));
                }
            }
        });
    }

    public static void handleSettlementHistory(S2CSettlementHistoryPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (!packet.shopPos().equals(PlayerShopClientState.shopPos())) {
                return;
            }
            PlayerShopClientState.applySettlementHistory(packet.page(), packet.totalPages(), packet.rows());
        });
    }

    public static void handleBuyResponse(S2CBuyResponsePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            CartResponsePolicy.ResponseResult cartResult = null;
            if (packet.cartCheckout()) {
                cartResult = ShopClientState.applyCartCheckoutResponse(
                        packet.requestId(), packet.success());
                if (!cartResult.matched()) {
                    return;
                }
            }
            ShopClientState.setCurrentBalanceMinorUnits(packet.resultingBalanceMinorUnits());
            ShopClientState.setStatus(buildBuyMessage(packet), packet.success());

            if (packet.success()) {
                // Refresh owned counts so the Sell button lights up immediately after buying,
                // without the player having to close and reopen the item.
                ShopPackets.CHANNEL.sendToServer(new com.enviouse.futureshops.network.packets.C2SInventorySyncPacket(
                        ShopClientState.getActiveShopId()));
            }
            // Update ConfirmationModal if ItemDetailScreen is open
            if (mc.screen instanceof ItemDetailScreen detailScreen) {
                detailScreen.onTransactionResult(packet.success(), buildBuyMessage(packet).getString());
            } else if (mc.screen instanceof CartScreen cartScreen) {
                cartScreen.onTransactionResult(packet.success(), buildBuyMessage(packet).getString());
            }
        });
    }

    public static void handleSellResponse(S2CSellResponsePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            ShopClientState.setCurrentBalanceMinorUnits(packet.resultingBalanceMinorUnits());
            ShopClientState.setStatus(buildSellMessage(packet), packet.success());

            if (packet.success()) {
                ShopPackets.CHANNEL.sendToServer(new com.enviouse.futureshops.network.packets.C2SInventorySyncPacket(
                        ShopClientState.getActiveShopId()));
            }

            // Update ConfirmationModal if ItemDetailScreen is open
            if (mc.screen instanceof ItemDetailScreen detailScreen) {
                detailScreen.onSellTransactionResult(packet.requestId(),
                        packet.success(), buildSellMessage(packet).getString());
            }
        });
    }

    public static void handleServerShopOfferResult(
            S2CServerShopOfferResultPacket packet
    ) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (!SERVER_SHOP_OFFER_RESPONSES.accept(packet)) {
                return;
            }
            ShopClientState.setCurrentBalanceMinorUnits(
                    packet.resultingBalanceMinorUnits());
            boolean success = packet.status().success();
            Component message = Component.translatable(
                    "gui.futureshops.offer.result."
                            + packet.status().name()
                            .toLowerCase(java.util.Locale.ROOT));
            ShopClientState.setStatus(message, success);
            if (mc.screen instanceof ItemDetailScreen detailScreen) {
                detailScreen.onOfferTransactionResult(
                        packet.requestId(), success,
                        message.getString(), packet.status());
            }
        });
    }

    public static void handleServerShopOfferCartResult(
            S2CServerShopOfferCartResultPacket packet
    ) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (!ShopClientState.hasTrackedCartCheckout()) {
                return;
            }
            CartResponsePolicy.ResponseResult response =
                    ShopClientState.applyCartCheckoutResponse(
                            packet.requestId(),
                            packet.status().success(),
                            packet.status()
                                    != com.enviouse.futureshops.server
                                    .escrow.runtime.ServerShopOfferService
                                    .Status.RECOVERY_REQUIRED
                                    && packet.status()
                                    != com.enviouse.futureshops.server
                                    .escrow.runtime.ServerShopOfferService
                                    .Status.QUARANTINED);
            if (!response.matched()) {
                return;
            }
            ShopClientState.setCurrentBalanceMinorUnits(
                    packet.resultingBalanceMinorUnits());
            Component message = Component.translatable(
                    "gui.futureshops.offer.result."
                            + packet.status().name()
                            .toLowerCase(java.util.Locale.ROOT));
            ShopClientState.setStatus(
                    message, packet.status().success());
            if (mc.screen instanceof CartScreen cartScreen) {
                cartScreen.onTransactionResult(
                        packet.status().success(),
                        message.getString());
            }
        });
    }

    public static void handleAdminOfferSaveResult(
            S2CAdminOfferSaveResultPacket packet
    ) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof AdminOfferEditorScreen editor) {
                editor.applySaveResult(packet);
            } else if (mc.screen
                    instanceof AdminItemPickerScreen picker) {
                picker.applySaveResult(packet);
            } else {
                ADMIN_OFFER_SAVE_RESULTS.record(packet);
            }
        });
    }

    public static void handlePlayerShopOfferSaveResult(
            com.enviouse.futureshops.network.packets
                    .S2CPlayerShopOfferSaveResultPacket packet
    ) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof AdminOfferEditorScreen editor
                    && editor.isPlayerShopEditor()) {
                editor.applyPlayerShopSaveResult(packet);
            } else {
                ADMIN_OFFER_SAVE_RESULTS.record(
                        packet.asAdminResult());
            }
        });
    }

    public static Optional<S2CAdminOfferSaveResultPacket>
    takeAdminOfferSaveResult(UUID requestId) {
        return ADMIN_OFFER_SAVE_RESULTS.take(requestId);
    }

    public static void handleBarterResponse(S2CBarterResponsePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            ShopClientState.setStatus(buildBarterMessage(packet), packet.success());

            // Update ConfirmationModal if BarterScreen is open
            if (mc.screen instanceof BarterScreen barterScreen) {
                barterScreen.onTransactionResult(packet.requestId(),
                        packet.success(), buildBarterMessage(packet).getString());
            }
        });
    }

    /**
     * Handles a server-initiated force-close.
     * Resets client state and closes any open shop screen.
     */
    public static void handleForceClose(S2CForceClosePacket ignoredPacket) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            clearAtmWithdrawalState();
            ClientRouteGuard.clear();
            MarketCapabilityClientState.clear();
            MarketProfileMutationClientState.clear();
            MarketClientNavigationCoordinator coordinator =
                    activeMarketNavigation();
            if (coordinator != null) {
                coordinator.close();
                releaseMarketNavigation(coordinator);
            }
            ShopClientState.reset();
            PlayerShopCartState.clear(); // Item 34: Clear player shop cart on disconnect
            if (mc.screen instanceof ShopScreenMarker) {
                mc.setScreen(null);
            }
        });
    }

    /**
     * Handles aggregated local shop data for the franchise/owner browsing UI.
     */
    public static void handleLocalShops(S2CLocalShopsPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            ShopClientState.applyLocalShops(packet.owners());
            if (mc.screen instanceof ShopMainScreen existing) {
                existing.refreshAfterDataUpdate();
            }
        });
    }

    /**
     * Handles franchise data — opens or updates the FranchiseManagementScreen.
     */
    public static void handleFranchiseData(S2CFranchiseDataPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof FranchiseManagementScreen screen) {
                screen.updateData(packet.inFranchise(), packet.franchiseId(), packet.franchiseName(),
                        packet.isLeader(), packet.members(), packet.hasPendingInvite(), packet.pendingFranchiseName());
            } else {
                mc.setScreen(new FranchiseManagementScreen(
                        packet.inFranchise(), packet.franchiseId(), packet.franchiseName(),
                        packet.isLeader(), packet.members(), packet.hasPendingInvite(), packet.pendingFranchiseName()));
            }
        });
    }

    /**
     * Handles cart verification response — stores warnings in client state
     * for the cart screen to display.
     */
    public static void handleCartVerification(S2CVerifyCartResponsePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> ShopClientState.applyCartVerification(packet.allOk(), packet.warnings()));
    }

    /**
     * Handles the ack for an in-GUI admin shop edit — localizes the string code and shows it in
     * the status strip (the shop screen renders {@link ShopClientState#getStatus()} every frame,
     * so no screen plumbing is needed). {@code arg} feeds the key's %s slot (e.g. ADDED count).
     */
    public static void handleAdminEditAck(S2CAdminEditAckPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            // A freshly-created barter target opens its ingredient editor directly, keyed by the
            // new listingId the server returned in `arg` (the OP then adds ingredients one at a time).
            if (packet.success() && "BARTER_TARGET_CREATED".equals(packet.code()) && !packet.arg().isBlank()) {
                net.minecraft.client.gui.screens.Screen back = mc.screen;
                mc.setScreen(new com.enviouse.futureshops.client.screen.BarterRecipeEditorScreen(back, packet.arg()));
                ShopClientState.setStatus(
                        Component.translatable("gui.futureshops.admin_edit.result.barter_target_created"), true);
                return;
            }
            if (packet.success() && "BARTER_TARGETS_CREATED".equals(packet.code()) && !packet.arg().isBlank()) {
                java.util.List<String> listingIds = java.util.Arrays.stream(packet.arg().split(","))
                        .map(String::trim)
                        .filter(id -> !id.isBlank())
                        .toList();
                if (!listingIds.isEmpty()) {
                    net.minecraft.client.gui.screens.Screen back = mc.screen;
                    mc.setScreen(new com.enviouse.futureshops.client.screen.BarterRecipeEditorScreen(back, listingIds));
                    ShopClientState.setStatus(
                            Component.translatable("gui.futureshops.admin_edit.result.barter_targets_created",
                                    listingIds.size()), true);
                    return;
                }
            }
            ShopClientState.setStatus(
                    Component.translatable(
                            "gui.futureshops.admin_edit.result." + packet.code().toLowerCase(java.util.Locale.ROOT),
                            packet.arg()),
                    packet.success());
        });
    }

    public static void handleBulkSellQuote(
            S2CBulkSellQuotePacket packet
    ) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (packet.quote() == null) {
                if (client.screen
                        instanceof BulkSellModeScreen mode) {
                    mode.onQuoteRejected();
                } else if (client.screen
                        instanceof BulkSellConfirmationScreen screen) {
                    screen.onQuoteRejected();
                }
                Component message = Component.translatable(
                        "gui.futureshops.bulk_sell.result."
                                + packet.status().name()
                                .toLowerCase(java.util.Locale.ROOT),
                        0, 0, 0, "0");
                ShopClientState.setStatus(message, false);
                if (client.player != null) {
                    client.player.displayClientMessage(
                            message, false);
                }
                return;
            }
            net.minecraft.client.gui.screens.Screen parent =
                    client.screen instanceof BulkSellModeScreen mode
                            ? mode.returnScreen()
                            : client.screen
                            instanceof BulkSellConfirmationScreen screen
                            ? screen.returnScreen()
                            : client.screen;
            client.setScreen(new BulkSellConfirmationScreen(
                    parent, packet.quote()));
        });
    }

    public static void handleBulkSellResult(
            S2CBulkSellResultPacket packet
    ) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            String paid = java.math.BigDecimal.valueOf(
                            packet.paidMinorUnits(),
                            packet.currencyDecimals())
                    .toPlainString() + " " + packet.currencyName();
            Component message = Component.translatable(
                    "gui.futureshops.bulk_sell.result."
                            + packet.status().name()
                            .toLowerCase(java.util.Locale.ROOT),
                    packet.soldLines(), packet.failedLines(),
                    packet.recoveryLines(), paid);
            boolean success = packet.status()
                    == com.enviouse.futureshops.server.shop
                    .BulkSellService.Status.SUCCESS
                    || packet.status()
                    == com.enviouse.futureshops.server.shop
                    .BulkSellService.Status.PARTIAL;
            ShopClientState.setStatus(message, success);
            if (client.screen
                    instanceof BulkSellConfirmationScreen screen) {
                screen.onResult(packet);
            } else if (client.player != null) {
                client.player.displayClientMessage(
                        message, false);
            }
        });
    }

    private static Component buildBuyMessage(S2CBuyResponsePacket packet) {
        if (packet.success()) {
            return Component.translatable(
                    packet.cartCheckout()
                            ? "gui.futureshops.status.buy.cart.success"
                            : "gui.futureshops.status.buy.single.success",
                    packet.totalQuantity(),
                    ShopUiUtil.formatMinorUnits(packet.totalMinorUnits()));
        }
        return Component.translatable(errorKey(packet.errorCode()));
    }

    private static Component buildSellMessage(S2CSellResponsePacket packet) {
        if (packet.success()) {
            return Component.translatable(
                    "gui.futureshops.status.sell.success",
                    packet.quantity(),
                    ShopUiUtil.formatMinorUnits(packet.totalMinorUnits()));
        }
        return Component.translatable(errorKey(packet.errorCode()));
    }

    private static Component buildBarterMessage(S2CBarterResponsePacket packet) {
        if (packet.success()) {
            return Component.translatable(
                    "gui.futureshops.status.barter.success",
                    packet.outputQuantity(),
                    packet.multiplier());
        }
        return Component.translatable(errorKey(packet.errorCode()));
    }

    /**
     * Maps a typed server result code to the chat-log lang key used for buy/sell/barter
     * transaction feedback. The switch is exhaustive over {@link ShopResultCode} so adding
     * a new constant without a matching case is a compile error (or, for codes that don't
     * apply to transaction chat, an intentional fall-through to the generic server-error
     * key via the {@code default} branch).
     */
    private static String errorKey(ShopResultCode code) {
        return switch (code) {
            case OUT_OF_STOCK -> "command.futureshops.buy.error.out_of_stock";
            case INVENTORY_FULL -> "command.futureshops.buy.error.inventory_full";
            case SHOP_CLOSED -> "command.futureshops.buy.error.shop_closed";
            case COOLDOWN -> "command.futureshops.buy.error.cooldown";
            case INVALID_ITEM, INVALID_RECIPE -> "command.futureshops.buy.error.invalid_item";
            case INSUFFICIENT_FUNDS -> "command.futureshops.error.insufficient_funds";
            case INSUFFICIENT_PHYSICAL_FUNDS -> "command.futureshops.error.insufficient_physical_funds";
            case INVALID_AMOUNT -> "command.futureshops.error.invalid_amount";
            case MAX_BALANCE_EXCEEDED -> "command.futureshops.error.max_balance_exceeded";
            case MISSING_ITEMS -> "gui.futureshops.status.sell.error.missing_items";
            case MISSING_INGREDIENTS -> "gui.futureshops.status.barter.error.missing_ingredients";
            // Codes not specific to transaction chat — fall back to the generic server-error
            // line so nothing ever renders the raw enum name to the player.
            case OK, BOUGHT, SOLD, CONFIG_SAVED, CONFIG_COPIED, DEPARTMENT_SET, PROMO_SET, PROMO_CLEARED,
                 LINKED, BARTER_LINKED, LINK_PENDING, LINK_NONE, BARTER_LINK_PENDING,
                 DESC_PENDING, LISTING_DESC_PENDING, NOT_OWNER, HOLD_ITEM, LISTING_LIMIT,
                 NO_LISTING, UNCONFIGURED, NOT_SINGLE_MODE, USE_SET_DEPARTMENT_ACTION,
                 NO_LINK, BAD_LINK_TARGET, RS_NOT_CONTROLLER, STORAGE_FULL,
                 MISSING_BARTER_ITEMS, ROLLBACK, NOTHING_TO_CLAIM, CLAIM_FAILED,
                 PROMO_FAILED, NO_CLIPBOARD, INVALID_REQUEST, INVALID_TARGET, SERVER_ERROR,
                 CANCELLED_BY_EVENT, SHOP_OUT_OF_MONEY, BUYBACK_CAP_REACHED
                    -> "command.futureshops.error.server";
        };
    }
}
