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
  void upgradesReplaceRatherThanAddAndDamageIsMultiplicative() {
    for (int lv = 1; lv <= 10; lv++) {
      var p = new WhiteFoxBlessingGrowth.Profile(WhiteFoxBlessingGrowth.fallback().resolve(lv));
      assertEquals(lv < 6 ? .03 : .05, p.number("move_speed"));
      assertEquals(lv < 3 ? 0 : lv < 8 ? .03 : .06, p.number("attack_speed"));
      assertEquals(lv < 4 ? 0 : lv < 9 ? .03 : .06, p.number("damage_reduction"));
      assertEquals(
          100 * (lv < 2 ? 1 : .9) * (lv < 4 ? 1 : lv < 9 ? .97 : .94), p.damage(100, true), .0001);
      assertEquals(lv >= 5, p.has("blessing_heal"));
      assertEquals(lv == 10, p.has("blessing_oath"));
    }
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
    assertEquals(tag, data.save(new CompoundTag()));
  }
}
