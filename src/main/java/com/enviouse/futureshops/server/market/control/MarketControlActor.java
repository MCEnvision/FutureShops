package com.enviouse.futureshops.server.market.control;

import java.util.Objects;
import java.util.UUID;

public record MarketControlActor(UUID actorId, String label) {
    public static final int MAX_LABEL_BYTES = 64;
    public static final UUID SYSTEM_ACTOR_ID = new UUID(0L, 0L);

    public MarketControlActor {
        actorId = Objects.requireNonNull(actorId, "actorId");
        label = MarketControlText.require(label, "actor label",
                MAX_LABEL_BYTES);
    }

    public static MarketControlActor system() {
        return new MarketControlActor(SYSTEM_ACTOR_ID, "FutureShops");
    }
}
