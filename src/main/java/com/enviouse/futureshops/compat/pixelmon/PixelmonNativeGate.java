package com.enviouse.futureshops.compat.pixelmon;

import com.mojang.logging.LogUtils;
import com.enviouse.futureshops.api.economy.EconomyApi;
import com.enviouse.futureshops.api.economy.EconomyProviderRegistry;
import com.enviouse.futureshops.api.economy.BindingV1;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import org.slf4j.Logger;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Exact and fail closed gate for Pixelmon Forge 1.20.1 9.2.3. */
public final class PixelmonNativeGate {
    public static final String MOD_ID = "pixelmon";
    public static final String SUPPORTED_VERSION = "9.2.3";
    public static final String MIXIN_CONFIG = "futureshops.pixelmon.mixins.json";
    public static final String EXPECTED_ARTIFACT_SHA512 =
            "3a9c6f375214c6d93c6cce8235e8a206e8f9731be8e168a254e78539087080796d97f0b22cb9a6db09d901c72e2e1ae53b9f2484761fb310479ce9f84ac9b145";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile State state = State.UNRESOLVED;
    private static final Map<Object, PixelmonNativeRequestContext.Request> PENDING =
            new WeakHashMap<>();
    private static final Map<UUID, PixelmonNativeEconomyAccess> ACCOUNTS =
            new ConcurrentHashMap<>();
    private static volatile String artifactFingerprint = "";

    private PixelmonNativeGate() {
    }

    public static void bootstrap() {
        if (!ModList.get().isLoaded(MOD_ID)) {
            state = State.ABSENT;
            return;
        }
        String version = ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("");
        artifactFingerprint = discoverArtifactFingerprint();
        if (!SUPPORTED_VERSION.equals(version) || !EXPECTED_ARTIFACT_SHA512.equals(artifactFingerprint)
                || !targetSurfacePresent()) {
            state = State.UNSUPPORTED_VERSION;
            LOGGER.warn("Pixelmon native economy integration disabled for unsupported version {}.", version);
            return;
        }
        state = State.ENABLED;
        EconomyProviderRegistry.registerPixelmon(EconomyApi.COMPATIBILITY_VERSION,
                context -> new PixelmonNativeProvider(context.server()));
        LOGGER.info("Pixelmon native economy integration enabled for {}.", SUPPORTED_VERSION);
    }

    public static State state() {
        return state;
    }

    public static Optional<PixelmonNativeRequestContext.Request> currentRequest() {
        return PixelmonNativeRequestContext.current();
    }

    public static boolean refuseCurrentRequest(String reason) {
        Optional<PixelmonNativeRequestContext.Request> request = currentRequest();
        request.ifPresent(value -> value.recordFailure(reason));
        return request.isPresent();
    }

    public static boolean isSupportedVersionLoaded() {
        if (state == State.ENABLED) {
            return true;
        }
        try {
            if (ModList.get() != null) {
                return ModList.get().isLoaded(MOD_ID)
                        && ModList.get().getModContainerById(MOD_ID)
                        .map(container -> SUPPORTED_VERSION.equals(
                                container.getModInfo().getVersion().toString())
                                && EXPECTED_ARTIFACT_SHA512.equals(discoverArtifactFingerprint())
                                && targetSurfacePresent())
                        .orElse(false);
            }
        } catch (RuntimeException ignored) {
            // LoadingModList is the authoritative early bootstrap source.
        }
        try {
            if (FMLLoader.getLoadingModList() == null) {
                return false;
            }
            for (ModFileInfo file : FMLLoader.getLoadingModList().getModFiles()) {
                for (IModInfo mod : file.getMods()) {
                    if (MOD_ID.equals(mod.getModId())) {
                        return SUPPORTED_VERSION.equals(mod.getVersion().toString())
                                && EXPECTED_ARTIFACT_SHA512.equals(sha512(file.getFile().getFilePath()))
                                && targetSurfacePresent(file.getFile());
                    }
                }
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return false;
    }

    public static String artifactFingerprint() {
        return artifactFingerprint.isBlank() ? EXPECTED_ARTIFACT_SHA512 : artifactFingerprint;
    }

    public static BindingV1 bindingFor(UUID accountId) {
        String fingerprint = artifactFingerprint();
        return new BindingV1(EconomyApi.PIXELMON_PROVIDER_ID, EconomyApi.COMPATIBILITY_VERSION,
                "pixelmon-native-mixin", "pixelmon-native-coordinator-v1", fingerprint,
                fingerprint, "pixelmon:9.2.3:native", accountId, "poke_dollars", 0,
                "pixelmon-native-storage", 1L, 1);
    }

    public static void registerAccount(UUID accountId, PixelmonNativeEconomyAccess account) {
        if (accountId != null && account != null) {
            ACCOUNTS.put(accountId, account);
        }
    }

    public static PixelmonNativeEconomyAccess account(UUID accountId) {
        return accountId == null ? null : ACCOUNTS.get(accountId);
    }

    public static java.util.Collection<PixelmonNativeEconomyAccess> accounts() {
        return java.util.List.copyOf(ACCOUNTS.values());
    }

    public static boolean applySetBalance(Object account, BigDecimal nextBalance,
                                          BigDecimal currentBalance) {
        Optional<PixelmonNativeRequestContext.Request> request = currentRequest();
        if (request.isEmpty()) {
            return false;
        }
        if (nextBalance == null || currentBalance == null) {
            request.get().recordFailure("invalid_balance");
            return true;
        }
        if (nextBalance.scale() < 0 || currentBalance.scale() < 0) {
            request.get().recordFailure("invalid_balance_scale");
            return true;
        }
        BigDecimal expected = BigDecimal.valueOf(request.get().amountMinorUnits(),
                request.get().binding().precision());
        BigDecimal delta = request.get().debit()
                ? currentBalance.subtract(nextBalance)
                : nextBalance.subtract(currentBalance);
        if (delta.compareTo(expected) != 0 || nextBalance.signum() < 0) {
            request.get().recordFailure("balance_delta_mismatch");
            return true;
        }
        request.get().recordResult(nextBalance);
        synchronized (PENDING) {
            PixelmonNativeRequestContext.Request previous = PENDING.putIfAbsent(account, request.get());
            if (previous != null && previous != request.get()) {
                request.get().recordFailure("pending_save");
            }
        }
        return true;
    }

    public static boolean applyDelta(Object account, BigDecimal currentBalance,
                                     BigDecimal amount, boolean debit) {
        Optional<PixelmonNativeRequestContext.Request> optional = currentRequest();
        if (optional.isEmpty()) {
            return false;
        }
        PixelmonNativeRequestContext.Request request = optional.get();
        if (amount == null || currentBalance == null || amount.signum() <= 0) {
            request.recordFailure("invalid_delta");
            return true;
        }
        BigDecimal expected = BigDecimal.valueOf(request.amountMinorUnits(),
                request.binding().precision());
        if (request.debit() != debit || amount.compareTo(expected) != 0) {
            request.recordFailure("delta_mismatch");
            return true;
        }
        BigDecimal next = debit ? currentBalance.subtract(amount) : currentBalance.add(amount);
        if (next.signum() < 0) {
            request.recordFailure("insufficient_balance");
            return true;
        }
        request.recordResult(next);
        synchronized (PENDING) {
            PixelmonNativeRequestContext.Request previous = PENDING.putIfAbsent(account, request);
            if (previous != null && previous != request) {
                request.recordFailure("pending_save");
            }
        }
        return true;
    }

    public static Optional<PixelmonNativeRequestContext.Request> pendingRequest(Object account) {
        synchronized (PENDING) {
            return Optional.ofNullable(PENDING.get(account));
        }
    }

    public static void clearPending(Object account) {
        synchronized (PENDING) {
            PENDING.remove(account);
        }
    }

    public enum State {
        UNRESOLVED,
        ABSENT,
        UNSUPPORTED_VERSION,
        ENABLED
    }

    private static boolean targetSurfacePresent() {
        try {
            return targetSurfacePresent(ModList.get().getModContainerById(MOD_ID)
                    .map(container -> container.getModInfo().getOwningFile().getFile())
                    .orElse(null));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean targetSurfacePresent(net.minecraftforge.forgespi.locating.IModFile file) {
        return file != null && file.findResource(
                "com/pixelmonmod/pixelmon/api/storage/PlayerPartyStorage.class") != null;
    }

    private static String discoverArtifactFingerprint() {
        try {
            return ModList.get().getModContainerById(MOD_ID)
                    .map(container -> sha512(container.getModInfo().getOwningFile().getFile().getFilePath()))
                    .orElse("");
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static String sha512(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return "";
        }
        try (java.io.InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) {
                    digest.update(buffer, 0, count);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException ignored) {
            return "";
        }
    }
}
