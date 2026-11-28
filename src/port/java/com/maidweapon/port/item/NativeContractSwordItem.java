package com.maidweapon.port.item;
import net.minecraft.world.item.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.UseOnContext;
/** Formal contract weapon; retains vanilla sword combat and native contract authority. */
public final class NativeContractSwordItem extends SwordItem {
 public NativeContractSwordItem(Tier tier,int damage,float speed){super(tier,new Properties().stacksTo(1).durability(385).attributes(SwordItem.createAttributes(tier,damage,speed)));}
 @Override public InteractionResult interactLivingEntity(ItemStack s,Player p,LivingEntity e,InteractionHand h){return NativeCarrierInteractions.interactLivingEntity(s,p,e,h);}
 @Override public InteractionResultHolder<ItemStack> use(Level l,Player p,InteractionHand h){return NativeCarrierInteractions.use(l,p,h);}
 @Override public InteractionResult useOn(UseOnContext c){return NativeCarrierInteractions.useOn(c);}
 @Override public boolean isFoil(ItemStack stack){return com.maidweapon.port.storage.NativeChannelService.displayReference(stack)!=null||super.isFoil(stack);}
 @Override public net.minecraft.network.chat.Component getName(ItemStack stack){
  if(this==com.maidweapon.port.content.NativeContent.SWORD.get()&&com.maidweapon.port.storage.NativeChannelService.displayReference(stack)==null)return net.minecraft.network.chat.Component.translatable("item.maid_weapon.maid_sword_empty");
  return super.getName(stack);
 }
 @Override public void appendHoverText(ItemStack stack,Item.TooltipContext context,java.util.List<net.minecraft.network.chat.Component> lines,TooltipFlag flag){
  super.appendHoverText(stack,context,lines,flag);
  if(com.maidweapon.port.storage.NativeChannelService.displayReference(stack)!=null){
   lines.add(net.minecraft.network.chat.Component.translatable("maid_weapon.native.tip.toggle"));
   lines.add(net.minecraft.network.chat.Component.translatable("maid_weapon.native.tip.transfer"));
  }
 }
}
