package com.maidweapon.port.mastery;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import static org.junit.jupiter.api.Assertions.*;
class NativeMasteryDataTest{
    @Test void independentAndPersistent(){
        var d=new NativeMasteryData();var id=UUID.randomUUID();
        assertEquals(2,d.credit(id,1,"minecraft:zombie").level());
        assertEquals(1,d.read(UUID.randomUUID()).level());
        var copy=NativeMasteryData.load(d.save(new CompoundTag(),null),null);
        assertEquals(d.read(id),copy.read(id));
    }
}
