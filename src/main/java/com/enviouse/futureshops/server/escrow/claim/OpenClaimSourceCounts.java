package com.enviouse.futureshops.server.escrow.claim;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record OpenClaimSourceCounts(
        long totalOpenClaims,
        Map<String, Long> matchingSourcePrefixes
) {
    public static final int MAXIMUM_PREFIXES = 16;

    public OpenClaimSourceCounts {
        if (totalOpenClaims < 0L) {
            throw new IllegalArgumentException(
                    "Open claim total must not be negative");
        }
        Map<String, Long> source = Objects.requireNonNull(
                matchingSourcePrefixes, "matchingSourcePrefixes");
        if (source.size() > MAXIMUM_PREFIXES) {
            throw new IllegalArgumentException(
                    "Open claim prefix count exceeds its limit");
        }
        LinkedHashMap<String, Long> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : source.entrySet()) {
            String prefix = requirePrefix(entry.getKey());
            Long count = Objects.requireNonNull(entry.getValue(),
                    "prefix count");
            if (count < 0L || count > totalOpenClaims
                    || copy.put(prefix, count) != null) {
                throw new IllegalArgumentException(
                        "Open claim prefix count is invalid");
            }
        }
        matchingSourcePrefixes = Map.copyOf(copy);
    }

    public long matching(String sourcePrefix) {
        return matchingSourcePrefixes.getOrDefault(
                requirePrefix(sourcePrefix), 0L);
    }

    static String requirePrefix(String value) {
        String prefix = Objects.requireNonNull(value,
                "sourcePrefix");
        if (prefix.isEmpty() || prefix.length() > 192
                || !prefix.equals(prefix.strip())) {
            throw new IllegalArgumentException(
                    "Open claim source prefix is invalid");
        }
        return prefix;
    }
}
