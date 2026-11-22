package com.maidweapon.port.storage;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Version-specific, bounded 1.21.1 implementation of the old NBT size rules. */
public final class ContractNbtBounds {
    private static final int MAX_SCAN_NODES = 65536;
    private static final int MAX_SAFE_DEPTH = 128;

    public static long serializedSize(CompoundTag tag) {
        if (tag == null) return 0;
        CountingOutput bytes = new CountingOutput();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            NbtIo.write(tag, output);
            return bytes.length;
        } catch (IOException | RuntimeException | StackOverflowError failure) {
            return Long.MAX_VALUE;
        }
    }

    public static int depth(Tag tag) {
        return depth(tag, 1, new int[] {0});
    }

    private static int depth(Tag tag, int current, int[] nodes) {
        if (current > MAX_SAFE_DEPTH || ++nodes[0] > MAX_SCAN_NODES)
            return Integer.MAX_VALUE;
        if (tag == null) return current;
        int maximum = current;
        if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                maximum = Math.max(maximum, depth(compound.get(key), current + 1, nodes));
                if (maximum == Integer.MAX_VALUE) return maximum;
            }
        } else if (tag instanceof ListTag list) {
            for (Tag next : list) {
                maximum = Math.max(maximum, depth(next, current + 1, nodes));
                if (maximum == Integer.MAX_VALUE) return maximum;
            }
        }
        return maximum;
    }

    private static final class CountingOutput extends OutputStream {
        private long length;
        @Override public void write(int value) { length++; }
        @Override public void write(byte[] bytes, int offset, int count) { length += count; }
    }

    private ContractNbtBounds() {}
}
