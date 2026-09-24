package com.enviouse.futureshops.compat.pixelmon;

import com.enviouse.futureshops.api.economy.BindingV1;
import com.enviouse.futureshops.api.economy.LegId;
import com.enviouse.futureshops.api.economy.RootId;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PixelmonNativeReceiptCodecTest {
    private static final String FINGERPRINT =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void receiptRoundTripPreservesOriginalBindingAndResult() {
        BindingV1 binding = new BindingV1("pixelmon", 1, "pixelmon-native", "pixelmon-9.2.3",
                FINGERPRINT, FINGERPRINT, "pixelmon", UUID.randomUUID(), "poke_dollars", 2,
                "pixelmon-native-store", 1L, 1);
        RootId root = RootId.random();
        LegId leg = LegId.random();
        try (PixelmonNativeRequestContext.Scope ignored = PixelmonNativeRequestContext.open(
                binding, root, leg, "withdraw", 125L, true, FINGERPRINT)) {
            PixelmonNativeRequestContext.current().orElseThrow()
                    .recordResult(new BigDecimal("12.34"));
            CompoundTag account = new CompoundTag();
            PixelmonNativeReceiptCodec.write(account,
                    PixelmonNativeRequestContext.current().orElseThrow());
            PixelmonNativeReceiptCodec.Receipt receipt =
                    PixelmonNativeReceiptCodec.read(account).orElseThrow();
            assertEquals(root.value(), receipt.rootId());
            assertEquals(leg.value(), receipt.legId());
            assertEquals(125L, receipt.amountMinorUnits());
            assertEquals(new BigDecimal("12.34"), receipt.resultingBalance());
            assertEquals(FINGERPRINT, receipt.payloadFingerprint());
        }
    }

    @Test
    void malformedReceiptIsRejectedWithinBounds() {
        CompoundTag account = new CompoundTag();
        CompoundTag receipt = new CompoundTag();
        receipt.putInt("schema", 1);
        receipt.putUUID("root", UUID.randomUUID());
        receipt.putUUID("leg", UUID.randomUUID());
        receipt.putString("operation", "withdraw");
        receipt.putLong("amount", 1L);
        receipt.putBoolean("debit", true);
        receipt.putString("payload", "not-a-fingerprint");
        receipt.putString("balance", "1.00");
        account.put("futureshopsReceipt", receipt);
        assertThrows(IllegalArgumentException.class,
                () -> PixelmonNativeReceiptCodec.read(account));
    }

    @Test
    void validReceiptCanBePreservedAcrossAProviderRewrite() {
        BindingV1 binding = new BindingV1("pixelmon", 1, "pixelmon-native", "pixelmon-9.2.3",
                FINGERPRINT, FINGERPRINT, "pixelmon", UUID.randomUUID(), "poke_dollars", 2,
                "pixelmon-native-store", 1L, 1);
        try (PixelmonNativeRequestContext.Scope ignored = PixelmonNativeRequestContext.open(
                binding, RootId.random(), LegId.random(), "deposit", 250L, false, FINGERPRINT)) {
            PixelmonNativeRequestContext.current().orElseThrow()
                    .recordResult(new BigDecimal("2.50"));
            CompoundTag source = new CompoundTag();
            PixelmonNativeReceiptCodec.write(source,
                    PixelmonNativeRequestContext.current().orElseThrow());
            CompoundTag rewritten = new CompoundTag();
            PixelmonNativeReceiptCodec.preserve(rewritten, source);
            assertEquals(PixelmonNativeReceiptCodec.read(source).orElseThrow(),
                    PixelmonNativeReceiptCodec.read(rewritten).orElseThrow());
        }
    }

    @Test
    void malformedReceiptRemainsAvailableForRecoveryInspection() {
        CompoundTag source = new CompoundTag();
        CompoundTag receipt = new CompoundTag();
        receipt.putInt("schema", 99);
        source.put("futureshopsReceipt", receipt);

        assertThrows(IllegalArgumentException.class,
                () -> PixelmonNativeReceiptCodec.read(source));
        assertEquals(99, source.getCompound("futureshopsReceipt").getInt("schema"));
    }
}
