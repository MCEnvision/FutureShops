package com.enviouse.futureshops.server.escrow.custody;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class CustodyTestFixtures {
    static final Instant NOW = Instant.parse("2026-07-16T12:00:00Z");

    private CustodyTestFixtures() {
    }

    static CustodyLot itemLot(String requestKey, int count) {
        CustodyItemSnapshot snapshot = CustodyItemSnapshot.capture("minecraft:diamond", count,
                new byte[]{10, 0, 1, 2, 3, 4});
        CustodyTransferEvidence evidence = evidence("player_inventory",
                CustodyAdapterCapability.RECONCILABLE, requestKey);
        return CustodyLot.held(UUID.randomUUID(), UUID.randomUUID(), requestKey,
                CustodyAssetType.ITEM_STACK, CustodyProtectionTier.RECONCILED, count, "",
                List.of(snapshot), List.of(), evidence, NOW);
    }

    static CustodyLot walletLot(String requestKey, long units) {
        CustodyTransferEvidence evidence = evidence("wallet",
                CustodyAdapterCapability.TRANSACTIONAL_PROTECTED, requestKey);
        return CustodyLot.held(UUID.randomUUID(), UUID.randomUUID(), requestKey,
                CustodyAssetType.WALLET_RESERVE, CustodyProtectionTier.PROTECTED, units,
                CustodyLot.BUILT_IN_CURRENCY_PROVIDER, List.of(), List.of(), evidence, NOW);
    }

    static CustodyLot protectedCurrencyLot(String requestKey, long denomination, int billCount) {
        CustodyItemSnapshot snapshot = CustodyItemSnapshot.capture("futureshops:money", billCount,
                new byte[]{10, 0, 7, 8, 9});
        List<ProtectedCurrencyProvenance> provenance = List.of(
                new ProtectedCurrencyProvenance(UUID.randomUUID(), denomination,
                        billCount, billCount, "test server", "test checksum " + requestKey));
        CustodyTransferEvidence evidence = evidence("player_inventory",
                CustodyAdapterCapability.RECONCILABLE, requestKey);
        return CustodyLot.held(UUID.randomUUID(), UUID.randomUUID(), requestKey,
                CustodyAssetType.PROTECTED_PHYSICAL_CURRENCY, CustodyProtectionTier.PROTECTED,
                Math.multiplyExact(denomination, billCount), CustodyLot.BUILT_IN_CURRENCY_PROVIDER,
                List.of(snapshot), provenance, evidence, NOW);
    }

    static CustodyLot foreignCurrencyLot(String requestKey, long units, int itemCount) {
        CustodyItemSnapshot snapshot = CustodyItemSnapshot.capture("coinmod:coin", itemCount,
                new byte[]{10, 0, 55, 66});
        CustodyTransferEvidence evidence = evidence("coinmod_inventory",
                CustodyAdapterCapability.UNPROTECTED_EXTERNAL, requestKey);
        return CustodyLot.held(UUID.randomUUID(), UUID.randomUUID(), requestKey,
                CustodyAssetType.FOREIGN_PHYSICAL_CURRENCY,
                CustodyProtectionTier.UNPROTECTED_FOREIGN, units, "coinmod:coin",
                List.of(snapshot), List.of(), evidence, NOW);
    }

    static CustodyTransferEvidence evidence(String sourceAdapter,
                                            CustodyAdapterCapability sourceCapability,
                                            String token) {
        CustodyEndpointEvidence source = CustodyEndpointEvidence.captured(sourceAdapter,
                sourceCapability, "player", "inventory", new byte[]{1}, new byte[]{2},
                token + " source");
        CustodyEndpointEvidence destination = CustodyEndpointEvidence.captured("escrow_vault",
                CustodyAdapterCapability.TRANSACTIONAL_PROTECTED, "escrow", "vault",
                new byte[]{3}, new byte[]{4}, token + " destination");
        return new CustodyTransferEvidence(source, destination);
    }

    static CustodyTransferEvidence terminalEvidence(String token) {
        CustodyEndpointEvidence source = CustodyEndpointEvidence.captured("escrow_vault",
                CustodyAdapterCapability.TRANSACTIONAL_PROTECTED, "escrow", "vault",
                new byte[]{4}, new byte[]{5}, token + " source");
        CustodyEndpointEvidence destination = CustodyEndpointEvidence.captured("player_inventory",
                CustodyAdapterCapability.RECONCILABLE, "player", "inventory",
                new byte[]{2}, new byte[]{6}, token + " destination");
        return new CustodyTransferEvidence(source, destination);
    }
}
