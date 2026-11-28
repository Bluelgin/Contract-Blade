package com.maidweapon.port.recovery;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidweapon.port.init.PortDataTypes;
import com.maidweapon.port.storage.NativeContractStore;
import com.maidweapon.port.storage.NativeTransferJournal;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Evidence-based session recovery: reopen an already committed LIVE contract only.
 * No STORED/pending recovery, no inferred absence, no spawn or inventory changes.
 */
public final class NativeLiveReconciliation {
    private static final java.util.Map<MinecraftServer,java.util.LinkedHashSet<com.maidweapon.port.storage.ContractReference>> REJECTED=new java.util.WeakHashMap<>();
    public static boolean acknowledge(MinecraftServer server,UUID maid) throws java.io.IOException {
        if(!server.isSameThread() || maid==null) return false;
        var view=NativeContractStore.get(server).inspect(maid);
        if(view==null || view.phase()!=NativeContractStore.Phase.LIVE) return false;
        int observed=0;
        for(var level:server.getAllLevels()){
            var entity=level.getEntity(maid);
            if(entity==null) continue;
            observed++;
            if(!(entity instanceof EntityMaid m) || !m.isAlive() || m.isRemoved()
                || !view.owner().equals(m.getOwnerUUID()) || !m.hasData(PortDataTypes.MAID_BINDING)
                || !view.binding().equals(m.getData(PortDataTypes.MAID_BINDING))) return false;
        }
        var lease=NativeContractStore.get(server).liveReference(maid);
        return observed==1 && lease!=null && NativeTransferJournal.get(server).acknowledgeObservedLive(view,lease);
    }
    /** Ordinary input may reauthorize only its exact existing live lease. */
    public static boolean acknowledge(net.minecraft.server.MinecraftServer server,UUID owner,net.minecraft.world.item.ItemStack carrier){
        try{return automatic(server,owner,carrier);}catch(RuntimeException rejected){return false;}
    }
    private static boolean automatic(net.minecraft.server.MinecraftServer server,UUID owner,net.minecraft.world.item.ItemStack carrier){
        if(server==null||!server.isSameThread()||owner==null||!com.maidweapon.port.item.NativeCarriers.accepts(carrier))return false;
        var ref=com.maidweapon.port.storage.NativeChannelService.displayReference(carrier);
        if(ref==null||!owner.equals(ref.owner())||!NativeContractStore.get(server).matchesLiveLease(ref))return false;
        // Unloaded is not failure evidence and must be retried when the entity loads.
        boolean loaded=false;
        for(var level:server.getAllLevels())loaded|=level.getEntity(ref.maid())!=null;
        if(!loaded)return false;
        var rejected=REJECTED.computeIfAbsent(server,s->new java.util.LinkedHashSet<>());
        if(rejected.contains(ref))return false;
        try{if(acknowledge(server,ref.maid()))return true;}
        catch(java.io.IOException|RuntimeException failure){com.mojang.logging.LogUtils.getLogger().warn("Native live evidence rejected for {}",ref.maid(),failure);}
        if(rejected.size()>=4096)rejected.remove(rejected.iterator().next());
        rejected.add(ref);
        com.mojang.logging.LogUtils.getLogger().warn("Native live contract {} remains quarantined; no entity or inventory changed",ref.maid());
        return false;
    }
    private NativeLiveReconciliation(){}
}
