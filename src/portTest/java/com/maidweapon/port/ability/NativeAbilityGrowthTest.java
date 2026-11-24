package com.maidweapon.port.ability;

import com.maidweapon.port.ability.progression.*;
import com.maidweapon.port.domain.resonance.SpiritCosts;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeAbilityGrowthTest{
    @Test void oldDataAndJavaFallbackAgree() throws Exception{
        var white=WhiteFoxBlessingGrowth.fallback();
        var black=BlackFoxSwordCoreGrowth.fallback();
        var red=AkatsukiDomainGrowth.fallback();
        var definitions=java.util.List.of(white,black,red);
        var names=java.util.List.of("white_fox_blessing","sealed_sword_core","akatsuki_domain");
        for(int i=0;i<names.size();i++){
            var resource="/data/maid_weapon/contract_ability_progression/"+names.get(i)+".json";
            try(var input=getClass().getResourceAsStream(resource)){
                assertNotNull(input);
                var parsed=ContractAbilityJson.parse(JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)));
                assertEquals(definitions.get(i).ability(),parsed.ability());
                for(int level=1;level<=10;level++){
                    var a=definitions.get(i).resolve(level);var b=parsed.resolve(level);
                    assertEquals(a.features(),b.features());
                    assertEquals(a.parameters().keySet(),b.parameters().keySet());
                    for(var key:a.parameters().keySet())assertEquals(a.parameters().get(key),b.parameters().get(key),1e-9);
                }
            }
        }
        assertEquals(5,SpiritCosts.scaledPoints(1000,SpiritCosts.Action.WHITE_CLEANSE.basisPoints));
        assertEquals(1,SpiritCosts.scaledPoints(1,SpiritCosts.Action.RED_DOMAIN_NORMAL.basisPoints));
    }
}
