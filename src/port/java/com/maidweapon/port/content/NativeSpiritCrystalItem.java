package com.maidweapon.port.content;
import com.maidweapon.port.resonance.NativeResonanceService;
import com.maidweapon.port.storage.NativeChannelService;
import net.minecraft.world.item.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
/** Original 50 legacy units, converted to the native pool's 1000/200 scale. */
public final class NativeSpiritCrystalItem extends Item {
 public NativeSpiritCrystalItem(){super(new Properties().stacksTo(16));}
 @Override public InteractionResultHolder<ItemStack> use(Level l,Player p,InteractionHand h){
  var crystal=p.getItemInHand(h);var weapon=p.getMainHandItem();
  if(NativeChannelService.displayReference(weapon)==null)weapon=p.getOffhandItem();
  if(NativeChannelService.displayReference(weapon)==null)return InteractionResultHolder.pass(crystal);
  if(l.isClientSide)return InteractionResultHolder.success(crystal);
  if(!(p instanceof ServerPlayer sp)||!sp.isAlive()||sp.isSpectator())return InteractionResultHolder.fail(crystal);
  var server=sp.getServer();var live=NativeChannelService.selectedLive(server,p.getUUID(),weapon);
  int before=live==null?NativeResonanceService.currentStored(server,p.getUUID(),weapon):NativeResonanceService.currentLive(server,p.getUUID(),live);
  if(before<0)return InteractionResultHolder.fail(crystal);
  int after=live==null?NativeResonanceService.recoverStored(server,p.getUUID(),weapon,com.maidweapon.port.config.NativeRules.resonance().legacyUnits(50)):NativeResonanceService.recoverLive(server,p.getUUID(),live,com.maidweapon.port.config.NativeRules.resonance().legacyUnits(50));
  if(after<=before){p.displayClientMessage(Component.translatable("maid_weapon.message.resonance_full"),true);return InteractionResultHolder.fail(crystal);}
  if(!p.getAbilities().instabuild)crystal.shrink(1);
  p.displayClientMessage(Component.translatable("maid_weapon.message.resonance_restored",after-before),true);
  return InteractionResultHolder.success(crystal);
 }
}
