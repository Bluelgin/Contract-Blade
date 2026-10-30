package com.maidweapon.forge.item;

import net.minecraft.world.item.Item;

/** Native maid accessories; gameplay is resolved by ContractBaubleEffects. */
public final class ContractSupportBaubleItem extends Item {
    public enum Kind { GROWTH, GUARD }
    private final Kind kind;
    public ContractSupportBaubleItem(Kind kind) { super(new Properties().stacksTo(1)); this.kind = kind; }
    public Kind kind() { return kind; }
}
