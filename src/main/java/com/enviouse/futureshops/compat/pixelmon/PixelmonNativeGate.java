package com.enviouse.futureshops.compat.pixelmon;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Optional;

/** Exact and fail closed gate for Pixelmon Forge 1.20.1 9.2.3. */
public final class PixelmonNativeGate {
    public static final String MOD_ID = "pixelmon";
    public static final String SUPPORTED_VERSION = "9.2.3";
    public static final String MIXIN_CONFIG = "futureshops.pixelmon.mixins.json";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile State state = State.UNRESOLVED;
    private static final Map<Object, PixelmonNativeRequestContext.Request> PENDING =
            new WeakHashMap<>();

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
        if (!SUPPORTED_VERSION.equals(version)) {
            state = State.UNSUPPORTED_VERSION;
            LOGGER.warn("Pixelmon native economy integration disabled for unsupported version {}.", version);
            return;
        }
        state = State.ENABLED;
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
        try {
            if (ModList.get() != null) {
                return ModList.get().isLoaded(MOD_ID)
                        && ModList.get().getModContainerById(MOD_ID)
                        .map(container -> SUPPORTED_VERSION.equals(
                                container.getModInfo().getVersion().toString()))
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
                        return SUPPORTED_VERSION.equals(mod.getVersion().toString());
                    }
                }
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return false;
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
            PENDING.put(account, request.get());
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
            PENDING.put(account, request);
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
}
