package com.maidweapon.port.recovery;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidweapon.port.init.PortDataTypes;
import com.maidweapon.port.storage.NativeContractStore;
import com.maidweapon.port.storage.NativeTransferJournal;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Restricted operator recovery: reopen an already committed LIVE contract only.
 * No STORED/pending recovery, no inferred absence, no spawn or inventory changes.
 */
public final class NativeLiveReconciliation {
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
        return observed==1 && NativeTransferJournal.get(server).acknowledgeObservedLive(view);
    }
    private NativeLiveReconciliation(){}
}
