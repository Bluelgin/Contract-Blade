package com.maidweapon.forge.system.danmaku;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in real server tick sample. Reports player count so FakePlayers cannot look like real clients. */
@Mod.EventBusSubscriber(modid="maid_weapon")
public final class MankaServerMetrics {
    private static long start;
    private static int ticks;
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(!Boolean.getBoolean("contractblade.danmaku.acceptance") || event.phase!=TickEvent.Phase.END)return;
        long now=System.nanoTime();if(start==0)start=now;
        ticks++;if(now-start<10_000_000_000L)return;
        var server=event.getServer();
        org.slf4j.LoggerFactory.getLogger("MankaAcceptance").info(
            "MANKA_TPS actualTPS={} averageMSPT={} realPlayers={}",
            Math.min(20,ticks*1e9/(now-start)),server.getAverageTickTime(),server.getPlayerCount());
        start=now;ticks=0;
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event){start=0;ticks=0;}
    private MankaServerMetrics() { }
}
