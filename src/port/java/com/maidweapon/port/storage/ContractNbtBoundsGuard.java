package com.maidweapon.port.storage;

import java.io.IOException;
import net.minecraft.nbt.CompoundTag;

/** Bounds for native entity snapshots; independent of legacy compression. */
public final class ContractNbtBoundsGuard {
    public static void validate(CompoundTag snapshot) throws IOException {
        if (snapshot == null || ContractNbtBounds.depth(snapshot) > 128
                || ContractNbtBounds.serializedSize(snapshot) > 32L * 1024 * 1024)
            throw new IOException("Native snapshot exceeds limits");
    }
    private ContractNbtBoundsGuard() {}
}
