package com.maidweapon.forge.system.fox.challenge;

import com.maidweapon.forge.api.BlackFoxEncounterApi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.deployment.ContractWeaponLocator;
import java.util.Map;
import java.util.WeakHashMap;

/** SlashBlade internally converts slash projectile hits into player damage sources. Preserve birth provenance. */
public final class BlackFoxAttackTrace {
    private static final Map<Entity, BlackFoxEncounterApi.HitContext> BIRTH = new WeakHashMap<>();
    private static final ThreadLocal<BlackFoxEncounterApi.HitContext> ACTIVE = new ThreadLocal<>();
    private static final ThreadLocal<Entity> PROJECTILE = new ThreadLocal<>();
    private record Melee(BlackFoxEncounterApi.HitContext context, long tick) { }
    private static final Map<Entity, Melee> MELEE = new WeakHashMap<>();
    public static void capture(Entity projectile, Entity shooter) {
        var context = carrier(shooter);
        if (context != null) BIRTH.put(projectile, context);
    }
    public static void melee(Entity shooter) {
        var context = carrier(shooter);
        if (context != null) MELEE.put(shooter, new Melee(context, shooter.level().getGameTime()));
    }
    public static BlackFoxEncounterApi.HitContext meleeContext(Entity shooter) {
        var record = MELEE.get(shooter);
        if (record == null || shooter.level().getGameTime() - record.tick() > 3) return null;
        var current = carrier(shooter);
        return current != null && current.originalBlade() == record.context().originalBlade() ? current : null;
    }
    private static BlackFoxEncounterApi.HitContext carrier(Entity shooter) {
        if (shooter instanceof ServerPlayer player && BlackFoxEncounters.get(player) != null)
            return new BlackFoxEncounterApi.HitContext(player, player.getMainHandItem());
        if (!(shooter instanceof TamableAnimal maid) || maid.getOwnerUUID() == null || shooter.getServer() == null) return null;
        var owner = shooter.getServer().getPlayerList().getPlayer(maid.getOwnerUUID());
        if (owner == null || BlackFoxEncounters.get(owner) == null) return null;
        String binding = shooter.getPersistentData().getString(TouhouLittleMaidHelper.TAG_ENTITY_BINDING_ID);
        var original = ContractWeaponLocator.findBoundWeaponByBinding(owner, binding);
        return ContractCarrierData.isOwner(original, owner)
                && shooter.getUUID().toString().equals(ContractCarrierData.getBoundMaidUUID(original))
                && SlashBladeCompat.isMatchingPhantom(original, maid.getMainHandItem())
                ? new BlackFoxEncounterApi.HitContext(owner, original) : null;
    }
    private static final ThreadLocal<Scope> SCOPES=new ThreadLocal<>();
    public static final class Scope implements AutoCloseable {
        final Scope previous;final Entity projectile;final BlackFoxEncounterApi.HitContext context;boolean closed;
        Scope(Entity entity){previous=SCOPES.get();projectile=PROJECTILE.get();context=ACTIVE.get();SCOPES.set(this);ACTIVE.set(BIRTH.get(entity));PROJECTILE.set(entity);}
        public void close(){if(closed)return;closed=true;if(SCOPES.get()!=this){SCOPES.remove();ACTIVE.remove();PROJECTILE.remove();return;}
            if(previous==null)SCOPES.remove();else SCOPES.set(previous);
            if(context==null)ACTIVE.remove();else ACTIVE.set(context);if(projectile==null)PROJECTILE.remove();else PROJECTILE.set(projectile);
        }
    }
    public static Scope open(Entity projectile){return new Scope(projectile);}
    public static void enter(Entity projectile){open(projectile);}
    public static void exit(){var scope=SCOPES.get();if(scope!=null)scope.close();else{ACTIVE.remove();PROJECTILE.remove();}}
    public static Entity projectile() { return PROJECTILE.get(); }
    public static BlackFoxEncounterApi.HitContext current() { return ACTIVE.get(); }
    public static void clear() { BIRTH.clear(); MELEE.clear(); SCOPES.remove();ACTIVE.remove();PROJECTILE.remove(); }
    private BlackFoxAttackTrace() { }
}
