package com.maidweapon.port.item;
import net.minecraft.world.item.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.UseOnContext;
/** Isolated development carrier; ordinary players use the formal swords. */
public final class ProbeContractCarrierItem extends Item {
 public ProbeContractCarrierItem(Properties properties){super(properties);}
 @Override public InteractionResult interactLivingEntity(ItemStack s,Player p,LivingEntity e,InteractionHand h){return NativeCarrierInteractions.interactLivingEntity(s,p,e,h);}
 @Override public InteractionResultHolder<ItemStack> use(Level l,Player p,InteractionHand h){return NativeCarrierInteractions.use(l,p,h);}
 @Override public InteractionResult useOn(UseOnContext c){return NativeCarrierInteractions.useOn(c);}
}
