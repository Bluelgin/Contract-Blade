package com.maidweapon.forge.system.fox;

import static org.junit.jupiter.api.Assertions.*;

import com.maidweapon.forge.api.ability.ContractAbilityRegistry;
import org.junit.jupiter.api.Test;

class FoxNumericalGrowthTest {
  @Test
  void blackAttackAndCoreUseOneBoundedTenLevelCurve() {
    var definition = BlackFoxSwordCoreGrowth.fallback();
    double previous = 0;
    for (int level = 1; level <= 10; level++) {
      var profile = new BlackFoxSwordCoreGrowth.Profile(definition.resolve(level));
      double bonus = profile.number("weapon_attack_bonus", 0);
      assertEquals(BlackFoxSwordCoreGrowth.attackBonus(level), bonus, 0.000001);
      assertTrue(bonus > previous);
      assertEquals(1 + bonus, profile.number("core_damage_multiplier",1), 0.000001);
      previous = bonus;
    }
    assertEquals(2, previous, 0.000001);
    assertEquals(2, BlackFoxSwordCoreGrowth.attackBonus(100), 0.000001);
    var registered = new ContractAbilityRegistry.Registration((p,c) -> true,
        BlackFoxSwordCoreGrowth.FEATURES,BlackFoxSwordCoreGrowth.PARAMETERS,definition);
    ContractAbilityRegistry.validate(BlackFoxSwordCoreGrowth.ABILITY,registered,definition);
  }

  @Test
  void whiteAlwaysGrowsAndGuardHasNoHpCeiling() {
    var definition = WhiteFoxBlessingGrowth.fallback();
    double previousHealth = 0, previousSpeed = 0, previousReduction = 0;
    for (int level = 1; level <= 10; level++) {
      var p = new WhiteFoxBlessingGrowth.Profile(definition.resolve(level));
      double hp = p.number("health_bonus"), speed = p.number("attack_speed_bonus"),
          reduction = p.number("guard_reduction");
      assertTrue(hp > previousHealth && speed > previousSpeed && reduction > previousReduction);
      assertEquals(WhiteFoxBlessingGrowth.healthBonus(level),hp,0.000001);
      assertEquals(WhiteFoxBlessingGrowth.attackSpeedBonus(level),speed,0.000001);
      assertEquals(1000*(1-reduction),p.guardedDamage(1000),0.0001);
      assertEquals(100*(1-reduction),p.guardedDamage(100),0.0001);
      assertEquals(60,p.ticks("guard_cooldown"));
      previousHealth=hp;previousSpeed=speed;previousReduction=reduction;
    }
    assertEquals(.5,previousHealth,0.000001);
    assertEquals(.3,previousSpeed,0.000001);
    assertEquals(.2,previousReduction,0.000001);
    var registered=new ContractAbilityRegistry.Registration((p,c)->true,
        WhiteFoxBlessingGrowth.FEATURES,WhiteFoxBlessingGrowth.PARAMETERS,definition);
    ContractAbilityRegistry.validate(WhiteFoxBlessingGrowth.ID,registered,definition);
  }
}
