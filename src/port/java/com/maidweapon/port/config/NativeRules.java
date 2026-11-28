package com.maidweapon.port.config;

import com.maidweapon.port.domain.resonance.ResonanceRules;
import com.maidweapon.port.domain.resonance.SpiritVeinRules;
import com.maidweapon.port.domain.oath.OathRules;
import net.neoforged.neoforge.common.ModConfigSpec;
/** Platform configuration adapter. Pure shared defaults never override loaded user values. */
public final class NativeRules {
 public static ResonanceRules resonance(){
  if(!MaidWeaponConfig.SPEC.isLoaded())return ResonanceRules.DEFAULT;
  int maximum=ContractRulesConfig.SPEC.isLoaded()?ContractRulesConfig.POOL_MAX_RESONANCE.get():1000;
  int legacy=ContractRulesConfig.SPEC.isLoaded()?ContractRulesConfig.MAX_RESONANCE.get():200;
  return new ResonanceRules(maximum,legacy,MaidWeaponConfig.RESONANCE_STORED_RECOVERY.get(),MaidWeaponConfig.RESONANCE_PASSIVE_RECOVERY.get(),MaidWeaponConfig.RESONANCE_ABILITY_IDLE_RECOVERY.get(),MaidWeaponConfig.RESONANCE_COMBAT_RECOVERY.get(),MaidWeaponConfig.RESONANCE_MANIFESTED_COMBAT_RECOVERY.get());
 }
 public static double vein(int stage){
  if(!MaidWeaponConfig.SPEC.isLoaded())return SpiritVeinRules.multiplier(stage);
  var list=MaidWeaponConfig.RESONANCE_PROGRESS_MULTIPLIERS.get();
  return stage>=0&&stage<list.size()?list.get(stage):SpiritVeinRules.multiplier(stage);
 }
 public static double oath(ModConfigSpec.DoubleValue value,double fallback){return OathConfig.SPEC.isLoaded()?value.get():fallback;}
 public static int backlashTicks(){return OathConfig.SPEC.isLoaded()?OathConfig.BACKLASH_DURATION.get():OathRules.BACKLASH_TICKS;}
 public static int killReward(){return resonance().legacyUnits(ContractRulesConfig.SPEC.isLoaded()?ContractRulesConfig.KILL_REWARD.get():5);}
 public static com.maidweapon.port.domain.mastery.MasteryProgress.Rule masteryRule(int target){
  if(!ContractRulesConfig.SPEC.isLoaded()||target<2||target>10)return null;
  var rule=ContractRulesConfig.LEVELS[target];if(!rule.custom())return null;
  return new com.maidweapon.port.domain.mastery.MasteryProgress.Rule(rule.tier().get(),rule.kills().get(),new java.util.HashSet<>(rule.entities().get()),rule.mode().get().equals("entities"));
 }
 public static int combatTimeout(){return MaidWeaponConfig.SPEC.isLoaded()?MaidWeaponConfig.RESONANCE_RECOVERY_DELAY.get():100;}
 private NativeRules(){}
}
