package com.enviouse.futureshops.server.escrow.custody;

import java.util.Objects;

public record CustodyTransferEvidence(
        CustodyEndpointEvidence source,
        CustodyEndpointEvidence destination
) {
    public CustodyTransferEvidence {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        if (source.adapterId().equals(destination.adapterId())
                && source.ownerKey().equals(destination.ownerKey())
                && source.locationKey().equals(destination.locationKey())) {
            throw new IllegalArgumentException("Custody source and destination must differ");
        }
    }
}
