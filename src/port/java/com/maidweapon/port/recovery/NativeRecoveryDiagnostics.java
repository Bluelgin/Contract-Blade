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
        event.getDispatcher().register(Commands.literal("contractportcheckpoint").requires(s->s.hasPermission(4))
            .executes(c->{
                int count=NativeCheckpointService.checkpoint(c.getSource().getServer());
                c.getSource().sendSuccess(()->Component.literal("Stored contract checkpoints: "+count),true);return count;
            }));
        event.getDispatcher().register(Commands.literal("contractportacklive")
            .requires(s->s.hasPermission(4)).then(Commands.argument("maid",UuidArgument.uuid())
                .executes(c->ack(c.getSource(),UuidArgument.getUuid(c,"maid")))));
        event.getDispatcher().register(Commands.literal("contractportinspect")
            .requires(s->s.hasPermission(4)).then(Commands.argument("maid",UuidArgument.uuid())
                .executes(c->inspect(c.getSource(),UuidArgument.getUuid(c,"maid")))));
    }
    private static int ack(net.minecraft.commands.CommandSourceStack source,UUID maid) {
        try {
            if(NativeLiveReconciliation.acknowledge(source.getServer(),maid)){
                source.sendSuccess(()->Component.literal("Observed LIVE contract acknowledged for this session; no entity or item changed."),true);
                return 1;
            }
        }catch(Exception failure){
            source.sendFailure(Component.literal("LIVE acknowledgment blocked: "+failure.getClass().getSimpleName()));
            return 0;
        }
        source.sendFailure(Component.literal("No matching committed live evidence; journal remains quarantined."));
        return 0;
    }
    private static int inspect(net.minecraft.commands.CommandSourceStack source,UUID maid) {
        try {
            var server=source.getServer();
            var view=NativeContractStore.get(server).inspect(maid);
            var journal=NativeTransferJournal.get(server);
            boolean permitted=view!=null&&view.phase()==NativeContractStore.Phase.STORED
                ?journal.permitsStored(NativeContractStore.get(server),new com.maidweapon.port.storage.ContractReference(maid,view.owner(),view.binding(),view.ticket()))
                :journal.permits(maid);
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
