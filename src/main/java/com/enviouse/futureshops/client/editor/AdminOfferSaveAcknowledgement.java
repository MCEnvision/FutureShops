package com.enviouse.futureshops.client.editor;

import com.enviouse.futureshops.catalog.AdminShopOfferConfigWriter;
import com.enviouse.futureshops.network.packets
        .S2CAdminOfferSaveResultPacket;

import java.util.UUID;

public final class AdminOfferSaveAcknowledgement {
    private AdminOfferSaveAcknowledgement() {
    }

    public static Decision decide(
            UUID pendingRequestId,
            AdminShopOfferConfigWriter.Operation operation,
            boolean closeAfterSave,
            S2CAdminOfferSaveResultPacket result
    ) {
        if (pendingRequestId == null
                || !pendingRequestId.equals(result.requestId())) {
            return Decision.IGNORED;
        }
        if (!result.success()) {
            return result.status()
                    == AdminShopOfferConfigWriter.Status.STALE
                    ? Decision.STALE : Decision.REJECTED;
        }
        if (operation == AdminShopOfferConfigWriter.Operation.REMOVE) {
            return Decision.REMOVED;
        }
        if (result.snapshot().isEmpty()) {
            return Decision.REJECTED;
        }
        return closeAfterSave
                ? Decision.ACKNOWLEDGED_CLOSE
                : Decision.ACKNOWLEDGED_KEEP_OPEN;
    }

    public enum Decision {
        IGNORED,
        REJECTED,
        STALE,
        ACKNOWLEDGED_KEEP_OPEN,
        ACKNOWLEDGED_CLOSE,
        REMOVED
    }
}
