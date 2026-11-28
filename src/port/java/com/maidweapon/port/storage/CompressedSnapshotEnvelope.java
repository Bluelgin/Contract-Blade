package com.maidweapon.port.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Bounded, read-only transport verification for legacy v1 compressed snapshots. */
public final class CompressedSnapshotEnvelope {
    public static final int FORMAT_V1 = 1;
    public static final int MAX_COMPRESSED = 8 * 1024 * 1024;
    public static final int MAX_INFLATED_STREAM = 32 * 1024 * 1024;
    public record Header(int format, int oldNbtSize, int gzipBytes, long checksum) {}

    public static Header validate(CompoundTag root) throws IOException {
        if (root == null || !root.contains("MaidEntityDataCompressed", Tag.TAG_BYTE_ARRAY))
            throw new IOException("Missing compressed snapshot");
        int format = root.getInt("MaidEntityDataFormat");
        if (format != FORMAT_V1) throw new IOException("Unknown format " + format);
        int size = root.getInt("MaidEntityDataUncompressedSize");
        if (size <= 0 || size > MAX_INFLATED_STREAM)
            throw new IOException("Invalid NBT size " + size);
        byte[] bytes = root.getByteArray("MaidEntityDataCompressed");
        if (bytes.length == 0 || bytes.length > MAX_COMPRESSED)
            throw new IOException("GZIP envelope exceeds budget");
        CRC32 crc = new CRC32();
        crc.update(bytes);
        long expected = root.getLong("MaidEntityDataChecksum");
        if (crc.getValue() != expected) throw new IOException("CRC mismatch");
        int inflated = 0;
        byte[] buffer = new byte[8192];
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            int n;
            while ((n = input.read(buffer)) != -1) {
                inflated += n;
                if (inflated > MAX_INFLATED_STREAM) throw new IOException("Inflation exceeds budget");
            }
        }
        // Old size is ContractNbtGuard.serializedSize, not gzip stream length.
        return new Header(format, size, bytes.length, expected);
    }
    private CompressedSnapshotEnvelope() {}
}
