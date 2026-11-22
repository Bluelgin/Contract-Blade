package com.maidweapon.port.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;

/** Verifies upstream TLM extension symbols without registering unsafe fake items. */
@LittleMaidExtension
public final class TlmApiProbe implements ILittleMaid {
    @Override
    public void bindMaidBauble(BaubleManager manager) {
        // Intentionally empty until real item registry is ported.
    }
}
