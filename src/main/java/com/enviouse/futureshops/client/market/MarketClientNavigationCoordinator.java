package com.enviouse.futureshops.client.market;

import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.server.market.query.MarketPageCard;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MarketClientNavigationCoordinator {
    private static final int MAXIMUM_OPEN_REQUESTS = 128;
    private static final int MAXIMUM_ROUTE_ACTIVATIONS = 4096;
    private static final int MAXIMUM_DETAIL_SELECTIONS = 64;
    private static final UUID ZERO = new UUID(0L, 0L);

    public enum OpenAction {
        TAB,
        DETAIL,
        RETURN,
        SWITCH_MODULE
    }

    public enum OpenDecision {
        ACCEPT,
        CLOSED,
        UNKNOWN_REQUEST,
        STALE_REQUEST,
        STALE_ROUTE,
        WRONG_MODULE,
        WRONG_VIEW,
        DUPLICATE_RESPONSE,
        ROUTE_LIMIT
    }

    public record OpenRequest(
        UUID requestId,
        OpenAction action,
        MarketRoute desiredRoute
    ) {
        public OpenRequest {
            requestId = requireId(requestId, "request identifier");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(desiredRoute, "desiredRoute");
        }

        public MarketModule module() {
            return desiredRoute.module();
        }

        public String viewId() {
            return desiredRoute.viewId();
        }
    }

    public record Command(
        Optional<OpenRequest> openRequest,
        Optional<MarketNavigationState.Transition> transition
    ) {
        public Command {
            openRequest = Objects.requireNonNull(openRequest, "openRequest");
            transition = Objects.requireNonNull(transition, "transition");
            if (openRequest.isPresent() == transition.isPresent()) {
                throw new IllegalArgumentException("Market command requires one outcome.");
            }
            if (transition.isPresent()
                && transition.orElseThrow().action()
                != MarketNavigationState.Action.CLOSE) {
                throw new IllegalArgumentException("Immediate market commands must close.");
            }
        }

        public static Command open(OpenRequest request) {
            return new Command(Optional.of(request), Optional.empty());
        }

        public static Command close(MarketNavigationState.Transition transition) {
            return new Command(Optional.empty(), Optional.of(transition));
        }
    }

    public record OpenResponse(
        OpenDecision decision,
        Optional<MarketNavigationState.Transition> transition
    ) {
        public OpenResponse {
            Objects.requireNonNull(decision, "decision");
            transition = Objects.requireNonNull(transition, "transition");
            if (decision == OpenDecision.ACCEPT && transition.isEmpty()) {
                throw new IllegalArgumentException("Accepted market opens require a transition.");
            }
            if (decision != OpenDecision.ACCEPT
                && decision != OpenDecision.ROUTE_LIMIT
                && transition.isPresent()) {
                throw new IllegalArgumentException("Rejected market opens cannot transition.");
            }
        }

        public static OpenResponse reject(OpenDecision decision) {
            return new OpenResponse(decision, Optional.empty());
        }
    }

    private record PendingOpen(
        OpenRequest request,
        UUID originRouteNonce
    ) {
        private PendingOpen {
            Objects.requireNonNull(request, "request");
            originRouteNonce = requireId(originRouteNonce, "origin route nonce");
        }
    }

    private record DetailKey(
        MarketModule module,
        String identity
    ) {
        private DetailKey {
            Objects.requireNonNull(module, "module");
            Objects.requireNonNull(identity, "identity");
        }
    }

    private final MarketNavigationState navigation;
    private final MarketRequestGate responseGate;
    private final MarketSessionPreferences preferences;
    private final LinkedHashMap<UUID, PendingOpen> pendingOpens =
        new LinkedHashMap<>();
    private final LinkedHashSet<UUID> consumedOpens = new LinkedHashSet<>();
    private final Set<UUID> activatedRouteNonces = new LinkedHashSet<>();
    private final LinkedHashMap<DetailKey, MarketDetailSelection>
        detailSelections = new LinkedHashMap<>();
    private UUID latestOpenRequest;
    private MarketNavigationState.Transition closeTransition;
    private boolean open = true;

    public MarketClientNavigationCoordinator(
        MarketRoute initialRoute,
        int maximumHistoryDepth,
        int maximumTrackedResponses,
        boolean boundShopSession,
        MarketSessionPreferences preferences
    ) {
        this(initialRoute, maximumHistoryDepth, maximumTrackedResponses,
            boundShopSession, preferences, false);
    }

    public MarketClientNavigationCoordinator(
        MarketRoute initialRoute,
        int maximumHistoryDepth,
        int maximumTrackedResponses,
        boolean boundShopSession,
        MarketSessionPreferences preferences,
        boolean rememberInitialRoute
    ) {
        MarketRoute initial = Objects.requireNonNull(initialRoute, "initialRoute");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        navigation = new MarketNavigationState(
            initial, maximumHistoryDepth, boundShopSession);
        responseGate = new MarketRequestGate(
            initial.routeNonce(), maximumTrackedResponses);
        activatedRouteNonces.add(initial.routeNonce());
        if (rememberInitialRoute) {
            preferences.rememberRoute(initial, true);
        }
    }

    public synchronized boolean isOpen() {
        return open;
    }

    public synchronized MarketRoute current() {
        requireOpen();
        return navigation.current();
    }

    public synchronized int historyDepth() {
        return navigation.historyDepth();
    }

    public MarketSessionPreferences preferences() {
        return preferences;
    }

    public synchronized void updateCurrent(MarketRoute route) {
        requireOpen();
        navigation.replaceCurrent(Objects.requireNonNull(route, "route"));
        preferences.rememberRouteState(route);
    }

    public synchronized OpenRequest beginTab(UUID requestId,
                                             String viewId) {
        requireOpen();
        UUID id = availableRequestId(requestId);
        MarketRoute current = navigation.current();
        MarketRoute preferred = preferences.entryRoute(
            current.module(), id);
        MarketRoute desired = new MarketRoute(current.module(), viewId,
            preferred.categoryId(), "", preferred.filterId(),
            preferred.sortId(), 0, 0, "", id);
        return beginOpen(id, OpenAction.TAB, desired);
    }

    public synchronized OpenRequest beginDetail(UUID requestId,
                                                MarketRoute desiredRoute) {
        requireOpen();
        UUID id = availableRequestId(requestId);
        MarketRoute desired = Objects.requireNonNull(desiredRoute, "desiredRoute");
        if (desired.module() != navigation.current().module()) {
            throw new IllegalArgumentException("Market detail must remain in its module.");
        }
        return beginOpen(id, OpenAction.DETAIL, desired.withNonce(id));
    }

    public synchronized MarketDetailSelection rememberDetail(
        MarketPageCard card
    ) {
        requireOpen();
        MarketRoute current = navigation.current();
        if (current.isDetail()) {
            throw new IllegalStateException(
                "Nested market detail routes are not supported.");
        }
        MarketDetailSelection selection = new MarketDetailSelection(
            current.module(), current.viewId(),
            Objects.requireNonNull(card, "card"));
        DetailKey key = new DetailKey(current.module(),
            selection.identity());
        detailSelections.remove(key);
        detailSelections.put(key, selection);
        while (detailSelections.size() > MAXIMUM_DETAIL_SELECTIONS) {
            detailSelections.remove(
                detailSelections.entrySet().iterator().next().getKey());
        }
        return selection;
    }

    public synchronized Optional<MarketDetailSelection> detailSelection(
        MarketModule module,
        String identity
    ) {
        requireOpen();
        DetailKey key = new DetailKey(
            Objects.requireNonNull(module, "module"),
            Objects.requireNonNull(identity, "identity"));
        MarketDetailSelection selection = detailSelections.remove(key);
        if (selection == null) {
            return Optional.empty();
        }
        detailSelections.put(key, selection);
        return Optional.of(selection);
    }

    public synchronized Command back(UUID requestId) {
        requireOpen();
        Optional<MarketRoute> previous = navigation.previous();
        if (previous.isEmpty()) {
            return Command.close(closeInternal());
        }
        UUID id = availableRequestId(requestId);
        OpenRequest request = beginOpen(id, OpenAction.RETURN,
            previous.orElseThrow().withNonce(id));
        return Command.open(request);
    }

    public synchronized Command escape(UUID requestId) {
        return back(requestId);
    }

    public synchronized OpenRequest beginSwitchModule(
        UUID requestId,
        MarketModule targetModule
    ) {
        requireOpen();
        UUID id = availableRequestId(requestId);
        MarketModule target = Objects.requireNonNull(targetModule, "targetModule");
        if (target == navigation.current().module()) {
            throw new IllegalArgumentException("Market module is already active.");
        }
        return beginOpen(id, OpenAction.SWITCH_MODULE,
            preferences.entryRoute(target, id));
    }

    public synchronized OpenResponse acceptOpenResponse(
        UUID requestId,
        MarketModule module,
        String viewId,
        UUID routeNonce
    ) {
        if (!open) {
            return OpenResponse.reject(OpenDecision.CLOSED);
        }
        UUID id = requireId(requestId, "request identifier");
        UUID route = requireId(routeNonce, "route nonce");
        MarketModule responseModule = Objects.requireNonNull(module, "module");
        String responseView = Objects.requireNonNull(viewId, "viewId");
        if (consumedOpens.contains(id)) {
            return OpenResponse.reject(OpenDecision.DUPLICATE_RESPONSE);
        }
        PendingOpen pending = pendingOpens.get(id);
        if (pending == null) {
            return OpenResponse.reject(OpenDecision.UNKNOWN_REQUEST);
        }
        if (!id.equals(latestOpenRequest)) {
            retireOpen(id);
            return OpenResponse.reject(OpenDecision.STALE_REQUEST);
        }
        if (!navigation.current().routeNonce().equals(
            pending.originRouteNonce())) {
            retireOpen(id);
            return OpenResponse.reject(OpenDecision.STALE_ROUTE);
        }
        MarketRoute desired = pending.request().desiredRoute();
        if (responseModule != desired.module()) {
            retireOpen(id);
            return OpenResponse.reject(OpenDecision.WRONG_MODULE);
        }
        if (!responseView.equals(desired.viewId())) {
            retireOpen(id);
            return OpenResponse.reject(OpenDecision.WRONG_VIEW);
        }
        if (activatedRouteNonces.contains(route)) {
            retireOpen(id);
            return OpenResponse.reject(OpenDecision.STALE_ROUTE);
        }
        if (activatedRouteNonces.size() >= MAXIMUM_ROUTE_ACTIVATIONS) {
            MarketNavigationState.Transition transition = closeInternal();
            return new OpenResponse(OpenDecision.ROUTE_LIMIT,
                Optional.of(transition));
        }
        MarketRoute activated = desired.withNonce(route);
        MarketNavigationState.Transition transition;
        switch (pending.request().action()) {
            case TAB, DETAIL -> transition = navigation.navigate(activated);
            case RETURN -> {
                Optional<MarketRoute> previous = navigation.previous();
                if (previous.isEmpty()
                    || !previous.orElseThrow().withNonce(route)
                    .equals(activated)) {
                    retireOpen(id);
                    return OpenResponse.reject(OpenDecision.STALE_ROUTE);
                }
                transition = navigation.back(route);
            }
            case SWITCH_MODULE -> transition =
                navigation.switchModuleEntry(activated);
            default -> throw new IllegalStateException("Unknown market open action.");
        }
        responseGate.enterRoute(route);
        activatedRouteNonces.add(route);
        preferences.rememberRoute(activated,
            pending.request().action() == OpenAction.TAB
                || pending.request().action() == OpenAction.SWITCH_MODULE);
        retireOpen(id);
        return new OpenResponse(OpenDecision.ACCEPT, Optional.of(transition));
    }

    public synchronized boolean cancelOpen(UUID requestId) {
        if (requestId == null || !pendingOpens.containsKey(requestId)) {
            return false;
        }
        retireOpen(requestId);
        return true;
    }

    public synchronized void beginResponseRequest(
        UUID requestId,
        MarketResponseFamily family
    ) {
        requireOpen();
        MarketRoute route = navigation.current();
        responseGate.begin(requestId, route.module(), route.routeNonce(),
            Objects.requireNonNull(family, "family"));
    }

    public synchronized MarketRequestGate.Decision acceptResponse(
        UUID requestId,
        MarketModule module,
        UUID routeNonce,
        MarketResponseFamily family
    ) {
        return responseGate.accept(requestId,
            Objects.requireNonNull(module, "module"), routeNonce,
            Objects.requireNonNull(family, "family"));
    }

    public synchronized void rememberPaymentSource(PaymentSource source) {
        preferences.rememberPaymentSource(source);
    }

    public synchronized Optional<PaymentSource> paymentSource() {
        return preferences.paymentSource();
    }

    public synchronized MarketNavigationState.Transition close() {
        return closeInternal();
    }

    private OpenRequest beginOpen(UUID requestId, OpenAction action,
                                  MarketRoute desiredRoute) {
        MarketRoute current = navigation.current();
        OpenRequest request = new OpenRequest(requestId, action, desiredRoute);
        pendingOpens.put(requestId,
            new PendingOpen(request, current.routeNonce()));
        latestOpenRequest = requestId;
        trimOpenRequests();
        return request;
    }

    private MarketNavigationState.Transition closeInternal() {
        if (!open) {
            return closeTransition;
        }
        closeTransition = navigation.close();
        responseGate.close();
        pendingOpens.clear();
        detailSelections.clear();
        latestOpenRequest = null;
        open = false;
        return closeTransition;
    }

    private UUID availableRequestId(UUID requestId) {
        UUID id = requireId(requestId, "request identifier");
        if (pendingOpens.containsKey(id) || consumedOpens.contains(id)) {
            throw new IllegalArgumentException("Market open request was already used.");
        }
        return id;
    }

    private void retireOpen(UUID requestId) {
        pendingOpens.remove(requestId);
        consumedOpens.add(requestId);
        if (requestId.equals(latestOpenRequest)) {
            latestOpenRequest = null;
        }
        while (consumedOpens.size() > MAXIMUM_OPEN_REQUESTS) {
            consumedOpens.remove(consumedOpens.iterator().next());
        }
    }

    private void trimOpenRequests() {
        while (pendingOpens.size() > MAXIMUM_OPEN_REQUESTS) {
            Map.Entry<UUID, PendingOpen> eldest =
                pendingOpens.entrySet().iterator().next();
            retireOpen(eldest.getKey());
        }
    }

    private void requireOpen() {
        if (!open) {
            throw new IllegalStateException("Market coordinator is closed.");
        }
    }

    private static UUID requireId(UUID value, String label) {
        if (value == null || ZERO.equals(value)) {
            throw new IllegalArgumentException("Market " + label + " is required.");
        }
        return value;
    }
}
