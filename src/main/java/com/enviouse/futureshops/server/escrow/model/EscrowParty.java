package com.enviouse.futureshops.server.escrow.model;

import java.util.Objects;
import java.util.UUID;

public record EscrowParty(EscrowPartyType type, String id) {
    public static final int MAX_ID_LENGTH = 160;

    public EscrowParty {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
        id = id.strip();
        if (id.isEmpty() || id.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException("Invalid escrow party id");
        }
        if (type == EscrowPartyType.PLAYER) {
            id = UUID.fromString(id).toString();
        }
    }

    public static EscrowParty player(UUID playerId) {
        return new EscrowParty(EscrowPartyType.PLAYER, Objects.requireNonNull(playerId, "playerId").toString());
    }

    public static EscrowParty system(String accountId) {
        return new EscrowParty(EscrowPartyType.SYSTEM, accountId);
    }

    public static EscrowParty shop(String shopId) {
        return new EscrowParty(EscrowPartyType.SHOP, shopId);
    }

    public static EscrowParty module(String moduleId) {
        return new EscrowParty(EscrowPartyType.MODULE, moduleId);
    }
}
