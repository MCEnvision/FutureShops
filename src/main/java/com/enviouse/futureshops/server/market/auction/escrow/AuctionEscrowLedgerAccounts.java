package com.enviouse.futureshops.server.market.auction.escrow;

import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;

import java.util.UUID;

public final class AuctionEscrowLedgerAccounts {
    private AuctionEscrowLedgerAccounts() {
    }

    public static LedgerAccountId wallet(UUID playerId) {
        return new LedgerAccountId(LedgerAccountType.PLAYER_WALLET,
                AuctionEscrowIds.requireId(playerId, "playerId")
                        .toString());
    }

    public static LedgerAccountId debt(UUID playerId) {
        return new LedgerAccountId(LedgerAccountType.PLAYER_DEBT,
                AuctionEscrowIds.requireId(playerId, "playerId")
                        .toString());
    }

    public static LedgerAccountId hold(UUID holdAccountId) {
        return new LedgerAccountId(LedgerAccountType.TRANSACTION_ESCROW,
                AuctionEscrowIds.requireId(holdAccountId,
                        "holdAccountId").toString());
    }

    public static LedgerAccountId claim(UUID claimId) {
        return new LedgerAccountId(LedgerAccountType.PLAYER_CLAIM,
                AuctionEscrowIds.requireId(claimId, "claimId")
                        .toString());
    }

    public static LedgerAccountId fee() {
        return new LedgerAccountId(LedgerAccountType.AUCTION_FEE,
                "auction");
    }

    public static LedgerAccountId treasury() {
        return new LedgerAccountId(LedgerAccountType.SERVER_TREASURY,
                "auction");
    }
}
