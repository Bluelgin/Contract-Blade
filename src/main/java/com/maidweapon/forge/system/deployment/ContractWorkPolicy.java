package com.maidweapon.forge.system.deployment;

import com.maidweapon.forge.compat.EpicFightCompat;
import com.maidweapon.forge.system.MaidCareTaskSystem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/** Automatic work is a borrowed setting, never authority over a manually configured maid. */
public final class ContractWorkPolicy {
    public static boolean combat(ContractProjectionMode mode) {
        return mode.weapon() && com.maidweapon.common.ContractRulesConfig.AUTO_COMBAT.get();
    }
    public static boolean feeding(ContractProjectionMode mode) {
        return mode.weapon() && com.maidweapon.common.ContractRulesConfig.AUTO_FEED.get()
                && com.maidweapon.common.MaidWeaponConfig.ENABLE_SAFE_FEEDING.get();
    }
    public static boolean automatic(ContractProjectionMode mode) { return combat(mode) || feeding(mode); }
    public static void prepare(ItemStack carrier, Entity maid, ContractProjectionMode mode) {
        if (automatic(mode)) MaidCareTaskSystem.rememberOriginalTask(carrier, maid);
        else release(carrier, maid);
    }

    public static void release(ItemStack carrier, Entity maid) {
        MaidCareTaskSystem.restoreOriginalTask(carrier, maid);
        MaidCareTaskSystem.clearOriginalTask(carrier);
        EpicFightCompat.clearSelection(maid);
    }

    private ContractWorkPolicy() { }
}
