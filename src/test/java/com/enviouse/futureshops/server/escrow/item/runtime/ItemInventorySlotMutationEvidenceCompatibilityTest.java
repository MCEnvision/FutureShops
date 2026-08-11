package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.escrow.item.ItemInventorySlot;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemInventorySlotMutationEvidenceCompatibilityTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void legacyHashesValidateStoredEvidenceWithoutReencoding() {
        byte[] snapshot = orderedSnapshot();
        ItemInventorySlotMutationEvidence evidence =
                new ItemInventorySlotMutationEvidence(
                        ItemInventorySlot.main(0),
                        new byte[0], snapshot);
        ItemStack decoded = ItemStackSnapshotCodec.decode(snapshot);

        assertTrue(evidence.hashesMatch(
                hashSlot(new byte[0]), hashSlot(snapshot)));
        assertTrue(evidence.matchesAfter(decoded.copy()));
        decoded.getOrCreateTag().putString("capability_value", "changed");
        assertFalse(evidence.matchesAfter(decoded));
    }

    private static byte[] orderedSnapshot() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(Tag.TAG_COMPOUND);
                output.writeUTF("");
                output.writeByte(Tag.TAG_COMPOUND);
                output.writeUTF("tag");
                output.writeByte(Tag.TAG_STRING);
                output.writeUTF("capability_value");
                output.writeUTF("preserved");
                output.writeByte(Tag.TAG_END);
                output.writeByte(Tag.TAG_BYTE);
                output.writeUTF("Count");
                output.writeByte(1);
                output.writeByte(Tag.TAG_STRING);
                output.writeUTF("id");
                output.writeUTF("minecraft:diamond");
                output.writeByte(Tag.TAG_END);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] hashSlot(byte[] snapshot) {
        byte[] framed;
        if (snapshot.length == 0) {
            framed = new byte[]{0};
        } else {
            framed = new byte[snapshot.length + 1];
            framed[0] = 1;
            System.arraycopy(snapshot, 0, framed, 1, snapshot.length);
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(framed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
