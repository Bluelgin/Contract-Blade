package com.maidweapon.forge.system.fox.challenge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class BlackFoxGuardCreditTest {
    @Test void ordinaryCutsAndRetaliationNeverEarnBreakCredit() {
        var credit = new BlackFoxParryCredit();
        assertFalse(credit.accept(BlackFoxFight.Skill.PROBE_CUT, 1, 0));
        assertFalse(credit.accept(BlackFoxFight.Skill.COUNTER_CUT, 2, 0));
        assertFalse(credit.accept(BlackFoxFight.Skill.APPROACH, 3, 0));
    }
    @Test void denseShotsShareOneCreditButCommittedRoundsAreDistinct() {
        var credit = new BlackFoxParryCredit();
        assertTrue(credit.accept(BlackFoxFight.Skill.TRIPLE_COMBO, 1, 0));
        for (int i=0;i<100;i++) assertFalse(credit.accept(BlackFoxFight.Skill.TRIPLE_COMBO, 1, 0));
        assertTrue(credit.accept(BlackFoxFight.Skill.TRIPLE_COMBO, 1, 1));
        assertTrue(credit.accept(BlackFoxFight.Skill.TRIPLE_COMBO, 1, 2));
        assertTrue(credit.accept(BlackFoxFight.Skill.TRIPLE_COMBO, 2, 0));
    }
    @Test void fifthDistinctSkillGuardKeepsShortRecoveryAndResets() {
        var credit = new BlackFoxParryCredit();
        var balance = new BlackFoxBalance();
        for(int i=0;i<5;i++) {
            assertTrue(credit.accept(BlackFoxFight.Skill.DIMENSION_STRIKE, i, 0));
            balance.parried(20L*i);
            assertEquals(i+1, balance.progress());
            if(i<4) assertFalse(balance.take());
        }
        assertTrue(balance.take()); assertFalse(balance.take());
        assertEquals(0,balance.progress());
        assertEquals(30,BlackFoxBalance.RECOVERY_TICKS);
    }
}
