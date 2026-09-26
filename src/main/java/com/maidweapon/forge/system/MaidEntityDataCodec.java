package com.maidweapon.forge.system;

import com.maidweapon.common.MaidWeaponConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;

/** Versioned, bounded storage for a complete serialized maid entity. */
public final class MaidEntityDataCodec {
    public static final String LEGACY_DATA = "MaidEntityData";
    public static final String COMPRESSED_DATA = "MaidEntityDataCompressed";
    public static final String FORMAT = "MaidEntityDataFormat";
    public static final String UNCOMPRESSED_SIZE = "MaidEntityDataUncompressedSize";
    public static final String CHECKSUM = "MaidEntityDataChecksum";
    public static final int FORMAT_VERSION = 1;

    public static boolean hasData(CompoundTag container) {
        return container != null && (container.contains(LEGACY_DATA, Tag.TAG_COMPOUND)
                || container.contains(COMPRESSED_DATA, Tag.TAG_BYTE_ARRAY));
    }

    public static CompoundTag read(CompoundTag container) throws IOException {
        if (container == null) throw new IOException("Missing maid data container");
        if (container.contains(LEGACY_DATA, Tag.TAG_COMPOUND)) {
            CompoundTag legacy = container.getCompound(LEGACY_DATA);
            long size = ContractNbtGuard.serializedSize(legacy);
            if (size > MaidWeaponConfig.CONTRACT_NBT_MAX_DECOMPRESSED_BYTES.get()
                    || ContractNbtGuard.depth(legacy) > MaidWeaponConfig.CONTRACT_NBT_MAX_DEPTH.get()) {
                throw new IOException("Legacy maid entity data exceeds safety limits");
            }
            return legacy.copy();
        }
        if (!container.contains(COMPRESSED_DATA, Tag.TAG_BYTE_ARRAY)) {
            throw new IOException("Missing maid entity data");
        }
        int format = container.getInt(FORMAT);
        if (format != FORMAT_VERSION) {
            throw new IOException("Unsupported maid entity data format " + format);
        }

        int expectedSize = container.getInt(UNCOMPRESSED_SIZE);
        int limit = MaidWeaponConfig.CONTRACT_NBT_MAX_DECOMPRESSED_BYTES.get();
        if (expectedSize < 0 || expectedSize > limit) {
            throw new IOException("Maid entity data exceeds decompression limit: " + expectedSize);
        }
        byte[] compressed = container.getByteArray(COMPRESSED_DATA);
        if (compressed.length > limit) {
            throw new IOException("Compressed maid entity data exceeds safety limit");
        }
        CRC32 checksum = new CRC32();
        checksum.update(compressed);
        if (checksum.getValue() != container.getLong(CHECKSUM)) {
            throw new IOException("Maid entity data checksum mismatch");
        }

        CompoundTag decoded;
        try (DataInputStream input = new DataInputStream(new GZIPInputStream(
                new ByteArrayInputStream(compressed)))) {
            decoded = NbtIo.read(input, new NbtAccounter(limit));
        } catch (RuntimeException | StackOverflowError exception) {
            throw new IOException("Unsafe compressed maid entity data", exception);
        }
        long actualSize = ContractNbtGuard.serializedSize(decoded);
        if (actualSize != expectedSize) {
            throw new IOException("Maid entity data size mismatch: expected "
                    + expectedSize + ", got " + actualSize);
        }
        return decoded;
    }

    /** Encodes first and only mutates the destination after every check succeeds. */
    public static void write(CompoundTag container, CompoundTag maidData) throws IOException {
        if (container == null || maidData == null) throw new IOException("Missing maid entity data");
        long rawSize = ContractNbtGuard.serializedSize(maidData);
        int limit = MaidWeaponConfig.CONTRACT_NBT_MAX_DECOMPRESSED_BYTES.get();
        if (rawSize < 0 || rawSize > limit || rawSize > Integer.MAX_VALUE) {
            throw new IOException("Maid entity data exceeds compression limit: " + rawSize);
        }
        if (ContractNbtGuard.depth(maidData) > MaidWeaponConfig.CONTRACT_NBT_MAX_DEPTH.get()) {
            throw new IOException("Maid entity data nesting is too deep");
        }

        byte[] compressed;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(maidData, bytes);
            compressed = bytes.toByteArray();
        } catch (RuntimeException | StackOverflowError exception) {
            throw new IOException("Failed to compress maid entity data", exception);
        }
        CRC32 checksum = new CRC32();
        checksum.update(compressed);

        CompoundTag verified = new CompoundTag();
        verified.putByteArray(COMPRESSED_DATA, compressed);
        verified.putInt(FORMAT, FORMAT_VERSION);
        verified.putInt(UNCOMPRESSED_SIZE, (int) rawSize);
        verified.putLong(CHECKSUM, checksum.getValue());
        read(verified);

        container.putByteArray(COMPRESSED_DATA, compressed);
        container.putInt(FORMAT, FORMAT_VERSION);
        container.putInt(UNCOMPRESSED_SIZE, (int) rawSize);
        container.putLong(CHECKSUM, checksum.getValue());
        container.remove(LEGACY_DATA);
    }

    public static boolean migrate(CompoundTag container) {
        if (container == null || !container.contains(LEGACY_DATA, Tag.TAG_COMPOUND)) return false;
        try {
            write(container, container.getCompound(LEGACY_DATA).copy());
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    public static boolean update(CompoundTag container, Consumer<CompoundTag> update) {
        try {
            CompoundTag decoded = read(container);
            update.accept(decoded);
            write(container, decoded);
            return true;
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    public static void remove(CompoundTag container) {
        if (container == null) return;
        container.remove(LEGACY_DATA);
        container.remove(COMPRESSED_DATA);
        container.remove(FORMAT);
        container.remove(UNCOMPRESSED_SIZE);
        container.remove(CHECKSUM);
    }

    public static int storedUncompressedSize(CompoundTag container) {
        if (container == null) return 0;
        if (container.contains(LEGACY_DATA, Tag.TAG_COMPOUND)) {
            long size = ContractNbtGuard.serializedSize(container.getCompound(LEGACY_DATA));
            return size > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) size;
        }
        return Math.max(0, container.getInt(UNCOMPRESSED_SIZE));
    }

    private MaidEntityDataCodec() {}
}
