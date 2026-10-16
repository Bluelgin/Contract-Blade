package com.maidweapon.forge.system.mastery;

import com.google.gson.JsonParser;
import com.maidweapon.forge.api.ability.*;
import com.maidweapon.forge.system.fox.*;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.maidweapon.forge.system.fox.BlackFoxSwordCoreGrowth.*;

class BlackFoxSwordCoreGrowthTest {
    private Profile at(int level) { return new Profile(fallback().resolve(level)); }
    @Test void tenDistinctCumulativeMilestonesMatchShippedDatapack() throws Exception {
        var registration = new ContractAbilityRegistry.Registration((p,c) -> false,FEATURES,PARAMETERS,fallback());
        ContractAbilityRegistry.validate(ABILITY,registration,fallback());
        try (var input = getClass().getResourceAsStream("/data/maid_weapon/contract_ability_progression/sealed_sword_core.json")) {
            assertNotNull(input);
            var datapack = ContractAbilityJson.parse(JsonParser.parseReader(new InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)));
            ContractAbilityRegistry.validate(ABILITY,registration,datapack);
            Set<net.minecraft.resources.ResourceLocation> previous = Set.of();
            for (int level=1; level<=10; level++) {
                var capabilities = fallback().resolve(level);
                assertEquals(capabilities,datapack.resolve(level));
                assertTrue(capabilities.features().containsAll(previous));
                assertTrue(capabilities.features().size()>previous.size(),"Every level adds an executable behavior");
                assertTrue(at(level).barrageRadius()>at(level).comboRadius()); previous=capabilities.features();
            }
        }
    }
    @Test void modeGatesFollowHeightAndHunterCapsAreIndependent() {
        for (int level=1;level<=10;level++) {
            assertEquals(level>=5,at(level).has(COMBO));
            assertEquals(level>=6?PositionMode.OWNER_FOLLOW:PositionMode.ANCHORED,at(level).positionMode());
            assertEquals(level<5?0:level<7?1:3,at(level).hunters());
            assertEquals(16,at(level).barrageRadius()); assertEquals(8,at(level).comboRadius());
        }
        assertTrue(at(8).has(DRIVE));assertTrue(at(8).has(VERTICAL));assertTrue(at(8).has(WAVE));
        assertFalse(at(9).has(SYNC));assertTrue(at(10).has(SYNC));
        assertEquals(400,BlackFoxSwordCoreTimeline.DURATION);
    }
    @Test void priorityFeaturesAppearAtTheirOwnMilestoneAndDoNotBypassRange() {
        assertEquals(0,BlackFoxSwordCoreTargeting.priority(at(1),true,true,true,true));
        assertEquals(1,BlackFoxSwordCoreTargeting.priority(at(2),true,true,true,true));
        assertEquals(2,BlackFoxSwordCoreTargeting.priority(at(3),true,true,true,true));
        assertEquals(3,BlackFoxSwordCoreTargeting.priority(at(8),true,true,true,true));
        assertEquals(4,BlackFoxSwordCoreTargeting.priority(at(9),true,true,true,true));
        assertEquals(0,BlackFoxSwordCoreTargeting.priority(at(10),false,false,false,false));
    }
    @Test void deniedUnknownOrRouteWithoutSummonCannotExecute() {
        for (var status:ContractAbilityProgressionService.Status.values())
            assertNull(ready(new ContractAbilityProgressionService.Result(status,10,ContractAbilityFeatureSet.EMPTY)));
        assertNull(ready(null));
        assertNull(ready(new ContractAbilityProgressionService.Result(ContractAbilityProgressionService.Status.DENIED,10,fallback().resolve(10))));
        assertNotNull(ready(new ContractAbilityProgressionService.Result(ContractAbilityProgressionService.Status.READY,1,fallback().resolve(1))));
    }
    @Test void defensiveProfileBoundsPreventUnboundedOrEqualRangeAndHunterOverrides() {
        var bad = new Profile(new ContractAbilityFeatureSet(FEATURES,Map.of(id("max_hunters"),99d,id("combo_radius"),99d,
                id("barrage_radius"),1d,id("barrage_interval"),Double.NaN)));
        assertEquals(3,bad.hunters());assertEquals(12,bad.barrageRadius());assertEquals(10,bad.comboRadius());
        assertEquals(12,bad.count("barrage_interval",12));
    }
}
