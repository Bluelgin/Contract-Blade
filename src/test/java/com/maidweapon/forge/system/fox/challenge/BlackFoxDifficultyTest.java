package com.maidweapon.forge.system.fox.challenge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class BlackFoxDifficultyTest {
    @Test void appliesOneGlobalMultiplierAndNativeReductionOnlyWhenExplicit() {
        assertEquals(10, BlackFoxDamageTuning.scaled(10,1,false));
        assertEquals(5, BlackFoxDamageTuning.scaled(10,.5,false));
        assertEquals(6.5, BlackFoxDamageTuning.scaled(10,1,true),.0001);
        assertEquals(3.25, BlackFoxDamageTuning.scaled(10,.5,true),.0001);
        assertEquals(20, BlackFoxDamageTuning.scaled(10,2,false));
        assertEquals(0, BlackFoxDamageTuning.scaled(10,0,true));
    }
    @Test void invalidInputsCannotCreateHealingOrNonFiniteDamage() {
        assertEquals(0,BlackFoxDamageTuning.scaled(Float.NaN,1,false));
        assertEquals(0,BlackFoxDamageTuning.scaled(-1,1,false));
        assertEquals(0,BlackFoxDamageTuning.scaled(10,Double.NaN,false));
        assertEquals(0,BlackFoxDamageTuning.scaled(10,-1,false));
        assertTrue(Float.isFinite(BlackFoxDamageTuning.scaled(Float.MAX_VALUE,10,true)));
    }
    @Test void reducedHeavyAttacksRemainHeavierThanOrdinaryCuts() {
        assertEquals(14,BlackFoxDamageTuning.IAIDO);
        assertEquals(10,BlackFoxDamageTuning.DIMENSION);
        assertEquals(12,BlackFoxDamageTuning.CORE_TIMEOUT);
        assertTrue(BlackFoxDamageTuning.IAIDO>BlackFoxDamageTuning.DIMENSION);
        assertTrue(BlackFoxDamageTuning.DIMENSION>BlackFoxDamageTuning.LIGHT);
    }
}
