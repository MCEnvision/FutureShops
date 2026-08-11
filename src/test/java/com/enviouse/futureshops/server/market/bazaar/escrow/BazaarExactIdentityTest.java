package com.enviouse.futureshops.server.market.bazaar.escrow;

import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarExactIdentityTest {
    @Test
    void canonicalSnbtMatchesOnlyTheConfiguredVariant() throws Exception {
        ItemStack configured = new ItemStack(Items.EMERALD, 1);
        configured.setTag(TagParser.parseTag(
                "{CustomModelData:7,display:{Name:'{\"text\":\"Token\"}'}}"));
        byte[] configuredBytes = ItemStackSnapshotCodec.encode(configured);

        assertTrue(BazaarSellItemCustody.matchesIdentity(
                configured.getTag().toString(), configuredBytes));

        ItemStack other = configured.copy();
        other.getOrCreateTag().putInt("CustomModelData", 8);
        assertFalse(BazaarSellItemCustody.matchesIdentity(
                configured.getTag().toString(),
                ItemStackSnapshotCodec.encode(other)));
    }

    @Test
    void legacyDigestIdentityRemainsCompatible() throws Exception {
        ItemStack stack = new ItemStack(Items.EMERALD, 1);
        stack.setTag(TagParser.parseTag("{CustomModelData:7}"));
        byte[] encoded = ItemStackSnapshotCodec.encode(stack);
        String digest = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(encoded));

        assertTrue(BazaarSellItemCustody.matchesIdentity(
                digest, encoded));
        assertTrue(BazaarSellItemCustody.matchesIdentity(
                "sha256:" + digest, encoded));
    }
}
