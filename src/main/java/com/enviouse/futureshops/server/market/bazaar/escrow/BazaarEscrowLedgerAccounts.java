package com.enviouse.futureshops.server.market.bazaar.escrow;

import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;

import java.util.UUID;

public final class BazaarEscrowLedgerAccounts {
    private BazaarEscrowLedgerAccounts() {
    }

    public static LedgerAccountId wallet(UUID ownerId) {
        return new LedgerAccountId(LedgerAccountType.PLAYER_WALLET,
                BazaarEscrowIds.requireId(ownerId, "ownerId").toString());
    }

    public static LedgerAccountId hold(UUID holdAccountId) {
        return new LedgerAccountId(LedgerAccountType.TRANSACTION_ESCROW,
                "bazaar.hold."
                        + BazaarEscrowIds.requireId(holdAccountId,
                        "holdAccountId"));
    }

    public static LedgerAccountId claim(UUID claimId) {
        return new LedgerAccountId(LedgerAccountType.PLAYER_CLAIM,
                BazaarEscrowIds.requireId(claimId, "claimId").toString());
    }

    public static LedgerAccountId fee() {
        return LedgerAccountId.system(LedgerAccountType.BAZAAR_FEE);
    }
}
