package com.maidweapon.forge.item;

import com.maidweapon.forge.system.deployment.ContractProjectionMode;
import net.minecraft.world.item.Item;

/** Pure item presentation; native TLM registration and runtime effects live elsewhere. */
public final class ContractProjectionBaubleItem extends Item {
    private final ContractProjectionMode mode;
    private final boolean waiver;
    public boolean resonanceWaiver() { return waiver; }


    public ContractProjectionBaubleItem(ContractProjectionMode mode) {
        this(mode, false);
    }

    public ContractProjectionBaubleItem(ContractProjectionMode mode, boolean waiver) {
        super(new Properties().stacksTo(1));
        this.waiver = waiver;
        this.mode = mode;
    }

    public ContractProjectionMode mode() { return mode; }

}
