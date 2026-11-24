package com.maidweapon.port.recovery;

import com.maidweapon.port.storage.NativeContractStore;
import com.maidweapon.port.storage.NativeTransferJournal;
import com.maidweapon.port.init.PortDataTypes;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.UUID;

/** Read-only administrator diagnostics. Missing loaded entity is UNKNOWN, never proof of absence. */
public final class NativeRecoveryDiagnostics {
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("contractportinspect")
            .requires(s->s.hasPermission(4)).then(Commands.argument("maid",UuidArgument.uuid())
                .executes(c->inspect(c.getSource(),UuidArgument.getUuid(c,"maid")))));
    }
    private static int inspect(net.minecraft.commands.CommandSourceStack source,UUID maid) {
        try {
            var server=source.getServer();
            var view=NativeContractStore.get(server).inspect(maid);
            var journal=NativeTransferJournal.get(server);
            boolean permitted=journal.permits(maid);
            String disk;
            try {var tag=journal.read(maid);disk=tag.getString("kind")+" / "+tag.getUUID("operation");}
            catch(java.nio.file.NoSuchFileException absent){disk="none";}
            int count=0, matching=0;
            for(var level:server.getAllLevels()){
                var entity=level.getEntity(maid);
                if(entity!=null) {
                    count++;
                    if(view!=null && entity instanceof EntityMaid m && m.isAlive() && !m.isRemoved()
                        && view.owner().equals(m.getOwnerUUID()) && m.hasData(PortDataTypes.MAID_BINDING)
                        && view.binding().equals(m.getData(PortDataTypes.MAID_BINDING))) matching++;
                }
            }
            String summary="ledger="+(view==null?"missing":view.phase())+" journal="+disk+" sessionAllowed="+permitted
                +" loaded="+count+" matching="+matching+"; unloaded presence UNKNOWN; no mutation performed";
            source.sendSuccess(()->Component.literal(summary),false);
            return 1;
        }catch(Exception failure){
            source.sendFailure(Component.literal("Contract recovery inspection blocked: "+failure.getClass().getSimpleName()));
            return 0;
        }
    }
    private NativeRecoveryDiagnostics(){}
}
