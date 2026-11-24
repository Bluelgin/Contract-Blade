package com.maidweapon.port.domain.resonance;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeResonanceRulesTest {
    @Test void defaultsAndRecovery() {
        var r=ResonanceRules.DEFAULT;
        assertEquals(1000,r.maximum()); assertEquals(25,r.legacyUnits(5));
        assertEquals(500,ResonanceRules.convertLegacy(100,200,1000));
        var a=new ResonanceAccumulator(); int restored=0;
        for(int i=0;i<40;i++) restored+=a.tick(i,r.recoveryUnits(false,ResonanceRules.Activity.STORED,1),restored,1000);
        assertEquals(5,restored);
        assertEquals(0,a.tick(39,Long.MAX_VALUE,restored,1000));
        assertEquals(0,a.tick(38,Long.MAX_VALUE,restored,1000));
        assertEquals(995,a.tick(1000,Long.MAX_VALUE,restored,1000));
        assertEquals(1_000_000,r.recoveryUnits(true,ResonanceRules.Activity.ABILITY_IDLE,1));
        assertEquals(500_000,r.recoveryUnits(true,ResonanceRules.Activity.COMBAT,1));
    }
    @Test void authorizationBeforeWaiver() {
        assertEquals(ResonanceRules.Payment.UNAUTHORIZED,ResonanceRules.evaluate(false,true,100,1));
        assertEquals(ResonanceRules.Payment.INVALID_COST,ResonanceRules.evaluate(true,true,100,-1));
    }
}
