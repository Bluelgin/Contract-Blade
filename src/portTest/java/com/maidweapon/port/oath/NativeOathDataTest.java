package com.maidweapon.port.oath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.CompoundTag;
class NativeOathDataTest{
    @Test void restAndFractionsPersist(){
        var d=new NativeOathData();var owner=UUID.randomUUID();var maid=UUID.randomUUID();var other=UUID.randomUUID();
        d.remainder(owner,maid,250000000);d.exhaust(owner,maid);
        assertTrue(d.resting(owner,maid));assertFalse(d.resting(owner,other));assertEquals(1,d.penalty(owner));
        var reloaded=NativeOathData.load(d.save(new CompoundTag(),null),null);
        assertEquals(250000000,reloaded.remainder(owner,maid));
        for(int i=0;i<3600;i++)reloaded.tick(owner);
        assertFalse(reloaded.resting(owner,maid));assertEquals(0,reloaded.penalty(owner));
    }
}
