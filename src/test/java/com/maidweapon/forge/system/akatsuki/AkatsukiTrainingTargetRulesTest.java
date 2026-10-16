package com.maidweapon.forge.system.akatsuki;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class AkatsukiTrainingTargetRulesTest {
  @Test
  void knownDummyNamesAndExplicitIdsWorkWithoutIncludingOtherEntities() {
    assertTrue(AkatsukiTrainingTargetRules.matches("dummmmmmy:target_dummy", List.of()));
    assertTrue(AkatsukiTrainingTargetRules.matches("example:training_dummy", List.of()));
    assertTrue(AkatsukiTrainingTargetRules.matches("example:combat_mannequin", List.of()));
    assertTrue(AkatsukiTrainingTargetRules.matches(
        "custom:practice_target", List.of("custom:practice_target")));
    assertFalse(AkatsukiTrainingTargetRules.matches("custom:practice_target", List.of()));
    assertFalse(AkatsukiTrainingTargetRules.matches("minecraft:warden", List.of()));
    assertFalse(AkatsukiTrainingTargetRules.matches("minecraft:villager", List.of()));
    assertFalse(AkatsukiTrainingTargetRules.matches("touhoulittlemaid:maid", List.of()));
  }
}
