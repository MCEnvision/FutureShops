package com.enviouse.futureshops.server.market.session;

import com.enviouse.futureshops.client.market.MarketModule;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MarketServerSessionRegistry {
    public static final int MAXIMUM_SESSIONS = 100_000;
    public static final int MAXIMUM_REQUESTS_PER_SESSION = 512;
    public static final int MAXIMUM_RETIRED_ROUTES_PER_PLAYER = 64;

    private static final UUID ZERO = new UUID(0L, 0L);

    private final LinkedHashMap<UUID, MutableSession> sessions =
            new LinkedHashMap<>();
    private final Map<UUID, LinkedHashSet<UUID>> retiredRoutes =
            new LinkedHashMap<>();
    private final long idleTimeoutMillis;
    private final int requestCapacity;
    private final long refillPeriodMillis;

    public MarketServerSessionRegistry(
            Duration idleTimeout,
            int requestCapacity,
            Duration refillPeriod
    ) {
        Objects.requireNonNull(idleTimeout, "idleTimeout");
        Objects.requireNonNull(refillPeriod, "refillPeriod");
        idleTimeoutMillis = idleTimeout.toMillis();
        refillPeriodMillis = refillPeriod.toMillis();
        if (idleTimeoutMillis <= 0L || refillPeriodMillis <= 0L
                || requestCapacity <= 0 || requestCapacity > 10_000) {
            throw new IllegalArgumentException(
                    "Market session limits are invalid");
        }
        this.requestCapacity = requestCapacity;
    }

    public static MarketServerSessionRegistry defaults() {
        return new MarketServerSessionRegistry(Duration.ofMinutes(30),
                40, Duration.ofSeconds(1));
    }

    public synchronized Session open(
            UUID playerId,
            MarketModule module,
            String view,
            UUID routeNonce,
            long nowMillis
    ) {
        UUID player = id(playerId, "playerId");
        MarketModule safeModule = Objects.requireNonNull(module, "module");
        String safeView = view(view);
        UUID route = id(routeNonce, "routeNonce");
        if (safeModule == MarketModule.SHOP || nowMillis < 0L) {
            throw new IllegalArgumentException(
                    "Market session route is invalid");
        }
        LinkedHashSet<UUID> retired = retiredRoutes.computeIfAbsent(
                player, ignored -> new LinkedHashSet<>());
        if (retired.contains(route)) {
            throw new IllegalArgumentException(
                    "Market session route was retired");
        }
        MutableSession previous = sessions.remove(player);
        if (previous != null && !previous.routeNonce.equals(route)) {
            rememberRetired(retired, previous.routeNonce);
        }
        trimExpired(nowMillis);
        while (sessions.size() >= MAXIMUM_SESSIONS) {
            Iterator<Map.Entry<UUID, MutableSession>> iterator =
                    sessions.entrySet().iterator();
            if (!iterator.hasNext()) {
                break;
            }
            Map.Entry<UUID, MutableSession> eldest = iterator.next();
            iterator.remove();
            LinkedHashSet<UUID> values = retiredRoutes.computeIfAbsent(
                    eldest.getKey(), ignored -> new LinkedHashSet<>());
            rememberRetired(values, eldest.getValue().routeNonce);
        }
        MutableSession session = new MutableSession(player, safeModule,
                safeView, route, nowMillis, requestCapacity);
        sessions.put(player, session);
        return session.snapshot();
    }

    public synchronized MarketSessionDecision accept(
            UUID playerId,
            UUID routeNonce,
            MarketModule module,
            String view,
            UUID requestId,
            String requestFingerprint,
            long nowMillis
    ) {
        UUID player = id(playerId, "playerId");
        UUID route = id(routeNonce, "routeNonce");
        UUID request = id(requestId, "requestId");
        MarketModule safeModule = Objects.requireNonNull(module, "module");
        String safeView = view(view);
        String fingerprint = fingerprint(requestFingerprint);
        if (nowMillis < 0L) {
            throw new IllegalArgumentException(
                    "Market session time is invalid");
        }
        MutableSession session = sessions.get(player);
        if (session == null) {
            return MarketSessionDecision.MISSING;
        }
        if (expired(session, nowMillis)) {
            sessions.remove(player);
            rememberRetired(retiredRoutes.computeIfAbsent(player,
                    ignored -> new LinkedHashSet<>()),
                    session.routeNonce);
            return MarketSessionDecision.EXPIRED;
        }
        if (!session.routeNonce.equals(route)) {
            return MarketSessionDecision.STALE_ROUTE;
        }
        if (session.module != safeModule) {
            return MarketSessionDecision.WRONG_MODULE;
        }
        if (!session.view.equals(safeView)) {
            return MarketSessionDecision.WRONG_VIEW;
        }
        String previous = session.requests.get(request);
        if (previous != null) {
            session.lastTouchedMillis = nowMillis;
            return previous.equals(fingerprint)
                    ? MarketSessionDecision.REPLAY
                    : MarketSessionDecision.CONFLICT;
        }
        if (!session.limiter.accept(nowMillis)) {
            return MarketSessionDecision.RATE_LIMITED;
        }
        session.requests.put(request, fingerprint);
        while (session.requests.size()
                > MAXIMUM_REQUESTS_PER_SESSION) {
            Iterator<UUID> iterator = session.requests.keySet()
                    .iterator();
            iterator.next();
            iterator.remove();
        }
        session.lastTouchedMillis = nowMillis;
        sessions.remove(player);
        sessions.put(player, session);
        return MarketSessionDecision.ACCEPT;
    }

    public synchronized Optional<Session> session(UUID playerId) {
        MutableSession session = sessions.get(id(playerId, "playerId"));
        return session == null ? Optional.empty()
                : Optional.of(session.snapshot());
    }

    public synchronized void close(UUID playerId) {
        UUID player = id(playerId, "playerId");
        MutableSession removed = sessions.remove(player);
        if (removed != null) {
            rememberRetired(retiredRoutes.computeIfAbsent(player,
                    ignored -> new LinkedHashSet<>()),
                    removed.routeNonce);
        }
    }

    public synchronized boolean close(
            UUID playerId,
            UUID routeNonce
    ) {
        UUID player = id(playerId, "playerId");
        UUID route = id(routeNonce, "routeNonce");
        MutableSession session = sessions.get(player);
        if (session == null || !session.routeNonce.equals(route)) {
            return false;
        }
        close(player);
        return true;
    }

    public synchronized int trimExpired(long nowMillis) {
        if (nowMillis < 0L) {
            throw new IllegalArgumentException(
                    "Market session time is invalid");
        }
        int removed = 0;
        Iterator<Map.Entry<UUID, MutableSession>> iterator =
                sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, MutableSession> entry = iterator.next();
            if (!expired(entry.getValue(), nowMillis)) {
                continue;
            }
            iterator.remove();
            rememberRetired(retiredRoutes.computeIfAbsent(
                    entry.getKey(), ignored -> new LinkedHashSet<>()),
                    entry.getValue().routeNonce);
            removed++;
        }
        return removed;
    }

    public synchronized int size() {
        return sessions.size();
    }

    public synchronized void clear() {
        sessions.clear();
        retiredRoutes.clear();
    }

    private boolean expired(MutableSession session, long nowMillis) {
        return nowMillis - session.lastTouchedMillis > idleTimeoutMillis;
    }

    private static void rememberRetired(
            LinkedHashSet<UUID> retired,
            UUID route
    ) {
        retired.add(route);
        while (retired.size() > MAXIMUM_RETIRED_ROUTES_PER_PLAYER) {
            Iterator<UUID> iterator = retired.iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private static UUID id(UUID value, String label) {
        UUID result = Objects.requireNonNull(value, label);
        if (ZERO.equals(result)) {
            throw new IllegalArgumentException(
                    "Market session identity is invalid");
        }
        return result;
    }

    private static String view(String value) {
        String result = Objects.requireNonNull(value, "view").strip()
                .toLowerCase(Locale.ROOT);
        if (result.isEmpty() || result.length() > 32
                || !result.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException(
                    "Market session view is invalid");
        }
        return result;
    }

    private static String fingerprint(String value) {
        String result = Objects.requireNonNull(value,
                "requestFingerprint");
        if (!result.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "Market request fingerprint is invalid");
        }
        return result;
    }

    public record Session(
            UUID playerId,
            MarketModule module,
            String view,
            UUID routeNonce,
            long openedAtMillis,
            long lastTouchedMillis,
            int trackedRequests
    ) {
        public Session {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(module, "module");
            Objects.requireNonNull(view, "view");
            Objects.requireNonNull(routeNonce, "routeNonce");
            if (openedAtMillis < 0L
                    || lastTouchedMillis < openedAtMillis
                    || trackedRequests < 0
                    || trackedRequests > MAXIMUM_REQUESTS_PER_SESSION) {
                throw new IllegalArgumentException(
                        "Market session snapshot is invalid");
            }
        }
    }

    private final class MutableSession {
        private final UUID playerId;
        private final MarketModule module;
        private final String view;
        private final UUID routeNonce;
        private final long openedAtMillis;
        private final LinkedHashMap<UUID, String> requests =
                new LinkedHashMap<>();
        private final TokenBucket limiter;
        private long lastTouchedMillis;

        private MutableSession(
                UUID playerId,
                MarketModule module,
                String view,
                UUID routeNonce,
                long nowMillis,
                int tokens
        ) {
            this.playerId = playerId;
            this.module = module;
            this.view = view;
            this.routeNonce = routeNonce;
            openedAtMillis = nowMillis;
            lastTouchedMillis = nowMillis;
            limiter = new TokenBucket(tokens, nowMillis);
        }

        private Session snapshot() {
            return new Session(playerId, module, view, routeNonce,
                    openedAtMillis, lastTouchedMillis, requests.size());
        }
    }

    private final class TokenBucket {
        private final Deque<Long> accepted = new ArrayDeque<>();
        private final int capacity;
        private long lastObservedMillis;

        private TokenBucket(int capacity, long nowMillis) {
            this.capacity = capacity;
            lastObservedMillis = nowMillis;
        }

        private boolean accept(long nowMillis) {
            long clamped = Math.max(lastObservedMillis, nowMillis);
            lastObservedMillis = clamped;
            long threshold = clamped - refillPeriodMillis;
            while (!accepted.isEmpty()
                    && accepted.peekFirst() <= threshold) {
                accepted.removeFirst();
            }
            if (accepted.size() >= capacity) {
                return false;
            }
            accepted.addLast(clamped);
            return true;
        }
    }
}
