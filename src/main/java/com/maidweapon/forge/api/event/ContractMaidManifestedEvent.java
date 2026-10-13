package com.maidweapon.forge.api.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;

/** Notification after successful storage-to-world commit; never grants permissions. */
public final class ContractMaidManifestedEvent extends Event {
  public final Player player;
  public final Entity maid;
  public final ItemStack carrier;

  public ContractMaidManifestedEvent(Player player, Entity maid, ItemStack carrier) {
    this.player = player;
    this.maid = maid;
    this.carrier = carrier;
  }
}
