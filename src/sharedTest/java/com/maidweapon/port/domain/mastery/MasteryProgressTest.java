package com.maidweapon.port.domain.mastery;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MasteryProgressTest{
    @Test void fullLegacyGrowth(){
        var p=MasteryProgress.INITIAL;
        for(int t=1;t<=4;t++)p=p.credit(t,"test:mob",null);
        assertEquals(5,p.level());
        p=p.credit(5,"minecraft:wither",null);assertEquals(5,p.level());
        p=p.credit(5,"minecraft:ender_dragon",null);assertEquals(6,p.level());
        p=p.credit(5,"minecraft:wither",null);assertEquals(7,p.level());
        p=p.credit(5,"minecraft:ender_dragon",null);p=p.credit(5,"minecraft:ender_dragon",null);
        p=p.credit(5,"minecraft:wither",null);assertEquals(8,p.level());
        p=p.credit(5,"minecraft:wither",null);assertEquals(9,p.level());
        while(p.dragonKills()+p.witherKills()<10)p=p.credit(5,"minecraft:wither",null);
        assertEquals(10,p.level());
    }
    @Test void customRule(){
        var r=new MasteryProgress.Rule(1,2,java.util.Set.of("test:boss"),true);
        var p=MasteryProgress.INITIAL.credit(5,"test:other",r);assertEquals(1,p.level());
        p=p.credit(0,"test:boss",r);assertEquals(1,p.level());assertEquals(1,p.progress().get(2));
        assertEquals(2,p.credit(0,"test:boss",r).level());
    }
}
