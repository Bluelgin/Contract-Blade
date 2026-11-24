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
    private static Identity stored(MinecraftServer server,UUID owner,ItemStack stack) {
        if (!server.isSameThread() || owner==null || stack==null || stack.getCount()!=1
            || !stack.is(ProbeItems.CONTRACT_CARRIER.get()) || !ContractStackDataAccess.readContract(stack).isEmpty()) return null;
        ContractReference ref=stack.get(PortDataTypes.CONTRACT_REFERENCE.get());
        if (ref==null || !owner.equals(ref.owner())) return null;
        var v=NativeContractStore.get(server).inspect(ref.maid());
        if (v==null || v.phase()!=NativeContractStore.Phase.STORED || !owner.equals(v.owner())
            || !ref.binding().equals(v.binding()) || !ref.ticket().equals(v.ticket())) return null;
        return new Identity(v.maid(),v.owner(),v.binding());
    }
    private static Identity live(MinecraftServer server,UUID owner,EntityMaid maid) {
        if (!server.isSameThread() || owner==null || maid==null || maid.isRemoved() || !maid.isAlive()
            || !(maid.level() instanceof ServerLevel level) || level.getServer()!=server
            || level.getEntity(maid.getUUID())!=maid || !owner.equals(maid.getOwnerUUID())
            || !maid.hasData(PortDataTypes.MAID_BINDING)) return null;
        var v=NativeContractStore.get(server).inspect(maid.getUUID());
        if (v==null || v.phase()!=NativeContractStore.Phase.LIVE || !owner.equals(v.owner())
            || !v.binding().equals(maid.getData(PortDataTypes.MAID_BINDING))) return null;
        return new Identity(v.maid(),v.owner(),v.binding());
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
    private static Payment pay(MinecraftServer server,Identity i,int cost,Attempt attempt) {
        if(i==null || attempt==null) return Payment.UNAUTHORIZED;
        if(attempt.completed) return Payment.ALREADY_PAID;
        if(cost<0) return Payment.INVALID_COST;
        Payment p=NativeResonanceSavedData.get(server).pay(i.maid,i.owner,i.binding,ResonanceRules.DEFAULT,cost,false);
        if(p==Payment.PAID || p==Payment.WAIVED) attempt.completed=true;
        return p;
    }
    public static Payment payStored(MinecraftServer server,UUID owner,ItemStack stack,int cost,Attempt attempt) {
        try{return pay(server,stored(server,owner,stack),cost,attempt);}catch(RuntimeException rejected){return Payment.UNAUTHORIZED;}
    }
    public static Payment payLive(MinecraftServer server,UUID owner,EntityMaid maid,int cost,Attempt attempt) {
        try{return pay(server,live(server,owner,maid),cost,attempt);}catch(RuntimeException rejected){return Payment.UNAUTHORIZED;}
    }
    private NativeResonanceService(){}
}
