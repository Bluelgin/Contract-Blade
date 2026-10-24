package com.maidweapon.forge.system.deployment;

import com.maidweapon.forge.compat.EpicFightCompat;
import com.maidweapon.forge.system.MaidCareTaskSystem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/** Projection grants equipment only. Work and schedule remain the player's choices. */
public final class ContractWorkPolicy {
    public static boolean combat(ContractProjectionMode mode) {
        return false;
    }
    public static boolean feeding(ContractProjectionMode mode) {
        return false;
    }
    public static boolean automatic(ContractProjectionMode mode) { return combat(mode) || feeding(mode); }
    public static void prepare(ItemStack carrier, Entity maid, ContractProjectionMode mode) {
        // Restore a pre-1.2 borrowed task/schedule once, then clear its snapshot.
        // Subsequent ticks must not overwrite a new manual task selection.
        if (carrier.getTag() != null && (carrier.getTag().contains(MaidCareTaskSystem.ORIGINAL_TASK_TAG)
                || carrier.getTag().contains(MaidCareTaskSystem.ORIGINAL_SCHEDULE_TAG))) {
            release(carrier, maid);
        }
    }

    public static void release(ItemStack carrier, Entity maid) {
        MaidCareTaskSystem.restoreOriginalTask(carrier, maid);
        MaidCareTaskSystem.clearOriginalTask(carrier);
        EpicFightCompat.clearSelection(maid);
    }

    private ContractWorkPolicy() { }
}
