package com.enviouse.futureshops.server.economy.migration;

import com.enviouse.futureshops.server.escrow.runtime.WalletMutationStatus;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LiveEscrowWalletInitializationGatewayTest {
    @Test
    void onlyAppliedAndReplayAreSuccessful() {
        assertEquals(WalletInitializationDisposition.APPLIED,
                LiveEscrowWalletInitializationGateway.map(
                        WalletMutationStatus.APPLIED).disposition());
        assertEquals(WalletInitializationDisposition.REPLAYED,
                LiveEscrowWalletInitializationGateway.map(
                        WalletMutationStatus.REPLAYED).disposition());
        assertEquals(WalletInitializationDisposition.ALREADY_INITIALIZED,
                LiveEscrowWalletInitializationGateway.map(
                        WalletMutationStatus.ALREADY_INITIALIZED)
                        .disposition());

        EnumSet.complementOf(EnumSet.of(
                        WalletMutationStatus.APPLIED,
                        WalletMutationStatus.REPLAYED,
                        WalletMutationStatus.ALREADY_INITIALIZED))
                .forEach(status -> assertEquals(
                        WalletInitializationDisposition.CONFLICT,
                        LiveEscrowWalletInitializationGateway.map(status)
                                .disposition()));
    }
}
