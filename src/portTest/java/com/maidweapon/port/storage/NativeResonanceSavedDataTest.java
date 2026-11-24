package com.maidweapon.port.storage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import com.maidweapon.port.domain.resonance.ResonanceRules;
import com.maidweapon.port.domain.resonance.ResonanceRules.Payment;

class NativeResonanceSavedDataTest {
    @Test void isolatedPoolsAndPersistentIdentity() {
        var d=new NativeResonanceSavedData(); var a=UUID.randomUUID(); var b=UUID.randomUUID();
        var owner=UUID.randomUUID(); var binding=UUID.randomUUID(); var rules=ResonanceRules.DEFAULT;
        assertEquals(Payment.PAID,d.pay(a,owner,binding,rules,275,false));
        assertEquals(Payment.INSUFFICIENT,d.pay(a,owner,binding,rules,726,false));
        assertEquals(1000,d.current(b,owner,UUID.randomUUID(),rules));
        var loaded=NativeResonanceSavedData.load(d.save(new CompoundTag(),null),null);
        assertEquals(725,loaded.current(a,owner,binding,rules));
        assertThrows(IllegalStateException.class,()->loaded.current(a,UUID.randomUUID(),binding,rules));
        assertThrows(IllegalStateException.class,()->loaded.current(a,owner,UUID.randomUUID(),rules));
        assertEquals(1000,loaded.recover(a,owner,binding,rules,Integer.MAX_VALUE));
    }
    @Test void futureSchemaRetained() {
        var f=new CompoundTag(); f.putInt("schema",99); f.putString("foreign","keep");
        var d=NativeResonanceSavedData.load(f,null);
        assertThrows(IllegalStateException.class,()->d.current(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),ResonanceRules.DEFAULT));
        assertEquals(f,d.save(new CompoundTag(),null));
    }
}
