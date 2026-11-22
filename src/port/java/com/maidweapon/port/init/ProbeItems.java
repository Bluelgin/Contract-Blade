package com.maidweapon.port.init;

import com.maidweapon.port.PortProbeMod;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A disposable test item under the PROBE namespace. Never reuse the 1.20.1
 * maid_weapon registry ID before every original item has been ported.
 */
public final class ProbeItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PortProbeMod.ID);
    public static final DeferredItem<Item> CONTRACT_BAUBLE =
        ITEMS.registerSimpleItem("contract_bauble", new Item.Properties().stacksTo(1));
    private ProbeItems() {}
}
