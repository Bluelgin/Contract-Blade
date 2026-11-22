package com.maidweapon.port.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.util.zip.CRC32;
import java.util.zip.GZIPOutputStream;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class CompressedSnapshotEnvelopeTest {
    private static CompoundTag sample() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(new byte[] {10, 0, 0});
        }
        byte[] compressed = bytes.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(compressed);
        CompoundTag nbt = new CompoundTag();
        nbt.putByteArray("MaidEntityDataCompressed", compressed);
        nbt.putInt("MaidEntityDataFormat", 1);
        nbt.putInt("MaidEntityDataUncompressedSize", 3);
        nbt.putLong("MaidEntityDataChecksum", crc.getValue());
        return nbt;
    }

    @Test
    void acceptsKnownV1Envelope() throws Exception {
        assertEquals(1, CompressedSnapshotEnvelope.validate(sample()).format());
    }

    @Test
    void rejectsChecksumTamperingAndDoesNotConsumeSource() throws Exception {
        CompoundTag root = sample();
        root.putLong("MaidEntityDataChecksum", 0L);
        assertThrows(java.io.IOException.class, () -> CompressedSnapshotEnvelope.validate(root));
        assertTrue(root.contains("MaidEntityDataCompressed"));
    }

    @Test
    void rejectsUnknownFutureFormat() throws Exception {
        CompoundTag root = sample();
        root.putInt("MaidEntityDataFormat", 2);
        assertThrows(java.io.IOException.class, () -> CompressedSnapshotEnvelope.validate(root));
    }

    @Test
    void rejectsBogusLargeDecompressedSize() throws Exception {
        CompoundTag root = sample();
        root.putInt("MaidEntityDataUncompressedSize", Integer.MAX_VALUE);
        assertThrows(java.io.IOException.class, () -> CompressedSnapshotEnvelope.validate(root));
    }
}
