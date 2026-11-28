package com.maidweapon.port.storage;

import java.io.IOException;
import net.minecraft.nbt.CompoundTag;

/** Bounds for native entity snapshots; independent of legacy compression. */
public final class ContractNbtBoundsGuard {
    public static void validate(CompoundTag snapshot) throws IOException {
        var loaded=com.maidweapon.port.config.MaidWeaponConfig.SPEC.isLoaded();
        int depth=loaded?com.maidweapon.port.config.MaidWeaponConfig.CONTRACT_NBT_MAX_DEPTH.get():128;
        long bytes=loaded?com.maidweapon.port.config.MaidWeaponConfig.CONTRACT_NBT_MAX_DECOMPRESSED_BYTES.get():16L*1024*1024;
        if (snapshot == null || ContractNbtBounds.depth(snapshot) > depth
                || ContractNbtBounds.serializedSize(snapshot) > bytes)
            throw new IOException("Native snapshot exceeds limits");
    }
    private ContractNbtBoundsGuard() {}
}
