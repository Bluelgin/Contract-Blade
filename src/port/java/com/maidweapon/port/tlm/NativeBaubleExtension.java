package com.maidweapon.port.tlm;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import com.maidweapon.port.init.ProbeItems;
/** Native TLM extension; registered by TLM, no reflection. */
@LittleMaidExtension
public final class NativeBaubleExtension implements ILittleMaid{
    @Override public void bindMaidBauble(BaubleManager manager){
        manager.bind(ProbeItems.OATH_RING.get(),new IMaidBauble(){});
        manager.bind(ProbeItems.ENDLESS_CONTRACT.get(),new IMaidBauble(){});
    }
}
