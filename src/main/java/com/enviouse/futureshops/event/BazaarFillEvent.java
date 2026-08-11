package com.enviouse.futureshops.event;

import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

/**
 * Fired AFTER a durable Bazaar commit that produced a fill (plan §18 Phase 6 API events).
 * Post-commit only and not cancellable — the fill is already settled in escrow; listeners
 * observe, they never participate (plan §15: a throwing listener cannot reach the value path).
 */
public class BazaarFillEvent extends Event {

    private final UUID fillId;
    private final String productId;
    private final long productVersion;
    private final int quantity;
    private final long priceMinor;
    private final UUID buyOrderId;
    private final UUID sellOrderId;

    public BazaarFillEvent(UUID fillId, String productId, long productVersion, int quantity,
                           long priceMinor, UUID buyOrderId, UUID sellOrderId) {
        this.fillId = fillId;
        this.productId = productId;
        this.productVersion = productVersion;
        this.quantity = quantity;
        this.priceMinor = priceMinor;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
    }

    public UUID getFillId() { return fillId; }
    public String getProductId() { return productId; }
    public long getProductVersion() { return productVersion; }
    public int getQuantity() { return quantity; }
    public long getPriceMinor() { return priceMinor; }
    public UUID getBuyOrderId() { return buyOrderId; }
    public UUID getSellOrderId() { return sellOrderId; }
}
