package com.enviouse.futureshops.catalog.offer;

public record OfferSchedule(
        long startsAtEpoch,
        long endsAtEpoch
) {
    public static OfferSchedule always() {
        return new OfferSchedule(0L, 0L);
    }

    public boolean activeAt(long epochSeconds) {
        return (startsAtEpoch == 0L || epochSeconds >= startsAtEpoch)
                && (endsAtEpoch == 0L || epochSeconds < endsAtEpoch);
    }
}
