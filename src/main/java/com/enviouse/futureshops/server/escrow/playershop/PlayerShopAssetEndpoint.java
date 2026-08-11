package com.enviouse.futureshops.server.escrow.playershop;

import java.util.Objects;
import java.util.UUID;

public record PlayerShopAssetEndpoint(
        Kind kind,
        UUID participantId,
        String reference
) {
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public PlayerShopAssetEndpoint {
        kind = Objects.requireNonNull(kind, "kind");
        participantId = Objects.requireNonNull(participantId, "participantId");
        reference = PlayerShopBinarySupport.requireString(reference,
                PlayerShopEscrowConstants.MAX_TEXT_LENGTH, "reference");
        if (kind.requiresParticipant() && ZERO_UUID.equals(participantId)) {
            throw new IllegalArgumentException("Player shop endpoint participant is invalid");
        }
        if (!kind.requiresParticipant() && !ZERO_UUID.equals(participantId)) {
            throw new IllegalArgumentException("Player shop system endpoint participant is invalid");
        }
    }

    public static PlayerShopAssetEndpoint participant(
            Kind kind,
            UUID participantId,
            String reference
    ) {
        return new PlayerShopAssetEndpoint(kind, participantId, reference);
    }

    public static PlayerShopAssetEndpoint system(Kind kind, String reference) {
        return new PlayerShopAssetEndpoint(kind, ZERO_UUID, reference);
    }

    public enum Kind {
        ACTOR_INVENTORY(true),
        ACTOR_WALLET(true),
        ACTOR_CASH(true),
        OWNER_WALLET(true),
        LINKED_STOCK(true),
        BARTER_STORAGE(true),
        SETTLEMENT_BALANCE(true),
        MONEY_CLAIM(true),
        ITEM_CLAIM(true),
        ADMIN_MINT(false),
        ADMIN_SINK(false);

        private final boolean requiresParticipant;

        Kind(boolean requiresParticipant) {
            this.requiresParticipant = requiresParticipant;
        }

        public boolean requiresParticipant() {
            return requiresParticipant;
        }
    }
}
