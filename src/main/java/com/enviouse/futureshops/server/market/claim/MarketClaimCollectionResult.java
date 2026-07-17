package com.enviouse.futureshops.server.market.claim;

import com.enviouse.futureshops.client.market.MarketModule;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;

public record MarketClaimCollectionResult(
        UUID requestId,
        UUID routeNonce,
        MarketModule module,
        String view,
        UUID claimId,
        MarketClaimPresentationKind kind,
        MarketClaimCollectionCode code,
        long deliveredUnits,
        long remainingUnits,
        OptionalLong resultingBalanceMinor,
        boolean replayed,
        boolean refreshClaims
) {
    private static final UUID ZERO = new UUID(0L, 0L);

    public MarketClaimCollectionResult {
        requestId = requireId(requestId);
        routeNonce = requireId(routeNonce);
        module = Objects.requireNonNull(module, "module");
        view = Objects.requireNonNull(view, "view");
        claimId = requireId(claimId);
        kind = Objects.requireNonNull(kind, "kind");
        code = Objects.requireNonNull(code, "code");
        resultingBalanceMinor = Objects.requireNonNull(
                resultingBalanceMinor, "resultingBalanceMinor");
        if (!MarketClaimCollectionCommand.CLAIMS_VIEW.equals(view)
                || deliveredUnits < 0L || remainingUnits < 0L
                || refreshClaims != code.refreshClaims()
                || code == MarketClaimCollectionCode.COLLECTED
                && (deliveredUnits <= 0L || remainingUnits != 0L)
                || code == MarketClaimCollectionCode
                .PARTIALLY_COLLECTED
                && (deliveredUnits <= 0L || remainingUnits <= 0L)
                || code == MarketClaimCollectionCode.ALREADY_COLLECTED
                && (deliveredUnits != 0L || remainingUnits != 0L)
                || !kind.collectible()
                && (deliveredUnits != 0L || remainingUnits != 0L
                || resultingBalanceMinor.isPresent())
                || replayed && code != MarketClaimCollectionCode.COLLECTED
                && code != MarketClaimCollectionCode
                .PARTIALLY_COLLECTED
                && code != MarketClaimCollectionCode.ALREADY_COLLECTED) {
            throw new IllegalArgumentException(
                    "Market claim collection result is invalid");
        }
    }

    public static MarketClaimCollectionResult failure(
            MarketClaimCollectionCommand command,
            MarketClaimCollectionCode code
    ) {
        MarketClaimCollectionCommand request = Objects.requireNonNull(
                command, "command");
        MarketClaimCollectionCode resultCode = Objects.requireNonNull(
                code, "code");
        if (resultCode == MarketClaimCollectionCode.COLLECTED
                || resultCode == MarketClaimCollectionCode
                .PARTIALLY_COLLECTED
                || resultCode == MarketClaimCollectionCode
                .ALREADY_COLLECTED) {
            throw new IllegalArgumentException(
                    "Market claim failure code is invalid");
        }
        return new MarketClaimCollectionResult(request.requestId(),
                request.routeNonce(), request.module(), request.view(),
                request.claimId(), MarketClaimPresentationKind.UNKNOWN,
                resultCode, 0L, 0L, OptionalLong.empty(), false,
                resultCode.refreshClaims());
    }

    public boolean terminal() {
        return code.terminal();
    }

    private static UUID requireId(UUID value) {
        UUID result = Objects.requireNonNull(value, "identity");
        if (ZERO.equals(result)) {
            throw new IllegalArgumentException(
                    "Market claim collection result identity is invalid");
        }
        return result;
    }
}
