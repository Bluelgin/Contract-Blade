package com.maidweapon.port.oath;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidweapon.port.domain.oath.OathRules;
import com.maidweapon.port.domain.resonance.ResonanceRules;
import com.maidweapon.port.domain.resonance.ResonanceRules.Payment;
import com.maidweapon.port.init.ProbeItems;
import com.maidweapon.port.resonance.NativeResonanceService;
import com.maidweapon.port.storage.ContractReference;
import com.maidweapon.port.storage.NativeChannelService;
import com.maidweapon.port.storage.NativeContractStore;
import com.maidweapon.port.tlm.PortContractLifecycleTrial;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Actual probe oath lifecycle. No persistent session can grant buffs after a restart.
 * The native journal still governs recovery of any interrupted live maid.
 */
public final class NativeOathService {
    private record Session(ContractReference ref,EntityMaid maid){}
    private static final Map<ServerPlayer,Session> ACTIVE=new WeakHashMap<>();
    private static boolean ring(EntityMaid maid){
        return com.maidweapon.port.tlm.NativeBaubleCapabilities.live(maid)==com.maidweapon.port.tlm.NativeBaubleCapabilities.Ultimate.OATH;
    }
    private static ItemStack carrier(ServerPlayer p,Session session){
        var inv=p.getInventory();
        for(int i=0;i<inv.getContainerSize();i++){
            var stack=inv.getItem(i);
            if(session.ref().equals(NativeChannelService.displayReference(stack))
                &&NativeChannelService.selectedLive(p.getServer(),p.getUUID(),stack)==session.maid())return stack;
        }
        return ItemStack.EMPTY;
    }
    private static boolean valid(ServerPlayer p,Session s){
        return p.isAlive()&&!p.isSpectator()&&s.maid().level()==p.level()&&!s.maid().isRemoved()
            &&s.maid().isAlive()&&ring(s.maid())&&!carrier(p,s).isEmpty()
            &&NativeResonanceService.currentLive(p.getServer(),p.getUUID(),s.maid())>0;
    }
    public static boolean burning(MinecraftServer server,UUID owner,UUID maid,UUID binding){
        if(server==null||!server.isSameThread())return false;
        var p=server.getPlayerList().getPlayer(owner);var s=p==null?null:ACTIVE.get(p);
        return s!=null&&s.ref().maid().equals(maid)&&s.ref().binding().equals(binding);
    }
    private static boolean empowered(EntityMaid maid){
        var server=maid.getServer();var owner=maid.getOwnerUUID();
        if(server==null||owner==null)return false;
        var p=server.getPlayerList().getPlayer(owner);var s=p==null?null:ACTIVE.get(p);
        return s!=null&&s.maid()==maid&&valid(p,s);
    }
    public static boolean active(ServerPlayer p){
        var s=ACTIVE.get(p);return s!=null&&valid(p,s);
    }
    /** Trusted input: resolve actual held stack; never accept owner or cost from packet. */
    public static boolean toggle(ServerPlayer p){
        if(p.getServer()==null||!p.getServer().isSameThread()||!p.isAlive()||p.isSpectator())return false;
        if(ACTIVE.containsKey(p)){end(p,false);return true;}
        if(p.containerMenu!=p.inventoryMenu)return false;
        var server=p.getServer();var held=p.getMainHandItem();
        var ref=NativeChannelService.displayReference(held);
        if(ref==null)return false;
        try{
            var data=NativeOathData.get(server);
            if(data.resting(p.getUUID(),ref.maid()))return false;
            var live=NativeChannelService.selectedLive(server,p.getUUID(),held);
            var stored=NativeChannelService.selected(server,p.getUUID(),held);
            if(live==null&&stored==null)return false;
            // Exactly one selected live companion; dormant channels do not count.
            var seen=new java.util.HashSet<UUID>();
            for(int i=0;i<p.getInventory().getContainerSize();i++){
                for(var entry:NativeChannelService.recoveryEntries(server,p.getUUID(),p.getInventory().getItem(i))){
                    if(entry.live()!=null&&seen.add(entry.reference().maid())&&!entry.reference().maid().equals(ref.maid()))return false;
                }
            }
            if(live==null){
                if(NativeResonanceService.currentStored(server,p.getUUID(),held)<=0)return false;
                if(com.maidweapon.port.tlm.NativeBaubleCapabilities.stored(server,stored)!=com.maidweapon.port.tlm.NativeBaubleCapabilities.Ultimate.OATH)return false;
                if(!held.has(com.maidweapon.port.init.PortDataTypes.CONTRACT_CHANNELS.get())
                    &&!NativeChannelService.convertExternal(server,p.getUUID(),held))return false;
                live=PortContractLifecycleTrial.manifest(p.serverLevel(),p.blockPosition().relative(p.getDirection()),p.getUUID(),held);
                if(live==null)return false;
            }
            ref=NativeChannelService.displayReference(held);
            if(ref==null||live.level()!=p.level()||!ring(live)||NativeResonanceService.currentLive(server,p.getUUID(),live)<=0)return false;
            ACTIVE.put(p,new Session(ref,live));
            p.displayClientMessage(Component.translatable("maid_weapon.native.oath.started"),true);return true;
        }catch(Exception rejected){return false;}
    }
    public static void tick(PlayerTickEvent.Pre e){
        if(!(e.getEntity() instanceof ServerPlayer p)||p.getServer()==null)return;
        try{
            var data=NativeOathData.get(p.getServer());data.tick(p.getUUID());
            var s=ACTIVE.get(p);if(s==null)return;
            int current=NativeResonanceService.currentLive(p.getServer(),p.getUUID(),s.maid());
            if(current==0){end(p,true);return;}
            if(!valid(p,s)){end(p,false);return;}
            var step=OathRules.tick(com.maidweapon.port.config.NativeRules.resonance().maximum(),com.maidweapon.port.config.NativeRules.oath(com.maidweapon.port.config.OathConfig.BURN,OathRules.BURN),data.remainder(p.getUUID(),s.ref().maid()));
            if(step.cost()>0&&NativeResonanceService.burnLive(p.getServer(),p.getUUID(),s.maid(),
                Math.min(current,step.cost()),NativeResonanceService.newAttempt())!=Payment.PAID){end(p,false);return;}
            data.remainder(p.getUUID(),s.ref().maid(),step.remainder());
            if(NativeResonanceService.currentLive(p.getServer(),p.getUUID(),s.maid())==0)end(p,true);
        }catch(RuntimeException rejected){end(p,false);}
    }
    public static void end(ServerPlayer p,boolean exhausted){
        var s=ACTIVE.remove(p);if(s==null||p.getServer()==null)return;
        try{
            if(exhausted&&p.isAlive())NativeOathData.get(p.getServer()).exhaust(p.getUUID(),s.ref().maid());
            var stack=carrier(p,s);
            if(!stack.isEmpty()&&s.maid().level() instanceof ServerLevel level)
                PortContractLifecycleTrial.capture(level,s.maid(),p.getUUID(),p.getScoreboardName(),stack);
            p.displayClientMessage(Component.translatable(exhausted?"maid_weapon.native.oath.exhausted":"maid_weapon.native.oath.ended"),true);
        }catch(RuntimeException failure){com.mojang.logging.LogUtils.getLogger().warn("Oath safe end failed",failure);}
    }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)end(p,false);}
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)end(p,false);}
    public static void death(LivingDeathEvent e){
        if(!e.isCanceled()&&e.getEntity() instanceof ServerPlayer p){
            end(p,false);try{NativeOathData.get(p.getServer()).clearRest(p.getUUID());}catch(RuntimeException ignored){}
        }
    }
    public static void stop(ServerStoppingEvent e){for(var p:e.getServer().getPlayerList().getPlayers())end(p,false);}
    public static void damage(LivingIncomingDamageEvent e){
        if(e.getEntity().level().isClientSide||e.isCanceled()||!(e.getAmount()>0)||!Float.isFinite(e.getAmount()))return;
        if(e.getEntity() instanceof EntityMaid maid&&empowered(maid)){e.setCanceled(true);return;}
        try {
        double multiplier=1;
        var source=e.getSource();var actor=source.getEntity();
        boolean direct=actor!=null&&actor==source.getDirectEntity();
        if(direct&&actor instanceof ServerPlayer p){
            if(active(p))multiplier*=1+com.maidweapon.port.config.NativeRules.oath(com.maidweapon.port.config.OathConfig.PLAYER_DAMAGE,OathRules.PLAYER_DAMAGE);
            multiplier*=(1-Math.min(.5,NativeOathData.get(p.getServer()).penalty(p.getUUID())*com.maidweapon.port.config.NativeRules.oath(com.maidweapon.port.config.OathConfig.BACKLASH_OUT,OathRules.BACKLASH_OUT)));
        }else if(direct&&actor instanceof EntityMaid maid&&empowered(maid))multiplier*=1+com.maidweapon.port.config.NativeRules.oath(com.maidweapon.port.config.OathConfig.MAID_DAMAGE,OathRules.MAID_DAMAGE);
        if(e.getEntity() instanceof ServerPlayer p){
            if(active(p))multiplier*=1-com.maidweapon.port.config.NativeRules.oath(com.maidweapon.port.config.OathConfig.PLAYER_DEFENSE,OathRules.PLAYER_DEFENSE);
            multiplier*=(1+Math.min(.5,NativeOathData.get(p.getServer()).penalty(p.getUUID())*com.maidweapon.port.config.NativeRules.oath(com.maidweapon.port.config.OathConfig.BACKLASH_IN,OathRules.BACKLASH_IN)));
        }
        // Unknown mod target caps are not safe to amplify. Vanilla-only positive bonus for this probe.
        if(multiplier>1&&!net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getEntity().getType()).getNamespace().equals("minecraft"))multiplier=1;
        e.setAmount((float)Math.min(Float.MAX_VALUE,e.getAmount()*multiplier));
        }catch(RuntimeException rejected){ /* Unknown data must not grant an effect. */ }
    }
    private NativeOathService(){}
}
