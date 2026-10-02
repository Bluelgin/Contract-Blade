package com.maidweapon.forge.compat.tlm;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/** Preserve an original item when the player manually replaces a projected slot. */
public final class TlmEquipmentReturns {
    public static void returnOriginal(LivingEntity maid, ItemStack original) {
        if (original.isEmpty()) return;
        ItemStack remaining = original.copy();
        try {
            Object inventory = maid.getClass().getMethod("getAvailableBackpackInv").invoke(maid);
            if (inventory instanceof IItemHandler slots) {
                remaining = ItemHandlerHelper.insertItemStacked(slots, remaining, false);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // A full or unavailable backpack returns the real item at the maid's feet.
        }
        if (!remaining.isEmpty()) maid.spawnAtLocation(remaining);
    }

    private TlmEquipmentReturns() { }
}
