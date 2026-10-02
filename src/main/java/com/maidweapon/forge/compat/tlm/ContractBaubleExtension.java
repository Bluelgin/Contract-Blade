package com.maidweapon.forge.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import com.maidweapon.forge.init.ModItems;

/** Registered by TLM itself during bauble initialization, on both logical sides. */
@LittleMaidExtension
public final class ContractBaubleExtension implements ILittleMaid {
    @Override
    public void bindMaidBauble(BaubleManager manager) {
        manager.bind(ModItems.RESONANCE_SWORD_TASSEL, new IMaidBauble() { });
        manager.bind(ModItems.GUARDIAN_RIBBON, new IMaidBauble() { });
        manager.bind(ModItems.HEARTBOUND_KNOT, new IMaidBauble() { });
    }
}
