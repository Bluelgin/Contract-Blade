package com.maidweapon.port.storage;
import com.maidweapon.port.domain.resonance.SpiritVeinRules;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NativeSpiritVeinDataTest{
    @Test void sequentialAndPersistent(){
        var d=new NativeSpiritVeinData();var owner=UUID.randomUUID();
        assertEquals(0,d.stage(owner));
        assertEquals(SpiritVeinRules.Upgrade.SKIPPED,d.upgrade(owner,2));
        assertEquals(SpiritVeinRules.Upgrade.SUCCESS,d.upgrade(owner,1));
        assertEquals(SpiritVeinRules.Upgrade.ALREADY,d.upgrade(owner,1));
        var reload=NativeSpiritVeinData.load(d.save(new CompoundTag(),null),null);
        assertEquals(1.25,SpiritVeinRules.multiplier(reload.stage(owner)));
        assertEquals(0,reload.stage(UUID.randomUUID()));
    }
}
