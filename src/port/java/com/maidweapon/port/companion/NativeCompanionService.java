package com.maidweapon.port.companion;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidweapon.port.config.ContractRulesConfig;
import com.maidweapon.port.init.PortDataTypes;
import com.maidweapon.port.item.NativeContractSwordItem;
import com.maidweapon.port.oath.NativeOathData;
import com.maidweapon.port.oath.NativeOathService;
import com.maidweapon.port.storage.*;
import com.maidweapon.port.tlm.PortContractLifecycleTrial;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Maintained manual companion policy, adapted to native identity/store APIs. */
public final class NativeCompanionService {
    public static final double RECALL_RANGE=32;
    private static final Map<ServerPlayer,Long> NEXT_REQUEST=new WeakHashMap<>();
    public static boolean resident(EntityMaid maid){return maid.isHomeModeEnable()||maid.isOrderedToSit();}
    public static boolean toggle(ServerPlayer owner){
        if(owner.getServer()==null||!owner.getServer().isSameThread()||!owner.isAlive()||owner.isSpectator()
                ||owner.containerMenu!=owner.inventoryMenu||NativeOathService.active(owner))return false;
        long now=owner.getServer().overworld().getGameTime();Long next=NEXT_REQUEST.get(owner);
        if(next!=null&&now<next)return false;
        NEXT_REQUEST.put(owner,now+10);
        var carrier=owner.getMainHandItem();
        if(!(carrier.getItem() instanceof NativeContractSwordItem)||!com.maidweapon.port.item.NativeCarriers.accepts(carrier))return message(owner,"hold_contract");
        var ref=NativeChannelService.displayReference(carrier);
        if(ref==null||!owner.getUUID().equals(ref.owner()))return message(owner,"hold_contract");
        try{
            if(NativeOathData.get(owner.getServer()).resting(owner.getUUID(),ref.maid()))return message(owner,"unavailable");
            var maid=NativeChannelService.selectedLive(owner.getServer(),owner.getUUID(),carrier);
            if(maid!=null){
                if(maid.level()!=owner.level())return message(owner,"other_world");
                if(maid.distanceToSqr(owner)>RECALL_RANGE*RECALL_RANGE)return message(owner,"too_far");
                if(!recall(owner,carrier,maid))return message(owner,"failed");
                success(owner,"recalled");return true;
            }
            var stored=NativeChannelService.selected(owner.getServer(),owner.getUUID(),carrier);
            if(stored==null){
                boolean observed=false;for(var level:owner.getServer().getAllLevels())observed|=level.getEntity(ref.maid())!=null;
                return message(owner,!observed&&NativeContractStore.get(owner.getServer()).matchesLiveLease(ref)?"not_loaded":"unavailable");
            }
            if(!canFollow(owner,ref))return message(owner,"limit_reached");
            if(!manifestRequested(owner,carrier))return message(owner,"failed");
            return true;
        }catch(RuntimeException rejected){com.mojang.logging.LogUtils.getLogger().warn("Native companion request rejected",rejected);return message(owner,"unavailable");}
    }
    /** Same split as Forge: input policy calls the authorized deployment operation. */
    public static boolean manifestRequested(ServerPlayer owner,ItemStack carrier){return manifestRequested(owner,carrier,false);}
    static boolean manifestRequested(ServerPlayer owner,ItemStack carrier,boolean guard){
        if(owner.getServer()==null||!owner.getServer().isSameThread()||carrier!=owner.getMainHandItem()
                ||!owner.isAlive()||owner.isSpectator()||NativeOathService.active(owner))return false;
        var ref=NativeChannelService.selected(owner.getServer(),owner.getUUID(),carrier);
        if(ref==null||NativeOathData.get(owner.getServer()).resting(owner.getUUID(),ref.maid())||!canFollow(owner,ref))return false;
        if(carrier.has(PortDataTypes.CONTRACT_REFERENCE.get())&&!NativeChannelService.convertExternal(owner.getServer(),owner.getUUID(),carrier))return false;
        var maid=PortContractLifecycleTrial.manifest(owner.serverLevel(),owner.position(),owner.getUUID(),carrier,candidate->{
            candidate.setHomeModeEnable(false);candidate.setOrderedToSit(false);
            NativeGuardService.prepare(candidate,guard,owner.getServer().overworld().getGameTime());
        });
        if(maid==null)return false;
        NativeContractStore.get(owner.getServer()).resident(ref,false);
        try{ContractDeploymentEffects.deployFinish(maid);ContractCompanionDialogue.say(owner,maid,guard?"protect":"greeting");}
        catch(RuntimeException cosmetic){com.mojang.logging.LogUtils.getLogger().warn("Companion presentation failed after successful manifestation",cosmetic);}
        return true;
    }
    public static boolean recallRequested(ServerPlayer owner,ItemStack carrier,EntityMaid maid){
        return owner.getServer()!=null&&owner.getServer().isSameThread()&&!NativeOathService.active(owner)
            &&NativeChannelService.selectedLive(owner.getServer(),owner.getUUID(),carrier)==maid
            &&maid.level()==owner.level()&&maid.distanceToSqr(owner)<=RECALL_RANGE*RECALL_RANGE&&recall(owner,carrier,maid);
    }
    private static boolean canFollow(ServerPlayer owner,ContractReference requested){
        int limit=ContractRulesConfig.SPEC.isLoaded()?ContractRulesConfig.MAX_FOLLOWERS.get():1;
        int followers=0;
        var store=NativeContractStore.get(owner.getServer());
        for(var ref:store.liveLeases(owner.getUUID())){
            if(ref.maid().equals(requested.maid()))continue;
            EntityMaid loaded=null;
            for(var level:owner.getServer().getAllLevels())if(level.getEntity(ref.maid()) instanceof EntityMaid candidate){loaded=candidate;break;}
            if(loaded!=null){
                if(!loaded.hasData(PortDataTypes.MAID_BINDING)||!ref.binding().equals(loaded.getData(PortDataTypes.MAID_BINDING)))return false;
                store.resident(ref,resident(loaded));
            }
            if(!store.resident(ref)&&++followers>=limit)return false;
        }
        return true;
    }
    private static boolean recall(ServerPlayer owner,ItemStack carrier,EntityMaid maid){
        if(!PortContractLifecycleTrial.capture((net.minecraft.server.level.ServerLevel)maid.level(),maid,owner.getUUID(),owner.getScoreboardName(),carrier))return false;
        try{ContractDeploymentEffects.recallFinish(maid);}catch(RuntimeException cosmetic){com.mojang.logging.LogUtils.getLogger().warn("Companion recall presentation failed after commit",cosmetic);}
        return true;
    }
    public static void tick(ServerTickEvent.Post event){
        for(var owner:event.getServer().getPlayerList().getPlayers())NativeGuardService.tick(owner);
        if(event.getServer().getTickCount()%5!=0)return;
        for(var owner:event.getServer().getPlayerList().getPlayers()){
            NativeTransferSafetyService.rescueSelfStoredContract(owner);
            for(int slot=0;slot<owner.getInventory().getContainerSize();slot++){
                var carrier=owner.getInventory().getItem(slot);
                if(NativeChannelService.displayReference(carrier)==null)continue;
                var maid=NativeChannelService.selectedLive(event.getServer(),owner.getUUID(),carrier);
                if(maid!=null){
                    var store=NativeContractStore.get(event.getServer());var ref=NativeChannelService.displayReference(carrier);
                    boolean home=resident(maid);
                    if(home){
                        var choice=new net.minecraft.nbt.CompoundTag();choice.putBoolean("Home",maid.isHomeModeEnable());choice.putBoolean("Sit",maid.isOrderedToSit());
                        maid.getPersistentData().put("MaidWeaponResidenceChoice",choice);
                    }else if(store.resident(ref)&&!canFollow(owner,ref)){
                        var choice=maid.getPersistentData().getCompound("MaidWeaponResidenceChoice");
                        maid.setHomeModeEnable(choice.getBoolean("Home"));maid.setOrderedToSit(choice.getBoolean("Sit")||!choice.getBoolean("Home"));
                        home=resident(maid);message(owner,"limit_reached");
                    }
                    store.resident(ref,home);
                }
            }
        }
        // Deliberately do not select work, combat or feeding tasks or overwrite schedules:
        // current Forge ContractWorkPolicy.combat()/feeding() both return false.
    }
    private static void recallFollowers(ServerPlayer owner){
        NEXT_REQUEST.remove(owner);NativeGuardService.disconnect(owner);
        for(int slot=0;slot<owner.getInventory().getContainerSize();slot++){
            var carrier=owner.getInventory().getItem(slot);
            if(NativeChannelService.displayReference(carrier)==null)continue;
            var maid=NativeChannelService.selectedLive(owner.getServer(),owner.getUUID(),carrier);
            if(maid!=null&&!resident(maid))recall(owner,carrier,maid);
        }
    }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer owner)recallFollowers(owner);}
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){if(event.getEntity() instanceof ServerPlayer owner)recallFollowers(owner);}
    public static void death(LivingDeathEvent event){if(event.getEntity() instanceof ServerPlayer owner)recallFollowers(owner);}
    private static boolean message(ServerPlayer owner,String suffix){owner.displayClientMessage(Component.translatable("maid_weapon.companion."+suffix),true);return false;}
    private static void success(ServerPlayer owner,String suffix){owner.displayClientMessage(Component.translatable("maid_weapon.companion."+suffix),true);}
    private NativeCompanionService(){}
}
