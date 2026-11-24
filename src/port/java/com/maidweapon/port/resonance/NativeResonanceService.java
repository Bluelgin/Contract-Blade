package com.maidweapon.port.resonance;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidweapon.port.domain.resonance.ResonanceRules;
import com.maidweapon.port.domain.resonance.ResonanceRules.Payment;
import com.maidweapon.port.init.PortDataTypes;
import com.maidweapon.port.init.ProbeItems;
import com.maidweapon.port.storage.ContractReference;
import com.maidweapon.port.storage.ContractStackDataAccess;
import com.maidweapon.port.storage.NativeContractStore;
import com.maidweapon.port.storage.NativeResonanceSavedData;
import com.maidweapon.port.storage.NativeTransferJournal;
import com.maidweapon.port.storage.NativeChannelService;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Trusted server entry point. No client-supplied cost, identity, or waiver.
 * Probe defaults only; config, combat events, ticking, oath/bauble and HUD are separate adapters.
 */
public final class NativeResonanceService {
    public static final class Attempt {
        private boolean completed;
        private Attempt() {}
    }
    public static Attempt newAttempt() { return new Attempt(); }
    private record Identity(UUID maid,UUID owner,UUID binding) {}
    private static boolean journalAllows(MinecraftServer server,UUID maid) {
        try {return NativeTransferJournal.get(server).permits(maid);}
        catch(java.io.IOException rejected){return false;}
    }
    private static Identity stored(MinecraftServer server,UUID owner,ItemStack stack) {
        ContractReference ref=NativeChannelService.selected(server,owner,stack);
        return ref==null?null:new Identity(ref.maid(),ref.owner(),ref.binding());
    }
    private static Identity live(MinecraftServer server,UUID owner,EntityMaid maid) {
        if (!server.isSameThread() || owner==null || maid==null || maid.isRemoved() || !maid.isAlive()
            || !(maid.level() instanceof ServerLevel level) || level.getServer()!=server
            || level.getEntity(maid.getUUID())!=maid || !owner.equals(maid.getOwnerUUID())
            || !maid.hasData(PortDataTypes.MAID_BINDING)) return null;
        var v=NativeContractStore.get(server).inspect(maid.getUUID());
        if (v==null || v.phase()!=NativeContractStore.Phase.LIVE || !owner.equals(v.owner())
            || !v.binding().equals(maid.getData(PortDataTypes.MAID_BINDING))) return null;
        return journalAllows(server,v.maid()) ? new Identity(v.maid(),v.owner(),v.binding()) : null;
    }
    private static int current(MinecraftServer server,Identity i) {
        return i==null ? -1 : NativeResonanceSavedData.get(server).current(i.maid,i.owner,i.binding,ResonanceRules.DEFAULT);
    }
    public static int currentStored(MinecraftServer server,UUID owner,ItemStack stack) {
        try{return current(server,stored(server,owner,stack));}catch(RuntimeException rejected){return -1;}
    }
    public static int currentLive(MinecraftServer server,UUID owner,EntityMaid maid) {
        try{return current(server,live(server,owner,maid));}catch(RuntimeException rejected){return -1;}
    }
    private static Payment pay(MinecraftServer server,Identity i,int cost,Attempt attempt,boolean waived) {
        if(i==null || attempt==null) return Payment.UNAUTHORIZED;
        if(attempt.completed) return Payment.ALREADY_PAID;
        if(cost<0) return Payment.INVALID_COST;
        Payment p=NativeResonanceSavedData.get(server).pay(i.maid,i.owner,i.binding,ResonanceRules.DEFAULT,cost,waived);
        if(p==Payment.PAID || p==Payment.WAIVED) attempt.completed=true;
        return p;
    }
    public static Payment payStored(MinecraftServer server,UUID owner,ItemStack stack,int cost,Attempt attempt) {
        try{
            var ref=NativeChannelService.selected(server,owner,stack);
            var i=ref==null?null:new Identity(ref.maid(),ref.owner(),ref.binding());
            boolean waived=i!=null&&com.maidweapon.port.tlm.NativeBaubleCapabilities.stored(server,ref)==com.maidweapon.port.tlm.NativeBaubleCapabilities.Ultimate.ENDLESS;
            return pay(server,i,cost,attempt,waived);
        }catch(RuntimeException rejected){return Payment.UNAUTHORIZED;}
    }
    public static Payment payLive(MinecraftServer server,UUID owner,EntityMaid maid,int cost,Attempt attempt) {
        try{
            var i=live(server,owner,maid);
            boolean waived=i!=null&&(com.maidweapon.port.tlm.NativeBaubleCapabilities.live(maid)==com.maidweapon.port.tlm.NativeBaubleCapabilities.Ultimate.ENDLESS
                ||com.maidweapon.port.oath.NativeOathService.burning(server,i.owner,i.maid,i.binding));
            return pay(server,i,cost,attempt,waived);
        }catch(RuntimeException rejected){return Payment.UNAUTHORIZED;}
    }
    /** Oath upkeep never consumes the general ability waiver. */
    public static Payment burnLive(MinecraftServer server,UUID owner,EntityMaid maid,int cost,Attempt attempt){
        try{return pay(server,live(server,owner,maid),cost,attempt,false);}
        catch(RuntimeException rejected){return Payment.UNAUTHORIZED;}
    }
    public static int recoverStored(MinecraftServer server,UUID owner,ItemStack stack,int amount) {
        try {
            Identity i=stored(server,owner,stack);
            return i==null || amount<0 || com.maidweapon.port.oath.NativeOathService.burning(server,i.owner,i.maid,i.binding) ? -1 : NativeResonanceSavedData.get(server).recover(i.maid,i.owner,i.binding,ResonanceRules.DEFAULT,amount);
        } catch(RuntimeException rejected){return -1;}
    }
    public static int recoverLive(MinecraftServer server,UUID owner,EntityMaid maid,int amount) {
        try {
            Identity i=live(server,owner,maid);
            return i==null || amount<0 ? -1 : NativeResonanceSavedData.get(server).recover(i.maid,i.owner,i.binding,ResonanceRules.DEFAULT,amount);
        }catch(RuntimeException rejected){return -1;}
    }
    private NativeResonanceService(){}
}
