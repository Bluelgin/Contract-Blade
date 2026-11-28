package com.maidweapon.port.companion;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidweapon.port.config.ContractRulesConfig;
import com.maidweapon.port.resonance.NativeResonanceService;
import com.maidweapon.port.storage.ContractReference;
import com.maidweapon.port.storage.NativeChannelService;
import com.maidweapon.port.tlm.NativeBaubleCapabilities;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Maintained ContractCompanionService guard policy; native health-loss observation only. */
public final class NativeGuardService {
    private static final String KEY="MaidWeaponCompanion";
    private static final int GUARD_TICKS=400;
    private record Injury(ContractReference reference,float health){}
    private static final Map<ServerPlayer,Injury> INJURIES=new WeakHashMap<>();
    private static final Map<ServerPlayer,Long> AUTO_NEXT=new WeakHashMap<>();
    private static long now(ServerPlayer owner){return owner.getServer().overworld().getGameTime();}
    private static boolean atHome(ServerPlayer owner){return owner.level().dimension().location().toString().equals("maid_weapon:contract_interior");}
    private static boolean allows(ServerPlayer owner,ItemStack carrier){
        var ref=NativeChannelService.selected(owner.getServer(),owner.getUUID(),carrier);
        if(ref==null)return false;
        String mode=ContractRulesConfig.SPEC.isLoaded()?ContractRulesConfig.autoManifestMode():"bauble";
        return mode.equals("always")||mode.equals("bauble")&&NativeBaubleCapabilities.storedGuard(owner.getServer(),ref);
    }
    private static boolean enough(ServerPlayer owner,ItemStack carrier){
        var ref=NativeChannelService.selected(owner.getServer(),owner.getUUID(),carrier);
        return ref!=null&&(NativeResonanceService.currentStored(owner.getServer(),owner.getUUID(),carrier)
            >=(ContractRulesConfig.SPEC.isLoaded()?ContractRulesConfig.HURT_MIN_RESONANCE.get():1)
            ||NativeBaubleCapabilities.stored(owner.getServer(),ref)==NativeBaubleCapabilities.Ultimate.ENDLESS);
    }
    /** Pre preserves the actual pre-hit health; post/next tick determine whether health was really lost. */
    public static void beforeDamage(LivingDamageEvent.Pre event){
        if(event.getEntity() instanceof ServerPlayer owner&&event.getNewDamage()>0&&Float.isFinite(event.getNewDamage())
            &&event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker&&attacker!=owner
            &&!atHome(owner)&&now(owner)>=AUTO_NEXT.getOrDefault(owner,0L)&&allows(owner,owner.getMainHandItem())&&enough(owner,owner.getMainHandItem())){
            INJURIES.put(owner,new Injury(NativeChannelService.selected(owner.getServer(),owner.getUUID(),owner.getMainHandItem()),owner.getHealth()));
        }
    }
    public static void afterDamage(LivingDamageEvent.Post event){
        if(!(event.getNewDamage()>0)||!Float.isFinite(event.getNewDamage()))return;
        if(event.getEntity() instanceof ServerPlayer owner&&event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker&&attacker!=owner&&!atHome(owner))prolong(owner);
        if(event.getSource().getEntity() instanceof ServerPlayer owner)prolong(owner);
    }
    static void prepare(EntityMaid maid,boolean guard,long now){
        var state=new net.minecraft.nbt.CompoundTag();state.putString("Mode",guard?"GUARD":"MANUAL");state.putLong("Until",guard?now+GUARD_TICKS:0);
        maid.getPersistentData().put(KEY,state);
    }
    private static void prolong(ServerPlayer owner){
        for(var carrier:owner.getInventory().items){
            var maid=NativeChannelService.selectedLive(owner.getServer(),owner.getUUID(),carrier);
            if(maid!=null&&maid.getPersistentData().getCompound(KEY).getString("Mode").equals("GUARD"))extend(maid,now(owner)+GUARD_TICKS);
        }
    }
    private static void extend(EntityMaid maid,long until){
        var state=maid.getPersistentData().getCompound(KEY);state.putLong("Until",Math.max(until,state.getLong("Until")));
    }
    private static boolean inCombat(ServerPlayer owner,EntityMaid maid){
        if(maid.getTarget()!=null&&maid.getTarget().isAlive())return true;
        return !owner.level().getEntitiesOfClass(Mob.class,owner.getBoundingBox().inflate(12),mob->mob.isAlive()&&(mob.getTarget()==owner||mob.getTarget()==maid)).isEmpty();
    }
    public static void tick(ServerPlayer owner){
        var injury=INJURIES.remove(owner);var carrier=owner.getMainHandItem();
        if(injury!=null&&owner.isAlive()&&!owner.isSpectator()&&!atHome(owner)&&owner.getHealth()<injury.health()
            &&now(owner)>=AUTO_NEXT.getOrDefault(owner,0L)&&injury.reference().equals(NativeChannelService.selected(owner.getServer(),owner.getUUID(),carrier))
            &&allows(owner,carrier)&&enough(owner,carrier)&&NativeCompanionService.manifestRequested(owner,carrier,true)){
            AUTO_NEXT.put(owner,now(owner)+(ContractRulesConfig.SPEC.isLoaded()?ContractRulesConfig.HURT_COOLDOWN.get():200));
        }
        if(owner.tickCount%5!=0)return;
        for(var stack:owner.getInventory().items){
            var maid=NativeChannelService.selectedLive(owner.getServer(),owner.getUUID(),stack);
            if(maid==null)continue;
            var state=maid.getPersistentData().getCompound(KEY);
            if(NativeCompanionService.resident(maid)){if(state.getString("Mode").equals("GUARD"))state.putString("Mode","RESIDENT");continue;}
            if(!state.getString("Mode").equals("GUARD")||com.maidweapon.port.oath.NativeOathService.active(owner))continue;
            if(inCombat(owner,maid))extend(maid,now(owner)+200);
            if(now(owner)>=state.getLong("Until")&&maid.level()==owner.level()&&maid.distanceToSqr(owner)<=32*32){
                ContractCompanionDialogue.say(owner,maid,owner.getHealth()<owner.getMaxHealth()?"hurt":"safe");
                NativeCompanionService.recallRequested(owner,stack,maid);
            }
        }
    }
    public static void disconnect(ServerPlayer owner){INJURIES.remove(owner);AUTO_NEXT.remove(owner);}
    public static void stop(ServerStoppedEvent event){INJURIES.clear();AUTO_NEXT.clear();}
    private NativeGuardService(){}
}
