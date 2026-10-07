package com.maidweapon.forge.system;

import com.maidweapon.common.MaidWeaponConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Measures a prospective contract before it is committed to an ItemStack. */
public final class ContractNbtGuard {
    private static final int MAX_REPORTED_ITEMS = 3;
    private static final int MAX_SCAN_NODES = 4096;
    private static final int MAX_DEPTH_SCAN_NODES = 65536;
    private static final int MAX_SAFE_SCAN_DEPTH = 512;

    public record ItemContribution(String path, String itemId, long bytes) {}

    public record Result(long bytes, int depth, List<ItemContribution> largestItems) {
        public boolean warning() {
            return bytes >= MaidWeaponConfig.CONTRACT_NBT_WARNING_BYTES.get();
        }

        public String warningLevel() {
            if (bytes >= MaidWeaponConfig.CONTRACT_NBT_CRITICAL_WARNING_BYTES.get()) return "critical";
            if (bytes >= MaidWeaponConfig.CONTRACT_NBT_HIGH_WARNING_BYTES.get()) return "high";
            return warning() ? "warning" : "none";
        }
    }

    public static Result inspect(CompoundTag prospectiveItemTag) {
        int depth = depth(prospectiveItemTag, 1, new int[]{0});
        long bytes = serializedSize(prospectiveItemTag);
        List<ItemContribution> items = new ArrayList<>();
        collectItems(prospectiveItemTag, "root", 1, new int[]{0}, items);
        items.sort(Comparator.comparingLong(ItemContribution::bytes).reversed());
        if (items.size() > MAX_REPORTED_ITEMS) {
            items = new ArrayList<>(items.subList(0, MAX_REPORTED_ITEMS));
        }
        return new Result(bytes, depth, List.copyOf(items));
    }

    public static long serializedSize(Tag tag) {
        if (tag == null) return 0L;
        CountingOutputStream bytes = new CountingOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            if (tag instanceof CompoundTag compound) {
                NbtIo.write(compound, output);
            } else {
                tag.write(output);
            }
            return bytes.count();
        } catch (IOException | RuntimeException | StackOverflowError exception) {
            return Long.MAX_VALUE;
        }
    }

    private static final class CountingOutputStream extends OutputStream {
        private long count;

        @Override
        public void write(int value) {
            count++;
        }

        @Override
        public void write(byte[] value, int offset, int length) {
            count += length;
        }

        private long count() {
            return count;
        }
    }

    public static String formatBytes(long bytes) {
        if (bytes == Long.MAX_VALUE) return "unreadable";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format("%.1f KiB", bytes / 1024.0);
        return String.format("%.2f MiB", bytes / (1024.0 * 1024.0));
    }

    private static int depth(Tag tag, int current, int[] visited) {
        if (current > MAX_SAFE_SCAN_DEPTH) return current;
        if (tag == null) return current;
        if (visited[0]++ >= MAX_DEPTH_SCAN_NODES) return Integer.MAX_VALUE;
        int maximum = current;
        if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                maximum = Math.max(maximum, depth(compound.get(key), current + 1, visited));
                if (maximum == Integer.MAX_VALUE) return maximum;
            }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) {
                maximum = Math.max(maximum, depth(child, current + 1, visited));
                if (maximum == Integer.MAX_VALUE) return maximum;
            }
        }
        return maximum;
    }

    public static int depth(Tag tag) {
        return depth(tag, 1, new int[]{0});
    }

    private static void collectItems(Tag tag, String path, int depth, int[] visited,
                                     List<ItemContribution> result) {
        if (tag == null || depth > 64 || visited[0]++ >= MAX_SCAN_NODES) return;
        if (tag instanceof CompoundTag compound) {
            if (compound.contains("id", Tag.TAG_STRING)
                    && (compound.contains("Count", Tag.TAG_BYTE)
                    || compound.contains("count", Tag.TAG_INT))) {
                result.add(new ItemContribution(path, compound.getString("id"),
                        serializedSize(compound)));
            }
            for (String key : compound.getAllKeys()) {
                collectItems(compound.get(key), path + "." + key, depth + 1, visited, result);
            }
        } else if (tag instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                collectItems(list.get(i), path + "[" + i + "]", depth + 1, visited, result);
            }
        }
    }

    private ContractNbtGuard() {}
}
