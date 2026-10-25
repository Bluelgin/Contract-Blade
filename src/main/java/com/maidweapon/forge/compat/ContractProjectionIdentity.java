package com.maidweapon.forge.compat;

import net.minecraft.world.item.ItemStack;

/** Optional-provider-free identity shared by transfer, storage and equipment guards. */
public final class ContractProjectionIdentity {
    public static boolean isProjection(ItemStack stack) {
        return !stack.isEmpty() && stack.getTag() != null
                && (stack.getTag().getBoolean("MaidWeaponPhantomCopy")
                || stack.getTag().getBoolean("MaidWeaponTaczProjection"));
    }
    private ContractProjectionIdentity() { }
}
