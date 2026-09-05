package com.enviouse.futureshopsp.server.debug;

import com.enviouse.futureshopsp.api.economy.MutationRequest;
import com.enviouse.futureshopsp.api.economy.ProviderCapabilities;
import com.enviouse.futureshopsp.api.economy.ProviderResult;
import com.enviouse.futureshopsp.api.economy.RequestId;
import net.minecraft.SharedConstants;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.Manifest;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DebugDiagnostics {
    private static final String CATEGORY = "futureshops.debug";
    private static final Logger LOGGER = LoggerFactory.getLogger(CATEGORY);
    private static final int MAX_EVENTS_PER_WINDOW = 128;
    private static final long WINDOW_NANOS = 10_000_000_000L;
    private static final Map<DebugModule, RateWindow> WINDOWS = new ConcurrentHashMap<>();
    private static final String DISCOVERED_ARTIFACT_HASH = discoverArtifactHash();
    private static final String DISCOVERED_SOURCE_COMMIT = discoverSourceCommit();
    private static volatile DebugSession session;

    private DebugDiagnostics() {
    }

    public static synchronized DebugToggleResult enable(DebugModule module) {
        Objects.requireNonNull(module, "module");
        DebugSession current = session;
        if (current != null && current.module() == module) {
            return new DebugToggleResult(false, current);
        }
        DebugSession next = new DebugSession(UUID.randomUUID(), module, System.currentTimeMillis());
        session = next;
        WINDOWS.clear();
        emit(module, "none", "debug_toggle", "enable", null, null, "authorized", "enabled", "none", "none",
                "none", "none", "none", "none", "enabled", "none", "operator may reproduce the bounded case");
        return new DebugToggleResult(true, next);
    }

    public static synchronized void disable() {
        DebugSession current = session;
        if (current != null) {
            emit(current.module(), "none", "debug_toggle", "disable", null, null, "authorized", "disabled", "none",
                    "none", "none", "none", "none", "none", "disabled", "none", "debug output is disabled");
        }
        session = null;
        WINDOWS.clear();
    }

    public static synchronized void reset() {
        session = null;
        WINDOWS.clear();
    }

    public static Optional<DebugSession> session() {
        return Optional.ofNullable(session);
    }

    public static boolean enabled(DebugModule module) {
        DebugSession current = session;
        return current != null && (current.module() == DebugModule.ALL || current.module() == module);
    }

    public static String statusLine() {
        DebugSession current = session;
        if (current == null) {
            return "debug=off";
        }
        return "debug=on module=" + current.module().id() + " session=" + current.sessionId();
    }

    public static void unauthorized() {
        emitUnconditional("debug_command_rejected", "unauthorized", "none", "none",
                "permission denied", "operator permission is required");
    }

    public static void invalidModule(String value) {
        String safe = sanitize(value);
        emitUnconditional("debug_command_rejected", "invalid_module", "none", safe,
                "module rejected", "use one of the documented debug modules");
    }

    public static void transaction(DebugModule module, String surface, String operation,
                                   MutationRequest request, ProviderCapabilities required,
                                   ProviderCapabilities observed, ProviderResult<?> result,
                                   String journalState, String receiptState, String custodyState,
                                   String claimState, String nextAction) {
        String requestId = request == null ? "none" : request.requestId().value().toString();
        String actor = request == null ? "none" : pseudonym(request.actor());
        String providerResult = result == null ? "none" : result.status().name();
        String error = result == null ? "none" : result.error().name();
        String diagnostic = result == null ? "none" : result.diagnostic();
        emit(module, "unknown", surface, operation, requestId, actor, "unknown", capabilities(required),
                capabilities(observed), validation(result), journalState, receiptState, custodyState, claimState,
                providerResult, error + ":" + diagnostic, nextAction);
    }

    public static void lifecycle(String provider, String state, String diagnostic) {
        emit(DebugModule.LIFECYCLE, sanitize(provider), "lifecycle", "transition", null, null, "none", "none", state,
                "none", "none", "none", "none", "none", sanitize(provider), sanitize(diagnostic),
                "follow the lifecycle safe action");
    }

    public static void provider(String provider, String operation, UUID actor, ProviderResult<?> result,
                                String accountClass, ProviderCapabilities required,
                                ProviderCapabilities observed, String nextAction) {
        emit(DebugModule.PROVIDER, sanitize(provider), "provider", operation, null, actor == null ? "none" : pseudonym(actor),
                sanitize(accountClass), capabilities(required), capabilities(observed), validation(result),
                "none", "none", "none", "none", result == null ? "none" : result.status().name(),
                result == null ? "none" : result.error().name() + ":" + result.diagnostic(), nextAction);
        if ("pixelmon".equals(provider)) {
            emit(DebugModule.PIXELMON, "pixelmon", "pixelmon", operation, null, actor == null ? "none" : pseudonym(actor),
                    sanitize(accountClass), capabilities(required), capabilities(observed), validation(result),
                    "none", "none", "none", "none", result == null ? "none" : result.status().name(),
                    result == null ? "none" : result.error().name() + ":" + result.diagnostic(), nextAction);
        }
        if ("vault".equals(provider)) {
            emit(DebugModule.VAULT, "vault", "vault", operation, null, actor == null ? "none" : pseudonym(actor),
                    sanitize(accountClass), capabilities(required), capabilities(observed), validation(result),
                    "none", "none", "none", "none", result == null ? "none" : result.status().name(),
                    result == null ? "none" : result.error().name() + ":" + result.diagnostic(), nextAction);
        }
    }

    private static String validation(ProviderResult<?> result) {
        if (result == null) {
            return "not_run";
        }
        return result.confirmed() ? "accepted" : "refused";
    }

    private static String capabilities(ProviderCapabilities capabilities) {
        if (capabilities == null) {
            return "none";
        }
        return "balance=" + capabilities.balanceQuery() + ",precheck=" + capabilities.precheck()
                + ",withdraw=" + capabilities.withdraw() + ",deposit=" + capabilities.deposit()
                + ",lookup=" + capabilities.receiptLookup() + ",retry=" + capabilities.idempotentRetry();
    }

    private static void emit(DebugModule module, String provider, String surface, String operation, String requestId,
                             String actor, String accountClass, String required, String observed,
                             String validation, String journalState, String receiptState,
                             String custodyState, String claimState, String providerResult,
                             String error, String nextAction) {
        if (!enabled(module)) {
            return;
        }
        RateWindow window = WINDOWS.computeIfAbsent(module, ignored -> new RateWindow());
        if (!window.allow()) {
            return;
        }
        DebugSession current = session;
        String sessionId = current == null ? "none" : current.sessionId().toString();
        String selected = current == null ? "none" : current.module().id();
        LOGGER.info("{} session={} module={} source_commit={} artifact_sha256={} minecraft={} loader={} provider={} lifecycle={} surface={} operation={} request_id={} actor_ref={} account_class={} required_capabilities={} observed_capabilities={} validation={} journal={} receipt={} custody={} claim={} provider_result={} error={} elapsed_ms={} side=server thread={} next_action={}",
                CATEGORY, sessionId, selected, sourceCommit(), artifactHash(), minecraftVersion(), loaderVersion(),
                sanitize(provider), "unknown", sanitize(surface), sanitize(operation), sanitize(requestId),
                sanitize(actor), sanitize(accountClass), sanitize(required), sanitize(observed), sanitize(validation),
                sanitize(journalState), sanitize(receiptState), sanitize(custodyState), sanitize(claimState),
                sanitize(providerResult), sanitize(error), "0", Thread.currentThread().getName(), sanitize(nextAction));
    }

    private static void emitUnconditional(String operation, String surface, String requestId,
                                          String actor, String error, String nextAction) {
        LOGGER.warn("{} session=none module=none source_commit={} artifact_sha256={} minecraft={} loader={} provider=none lifecycle=none surface={} operation={} request_id={} actor_ref={} account_class=none required_capabilities=none observed_capabilities=none validation=refused journal=none receipt=none custody=none claim=none provider_result=rejected error={} elapsed_ms=0 side=server thread={} next_action={}",
                CATEGORY, sourceCommit(), artifactHash(), minecraftVersion(), loaderVersion(), sanitize(surface),
                sanitize(operation), sanitize(requestId), sanitize(actor), sanitize(error),
                Thread.currentThread().getName(), sanitize(nextAction));
    }

    private static String sourceCommit() {
        return sanitize(System.getProperty("futureshops.source_commit", DISCOVERED_SOURCE_COMMIT));
    }

    private static String artifactHash() {
        return sanitize(System.getProperty("futureshops.artifact_sha256", DISCOVERED_ARTIFACT_HASH));
    }

    private static String discoverSourceCommit() {
        String manifestValue = manifestValue("FutureShops-Source-Commit");
        return manifestValue == null || manifestValue.isBlank() ? "unknown" : manifestValue;
    }

    private static String discoverArtifactHash() {
        try {
            Path location = null;
            try {
                location = ModList.get().getModFileById("futureshops").getFile().getFilePath();
            } catch (RuntimeException ignored) {
                // Unit tests and early loading may not have a mod file registry yet.
            }
            if (location == null) {
                location = Path.of(DebugDiagnostics.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            }
            if (!Files.isRegularFile(location)) {
                return "dev-classes";
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(location)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            return "unknown";
        }
    }

    private static String manifestValue(String name) {
        try (InputStream input = DebugDiagnostics.class.getResourceAsStream("/META-INF/MANIFEST.MF")) {
            if (input == null) {
                return null;
            }
            return new Manifest(input).getMainAttributes().getValue(name);
        } catch (Exception exception) {
            return null;
        }
    }

    private static String minecraftVersion() {
        try {
            return sanitize(SharedConstants.getCurrentVersion().getName());
        } catch (RuntimeException exception) {
            return "unknown";
        }
    }

    private static String loaderVersion() {
        try {
            return sanitize(ModList.get().getModContainerById("neoforge")
                    .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown"));
        } catch (RuntimeException exception) {
            return "unknown";
        }
    }

    private static String pseudonym(UUID actor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(actor.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            return "unknown";
        }
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        String singleLine = value.replace('\n', ' ').replace('\r', ' ').replace('=', ':').trim();
        return singleLine.length() <= 256 ? singleLine : singleLine.substring(0, 256);
    }

    public record DebugSession(UUID sessionId, DebugModule module, long startedAtMillis) {
    }

    public record DebugToggleResult(boolean changed, DebugSession session) {
    }

    private static final class RateWindow {
        private final AtomicLong started = new AtomicLong(System.nanoTime());
        private final AtomicInteger count = new AtomicInteger();

        boolean allow() {
            long now = System.nanoTime();
            long start = started.get();
            if (now - start >= WINDOW_NANOS && started.compareAndSet(start, now)) {
                count.set(0);
            }
            return count.incrementAndGet() <= MAX_EVENTS_PER_WINDOW;
        }
    }
}
