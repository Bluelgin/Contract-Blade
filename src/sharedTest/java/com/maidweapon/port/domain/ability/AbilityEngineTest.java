package com.maidweapon.port.domain.ability;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class AbilityEngineTest {
    @Test void authorityCooldownAndFailureCannotDuplicatePayment(){
        var e=new AbilityEngine(); e.register(new AbilityEngine.Definition("test:skill",25,10));
        var i=new AbilityEngine.Identity(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        var paid=new AtomicInteger();
        java.util.function.Supplier<Boolean> pay=()->{paid.incrementAndGet();return true;};
        assertEquals(AbilityEngine.Result.UNAUTHORIZED,e.execute("test:skill",i,1,()->false,()->true,pay,()->true));
        assertEquals(0,paid.get());
        assertEquals(AbilityEngine.Result.EFFECT_FAILED,e.execute("test:skill",i,1,()->true,()->true,pay,()->{throw new IllegalStateException();}));
        assertEquals(AbilityEngine.Result.COOLDOWN,e.execute("test:skill",i,2,()->true,()->true,pay,()->true));
        assertEquals(1,paid.get());
        assertEquals(AbilityEngine.Result.SUCCESS,e.execute("test:skill",i,11,()->true,()->true,pay,()->true));
        assertEquals(2,paid.get());
    }
    @Test void rejectedPaymentDoesNotStartCooldown(){
        var e=new AbilityEngine();e.register(new AbilityEngine.Definition("test:skill",25,10));
        var i=new AbilityEngine.Identity(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        assertEquals(AbilityEngine.Result.INSUFFICIENT,e.execute("test:skill",i,1,()->true,()->true,()->false,()->true));
        assertEquals(AbilityEngine.Result.SUCCESS,e.execute("test:skill",i,1,()->true,()->true,()->true,()->true));
    }
}
