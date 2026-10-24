package com.maidweapon.forge.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** Ordinary-contract maid bauble. Does not store level or gameplay authority in item NBT. */
public final class DanmakuTalismanItem extends Item {
    public DanmakuTalismanItem() { super(new Properties().stacksTo(1).rarity(Rarity.RARE)); }

}
