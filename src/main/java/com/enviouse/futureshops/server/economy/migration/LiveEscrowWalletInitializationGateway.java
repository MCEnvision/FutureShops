package com.enviouse.futureshops.server.economy.migration;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeException;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeManager;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeService;
import com.enviouse.futureshops.server.escrow.runtime.EscrowWalletService;
import com.enviouse.futureshops.server.escrow.runtime.WalletMutationResult;
import com.enviouse.futureshops.server.escrow.runtime.WalletMutationStatus;

import java.util.Objects;

public final class LiveEscrowWalletInitializationGateway
        implements WalletInitializationGateway {
    private static final String LEGACY_REASON = "legacy_balance_migration";
    private static final String STARTING_REASON = "starting_balance_grant";

    @Override
    public WalletInitializationResult initialize(
            WalletInitializationRequest request
    ) {
        Objects.requireNonNull(request, "request");
        if (!runtimeReady()) {
            return WalletInitializationResult.retryLater(
                    "Escrow runtime is not ready");
        }
        boolean allowNegative = request.source()
                == WalletInitializationSource.LEGACY_BALANCE
                && Config.economyAllowNegative;
        try {
            WalletMutationResult result = EscrowWalletService.live().initialize(
                    request.requestId(),
                    request.playerId(),
                    request.balanceMinorUnits(),
                    allowNegative,
                    reason(request.source()));
            return map(result.status());
        } catch (EscrowRuntimeException exception) {
            if (!runtimeReady()) {
                return WalletInitializationResult.retryLater(
                        "Escrow runtime is not ready");
            }
            return WalletInitializationResult.conflict(
                    "Escrow wallet initialization failed");
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return WalletInitializationResult.conflict(
                    "Escrow wallet initialization is invalid");
        }
    }

    @Override
    public boolean supportsNegativeLegacyBalances() {
        return Config.economyAllowNegative;
    }

    static WalletInitializationResult map(WalletMutationStatus status) {
        Objects.requireNonNull(status, "status");
        return switch (status) {
            case APPLIED -> WalletInitializationResult.applied();
            case REPLAYED -> WalletInitializationResult.replayed();
            case ALREADY_INITIALIZED ->
                    WalletInitializationResult.alreadyInitialized(
                    "Wallet was initialized by another request");
            case NEGATIVE_NOT_ALLOWED -> WalletInitializationResult.conflict(
                    "Negative legacy balance is not allowed");
            case ARITHMETIC_OVERFLOW -> WalletInitializationResult.conflict(
                    "Wallet initialization arithmetic overflowed");
            case CONFLICT -> WalletInitializationResult.conflict(
                    "Wallet initialization receipt conflicts");
            default -> WalletInitializationResult.conflict(
                    "Wallet initialization was rejected");
        };
    }

    private static boolean runtimeReady() {
        EscrowRuntimeService runtime = EscrowRuntimeManager.getOrNull();
        return runtime != null && runtime.isReady();
    }

    private static String reason(WalletInitializationSource source) {
        return source == WalletInitializationSource.LEGACY_BALANCE
                ? LEGACY_REASON : STARTING_REASON;
    }
}
