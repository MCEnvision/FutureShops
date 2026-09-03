package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.Config;
import com.enviouse.futureshopsp.api.economy.EconomyApi;
import com.enviouse.futureshopsp.api.economy.EconomyProviderContext;
import com.enviouse.futureshopsp.api.economy.EconomyProviderRegistry;
import com.enviouse.futureshopsp.api.economy.BalanceSnapshot;
import com.enviouse.futureshopsp.api.economy.MutationKind;
import com.enviouse.futureshopsp.api.economy.MutationRequest;
import com.enviouse.futureshopsp.api.economy.ProviderError;
import com.enviouse.futureshopsp.api.economy.ProviderResult;
import com.enviouse.futureshopsp.api.economy.ProviderResolution;
import com.enviouse.futureshopsp.api.economy.ProviderLifecycle;
import com.enviouse.futureshopsp.api.economy.RequestId;
import com.enviouse.futureshopsp.server.shop.PlayerShopBarterEscrowSavedData;
import com.enviouse.futureshopsp.server.shop.PlayerShopSaleEscrowSavedData;
import com.enviouse.futureshopsp.server.shop.PlayerShopSettlementSavedData;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.UUID;

public final class BalanceManager {
    private static EconomyProvider provider;
    private static EconomyTransactionCoordinator coordinator;
    private static EconomyLifecycleController lifecycleController;
    private static EconomyTransactionJournal journal;
    private static EconomyCustodyStore custody;
    private static EconomyClaimStore claims;
    private static InternalEconomyReceiptStore receipts;
    private static PlayerShopBarterEscrowSavedData barterEscrow;
    private static PlayerShopSaleEscrowSavedData saleEscrow;
    private static PlayerShopSettlementSavedData settlements;

    private BalanceManager() {
    }

    public static void initialize(MinecraftServer server) {
        EconomyProviderRegistry.freeze();
        ProviderSelectionSnapshot selection = ProviderSelectionManager.resolveAtStartup(Config.economyProviderId);
        boolean ephemeral = server.overworld() == null;
        journal = ephemeral ? new InMemoryEconomyTransactionJournal() : EconomyJournalSavedData.get(server);
        custody = ephemeral ? new InMemoryEconomyCustodyStore() : EconomyCustodySavedData.get(server);
        claims = ephemeral ? new InMemoryEconomyClaimStore() : EconomyClaimSavedData.get(server);
        receipts = ephemeral ? new InMemoryInternalEconomyReceiptStore() : InternalEconomyReceiptSavedData.get(server);
        barterEscrow = ephemeral ? new PlayerShopBarterEscrowSavedData() : PlayerShopBarterEscrowSavedData.get(server);
        saleEscrow = ephemeral ? new PlayerShopSaleEscrowSavedData() : PlayerShopSaleEscrowSavedData.get(server);
        settlements = ephemeral ? new PlayerShopSettlementSavedData() : PlayerShopSettlementSavedData.get(server);
        boolean internalSelection = EconomyApi.INTERNAL_PROVIDER_ID.equals(selection.activeProviderId());
        boolean cleanMarkerValid = journal.cleanMarkerValid() && custody.cleanMarkerValid() && claims.cleanMarkerValid()
                && barterEscrow.cleanMarkerValid() && saleEscrow.cleanMarkerValid() && settlements.cleanMarkerValid();
        if (internalSelection) {
            cleanMarkerValid &= receipts.cleanMarkerValid();
        }
        boolean integrityValid = journal.integrityValid() && custody.integrityValid() && claims.integrityValid()
                && barterEscrow.integrityValid() && saleEscrow.integrityValid() && settlements.integrityValid()
                && (!internalSelection || receipts.integrityValid());
        boolean hasIncompleteRecords = journal.hasIncompleteRecords() || custody.hasIncompleteRecords()
                || claims.hasIncompleteRecords() || barterEscrow.hasIncompleteRecords() || saleEscrow.hasIncompleteRecords();
        journal.markUnclean();
        custody.markUnclean();
        claims.markUnclean();
        barterEscrow.markUnclean();
        saleEscrow.markUnclean();
        settlements.markUnclean();
        if (internalSelection) {
            receipts.markUnclean();
        }
        lifecycleController = new EconomyLifecycleController(selection.activeProviderId());
        if (EconomyApi.INTERNAL_PROVIDER_ID.equals(selection.activeProviderId())) {
            InternalEconomyProvider legacy = new InternalEconomyProvider(server);
            com.enviouse.futureshopsp.api.economy.EconomyProvider publicProvider =
                    new PublicInternalEconomyProvider(legacy, receipts);
            lifecycleController.resolve(ProviderLifecycle.READY, "", cleanMarkerValid, integrityValid,
                    hasIncompleteRecords);
            coordinator = new EconomyTransactionCoordinator(publicProvider, lifecycleController, journal, custody, claims);
            recoverIncompleteJournalRecords();
            provider = new CoordinatedEconomyProvider(publicProvider, coordinator, legacy);
            return;
        }
        ProviderResolution resolution = EconomyProviderRegistry.resolve(
                selection.activeProviderId(), new EconomyProviderContext(server));
        if (resolution.provider().isPresent() && resolution.lifecycle() == ProviderLifecycle.READY) {
            com.enviouse.futureshopsp.api.economy.EconomyProvider publicProvider = resolution.provider().orElseThrow();
            lifecycleController.resolve(resolution.lifecycle(), resolution.diagnostic(), cleanMarkerValid, integrityValid,
                    hasIncompleteRecords);
            coordinator = new EconomyTransactionCoordinator(publicProvider, lifecycleController, journal, custody, claims);
            recoverIncompleteJournalRecords();
            provider = new ExternalLegacyEconomyProvider(publicProvider, coordinator);
        } else {
            lifecycleController.resolve(resolution.lifecycle(), resolution.diagnostic(), cleanMarkerValid, integrityValid,
                    hasIncompleteRecords);
            provider = new UnavailableEconomyProvider(selection.activeProviderId(), resolution.lifecycle(),
                    resolution.diagnostic());
        }
    }

    public static void clear() {
        if (lifecycleController != null) {
            if (lifecycleController.snapshot().lifecycle() == ProviderLifecycle.READY) {
                lifecycleController.beginDraining();
            }
            boolean journalFlushed = journal == null || journal.flush();
            boolean custodyFlushed = custody == null || custody.flush();
            boolean claimsFlushed = claims == null || claims.flush();
            boolean barterEscrowFlushed = barterEscrow == null || barterEscrow.flush();
            boolean saleEscrowFlushed = saleEscrow == null || saleEscrow.flush();
            boolean settlementsFlushed = settlements == null || settlements.flush();
            boolean receiptsFlushed = receipts == null || receipts.flush();
            if (lifecycleController.writeCleanMarkerLast(journalFlushed && receiptsFlushed,
                    custodyFlushed, claimsFlushed, barterEscrowFlushed && saleEscrowFlushed && settlementsFlushed)) {
                if (journal != null) {
                    journal.markCleanMarker();
                }
                if (custody != null) {
                    custody.markCleanMarker();
                }
                if (claims != null) {
                    claims.markCleanMarker();
                }
                if (barterEscrow != null) {
                    barterEscrow.markCleanMarker();
                }
                if (saleEscrow != null) {
                    saleEscrow.markCleanMarker();
                }
                if (settlements != null) {
                    settlements.markCleanMarker();
                }
                if (receipts != null) {
                    receipts.markCleanMarker();
                }
            }
        }
        provider = null;
        coordinator = null;
        lifecycleController = null;
        journal = null;
        custody = null;
        claims = null;
        receipts = null;
        barterEscrow = null;
        saleEscrow = null;
        settlements = null;
    }

    public static void beginDraining() {
        if (lifecycleController != null) {
            lifecycleController.beginDraining();
        }
    }

    private static void recoverIncompleteJournalRecords() {
        if (coordinator == null || journal == null) {
            return;
        }
        for (EconomyJournalRecord record : journal.snapshot()) {
            if (!record.incomplete()) {
                continue;
            }
            coordinator.recover(record.request().requestId());
            if (lifecycleController.snapshot().lifecycle() == ProviderLifecycle.FROZEN) {
                return;
            }
        }
        if (freezeIfUnresolvedItemState(lifecycleController, custody, claims, barterEscrow, saleEscrow)) {
            return;
        }
        lifecycleController.markRecovered();
    }

    static boolean freezeIfUnresolvedItemState(EconomyLifecycleController lifecycle,
                                               EconomyCustodyStore custody,
                                               EconomyClaimStore claims,
                                               PlayerShopBarterEscrowSavedData barterEscrow) {
        return freezeIfUnresolvedItemState(lifecycle, custody, claims, barterEscrow, null);
    }

    static boolean freezeIfUnresolvedItemState(EconomyLifecycleController lifecycle,
                                               EconomyCustodyStore custody,
                                               EconomyClaimStore claims,
                                               PlayerShopBarterEscrowSavedData barterEscrow,
                                               PlayerShopSaleEscrowSavedData saleEscrow) {
        if (custody != null && custody.hasIncompleteRecords()) {
            lifecycle.markAmbiguous("item custody requires operator recovery");
            return true;
        }
        if (claims != null && claims.hasIncompleteRecords()) {
            lifecycle.markAmbiguous("economy claims require operator recovery");
            return true;
        }
        if (barterEscrow != null && barterEscrow.hasIncompleteRecords()) {
            lifecycle.markAmbiguous("player shop barter escrow requires operator recovery");
            return true;
        }
        if (saleEscrow != null && saleEscrow.hasIncompleteRecords()) {
            lifecycle.markAmbiguous("player shop sale escrow requires operator recovery");
            return true;
        }
        return false;
    }

    public static long getBalance(UUID playerUUID) {
        if (provider == null) {
            throw new IllegalStateException("BalanceManager accessed before initialization.");
        }
        return provider.getBalance(playerUUID);
    }

    /** Returns a typed balance result without exposing an unavailable state as zero. */
    public static ProviderResult<BalanceSnapshot> queryBalance(UUID playerUUID) {
        if (playerUUID == null) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST, "player id is required");
        }
        if (coordinator != null) {
            return coordinator.balance(playerUUID);
        }
        EconomyLifecycleSnapshot state = lifecycleController == null
                ? EconomyLifecycleSnapshot.of("unknown", ProviderLifecycle.UNRESOLVED, "economy is not initialized")
                : lifecycleController.snapshot();
        if (state.lifecycle() == ProviderLifecycle.RECOVERING || state.lifecycle() == ProviderLifecycle.FROZEN) {
            return ProviderResult.recoveryRequired(state.diagnostic());
        }
        return ProviderResult.unavailable(ProviderError.NOT_READY,
                state.diagnostic().isBlank() ? "economy provider is not ready" : state.diagnostic());
    }

    /** Adjusts a ready internal balance through the durable coordinator. */
    public static ProviderResult<BalanceSnapshot> setInternalBalance(UUID playerUUID, long targetMinorUnits) {
        if (playerUUID == null) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST, "player id is required");
        }
        if (targetMinorUnits < 0L) {
            return ProviderResult.rejected(ProviderError.INVALID_AMOUNT, "target balance must not be negative");
        }
        if (!isInternalEconomyReady()) {
            EconomyLifecycleSnapshot state = getLifecycleSnapshotOrUnresolved();
            if (state.lifecycle() == ProviderLifecycle.RECOVERING || state.lifecycle() == ProviderLifecycle.FROZEN) {
                return ProviderResult.recoveryRequired(state.diagnostic());
            }
            return ProviderResult.unavailable(ProviderError.NOT_READY,
                    state.diagnostic().isBlank() ? "internal economy is not ready" : state.diagnostic());
        }

        ProviderResult<BalanceSnapshot> current = queryBalance(playerUUID);
        if (!current.confirmed()) {
            return current;
        }
        long delta;
        try {
            delta = Math.subtractExact(targetMinorUnits, current.value().orElseThrow().balanceMinorUnits());
        } catch (ArithmeticException exception) {
            return ProviderResult.rejected(ProviderError.INVALID_AMOUNT, "target balance is outside the supported range");
        }
        if (delta == 0L) {
            return current;
        }

        long amount = Math.abs(delta);
        MutationKind kind = delta > 0L ? MutationKind.DEPOSIT : MutationKind.WITHDRAW;
        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), playerUUID, amount, kind);
        ProviderResult<com.enviouse.futureshopsp.api.economy.MutationReceipt> mutation =
                kind == MutationKind.DEPOSIT ? coordinator.deposit(request) : coordinator.withdraw(request);
        if (!mutation.confirmed()) {
            return new ProviderResult<>(mutation.status(), mutation.error(), java.util.Optional.empty(),
                    java.util.Optional.empty(), mutation.diagnostic());
        }
        long resultingBalance = mutation.receipt().flatMap(receipt -> receipt.resultingBalanceMinorUnits().isPresent()
                ? java.util.Optional.of(receipt.resultingBalanceMinorUnits().getAsLong())
                : java.util.Optional.empty()).orElse(targetMinorUnits);
        return ProviderResult.confirmed(new BalanceSnapshot(playerUUID, resultingBalance));
    }

    public static EconomyProvider getProvider() {
        if (provider == null) {
            throw new IllegalStateException("BalanceManager accessed before initialization.");
        }
        return provider;
    }

    public static EconomyTransactionCoordinator getCoordinator() {
        if (coordinator == null) {
            throw new IllegalStateException("Economy coordinator accessed before initialization.");
        }
        return coordinator;
    }

    public static EconomyLifecycleSnapshot getLifecycleSnapshot() {
        if (lifecycleController == null) {
            throw new IllegalStateException("Economy lifecycle accessed before initialization.");
        }
        return lifecycleController.snapshot();
    }

    /** Returns lifecycle state for presentation paths that may run during startup or shutdown. */
    public static EconomyLifecycleSnapshot getLifecycleSnapshotOrUnresolved() {
        return lifecycleController == null
                ? EconomyLifecycleSnapshot.of("unknown", ProviderLifecycle.UNRESOLVED, "economy is not initialized")
                : lifecycleController.snapshot();
    }

    public static boolean isInternalEconomyReady() {
        EconomyLifecycleSnapshot state = getLifecycleSnapshotOrUnresolved();
        return EconomyApi.INTERNAL_PROVIDER_ID.equals(state.providerId())
                && state.lifecycle() == ProviderLifecycle.READY;
    }

    public static EconomyCustodyStore getCustodyStore() {
        if (custody == null) {
            throw new IllegalStateException("Economy custody accessed before initialization.");
        }
        return custody;
    }

    public static EconomyClaimStore getClaimStore() {
        if (claims == null) {
            throw new IllegalStateException("Economy claims accessed before initialization.");
        }
        return claims;
    }

    public static PlayerShopSaleEscrowSavedData getSaleEscrow() {
        if (saleEscrow == null) {
            throw new IllegalStateException("player shop sale escrow accessed before initialization.");
        }
        return saleEscrow;
    }

    public static TransactionResult transfer(UUID fromPlayerUUID, UUID toPlayerUUID, long amountMinorUnits) {
        return getProvider().transfer(fromPlayerUUID, toPlayerUUID, amountMinorUnits);
    }

    public static List<BalanceEntry> getTopBalances(int page, int pageSize) {
        return getProvider().getTopBalances(page, pageSize);
    }
}
