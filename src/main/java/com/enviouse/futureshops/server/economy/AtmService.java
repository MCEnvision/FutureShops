package com.enviouse.futureshops.server.economy;

import com.enviouse.futureshops.data.AtmDenominationData;
import com.enviouse.futureshops.money.AtmCurrencyCatalog;
import com.enviouse.futureshops.money.AtmCurrencyRoute;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.S2CAtmDataPacket;
import com.enviouse.futureshops.network.packets.S2CAtmResultPacket;
import com.enviouse.futureshops.network.packets.S2CAtmCollectCashResultPacket;
import com.enviouse.futureshops.network.packets.C2SAtmDepositPacket;
import com.enviouse.futureshops.network.packets.S2CAtmDepositResultPacket;
import com.enviouse.futureshops.server.escrow.runtime.AtmAccessSnapshot;
import com.enviouse.futureshops.server.escrow.runtime.AtmWithdrawalOutcome;
import com.enviouse.futureshops.server.escrow.runtime.AtmWithdrawalStatus;
import com.enviouse.futureshops.server.escrow.runtime.EscrowAtmWithdrawalService;
import com.enviouse.futureshops.server.escrow.runtime.EscrowCashDepositService;
import com.enviouse.futureshops.server.security.ServerRequestAction;
import com.enviouse.futureshops.server.security.ServerRequestSecurityManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;

public final class AtmService {
    private AtmService() {
    }

    public static void requestData(ServerPlayer player, boolean openScreen) {
        ServerRequestSecurityManager.GateDecision gate =
                ServerRequestSecurityManager.tryAcquire(
                        player, ServerRequestAction.ATM_DATA);
        if (!gate.allowed()) {
            sendDataRejection(player, gate);
            return;
        }

        sendAuthoritativeData(player, openScreen);
    }

    private static void sendAuthoritativeData(
            ServerPlayer player,
            boolean openScreen
    ) {
        AtmAccessSnapshot access = EscrowAtmWithdrawalService.access(player);
        AtmCurrencyCatalog catalog = access.catalog();
        List<AtmDenominationData> denominations = catalog.denominations()
                .stream()
                .map(value -> new AtmDenominationData(
                        value.itemId(), value.valueMinorUnits(),
                        value.maximumStackSize()))
                .toList();
        AtmCashClaimCenter.CashClaimSummary cashClaims =
                AtmCashClaimCenter.summary(player);
        ShopPackets.sendToPlayer(player, new S2CAtmDataPacket(
                access.balanceMinorUnits(), access.balanceKnown(),
                catalog.currencyName(), catalog.decimalPlaces(),
                catalog.providerId(), catalog.route().name(),
                catalog.route()
                        == AtmCurrencyRoute.PROTECTED_ESCROW,
                catalog.signature(), denominations,
                access.serviceAvailable(), access.availabilityCode(),
                openScreen, cashClaims.pendingClaimCount(),
                cashClaims.collectibleClaims()));
    }

    public static void withdraw(ServerPlayer player, UUID requestId,
                                String signature, List<Integer> counts) {
        AtmWithdrawalOutcome result = withdrawThroughGate(
                player, requestId, signature,
                () -> EscrowAtmWithdrawalService.withdraw(
                        player, requestId, signature, counts));
        ShopPackets.sendToPlayer(player, new S2CAtmResultPacket(
                result.requestId(), result.status().name(),
                result.retryable(), result.replayed(),
                result.balanceKnown(), result.balanceMinorUnits(),
                result.amountMinorUnits(), result.deliveredBillCount(),
                result.claimedBillCount(), result.currencySignature(),
                result.retryAfterMillis()));
        if (result.status().success()
                || result.status() == AtmWithdrawalStatus.CURRENCY_CHANGED) {
            refreshAfterMutation(player);
        }
    }

    public static AtmWithdrawalOutcome withdrawAutomatic(
            ServerPlayer player,
            UUID requestId,
            long amountMinorUnits,
            boolean multipleBills,
            String signature,
            List<Integer> counts
    ) {
        AtmWithdrawalOutcome result = withdrawThroughGate(
                player, requestId, signature,
                () -> EscrowAtmWithdrawalService.withdrawAutomatic(
                        player, requestId, amountMinorUnits,
                        multipleBills, signature, counts));
        if (result.status().success()) {
            refreshAfterMutation(player);
        }
        return result;
    }

    public static void collectCash(ServerPlayer player,
                                   UUID requestId,
                                   List<UUID> claimIds) {
        ServerRequestSecurityManager.GateDecision gate =
                ServerRequestSecurityManager.tryAcquire(
                        player, ServerRequestAction.ATM_CASH_COLLECTION);
        if (!gate.allowed()) {
            sendCashCollectionRejection(player, requestId, gate);
            return;
        }

        S2CAtmCollectCashResultPacket result =
                AtmCashClaimCenter.collect(player, requestId, claimIds);
        ShopPackets.sendToPlayer(player, result);
        refreshAfterMutation(player);
    }

    public static void deposit(
            ServerPlayer player,
            UUID requestId,
            String currencySignature,
            C2SAtmDepositPacket.Source source,
            OptionalLong requestedMinorUnits
    ) {
        S2CAtmDepositResultPacket packet;
        try {
            EscrowCashDepositService.DepositRequest request =
                    new EscrowCashDepositService.DepositRequest(
                            requestId,
                            currencySignature,
                            EscrowCashDepositService.Source
                                    .valueOf(source.name()),
                            requestedMinorUnits);
            packet = depositResultPacket(
                    EscrowCashDepositService.deposit(player, request));
        } catch (RuntimeException exception) {
            packet = depositFailurePacket(requestId, "SERVER_ERROR");
        }
        ShopPackets.sendToPlayer(player, packet);
        if (packet.success()
                || packet.status().equals("CONFIG_CHANGED")
                || packet.status().equals("CANCELLED")) {
            refreshAfterMutation(player);
        }
    }

    private static void sendDataRejection(
            ServerPlayer player,
            ServerRequestSecurityManager.GateDecision gate
    ) {
        if (gate.status()
                == ServerRequestSecurityManager.GateStatus.RATE_LIMITED) {
            player.sendSystemMessage(Component.translatable(
                    "message.futureshops.atm.data_rate_limited",
                    retrySeconds(gate)));
            return;
        }
        player.sendSystemMessage(Component.translatable(
                "message.futureshops.atm.security_unavailable"));
    }

    private static AtmWithdrawalOutcome withdrawThroughGate(
            ServerPlayer player,
            UUID requestId,
            String signature,
            Supplier<AtmWithdrawalOutcome> operation
    ) {
        ServerRequestSecurityManager.GateDecision gate =
                ServerRequestSecurityManager.tryAcquire(
                        player, ServerRequestAction.ATM_WITHDRAWAL);
        if (gate.allowed()) {
            return operation.get();
        }
        AtmWithdrawalStatus status = gate.status()
                == ServerRequestSecurityManager.GateStatus.RATE_LIMITED
                ? AtmWithdrawalStatus.RATE_LIMITED
                : AtmWithdrawalStatus.ESCROW_UNAVAILABLE;
        return AtmWithdrawalOutcome.failure(
                requestId, status,
                true, false, false, 0L, 0L, 0, signature,
                status == AtmWithdrawalStatus.RATE_LIMITED
                        ? retryAfterMillis(gate) : 0L);
    }

    private static void sendCashCollectionRejection(
            ServerPlayer player,
            UUID requestId,
            ServerRequestSecurityManager.GateDecision gate
    ) {
        String status = gate.status()
                == ServerRequestSecurityManager.GateStatus.RATE_LIMITED
                ? "RATE_LIMITED" : "UNAVAILABLE";
        long retryAfterMillis = gate.status()
                == ServerRequestSecurityManager.GateStatus.RATE_LIMITED
                ? retryAfterMillis(gate) : 0L;
        ShopPackets.sendToPlayer(player,
                new S2CAtmCollectCashResultPacket(
                        requestId, status, true, false,
                        0, AtmCashClaimCenter.pendingClaimCount(player),
                        List.of(), retryAfterMillis));
    }

    private static S2CAtmDepositResultPacket depositResultPacket(
            EscrowCashDepositService.DepositResult result
    ) {
        Optional<S2CAtmDepositResultPacket.LegacyMigrationSummary> legacy =
                result.legacyMigration().map(value ->
                        new S2CAtmDepositResultPacket
                                .LegacyMigrationSummary(
                                value.availableMinorUnits(),
                                value.billCount(), value.bills().size()));
        String status = result.status().name();
        boolean success = status.equals("SUCCESS");
        return new S2CAtmDepositResultPacket(
                result.requestId(), status, result.retryable(),
                result.replayed(),
                result.transactionId(), result.depositedMinorUnits(),
                result.itemsConsumed(), result.walletCreditMinorUnits(),
                result.overflowClaimMinorUnits(), success,
                success ? result.resultingBalanceMinorUnits() : 0L,
                result.cleanupPending(), legacy,
                result.retryAfterMillis());
    }

    private static S2CAtmDepositResultPacket depositFailurePacket(
            UUID requestId,
            String status
    ) {
        return new S2CAtmDepositResultPacket(
                requestId, status, true, false, Optional.empty(),
                0L, 0, 0L, 0L, false, 0L, false,
                Optional.empty(), 0L);
    }

    private static long retrySeconds(
            ServerRequestSecurityManager.GateDecision gate
    ) {
        long nanos = gate.retryAfter().toNanos();
        if (nanos <= 0L) {
            return 1L;
        }
        return Math.addExact(
                Math.subtractExact(nanos, 1L) / 1_000_000_000L,
                1L);
    }

    private static void refreshAfterMutation(ServerPlayer player) {
        try {
            sendAuthoritativeData(player, false);
        } catch (RuntimeException ignored) {
        }
    }

    private static long retryAfterMillis(
            ServerRequestSecurityManager.GateDecision gate
    ) {
        long nanos = gate.retryAfter().toNanos();
        if (nanos <= 0L) {
            return 1L;
        }
        long millis = Math.addExact(
                Math.subtractExact(nanos, 1L) / 1_000_000L,
                1L);
        long maximum = Math.min(
                S2CAtmDepositResultPacket.MAX_RETRY_AFTER_MILLIS,
                S2CAtmResultPacket.MAX_RETRY_AFTER_MILLIS);
        maximum = Math.min(maximum,
                S2CAtmCollectCashResultPacket.MAX_RETRY_AFTER_MILLIS);
        return Math.min(maximum, millis);
    }
}
