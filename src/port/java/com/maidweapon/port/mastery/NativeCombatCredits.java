package com.maidweapon.port.mastery;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidweapon.port.domain.mastery.MonsterTierRegistry;
import com.maidweapon.port.domain.resonance.ResonanceRules;
import com.maidweapon.port.resonance.NativeResonanceService;
import com.maidweapon.port.storage.ContractReference;
import com.maidweapon.port.storage.NativeChannelService;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Strict direct-hit subset of Forge attribution. No projectile or third-party area attack inference. */
public final class NativeCombatCredits {
    private record Credit(ServerPlayer owner,ContractReference ref,EntityMaid live){}
    private static final Map<LivingEntity,Credit> HITS=new WeakHashMap<>();
    private static final java.util.Set<LivingEntity> SETTLED=java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<ServerPlayer,Map<UUID,Long>> COMBAT=new WeakHashMap<>();
    private static Credit resolve(DamageSource source){
        if(source.getEntity()==null||source.getEntity()!=source.getDirectEntity())return null;
        if(source.getEntity() instanceof ServerPlayer p && p.isAlive()&&!p.isSpectator()&&p.getServer()!=null){
            var ref=NativeChannelService.selected(p.getServer(),p.getUUID(),p.getMainHandItem());
            return ref==null?null:new Credit(p,ref,null);
        }
        if(source.getEntity() instanceof EntityMaid maid && maid.getServer()!=null && maid.getOwnerUUID()!=null){
            var p=maid.getServer().getPlayerList().getPlayer(maid.getOwnerUUID());
            if(p==null||!p.isAlive()||p.isSpectator()||NativeChannelService.selectedLive(p.getServer(),p.getUUID(),p.getMainHandItem())!=maid)return null;
            return new Credit(p,NativeChannelService.displayReference(p.getMainHandItem()),maid);
        }
        return null;
    }
    private static void report(Credit c){
        if(c==null||c.ref()==null)return;
        long now=c.owner().getServer().overworld().getGameTime();
        var map=COMBAT.computeIfAbsent(c.owner(),p->new java.util.HashMap<>());
        map.values().removeIf(t->t<=now);
        if(map.size()<512||map.containsKey(c.ref().maid()))map.put(c.ref().maid(),now+100);
    }
    public static boolean fighting(ServerPlayer p,UUID maid,long now){
        var map=COMBAT.get(p);return map!=null&&map.getOrDefault(maid,0L)>now;
    }
    public static void damaged(LivingDamageEvent.Post event){
        var target=event.getEntity();
        if(target.level().isClientSide||!(event.getNewDamage()>0)||!Float.isFinite(event.getNewDamage()))return;
        var c=resolve(event.getSource());
        report(c);
        if(c!=null&&!SETTLED.contains(target))HITS.put(target,c);else HITS.remove(target);
    }
    public static void death(LivingDeathEvent event){
        var target=event.getEntity();
        if(target.level().isClientSide||event.isCanceled())return;
        if(target instanceof ServerPlayer p){
            HITS.values().removeIf(c->c.owner()==p);COMBAT.remove(p);return;
        }
        if(!(target instanceof Enemy) && !BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getPath().equals("ender_dragon"))return;
        if(SETTLED.contains(target))return;
        var hit=HITS.remove(target);var kill=resolve(event.getSource());
        if(hit==null||kill==null||!hit.equals(kill)||kill.owner().isCreative())return;
        SETTLED.add(target);
        try{
            var p=kill.owner();var server=p.getServer();
            String id=BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();
            int tier=MonsterTierRegistry.getTier(id);
            if(tier<=0)tier=MonsterTierRegistry.estimateTierFromHealth(target.getMaxHealth());
            var data=NativeMasteryData.get(server);
            int before=data.read(p.getUUID()).level();
            var next=data.credit(p.getUUID(),tier,id);
            int reward=ResonanceRules.DEFAULT.legacyUnits(5);
            if(kill.live()==null)NativeResonanceService.recoverStored(server,p.getUUID(),p.getMainHandItem(),reward);
            else NativeResonanceService.recoverLive(server,p.getUUID(),kill.live(),reward);
            if(next.level()>before)p.displayClientMessage(Component.literal("[Port Probe] Contract mastery Lv."+next.level()),true);
        }catch(RuntimeException rejected){ /* Unknown schema fails closed; no retry of a settled target. */ }
    }
    private NativeCombatCredits(){}
}
