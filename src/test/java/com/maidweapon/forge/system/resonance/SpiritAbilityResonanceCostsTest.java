package com.maidweapon.forge.system.resonance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpiritAbilityResonanceCostsTest {
    @Test void costsAreScaledAcrossAllThreeSpiritRoutes() {
        var A = SpiritAbilityResonanceCosts.Action.class;
        assertEquals(50, SpiritAbilityResonanceCosts.scaled(1000,
                SpiritAbilityResonanceCosts.Action.BLACK_SUMMON));
        assertEquals(4, SpiritAbilityResonanceCosts.scaled(1000,
                SpiritAbilityResonanceCosts.Action.BLACK_UPKEEP));
        assertEquals(10, SpiritAbilityResonanceCosts.scaled(1000,
                SpiritAbilityResonanceCosts.Action.BLACK_MODE));
        assertEquals(5, SpiritAbilityResonanceCosts.scaled(1000,
                SpiritAbilityResonanceCosts.Action.WHITE_CLEANSE));
        assertEquals(7, SpiritAbilityResonanceCosts.scaled(1000,
                SpiritAbilityResonanceCosts.Action.WHITE_HEAL));
        assertEquals(50, SpiritAbilityResonanceCosts.scaled(1000,
                SpiritAbilityResonanceCosts.Action.RED_LIMIT_BREAK));
        assertEquals(80, SpiritAbilityResonanceCosts.scaled(1000,
                SpiritAbilityResonanceCosts.Action.RED_STELLAR_FINISH));
        assertEquals(20, SpiritAbilityResonanceCosts.scaled(1000,
                SpiritAbilityResonanceCosts.Action.RED_CORE_BURST));
        assertEquals(2, SpiritAbilityResonanceCosts.scaled(1000,
                SpiritAbilityResonanceCosts.Action.RED_DOMAIN_NORMAL));
        assertEquals(4, SpiritAbilityResonanceCosts.scaled(1000,
                SpiritAbilityResonanceCosts.Action.RED_DOMAIN_RAMPAGE));
        assertEquals(150, SpiritAbilityResonanceCosts.scaledPoints(1000, 1500));
    }
    @Test void tinyServerPoolsNeverGetZeroCostAndInvalidCapFailsClosed() {
        assertEquals(1, SpiritAbilityResonanceCosts.scaledPoints(3, 15));
        assertEquals(0, SpiritAbilityResonanceCosts.scaledPoints(3, 0));
        assertEquals(Integer.MAX_VALUE, SpiritAbilityResonanceCosts.scaledPoints(0, 600));
        assertEquals(Integer.MAX_VALUE, SpiritAbilityResonanceCosts.scaledPoints(1000, -10));
    }
}
