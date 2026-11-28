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
 @Override public InteractionResult interactLivingEntity(ItemStack stack,Player player,LivingEntity target,InteractionHand hand){
  // Maintained Forge behavior: only an unbound main-hand blade captures.
  // A bound blade passes interaction to TLM, including its normal maid GUI.
  if(player.level().isClientSide||hand!=InteractionHand.MAIN_HAND
      ||stack.has(com.maidweapon.port.init.PortDataTypes.CONTRACT_REFERENCE.get())
      ||stack.has(com.maidweapon.port.init.PortDataTypes.CONTRACT_CHANNELS.get())
      ||com.maidweapon.port.projection.NativeProjection.isCopy(stack))return InteractionResult.PASS;
  return NativeCarrierInteractions.interactLivingEntity(stack,player,target,hand);
 }
 @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
  return InteractionResultHolder.pass(player.getItemInHand(hand));
 }
 @Override public boolean isValidRepairItem(ItemStack stack,ItemStack material){return material.is(Items.GOLD_INGOT);}
 @Override public boolean isFoil(ItemStack stack){return com.maidweapon.port.storage.NativeChannelService.displayReference(stack)!=null||super.isFoil(stack);}
 @Override public net.minecraft.network.chat.Component getName(ItemStack stack){
  if(this==com.maidweapon.port.content.NativeContent.SWORD.get()&&!com.maidweapon.port.projection.NativeProjection.isCopy(stack)&&com.maidweapon.port.storage.NativeChannelService.displayReference(stack)==null)return net.minecraft.network.chat.Component.translatable("item.maid_weapon.maid_sword_empty");
  return super.getName(stack);
 }
}
