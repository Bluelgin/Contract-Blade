package com.maidweapon.port.domain.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Java 17 compatible. One server-thread session, no Minecraft types.
 * Payment is committed before effect. Failed/throwing effects are not refunded
 * automatically; partial side effects cannot be proved absent.
 */
public final class AbilityEngine {
    public enum Result { SUCCESS, UNAUTHORIZED, UNKNOWN_ABILITY, COOLDOWN, INELIGIBLE, INSUFFICIENT, EFFECT_FAILED, BUSY }
    public record Definition(String id,int cost,int cooldownTicks) {
        public Definition {
            if(id==null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || id.length()>128
                || cost<0 || cost>1_000_000 || cooldownTicks<0 || cooldownTicks>72000)
                throw new IllegalArgumentException("Invalid ability definition");
        }
    }
    public record Identity(UUID owner,UUID maid,UUID binding) {
        public Identity { Objects.requireNonNull(owner); Objects.requireNonNull(maid); Objects.requireNonNull(binding); }
    }
    private record Key(Identity identity,String ability){}
    private final Map<String,Definition> definitions=new HashMap<>();
    private final Map<Key,Long> cooldowns=new HashMap<>();
    private final java.util.Set<Identity> executing=new java.util.HashSet<>();
    private static final int MAX_DEFINITIONS=1024, MAX_COOLDOWNS=65536;
    public void register(Definition definition){
        Objects.requireNonNull(definition);
        if(definitions.size()>=MAX_DEFINITIONS || definitions.putIfAbsent(definition.id(),definition)!=null)
            throw new IllegalStateException("Ability registry full or duplicate");
    }
    public Result execute(String id,Identity identity,long tick,BooleanSupplier authorized,
                          BooleanSupplier eligible,Supplier<Boolean> pay,BooleanSupplier effect){
        if(identity==null || tick<0 || !authorized.getAsBoolean()) return Result.UNAUTHORIZED;
        Definition definition=definitions.get(id);
        if(definition==null) return Result.UNKNOWN_ABILITY;
        Key key=new Key(identity,id);
        if(cooldowns.getOrDefault(key,0L)>tick) return Result.COOLDOWN;
        if(!executing.add(identity)) return Result.BUSY;
        try {
            if(!eligible.getAsBoolean() || !authorized.getAsBoolean()) return Result.INELIGIBLE;
            if(definition.cooldownTicks()>0 && !cooldowns.containsKey(key) && cooldowns.size()>=MAX_COOLDOWNS)
                return Result.BUSY;
            if(!Boolean.TRUE.equals(pay.get())) return Result.INSUFFICIENT;
            if(definition.cooldownTicks()>0) cooldowns.put(key,tick>Long.MAX_VALUE-definition.cooldownTicks()
                ?Long.MAX_VALUE:tick+definition.cooldownTicks());
            try {return effect.getAsBoolean()?Result.SUCCESS:Result.EFFECT_FAILED;}
            catch(RuntimeException failure){return Result.EFFECT_FAILED;}
        } finally {executing.remove(identity);}
    }
    public Definition definition(String id){return definitions.get(id);}
    public void prune(long tick){cooldowns.entrySet().removeIf(e->e.getValue()<=tick);}
}
