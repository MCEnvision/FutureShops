package com.enviouse.futureshops.server.market.bazaar;

import java.math.BigInteger;

public final class BazaarFeeMath {
    private static final BigInteger BASIS_POINTS = BigInteger.valueOf(10_000L);

    private BazaarFeeMath() {
    }

    public static long cumulativeFee(long grossMinor, int basisPoints) {
        if (grossMinor < 0L || basisPoints < 0 || basisPoints > 10_000) {
            throw new IllegalArgumentException("Bazaar fee inputs are invalid");
        }
        return BigInteger.valueOf(grossMinor)
                .multiply(BigInteger.valueOf(basisPoints))
                .divide(BASIS_POINTS)
                .longValueExact();
    }

    public static long incrementalFee(long priorGrossMinor, long priorFeeMinor,
                                      long addedGrossMinor, int basisPoints) {
        long cumulativeGross = Math.addExact(priorGrossMinor, addedGrossMinor);
        long cumulativeFee = cumulativeFee(cumulativeGross, basisPoints);
        if (priorFeeMinor < 0L || priorFeeMinor > cumulativeFee) {
            throw new IllegalArgumentException("Bazaar prior fee is invalid");
        }
        return Math.subtractExact(cumulativeFee, priorFeeMinor);
    }
}
