package com.enviouse.futureshops.client;

import com.enviouse.futureshops.catalog.offer.OfferAction;
import com.enviouse.futureshops.network.packets.C2SServerShopOfferPacket;
import com.enviouse.futureshops.network.packets.S2CServerShopOfferResultPacket;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ServerShopOfferResponseTracker {
    private PendingRequest pending;
    private S2CServerShopOfferResultPacket lastResult;

    public synchronized C2SServerShopOfferPacket begin(
            String shopId,
            String listingId,
            String optionId,
            OfferAction action,
            int quantity,
            long revision,
            Optional<com.enviouse.futureshops.money.PaymentSource> source
    ) {
        if (pending != null) {
            C2SServerShopOfferPacket retry = pending.packet();
            if (!retry.shopId().equals(shopId)
                    || !retry.listingId().equals(listingId)
                    || !retry.optionId().equals(optionId)
                    || retry.action() != action
                    || retry.quantity() != quantity
                    || retry.expectedOfferRevision() != revision
                    || !retry.paymentSource().equals(source)) {
                throw new IllegalStateException(
                        "A different server shop offer request is pending");
            }
            pending = new PendingRequest(retry, System.currentTimeMillis());
            lastResult = null;
            return retry;
        }
        UUID requestId = UUID.randomUUID();
        C2SServerShopOfferPacket packet =
                new C2SServerShopOfferPacket(shopId, listingId,
                        optionId, action, quantity, revision, requestId,
                        source);
        pending = new PendingRequest(packet, System.currentTimeMillis());
        lastResult = null;
        return packet;
    }

    public synchronized boolean accept(
            S2CServerShopOfferResultPacket result
    ) {
        Objects.requireNonNull(result, "result");
        if (pending == null
                || !pending.requestId().equals(result.requestId())
                || !pending.listingId().equals(result.listingId())
                || !pending.optionId().equals(result.optionId())) {
            return false;
        }
        lastResult = result;
        if (result.status()
                != com.enviouse.futureshops.server.escrow.runtime
                .ServerShopOfferService.Status.RECOVERY_REQUIRED
                && result.status()
                != com.enviouse.futureshops.server.escrow.runtime
                .ServerShopOfferService.Status.QUARANTINED) {
            pending = null;
        }
        return true;
    }

    public synchronized Optional<PendingRequest> pending() {
        return Optional.ofNullable(pending);
    }

    public synchronized Optional<S2CServerShopOfferResultPacket>
    lastResult() {
        return Optional.ofNullable(lastResult);
    }

    public synchronized void clear() {
        pending = null;
        lastResult = null;
    }

    public record PendingRequest(
            C2SServerShopOfferPacket packet,
            long submittedAtMillis
    ) {
        public PendingRequest {
            Objects.requireNonNull(packet, "packet");
        }

        public UUID requestId() {
            return packet.requestId();
        }

        public String listingId() {
            return packet.listingId();
        }

        public String optionId() {
            return packet.optionId();
        }

        public OfferAction action() {
            return packet.action();
        }
    }
}
