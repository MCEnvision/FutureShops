package com.enviouse.futureshops.server.escrow.item;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemEscrowBinaryIoTest {
    @Test
    void instantCodecRejectsNoncanonicalNanoseconds() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeLong(10L);
        output.writeInt(1000000000);

        assertThrows(IllegalArgumentException.class,
                () -> ItemEscrowBinaryIo.readInstant(
                        new DataInputStream(new ByteArrayInputStream(
                                bytes.toByteArray()))));

        ByteArrayOutputStream canonicalBytes = new ByteArrayOutputStream();
        DataOutputStream canonicalOutput = new DataOutputStream(
                canonicalBytes);
        ItemEscrowBinaryIo.writeInstant(canonicalOutput,
                Instant.ofEpochSecond(10L, 999999999));
        assertEquals(Instant.ofEpochSecond(10L, 999999999),
                ItemEscrowBinaryIo.readInstant(new DataInputStream(
                        new ByteArrayInputStream(
                                canonicalBytes.toByteArray()))));
    }
}
