package com.maidweapon.port.init;

import com.maidweapon.port.PortProbeMod;
import com.maidweapon.port.item.ProbeContractCarrierItem;
import com.maidweapon.port.item.ProbeSpiritVeinItem;
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
    public static final DeferredItem<ProbeContractCarrierItem> CONTRACT_CARRIER =
        ITEMS.register("contract_carrier", () -> new ProbeContractCarrierItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ProbeSpiritVeinItem> VEIN_ONE=ITEMS.register("vein_one",()->new ProbeSpiritVeinItem(1));
    public static final DeferredItem<ProbeSpiritVeinItem> VEIN_TWO=ITEMS.register("vein_two",()->new ProbeSpiritVeinItem(2));
    public static final DeferredItem<ProbeSpiritVeinItem> VEIN_THREE=ITEMS.register("vein_three",()->new ProbeSpiritVeinItem(3));
    private ProbeItems() {}
}
