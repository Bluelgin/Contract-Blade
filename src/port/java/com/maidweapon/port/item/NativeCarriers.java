package com.maidweapon.port.item;
import net.minecraft.world.item.ItemStack;
/** Server-owned item types, never a client-controlled tag or component claiming authority. */
public final class NativeCarriers {
 public static boolean accepts(ItemStack s){return s!=null&&!s.isEmpty()&&s.getCount()==1&&(s.getItem() instanceof NativeContractSwordItem||s.getItem() instanceof ProbeContractCarrierItem||s.has(com.maidweapon.port.init.PortDataTypes.CONTRACT_REFERENCE.get())&&com.maidweapon.port.tlm.NativeSoulSlabService.filled(s));}
 private NativeCarriers(){}
}
