package com.maidweapon.port.resonance;

import com.maidweapon.port.domain.resonance.ResonanceAccumulator;
import com.maidweapon.port.domain.resonance.ResonanceRules;
import com.maidweapon.port.domain.resonance.SpiritVeinRules;
import com.maidweapon.port.mastery.NativeCombatCredits;
import com.maidweapon.port.storage.NativeChannelService;
import com.maidweapon.port.storage.NativeResonanceSavedData;
import com.maidweapon.port.storage.NativeSpiritVeinData;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Native inventory recovery, one observed second per authorized maid, not wall time.
 * Each contract's remainder survives selection and ticket changes. No global entity scan.
 */
public final class NativeRecoveryTick {
    private record Session(UUID binding,ResonanceAccumulator accumulator){}
    private static final int MAX_ACTIVE=512;
    private static final Map<ServerPlayer,Map<UUID,Session>> SESSIONS=new WeakHashMap<>();
    public static void post(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer player)||player.getServer()==null||!player.isAlive())return;
        var server=player.getServer();
        long now=server.overworld().getGameTime();
        if(now%20!=0)return;
        try{
            var rules=ResonanceRules.DEFAULT;
            var pool=NativeResonanceSavedData.get(server);
            double multiplier=SpiritVeinRules.multiplier(NativeSpiritVeinData.get(server).stage(player.getUUID()));
            var sessions=SESSIONS.computeIfAbsent(player,p->new java.util.HashMap<>());
            var seen=new java.util.HashSet<UUID>();
            var inv=player.getInventory();
            for(int slot=-1;slot<inv.getContainerSize()&&seen.size()<MAX_ACTIVE;slot++){
                var stack=slot<0?player.containerMenu.getCarried():inv.getItem(slot);
                for(var entry:NativeChannelService.recoveryEntries(server,player.getUUID(),stack)){
                    var ref=entry.reference();var live=entry.live();
                    if(seen.size()>=MAX_ACTIVE)break;
                    if(!seen.add(ref.maid()))continue;
                    try{
                        int current=pool.current(ref.maid(),ref.owner(),ref.binding(),rules);
                        var session=sessions.get(ref.maid());
                        if(session==null||!session.binding().equals(ref.binding())){
                            if(sessions.size()>=MAX_ACTIVE)sessions.keySet().removeIf(id->!seen.contains(id));
                            session=new Session(ref.binding(),new ResonanceAccumulator());
                            sessions.put(ref.maid(),session);
                        }
                        boolean combat=NativeCombatCredits.fighting(player,ref.maid(),now)
                            ||(live!=null&&live.getTarget()!=null&&live.getTarget().isAlive());
                        var activity=combat?ResonanceRules.Activity.COMBAT:live==null
                            ?ResonanceRules.Activity.STORED:ResonanceRules.Activity.MANIFESTED_IDLE;
                        int restored=session.accumulator().tick(now,
                            rules.recoveryUnits(live!=null,activity,multiplier)*20,current,rules.maximum());
                        if(restored>0)pool.recover(ref.maid(),ref.owner(),ref.binding(),rules,restored);
                    }catch(RuntimeException malformed){sessions.remove(ref.maid());}
                }
            }
        }catch(RuntimeException invalid){SESSIONS.remove(player);}
    }
    private NativeRecoveryTick(){}
}
