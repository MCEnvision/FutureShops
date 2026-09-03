package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.RequestId;

import java.util.Objects;
import java.util.UUID;

/** Immutable custody record. Item contents are represented by a bounded identity and hash. */
public record CustodyRecord(RequestId requestId, String custodyId, UUID owner, String itemKey,
                            long quantity, String contentHash, CustodyState state) {
    public CustodyRecord {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(state, "state");
        custodyId = bounded(custodyId, "custodyId");
        itemKey = bounded(itemKey, "itemKey");
        contentHash = bounded(contentHash, "contentHash");
        if (quantity <= 0L) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    private static String bounded(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 256
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(name + " must be a bounded single line");
        }
        return value;
    }
}
