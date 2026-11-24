package com.maidweapon.port.recovery;

import com.maidweapon.port.storage.ContractReference;
import com.maidweapon.port.storage.NativeContractStore;
import com.maidweapon.port.storage.NativeTransferJournal;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Server-thread checkpoint of captures that this process actually performed.
 * A flush is native vanilla save completion, not a filesystem-wide transaction.
 * Never promotes a previous-session uncheckpointed journal by guessing absence.
 */
public final class NativeCheckpointService {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static boolean noLoadedEntity(MinecraftServer server,UUID maid){
        for(var level:server.getAllLevels())if(level.getEntity(maid)!=null)return false;
        return true;
    }
    public static int checkpoint(MinecraftServer server){
        if(!server.isSameThread())return 0;
        try{
            var journal=NativeTransferJournal.get(server);
            var store=NativeContractStore.get(server);
            var candidates=new ArrayList<ContractReference>();
            for(UUID maid:journal.currentIntents()){
                try{
                    var v=store.inspect(maid);
                    if(v!=null&&v.phase()==NativeContractStore.Phase.STORED&&noLoadedEntity(server,maid))
                        candidates.add(new ContractReference(maid,v.owner(),v.binding(),v.ticket()));
                }catch(RuntimeException malformed){LOG.warn("Skipping malformed checkpoint record {}",maid,malformed);}
            }
            if(candidates.isEmpty())return 0;
            // Native saveEverything includes players + world data, flush=true, forced=true.
            if(!server.saveEverything(true,true,true))return 0;
            int count=0;
            for(var ref:candidates){
                try{
                    if(noLoadedEntity(server,ref.maid())&&journal.markStoredAfterFlush(store,ref))count++;
                }catch(Exception failed){LOG.warn("Contract checkpoint failed {}",ref.maid(),failed);}
            }
            LOG.info("Contract port checkpoint stored {} / {} candidates",count,candidates.size());
            return count;
        }catch(RuntimeException failed){LOG.warn("Contract checkpoint aborted",failed);return 0;}
    }
    public static void stop(ServerStoppingEvent event){checkpoint(event.getServer());}
    private NativeCheckpointService(){}
}
