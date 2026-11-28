package com.maidweapon.port.fox;
import com.maidweapon.port.ability.progression.WhiteFoxBlessingGrowth;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NativeWhiteFoxRulesTest{
    @Test void guardsAndCooldown(){
        var f=new WhiteFoxBlessingGrowth.Profile(WhiteFoxBlessingGrowth.fallback().resolve(10));
        assertEquals(80f,f.guardedDamage(100f),.0001f);
        var state=new WhiteFoxGuardRuntime();
        assertTrue(state.consume(WhiteFoxGuardRuntime.Action.GUARD,1,f.ticks("guard_cooldown")));
        assertFalse(state.consume(WhiteFoxGuardRuntime.Action.GUARD,2,f.ticks("guard_cooldown")));
        state.suspend();assertFalse(state.consume(WhiteFoxGuardRuntime.Action.GUARD,3,f.ticks("guard_cooldown")));
        for(int i=0;i<5;i++)assertFalse(state.hit(100+i*10,6,100,true));
        assertTrue(state.hit(150,6,100,true));
    }
}
