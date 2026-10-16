package com.maidweapon.forge.system.contract;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.maidweapon.forge.api.ability.*;
import com.maidweapon.forge.system.fox.*;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class WhiteFoxBlessingTest {
  @Test
  void shippedRouteMatchesFallbackAtEveryLevel() throws Exception {
    var registration =
        new ContractAbilityRegistry.Registration(
            (p, c) -> false,
            WhiteFoxBlessingGrowth.FEATURES,
            WhiteFoxBlessingGrowth.PARAMETERS,
            WhiteFoxBlessingGrowth.fallback());
    try (var input =
        getClass()
            .getResourceAsStream(
                "/data/maid_weapon/contract_ability_progression/white_fox_blessing.json")) {
      var route =
          ContractAbilityJson.parse(JsonParser.parseReader(new java.io.InputStreamReader(input)));
      ContractAbilityRegistry.validate(WhiteFoxBlessingGrowth.ID, registration, route);
      for (int lv = 1; lv <= 10; lv++)
        assertEquals(WhiteFoxBlessingGrowth.fallback().resolve(lv), route.resolve(lv));
    }
  }

  @Test
  void eachLevelAddsAPhysicalAbilityAndGuardHasAbsoluteCap() {
    String[] route = {
      "guard",
      "cleanse",
      "heal",
      "mobility",
      "lantern",
      "edge",
      "resonance",
      "domain",
      "crisis",
      "oath"
    };
    for (int lv = 1; lv <= 10; lv++) {
      var p = new WhiteFoxBlessingGrowth.Profile(WhiteFoxBlessingGrowth.fallback().resolve(lv));
      for (int i = 0; i < 10; i++) assertEquals(i < lv, p.has("blessing_" + route[i]));
      assertEquals(8.5f, p.guardedDamage(10), .0001);
      assertEquals(997f, p.guardedDamage(1000), .0001);
      assertEquals(0, p.guardedDamage(0));
      assertTrue(Float.isNaN(p.guardedDamage(Float.NaN)));
    }
  }

  @Test
  void crisisReceiptIsIndependentAndDurable() {
    var data = WhiteFoxBlessingCooldownData.load(new CompoundTag());
    var owner = UUID.randomUUID();
    assertTrue(data.consumeCrisis(owner, 100, 1200));
    assertTrue(data.consume(owner, 100, 12000));
    data = WhiteFoxBlessingCooldownData.load(data.save(new CompoundTag()));
    assertFalse(data.consumeCrisis(owner, 1299, 1200));
    assertTrue(data.consumeCrisis(owner, 1300, 1200));
    assertFalse(data.ready(owner, 1300));
  }

  @Test
  void cooldownSurvivesReloadAndIsSharedByHandsAndIsolatedByOwner() {
    var data = WhiteFoxBlessingCooldownData.load(new CompoundTag());
    var a = UUID.randomUUID();
    var b = UUID.randomUUID();
    assertTrue(data.consume(a, 100, 12000));
    assertFalse(data.consume(a, 100, 12000));
    assertTrue(data.ready(b, 100));
    data = WhiteFoxBlessingCooldownData.load(data.save(new CompoundTag()));
    assertFalse(data.ready(a, 12099));
    assertTrue(data.ready(a, 12100));
  }

  @Test
  void futureCooldownSchemaCannotResetProtection() {
    var tag = new CompoundTag();
    tag.putInt("Schema", 42);
    tag.putString("Future", "retained");
    var data = WhiteFoxBlessingCooldownData.load(tag);
    assertFalse(data.consume(UUID.randomUUID(), 100, 12000));
    assertFalse(data.consumeCrisis(UUID.randomUUID(), 100, 1200));
    assertEquals(tag, data.save(new CompoundTag()));
  }
}
