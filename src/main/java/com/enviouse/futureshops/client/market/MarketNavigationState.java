package com.enviouse.futureshops.client.market;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class MarketNavigationState {
    private static final int MAX_ROUTE_ACTIVATIONS = 4096;
    public enum Action {
        NAVIGATE,
        RETURN,
        SWITCH_MODULE,
        CLOSE
    }

    public record Transition(Action action, Optional<MarketRoute> route, boolean closeBoundShopSession) {
        public Transition {
            Objects.requireNonNull(action, "action");
            route = Objects.requireNonNull(route, "route");
            if (action == Action.CLOSE && route.isPresent()) {
                throw new IllegalArgumentException("A close transition cannot expose a route.");
            }
            if (action != Action.CLOSE && route.isEmpty()) {
                throw new IllegalArgumentException("An open navigation transition requires a route.");
            }
        }
    }

    private final int maximumDepth;
    private final Deque<MarketRoute> history = new ArrayDeque<>();
    private final Set<UUID> activatedRouteNonces = new HashSet<>();
    private MarketRoute current;
    private boolean open = true;
    private boolean boundShopSession;

    public MarketNavigationState(MarketRoute root, int maximumDepth, boolean boundShopSession) {
        current = Objects.requireNonNull(root, "root");
        if (maximumDepth <= 0 || maximumDepth > 256) {
            throw new IllegalArgumentException("Market route history depth is invalid.");
        }
        this.maximumDepth = maximumDepth;
        this.boundShopSession = boundShopSession;
        activatedRouteNonces.add(root.routeNonce());
    }

    public synchronized MarketRoute current() {
        requireOpen();
        return current;
    }

    public synchronized int historyDepth() {
        return history.size();
    }

    public synchronized Optional<MarketRoute> previous() {
        requireOpen();
        return Optional.ofNullable(history.peekLast());
    }

    public synchronized boolean isOpen() {
        return open;
    }

    public synchronized Transition navigate(MarketRoute next) {
        requireOpen();
        Objects.requireNonNull(next, "next");
        if (next.module() != current.module()) {
            return switchModule(next);
        }
        requireFreshNonce(next.routeNonce());
        history.addLast(current);
        while (history.size() > maximumDepth) {
            history.removeFirst();
        }
        current = next;
        return new Transition(Action.NAVIGATE, Optional.of(current), false);
    }

    public synchronized Transition back() {
        requireOpen();
        if (history.isEmpty()) {
            return close();
        }
        if (activatedRouteNonces.size() >= MAX_ROUTE_ACTIVATIONS) {
            return close();
        }
        UUID nonce = freshNonce();
        return activateBack(nonce);
    }

    public synchronized Transition back(UUID nonce) {
        requireOpen();
        if (history.isEmpty()) {
            return close();
        }
        if (activatedRouteNonces.size() >= MAX_ROUTE_ACTIVATIONS) {
            return close();
        }
        requireFreshNonce(nonce);
        return activateBack(nonce);
    }

    private Transition activateBack(UUID nonce) {
        current = history.removeLast().withNonce(nonce);
        return new Transition(Action.RETURN, Optional.of(current), false);
    }

    public synchronized Transition escape() {
        return back();
    }

    public synchronized Transition switchModule(MarketRoute moduleRoot) {
        requireOpen();
        Objects.requireNonNull(moduleRoot, "moduleRoot");
        if (!moduleRoot.isRoot()) {
            throw new IllegalArgumentException("Module switching requires a root route.");
        }
        return switchModuleEntry(moduleRoot);
    }

    public synchronized Transition switchModuleEntry(MarketRoute moduleEntry) {
        requireOpen();
        Objects.requireNonNull(moduleEntry, "moduleEntry");
        requireFreshNonce(moduleEntry.routeNonce());
        boolean closeSession = boundShopSession;
        boundShopSession = false;
        history.clear();
        current = moduleEntry;
        return new Transition(Action.SWITCH_MODULE, Optional.of(current), closeSession);
    }

    public synchronized void replaceCurrent(MarketRoute replacement) {
        requireOpen();
        Objects.requireNonNull(replacement, "replacement");
        if (replacement.module() != current.module()
            || !replacement.viewId().equals(current.viewId())
            || !replacement.routeNonce().equals(current.routeNonce())) {
            throw new IllegalArgumentException("Market route identity cannot be replaced.");
        }
        current = replacement;
    }

    public synchronized Transition close() {
        requireOpen();
        boolean closeSession = boundShopSession;
        boundShopSession = false;
        history.clear();
        open = false;
        return new Transition(Action.CLOSE, Optional.empty(), closeSession);
    }

    private void requireOpen() {
        if (!open) {
            throw new IllegalStateException("Market navigation is closed.");
        }
    }

    private void requireFreshNonce(UUID nonce) {
        Objects.requireNonNull(nonce, "nonce");
        if (activatedRouteNonces.size() >= MAX_ROUTE_ACTIVATIONS
            || !activatedRouteNonces.add(nonce)) {
            throw new IllegalArgumentException("Market route nonce was already activated.");
        }
    }

    private UUID freshNonce() {
        for (int attempt = 0; attempt < 16; attempt++) {
            UUID nonce = UUID.randomUUID();
            if (activatedRouteNonces.size() < MAX_ROUTE_ACTIVATIONS
                && activatedRouteNonces.add(nonce)) {
                return nonce;
            }
        }
        throw new IllegalStateException("Market route nonce generation failed.");
    }
}
