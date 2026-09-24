package com.enviouse.futureshops.server.debug;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default off, bounded, server side diagnostics. This class never owns money,
 * inventory, persistence, or a provider decision.
 */
public final class DebugDiagnostics {
    public static final int SCHEMA_VERSION = 2;
    public static final long CAPTURE_DURATION_MILLIS = 60_000L;
    public static final int MAX_EVENTS_PER_SECOND = 100;
    public static final int MAX_EVENTS_PER_CAPTURE = 2_000;
    public static final int MAX_EVENT_BYTES = 4 * 1024;
    public static final long MAX_CAPTURE_BYTES = 5L * 1024L * 1024L;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long WINDOW_NANOS = TimeUnit.SECONDS.toNanos(1L);
    private static final Object LOCK = new Object();
    private static volatile DebugSession session;
    private static volatile ExecutorService output;
    private static final ConcurrentLinkedQueue<DiagnosticEventV2> EVENTS =
            new ConcurrentLinkedQueue<>();
    private static final AtomicInteger EVENT_COUNT = new AtomicInteger();
    private static final AtomicLong EVENT_BYTES = new AtomicLong();
    private static final AtomicLong DROPPED = new AtomicLong();
    private static final AtomicLong WINDOW_START = new AtomicLong(System.nanoTime());
    private static final AtomicInteger WINDOW_COUNT = new AtomicInteger();

    private DebugDiagnostics() {
    }

    public static DebugToggleResult enable(DebugModule module) {
        return enable(module, DebugSelector.none());
    }

    public static DebugToggleResult enable(DebugModule module, DebugSelector selector) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(selector, "selector");
        synchronized (LOCK) {
            DebugSession current = activeSession();
            if (current != null && current.module() == module
                    && current.selector().equals(selector)) {
                return new DebugToggleResult(false, current);
            }
            stopOutput();
            DebugSession next = new DebugSession(UUID.randomUUID(), module, selector,
                    System.currentTimeMillis());
            session = next;
            EVENTS.clear();
            EVENT_COUNT.set(0);
            EVENT_BYTES.set(0L);
            DROPPED.set(0L);
            WINDOW_START.set(System.nanoTime());
            WINDOW_COUNT.set(0);
            output = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "futureshops-debug-output");
                thread.setDaemon(true);
                return thread;
            });
            return new DebugToggleResult(true, next);
        }
    }

    public static void disable() {
        synchronized (LOCK) {
            session = null;
            stopOutput();
            EVENTS.clear();
            EVENT_COUNT.set(0);
            EVENT_BYTES.set(0L);
            DROPPED.set(0L);
            WINDOW_COUNT.set(0);
        }
    }

    public static void reset() {
        disable();
    }

    public static Optional<DebugSession> session() {
        return Optional.ofNullable(activeSession());
    }

    public static boolean enabled(DebugModule module) {
        DebugSession current = activeSession();
        return current != null && (current.module() == DebugModule.ALL
                || current.module() == module);
    }

    public static boolean enabledFor(DebugModule module, String rootRef, UUID actor) {
        DebugSession current = activeSession();
        return current != null
                && (current.module() == DebugModule.ALL || current.module() == module)
                && current.selector().matches(rootRef, actor);
    }

    public static String statusLine() {
        DebugSession current = activeSession();
        if (current == null) {
            return "debug=off";
        }
        long remaining = Math.max(0L,
                current.startedAtMillis() + CAPTURE_DURATION_MILLIS
                        - System.currentTimeMillis());
        return "debug=on side=server module=" + current.module().id()
                + " selector=" + current.selector().display(current.captureId())
                + " capture=" + current.captureId()
                + " remaining_ms=" + remaining
                + " events=" + EVENT_COUNT.get()
                + " dropped=" + DROPPED.get()
                + " bytes=" + EVENT_BYTES.get()
                + " limits=60s,100events_per_second,2000events,4096bytes_per_event,5242880bytes"
                + " output=server.log";
    }

    public static List<DiagnosticEventV2> snapshot() {
        return List.copyOf(new ArrayList<>(EVENTS));
    }

    public static long droppedEvents() {
        activeSession();
        return DROPPED.get();
    }

    /** Record one event after the caller has observed its real route decision. */
    public static void record(DebugModule module, String operation, String rootRef,
                              String legRef, UUID actor, String provider,
                              String lifecycle, String desiredState, String actualState,
                              String reason, String capabilities, String journalState,
                              String receiptState, String custodyState, String claimState,
                              String nextAction) {
        DebugSession current = activeSession();
        if (current == null || (current.module() != DebugModule.ALL
                && current.module() != module)
                || !current.selector().matches(rootRef, actor)) {
            return;
        }
        if (!allowRate()) {
            DROPPED.incrementAndGet();
            return;
        }
        String captureRoot = pseudonym(current.captureId(), rootRef);
        String captureLeg = pseudonym(current.captureId(), legRef);
        String captureActor = actor == null
                ? "none" : pseudonym(current.captureId(), actor.toString());
        long sequence = EVENT_COUNT.incrementAndGet();
        if (sequence > MAX_EVENTS_PER_CAPTURE) {
            EVENT_COUNT.decrementAndGet();
            DROPPED.incrementAndGet();
            return;
        }
        DiagnosticEventV2 event = new DiagnosticEventV2(current.captureId(),
                sourceCommit(), artifactHash(), "server", sequence, module,
                operation, captureRoot, captureLeg, captureActor, provider,
                lifecycle, desiredState, actualState, reason, capabilities,
                journalState, receiptState, custodyState, claimState, nextAction);
        long bytes = estimateBytes(event);
        if (bytes > MAX_EVENT_BYTES
                || EVENT_BYTES.get() + bytes > MAX_CAPTURE_BYTES) {
            EVENT_COUNT.decrementAndGet();
            DROPPED.incrementAndGet();
            return;
        }
        EVENT_BYTES.addAndGet(bytes);
        EVENTS.add(event);
        while (EVENTS.size() > MAX_EVENTS_PER_CAPTURE) {
            EVENTS.poll();
        }
        ExecutorService worker = output;
        if (worker != null) {
            try {
                worker.execute(() -> LOGGER.info("futureshops.debug schema={} capture={} sequence={} module={} operation={} root_ref={} leg_ref={} actor_ref={} provider={} lifecycle={} desired={} actual={} reason={} capabilities={} journal={} receipt={} custody={} claim={} next_action={}",
                        SCHEMA_VERSION, event.captureId(), event.sequence(), event.module().id(),
                        event.operation(), event.rootRef(), event.legRef(), event.actorRef(),
                        event.provider(), event.lifecycle(), event.desiredState(), event.actualState(),
                        event.reason(), event.capabilities(), event.journalState(), event.receiptState(),
                        event.custodyState(), event.claimState(), event.nextAction()));
            } catch (RuntimeException ignored) {
                DROPPED.incrementAndGet();
            }
        }
    }

    public static void unauthorized() {
        LOGGER.warn("futureshops debug command rejected reason=permission_denied");
    }

    public static void invalidModule(String value) {
        LOGGER.warn("futureshops debug command rejected reason=invalid_module value={}",
                sanitize(value));
    }

    public static String pseudonym(UUID captureId, String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(captureId.toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            digest.update(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest(), 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            return "unknown";
        }
    }

    static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        String safe = value.replace('\n', ' ').replace('\r', ' ')
                .replace('=', ':').trim();
        if (safe.length() > 256) {
            safe = safe.substring(0, 256);
        }
        return safe;
    }

    static String sanitizeReason(String value) {
        String safe = sanitize(value);
        safe = safe.replaceAll("(?i)(balance|amount|value|nbt|token|password|secret|uuid|path|address)[:][^, ]+",
                "$1:redacted");
        return safe.replaceAll("(?i)[0-9a-f]{8}-[0-9a-f-]{27,}", "redacted");
    }

    private static DebugSession activeSession() {
        DebugSession current = session;
        if (current != null && System.currentTimeMillis()
                >= current.startedAtMillis() + CAPTURE_DURATION_MILLIS) {
            disable();
            return null;
        }
        return current;
    }

    private static boolean allowRate() {
        long now = System.nanoTime();
        long start = WINDOW_START.get();
        if (now - start >= WINDOW_NANOS && WINDOW_START.compareAndSet(start, now)) {
            WINDOW_COUNT.set(0);
        }
        return WINDOW_COUNT.incrementAndGet() <= MAX_EVENTS_PER_SECOND;
    }

    private static long estimateBytes(DiagnosticEventV2 event) {
        return 128L + event.toString().length();
    }

    private static String sourceCommit() {
        return sanitize(System.getProperty("futureshops.source_commit", "unknown"));
    }

    private static String artifactHash() {
        return sanitize(System.getProperty("futureshops.artifact_sha256", "unknown"));
    }

    private static void stopOutput() {
        ExecutorService worker = output;
        output = null;
        if (worker != null) {
            worker.shutdown();
            try {
                worker.awaitTermination(250L, TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                worker.shutdownNow();
            }
        }
    }

    public record DebugSession(UUID captureId, DebugModule module,
                               DebugSelector selector, long startedAtMillis) {
        public DebugSession {
            Objects.requireNonNull(captureId, "captureId");
            Objects.requireNonNull(module, "module");
            Objects.requireNonNull(selector, "selector");
        }
    }

    public record DebugToggleResult(boolean changed, DebugSession session) {
    }
}
