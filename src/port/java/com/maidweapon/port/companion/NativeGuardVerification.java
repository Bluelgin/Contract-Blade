package com.maidweapon.port.companion;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidweapon.port.config.ContractRulesConfig;
import com.maidweapon.port.content.NativeContent;
import com.maidweapon.port.storage.NativeChannelService;
import com.maidweapon.port.tlm.PortContractLifecycleTrial;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
/** Disposable native health-damage regression, never run by ordinary gameplay. */
public final class NativeGuardVerification {
 public static EntityMaid run(ServerPlayer owner,EntityMaid maid,ItemStack carrier)throws Exception{
  var level=owner.serverLevel();var id=maid.getUUID();var task=maid.getTask();var schedule=maid.getSchedule();
  var slots=maid.getMaidBauble();var original=new java.util.ArrayList<ItemStack>();
  for(int i=0;i<slots.getSlots();i++){original.add(slots.getStackInSlot(i).copy());slots.setStackInSlot(i,ItemStack.EMPTY);}
  var clock=(net.minecraft.world.level.storage.ServerLevelData)level.getLevelData();long time=clock.getGameTime();int ticks=owner.tickCount;
  var previous=ContractRulesConfig.AUTO_MANIFEST_MODE.get();int cooldown=ContractRulesConfig.HURT_COOLDOWN.get();
  com.electronwill.nightconfig.core.file.FileWatcher.defaultInstance().removeWatch(owner.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("serverconfig/maid_weapon-server.toml"));
  // FakePlayer never ticks down vanilla's first-login immunity. End that
  // disposable setup period explicitly, then use the unmodified hurt pipeline.
  var immunity=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");immunity.setAccessible(true);immunity.setInt(owner,0);
  var absorption=owner.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_ABSORPTION);double absorptionLimit=absorption.getBaseValue();
  var attacker=new Zombie(level);owner.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
  try{
   ContractRulesConfig.AUTO_MANIFEST_MODE.set("bauble");ContractRulesConfig.HURT_COOLDOWN.set(200);
   check(NativeCompanionService.recallRequested(owner,carrier,maid),"prepare sealed companion");
   owner.getInventory().setItem(8,new ItemStack(NativeContent.GUARD.get()));
   damage(owner,attacker);NativeGuardService.tick(owner);
   check(NativeChannelService.selected(owner.getServer(),owner.getUUID(),carrier)!=null,"player inventory charm granted native maid permission");
   maid=NativeCompanionService.manifestRequested(owner,carrier)?NativeChannelService.selectedLive(owner.getServer(),owner.getUUID(),carrier):null;
   check(maid!=null,"equip real native charm");maid.getMaidBauble().setStackInSlot(0,new ItemStack(NativeContent.GUARD.get()));
   check(NativeCompanionService.recallRequested(owner,carrier,maid),"store native charm");
   ContractRulesConfig.AUTO_MANIFEST_MODE.set("disabled");damage(owner,attacker);NativeGuardService.tick(owner);
   check(NativeChannelService.selected(owner.getServer(),owner.getUUID(),carrier)!=null,"disabled policy ignored");
   ContractRulesConfig.AUTO_MANIFEST_MODE.set("bauble");owner.setHealth(20);owner.invulnerableTime=0;
   owner.hurt(owner.damageSources().fall(),2);NativeGuardService.tick(owner);
   check(NativeChannelService.selected(owner.getServer(),owner.getUUID(),carrier)!=null,"environmental injury summoned");
   owner.setHealth(20);owner.invulnerableTime=0;absorption.setBaseValue(4);owner.setAbsorptionAmount(4);
   check(owner.getAbsorptionAmount()==4,"native absorption fixture setup");
   owner.hurt(owner.damageSources().mobAttack(attacker),2);NativeGuardService.tick(owner);owner.setAbsorptionAmount(0);
   check(owner.getHealth()==20&&NativeChannelService.selected(owner.getServer(),owner.getUUID(),carrier)!=null,"absorbed hit without health loss summoned");
   damage(owner,attacker);NativeGuardService.tick(owner);maid=NativeChannelService.selectedLive(owner.getServer(),owner.getUUID(),carrier);
   check(maid!=null&&maid.getUUID().equals(id)&&maid.getTask()==task&&maid.getSchedule()==schedule,"actual injury did not summon same maid or changed work/schedule");
   check(owner.getHealth()<20&&!com.maidweapon.port.oath.NativeOathService.active(owner)&&maid.getPersistentData().getCompound("MaidWeaponCompanion").getString("Mode").equals("GUARD"),"guard healed, started oath or lost intent");
   check(NativeCompanionService.recallRequested(owner,carrier,maid),"manual recall guard");
   damage(owner,attacker);NativeGuardService.tick(owner);
   check(NativeChannelService.selected(owner.getServer(),owner.getUUID(),carrier)!=null,"cooldown ignored");
   clock.setGameTime(time+201);damage(owner,attacker);NativeGuardService.tick(owner);maid=NativeChannelService.selectedLive(owner.getServer(),owner.getUUID(),carrier);
   check(maid!=null,"cooldown never expired");maid.setTarget(null);
   clock.setGameTime(time+602);owner.tickCount=5;NativeGuardService.tick(owner);
   check(NativeChannelService.selected(owner.getServer(),owner.getUUID(),carrier)!=null&&level.getEntity(id)==null,"safe expiry did not recall");
   check(NativeCompanionService.manifestRequested(owner,carrier),"restore manual state");maid=NativeChannelService.selectedLive(owner.getServer(),owner.getUUID(),carrier);
   for(int i=0;i<original.size();i++)maid.getMaidBauble().setStackInSlot(i,original.get(i));
   com.mojang.logging.LogUtils.getLogger().info("NATIVE_GUARD_PASS: real native health-loss event, maid-only bauble permission, disabled/environment/absorption denial, original task/schedule, cooldown and safe timed recall");
   return maid;
  }finally{
   clock.setGameTime(time);owner.tickCount=ticks;ContractRulesConfig.AUTO_MANIFEST_MODE.set(previous);ContractRulesConfig.HURT_COOLDOWN.set(cooldown);
   NativeGuardService.disconnect(owner);owner.setAbsorptionAmount(0);absorption.setBaseValue(absorptionLimit);owner.setHealth(20);owner.getInventory().setItem(8,ItemStack.EMPTY);
  }
 }
 private static void damage(ServerPlayer owner,Zombie attacker){owner.setHealth(20);owner.invulnerableTime=0;check(owner.hurt(owner.damageSources().mobAttack(attacker),2)&&owner.getHealth()<20,"native injury not applied");}
 private static void check(boolean v,String m){if(!v)throw new IllegalStateException("Native guard fixture: "+m);}
 private NativeGuardVerification(){}
}
