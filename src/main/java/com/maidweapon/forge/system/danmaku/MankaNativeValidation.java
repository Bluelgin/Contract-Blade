package com.maidweapon.forge.system.danmaku;

import com.maidweapon.forge.compat.tlm.MankaAcceptanceCommands;
import com.maidweapon.forge.compat.tlm.danmaku.*;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.mastery.ContractMasteryService;
import com.maidweapon.forge.system.resonance.ResonanceService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Real Forge/TLM server with explicitly labelled FakePlayer; not a multiplayer or visual test. */
@Mod.EventBusSubscriber(modid="maid_weapon")
public final class MankaNativeValidation {
    private static ServerPlayer player;
    private static int ticks, cases;
    private static final Set<UUID> FANTASY = new HashSet<>();
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if(!Boolean.getBoolean("contractblade.danmaku.nativeValidation")) return;
        try {
            player=net.minecraftforge.common.util.FakePlayerFactory.get(event.getServer().overworld(),
                    new com.mojang.authlib.GameProfile(UUID.fromString("6044dfb7-97bd-47b6-9bae-72d42b53248a"),"MankaNative"));
            var field=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");field.setAccessible(true);
            @SuppressWarnings("unchecked") var map=(Map<UUID,ServerPlayer>)field.get(event.getServer().getPlayerList());
            map.put(player.getUUID(),player);MankaAcceptanceCommands.setup(player);ticks=0;cases=0;FANTASY.clear();
        } catch(Throwable failure) { fail(failure); }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || player==null) return;
        try {
            player.tickCount++;
            var pe=new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player);
            DanmakuCombatService.tick(pe);DanmakuShotQueue.playerTick(pe);
            int now=ticks++;
            if(now%90==0 && cases<40) {
                int level=cases/4+1;var form=DanmakuForm.values()[cases%4];
                ContractMasteryService.setLevel(player.getServer().createCommandSourceStack(),player,level);
                var item=player.getMainHandItem();String binding=ContractCarrierData.getBindingId(item);
                ResonanceService.set(player,item,binding,1000);
                boolean result=DanmakuCombatService.cast(player,form);
                check(result==form.unlocked(level),"unlock level="+level+" form="+form);
                int expected=result?new int[]{60,100,160,320}[form.ordinal()]:0;
                check(ResonanceService.current(player,item,binding)==1000-expected,"single exact debit");
                cases++;
            }
            if(cases==40) for(var bullet:player.serverLevel().getEntitiesOfClass(ContractDanmakuProjectile.class,
                    player.getBoundingBox().inflate(48)))
                if(bullet.visualForm()==3 && bullet.getOwner()==player) FANTASY.add(bullet.getUUID());
            if(cases==40 && now>39*90+85) {
                check(FANTASY.size()==32,"actual fantasy entity count="+FANTASY.size());
                var item=player.getMainHandItem();String binding=ContractCarrierData.getBindingId(item);
                ResonanceService.set(player,item,binding,0);
                check(!DanmakuCombatService.cast(player,DanmakuForm.NEEDLE),"zero balance refused");
                MankaAcceptanceCommands.bauble(player,2);
                check(DanmakuCombatService.cast(player,DanmakuForm.NEEDLE),"real native endless bauble waives cost");
                check(ResonanceService.current(player,item,binding)==0,"waiver preserves zero");
                MankaAcceptanceCommands.bauble(player,0);
                check(DanmakuEligibility.context(player)==null,"inventory-only talisman denied");
                org.slf4j.LoggerFactory.getLogger("MankaAcceptance").info(
                    "MANKA_NATIVE_PASS cases=40 actual_fantasy_bullets=32 zero_balance_denied=true waiver=true inventory_only_denied=true FakePlayer=true");
                var server=player.getServer();player=null;server.halt(false);
            }
        }catch(Throwable failure){ fail(failure); }
    }
    private static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    private static void fail(Throwable failure){
        org.slf4j.LoggerFactory.getLogger("MankaAcceptance").error("MANKA_NATIVE_FAIL",failure);
        if(player!=null){var server=player.getServer();player=null;server.halt(false);}
    }
    private MankaNativeValidation() { }
}
