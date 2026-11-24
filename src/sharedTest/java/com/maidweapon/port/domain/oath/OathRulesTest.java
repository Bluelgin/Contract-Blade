package com.maidweapon.port.domain.oath;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OathRulesTest{
    @Test void exactFortySecondBurnAndSmallPool(){
        for(int maximum:new int[]{1,200,1000,9999}){
            long rest=0;int spent=0;
            for(int i=0;i<800;i++){var step=OathRules.tick(maximum,OathRules.BURN,rest);spent+=step.cost();rest=step.remainder();}
            assertEquals(maximum,spent);
            assertEquals(0,rest);
        }
        assertEquals(.5,OathRules.outgoingPenalty(999));
        assertEquals(1.5,OathRules.incomingPenalty(999));
    }
}
