package com.maidweapon.port.tlm;

import com.maidweapon.port.config.*;
import com.maidweapon.port.content.NativeContent;
import com.maidweapon.port.mastery.NativeMasteryData;
import com.maidweapon.port.resonance.NativeResonanceService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Disposable native fixture: never modifies production config handling. */
final class NativeConfigurationVerification {
 static void run(ServerPlayer p,ItemStack weapon)throws Exception{
  var world=p.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
  // Same isolation boundary as Forge's regression fixture: rapid config mutations
  // must not race NightConfig's asynchronous file-watch reload in this test world.
  var watcher=com.electronwill.nightconfig.core.file.FileWatcher.defaultInstance();
  watcher.removeWatch(world.resolve("serverconfig/maid_weapon-server.toml"));
  watcher.removeWatch(world.resolve("serverconfig/maid_weapon-oath-server.toml"));
  watcher.removeWatch(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("maid_weapon-common.toml"));
  int maximum=ContractRulesConfig.POOL_MAX_RESONANCE.get();
  double recovery=MaidWeaponConfig.RESONANCE_STORED_RECOVERY.get(),burn=OathConfig.BURN.get();
  var rule=ContractRulesConfig.LEVELS[2];var mode=rule.mode().get();var kills=rule.kills().get();var entities=rule.entities().get();
  try{
   ContractRulesConfig.POOL_MAX_RESONANCE.set(2000);MaidWeaponConfig.RESONANCE_STORED_RECOVERY.set(.8);OathConfig.BURN.set(.01);
   check(NativeRules.resonance().maximum()==2000&&NativeRules.resonance().stored()==.8&&NativeRules.oath(OathConfig.BURN,.025)==.01,"loaded values replaced by defaults");
   check(NativeResonanceService.currentStored(p.getServer(),p.getUUID(),weapon)==875,"configuration change reset existing pool");
   var crystals=new ItemStack(NativeContent.CRYSTAL.get(),2);p.setItemInHand(InteractionHand.MAIN_HAND,crystals);p.setItemInHand(InteractionHand.OFF_HAND,weapon);
   check(NativeContent.CRYSTAL.get().use(p.level(),p,InteractionHand.MAIN_HAND).getResult().consumesAction(),"configured crystal use failed");
   check(crystals.getCount()==1&&NativeResonanceService.currentStored(p.getServer(),p.getUUID(),weapon)==1375,"50 legacy units were not converted using configured maximum");
   rule.mode().set("entities");rule.kills().set(3);rule.entities().set(java.util.List.of("minecraft:zombie"));
   var mastery=NativeMasteryData.get(p.getServer());
   check(mastery.credit(p.getUUID(),5,"minecraft:creeper").level()==1,"custom entity list ignored");
   check(mastery.credit(p.getUUID(),1,"minecraft:zombie").level()==1,"custom kill threshold ignored");
   check(mastery.credit(p.getUUID(),1,"minecraft:zombie").level()==1,"custom kill threshold ignored twice");
   check(mastery.credit(p.getUUID(),1,"minecraft:zombie").level()==2,"custom progression did not execute");
  }finally{
   ContractRulesConfig.POOL_MAX_RESONANCE.set(maximum);MaidWeaponConfig.RESONANCE_STORED_RECOVERY.set(recovery);OathConfig.BURN.set(burn);
   rule.mode().set(mode);rule.kills().set(kills);rule.entities().set(entities);
   p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
  }
  com.mojang.logging.LogUtils.getLogger().info("NATIVE_CONFIGURATION_PASS: custom pool/rates/oath burn, 50 legacy -> 500 native crystal units, one item consumed, custom entity/kill progression, values restored");
 }
 private static void check(boolean v,String message){if(!v)throw new IllegalStateException("Native configuration fixture: "+message);}
}
