package com.maidweapon.port.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.CRC32;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/**
 * Actual v1 contract snapshot NBT decoder/encoder for Minecraft 1.21.1.
 * Preserves the old compressed byte CRC and serialized-NBT-size convention.
 * This is still NOT TLM entity migration: Forge caps need a separate adapter.
 */
public final class LegacyMaidSnapshotCodec {
    public static final String LEGACY = "MaidEntityData";
    public static final String BYTES = "MaidEntityDataCompressed";
    public static final String FORMAT = "MaidEntityDataFormat";
    public static final String SIZE = "MaidEntityDataUncompressedSize";
    public static final String CHECKSUM = "MaidEntityDataChecksum";
    public static final int VERSION = 1;
    public static final int MAX_NBT_SIZE = 32 * 1024 * 1024;
    public static final int MAX_CONTAINER_SIZE = 40 * 1024 * 1024;
    public static final int MAX_NBT_DEPTH = 128;

    public static boolean contains(CompoundTag root) {
        return root != null && (root.contains(LEGACY, Tag.TAG_COMPOUND)
                || root.contains(BYTES, Tag.TAG_BYTE_ARRAY));
    }

    public static CompoundTag read(CompoundTag root) throws IOException {
        if (root == null) throw new IOException("Missing contract data");
        validateContainer(root);
        if (root.contains(LEGACY, Tag.TAG_COMPOUND)) {
            CompoundTag snapshot = root.getCompound(LEGACY);
            validateEntity(snapshot);
            return snapshot.copy();
        }
        CompressedSnapshotEnvelope.validate(root);
        int expected = root.getInt(SIZE);
        CompoundTag snapshot;
        try (DataInputStream input = new DataInputStream(new GZIPInputStream(
                new ByteArrayInputStream(root.getByteArray(BYTES))))) {
            snapshot = NbtIo.read(input, NbtAccounter.create(MAX_NBT_SIZE));
        } catch (RuntimeException | StackOverflowError failure) {
            throw new IOException("Cannot safely parse legacy NBT", failure);
        }
        validateEntity(snapshot);
        if (ContractNbtBounds.serializedSize(snapshot) != expected) {
            throw new IOException("Legacy serialized NBT size mismatch");
        }
        return snapshot;
    }

    public static void write(CompoundTag root, CompoundTag entity) throws IOException {
        if (root == null || entity == null) throw new IOException("Null contract or entity");
        validateEntity(entity);
        long count = ContractNbtBounds.serializedSize(entity);
        if (count > Integer.MAX_VALUE) throw new IOException("Legacy entity size overflow");
        byte[] compressed;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(entity, output);
            compressed = output.toByteArray();
        } catch (RuntimeException | StackOverflowError failure) {
            throw new IOException("Cannot compress native entity data", failure);
        }
        CRC32 crc = new CRC32();
        crc.update(compressed);

        CompoundTag candidate = root.copy();
        candidate.remove(LEGACY);
        candidate.putByteArray(BYTES, compressed);
        candidate.putInt(FORMAT, VERSION);
        candidate.putInt(SIZE, (int)count);
        candidate.putLong(CHECKSUM, crc.getValue());
        validateContainer(candidate);
        read(candidate);

        // Only publish after validation. An error leaves the old root untouched.
        root.putByteArray(BYTES, compressed);
        root.putInt(FORMAT, VERSION);
        root.putInt(SIZE, (int)count);
        root.putLong(CHECKSUM, crc.getValue());
        root.remove(LEGACY);
    }

    public static void validateEntity(CompoundTag data) throws IOException {
        if (data == null || ContractNbtBounds.serializedSize(data) > MAX_NBT_SIZE
                || ContractNbtBounds.depth(data) > MAX_NBT_DEPTH)
            throw new IOException("Contract entity exceeds NBT limits");
    }

    public static void validateContainer(CompoundTag root) throws IOException {
        if (ContractNbtBounds.serializedSize(root) > MAX_CONTAINER_SIZE
                || ContractNbtBounds.depth(root) > MAX_NBT_DEPTH)
            throw new IOException("Contract carrier exceeds NBT limits");
    }

    public static void clear(CompoundTag root) {
        if (root == null) return;
        root.remove(LEGACY);
        root.remove(BYTES);
        root.remove(FORMAT);
        root.remove(SIZE);
        root.remove(CHECKSUM);
    }

    private LegacyMaidSnapshotCodec() {}
}
