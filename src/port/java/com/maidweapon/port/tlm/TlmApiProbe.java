package com.maidweapon.port.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.maidweapon.port.init.ProbeItems;

/** Verifies upstream TLM extension symbols without registering unsafe fake items. */
@LittleMaidExtension
public final class TlmApiProbe implements ILittleMaid {
    @Override
    public void bindMaidBauble(BaubleManager manager) {
        manager.bind(ProbeItems.CONTRACT_BAUBLE, new IMaidBauble() {});
    }
}
