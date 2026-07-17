package com.enviouse.futureshops.server.escrow.playershop;

import java.util.Objects;
import java.util.UUID;

public record PlayerShopItemTransfer(
        UUID transferId,
        PlayerShopAssetEndpoint source,
        PlayerShopAssetEndpoint destination,
        PlayerShopItemLot lot
) {
    public PlayerShopItemTransfer {
        transferId = PlayerShopBinarySupport.requireUuid(transferId, "item transfer id");
        source = Objects.requireNonNull(source, "source");
        destination = Objects.requireNonNull(destination, "destination");
        lot = Objects.requireNonNull(lot, "lot");
        if (source.equals(destination)) {
            throw new IllegalArgumentException("Player shop item transfer is invalid");
        }
    }
}
