package com.maidweapon.forge.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Unwind specialized hand projections before their underlying general equipment snapshot. */
public final class ContractEquipmentCleanup {
    public static void clear(Player owner, LivingEntity maid, ItemStack source) {
        TaczCompat.clear(owner, maid, source);
        TripleMagicCompat.clearPhantoms(maid, source);
    }

    private ContractEquipmentCleanup() { }
}
