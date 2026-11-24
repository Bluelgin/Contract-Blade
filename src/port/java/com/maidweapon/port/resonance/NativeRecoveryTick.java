package com.maidweapon.port.resonance;

import com.maidweapon.port.domain.resonance.ResonanceAccumulator;
import com.maidweapon.port.domain.resonance.ResonanceRules;
import com.maidweapon.port.domain.resonance.SpiritVeinRules;
import com.maidweapon.port.init.PortDataTypes;
import com.maidweapon.port.storage.ContractReference;
import com.maidweapon.port.storage.NativeSpiritVeinData;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Probe-only recovery of the selected STORED carrier. No inventory/world scan,
 * no offline catch-up, no live/combat/oath recovery until their adapters exist.
 */
public final class NativeRecoveryTick {
    private record Session(ContractReference ref,ResonanceAccumulator accumulator){}
    private static final Map<ServerPlayer,Session> SESSIONS=new WeakHashMap<>();
    public static void post(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer player)||player.getServer()==null)return;
        var server=player.getServer();
        var carrier=player.getMainHandItem();
        var ref=carrier.get(PortDataTypes.CONTRACT_REFERENCE.get());
        if(ref==null){SESSIONS.remove(player);return;}
        int current=NativeResonanceService.currentStored(server,player.getUUID(),carrier);
        if(current<0){SESSIONS.remove(player);return;}
        try{
            var session=SESSIONS.get(player);
            if(session==null||!ref.equals(session.ref())){
                session=new Session(ref,new ResonanceAccumulator());SESSIONS.put(player,session);
            }
            var rules=ResonanceRules.DEFAULT;
            double multiplier=SpiritVeinRules.multiplier(NativeSpiritVeinData.get(server).stage(player.getUUID()));
            int restored=session.accumulator().tick(server.overworld().getGameTime(),
                rules.recoveryUnits(false,ResonanceRules.Activity.STORED,multiplier),current,rules.maximum());
            if(restored>0)NativeResonanceService.recoverStored(server,player.getUUID(),carrier,restored);
        }catch(RuntimeException invalid){SESSIONS.remove(player);}
    }
    private NativeRecoveryTick(){}
}
