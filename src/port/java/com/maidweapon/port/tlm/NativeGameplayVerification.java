package com.maidweapon.port.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.item.AbstractStoreMaidItem;
import com.maidweapon.port.content.NativeContent;
import com.maidweapon.port.init.PortDataTypes;
import com.maidweapon.port.menu.NativeMaidInjectorMenu;
import com.maidweapon.port.storage.NativeChannelService;
import com.maidweapon.port.resonance.NativeResonanceService;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Opt-in disposable native fixture; never invoked during normal gameplay. */
final class NativeGameplayVerification {
 static void run(ServerLevel level)throws Exception{
  // Standard FakePlayer is unconditionally invulnerable. This disposable subclass
  // uses the actual native hurt pipeline so health-loss regression is meaningful.
  var p=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"NativeAltarFixture")){
   @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source){return false;}
  };
  p.getInventory().clearContent();p.getInventory().selected=0;
  var pos=level.getSharedSpawnPos().above(24);level.getChunkAt(pos);p.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+1.5,0,0);
  level.setBlockAndUpdate(pos,NativeContent.INJECTOR.get().defaultBlockState());
  var maid=new EntityMaid(level);maid.setOwnerUUID(p.getUUID());maid.setModelId("contract_fox:fox_white");maid.moveTo(pos.above(),0,0);
  maid.getMaidInv().setStackInSlot(0,new ItemStack(Items.DIAMOND,19));maid.setExperience(47);
  var id=maid.getUUID();check(level.addFreshEntity(maid)&&level.getEntity(id)==maid,"source entity publication");
  var emptyInput=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("touhou_little_maid:smart_slab_empty")));
  p.setItemInHand(InteractionHand.MAIN_HAND,emptyInput);
  net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent(p,maid,emptyInput));
  var slab=p.getMainHandItem();check(NativeSoulSlabService.filled(slab)&&maid.isRemoved(),"native TLM soul slab interaction");var nativeCopy=slab.copy();
  p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
  var menu=new NativeMaidInjectorMenu(80,p.getInventory(),pos);
  menu.getSlot(0).set(new ItemStack(NativeContent.SWORD.get()));menu.getSlot(1).set(slab);
  check(menu.stillValid(p)&&menu.canInject(),"formal altar inputs");
  p.moveTo(pos.getX()+100,pos.getY(),pos.getZ(),0,0);
  check(!menu.clickMenuButton(p,0)&&slab.getCount()==1,"remote menu denied without consuming slab");
  p.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+1.5,0,0);
  check(menu.clickMenuButton(p,0),"normal native slab infusion");
  check(!menu.clickMenuButton(p,0),"duplicate button not paid/imported twice");
  var weapon=menu.getSlot(2).getItem();var empty=menu.getSlot(3).getItem();
  check(weapon.is(NativeContent.SWORD.get())&&NativeSoulSlabService.empty(empty),"formal outputs");
  check(!weapon.has(com.github.tartaricacid.touhoulittlemaid.init.InitDataComponent.MAID_INFO.get()),"full snapshot leaked into weapon");
  var original=NativeChannelService.selected(level.getServer(),p.getUUID(),weapon);
  check(original!=null&&original.maid().equals(id),"import identity");
  check(!NativeSoulSlabService.importInto(p,new ItemStack(NativeContent.SWORD.get()),nativeCopy.copy()),"duplicate native slab reimport");
  p.setItemInHand(InteractionHand.MAIN_HAND,nativeCopy.copy());
  var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
  var blocked=new PlayerInteractEvent.RightClickBlock(p,InteractionHand.MAIN_HAND,pos.below(),new BlockHitResult(Vec3.atCenterOf(pos.below()),Direction.UP,pos.below(),false));
  NativeSoulSlabService.interact(blocked);check(blocked.isCanceled(),"stale native slab cannot bypass ledger");
  var payment=NativeResonanceService.payStored(level.getServer(),p.getUUID(),weapon,125,NativeResonanceService.newAttempt());
  check(payment==com.maidweapon.port.domain.resonance.ResonanceRules.Payment.PAID,"formal payment");
  var input=CraftingInput.of(2,1,java.util.List.of(new ItemStack(Items.DIAMOND),weapon));
  var recipe=level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level).orElseThrow();
  var upgraded=recipe.value().assemble(input,level.registryAccess());
  check(upgraded.is(NativeContent.HEAVY.get())&&original.equals(NativeChannelService.displayReference(upgraded)),"native weapon upgrade lost contract components");
  menu.getSlot(2).set(ItemStack.EMPTY);menu.getSlot(3).set(ItemStack.EMPTY);
  menu.getSlot(0).set(upgraded);menu.getSlot(1).set(empty);
  var staleWeapon=upgraded.copy();
  check(menu.isExtracting()&&menu.clickMenuButton(p,0),"ordinary soul slab export");
  var blank=menu.getSlot(2).getItem();var transferred=menu.getSlot(3).getItem();
  check(transferred.get(com.github.tartaricacid.touhoulittlemaid.init.InitDataComponent.MAID_INFO.get()).copyTag().getString("model_id").equals("contract_fox:fox_white"),"native soul slab appearance preview lost model");
  p.setItemInHand(InteractionHand.MAIN_HAND,transferred);
  var openTable=new PlayerInteractEvent.RightClickBlock(p,InteractionHand.MAIN_HAND,pos,hit);
  NativeSoulSlabService.interact(openTable);check(!openTable.isCanceled(),"transferred soul slab stole the binding-table interaction");
  p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
  check(NativeChannelService.displayReference(blank)==null&&NativeSoulSlabService.filled(transferred),"extraction outputs");
  check(NativeChannelService.selected(level.getServer(),p.getUUID(),staleWeapon)==null,"old weapon copy remained authorized");
  check(NativeResonanceService.currentStored(level.getServer(),p.getUUID(),transferred)==875,"export reset resonance");
  menu.getSlot(2).set(ItemStack.EMPTY);menu.getSlot(3).set(ItemStack.EMPTY);
  menu.getSlot(0).set(blank);menu.getSlot(1).set(transferred);
  check(menu.clickMenuButton(p,0),"transferred slab rebind");
  var rebound=menu.getSlot(2).getItem();menu.getSlot(2).set(ItemStack.EMPTY);
  var ref=NativeChannelService.selected(level.getServer(),p.getUUID(),rebound);
  check(ref!=null&&ref.binding().equals(original.binding())&&ref.maid().equals(id)&&NativeResonanceService.currentStored(level.getServer(),p.getUUID(),rebound)==875,"rebind reset identity or resources");
  p.setItemInHand(InteractionHand.MAIN_HAND,rebound);
  var place=pos.offset(2,1,0);level.getChunkAt(place);
  var target=new BlockHitResult(Vec3.atCenterOf(place.below()),Direction.UP,place.below(),false);
  check(rebound.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(p,InteractionHand.MAIN_HAND,target))==InteractionResult.PASS
    &&rebound.getItem().use(level,p,InteractionHand.MAIN_HAND).getResult()==InteractionResult.PASS
    &&NativeChannelService.selected(level.getServer(),p.getUUID(),rebound)!=null,"right click bypassed maintained companion input");
  check(com.maidweapon.port.companion.NativeCompanionService.manifestRequested(p,rebound),"formal companion manifestation");
  var live=NativeChannelService.selectedLive(level.getServer(),p.getUUID(),rebound);
  check(live!=null&&live.getUUID().equals(id)&&live.getMaidInv().getStackInSlot(0).getCount()==19&&live.getExperience()==47&&live.getModelId().equals("contract_fox:fox_white"),"formal live inventory, model or XP changed");
  live=com.maidweapon.port.projection.NativeProjectionVerification.run(p,live,rebound);
  live=com.maidweapon.port.companion.NativeGuardVerification.run(p,live,rebound);
  var illegalSlab=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("touhou_little_maid:smart_slab_empty")));
  var steal=new com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent(p,live,illegalSlab);
  net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(steal);
  check(steal.isCanceled()&&!live.isRemoved()&&level.getEntity(id)==live,"native TLM slab capture bypassed live lease");
  check(rebound.getItem().interactLivingEntity(rebound,p,live,InteractionHand.MAIN_HAND)==InteractionResult.PASS,"bound blade intercepted native maid GUI");
  check(com.maidweapon.port.companion.NativeCompanionService.recallRequested(p,rebound,live),"formal companion recall");
  check(level.getEntity(id)==null&&NativeResonanceService.currentStored(level.getServer(),p.getUUID(),rebound)==875,"formal recall lost entity identity or balance");
  var releaseInput=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("touhou_little_maid:smart_slab_empty")));
  var releasedSlab=NativeSoulSlabService.extract(p,rebound,releaseInput);check(!releasedSlab.isEmpty(),"release slab extraction");
  var oldSlab=releasedSlab.copy();p.setItemInHand(InteractionHand.MAIN_HAND,releasedSlab);
  var releaseHit=new BlockHitResult(Vec3.atCenterOf(place.below()),Direction.UP,place.below(),false);
  var release=new PlayerInteractEvent.RightClickBlock(p,InteractionHand.MAIN_HAND,place.below(),releaseHit);
  NativeSoulSlabService.interact(release);
  var free=level.getEntity(id);
  check(release.isCanceled()&&free instanceof EntityMaid&&NativeSoulSlabService.empty(p.getMainHandItem()),"native soul slab release did not return an empty slab");
  var freeMaid=(EntityMaid)free;check(freeMaid.getMaidInv().getStackInSlot(0).getCount()==19,"release lost inventory");
  check(!NativeSoulSlabService.importInto(p,new ItemStack(NativeContent.SWORD.get()),oldSlab),"released soul slab copy revived");
  // The genuinely released maid can use TLM's normal slab interaction again.
  p.getCooldowns().removeCooldown(p.getMainHandItem().getItem());
  net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent(p,freeMaid,p.getMainHandItem()));
  var recaptured=p.getMainHandItem();check(NativeSoulSlabService.filled(recaptured)&&freeMaid.isRemoved(),"released maid cannot return through native TLM slab capture");
  var finalWeapon=new ItemStack(NativeContent.SWORD.get());check(NativeSoulSlabService.importInto(p,finalWeapon,recaptured),"released native maid rebind failed");
  check(NativeResonanceService.currentStored(level.getServer(),p.getUUID(),finalWeapon)==875,"native slab round trip reset pool");
  NativeConfigurationVerification.run(p,finalWeapon);
  menu.removed(p);check(menu.slots.subList(0,4).stream().noneMatch(s->s.hasItem()),"closing menu did not clear/return contents");
  p.getInventory().clearContent();level.removeBlock(pos,false);
  com.mojang.logging.LogUtils.getLogger().info("NATIVE_FORMAL_ALTAR_PASS: native TLM slab, distance/replay denial, upgrade components, transfer/rebind, 19 diamonds, 47 XP, 875 resonance, formal companion deployment, native GUI pass-through and recall");
 }
 private static void check(boolean value,String message){if(!value)throw new IllegalStateException("Formal gameplay fixture: "+message);}
}
