package com.maidweapon.forge.api.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;

/** Fired after a maid is serialized into a contract weapon. */
public final class ContractMaidCapturedEvent extends Event {
    private final Player player;
    private final Entity maid;
    private final ItemStack weapon;
    private final boolean firstCapture;

    public ContractMaidCapturedEvent(Player player, Entity maid, ItemStack weapon,
                                     boolean firstCapture) {
        this.player = player;
        this.maid = maid;
        this.weapon = weapon;
        this.firstCapture = firstCapture;
    }

    public Player getPlayer() {
        return player;
    }

    public Entity getMaid() {
        return maid;
    }

    public ItemStack getWeapon() {
        return weapon;
    }

    public boolean isFirstCapture() {
        return firstCapture;
    }
}
