package com.maidweapon.forge.system;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Read-only size breakdown used by the development command and warning logs. */
public final class ContractNbtAudit {
    private static final String INTRINSIC = "MaidWeaponIntrinsicSpirits";
    private static final String EXTERNAL = "MaidWeaponExternalContract";
    private static final String PROJECTION = "MaidWeaponIntrinsicProjection";
    private static final String CONTRACT = "Contract";

    public record Report(long completeItemBytes, long itemTagBytes,
                         int currentMaidBytes, int currentCompressedBytes,
                         long intrinsicArchiveBytes, long externalArchiveBytes,
                         boolean duplicateProjection, String projectedSpirit) {}

    public static Report inspect(ItemStack stack) {
        CompoundTag root = stack.getTag();
        CompoundTag savedItem = stack.save(new CompoundTag());
        long complete = ContractNbtGuard.serializedSize(savedItem);
        long tagBytes = root == null ? 0 : ContractNbtGuard.serializedSize(root);
        int maidBytes = MaidEntityDataCodec.storedUncompressedSize(root);
        int compressedBytes = root != null
                && root.contains(MaidEntityDataCodec.COMPRESSED_DATA, Tag.TAG_BYTE_ARRAY)
                ? root.getByteArray(MaidEntityDataCodec.COMPRESSED_DATA).length : 0;
        long intrinsicBytes = root != null && root.contains(INTRINSIC, Tag.TAG_COMPOUND)
                ? ContractNbtGuard.serializedSize(root.getCompound(INTRINSIC)) : 0;
        long externalBytes = root != null && root.contains(EXTERNAL, Tag.TAG_COMPOUND)
                ? ContractNbtGuard.serializedSize(root.getCompound(EXTERNAL)) : 0;
        String projected = root == null ? "" : root.getString(PROJECTION);
        boolean duplicate = false;
        if (root != null && !projected.isEmpty() && MaidEntityDataCodec.hasData(root)) {
            CompoundTag entry = root.getCompound(INTRINSIC).getCompound(projected);
            duplicate = entry.contains(CONTRACT, Tag.TAG_COMPOUND)
                    && MaidEntityDataCodec.hasData(entry.getCompound(CONTRACT));
        }
        return new Report(complete, tagBytes, maidBytes, compressedBytes,
                intrinsicBytes, externalBytes, duplicate, projected);
    }

    private ContractNbtAudit() {}
}
