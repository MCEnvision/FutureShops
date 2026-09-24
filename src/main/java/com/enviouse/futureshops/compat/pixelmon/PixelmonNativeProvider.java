package com.enviouse.futureshops.compat.pixelmon;

import com.enviouse.futureshops.api.economy.AccountRef;
import com.enviouse.futureshops.api.economy.BalanceSnapshot;
import com.enviouse.futureshops.api.economy.BindingV1;
import com.enviouse.futureshops.api.economy.CurrencyMetadata;
import com.enviouse.futureshops.api.economy.CurrencyV1;
import com.enviouse.futureshops.api.economy.EconomyApi;
import com.enviouse.futureshops.api.economy.EconomyCapability;
import com.enviouse.futureshops.api.economy.EconomyProvider;
import com.enviouse.futureshops.api.economy.MutationKind;
import com.enviouse.futureshops.api.economy.MutationReceipt;
import com.enviouse.futureshops.api.economy.MutationRequest;
import com.enviouse.futureshops.api.economy.ProviderCapabilities;
import com.enviouse.futureshops.api.economy.ProviderError;
import com.enviouse.futureshops.api.economy.ProviderLifecycle;
import com.enviouse.futureshops.api.economy.ProviderReadiness;
import com.enviouse.futureshops.api.economy.ProviderResult;
import com.enviouse.futureshops.api.economy.RequestId;
import com.enviouse.futureshops.api.economy.RootId;
import com.enviouse.futureshops.server.escrow.coordinator.BoundLeg;
import com.enviouse.futureshops.server.escrow.coordinator.CustodyPlan;
import com.enviouse.futureshops.server.escrow.coordinator.DispatchReceipt;
import com.enviouse.futureshops.server.escrow.coordinator.DispatchResult;
import com.enviouse.futureshops.server.escrow.coordinator.DurableEconomyCoordinator;
import com.enviouse.futureshops.server.escrow.coordinator.DurableEconomyEffect;
import com.enviouse.futureshops.server.escrow.coordinator.PreparedRoot;
import com.enviouse.futureshops.server.escrow.coordinator.RouteContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/** Exact Pixelmon 9.2.3 provider routed through the durable coordinator. */
public final class PixelmonNativeProvider implements EconomyProvider {
    public static final String PROVIDER_ID = EconomyApi.PIXELMON_PROVIDER_ID;
    private static final CurrencyMetadata CURRENCY =
            new CurrencyMetadata("PokéDollar", "PokéDollars", 0);
    private static final ProviderCapabilities CAPABILITIES =
            ProviderCapabilities.all();

    private final MinecraftServer server;
    private final DurableEconomyCoordinator coordinator;
    private final ProviderReadiness readiness;

    public PixelmonNativeProvider(MinecraftServer server) {
        this.server = server;
        DurableEconomyCoordinator opened = null;
        ProviderReadiness result;
        try {
            Path journal = server.getWorldPath(LevelResource.ROOT)
                    .resolve("data/futureshops/pixelmon-economy.wal");
            opened = DurableEconomyCoordinator.open(journal, new NativeEffect());
            result = new ProviderReadiness(ProviderLifecycle.READY, "");
        } catch (IOException | RuntimeException exception) {
            result = new ProviderReadiness(ProviderLifecycle.FAILED,
                    "pixelmon durable coordinator is unavailable");
        } catch (Exception exception) {
            result = new ProviderReadiness(ProviderLifecycle.FAILED,
                    "pixelmon durable coordinator failed to initialize");
        }
        coordinator = opened;
        readiness = result;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public int compatibilityVersion() {
        return EconomyApi.COMPATIBILITY_VERSION;
    }

    @Override
    public CurrencyMetadata currency() {
        return CURRENCY;
    }

    @Override
    public ProviderCapabilities capabilities() {
        return readiness.ready() ? CAPABILITIES : ProviderCapabilities.none();
    }

    @Override
    public ProviderReadiness readiness() {
        return readiness;
    }

    @Override
    public ProviderResult<BalanceSnapshot> balance(UUID playerId) {
        PixelmonNativeEconomyAccess account = PixelmonNativeGate.account(playerId);
        return account == null
                ? ProviderResult.unavailable(ProviderError.NOT_READY,
                "pixelmon account is not loaded")
                : account.futureshops$balance();
    }

    @Override
    public ProviderResult<BalanceSnapshot> precheck(MutationRequest request) {
        if (request == null) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST,
                    "mutation request is required");
        }
        ProviderResult<BalanceSnapshot> balance = balance(request.actor());
        if (!balance.confirmed()) {
            return balance;
        }
        if (requiresFunds(request.kind())
                && balance.value().orElseThrow().balanceMinorUnits() < request.amountMinorUnits()) {
            return ProviderResult.rejected(ProviderError.INSUFFICIENT_FUNDS,
                    "pixelmon account has insufficient PokéDollars");
        }
        return balance;
    }

    @Override
    public ProviderResult<MutationReceipt> withdraw(MutationRequest request) {
        return mutate(request, true);
    }

    @Override
    public ProviderResult<MutationReceipt> deposit(MutationRequest request) {
        return mutate(request, false);
    }

    @Override
    public ProviderResult<MutationReceipt> lookup(RequestId requestId) {
        if (requestId == null) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST,
                    "receipt request is required");
        }
        for (PixelmonNativeEconomyAccess account : PixelmonNativeGate.accounts()) {
            ProviderResult<MutationReceipt> result = account.futureshops$lookup(requestId);
            if (result.confirmed() || result.status() == com.enviouse.futureshops.api.economy.ProviderResultStatus.RECOVERY_REQUIRED) {
                return result;
            }
        }
        return ProviderResult.rejected(ProviderError.RECEIPT_NOT_FOUND,
                "pixelmon receipt was not found");
    }

    @Override
    public ProviderResult<MutationReceipt> retry(MutationRequest request) {
        return mutate(request, request != null && request.kind() == MutationKind.WITHDRAW);
    }

    @Override
    public BindingV1 bind(AccountRef account, CurrencyV1 currency) {
        if (account == null || currency == null || !currency.id().equals("poke_dollars")) {
            throw new IllegalArgumentException("pixelmon binding identity is invalid");
        }
        return binding(account.accountUuid());
    }

    private ProviderResult<MutationReceipt> mutate(MutationRequest request, boolean debit) {
        if (request == null || request.kind() == null || request.amountMinorUnits() <= 0L) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST,
                    "pixelmon mutation request is invalid");
        }
        if (!readiness.ready() || coordinator == null) {
            return ProviderResult.unavailable(ProviderError.NOT_READY,
                    readiness.diagnostic());
        }
        PixelmonNativeEconomyAccess account = PixelmonNativeGate.account(request.actor());
        if (account == null) {
            return ProviderResult.unavailable(ProviderError.NOT_READY,
                    "pixelmon account is not loaded");
        }
        BindingV1 binding = binding(request.actor());
        RootId root = new RootId(request.requestId().value());
        com.enviouse.futureshops.api.economy.LegId legId = new com.enviouse.futureshops.api.economy.LegId(request.requestId().value());
        String operation = request.kind().name().toLowerCase(java.util.Locale.ROOT);
        String fingerprint = fingerprint(request, binding);
        BoundLeg leg = new BoundLeg(root, legId, binding, operation,
                request.amountMinorUnits(), fingerprint, debit,
                "pixelmon-native", System.currentTimeMillis(), null);
        try {
            RouteContext route = new RouteContext("pixelmon-native", request.actor(),
                    binding, true, true);
            PreparedRoot prepared = coordinator.admit(route, root,
                    java.util.List.of(leg), new CustodyPlan(
                            "pixelmon:" + request.requestId().value(), request.amountMinorUnits()));
            DurableEconomyCoordinator.SettlementOutcome outcome = coordinator.execute(prepared);
            if (outcome.status() == DurableEconomyCoordinator.SettlementOutcome.Status.CONFIRMED) {
                return account.futureshops$lookup(request.requestId());
            }
            if (outcome.status() == DurableEconomyCoordinator.SettlementOutcome.Status.REJECTED) {
                return ProviderResult.rejected(ProviderError.PROVIDER_EXCEPTION,
                        "pixelmon mutation was rejected");
            }
            return ProviderResult.recoveryRequired("pixelmon mutation requires recovery");
        } catch (Exception exception) {
            return ProviderResult.rejected(ProviderError.PROVIDER_EXCEPTION,
                    "pixelmon coordinator refused the mutation");
        }
    }

    private BindingV1 binding(UUID account) {
        return PixelmonNativeGate.bindingFor(account);
    }

    private static boolean requiresFunds(MutationKind kind) {
        return kind == MutationKind.WITHDRAW || kind == MutationKind.TRANSFER_DEBIT
                || kind == MutationKind.FEE;
    }

    private static String fingerprint(MutationRequest request, BindingV1 binding) {
        return sha256(request.requestId().value() + "|" + request.actor() + "|"
                + request.amountMinorUnits() + "|" + request.kind() + "|"
                + binding.backendClassFingerprint());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("sha256 is unavailable", exception);
        }
    }

    private final class NativeEffect implements DurableEconomyEffect {
        @Override
        public DispatchResult dispatch(BoundLeg leg) {
            PixelmonNativeEconomyAccess account = PixelmonNativeGate.account(leg.binding().accountUuid());
            if (account == null) {
                return DispatchResult.unknown();
            }
            ProviderResult<MutationReceipt> result = account.futureshops$mutate(
                    leg.legId().requestId(),
                    leg.debit() ? MutationKind.WITHDRAW : MutationKind.DEPOSIT,
                    leg.minorUnits(), leg.payloadFingerprint());
            if (!result.confirmed()) {
                return result.status() == com.enviouse.futureshops.api.economy.ProviderResultStatus.REJECTED
                        ? DispatchResult.rejected() : DispatchResult.unknown();
            }
            MutationReceipt receipt = result.value().orElseThrow();
            return DispatchResult.confirmed(new DispatchReceipt(receipt.externalOperationId(),
                    receipt.amountMinorUnits(), leg.payloadFingerprint()));
        }

        @Override
        public Optional<DispatchReceipt> lookup(BoundLeg leg) {
            PixelmonNativeEconomyAccess account = PixelmonNativeGate.account(leg.binding().accountUuid());
            if (account == null) {
                return Optional.empty();
            }
            return account.futureshops$lookup(leg.legId().requestId()).value()
                    .map(receipt -> new DispatchReceipt(receipt.externalOperationId(),
                            receipt.amountMinorUnits(), leg.payloadFingerprint()));
        }
    }
}
