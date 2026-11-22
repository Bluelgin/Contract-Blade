package com.maidweapon.port.storage;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Read-only active/external/intrinsic channel traversal with bounded cardinality. */
public final class ContractChannelView {
    public static final int MAX_INTRINSIC_SPIRITS = 64;

    public static List<CompoundTag> snapshotChannels(CompoundTag root) {
        if (root == null) return List.of();
        ArrayList<CompoundTag> result = new ArrayList<>();
        result.add(root.copy());
        if (root.contains("MaidWeaponExternalContract", Tag.TAG_COMPOUND))
            result.add(root.getCompound("MaidWeaponExternalContract").copy());
        CompoundTag spirits = root.getCompound("MaidWeaponIntrinsicSpirits");
        if (spirits.getAllKeys().size() > MAX_INTRINSIC_SPIRITS)
            throw new IllegalArgumentException("Too many intrinsic contract channels");
        for (String id : spirits.getAllKeys()) {
            CompoundTag channel = spirits.getCompound(id);
            if (channel.contains("Contract", Tag.TAG_COMPOUND))
                result.add(channel.getCompound("Contract").copy());
        }
        return List.copyOf(result);
    }

    /** Core storage's existing validity requirement, deliberately unchanged. */
    public static boolean validContract(CompoundTag root) {
        return root != null && root.contains("MaidData", Tag.TAG_COMPOUND)
            && root.contains("MaidUUID", Tag.TAG_STRING)
            && root.contains("MaidBindingId", Tag.TAG_STRING);
    }

    private ContractChannelView() {}
}
