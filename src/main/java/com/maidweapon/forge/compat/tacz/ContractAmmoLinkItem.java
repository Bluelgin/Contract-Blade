package com.maidweapon.forge.compat.tacz;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;

/** Registry tombstone for old saves. No longer implements IAmmoBox or supplies ammunition. */
public final class ContractAmmoLinkItem extends Item {
    public ContractAmmoLinkItem(Properties properties) { super(properties); }
    @Override
    public boolean onDroppedByPlayer(ItemStack stack, Player player) { return false; }
}
