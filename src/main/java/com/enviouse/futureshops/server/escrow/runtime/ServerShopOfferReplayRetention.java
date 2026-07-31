package com.enviouse.futureshops.server.escrow.runtime;

import net.minecraft.server.MinecraftServer;

import java.util.UUID;

public final class ServerShopOfferReplayRetention {
    private ServerShopOfferReplayRetention() {
    }

    public static synchronized boolean ensureSingleCapacity(
            MinecraftServer server,
            UUID requestId,
            UUID playerId
    ) {
        java.util.Objects.requireNonNull(server, "server");
        java.util.Objects.requireNonNull(requestId, "requestId");
        EscrowRuntimeManager.requireServerThread(server);
        ServerShopOfferCommitSavedData commits =
                ServerShopOfferCommitSavedData.get(server);
        ServerShopOfferPreparedSavedData prepared =
                ServerShopOfferPreparedSavedData.get(server);
        ServerShopOfferReplayLedger ledger =
                ServerShopOfferReplayLedger.get(server);
        synchronized (commits) {
            while (!commits.canCommit(requestId)) {
                if (commits.compactOldestReplay().isEmpty()
                        && commits.compactOldestReplay(
                        ledger).isEmpty()) {
                    return false;
                }
            }
        }
        synchronized (prepared) {
            while (!prepared.canPrepare(requestId)) {
                if (prepared.compactOldestReplay().isEmpty()
                        && prepared.compactOldestReplay(
                        ledger).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    public static synchronized boolean ensureCartCapacity(
            MinecraftServer server,
            UUID requestId,
            UUID playerId
    ) {
        java.util.Objects.requireNonNull(server, "server");
        java.util.Objects.requireNonNull(requestId, "requestId");
        EscrowRuntimeManager.requireServerThread(server);
        ServerShopOfferCartCommitSavedData commits =
                ServerShopOfferCartCommitSavedData.get(server);
        ServerShopOfferCartPreparedSavedData prepared =
                ServerShopOfferCartPreparedSavedData.get(server);
        ServerShopOfferReplayLedger ledger =
                ServerShopOfferReplayLedger.get(server);
        synchronized (commits) {
            while (!commits.canCommit(requestId)) {
                if (commits.compactOldestReplay().isEmpty()
                        && commits.compactOldestReplay(
                        ledger).isEmpty()) {
                    return false;
                }
            }
        }
        synchronized (prepared) {
            while (!prepared.canPrepare(requestId)) {
                if (prepared.compactOldestReplay().isEmpty()
                        && prepared.compactOldestReplay(
                        ledger).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }
}
